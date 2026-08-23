package com.groundwork.evidence.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class LegacyDocumentEvidenceBridge {
    private final JdbcTemplate jdbc;
    private final EvidenceIndexingService indexing;

    public LegacyDocumentEvidenceBridge(JdbcTemplate jdbc, EvidenceIndexingService indexing) {
        this.jdbc = jdbc;
        this.indexing = indexing;
    }

    public ImportResult importWorkspace(UUID workspaceId) {
        var documents = jdbc.query("""
            SELECT id, title, content, source_type, content_hash, updated_at
            FROM documents WHERE workspace_id = ? ORDER BY created_at
            """, (rs, rowNum) -> new LegacyDocument(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("content"), rs.getString("source_type"), rs.getString("content_hash"),
                rs.getTimestamp("updated_at").toInstant().toString()), workspaceId);
        int createdVersions = 0;
        for (LegacyDocument document : documents) {
            var result = indexing.upsert(workspaceId, null, "MANUAL", "document:" + document.id(), "DOCUMENT",
                document.title(), "/sources?document=" + document.id(), Map.of("workspaceScoped", true),
                document.hash(), document.content(), Map.of("legacyDocumentId", document.id(),
                    "sourceType", document.sourceType(), "updatedAt", document.updatedAt()));
            if (result.versionCreated()) createdVersions++;
        }
        return new ImportResult(documents.size(), createdVersions);
    }

    private record LegacyDocument(UUID id, String title, String content, String sourceType,
                                  String hash, String updatedAt) {}
    public record ImportResult(int documents, int versionsCreated) {}
}
