package com.groundwork.evidence.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductAnalyticsService {
    private final JdbcTemplate jdbc;
    private final EvidenceJson json;
    private final CurrentUserIdResolver users;

    public ProductAnalyticsService(JdbcTemplate jdbc, EvidenceJson json, CurrentUserIdResolver users) {
        this.jdbc = jdbc;
        this.json = json;
        this.users = users;
    }

    public void record(UUID workspaceId, String name, String entityType, UUID entityId,
            Map<String, Object> properties) {
        jdbc.update("INSERT INTO product_events " +
                "(id, workspace_id, actor_user_id, event_name, entity_type, entity_id, properties) " +
                "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
            UUID.randomUUID(), workspaceId, users.optional().orElse(null), name, entityType, entityId,
            json.write(properties == null ? Map.of() : properties));
    }

    public Map<String, Object> summary(UUID workspaceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connections", count("SELECT count(*) FROM connector_connections WHERE workspace_id = ? AND status = 'ACTIVE'", workspaceId));
        result.put("changes", count("SELECT count(*) FROM change_sets WHERE workspace_id = ?", workspaceId));
        result.put("analyzedChanges", count("SELECT count(*) FROM change_sets WHERE workspace_id = ? AND current_analysis_status IN ('COMPLETED','PARTIAL')", workspaceId));
        result.put("openEvidenceGaps", count("SELECT count(*) FROM change_findings finding JOIN change_sets change ON change.id = finding.change_set_id WHERE change.workspace_id = ? AND finding.deterministic = true AND (finding.details->>'missing')::boolean = true", workspaceId));
        result.put("releaseRecords", count("SELECT count(*) FROM release_records WHERE workspace_id = ?", workspaceId));
        result.put("feedbackEvents", count("SELECT count(*) FROM finding_feedback_events WHERE workspace_id = ?", workspaceId));
        List<Map<String, Object>> activity = jdbc.query("""
            SELECT event_name, count(*) AS event_count, max(occurred_at) AS last_seen
            FROM product_events WHERE workspace_id = ? AND occurred_at > now() - interval '30 days'
            GROUP BY event_name ORDER BY event_count DESC, event_name
            """, (rs, rowNum) -> Map.of("eventName", rs.getString("event_name"),
                "count", rs.getLong("event_count"), "lastSeen", rs.getTimestamp("last_seen").toInstant()), workspaceId);
        result.put("last30Days", activity);
        return result;
    }

    private long count(String sql, UUID workspaceId) {
        Long value = jdbc.queryForObject(sql, Long.class, workspaceId);
        return value == null ? 0L : value;
    }
}
