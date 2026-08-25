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
            @Value("${groundwork.embedding.protocol:openai-compatible}") String protocol,
            @Value("${groundwork.embedding.base-url}") String baseUrl,
            @Value("${groundwork.embedding.api-key}") String apiKey,
            @Value("${groundwork.embedding.model:gemini-embedding-001}") String model,
            @Value("${groundwork.embedding.dimensions:1536}") int dimensions,
            @Value("${groundwork.ai.timeout-seconds:60}") long timeoutSeconds) {
        if ("local".equalsIgnoreCase(provider)) {
            return new DeterministicEmbeddingAdapter(dimensions);
        }
        requireSupportedRemoteProvider(provider, protocol, "embedding");
        if ("openai-compatible".equalsIgnoreCase(protocol)) {
            requireRealKey(apiKey, "embedding");
            return new OpenAiCompatibleEmbeddingAdapter(
                objectMapper, baseUrl, apiKey, model, dimensions, Duration.ofSeconds(timeoutSeconds));
        }
        throw new IllegalStateException("Unsupported embedding protocol: " + protocol);
    }

    @Bean
    public ChatGenerationPort chatGenerationPort(
            ObjectMapper objectMapper,
            @Value("${groundwork.chat.provider:gemini}") String provider,
            @Value("${groundwork.chat.protocol:openai-compatible}") String protocol,
            @Value("${groundwork.chat.base-url}") String baseUrl,
            @Value("${groundwork.chat.api-key}") String apiKey,
            @Value("${groundwork.chat.model}") String model,
            @Value("${groundwork.ai.timeout-seconds:60}") long timeoutSeconds) {
        if (!isRealKey(apiKey)) return new UnavailableChatGenerationAdapter();
        requireSupportedRemoteProvider(provider, protocol, "chat");
        return new OpenAiCompatibleChatAdapter(
            objectMapper, baseUrl, apiKey, model, Duration.ofSeconds(timeoutSeconds));
    }

    private void requireSupportedRemoteProvider(String provider, String protocol, String capability) {
        if (!"gemini".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("Unsupported " + capability + " provider: " + provider);
        }
        if (!"openai-compatible".equalsIgnoreCase(protocol)) {
            throw new IllegalStateException("Unsupported " + capability + " protocol: " + protocol);
        }
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
