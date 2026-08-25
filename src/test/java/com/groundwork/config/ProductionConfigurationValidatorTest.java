package com.groundwork.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {
    @Test
    void acceptsHardenedConfiguration() {
        validator(true, "a-unique-secret-longer-than-forty-eight-characters-123456", "https://groundwork.example",
            "gemini", "openai-compatible", "live-embedding-key", "live-chat-key", false).afterPropertiesSet();
    }

    @Test
    void rejectsLocalOrPlaceholderProductionConfiguration() {
        assertThatThrownBy(() -> validator(false, "supersecretkeyforgroundworkjwttokengeneration12345", "*",
            "local", "openai-compatible", "demo_key", "demo_key", false).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validator(true, "a-unique-secret-longer-than-forty-eight-characters-123456",
            "https://groundwork.example", "gemini", "openai-compatible", "demo_key", "live-chat-key", false).afterPropertiesSet())
            .hasMessageContaining("live chat and embedding");
        assertThatThrownBy(() -> validator(true, "a-unique-secret-longer-than-forty-eight-characters-123456",
            "https://groundwork.example", "gemini", "openai-compatible", "live-embedding-key", "live-chat-key", true).afterPropertiesSet())
            .hasMessageContaining("Billing cannot be enabled");
    }

    @Test
    void rejectsProviderAndProtocolBeingConfused() {
        assertThatThrownBy(() -> validator(true, "a-unique-secret-longer-than-forty-eight-characters-123456",
            "https://groundwork.example", "openai-compatible", "openai-compatible",
            "live-embedding-key", "live-chat-key", false).afterPropertiesSet())
            .hasMessageContaining("Gemini chat and embedding providers");

        assertThatThrownBy(() -> validator(true, "a-unique-secret-longer-than-forty-eight-characters-123456",
            "https://groundwork.example", "gemini", "native",
            "live-embedding-key", "live-chat-key", false).afterPropertiesSet())
            .hasMessageContaining("OpenAI-compatible protocol");
    }

    private ProductionConfigurationValidator validator(boolean security, String secret, String origins,
            String provider, String protocol, String embeddingKey, String chatKey, boolean billing) {
        return new ProductionConfigurationValidator(security, secret, origins, provider, protocol,
            "gemini", "openai-compatible", embeddingKey, chatKey, billing,
            "a-unique-connector-key-longer-than-forty-eight-characters-123456", "a-github-webhook-secret-longer-than-32-chars",
            false, "", "", false, "", "");
    }
}
