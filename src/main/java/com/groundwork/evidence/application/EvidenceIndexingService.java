package com.groundwork.evidence.application;

import com.groundwork.application.port.out.EmbeddingPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class EvidenceIndexingService {
    private final EvidenceCatalogRepository catalog;
    private final EmbeddingPort embeddings;
    private final MeterRegistry metrics;

    public EvidenceIndexingService(EvidenceCatalogRepository catalog, EmbeddingPort embeddings,
            MeterRegistry metrics) {
        this.catalog = catalog;
        this.embeddings = embeddings;
        this.metrics = metrics;
    }

    public EvidenceCatalogRepository.ArtifactUpsert upsert(UUID workspaceId, UUID connectionId,
            String provider, String externalId, String artifactType, String title, String canonicalUrl,
            Map<String, Object> sourceAcl, String sourceVersion, String content, Map<String, Object> metadata) {
        var result = catalog.upsert(workspaceId, connectionId, provider, externalId, artifactType,
            title, canonicalUrl, sourceAcl, sourceVersion, content, metadata);
        if (result.versionCreated() && content != null && !content.isBlank()) {
            try {
                double[] vector = embeddings.embed(content.length() > 20_000 ? content.substring(0, 20_000) : content);
                catalog.storeEmbedding(result.version().id(), vector, embeddings.modelName(), embeddings.modelVersion());
                metrics.counter("groundwork.evidence.embedding", "result", "success", "type", artifactType).increment();
            } catch (RuntimeException exception) {
                metrics.counter("groundwork.evidence.embedding", "result", "degraded", "type", artifactType).increment();
            }
        }
        return result;
    }
}
