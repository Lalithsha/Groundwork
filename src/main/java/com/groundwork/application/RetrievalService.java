package com.groundwork.application;

import com.groundwork.adapter.out.ai.CohereRerankAdapter;
import com.groundwork.domain.model.DocumentChunk;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RetrievalService {

    private static final int RRF_K = 60;

    private final DocumentRepository documentRepository;
    private final CohereRerankAdapter rerankAdapter;
    private final Counter hitCounter;
    private final Counter missCounter;

    public RetrievalService(DocumentRepository documentRepository, CohereRerankAdapter rerankAdapter, MeterRegistry meterRegistry) {
        this.documentRepository = documentRepository;
        this.rerankAdapter = rerankAdapter;
        this.hitCounter = meterRegistry.counter("cache.retrieval.hits");
        this.missCounter = meterRegistry.counter("cache.retrieval.misses");
    }

    public List<DocumentChunk> retrieve(String query, String mode, int limit) {
        return retrieve(query, mode, null, limit);
    }

    @Cacheable(value = "retrieval", key = "#query.hashCode() + ':' + #mode + ':' + (#docFilter != null ? #docFilter : 'all')")
    public List<DocumentChunk> retrieve(String query, String mode, String docFilter, int limit) {
        missCounter.increment();
        if ("naive".equalsIgnoreCase(mode)) {
            return documentRepository.searchVectorOnly(query, docFilter, limit);
        }

        // Hybrid mode: Fetch candidates from both Vector search and Full-Text search
        List<DocumentChunk> vectorResults = documentRepository.searchVectorOnly(query, docFilter, 20);
        List<DocumentChunk> keywordResults = documentRepository.searchKeywordOnly(query, docFilter, 20);

        // Reciprocal Rank Fusion (RRF)
        Map<UUID, Double> rrfScores = new HashMap<>();
        Map<UUID, DocumentChunk> docMap = new HashMap<>();

        accumulateRrfScores(vectorResults, rrfScores, docMap);
        accumulateRrfScores(keywordResults, rrfScores, docMap);

        List<DocumentChunk> merged = docMap.entrySet().stream()
            .map(entry -> {
                DocumentChunk orig = entry.getValue();
                double rrfScore = rrfScores.get(entry.getKey());
                return new DocumentChunk(orig.id(), orig.title(), orig.content(), orig.sourceType(), orig.contentHash(), rrfScore);
            })
            .sorted(Comparator.comparingDouble(DocumentChunk::score).reversed())
            .limit(10)
            .collect(Collectors.toList());

        // Reranking step via Cohere API adapter
        return rerankAdapter.rerank(query, merged, limit);
    }

    private void accumulateRrfScores(List<DocumentChunk> list, Map<UUID, Double> scores, Map<UUID, DocumentChunk> docMap) {
        for (int rank = 0; rank < list.size(); rank++) {
            DocumentChunk doc = list.get(rank);
            docMap.putIfAbsent(doc.id(), doc);
            double currentScore = scores.getOrDefault(doc.id(), 0.0);
            scores.put(doc.id(), currentScore + (1.0 / (RRF_K + rank + 1)));
        }
    }
}
