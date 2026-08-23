package com.groundwork.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionConfigurationValidator implements InitializingBean {
    private final boolean securityEnabled;
    private final String jwtSecret;
    private final String allowedOrigins;
    private final String embeddingProvider;
    private final String embeddingApiKey;
    private final String chatApiKey;
    private final boolean billingEnabled;

    public ProductionConfigurationValidator(
            @Value("${groundwork.security.enabled:false}") boolean securityEnabled,
            @Value("${groundwork.jwt.secret}") String jwtSecret,
            @Value("${groundwork.security.allowed-origins}") String allowedOrigins,
            @Value("${groundwork.embedding.provider}") String embeddingProvider,
            @Value("${groundwork.embedding.api-key:}") String embeddingApiKey,
            @Value("${groundwork.chat.api-key:}") String chatApiKey,
            @Value("${groundwork.billing.enabled:false}") boolean billingEnabled) {
        this.securityEnabled = securityEnabled;
        this.jwtSecret = jwtSecret;
        this.allowedOrigins = allowedOrigins;
        this.embeddingProvider = embeddingProvider;
        this.embeddingApiKey = embeddingApiKey;
        this.chatApiKey = chatApiKey;
        this.billingEnabled = billingEnabled;
    }

    @Override
    public void afterPropertiesSet() {
        if (!securityEnabled) throw new IllegalStateException("SECURITY_ENABLED must be true in production");
        if (jwtSecret.length() < 48 || jwtSecret.contains("supersecretkeyforgroundwork")) {
            throw new IllegalStateException("Production requires a unique JWT signing secret of at least 48 characters");
        }
        if (allowedOrigins.contains("*")) throw new IllegalStateException("Wildcard CORS origins are forbidden in production");
        if (!"openai-compatible".equalsIgnoreCase(embeddingProvider)) {
            throw new IllegalStateException("Production requires a remote embedding provider");
        }
        if (isPlaceholder(embeddingApiKey) || isPlaceholder(chatApiKey)) {
            throw new IllegalStateException("Production requires live chat and embedding API keys");
        }
        if (billingEnabled) {
            throw new IllegalStateException("Billing cannot be enabled until a verified payment-provider adapter is installed");
        }
    }

    private boolean isPlaceholder(String value) {
        return value == null || value.isBlank() || value.toLowerCase(java.util.Locale.ROOT).contains("demo") ||
            value.toLowerCase(java.util.Locale.ROOT).contains("replace-with");
    }
}
