package com.groundwork.evidence.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.groundwork.application.Hashing;
import com.groundwork.evidence.domain.ConnectorConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GithubWebhookService {
    private static final Pattern DELIVERY_ID = Pattern.compile("[A-Za-z0-9-]{8,100}");
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
        "pull_request", "pull_request_review", "check_suite", "check_run", "workflow_run", "ping");

    private final String secret;
    private final int maxPayloadBytes;
    private final ConnectorRepository connections;
    private final IntegrationEventRepository events;
    private final EvidenceJson json;

    public GithubWebhookService(
            ConnectorRepository connections,
            IntegrationEventRepository events,
            EvidenceJson json,
            @Value("${groundwork.integrations.github.webhook-secret:local-github-webhook-secret-change-me}") String secret,
            @Value("${groundwork.integrations.github.max-webhook-bytes:2097152}") int maxPayloadBytes) {
        this.connections = connections;
        this.events = events;
        this.json = json;
        this.secret = secret;
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public IntegrationEventRepository.AcceptResult accept(String deliveryId, String eventType,
            String signature, String payload) {
        validateHeaders(deliveryId, eventType);
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxPayloadBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "GitHub webhook payload is too large");
        }
        if (!verify(signature, bytes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "GitHub webhook signature is invalid");
        }
        JsonNode root = json.tree(payload);
        if ("ping".equals(eventType)) {
            String installationId = root.path("installation").path("id").asText();
            ConnectorConnection connection = requireInstallation(installationId);
            return events.accept(connection.workspaceId(), connection.id(), "GITHUB", deliveryId,
                eventType, root.path("action").asText(null), Hashing.sha256(payload), payload);
        }
        String installationId = root.path("installation").path("id").asText();
        ConnectorConnection connection = requireInstallation(installationId);
        return events.accept(connection.workspaceId(), connection.id(), "GITHUB", deliveryId,
            eventType, root.path("action").asText(null), Hashing.sha256(payload), payload);
    }

    public String signForDemo(String payload) {
        return "sha256=" + hmac(payload.getBytes(StandardCharsets.UTF_8));
    }

    private ConnectorConnection requireInstallation(String installationId) {
        if (installationId == null || installationId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub installation ID is missing");
        }
        return connections.findGithubInstallation(installationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "GitHub installation is not registered in Groundwork"));
    }

    private void validateHeaders(String deliveryId, String eventType) {
        if (deliveryId == null || !DELIVERY_ID.matcher(deliveryId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub delivery ID is invalid");
        }
        String normalized = eventType == null ? "" : eventType.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EVENTS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.ACCEPTED, "GitHub event is not supported");
        }
    }

    private boolean verify(String signature, byte[] payload) {
        if (signature == null || !signature.startsWith("sha256=")) return false;
        byte[] expected;
        byte[] provided;
        try {
            expected = HexFormat.of().parseHex(hmac(payload));
            provided = HexFormat.of().parseHex(signature.substring(7));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided);
    }

    private String hmac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
