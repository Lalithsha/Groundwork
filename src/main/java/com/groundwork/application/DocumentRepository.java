package com.groundwork.application;

import com.groundwork.domain.model.DocumentChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findAllTitles() {
        String sql = "SELECT DISTINCT title FROM documents ORDER BY title ASC";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    public void deleteByTitle(String title) {
        String sql = "DELETE FROM documents WHERE title = ?";
        jdbcTemplate.update(sql, title);
    }

    public List<DocumentChunk> searchVectorOnly(String queryText, String docFilter, int limit) {
        if (docFilter != null && !docFilter.isBlank()) {
            String sql = """
                SELECT id, title, content, source_type, content_hash, 1.0 AS score
                FROM documents
                WHERE LOWER(title) LIKE LOWER(?)
                ORDER BY created_at DESC
                LIMIT ?
                """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> new DocumentChunk(
                UUID.fromString(rs.getString("id")),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("source_type"),
                rs.getString("content_hash"),
                rs.getDouble("score")
            ), "%" + docFilter.trim() + "%", limit);
        }

        String sql = """
            SELECT id, title, content, source_type, content_hash, 1.0 AS score
            FROM documents
            ORDER BY created_at DESC
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DocumentChunk(
            UUID.fromString(rs.getString("id")),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("source_type"),
            rs.getString("content_hash"),
            rs.getDouble("score")
        ), limit);
    }

    public List<DocumentChunk> searchKeywordOnly(String queryText, String docFilter, int limit) {
        if (docFilter != null && !docFilter.isBlank()) {
            String sql = """
                SELECT id, title, content, source_type, content_hash,
                       ts_rank(content_tsv, plainto_tsquery('english', ?)) AS score
                FROM documents, plainto_tsquery('english', ?) query
                WHERE LOWER(title) LIKE LOWER(?) AND content_tsv @@ query
                ORDER BY score DESC
                LIMIT ?
                """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> new DocumentChunk(
                UUID.fromString(rs.getString("id")),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("source_type"),
                rs.getString("content_hash"),
                rs.getDouble("score")
            ), queryText, queryText, "%" + docFilter.trim() + "%", limit);
        }

        String sql = """
            SELECT id, title, content, source_type, content_hash,
                   ts_rank(content_tsv, plainto_tsquery('english', ?)) AS score
            FROM documents, plainto_tsquery('english', ?) query
            WHERE content_tsv @@ query
            ORDER BY score DESC
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DocumentChunk(
            UUID.fromString(rs.getString("id")),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("source_type"),
            rs.getString("content_hash"),
            rs.getDouble("score")
        ), queryText, queryText, limit);
    }

    public void save(String title, String content, String sourceType, String contentHash) {
        saveWithWorkspace(title, content, sourceType, contentHash, null);
    }

    public void saveWithWorkspace(String title, String content, String sourceType, String contentHash, UUID workspaceId) {
        String sql = """
            INSERT INTO documents (title, content, source_type, content_hash, workspace_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (content_hash) DO UPDATE SET content = EXCLUDED.content, workspace_id = COALESCE(EXCLUDED.workspace_id, documents.workspace_id), updated_at = now()
            """;
        jdbcTemplate.update(sql, title, content, sourceType, contentHash, workspaceId);
    }

    public List<DocumentChunk> findByTitle(String title) {
        String sql = """
            SELECT id, title, content, source_type, content_hash, 1.0 AS score
            FROM documents
            WHERE LOWER(title) = LOWER(?)
            ORDER BY created_at ASC
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DocumentChunk(
            UUID.fromString(rs.getString("id")),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("source_type"),
            rs.getString("content_hash"),
            rs.getDouble("score")
        ), title.trim());
    }

    public List<DocumentChunk> findByWorkspaceId(UUID workspaceId) {
        String sql = """
            SELECT id, title, content, source_type, content_hash, 1.0 AS score
            FROM documents
            WHERE workspace_id = ?
            ORDER BY created_at DESC
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DocumentChunk(
            UUID.fromString(rs.getString("id")),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("source_type"),
            rs.getString("content_hash"),
            rs.getDouble("score")
        ), workspaceId);
    }

    public List<DocumentChunk> findAll() {
        String sql = """
            SELECT id, title, content, source_type, content_hash, 1.0 AS score
            FROM documents
            ORDER BY created_at DESC
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DocumentChunk(
            UUID.fromString(rs.getString("id")),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("source_type"),
            rs.getString("content_hash"),
            rs.getDouble("score")
        ));
    }
}
