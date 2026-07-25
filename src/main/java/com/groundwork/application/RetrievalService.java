package com.groundwork.application;

import com.groundwork.domain.model.DocumentChunk;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RetrievalService {

    private static final int RRF_K = 60;

    // Simulated/JDBC vector + keyword search adapter interface
    private final DocumentRepository documentRepository;

    public RetrievalService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Cacheable(value = "retrieval", key = "#query.hashCode() + ':' + #mode")
    public List<DocumentChunk> retrieve(String query, String mode, int limit) {
        if ("naive".equalsIgnoreCase(mode)) {
            return documentRepository.searchVectorOnly(query, limit);
        }

        // Hybrid mode: Fetch candidates from both Vector search and Full-Text search
        List<DocumentChunk> vectorResults = documentRepository.searchVectorOnly(query, 20);
        List<DocumentChunk> keywordResults = documentRepository.searchKeywordOnly(query, 20);

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

        // Reranking step (Cohere or fallback top-K cut)
        return rerankCandidates(query, merged, limit);
    }

    private void accumulateRrfScores(List<DocumentChunk> list, Map<UUID, Double> scores, Map<UUID, DocumentChunk> docMap) {
        for (int rank = 0; rank < list.size(); rank++) {
            DocumentChunk doc = list.get(rank);
            docMap.putIfAbsent(doc.id(), doc);
            double currentScore = scores.getOrDefault(doc.id(), 0.0);
            scores.put(doc.id(), currentScore + (1.0 / (RRF_K + rank + 1)));
        }
    }

    private List<DocumentChunk> rerankCandidates(String query, List<DocumentChunk> candidates, int topK) {
        // Returns candidates sorted by score up to topK
        return candidates.stream().limit(topK).toList();
    }
}
