package com.groundwork.evidence.application;

import com.groundwork.evidence.domain.ReleaseRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReleaseRecordRepository {
    private static final String COLUMNS = """
        id, workspace_id, name, repository_full_name, base_ref, head_ref, status,
        manifest::text AS manifest, manifest_hash, frozen_at, created_at
        """;
    private final JdbcTemplate jdbc;
    private final EvidenceJson json;
    private final RowMapper<ReleaseRecord> mapper;

    public ReleaseRecordRepository(JdbcTemplate jdbc, EvidenceJson json) {
        this.jdbc = jdbc;
        this.json = json;
        this.mapper = (rs, rowNum) -> new ReleaseRecord(
            rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
            rs.getString("name"), rs.getString("repository_full_name"), rs.getString("base_ref"),
            rs.getString("head_ref"), rs.getString("status"), json.map(rs.getString("manifest")),
            rs.getString("manifest_hash"), rs.getTimestamp("frozen_at").toInstant(),
            rs.getTimestamp("created_at").toInstant());
    }

    @Transactional
    public ReleaseRecord create(UUID workspaceId, String name, String repositoryFullName,
            String baseRef, String headRef, String status, Map<String, Object> manifest,
            String manifestHash, UUID createdBy, List<ChangeEvidence> changes) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO release_records (
                id, workspace_id, name, repository_full_name, base_ref, head_ref, status,
                manifest, manifest_hash, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
            """, id, workspaceId, name, repositoryFullName, baseRef, headRef, status,
            json.write(manifest), manifestHash, createdBy);
        for (ChangeEvidence change : changes) {
            jdbc.update("""
                INSERT INTO release_evidence_items (
                    release_record_id, change_set_id, evidence_type, evidence_digest, metadata
                ) VALUES (?, ?, 'CHANGE_SET', ?, CAST(? AS jsonb))
                """, id, change.changeSetId(), change.digest(), json.write(change.metadata()));
        }
        return findAuthorized(workspaceId, id).orElseThrow();
    }

    public Optional<ReleaseRecord> findAuthorized(UUID workspaceId, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM release_records WHERE workspace_id = ? AND id = ?",
            mapper, workspaceId, id).stream().findFirst();
    }

    public List<ReleaseRecord> findByWorkspace(UUID workspaceId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM release_records WHERE workspace_id = ? " +
            "ORDER BY created_at DESC", mapper, workspaceId);
    }

    public boolean updateStatus(UUID workspaceId, UUID id, String status) {
        return jdbc.update("UPDATE release_records SET status = ? WHERE workspace_id = ? AND id = ?",
            status, workspaceId, id) == 1;
    }

    public List<Map<String, Object>> evidence(UUID workspaceId, UUID releaseId) {
        return jdbc.query("""
            SELECT item.change_set_id, item.evidence_type, item.evidence_digest,
                   item.metadata::text AS metadata
            FROM release_evidence_items item
            JOIN release_records release ON release.id = item.release_record_id
            WHERE release.workspace_id = ? AND release.id = ?
            ORDER BY item.evidence_type, item.evidence_digest
            """, (rs, rowNum) -> Map.of(
                "changeSetId", rs.getObject("change_set_id", UUID.class),
                "evidenceType", rs.getString("evidence_type"),
                "evidenceDigest", rs.getString("evidence_digest"),
                "metadata", json.map(rs.getString("metadata"))), workspaceId, releaseId);
    }

    public record ChangeEvidence(UUID changeSetId, String digest, Map<String, Object> metadata) {}
}
