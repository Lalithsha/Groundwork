package com.groundwork.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkspaceMembershipService {
    private static final Set<String> ROLES = Set.of("OWNER", "ADMIN", "EDITOR", "VIEWER");
    private final JdbcTemplate jdbcTemplate;

    public WorkspaceMembershipService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Member> list(UUID workspaceId) {
        return jdbcTemplate.query("""
            SELECT account.id, account.email, membership.role, membership.created_at
            FROM workspace_memberships membership
            JOIN users account ON account.id = membership.user_id
            WHERE membership.workspace_id = ? ORDER BY membership.created_at
            """, (rs, rowNum) -> new Member(rs.getObject("id", UUID.class), rs.getString("email"),
                rs.getString("role"), rs.getTimestamp("created_at").toInstant()), workspaceId);
    }

    @Transactional
    public Member put(UUID workspaceId, String email, String requestedRole) {
        String role = normalizeRole(requestedRole);
        UUID userId = jdbcTemplate.query("SELECT id FROM users WHERE LOWER(email) = LOWER(?)",
            (rs, rowNum) -> rs.getObject(1, UUID.class), email.strip()).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User must register before joining a workspace"));
        if (!"OWNER".equals(role) && isOwner(workspaceId, userId) && ownerCount(workspaceId) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A workspace must retain at least one owner");
        }
        jdbcTemplate.update("""
            INSERT INTO workspace_memberships (workspace_id, user_id, role) VALUES (?, ?, ?)
            ON CONFLICT (workspace_id, user_id) DO UPDATE SET role = EXCLUDED.role
            """, workspaceId, userId, role);
        return list(workspaceId).stream().filter(member -> member.userId().equals(userId)).findFirst().orElseThrow();
    }

    @Transactional
    public void remove(UUID workspaceId, UUID userId) {
        String role = jdbcTemplate.query("SELECT role FROM workspace_memberships WHERE workspace_id = ? AND user_id = ?",
            (rs, rowNum) -> rs.getString(1), workspaceId, userId).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace member not found"));
        if ("OWNER".equals(role)) {
            if (ownerCount(workspaceId) <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A workspace must retain at least one owner");
            }
        }
        jdbcTemplate.update("DELETE FROM workspace_memberships WHERE workspace_id = ? AND user_id = ?", workspaceId, userId);
    }

    private int ownerCount(UUID workspaceId) {
        Integer owners = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM workspace_memberships WHERE workspace_id = ? AND role = 'OWNER'", Integer.class, workspaceId);
        return owners == null ? 0 : owners;
    }

    public boolean isOwner(UUID workspaceId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM workspace_memberships
            WHERE workspace_id = ? AND user_id = ? AND role = 'OWNER'
            """, Integer.class, workspaceId, userId);
        return count != null && count > 0;
    }

    public boolean isOwner(UUID workspaceId, String email) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM workspace_memberships membership
            JOIN users account ON account.id = membership.user_id
            WHERE membership.workspace_id = ? AND LOWER(account.email) = LOWER(?) AND membership.role = 'OWNER'
            """, Integer.class, workspaceId, email.strip());
        return count != null && count > 0;
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "VIEWER" : role.strip().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be OWNER, ADMIN, EDITOR, or VIEWER");
        }
        return normalized;
    }

    public record Member(UUID userId, String email, String role, java.time.Instant createdAt) {}
}
