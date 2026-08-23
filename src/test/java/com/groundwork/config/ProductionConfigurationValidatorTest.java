package com.groundwork.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {
    @Test
    void acceptsHardenedConfiguration() {
        validator(true, "a-unique-secret-longer-than-forty-eight-characters-123456", "https://groundwork.example",
            "openai-compatible", "live-embedding-key", "live-chat-key", false).afterPropertiesSet();
    }

    @Test
    void rejectsLocalOrPlaceholderProductionConfiguration() {
        assertThatThrownBy(() -> validator(false, "supersecretkeyforgroundworkjwttokengeneration12345", "*",
            "local", "demo_key", "demo_key", false).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validator(true, "a-unique-secret-longer-than-forty-eight-characters-123456",
            "https://groundwork.example", "openai-compatible", "demo_key", "live-chat-key", false).afterPropertiesSet())
            .hasMessageContaining("live chat and embedding");
        assertThatThrownBy(() -> validator(true, "a-unique-secret-longer-than-forty-eight-characters-123456",
            "https://groundwork.example", "openai-compatible", "live-embedding-key", "live-chat-key", true).afterPropertiesSet())
            .hasMessageContaining("Billing cannot be enabled");
    }

    private ProductionConfigurationValidator validator(boolean security, String secret, String origins,
            String provider, String embeddingKey, String chatKey, boolean billing) {
        return new ProductionConfigurationValidator(security, secret, origins, provider, embeddingKey, chatKey, billing,
            "a-unique-connector-key-longer-than-forty-eight-characters-123456", "a-github-webhook-secret-longer-than-32-chars",
            false, "", "", false, "", "");
    }
}
