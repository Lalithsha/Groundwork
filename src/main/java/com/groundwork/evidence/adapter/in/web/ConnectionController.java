package com.groundwork.evidence.adapter.in.web;

import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.evidence.application.ConnectorRepository;
import com.groundwork.evidence.application.ConnectorSyncRepository;
import com.groundwork.evidence.application.ConnectorSyncService;
import com.groundwork.evidence.application.EvidenceCatalogRepository;
import com.groundwork.evidence.application.LegacyDocumentEvidenceBridge;
import com.groundwork.evidence.application.ProductAnalyticsService;
import com.groundwork.evidence.application.port.out.KnowledgeSourcePort;
import com.groundwork.evidence.domain.ConnectorConnection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ConnectionController {
    private static final Set<String> PROVIDERS = Set.of("GITHUB", "JIRA", "CONFLUENCE", "MANUAL", "DEMO");
    private final ConnectorRepository connections;
    private final WorkspaceAccessService access;
    private final ConnectorSyncService sync;
    private final ConnectorSyncRepository syncRuns;
    private final EvidenceCatalogRepository catalog;
    private final LegacyDocumentEvidenceBridge documentBridge;
    private final ProductAnalyticsService analytics;

    public ConnectionController(ConnectorRepository connections, WorkspaceAccessService access,
            ConnectorSyncService sync, ConnectorSyncRepository syncRuns, EvidenceCatalogRepository catalog,
            LegacyDocumentEvidenceBridge documentBridge, ProductAnalyticsService analytics) {
        this.connections = connections;
        this.access = access;
        this.sync = sync;
        this.syncRuns = syncRuns;
        this.catalog = catalog;
        this.documentBridge = documentBridge;
        this.analytics = analytics;
    }

    @GetMapping("/workspaces/{workspaceId}/connections")
    public List<ConnectorConnection> list(@PathVariable UUID workspaceId) {
        access.requireViewer(workspaceId);
        return connections.findByWorkspace(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectorConnection create(@PathVariable UUID workspaceId, @Valid @RequestBody ConnectionRequest request) {
        access.requireAdmin(workspaceId);
        String provider = request.provider().toUpperCase(java.util.Locale.ROOT);
        if (!PROVIDERS.contains(provider)) throw new IllegalArgumentException("Unsupported connection provider");
        ConnectorConnection connection = connections.upsert(workspaceId, provider, request.externalAccountId(), request.displayName(),
            request.scopes() == null ? List.of() : request.scopes(),
            request.metadata() == null ? Map.of() : request.metadata(), request.credential());
        analytics.record(workspaceId, "connection_activated", "CONNECTION", connection.id(),
            Map.of("provider", provider));
        return connection;
    }

    @PostMapping("/workspaces/{workspaceId}/connections/{connectionId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ConnectorSyncService.SyncResult sync(@PathVariable UUID workspaceId, @PathVariable UUID connectionId) {
        access.requireEditor(workspaceId);
        return sync.sync(workspaceId, connectionId);
    }

    @PostMapping("/workspaces/{workspaceId}/connections/{connectionId}/evidence")
    public ConnectorSyncService.SyncResult ingest(@PathVariable UUID workspaceId, @PathVariable UUID connectionId,
            @Valid @RequestBody ManualEvidenceRequest request) {
        access.requireEditor(workspaceId);
        List<KnowledgeSourcePort.SourceArtifact> artifacts = request.artifacts().stream()
            .map(item -> new KnowledgeSourcePort.SourceArtifact(item.externalId(), item.artifactType(), item.title(),
                item.canonicalUrl(), item.sourceVersion(), item.content(),
                item.metadata() == null ? Map.of() : item.metadata(),
                item.sourceAcl() == null ? Map.of("workspaceScoped", true) : item.sourceAcl()))
            .toList();
        return sync.ingest(workspaceId, connectionId, artifacts, request.reconcile());
    }

    @GetMapping("/workspaces/{workspaceId}/connections/{connectionId}/sync-runs")
    public List<Map<String, Object>> syncRuns(@PathVariable UUID workspaceId, @PathVariable UUID connectionId) {
        access.requireViewer(workspaceId);
        connections.findById(connectionId).filter(value -> value.workspaceId().equals(workspaceId))
            .orElseThrow(() -> new IllegalArgumentException("Connection was not found"));
        return syncRuns.findByConnection(workspaceId, connectionId);
    }

    @PostMapping("/workspaces/{workspaceId}/evidence/import-documents")
    public LegacyDocumentEvidenceBridge.ImportResult importDocuments(@PathVariable UUID workspaceId) {
        access.requireEditor(workspaceId);
        return documentBridge.importWorkspace(workspaceId);
    }

    @DeleteMapping("/connections/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID connectionId) {
        ConnectorConnection connection = connections.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Connection was not found"));
        access.requireAdmin(connection.workspaceId());
        if (!connections.revoke(connectionId, connection.workspaceId())) {
            throw new IllegalArgumentException("Connection was not found");
        }
        catalog.markConnectionInaccessible(connection.workspaceId(), connectionId);
        analytics.record(connection.workspaceId(), "connection_revoked", "CONNECTION", connectionId,
            Map.of("provider", connection.provider()));
    }

    public record ConnectionRequest(@NotBlank String provider, @NotBlank String externalAccountId,
            @NotBlank String displayName, List<String> scopes, Map<String, Object> metadata, String credential) {}
    public record ManualEvidenceRequest(@NotNull List<ManualEvidenceItem> artifacts, boolean reconcile) {}
    public record ManualEvidenceItem(@NotBlank String externalId, @NotBlank String artifactType,
            @NotBlank String title, String canonicalUrl, @NotBlank String sourceVersion,
            @NotBlank String content, Map<String, Object> metadata, Map<String, Object> sourceAcl) {}
}
