package com.groundwork.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.application.port.out.EmbeddingPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiCompatibleEmbeddingAdapter implements EmbeddingPort {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final Duration timeout;

    public OpenAiCompatibleEmbeddingAdapter(
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey,
            String model,
            int dimensions,
            Duration timeout) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(normalizeBaseUrl(baseUrl) + "embeddings");
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.timeout = timeout;
    }

    @Override
    public List<double[]> embed(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("input", texts);
            payload.put("dimensions", dimensions);

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Embedding provider returned HTTP " + response.statusCode());
            }
            return parse(response.body(), texts.size());
        } catch (IOException exception) {
            throw new IllegalStateException("Embedding provider response could not be processed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding request was interrupted", exception);
        }
    }

    private List<double[]> parse(String body, int expectedCount) throws IOException {
        JsonNode data = objectMapper.readTree(body).path("data");
        if (!data.isArray() || data.size() != expectedCount) {
            throw new IllegalStateException("Embedding provider returned an unexpected item count");
        }
        List<double[]> result = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            JsonNode values = item.path("embedding");
            if (!values.isArray() || values.size() != dimensions) {
                throw new IllegalStateException("Embedding provider returned an unexpected vector dimension");
            }
            double[] vector = new double[dimensions];
            for (int i = 0; i < dimensions; i++) vector[i] = values.get(i).asDouble();
            result.add(vector);
        }
        return List.copyOf(result);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Override public String modelName() { return model; }
    @Override public String modelVersion() { return "remote"; }
    @Override public int dimensions() { return dimensions; }
}
