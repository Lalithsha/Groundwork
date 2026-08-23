package com.groundwork.evidence.application;

import com.groundwork.evidence.application.port.out.KnowledgeSourcePort;
import com.groundwork.evidence.domain.ConnectorConnection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectorSyncServiceTest {
    @Test
    void recordsProviderFetchFailureAsDurableFailedRunAndDegradedConnection() {
        ConnectorRepository connections = mock(ConnectorRepository.class);
        ConnectorSyncRepository runs = mock(ConnectorSyncRepository.class);
        KnowledgeSourcePort source = mock(KnowledgeSourcePort.class);
        UUID workspaceId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        ConnectorConnection connection = new ConnectorConnection(connectionId, workspaceId, "JIRA", "cloud-1",
            "Jira", "ACTIVE", List.of(), Map.of(), null, null, now, now);
        when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
        when(source.supports("JIRA")).thenReturn(true);
        when(runs.start(connectionId, workspaceId, "JIRA")).thenReturn(runId);
        when(source.fetch(connection)).thenThrow(new IllegalStateException("provider unavailable"));
        ConnectorSyncService service = new ConnectorSyncService(connections, runs,
            mock(EvidenceCatalogRepository.class), mock(EvidenceIndexingService.class), List.of(source),
            mock(ProductAnalyticsService.class));

        assertThatThrownBy(() -> service.sync(workspaceId, connectionId))
            .isInstanceOf(IllegalStateException.class).hasMessage("provider unavailable");
        verify(runs).fail(runId, connectionId, "JIRA", "provider unavailable");
        verify(connections).markFailure(connectionId, "provider unavailable");
    }
}
