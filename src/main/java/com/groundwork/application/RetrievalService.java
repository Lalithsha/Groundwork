package com.groundwork.application;

import com.groundwork.adapter.out.ai.CohereRerankAdapter;
import com.groundwork.application.port.out.EmbeddingPort;
import com.groundwork.domain.model.DocumentChunk;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RetrievalService {
    private static final int RRF_K = 60;

    private final DocumentRepository documents;
    private final CohereRerankAdapter reranker;
    private final EmbeddingPort embeddings;
    private final Counter missCounter;

    public RetrievalService(DocumentRepository documents, CohereRerankAdapter reranker,
            EmbeddingPort embeddings, MeterRegistry meterRegistry) {
        this.documents = documents;
        this.reranker = reranker;
        this.embeddings = embeddings;
        this.missCounter = meterRegistry.counter("groundwork.retrieval.cache.misses");
    }

    public List<DocumentChunk> retrieve(String query, String mode, int limit) {
        return retrieve(query, mode, null, null, limit);
    }

    public List<DocumentChunk> retrieve(String query, String mode, String documentFilter, int limit) {
        return retrieve(query, mode, null, documentFilter, limit);
    }

    @Cacheable(value = "retrieval", key = "T(com.groundwork.application.Hashing).sha256(#query) + ':' + #mode + ':' + " +
        "(#workspaceId != null ? #workspaceId : 'all') + ':' + " +
        "(#documentFilter != null ? #documentFilter : 'all') + ':' + #limit")
    public List<DocumentChunk> retrieve(String query, String mode, UUID workspaceId,
            String documentFilter, int limit) {
        if (query == null || query.isBlank()) return List.of();
        missCounter.increment();
        double[] queryEmbedding = embeddings.embed(query);
        if ("naive".equalsIgnoreCase(mode) || "vector".equalsIgnoreCase(mode)) {
            return documents.searchVector(queryEmbedding, workspaceId, documentFilter, limit);
        }

        List<DocumentChunk> vector = documents.searchVector(queryEmbedding, workspaceId, documentFilter, 20);
        List<DocumentChunk> keyword = documents.searchKeyword(query, workspaceId, documentFilter, 20);
        List<DocumentChunk> fused = fuse(vector, keyword).stream().limit(10).toList();
        if ("hybrid".equalsIgnoreCase(mode)) return fused.stream().limit(limit).toList();
        return reranker.rerank(query, fused, limit);
    }

    List<DocumentChunk> fuse(List<DocumentChunk> vector, List<DocumentChunk> keyword) {
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, DocumentChunk> chunks = new HashMap<>();
        accumulate(vector, scores, chunks);
        accumulate(keyword, scores, chunks);
        return chunks.entrySet().stream()
            .map(entry -> withScore(entry.getValue(), scores.get(entry.getKey())))
            .sorted(Comparator.comparingDouble(DocumentChunk::score).reversed())
            .toList();
    }

    private void accumulate(List<DocumentChunk> ranked, Map<UUID, Double> scores,
            Map<UUID, DocumentChunk> chunks) {
        for (int rank = 0; rank < ranked.size(); rank++) {
            DocumentChunk chunk = ranked.get(rank);
            chunks.putIfAbsent(chunk.id(), chunk);
            scores.merge(chunk.id(), 1.0 / (RRF_K + rank + 1), Double::sum);
        }
    }

    private DocumentChunk withScore(DocumentChunk chunk, double score) {
        return new DocumentChunk(chunk.id(), chunk.documentId(), chunk.title(), chunk.content(),
            chunk.sourceType(), chunk.contentHash(), score, chunk.chunkIndex(),
            chunk.sectionTitle(), chunk.pageNumber());
    }
}
