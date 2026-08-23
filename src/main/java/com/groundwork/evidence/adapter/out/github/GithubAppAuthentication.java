package com.groundwork.evidence.adapter.out.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Component
public class GithubAppAuthentication {
    private final String appId;
    private final PrivateKey privateKey;
    private final RestClient rest;
    private final ObjectMapper mapper;

    public GithubAppAuthentication(
            RestClient.Builder builder,
            ObjectMapper mapper,
            @Value("${groundwork.integrations.github.api-url:https://api.github.com}") String apiUrl,
            @Value("${groundwork.integrations.github.app-id:}") String appId,
            @Value("${groundwork.integrations.github.private-key-pkcs8-base64:}") String privateKeyBase64) {
        this.appId = appId;
        this.privateKey = decodePrivateKey(privateKeyBase64);
        this.rest = builder.baseUrl(apiUrl)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return appId != null && !appId.isBlank() && privateKey != null;
    }

    public Optional<String> installationToken(String installationId) {
        if (!isConfigured() || installationId == null || installationId.isBlank()) return Optional.empty();
        String response = rest.post()
            .uri("/app/installations/{installationId}/access_tokens", installationId)
            .header("Authorization", "Bearer " + appJwt())
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}")
            .retrieve()
            .body(String.class);
        try {
            JsonNode node = mapper.readTree(response);
            String token = node.path("token").asText();
            return token.isBlank() ? Optional.empty() : Optional.of(token);
        } catch (Exception exception) {
            throw new IllegalStateException("GitHub installation-token response was invalid", exception);
        }
    }

    private String appJwt() {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(appId)
            .issuedAt(Date.from(now.minusSeconds(30)))
            .expiration(Date.from(now.plusSeconds(8 * 60)))
            .signWith(privateKey)
            .compact();
    }

    private PrivateKey decodePrivateKey(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded.replaceAll("\\s", ""));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("GitHub private key must be an RSA PKCS#8 key encoded as base64", exception);
        }
    }
}
