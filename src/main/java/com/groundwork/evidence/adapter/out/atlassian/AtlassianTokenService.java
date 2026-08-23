package com.groundwork.evidence.adapter.out.atlassian;

import com.fasterxml.jackson.databind.JsonNode;
import com.groundwork.evidence.application.ConnectorRepository;
import com.groundwork.evidence.application.EvidenceJson;
import com.groundwork.evidence.domain.ConnectorConnection;
import com.groundwork.evidence.application.port.out.AtlassianOAuthPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AtlassianTokenService implements AtlassianOAuthPort {
    private final ConnectorRepository connections;
    private final EvidenceJson json;
    private final RestClient rest;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;

    public AtlassianTokenService(ConnectorRepository connections, EvidenceJson json, RestClient.Builder builder,
            @Value("${groundwork.integrations.atlassian.client-id:}") String clientId,
            @Value("${groundwork.integrations.atlassian.client-secret:}") String clientSecret,
            @Value("${groundwork.integrations.atlassian.token-url:https://auth.atlassian.com/oauth/token}") String tokenUrl) {
        this.connections = connections;
        this.json = json;
        this.rest = builder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
    }

    public String accessToken(ConnectorConnection connection) {
        String stored = connections.credential(connection.id())
            .orElseThrow(() -> new IllegalStateException("Connector credentials are unavailable; reconnect the source"));
        Map<String, Object> bundle = json.map(stored);
        Instant expiresAt = parseInstant(bundle.get("expiresAt"));
        if (expiresAt != null && expiresAt.isAfter(Instant.now().plusSeconds(90))) {
            return String.valueOf(bundle.get("accessToken"));
        }
        String refreshToken = String.valueOf(bundle.getOrDefault("refreshToken", ""));
        if (refreshToken.isBlank()) throw new IllegalStateException("Connector access expired; reconnect the source");
        JsonNode refreshed = token(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken));
        Map<String, Object> rotated = tokenBundle(refreshed, refreshToken);
        connections.upsert(connection.workspaceId(), connection.provider(), connection.externalAccountId(),
            connection.displayName(), connection.scopes(), connection.metadata(), json.write(rotated));
        return String.valueOf(rotated.get("accessToken"));
    }

    @Override
    public JsonNode exchangeAuthorizationCode(String code, String redirectUri) {
        return token(Map.of("grant_type", "authorization_code", "code", code, "redirect_uri", redirectUri));
    }

    @Override
    public Map<String, Object> tokenBundle(JsonNode response, String priorRefreshToken) {
        String access = response.path("access_token").asText();
        if (access.isBlank()) throw new IllegalStateException("Atlassian did not return an access token");
        long expiresIn = Math.max(60, response.path("expires_in").asLong(3600));
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("accessToken", access);
        bundle.put("refreshToken", response.path("refresh_token").asText(priorRefreshToken == null ? "" : priorRefreshToken));
        bundle.put("expiresAt", Instant.now().plusSeconds(expiresIn).toString());
        bundle.put("tokenType", response.path("token_type").asText("Bearer"));
        return bundle;
    }

    @Override
    public JsonNode accessibleResources(String accessToken) {
        JsonNode response = rest.get().uri("https://api.atlassian.com/oauth/token/accessible-resources")
            .header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class);
        if (response == null) throw new IllegalStateException("Atlassian accessible resources returned no response");
        return response;
    }

    private JsonNode token(Map<String, String> values) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("Atlassian OAuth is not configured");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        values.forEach(form::add);
        JsonNode response = rest.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON).body(form).retrieve().body(JsonNode.class);
        if (response == null) throw new IllegalStateException("Atlassian token exchange returned no response");
        return response;
    }

    private static Instant parseInstant(Object value) {
        if (value == null) return null;
        try { return Instant.parse(String.valueOf(value)); }
        catch (RuntimeException ignored) { return null; }
    }
}
