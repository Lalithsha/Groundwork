package com.groundwork.application;

import com.groundwork.domain.model.DocumentComparison;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ComparisonRepository {

    private final JdbcTemplate jdbcTemplate;

    public ComparisonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<DocumentComparison> rowMapper = (rs, rowNum) -> new DocumentComparison(
        UUID.fromString(rs.getString("id")),
        rs.getString("workspace_id") != null ? UUID.fromString(rs.getString("workspace_id")) : null,
        rs.getString("doc_title_a"),
        rs.getString("doc_title_b"),
        rs.getString("comparison_result"),
        rs.getString("diff_summary"),
        rs.getTimestamp("created_at").toInstant()
    );

    public DocumentComparison save(UUID workspaceId, String docTitleA, String docTitleB, String comparisonResult, String diffSummary) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
            INSERT INTO document_comparisons (id, workspace_id, doc_title_a, doc_title_b, comparison_result, diff_summary, created_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
            """;
        jdbcTemplate.update(sql, id, workspaceId, docTitleA, docTitleB, comparisonResult, diffSummary, java.sql.Timestamp.from(now));
        return new DocumentComparison(id, workspaceId, docTitleA, docTitleB, comparisonResult, diffSummary, now);
    }

    public List<DocumentComparison> findByWorkspaceId(UUID workspaceId) {
        String sql = "SELECT id, workspace_id, doc_title_a, doc_title_b, comparison_result, diff_summary::text AS diff_summary, created_at FROM document_comparisons WHERE workspace_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, workspaceId);
    }

    public List<DocumentComparison> findAll() {
        String sql = "SELECT id, workspace_id, doc_title_a, doc_title_b, comparison_result, diff_summary::text AS diff_summary, created_at FROM document_comparisons ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Optional<DocumentComparison> findById(UUID id) {
        String sql = "SELECT id, workspace_id, doc_title_a, doc_title_b, comparison_result, diff_summary::text AS diff_summary, created_at FROM document_comparisons WHERE id = ?";
        List<DocumentComparison> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }
}
