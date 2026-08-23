package com.groundwork.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
public class WorkspaceAccessService {
    private static final Map<String, Integer> ROLE_LEVEL = Map.of(
        "VIEWER", 1, "EDITOR", 2, "ADMIN", 3, "OWNER", 4);

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUser currentUser;
    private final boolean securityEnabled;

    public WorkspaceAccessService(JdbcTemplate jdbcTemplate, CurrentUser currentUser,
            @Value("${groundwork.security.enabled:false}") boolean securityEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
        this.securityEnabled = securityEnabled;
    }

    public void requireViewer(UUID workspaceId) { requireRole(workspaceId, "VIEWER"); }
    public void requireEditor(UUID workspaceId) { requireRole(workspaceId, "EDITOR"); }
    public void requireAdmin(UUID workspaceId) { requireRole(workspaceId, "ADMIN"); }
    public void requireOwner(UUID workspaceId) { requireRole(workspaceId, "OWNER"); }
    public boolean securityEnabled() { return securityEnabled; }

    public void requireRole(UUID workspaceId, String requiredRole) {
        if (!securityEnabled || currentUser.isSystemAdmin()) return;
        if (workspaceId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceId is required");
        String email = currentUser.email()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required"));
        var roles = jdbcTemplate.queryForList("""
            SELECT membership.role FROM workspace_memberships membership
            JOIN users account ON account.id = membership.user_id
            WHERE membership.workspace_id = ? AND LOWER(account.email) = LOWER(?)
            """, String.class, workspaceId, email);
        if (roles.isEmpty() || ROLE_LEVEL.getOrDefault(roles.getFirst(), 0) < ROLE_LEVEL.get(requiredRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient workspace permission");
        }
    }
}
