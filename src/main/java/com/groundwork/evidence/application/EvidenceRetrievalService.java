package com.groundwork.evidence.application;

import com.groundwork.application.port.out.EmbeddingPort;
import com.groundwork.evidence.domain.EvidenceRelationship;
import com.groundwork.evidence.domain.EvidenceSearchHit;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EvidenceRetrievalService {
    private static final int RRF_K = 60;
    private final EvidenceCatalogRepository catalog;
    private final EmbeddingPort embeddings;
    private final MeterRegistry metrics;

    public EvidenceRetrievalService(EvidenceCatalogRepository catalog, EmbeddingPort embeddings,
            MeterRegistry metrics) {
        this.catalog = catalog;
        this.embeddings = embeddings;
        this.metrics = metrics;
    }

    public List<EvidenceSearchHit> retrieve(UUID workspaceId, String query, int limit, boolean expandGraph) {
        long started = System.nanoTime();
        List<EvidenceSearchHit> keyword = catalog.searchKeyword(workspaceId, query, 20);
        List<EvidenceSearchHit> vector;
        try { vector = catalog.searchVector(workspaceId, embeddings.embed(query), 20); }
        catch (RuntimeException exception) { vector = List.of(); }
        List<EvidenceSearchHit> fused = fuse(vector, keyword);
        if (expandGraph && !fused.isEmpty()) fused = expand(workspaceId, fused, Math.min(limit, 5));
        metrics.timer("groundwork.evidence.retrieval").record(System.nanoTime() - started,
            java.util.concurrent.TimeUnit.NANOSECONDS);
        return fused.stream().limit(limit).toList();
    }

    List<EvidenceSearchHit> fuse(List<EvidenceSearchHit> vector, List<EvidenceSearchHit> keyword) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        Map<UUID, EvidenceSearchHit> hits = new LinkedHashMap<>();
        accumulate(vector, scores, hits);
        accumulate(keyword, scores, hits);
        return hits.values().stream().map(hit -> new EvidenceSearchHit(hit.artifactId(), hit.versionId(),
                hit.artifactType(), hit.title(), hit.canonicalUrl(), hit.sourceVersion(), hit.content(),
                hit.metadata(), scores.get(hit.versionId()), "HYBRID"))
            .sorted(Comparator.comparingDouble(EvidenceSearchHit::score).reversed()).toList();
    }

    private void accumulate(List<EvidenceSearchHit> ranked, Map<UUID, Double> scores,
            Map<UUID, EvidenceSearchHit> hits) {
        for (int index = 0; index < ranked.size(); index++) {
            EvidenceSearchHit hit = ranked.get(index);
            hits.putIfAbsent(hit.versionId(), hit);
            scores.merge(hit.versionId(), 1.0 / (RRF_K + index + 1), Double::sum);
        }
    }

    private List<EvidenceSearchHit> expand(UUID workspaceId, List<EvidenceSearchHit> fused, int seedLimit) {
        Set<UUID> neighborIds = new LinkedHashSet<>();
        for (EvidenceSearchHit seed : fused.stream().limit(seedLimit).toList()) {
            for (EvidenceRelationship relationship : catalog.relationships(workspaceId, seed.artifactId())) {
                neighborIds.add(relationship.sourceArtifactId().equals(seed.artifactId())
                    ? relationship.targetArtifactId() : relationship.sourceArtifactId());
            }
        }
        Set<UUID> existing = fused.stream().map(EvidenceSearchHit::artifactId)
            .collect(java.util.stream.Collectors.toSet());
        neighborIds.removeAll(existing);
        List<EvidenceSearchHit> result = new ArrayList<>(fused);
        for (EvidenceSearchHit neighbor : catalog.currentVersions(workspaceId, neighborIds.stream().limit(10).toList())) {
            result.add(new EvidenceSearchHit(neighbor.artifactId(), neighbor.versionId(), neighbor.artifactType(),
                neighbor.title(), neighbor.canonicalUrl(), neighbor.sourceVersion(), neighbor.content(),
                neighbor.metadata(), 0.001, "GRAPH"));
        }
        return List.copyOf(result);
    }
}
