package com.groundwork.evidence.application;

import com.groundwork.application.Hashing;
import com.groundwork.evidence.domain.EvidenceArtifact;
import com.groundwork.evidence.domain.EvidenceArtifactVersion;
import com.groundwork.evidence.domain.EvidenceRelationship;
import com.groundwork.evidence.domain.EvidenceSearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EvidenceCatalogRepository {
    private static final String ARTIFACT_COLUMNS = """
        id, workspace_id, connection_id, source_provider, external_id, artifact_type,
        title, canonical_url, lifecycle_state, source_acl::text AS source_acl, created_at, updated_at
        """;
    private static final String VERSION_COLUMNS = """
        id, artifact_id, source_version, content_hash, content, metadata::text AS metadata,
        valid_from, valid_to, embedding_model, embedding_version, created_at
        """;

    private final JdbcTemplate jdbc;
    private final EvidenceJson json;
    private final RowMapper<EvidenceArtifact> artifactMapper;
    private final RowMapper<EvidenceArtifactVersion> versionMapper;
    private final RowMapper<EvidenceRelationship> relationshipMapper;

    public EvidenceCatalogRepository(JdbcTemplate jdbc, EvidenceJson json) {
        this.jdbc = jdbc;
        this.json = json;
        this.artifactMapper = (rs, rowNum) -> new EvidenceArtifact(
            rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
            rs.getObject("connection_id", UUID.class), rs.getString("source_provider"),
            rs.getString("external_id"), rs.getString("artifact_type"), rs.getString("title"),
            rs.getString("canonical_url"), rs.getString("lifecycle_state"),
            json.map(rs.getString("source_acl")), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
        this.versionMapper = (rs, rowNum) -> new EvidenceArtifactVersion(
            rs.getObject("id", UUID.class), rs.getObject("artifact_id", UUID.class),
            rs.getString("source_version"), rs.getString("content_hash"), rs.getString("content"),
            json.map(rs.getString("metadata")), rs.getTimestamp("valid_from").toInstant(),
            rs.getTimestamp("valid_to") == null ? null : rs.getTimestamp("valid_to").toInstant(),
            rs.getString("embedding_model"), rs.getString("embedding_version"),
            rs.getTimestamp("created_at").toInstant());
        this.relationshipMapper = (rs, rowNum) -> new EvidenceRelationship(
            rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
            rs.getObject("source_artifact_id", UUID.class), rs.getObject("target_artifact_id", UUID.class),
            rs.getString("relationship_type"), rs.getString("provenance_type"),
            json.map(rs.getString("provenance")), rs.getObject("confidence", Double.class),
            rs.getTimestamp("valid_from").toInstant(),
            rs.getTimestamp("valid_to") == null ? null : rs.getTimestamp("valid_to").toInstant());
    }

    @Transactional
    public ArtifactUpsert upsert(UUID workspaceId, UUID connectionId, String provider, String externalId,
            String artifactType, String title, String canonicalUrl, Map<String, Object> sourceAcl,
            String sourceVersion, String content, Map<String, Object> metadata) {
        UUID artifactId = findByExternalId(workspaceId, provider, externalId)
            .map(EvidenceArtifact::id).orElseGet(UUID::randomUUID);
        jdbc.update("""
            INSERT INTO evidence_artifacts (
                id, workspace_id, connection_id, source_provider, external_id, artifact_type,
                title, canonical_url, lifecycle_state, source_acl
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'CURRENT', CAST(? AS jsonb))
            ON CONFLICT (workspace_id, source_provider, external_id) DO UPDATE SET
                connection_id = COALESCE(EXCLUDED.connection_id, evidence_artifacts.connection_id),
                artifact_type = EXCLUDED.artifact_type,
                title = EXCLUDED.title,
                canonical_url = EXCLUDED.canonical_url,
                lifecycle_state = 'CURRENT',
                source_acl = EXCLUDED.source_acl,
                updated_at = now()
            """, artifactId, workspaceId, connectionId, provider, externalId, artifactType,
            title, canonicalUrl, json.write(sourceAcl));

        String safeContent = content == null ? "" : content;
        String hash = Hashing.sha256(safeContent);
        Optional<EvidenceArtifactVersion> current = findCurrentVersion(artifactId);
        if (current.isPresent() && current.get().contentHash().equals(hash)) {
            return new ArtifactUpsert(findById(artifactId).orElseThrow(), current.get(), false);
        }

        Instant now = Instant.now();
        jdbc.update("UPDATE evidence_artifact_versions SET valid_to = ? WHERE artifact_id = ? AND valid_to IS NULL",
            Timestamp.from(now), artifactId);
        UUID versionId = UUID.randomUUID();
        String effectiveSourceVersion = sourceVersion == null || sourceVersion.isBlank() ? hash : sourceVersion;
        jdbc.update("""
            INSERT INTO evidence_artifact_versions (
                id, artifact_id, source_version, content_hash, content, metadata, valid_from
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
            ON CONFLICT (artifact_id, source_version) DO UPDATE SET
                content_hash = EXCLUDED.content_hash,
                content = EXCLUDED.content,
                metadata = EXCLUDED.metadata,
                valid_from = EXCLUDED.valid_from,
                valid_to = NULL
            """, versionId, artifactId, effectiveSourceVersion, hash, safeContent,
            json.write(metadata), Timestamp.from(now));
        EvidenceArtifactVersion version = findCurrentVersion(artifactId).orElseThrow();
        return new ArtifactUpsert(findById(artifactId).orElseThrow(), version, true);
    }

    public Optional<EvidenceArtifact> findById(UUID artifactId) {
        return jdbc.query("SELECT " + ARTIFACT_COLUMNS + " FROM evidence_artifacts WHERE id = ?",
            artifactMapper, artifactId).stream().findFirst();
    }

    public Optional<EvidenceArtifact> findAuthorizedById(UUID workspaceId, UUID artifactId) {
        return jdbc.query("SELECT " + ARTIFACT_COLUMNS + " FROM evidence_artifacts WHERE id = ? AND workspace_id = ?",
            artifactMapper, artifactId, workspaceId).stream().findFirst();
    }

    public Optional<EvidenceArtifact> findByExternalId(UUID workspaceId, String provider, String externalId) {
        return jdbc.query("SELECT " + ARTIFACT_COLUMNS + " FROM evidence_artifacts " +
            "WHERE workspace_id = ? AND source_provider = ? AND external_id = ?", artifactMapper,
            workspaceId, provider, externalId).stream().findFirst();
    }

    public List<EvidenceArtifact> findByWorkspace(UUID workspaceId, String artifactType, int limit) {
        return jdbc.query("SELECT " + ARTIFACT_COLUMNS + " FROM evidence_artifacts " +
            "WHERE workspace_id = ? AND lifecycle_state = 'CURRENT' " +
            "AND (CAST(? AS text) IS NULL OR artifact_type = CAST(? AS text)) " +
            "ORDER BY updated_at DESC LIMIT ?", artifactMapper, workspaceId, artifactType, artifactType, limit);
    }

    public Optional<EvidenceArtifactVersion> findCurrentVersion(UUID artifactId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM evidence_artifact_versions " +
            "WHERE artifact_id = ? AND valid_to IS NULL ORDER BY created_at DESC LIMIT 1", versionMapper, artifactId)
            .stream().findFirst();
    }

    public List<EvidenceArtifactVersion> findVersions(UUID workspaceId, UUID artifactId) {
        return jdbc.query("""
            SELECT version.id, version.artifact_id, version.source_version, version.content_hash,
                   version.content, version.metadata::text AS metadata, version.valid_from,
                   version.valid_to, version.embedding_model, version.embedding_version, version.created_at
            FROM evidence_artifact_versions version
            JOIN evidence_artifacts artifact ON artifact.id = version.artifact_id
            WHERE artifact.workspace_id = ? AND artifact.id = ?
            ORDER BY version.valid_from DESC
            """, versionMapper, workspaceId, artifactId);
    }

    @Transactional
    public EvidenceRelationship relate(UUID workspaceId, UUID sourceArtifactId, UUID targetArtifactId,
            String relationshipType, String provenanceType, Map<String, Object> provenance, Double confidence) {
        if (sourceArtifactId.equals(targetArtifactId)) throw new IllegalArgumentException("Evidence cannot relate to itself");
        requireArtifactWorkspace(workspaceId, sourceArtifactId);
        requireArtifactWorkspace(workspaceId, targetArtifactId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO evidence_relationships (
                id, workspace_id, source_artifact_id, target_artifact_id, relationship_type,
                provenance_type, provenance, confidence
            ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
            ON CONFLICT (source_artifact_id, target_artifact_id, relationship_type, provenance_type)
            DO UPDATE SET provenance = EXCLUDED.provenance, confidence = EXCLUDED.confidence,
                          valid_to = NULL, valid_from = now()
            """, id, workspaceId, sourceArtifactId, targetArtifactId, relationshipType,
            provenanceType, json.write(provenance), confidence);
        return jdbc.query("""
            SELECT id, workspace_id, source_artifact_id, target_artifact_id, relationship_type,
                   provenance_type, provenance::text AS provenance, confidence, valid_from, valid_to
            FROM evidence_relationships
            WHERE source_artifact_id = ? AND target_artifact_id = ? AND relationship_type = ?
              AND provenance_type = ? AND valid_to IS NULL
            """, relationshipMapper, sourceArtifactId, targetArtifactId, relationshipType, provenanceType)
            .stream().findFirst().orElseThrow();
    }

    public List<EvidenceRelationship> relationships(UUID workspaceId, UUID artifactId) {
        return jdbc.query("""
            SELECT id, workspace_id, source_artifact_id, target_artifact_id, relationship_type,
                   provenance_type, provenance::text AS provenance, confidence, valid_from, valid_to
            FROM evidence_relationships
            WHERE workspace_id = ? AND valid_to IS NULL
              AND (source_artifact_id = ? OR target_artifact_id = ?)
            ORDER BY created_at
            """, relationshipMapper, workspaceId, artifactId, artifactId);
    }

    public List<EvidenceSearchHit> searchKeyword(UUID workspaceId, String query, int limit) {
        String sql = """
            SELECT artifact.id AS artifact_id, version.id AS version_id, artifact.artifact_type,
                   artifact.title, artifact.canonical_url, version.source_version, version.content,
                   version.metadata::text AS metadata,
                   ts_rank_cd(version.content_tsv, websearch_to_tsquery('english', ?)) AS score
            FROM evidence_artifact_versions version
            JOIN evidence_artifacts artifact ON artifact.id = version.artifact_id
            WHERE artifact.workspace_id = ? AND artifact.lifecycle_state = 'CURRENT'
              AND version.valid_to IS NULL
              AND version.content_tsv @@ websearch_to_tsquery('english', ?)
            ORDER BY score DESC LIMIT ?
            """;
        return jdbc.query(sql, searchHitMapper("KEYWORD"), query, workspaceId, query, limit);
    }

    public List<EvidenceSearchHit> searchVector(UUID workspaceId, double[] embedding, int limit) {
        String vector = vectorLiteral(embedding);
        String sql = """
            SELECT artifact.id AS artifact_id, version.id AS version_id, artifact.artifact_type,
                   artifact.title, artifact.canonical_url, version.source_version, version.content,
                   version.metadata::text AS metadata,
                   (1 - (version.embedding <=> CAST(? AS vector))) AS score
            FROM evidence_artifact_versions version
            JOIN evidence_artifacts artifact ON artifact.id = version.artifact_id
            WHERE artifact.workspace_id = ? AND artifact.lifecycle_state = 'CURRENT'
              AND version.valid_to IS NULL AND version.embedding IS NOT NULL
            ORDER BY version.embedding <=> CAST(? AS vector) LIMIT ?
            """;
        return jdbc.query(sql, searchHitMapper("VECTOR"), vector, workspaceId, vector, limit);
    }

    public List<EvidenceSearchHit> currentVersions(UUID workspaceId, List<UUID> artifactIds) {
        if (artifactIds == null || artifactIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(artifactIds.size(), "?"));
        String sql = """
            SELECT artifact.id AS artifact_id, version.id AS version_id, artifact.artifact_type,
                   artifact.title, artifact.canonical_url, version.source_version, version.content,
                   version.metadata::text AS metadata, 0.0 AS score
            FROM evidence_artifact_versions version
            JOIN evidence_artifacts artifact ON artifact.id = version.artifact_id
            WHERE artifact.workspace_id = ? AND artifact.lifecycle_state = 'CURRENT'
              AND version.valid_to IS NULL AND artifact.id IN (%s)
            ORDER BY artifact.updated_at DESC
            """.formatted(placeholders);
        Object[] args = new Object[artifactIds.size() + 1];
        args[0] = workspaceId;
        for (int index = 0; index < artifactIds.size(); index++) args[index + 1] = artifactIds.get(index);
        return jdbc.query(sql, searchHitMapper("GRAPH"), args);
    }

    public void storeEmbedding(UUID versionId, double[] embedding, String model, String embeddingVersion) {
        jdbc.update("UPDATE evidence_artifact_versions SET embedding = CAST(? AS vector), embedding_model = ?, " +
            "embedding_version = ? WHERE id = ?", vectorLiteral(embedding), model, embeddingVersion, versionId);
    }

    public boolean markInaccessible(UUID workspaceId, UUID artifactId) {
        return jdbc.update("UPDATE evidence_artifacts SET lifecycle_state = 'INACCESSIBLE', updated_at = now() " +
            "WHERE id = ? AND workspace_id = ?", artifactId, workspaceId) == 1;
    }

    public void markSeenInSync(UUID workspaceId, UUID artifactId, UUID syncRunId) {
        jdbc.update("UPDATE evidence_artifacts SET last_seen_sync_id = ?, lifecycle_state = 'CURRENT', " +
            "updated_at = now() WHERE id = ? AND workspace_id = ?", syncRunId, artifactId, workspaceId);
    }

    public int reconcileMissing(UUID workspaceId, UUID connectionId, UUID syncRunId, String artifactType) {
        return jdbc.update("""
            UPDATE evidence_artifacts SET lifecycle_state = 'INACCESSIBLE', updated_at = now()
            WHERE workspace_id = ? AND connection_id = ? AND artifact_type = ?
              AND lifecycle_state = 'CURRENT'
              AND (last_seen_sync_id IS NULL OR last_seen_sync_id <> ?)
            """, workspaceId, connectionId, artifactType, syncRunId);
    }

    public int markConnectionInaccessible(UUID workspaceId, UUID connectionId) {
        return jdbc.update("UPDATE evidence_artifacts SET lifecycle_state = 'INACCESSIBLE', updated_at = now() " +
            "WHERE workspace_id = ? AND connection_id = ? AND lifecycle_state = 'CURRENT'",
            workspaceId, connectionId);
    }

    private RowMapper<EvidenceSearchHit> searchHitMapper(String stage) {
        return (rs, rowNum) -> new EvidenceSearchHit(
            rs.getObject("artifact_id", UUID.class), rs.getObject("version_id", UUID.class),
            rs.getString("artifact_type"), rs.getString("title"), rs.getString("canonical_url"),
            rs.getString("source_version"), rs.getString("content"), json.map(rs.getString("metadata")),
            rs.getDouble("score"), stage);
    }

    private void requireArtifactWorkspace(UUID workspaceId, UUID artifactId) {
        if (jdbc.queryForObject("SELECT count(*) FROM evidence_artifacts WHERE id = ? AND workspace_id = ?",
                Integer.class, artifactId, workspaceId) != 1) {
            throw new IllegalArgumentException("Evidence artifact does not belong to the workspace");
        }
    }

    private String vectorLiteral(double[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) value.append(',');
            value.append((float) vector[index]);
        }
        return value.append(']').toString();
    }

    public record ArtifactUpsert(EvidenceArtifact artifact, EvidenceArtifactVersion version, boolean versionCreated) {}
}
