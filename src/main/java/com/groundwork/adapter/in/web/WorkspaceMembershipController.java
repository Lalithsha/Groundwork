package com.groundwork.adapter.in.web;

import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.application.WorkspaceMembershipService;
import com.groundwork.application.WorkspaceMembershipService.Member;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
public class WorkspaceMembershipController {
    private final WorkspaceMembershipService memberships;
    private final WorkspaceAccessService access;

    public WorkspaceMembershipController(WorkspaceMembershipService memberships, WorkspaceAccessService access) {
        this.memberships = memberships;
        this.access = access;
    }

    public record PutMemberRequest(@Email @NotBlank String email, String role) {}

    @GetMapping
    public List<Member> list(@PathVariable UUID workspaceId) {
        access.requireAdmin(workspaceId);
        return memberships.list(workspaceId);
    }

    @PutMapping
    public ResponseEntity<Member> put(@PathVariable UUID workspaceId, @Valid @RequestBody PutMemberRequest request) {
        if ("OWNER".equalsIgnoreCase(request.role()) || memberships.isOwner(workspaceId, request.email())) access.requireOwner(workspaceId);
        else access.requireAdmin(workspaceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(memberships.put(workspaceId, request.email(), request.role()));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(@PathVariable UUID workspaceId, @PathVariable UUID userId) {
        if (memberships.isOwner(workspaceId, userId)) access.requireOwner(workspaceId);
        else access.requireAdmin(workspaceId);
        memberships.remove(workspaceId, userId);
        return ResponseEntity.noContent().build();
    }
}
