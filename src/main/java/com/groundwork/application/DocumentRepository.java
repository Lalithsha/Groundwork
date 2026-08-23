package com.groundwork.application;

import com.groundwork.domain.model.DocumentChunk;
import com.groundwork.domain.model.TextChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class DocumentRepository {
    private static final String CHUNK_COLUMNS = """
        id, document_id, title, content, source_type, content_hash,
        COALESCE(chunk_index, 0) AS chunk_index, section_title, page_number
        """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<DocumentChunk> chunkMapper = (rs, rowNum) -> new DocumentChunk(
        rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
        rs.getString("title"), rs.getString("content"), rs.getString("source_type"),
        rs.getString("content_hash"), rs.getDouble("score"), rs.getInt("chunk_index"),
        rs.getString("section_title"), rs.getObject("page_number", Integer.class)
    );

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findAllTitles() {
        return jdbcTemplate.queryForList("""
            SELECT title FROM source_documents WHERE status = 'READY'
            UNION SELECT title FROM documents WHERE document_id IS NULL
            ORDER BY title
            """, String.class);
    }

    public List<String> findAllTitles(UUID workspaceId) {
        if (workspaceId == null) return findAllTitles();
        return jdbcTemplate.queryForList("""
            SELECT title FROM source_documents WHERE status = 'READY' AND workspace_id = ?
            UNION SELECT title FROM documents WHERE document_id IS NULL AND workspace_id = ?
            ORDER BY title
            """, String.class, workspaceId, workspaceId);
    }

    @Transactional
    public void deleteByTitle(String title) {
        jdbcTemplate.update("DELETE FROM source_documents WHERE LOWER(title) = LOWER(?)", title);
        jdbcTemplate.update("DELETE FROM documents WHERE document_id IS NULL AND LOWER(title) = LOWER(?)", title);
    }

    @Transactional
    public void deleteByTitle(String title, UUID workspaceId) {
        if (workspaceId == null) {
            deleteByTitle(title);
            return;
        }
        jdbcTemplate.update("DELETE FROM source_documents WHERE workspace_id = ? AND LOWER(title) = LOWER(?)", workspaceId, title);
        jdbcTemplate.update("DELETE FROM documents WHERE document_id IS NULL AND workspace_id = ? AND LOWER(title) = LOWER(?)", workspaceId, title);
    }

    public List<DocumentChunk> searchVector(double[] queryEmbedding, UUID workspaceId, String documentFilter, int limit) {
        String sql = """
            SELECT %s, (1 - (embedding <=> CAST(? AS vector))) AS score
            FROM documents
            WHERE embedding IS NOT NULL
              AND (CAST(? AS uuid) IS NULL OR workspace_id = CAST(? AS uuid))
              AND (CAST(? AS text) IS NULL OR LOWER(title) LIKE LOWER(CAST(? AS text)))
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT ?
            """.formatted(CHUNK_COLUMNS);
        String vector = toVectorLiteral(queryEmbedding);
        String filter = normalizedFilter(documentFilter);
        return jdbcTemplate.query(sql, chunkMapper,
            vector, workspaceId, workspaceId, filter, likeFilter(filter), vector, limit);
    }

    public List<DocumentChunk> searchKeyword(String queryText, UUID workspaceId, String documentFilter, int limit) {
        String sql = """
            SELECT %s, ts_rank_cd(content_tsv, websearch_to_tsquery('english', ?)) AS score
            FROM documents
            WHERE content_tsv @@ websearch_to_tsquery('english', ?)
              AND (CAST(? AS uuid) IS NULL OR workspace_id = CAST(? AS uuid))
              AND (CAST(? AS text) IS NULL OR LOWER(title) LIKE LOWER(CAST(? AS text)))
            ORDER BY score DESC LIMIT ?
            """.formatted(CHUNK_COLUMNS);
        String filter = normalizedFilter(documentFilter);
        return jdbcTemplate.query(sql, chunkMapper,
            queryText, queryText, workspaceId, workspaceId, filter, likeFilter(filter), limit);
    }

    /** Compatibility entry points retained for existing application callers. */
    public List<DocumentChunk> searchVectorOnly(String queryText, String docFilter, int limit) {
        return searchKeyword(queryText, null, docFilter, limit);
    }

    public List<DocumentChunk> searchKeywordOnly(String queryText, String docFilter, int limit) {
        return searchKeyword(queryText, null, docFilter, limit);
    }

    public void save(String title, String content, String sourceType, String contentHash) {
        saveWithWorkspace(title, content, sourceType, contentHash, null);
    }

    public void saveWithWorkspace(String title, String content, String sourceType, String contentHash, UUID workspaceId) {
        jdbcTemplate.update("""
            INSERT INTO documents (title, content, source_type, content_hash, workspace_id, chunk_index, token_count)
            VALUES (?, ?, ?, ?, ?, 0, ?)
            ON CONFLICT (content_hash) WHERE document_id IS NULL
            DO UPDATE SET content = EXCLUDED.content,
                          workspace_id = COALESCE(EXCLUDED.workspace_id, documents.workspace_id),
                          updated_at = now()
            """, title, content, sourceType, contentHash, workspaceId, approximateTokens(content));
    }

    @Transactional
    public void replaceDocumentChunks(UUID documentId, UUID workspaceId, String title, String sourceType,
            List<TextChunk> chunks, List<double[]> embeddings, String embeddingModel, String embeddingVersion) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("Every chunk must have exactly one embedding");
        }
        jdbcTemplate.update("DELETE FROM documents WHERE document_id = ?", documentId);
        String sql = """
            INSERT INTO documents (
                document_id, workspace_id, title, content, source_type, content_hash,
                embedding, chunk_index, token_count, section_title, page_number,
                embedding_model, embedding_version
            ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?, ?, ?, ?, ?)
            """;
        for (int index = 0; index < chunks.size(); index++) {
            TextChunk chunk = chunks.get(index);
            jdbcTemplate.update(sql, documentId, workspaceId, title, chunk.content(), sourceType,
                Hashing.sha256(documentId + ":" + chunk.index() + ":" + chunk.content()),
                toVectorLiteral(embeddings.get(index)), chunk.index(), chunk.tokenCount(),
                chunk.sectionTitle(), chunk.pageNumber(), embeddingModel, embeddingVersion);
        }
    }

    public List<DocumentChunk> findByTitle(String title) {
        String sql = "SELECT " + CHUNK_COLUMNS + ", 1.0 AS score FROM documents " +
            "WHERE LOWER(title) = LOWER(?) ORDER BY COALESCE(chunk_index, 0), created_at";
        return jdbcTemplate.query(sql, chunkMapper, title.trim());
    }

    public List<DocumentChunk> findByTitle(String title, UUID workspaceId) {
        if (workspaceId == null) return findByTitle(title);
        String sql = "SELECT " + CHUNK_COLUMNS + ", 1.0 AS score FROM documents " +
            "WHERE LOWER(title) = LOWER(?) AND workspace_id = ? ORDER BY COALESCE(chunk_index, 0), created_at";
        return jdbcTemplate.query(sql, chunkMapper, title.trim(), workspaceId);
    }

    public List<DocumentChunk> findByWorkspaceId(UUID workspaceId) {
        String sql = "SELECT " + CHUNK_COLUMNS + ", 1.0 AS score FROM documents " +
            "WHERE workspace_id = ? ORDER BY created_at DESC, COALESCE(chunk_index, 0)";
        return jdbcTemplate.query(sql, chunkMapper, workspaceId);
    }

    public List<DocumentChunk> findAll() {
        return jdbcTemplate.query("SELECT " + CHUNK_COLUMNS +
            ", 1.0 AS score FROM documents ORDER BY created_at DESC", chunkMapper);
    }

    public List<DocumentChunk> findByDocumentId(UUID documentId) {
        return jdbcTemplate.query("SELECT " + CHUNK_COLUMNS +
            ", 1.0 AS score FROM documents WHERE document_id = ? ORDER BY chunk_index", chunkMapper, documentId);
    }

    private String normalizedFilter(String filter) { return filter == null || filter.isBlank() ? null : filter.trim(); }
    private String likeFilter(String filter) { return filter == null ? null : "%" + filter + "%"; }

    private String toVectorLiteral(double[] vector) {
        StringBuilder result = new StringBuilder(vector.length * 8).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) result.append(',');
            result.append((float) vector[i]);
        }
        return result.append(']').toString();
    }

    private int approximateTokens(String value) {
        return value == null || value.isBlank() ? 0 : value.trim().split("\\s+").length;
    }
}
