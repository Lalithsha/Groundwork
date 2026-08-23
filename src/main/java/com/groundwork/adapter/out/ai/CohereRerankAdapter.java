package com.groundwork.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.domain.model.DocumentChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class CohereRerankAdapter {
    private static final Logger log = LoggerFactory.getLogger(CohereRerankAdapter.class);
    private final String apiKey;
    private final String model;
    private final URI endpoint;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public CohereRerankAdapter(ObjectMapper mapper,
            @Value("${groundwork.cohere.api-key:}") String apiKey,
            @Value("${groundwork.cohere.model:rerank-english-v3.0}") String model,
            @Value("${groundwork.cohere.base-url:https://api.cohere.com/v1/rerank}") String baseUrl,
            @Value("${groundwork.cohere.timeout-seconds:5}") long timeoutSeconds) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = URI.create(baseUrl);
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public List<DocumentChunk> rerank(String query, List<DocumentChunk> candidates, int topK) {
        if (candidates.isEmpty()) return candidates;
        if (apiKey == null || apiKey.isBlank()) {
            // Fallback: sort by existing RRF score
            return candidates.stream()
                .sorted(Comparator.comparingDouble(DocumentChunk::score).reversed())
                .limit(topK)
                .toList();
        }

        try {
            List<String> docs = candidates.stream().map(DocumentChunk::content).toList();
            Map<String, Object> body = Map.of(
                "model", model,
                "query", query,
                "documents", docs,
                "top_n", topK
            );

            String requestJson = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(timeout)
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var jsonNode = mapper.readTree(response.body());
                var results = jsonNode.get("results");
                List<DocumentChunk> reranked = new ArrayList<>();
                for (var item : results) {
                    int index = item.get("index").asInt();
                    double relevanceScore = item.get("relevance_score").asDouble();
                    DocumentChunk orig = candidates.get(index);
                    reranked.add(new DocumentChunk(orig.id(), orig.documentId(), orig.title(), orig.content(),
                        orig.sourceType(), orig.contentHash(), relevanceScore, orig.chunkIndex(),
                        orig.sectionTitle(), orig.pageNumber()));
                }
                return reranked;
            }
            log.warn("Reranker returned HTTP {}; preserving RRF order", response.statusCode());
        } catch (Exception exception) {
            log.warn("Reranker unavailable; preserving RRF order: {}", exception.getMessage());
        }

        return candidates.stream()
            .sorted(Comparator.comparingDouble(DocumentChunk::score).reversed())
            .limit(topK)
            .toList();
    }
}
