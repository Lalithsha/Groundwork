package com.groundwork.evidence.adapter.in.web;

import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.evidence.application.ReleaseRecordRepository;
import com.groundwork.evidence.application.ReleaseRecordService;
import com.groundwork.evidence.application.ReleaseRecordExportService;
import com.groundwork.evidence.domain.ReleaseRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReleaseRecordController {
    private final ReleaseRecordRepository releases;
    private final ReleaseRecordService service;
    private final WorkspaceAccessService access;
    private final ReleaseRecordExportService exports;

    public ReleaseRecordController(ReleaseRecordRepository releases, ReleaseRecordService service,
            WorkspaceAccessService access, ReleaseRecordExportService exports) {
        this.releases = releases;
        this.service = service;
        this.access = access;
        this.exports = exports;
    }

    @GetMapping("/workspaces/{workspaceId}/releases")
    public List<ReleaseRecord> list(@PathVariable UUID workspaceId) {
        access.requireViewer(workspaceId);
        return releases.findByWorkspace(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/releases")
    public ReleaseRecord create(@PathVariable UUID workspaceId, @Valid @RequestBody ReleaseRequest request) {
        access.requireEditor(workspaceId);
        return service.freeze(workspaceId, request.name(), request.repositoryFullName(), request.baseRef(),
            request.headRef(), request.changeSetIds());
    }

    @GetMapping("/workspaces/{workspaceId}/releases/{releaseId}")
    public ReleaseDetail detail(@PathVariable UUID workspaceId, @PathVariable UUID releaseId) {
        access.requireViewer(workspaceId);
        ReleaseRecord release = releases.findAuthorized(workspaceId, releaseId)
            .orElseThrow(() -> new IllegalArgumentException("Release record was not found"));
        return new ReleaseDetail(release, service.verify(workspaceId, releaseId));
    }

    @GetMapping("/workspaces/{workspaceId}/releases/{releaseId}/export")
    public ResponseEntity<ReleaseRecord> export(@PathVariable UUID workspaceId, @PathVariable UUID releaseId) {
        access.requireViewer(workspaceId);
        ReleaseRecord release = releases.findAuthorized(workspaceId, releaseId)
            .orElseThrow(() -> new IllegalArgumentException("Release record was not found"));
        String filename = release.name().replaceAll("[^A-Za-z0-9._-]", "-") + "-evidence.json";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_JSON).body(release);
    }

    @GetMapping(value = "/workspaces/{workspaceId}/releases/{releaseId}/export.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> exportHtml(@PathVariable UUID workspaceId, @PathVariable UUID releaseId) {
        access.requireViewer(workspaceId);
        ReleaseRecord release = releases.findAuthorized(workspaceId, releaseId)
            .orElseThrow(() -> new IllegalArgumentException("Release record was not found"));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + safeFilename(release.name()) + "-evidence.html\"")
            .contentType(MediaType.TEXT_HTML).body(exports.html(release, service.verify(workspaceId, releaseId)));
    }

    @GetMapping(value = "/workspaces/{workspaceId}/releases/{releaseId}/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID workspaceId, @PathVariable UUID releaseId) {
        access.requireViewer(workspaceId);
        ReleaseRecord release = releases.findAuthorized(workspaceId, releaseId)
            .orElseThrow(() -> new IllegalArgumentException("Release record was not found"));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + safeFilename(release.name()) + "-evidence.pdf\"")
            .contentType(MediaType.APPLICATION_PDF).body(exports.pdf(release, service.verify(workspaceId, releaseId)));
    }

    private String safeFilename(String name) { return name.replaceAll("[^A-Za-z0-9._-]", "-"); }

    public record ReleaseRequest(@NotBlank String name, @NotBlank String repositoryFullName,
            String baseRef, @NotBlank String headRef, @NotEmpty List<UUID> changeSetIds) {}
    public record ReleaseDetail(ReleaseRecord release, ReleaseRecordService.Verification verification) {}
}
