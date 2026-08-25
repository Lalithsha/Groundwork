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
    private final String embeddingProtocol;
    private final String chatProvider;
    private final String chatProtocol;
    private final String embeddingApiKey;
    private final String chatApiKey;
    private final boolean billingEnabled;
    private final String connectorCredentialKey;
    private final String githubWebhookSecret;
    private final boolean githubEnabled;
    private final String githubAppId;
    private final String githubPrivateKey;
    private final boolean atlassianEnabled;
    private final String atlassianClientId;
    private final String atlassianClientSecret;

    public ProductionConfigurationValidator(
            @Value("${groundwork.security.enabled:false}") boolean securityEnabled,
            @Value("${groundwork.jwt.secret}") String jwtSecret,
            @Value("${groundwork.security.allowed-origins}") String allowedOrigins,
            @Value("${groundwork.embedding.provider}") String embeddingProvider,
            @Value("${groundwork.embedding.protocol}") String embeddingProtocol,
            @Value("${groundwork.chat.provider}") String chatProvider,
            @Value("${groundwork.chat.protocol}") String chatProtocol,
            @Value("${groundwork.embedding.api-key:}") String embeddingApiKey,
            @Value("${groundwork.chat.api-key:}") String chatApiKey,
            @Value("${groundwork.billing.enabled:false}") boolean billingEnabled,
            @Value("${groundwork.connectors.credential-key:}") String connectorCredentialKey,
            @Value("${groundwork.integrations.github.webhook-secret:}") String githubWebhookSecret,
            @Value("${groundwork.integrations.github.enabled:false}") boolean githubEnabled,
            @Value("${groundwork.integrations.github.app-id:}") String githubAppId,
            @Value("${groundwork.integrations.github.private-key-pkcs8-base64:}") String githubPrivateKey,
            @Value("${groundwork.integrations.atlassian.enabled:false}") boolean atlassianEnabled,
            @Value("${groundwork.integrations.atlassian.client-id:}") String atlassianClientId,
            @Value("${groundwork.integrations.atlassian.client-secret:}") String atlassianClientSecret) {
        this.securityEnabled = securityEnabled;
        this.jwtSecret = jwtSecret;
        this.allowedOrigins = allowedOrigins;
        this.embeddingProvider = embeddingProvider;
        this.embeddingProtocol = embeddingProtocol;
        this.chatProvider = chatProvider;
        this.chatProtocol = chatProtocol;
        this.embeddingApiKey = embeddingApiKey;
        this.chatApiKey = chatApiKey;
        this.billingEnabled = billingEnabled;
        this.connectorCredentialKey = connectorCredentialKey;
        this.githubWebhookSecret = githubWebhookSecret;
        this.githubEnabled = githubEnabled;
        this.githubAppId = githubAppId;
        this.githubPrivateKey = githubPrivateKey;
        this.atlassianEnabled = atlassianEnabled;
        this.atlassianClientId = atlassianClientId;
        this.atlassianClientSecret = atlassianClientSecret;
    }

    @Override
    public void afterPropertiesSet() {
        if (!securityEnabled) throw new IllegalStateException("SECURITY_ENABLED must be true in production");
        if (jwtSecret.length() < 48 || jwtSecret.contains("supersecretkeyforgroundwork")) {
            throw new IllegalStateException("Production requires a unique JWT signing secret of at least 48 characters");
        }
        if (allowedOrigins.contains("*")) throw new IllegalStateException("Wildcard CORS origins are forbidden in production");
        if (!"gemini".equalsIgnoreCase(embeddingProvider) || !"gemini".equalsIgnoreCase(chatProvider)) {
            throw new IllegalStateException("Production is configured for Gemini chat and embedding providers");
        }
        if (!"openai-compatible".equalsIgnoreCase(embeddingProtocol) ||
                !"openai-compatible".equalsIgnoreCase(chatProtocol)) {
            throw new IllegalStateException("Gemini integrations require the configured OpenAI-compatible protocol");
        }
        if (isPlaceholder(embeddingApiKey) || isPlaceholder(chatApiKey)) {
            throw new IllegalStateException("Production requires live chat and embedding API keys");
        }
        if (billingEnabled) {
            throw new IllegalStateException("Billing cannot be enabled until a verified payment-provider adapter is installed");
        }
        if (connectorCredentialKey.length() < 48 || connectorCredentialKey.contains("local-development")) {
            throw new IllegalStateException("Production requires a unique connector credential key of at least 48 characters");
        }
        if (githubWebhookSecret.length() < 32 || githubWebhookSecret.contains("local-github")) {
            throw new IllegalStateException("Production requires a unique GitHub webhook secret of at least 32 characters");
        }
        if (githubEnabled && (githubAppId.isBlank() || githubPrivateKey.isBlank())) {
            throw new IllegalStateException("Enabled GitHub integration requires an app ID and PKCS#8 private key");
        }
        if (atlassianEnabled && (atlassianClientId.isBlank() || atlassianClientSecret.isBlank())) {
            throw new IllegalStateException("Enabled Atlassian integration requires an OAuth client ID and secret");
        }
    }

    private boolean isPlaceholder(String value) {
        return value == null || value.isBlank() || value.toLowerCase(java.util.Locale.ROOT).contains("demo") ||
            value.toLowerCase(java.util.Locale.ROOT).contains("replace-with");
    }
}
