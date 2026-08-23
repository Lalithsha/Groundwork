package com.groundwork.evidence.application;

import com.groundwork.application.Hashing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConnectorOAuthStateRepository {
    private final JdbcTemplate jdbc;
    private final EvidenceJson json;

    public ConnectorOAuthStateRepository(JdbcTemplate jdbc, EvidenceJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void create(String rawState, UUID workspaceId, UUID userId, String provider,
            List<String> scopes, Map<String, Object> selectedResources, Instant expiresAt) {
        jdbc.update("""
            INSERT INTO connector_oauth_states
                (state_hash, workspace_id, initiated_by, provider, scopes, selected_resources, expires_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
            """, Hashing.sha256(rawState), workspaceId, userId, provider,
            scopes.toArray(String[]::new), json.write(selectedResources), java.sql.Timestamp.from(expiresAt));
    }

    @Transactional
    public Optional<OAuthState> consume(String rawState) {
        return jdbc.query("""
            UPDATE connector_oauth_states SET consumed_at = now()
            WHERE state_hash = ? AND consumed_at IS NULL AND expires_at > now()
            RETURNING workspace_id, initiated_by, provider, scopes,
                      selected_resources::text AS selected_resources, expires_at
            """, (rs, rowNum) -> new OAuthState(rs.getObject("workspace_id", UUID.class),
                rs.getObject("initiated_by", UUID.class), rs.getString("provider"),
                scopes(rs.getArray("scopes")), json.map(rs.getString("selected_resources")),
                rs.getTimestamp("expires_at").toInstant()), Hashing.sha256(rawState)).stream().findFirst();
    }

    public int deleteExpired() {
        return jdbc.update("DELETE FROM connector_oauth_states WHERE expires_at < now() - interval '1 day'");
    }

    private static List<String> scopes(Array value) throws SQLException {
        if (value == null || !(value.getArray() instanceof String[] strings)) return List.of();
        return List.copyOf(Arrays.asList(strings));
    }

    public record OAuthState(UUID workspaceId, UUID userId, String provider, List<String> scopes,
                             Map<String, Object> selectedResources, Instant expiresAt) {}
}
