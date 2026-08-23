package com.groundwork.application;

import com.groundwork.domain.model.Workspace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkspaceRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkspaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Workspace> rowMapper = (rs, rowNum) -> new Workspace(
        UUID.fromString(rs.getString("id")),
        rs.getString("name"),
        rs.getString("description"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant()
    );

    public Workspace save(String name, String description) {
        return save(name, description, null);
    }

    @Transactional
    public Workspace save(String name, String description, String ownerEmail) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        UUID ownerId = ownerEmail == null ? null : jdbcTemplate.queryForList(
            "SELECT id FROM users WHERE LOWER(email) = LOWER(?)", UUID.class, ownerEmail).stream().findFirst().orElse(null);
        String sql = """
            INSERT INTO workspaces (id, name, description, owner_user_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql, id, name, description, ownerId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        if (ownerId != null) {
            jdbcTemplate.update("INSERT INTO workspace_memberships (workspace_id, user_id, role) VALUES (?, ?, 'OWNER')", id, ownerId);
        }
        return new Workspace(id, name, description, now, now);
    }

    public List<Workspace> findAll() {
        String sql = "SELECT id, name, description, created_at, updated_at FROM workspaces ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<Workspace> findAllForUser(String email) {
        String sql = """
            SELECT workspace.id, workspace.name, workspace.description, workspace.created_at, workspace.updated_at
            FROM workspaces workspace
            JOIN workspace_memberships membership ON membership.workspace_id = workspace.id
            JOIN users account ON account.id = membership.user_id
            WHERE LOWER(account.email) = LOWER(?)
            ORDER BY workspace.created_at DESC
            """;
        return jdbcTemplate.query(sql, rowMapper, email);
    }

    public Optional<Workspace> findById(UUID id) {
        String sql = "SELECT id, name, description, created_at, updated_at FROM workspaces WHERE id = ?";
        List<Workspace> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public boolean deleteById(UUID id) {
        String sql = "DELETE FROM workspaces WHERE id = ?";
        return jdbcTemplate.update(sql, id) > 0;
    }
}
