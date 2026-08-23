package com.groundwork.evidence.adapter.in.web;

import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.evidence.application.EvidenceCatalogRepository;
import com.groundwork.evidence.application.EvidenceRetrievalService;
import com.groundwork.evidence.domain.EvidenceArtifact;
import com.groundwork.evidence.domain.EvidenceArtifactVersion;
import com.groundwork.evidence.domain.EvidenceRelationship;
import com.groundwork.evidence.domain.EvidenceSearchHit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class EvidenceCatalogController {
    private final EvidenceCatalogRepository catalog;
    private final EvidenceRetrievalService retrieval;
    private final WorkspaceAccessService access;

    public EvidenceCatalogController(EvidenceCatalogRepository catalog, EvidenceRetrievalService retrieval,
            WorkspaceAccessService access) {
        this.catalog = catalog;
        this.retrieval = retrieval;
        this.access = access;
    }

    @GetMapping("/workspaces/{workspaceId}/evidence")
    public List<EvidenceArtifact> artifacts(@PathVariable UUID workspaceId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "100") int limit) {
        access.requireViewer(workspaceId);
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        return catalog.findByWorkspace(workspaceId, type, limit);
    }

    @GetMapping("/workspaces/{workspaceId}/evidence/search")
    public List<EvidenceSearchHit> search(@PathVariable UUID workspaceId,
            @RequestParam String query, @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "true") boolean expandGraph) {
        access.requireViewer(workspaceId);
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be between 1 and 50");
        return retrieval.retrieve(workspaceId, query, limit, expandGraph);
    }

    @GetMapping("/workspaces/{workspaceId}/evidence/{artifactId}")
    public ArtifactDetail detail(@PathVariable UUID workspaceId, @PathVariable UUID artifactId) {
        access.requireViewer(workspaceId);
        EvidenceArtifact artifact = catalog.findAuthorizedById(workspaceId, artifactId)
            .orElseThrow(() -> new IllegalArgumentException("Evidence artifact was not found"));
        return new ArtifactDetail(artifact, catalog.findVersions(workspaceId, artifactId),
            catalog.relationships(workspaceId, artifactId));
    }

    public record ArtifactDetail(EvidenceArtifact artifact, List<EvidenceArtifactVersion> versions,
                                 List<EvidenceRelationship> relationships) {}
}
