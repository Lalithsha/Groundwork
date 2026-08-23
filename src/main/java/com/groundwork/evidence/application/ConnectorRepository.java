package com.groundwork.evidence.application;

import com.groundwork.evidence.domain.ConnectorConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

@Repository
public class ConnectorRepository {
    private static final String COLUMNS = """
        id, workspace_id, provider, external_account_id, display_name, status, scopes,
        metadata::text AS metadata, last_synced_at, last_error, created_at, updated_at
        """;

    private final JdbcTemplate jdbc;
    private final EvidenceJson json;
    private final ConnectorCredentialCipher cipher;
    private final RowMapper<ConnectorConnection> mapper;

    public ConnectorRepository(JdbcTemplate jdbc, EvidenceJson json, ConnectorCredentialCipher cipher) {
        this.jdbc = jdbc;
        this.json = json;
        this.cipher = cipher;
        this.mapper = (rs, rowNum) -> new ConnectorConnection(
            rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
            rs.getString("provider"), rs.getString("external_account_id"), rs.getString("display_name"),
            rs.getString("status"), readScopes(rs.getArray("scopes")), json.map(rs.getString("metadata")),
            rs.getTimestamp("last_synced_at") == null ? null : rs.getTimestamp("last_synced_at").toInstant(),
            rs.getString("last_error"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    }

    @Transactional
    public ConnectorConnection upsert(UUID workspaceId, String provider, String externalAccountId,
            String displayName, List<String> scopes, Map<String, Object> metadata, String credential) {
        UUID id = findId(workspaceId, provider, externalAccountId).orElseGet(UUID::randomUUID);
        String encrypted = cipher.encrypt(credential, credentialContext(workspaceId, provider, externalAccountId));
        jdbc.update("""
            INSERT INTO connector_connections (
                id, workspace_id, provider, external_account_id, display_name, status,
                scopes, encrypted_credentials, credential_key_version, metadata
            ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, CAST(? AS jsonb))
            ON CONFLICT (workspace_id, provider, external_account_id) DO UPDATE SET
                display_name = EXCLUDED.display_name,
                status = 'ACTIVE',
                scopes = EXCLUDED.scopes,
                encrypted_credentials = COALESCE(EXCLUDED.encrypted_credentials, connector_connections.encrypted_credentials),
                credential_key_version = COALESCE(EXCLUDED.credential_key_version, connector_connections.credential_key_version),
                metadata = EXCLUDED.metadata,
                last_error = NULL,
                updated_at = now()
            """, id, workspaceId, provider, externalAccountId, displayName,
            scopes == null ? new String[0] : scopes.toArray(String[]::new), encrypted,
            encrypted == null ? null : cipher.currentVersion(), json.write(metadata));
        return findByWorkspaceProviderExternalId(workspaceId, provider, externalAccountId).orElseThrow();
    }

    public List<ConnectorConnection> findByWorkspace(UUID workspaceId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM connector_connections WHERE workspace_id = ? ORDER BY created_at DESC",
            mapper, workspaceId);
    }

    public Optional<ConnectorConnection> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM connector_connections WHERE id = ?", mapper, id)
            .stream().findFirst();
    }

    public Optional<ConnectorConnection> findByWorkspaceProviderExternalId(UUID workspaceId, String provider,
            String externalId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM connector_connections " +
            "WHERE workspace_id = ? AND provider = ? AND external_account_id = ?", mapper,
            workspaceId, provider, externalId).stream().findFirst();
    }

    public Optional<ConnectorConnection> findGithubInstallation(String installationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM connector_connections " +
            "WHERE provider = 'GITHUB' AND external_account_id = ? AND status = 'ACTIVE'", mapper, installationId)
            .stream().findFirst();
    }

    public Optional<String> credential(UUID connectionId) {
        return jdbc.query("""
            SELECT workspace_id, provider, external_account_id, encrypted_credentials
            FROM connector_connections WHERE id = ?
            """, (rs, rowNum) -> {
                String encrypted = rs.getString("encrypted_credentials");
                if (encrypted == null) return null;
                return cipher.decrypt(encrypted, credentialContext(
                    rs.getObject("workspace_id", UUID.class), rs.getString("provider"),
                    rs.getString("external_account_id")));
            }, connectionId).stream().filter(Objects::nonNull).findFirst();
    }

    public void markSyncSuccess(UUID id) {
        jdbc.update("UPDATE connector_connections SET status = 'ACTIVE', last_synced_at = now(), " +
            "last_error = NULL, updated_at = now() WHERE id = ?", id);
    }

    public void markFailure(UUID id, String message) {
        jdbc.update("UPDATE connector_connections SET status = 'DEGRADED', last_error = ?, updated_at = now() WHERE id = ?",
            truncate(message), id);
    }

    public boolean revoke(UUID id, UUID workspaceId) {
        return jdbc.update("UPDATE connector_connections SET status = 'REVOKED', encrypted_credentials = NULL, " +
            "updated_at = now() WHERE id = ? AND workspace_id = ?", id, workspaceId) == 1;
    }

    private Optional<UUID> findId(UUID workspaceId, String provider, String externalAccountId) {
        return jdbc.queryForList("SELECT id FROM connector_connections WHERE workspace_id = ? AND provider = ? " +
            "AND external_account_id = ?", UUID.class, workspaceId, provider, externalAccountId).stream().findFirst();
    }

    private String credentialContext(UUID workspaceId, String provider, String externalId) {
        return workspaceId + ":" + provider + ":" + externalId;
    }

    private List<String> readScopes(Array value) throws SQLException {
        if (value == null) return List.of();
        Object array = value.getArray();
        return array instanceof String[] strings ? List.copyOf(Arrays.asList(strings)) : List.of();
    }

    private String truncate(String message) {
        if (message == null) return "Connector operation failed";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
