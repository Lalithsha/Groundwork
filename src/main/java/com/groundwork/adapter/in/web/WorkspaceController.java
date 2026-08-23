package com.groundwork.adapter.in.web;

import com.groundwork.application.WorkspaceRepository;
import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.application.CurrentUser;
import com.groundwork.domain.model.Workspace;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAccessService access;
    private final CurrentUser currentUser;

    public WorkspaceController(WorkspaceRepository workspaceRepository, WorkspaceAccessService access, CurrentUser currentUser) {
        this.workspaceRepository = workspaceRepository;
        this.access = access;
        this.currentUser = currentUser;
    }

    public record CreateWorkspaceRequest(String name, String description) {}

    @PostMapping
    public ResponseEntity<Workspace> createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Workspace created = workspaceRepository.save(request.name().trim(), request.description(), currentUser.email().orElse(null));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Workspace>> getAllWorkspaces() {
        return ResponseEntity.ok(currentUser.email().map(workspaceRepository::findAllForUser)
            .orElseGet(workspaceRepository::findAll));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Workspace> getWorkspaceById(@PathVariable UUID id) {
        access.requireViewer(id);
        return workspaceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable UUID id) {
        access.requireAdmin(id);
        boolean deleted = workspaceRepository.deleteById(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
