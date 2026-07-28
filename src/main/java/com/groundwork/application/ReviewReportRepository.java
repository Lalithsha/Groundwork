package com.groundwork.application;

import com.groundwork.domain.model.DecisionLogEntry;
import com.groundwork.domain.model.ReviewReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReviewReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReviewReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ReviewReport> reportRowMapper = (rs, rowNum) -> new ReviewReport(
        UUID.fromString(rs.getString("id")),
        rs.getString("workspace_id") != null ? UUID.fromString(rs.getString("workspace_id")) : null,
        rs.getString("title"),
        rs.getString("status"),
        rs.getObject("score") != null ? rs.getDouble("score") : null,
        rs.getString("feedback"),
        rs.getString("report_data"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant()
    );

    private final RowMapper<DecisionLogEntry> decisionRowMapper = (rs, rowNum) -> new DecisionLogEntry(
        UUID.fromString(rs.getString("id")),
        rs.getString("workspace_id") != null ? UUID.fromString(rs.getString("workspace_id")) : null,
        rs.getString("decision"),
        rs.getString("rationale"),
        rs.getString("actor"),
        rs.getTimestamp("created_at").toInstant()
    );

    public ReviewReport saveReport(UUID workspaceId, String title, String status, Double score, String feedback, String reportData) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
            INSERT INTO review_reports (id, workspace_id, title, status, score, feedback, report_data, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """;
        jdbcTemplate.update(sql, id, workspaceId, title, status, score, feedback, reportData, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return new ReviewReport(id, workspaceId, title, status, score, feedback, reportData, now, now);
    }

    public DecisionLogEntry saveDecision(UUID workspaceId, String decision, String rationale, String actor) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
            INSERT INTO decision_log (id, workspace_id, decision, rationale, actor, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql, id, workspaceId, decision, rationale, actor != null ? actor : "AI_REVIEWER", java.sql.Timestamp.from(now));
        return new DecisionLogEntry(id, workspaceId, decision, rationale, actor != null ? actor : "AI_REVIEWER", now);
    }

    public List<ReviewReport> findReportsByWorkspace(UUID workspaceId) {
        if (workspaceId != null) {
            String sql = "SELECT id, workspace_id, title, status, score, feedback, report_data::text AS report_data, created_at, updated_at FROM review_reports WHERE workspace_id = ? ORDER BY created_at DESC";
            return jdbcTemplate.query(sql, reportRowMapper, workspaceId);
        }
        return findAllReports();
    }

    public List<ReviewReport> findAllReports() {
        String sql = "SELECT id, workspace_id, title, status, score, feedback, report_data::text AS report_data, created_at, updated_at FROM review_reports ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, reportRowMapper);
    }

    public Optional<ReviewReport> findReportById(UUID id) {
        String sql = "SELECT id, workspace_id, title, status, score, feedback, report_data::text AS report_data, created_at, updated_at FROM review_reports WHERE id = ?";
        List<ReviewReport> list = jdbcTemplate.query(sql, reportRowMapper, id);
        return list.stream().findFirst();
    }

    public List<DecisionLogEntry> findDecisionsByWorkspace(UUID workspaceId) {
        if (workspaceId != null) {
            String sql = "SELECT id, workspace_id, decision, rationale, actor, created_at FROM decision_log WHERE workspace_id = ? ORDER BY created_at DESC";
            return jdbcTemplate.query(sql, decisionRowMapper, workspaceId);
        }
        return findAllDecisions();
    }

    public List<DecisionLogEntry> findAllDecisions() {
        String sql = "SELECT id, workspace_id, decision, rationale, actor, created_at FROM decision_log ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, decisionRowMapper);
    }
}
