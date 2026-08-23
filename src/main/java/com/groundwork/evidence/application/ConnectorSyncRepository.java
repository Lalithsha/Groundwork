package com.groundwork.evidence.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ConnectorSyncRepository {
    private final JdbcTemplate jdbc;

    public ConnectorSyncRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID start(UUID connectionId, UUID workspaceId, String resourceType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO connector_sync_runs (id, connection_id, workspace_id, resource_type, cursor_before)
            VALUES (?, ?, ?, ?, (SELECT cursor_value FROM connector_sync_cursors
                                  WHERE connection_id = ? AND resource_type = ?))
            """, id, connectionId, workspaceId, resourceType, connectionId, resourceType);
        return id;
    }

    @Transactional
    public void complete(UUID runId, String resourceType, UUID connectionId, int discovered,
            int indexed, int tombstoned, int failures, String cursorAfter) {
        String status = failures == 0 ? "COMPLETED" : indexed > 0 ? "PARTIAL" : "FAILED";
        jdbc.update("""
            UPDATE connector_sync_runs SET status = ?, cursor_after = ?, discovered_count = ?,
                indexed_count = ?, tombstoned_count = ?, failure_count = ?, completed_at = now()
            WHERE id = ?
            """, status, cursorAfter, discovered, indexed, tombstoned, failures, runId);
        if (failures == 0) {
            jdbc.update("""
                INSERT INTO connector_sync_cursors (connection_id, resource_type, cursor_value, last_success_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (connection_id, resource_type) DO UPDATE SET
                    cursor_value = EXCLUDED.cursor_value, last_success_at = now(),
                    last_error = NULL, updated_at = now()
                """, connectionId, resourceType, cursorAfter);
        }
    }

    @Transactional
    public void fail(UUID runId, UUID connectionId, String resourceType, String message) {
        String safe = truncate(message);
        jdbc.update("UPDATE connector_sync_runs SET status = 'FAILED', failure_count = failure_count + 1, " +
            "error_message = ?, completed_at = now() WHERE id = ?", safe, runId);
        jdbc.update("""
            INSERT INTO connector_sync_cursors (connection_id, resource_type, last_error)
            VALUES (?, ?, ?)
            ON CONFLICT (connection_id, resource_type) DO UPDATE SET
                last_error = EXCLUDED.last_error, updated_at = now()
            """, connectionId, resourceType, safe);
    }

    public List<Map<String, Object>> findByConnection(UUID workspaceId, UUID connectionId) {
        return jdbc.query("""
            SELECT id, resource_type, status, cursor_before, cursor_after, discovered_count,
                   indexed_count, tombstoned_count, failure_count, error_message,
                   started_at, completed_at
            FROM connector_sync_runs WHERE workspace_id = ? AND connection_id = ?
            ORDER BY started_at DESC LIMIT 50
            """, (rs, rowNum) -> Map.ofEntries(
                Map.entry("id", rs.getObject("id", UUID.class)),
                Map.entry("resourceType", rs.getString("resource_type")),
                Map.entry("status", rs.getString("status")),
                Map.entry("cursorBefore", nullable(rs.getString("cursor_before"))),
                Map.entry("cursorAfter", nullable(rs.getString("cursor_after"))),
                Map.entry("discovered", rs.getInt("discovered_count")),
                Map.entry("indexed", rs.getInt("indexed_count")),
                Map.entry("tombstoned", rs.getInt("tombstoned_count")),
                Map.entry("failures", rs.getInt("failure_count")),
                Map.entry("error", nullable(rs.getString("error_message"))),
                Map.entry("startedAt", rs.getTimestamp("started_at").toInstant()),
                Map.entry("completedAt", instantOrEmpty(rs.getTimestamp("completed_at")))
            ), workspaceId, connectionId);
    }

    private static Object nullable(String value) { return value == null ? "" : value; }
    private static Object instantOrEmpty(Timestamp value) { return value == null ? "" : value.toInstant(); }
    private static String truncate(String value) {
        if (value == null || value.isBlank()) return "Connector synchronization failed";
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
