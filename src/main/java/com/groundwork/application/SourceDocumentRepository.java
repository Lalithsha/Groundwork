package com.groundwork.application;

import com.groundwork.domain.model.SourceDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SourceDocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SourceDocument> mapper = (rs, rowNum) -> new SourceDocument(
        rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
        rs.getString("title"), rs.getString("media_type"), rs.getString("source_type"),
        rs.getString("content_hash"), rs.getString("status"), rs.getInt("version"),
        rs.getString("embedding_model"), rs.getString("error_message"),
        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()
    );

    public SourceDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SourceDocument create(UUID workspaceId, String title, String mediaType, String sourceType,
            String rawContent, String contentHash) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
            INSERT INTO source_documents (
                id, workspace_id, title, media_type, source_type, raw_content,
                content_hash, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'QUEUED', ?, ?)
            """, id, workspaceId, title, mediaType, sourceType, rawContent, contentHash,
            java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return new SourceDocument(id, workspaceId, title, mediaType, sourceType, contentHash,
            "QUEUED", 1, null, null, now, now);
    }

    public Optional<SourceDocument> findByContentHash(UUID workspaceId, String contentHash) {
        return jdbcTemplate.query("""
            SELECT id, workspace_id, title, media_type, source_type, content_hash, status,
                   version, embedding_model, error_message, created_at, updated_at
            FROM source_documents
            WHERE workspace_id IS NOT DISTINCT FROM CAST(? AS uuid) AND content_hash = ?
              AND status <> 'DELETED'
            """, mapper, workspaceId, contentHash).stream().findFirst();
    }

    public Optional<SourceContent> findContentById(UUID id) {
        return jdbcTemplate.query("""
            SELECT id, workspace_id, title, media_type, source_type, raw_content, content_hash, status
            FROM source_documents WHERE id = ?
            """, (rs, rowNum) -> new SourceContent(
                rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                rs.getString("title"), rs.getString("media_type"), rs.getString("source_type"),
                rs.getString("raw_content"), rs.getString("content_hash"), rs.getString("status")
            ), id).stream().findFirst();
    }

    public List<SourceDocument> findByWorkspace(UUID workspaceId) {
        return jdbcTemplate.query("""
            SELECT id, workspace_id, title, media_type, source_type, content_hash, status,
                   version, embedding_model, error_message, created_at, updated_at
            FROM source_documents
            WHERE (CAST(? AS uuid) IS NULL OR workspace_id = CAST(? AS uuid))
              AND status <> 'DELETED'
            ORDER BY created_at DESC
            """, mapper, workspaceId, workspaceId);
    }

    public void markProcessing(UUID id) {
        jdbcTemplate.update("UPDATE source_documents SET status = 'PROCESSING', error_message = NULL, updated_at = now() WHERE id = ?", id);
    }

    public void markQueued(UUID id) {
        jdbcTemplate.update("UPDATE source_documents SET status = 'QUEUED', error_message = NULL, updated_at = now() WHERE id = ?", id);
    }

    public void markReady(UUID id, String embeddingModel) {
        jdbcTemplate.update("UPDATE source_documents SET status = 'READY', embedding_model = ?, error_message = NULL, updated_at = now() WHERE id = ?",
            embeddingModel, id);
    }

    public void markFailed(UUID id, String message) {
        jdbcTemplate.update("UPDATE source_documents SET status = 'FAILED', error_message = ?, updated_at = now() WHERE id = ?",
            truncate(message), id);
    }

    private String truncate(String message) {
        if (message == null) return "Unknown ingestion failure";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record SourceContent(UUID id, UUID workspaceId, String title, String mediaType,
                                String sourceType, String rawContent, String contentHash, String status) {}
}
