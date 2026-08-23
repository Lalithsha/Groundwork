package com.groundwork.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.application.port.out.ChatGenerationPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class OpenAiCompatibleChatAdapter implements ChatGenerationPort {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public OpenAiCompatibleChatAdapter(
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey,
            String model,
            Duration timeout) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.objectMapper = objectMapper;
        this.endpoint = URI.create((baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "chat/completions");
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public String generate(String prompt) {
        HttpResponse<String> response = send(prompt, false, HttpResponse.BodyHandlers.ofString());
        try {
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content.isBlank()) throw new IllegalStateException("Chat provider returned an empty response");
            return content;
        } catch (IOException exception) {
            throw new IllegalStateException("Chat provider response could not be processed", exception);
        }
    }

    @Override
    public void stream(String prompt, Consumer<String> tokenConsumer) {
        HttpResponse<Stream<String>> response = send(prompt, true, HttpResponse.BodyHandlers.ofLines());
        try (Stream<String> lines = response.body()) {
            lines.filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5).trim())
                .takeWhile(data -> !"[DONE]".equals(data))
                .forEach(data -> emitDelta(data, tokenConsumer));
        }
    }

    private void emitDelta(String data, Consumer<String> tokenConsumer) {
        if (data.isBlank()) return;
        try {
            String token = objectMapper.readTree(data)
                .path("choices").path(0).path("delta").path("content").asText("");
            if (!token.isEmpty()) tokenConsumer.accept(token);
        } catch (IOException exception) {
            throw new IllegalStateException("Streaming chat event could not be processed", exception);
        }
    }

    private <T> HttpResponse<T> send(String prompt, boolean stream, HttpResponse.BodyHandler<T> handler) {
        try {
            Map<String, Object> payload = Map.of(
                "model", model,
                "temperature", 0,
                "stream", stream,
                "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
            HttpResponse<T> response = httpClient.send(request, handler);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Chat provider returned HTTP " + response.statusCode());
            }
            return response;
        } catch (IOException exception) {
            throw new IllegalStateException("Chat provider request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chat provider request was interrupted", exception);
        }
    }

    @Override public boolean isAvailable() { return true; }
}
