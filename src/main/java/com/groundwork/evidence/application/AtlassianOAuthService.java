package com.groundwork.evidence.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.groundwork.evidence.application.port.out.AtlassianOAuthPort;
import com.groundwork.evidence.domain.ConnectorConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AtlassianOAuthService {
    private static final Set<String> PROVIDERS = Set.of("JIRA", "CONFLUENCE");
    private final ConnectorOAuthStateRepository states;
    private final ConnectorRepository connections;
    private final AtlassianOAuthPort tokens;
    private final EvidenceJson json;
    private final String clientId;
    private final String authorizationUrl;
    private final String redirectUri;

    public AtlassianOAuthService(ConnectorOAuthStateRepository states, ConnectorRepository connections,
            AtlassianOAuthPort tokens, EvidenceJson json,
            @Value("${groundwork.integrations.atlassian.client-id:}") String clientId,
            @Value("${groundwork.integrations.atlassian.authorization-url:https://auth.atlassian.com/authorize}") String authorizationUrl,
            @Value("${groundwork.integrations.atlassian.redirect-uri:http://localhost:8080/api/integrations/atlassian/oauth/callback}") String redirectUri) {
        this.states = states;
        this.connections = connections;
        this.tokens = tokens;
        this.json = json;
        this.clientId = clientId;
        this.authorizationUrl = authorizationUrl;
        this.redirectUri = redirectUri;
    }

    public AuthorizationStart start(UUID workspaceId, UUID userId, String provider,
            List<String> scopes, Map<String, Object> selectedResources) {
        String normalized = provider.toUpperCase(Locale.ROOT);
        if (!PROVIDERS.contains(normalized)) throw new IllegalArgumentException("Atlassian provider must be JIRA or CONFLUENCE");
        if (clientId.isBlank()) throw new IllegalStateException("Atlassian OAuth is not configured");
        List<String> effectiveScopes = scopes == null || scopes.isEmpty()
            ? defaultScopes(normalized) : List.copyOf(scopes);
        String state = randomState();
        states.create(state, workspaceId, userId, normalized, effectiveScopes,
            selectedResources == null ? Map.of() : selectedResources, Instant.now().plusSeconds(600));
        String url = authorizationUrl + "?audience=api.atlassian.com&client_id=" + encode(clientId) +
            "&scope=" + encode(String.join(" ", effectiveScopes)) + "&redirect_uri=" + encode(redirectUri) +
            "&state=" + encode(state) + "&response_type=code&prompt=consent";
        return new AuthorizationStart(url, Instant.now().plusSeconds(600));
    }

    public ConnectorConnection callback(String code, String rawState) {
        var state = states.consume(rawState)
            .orElseThrow(() -> new IllegalArgumentException("OAuth state is invalid, expired, or already used"));
        JsonNode tokenResponse = tokens.exchangeAuthorizationCode(code, redirectUri);
        Map<String, Object> tokenBundle = tokens.tokenBundle(tokenResponse, null);
        String accessToken = String.valueOf(tokenBundle.get("accessToken"));
        JsonNode resources = tokens.accessibleResources(accessToken);
        JsonNode cloud = selectCloud(resources, state.selectedResources());
        String cloudId = cloud.path("id").asText();
        if (cloudId.isBlank()) throw new IllegalStateException("No accessible Atlassian site was returned");
        Map<String, Object> metadata = new LinkedHashMap<>(state.selectedResources());
        metadata.put("cloudId", cloudId);
        metadata.put("siteName", cloud.path("name").asText("Atlassian Cloud"));
        metadata.put("siteUrl", cloud.path("url").asText(""));
        metadata.put("oauth", true);
        return connections.upsert(state.workspaceId(), state.provider(), cloudId,
            state.provider().equals("JIRA") ? "Jira · " + metadata.get("siteName") : "Confluence · " + metadata.get("siteName"),
            state.scopes(), metadata, json.write(tokenBundle));
    }

    @Scheduled(cron = "0 17 * * * *")
    public void cleanupExpiredStates() {
        states.deleteExpired();
    }

    private JsonNode selectCloud(JsonNode resources, Map<String, Object> selection) {
        if (resources == null || !resources.isArray() || resources.isEmpty()) {
            throw new IllegalStateException("The Atlassian account exposes no accessible sites");
        }
        String selectedCloudId = String.valueOf(selection.getOrDefault("cloudId", ""));
        if (!selectedCloudId.isBlank()) {
            for (JsonNode resource : resources) if (selectedCloudId.equals(resource.path("id").asText())) return resource;
            throw new IllegalArgumentException("The selected Atlassian site is no longer accessible");
        }
        return resources.get(0);
    }

    private static List<String> defaultScopes(String provider) {
        if (provider.equals("JIRA")) return List.of("read:jira-work", "read:jira-user", "offline_access");
        return List.of("read:confluence-content.all", "read:confluence-space.summary", "offline_access");
    }

    private static String randomState() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    public record AuthorizationStart(String authorizationUrl, Instant expiresAt) {}
}
