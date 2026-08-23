package com.groundwork.evidence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.evidence.domain.ConnectorConnection;
import com.groundwork.evidence.domain.WebhookDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubWebhookServiceTest {
    @Test
    void acceptsValidSignatureAndRejectsTamperedPayload() {
        ConnectorRepository connections = mock(ConnectorRepository.class);
        IntegrationEventRepository events = mock(IntegrationEventRepository.class);
        UUID workspaceId = UUID.randomUUID(); UUID connectionId = UUID.randomUUID();
        ConnectorConnection connection = new ConnectorConnection(connectionId, workspaceId, "GITHUB", "42",
            "test", "ACTIVE", List.of(), Map.of(), null, null, Instant.now(), Instant.now());
        when(connections.findGithubInstallation("42")).thenReturn(Optional.of(connection));
        WebhookDelivery delivery = new WebhookDelivery(UUID.randomUUID(), connectionId, "GITHUB",
            "delivery-123456", "pull_request", "opened", true, "digest", null,
            "RECEIVED", null, Instant.now(), null);
        when(events.accept(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new IntegrationEventRepository.AcceptResult(delivery, false));
        GithubWebhookService service = new GithubWebhookService(connections, events,
            new EvidenceJson(new ObjectMapper()), "a-long-unit-test-webhook-secret", 4096);
        String payload = "{\"action\":\"opened\",\"installation\":{\"id\":42}}";
        String signature = service.signForDemo(payload);

        assertThat(service.accept("delivery-123456", "pull_request", signature, payload).duplicate()).isFalse();
        assertThatThrownBy(() -> service.accept("delivery-654321", "pull_request", signature, payload + " "))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED");
    }
}
