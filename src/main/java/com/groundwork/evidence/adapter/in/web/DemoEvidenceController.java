package com.groundwork.evidence.adapter.in.web;

import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.evidence.application.DemoEvidenceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/demo")
public class DemoEvidenceController {
    private final DemoEvidenceService demo;
    private final WorkspaceAccessService access;

    public DemoEvidenceController(DemoEvidenceService demo, WorkspaceAccessService access) {
        this.demo = demo;
        this.access = access;
    }

    @PostMapping("/evidence")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DemoEvidenceService.DemoResult seed(@PathVariable UUID workspaceId) {
        access.requireEditor(workspaceId);
        return demo.seed(workspaceId);
    }
}
