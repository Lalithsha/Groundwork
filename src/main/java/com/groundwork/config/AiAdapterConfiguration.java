package com.groundwork.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.adapter.out.ai.DeterministicEmbeddingAdapter;
import com.groundwork.adapter.out.ai.OpenAiCompatibleChatAdapter;
import com.groundwork.adapter.out.ai.OpenAiCompatibleEmbeddingAdapter;
import com.groundwork.adapter.out.ai.UnavailableChatGenerationAdapter;
import com.groundwork.application.port.out.ChatGenerationPort;
import com.groundwork.application.port.out.EmbeddingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiAdapterConfiguration {

    @Bean
    public EmbeddingPort embeddingPort(
            ObjectMapper objectMapper,
            @Value("${groundwork.embedding.provider:local}") String provider,
            @Value("${groundwork.embedding.base-url}") String baseUrl,
            @Value("${groundwork.embedding.api-key}") String apiKey,
            @Value("${groundwork.embedding.model:gemini-embedding-001}") String model,
            @Value("${groundwork.embedding.dimensions:1536}") int dimensions,
            @Value("${groundwork.ai.timeout-seconds:60}") long timeoutSeconds) {
        if ("openai-compatible".equalsIgnoreCase(provider)) {
            requireRealKey(apiKey, "embedding");
            return new OpenAiCompatibleEmbeddingAdapter(
                objectMapper, baseUrl, apiKey, model, dimensions, Duration.ofSeconds(timeoutSeconds));
        }
        return new DeterministicEmbeddingAdapter(dimensions);
    }

    @Bean
    public ChatGenerationPort chatGenerationPort(
            ObjectMapper objectMapper,
            @Value("${groundwork.chat.base-url}") String baseUrl,
            @Value("${groundwork.chat.api-key}") String apiKey,
            @Value("${groundwork.chat.model}") String model,
            @Value("${groundwork.ai.timeout-seconds:60}") long timeoutSeconds) {
        if (!isRealKey(apiKey)) return new UnavailableChatGenerationAdapter();
        return new OpenAiCompatibleChatAdapter(
            objectMapper, baseUrl, apiKey, model, Duration.ofSeconds(timeoutSeconds));
    }

    private void requireRealKey(String apiKey, String capability) {
        if (!isRealKey(apiKey)) {
            throw new IllegalStateException("A real API key is required for remote " + capability + " generation");
        }
    }

    private boolean isRealKey(String apiKey) {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("demo") && !apiKey.startsWith("your_");
    }
}
