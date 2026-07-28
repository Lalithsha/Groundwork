package com.groundwork.application;

import com.groundwork.domain.model.KnowledgeArtifact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class KnowledgeArtifactRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeArtifactRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<KnowledgeArtifact> rowMapper = (rs, rowNum) -> new KnowledgeArtifact(
        UUID.fromString(rs.getString("id")),
        rs.getString("workspace_id") != null ? UUID.fromString(rs.getString("workspace_id")) : null,
        rs.getString("title"),
        rs.getString("artifact_type"),
        rs.getString("content"),
        rs.getString("structured_data"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant()
    );

    public KnowledgeArtifact save(UUID workspaceId, String title, String artifactType, String content, String structuredData) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
            INSERT INTO knowledge_artifacts (id, workspace_id, title, artifact_type, content, structured_data, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """;
        jdbcTemplate.update(sql, id, workspaceId, title, artifactType, content, structuredData, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return new KnowledgeArtifact(id, workspaceId, title, artifactType, content, structuredData, now, now);
    }

    public List<KnowledgeArtifact> findByWorkspaceId(UUID workspaceId) {
        String sql = "SELECT id, workspace_id, title, artifact_type, content, structured_data::text AS structured_data, created_at, updated_at FROM knowledge_artifacts WHERE workspace_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, workspaceId);
    }

    public List<KnowledgeArtifact> findAll() {
        String sql = "SELECT id, workspace_id, title, artifact_type, content, structured_data::text AS structured_data, created_at, updated_at FROM knowledge_artifacts ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Optional<KnowledgeArtifact> findById(UUID id) {
        String sql = "SELECT id, workspace_id, title, artifact_type, content, structured_data::text AS structured_data, created_at, updated_at FROM knowledge_artifacts WHERE id = ?";
        List<KnowledgeArtifact> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public boolean deleteById(UUID id) {
        String sql = "DELETE FROM knowledge_artifacts WHERE id = ?";
        return jdbcTemplate.update(sql, id) > 0;
    }
}
