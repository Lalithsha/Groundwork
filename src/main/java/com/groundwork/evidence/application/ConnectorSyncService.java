package com.groundwork.evidence.application;

import com.groundwork.evidence.application.port.out.KnowledgeSourcePort;
import com.groundwork.evidence.domain.ConnectorConnection;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ConnectorSyncService {
    private static final Set<String> ARTIFACT_TYPES = Set.of("DOCUMENT", "REQUIREMENT", "ISSUE", "ADR", "RUNBOOK",
        "INCIDENT", "TEST_RUN", "BUILD", "DEPLOYMENT", "API_CONTRACT", "SECURITY_SCAN");
    private final ConnectorRepository connections;
    private final ConnectorSyncRepository runs;
    private final EvidenceCatalogRepository catalog;
    private final EvidenceIndexingService indexing;
    private final List<KnowledgeSourcePort> sources;
    private final ProductAnalyticsService analytics;

    public ConnectorSyncService(ConnectorRepository connections, ConnectorSyncRepository runs,
            EvidenceCatalogRepository catalog, EvidenceIndexingService indexing,
            List<KnowledgeSourcePort> sources, ProductAnalyticsService analytics) {
        this.connections = connections;
        this.runs = runs;
        this.catalog = catalog;
        this.indexing = indexing;
        this.sources = sources;
        this.analytics = analytics;
    }

    public SyncResult sync(UUID workspaceId, UUID connectionId) {
        ConnectorConnection connection = requireActive(workspaceId, connectionId);
        KnowledgeSourcePort source = sources.stream().filter(value -> value.supports(connection.provider()))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("This connector does not support API synchronization"));
        UUID runId = runs.start(connection.id(), workspaceId, connection.provider());
        List<KnowledgeSourcePort.SourceArtifact> artifacts;
        try {
            artifacts = source.fetch(connection);
        } catch (RuntimeException failure) {
            failRun(runId, connection, failure);
            throw failure;
        }
        return ingest(workspaceId, connection, artifacts, true, runId);
    }

    public SyncResult ingest(UUID workspaceId, UUID connectionId,
            List<KnowledgeSourcePort.SourceArtifact> artifacts, boolean reconcile) {
        return ingest(workspaceId, requireActive(workspaceId, connectionId), artifacts, reconcile);
    }

    private SyncResult ingest(UUID workspaceId, ConnectorConnection connection,
            List<KnowledgeSourcePort.SourceArtifact> artifacts, boolean reconcile) {
        UUID runId = runs.start(connection.id(), workspaceId, connection.provider());
        return ingest(workspaceId, connection, artifacts, reconcile, runId);
    }

    private SyncResult ingest(UUID workspaceId, ConnectorConnection connection,
            List<KnowledgeSourcePort.SourceArtifact> artifacts, boolean reconcile, UUID runId) {
        int indexed = 0;
        int failures = 0;
        Set<String> seenTypes = new java.util.LinkedHashSet<>();
        try {
            for (KnowledgeSourcePort.SourceArtifact item : artifacts) {
                try {
                    String type = item.artifactType().toUpperCase(java.util.Locale.ROOT);
                    if (!ARTIFACT_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported evidence artifact type " + type);
                    var upsert = indexing.upsert(workspaceId, connection.id(), connection.provider(), item.externalId(),
                        type, item.title(), item.canonicalUrl(), item.sourceAcl(), item.sourceVersion(), item.content(),
                        item.metadata());
                    catalog.markSeenInSync(workspaceId, upsert.artifact().id(), runId);
                    seenTypes.add(type);
                    indexed++;
                } catch (RuntimeException itemFailure) {
                    failures++;
                }
            }
            int tombstoned = 0;
            if (reconcile && failures == 0) {
                for (String type : seenTypes) tombstoned += catalog.reconcileMissing(workspaceId, connection.id(), runId, type);
            }
            String cursor = Instant.now().toString();
            runs.complete(runId, connection.provider(), connection.id(), artifacts.size(), indexed,
                tombstoned, failures, cursor);
            if (failures == 0) connections.markSyncSuccess(connection.id());
            else connections.markFailure(connection.id(), failures + " evidence item(s) failed to synchronize");
            analytics.record(workspaceId, "connector_synced", "CONNECTION", connection.id(),
                Map.of("provider", connection.provider(), "indexed", indexed, "failures", failures));
            return new SyncResult(runId, failures == 0 ? "COMPLETED" : indexed > 0 ? "PARTIAL" : "FAILED",
                artifacts.size(), indexed, tombstoned, failures);
        } catch (RuntimeException failure) {
            failRun(runId, connection, failure);
            throw failure;
        }
    }

    private void failRun(UUID runId, ConnectorConnection connection, RuntimeException failure) {
        runs.fail(runId, connection.id(), connection.provider(), failure.getMessage());
        connections.markFailure(connection.id(), failure.getMessage());
    }

    private ConnectorConnection requireActive(UUID workspaceId, UUID connectionId) {
        return connections.findById(connectionId)
            .filter(value -> value.workspaceId().equals(workspaceId))
            .filter(value -> !"REVOKED".equals(value.status()))
            .orElseThrow(() -> new IllegalArgumentException("Active connector was not found in this workspace"));
    }

    public record SyncResult(UUID runId, String status, int discovered, int indexed,
                             int tombstoned, int failures) {}
}
