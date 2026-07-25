package com.groundwork.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.domain.model.DocumentChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class CohereRerankAdapter {

    @Value("${groundwork.cohere.api-key:demo_cohere_key}")
    private String apiKey;

    private final ObjectMapper mapper = new ObjectMapper();

    public List<DocumentChunk> rerank(String query, List<DocumentChunk> candidates, int topK) {
        if (candidates.isEmpty()) return candidates;
        if (apiKey == null || apiKey.equals("demo_cohere_key") || apiKey.isBlank()) {
            // Fallback: sort by existing RRF score
            return candidates.stream()
                .sorted(Comparator.comparingDouble(DocumentChunk::score).reversed())
                .limit(topK)
                .toList();
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

            List<String> docs = candidates.stream().map(DocumentChunk::content).toList();
            Map<String, Object> body = Map.of(
                "model", "rerank-english-v3.0",
                "query", query,
                "documents", docs,
                "top_n", topK
            );

            String requestJson = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.cohere.com/v1/rerank"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(3))
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
                    reranked.add(new DocumentChunk(orig.id(), orig.title(), orig.content(), orig.sourceType(), orig.contentHash(), relevanceScore));
                }
                return reranked;
            }
        } catch (Exception ignored) {
            // Soft fallback to RRF candidates on external API failure
        }

        return candidates.stream()
            .sorted(Comparator.comparingDouble(DocumentChunk::score).reversed())
            .limit(topK)
            .toList();
    }
}
