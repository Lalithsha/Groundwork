package com.groundwork.evidence.application;

import com.groundwork.application.Hashing;
import com.groundwork.evidence.domain.EvidencePolicy;
import com.groundwork.evidence.domain.PolicyEvaluation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PolicyRepository {
    private final JdbcTemplate jdbc;
    private final EvidenceJson json;
    private final RowMapper<EvidencePolicy> policyMapper;
    private final RowMapper<PolicyEvaluation> evaluationMapper;

    public PolicyRepository(JdbcTemplate jdbc, EvidenceJson json) {
        this.jdbc = jdbc;
        this.json = json;
        this.policyMapper = (rs, rowNum) -> new EvidencePolicy(
            rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
            rs.getString("name"), rs.getString("description"), rs.getObject("active_version", Integer.class),
            rs.getBoolean("enabled"), rs.getObject("policy_version_id", UUID.class), rs.getString("rule_type"),
            rs.getString("severity"), json.map(rs.getString("definition")),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
        this.evaluationMapper = (rs, rowNum) -> new PolicyEvaluation(
            rs.getObject("id", UUID.class), rs.getObject("change_set_id", UUID.class),
            rs.getObject("policy_version_id", UUID.class), rs.getString("policy_name"),
            rs.getInt("policy_version"), rs.getString("result"), json.list(rs.getString("evidence")),
            rs.getString("message"), rs.getTimestamp("evaluated_at").toInstant());
    }

    @Transactional
    public EvidencePolicy createVersion(UUID workspaceId, String name, String description, String ruleType,
            String severity, Map<String, Object> definition, UUID createdBy, boolean enable) {
        UUID policyId = jdbc.queryForList("SELECT id FROM evidence_policies WHERE workspace_id = ? AND name = ?",
            UUID.class, workspaceId, name).stream().findFirst().orElseGet(UUID::randomUUID);
        jdbc.update("""
            INSERT INTO evidence_policies (id, workspace_id, name, description, enabled)
            VALUES (?, ?, ?, ?, false)
            ON CONFLICT (workspace_id, name) DO UPDATE SET
                description = EXCLUDED.description, updated_at = now()
            """, policyId, workspaceId, name, description);
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) + 1 FROM evidence_policy_versions WHERE policy_id = ?",
            Integer.class, policyId);
        int version = next == null ? 1 : next;
        String definitionJson = json.write(definition);
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO evidence_policy_versions (
                id, policy_id, version, rule_type, severity, definition, definition_hash, created_by
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
            """, versionId, policyId, version, ruleType, severity, definitionJson,
            Hashing.sha256(definitionJson), createdBy);
        if (enable) {
            jdbc.update("UPDATE evidence_policies SET active_version = ?, enabled = true, updated_at = now() WHERE id = ?",
                version, policyId);
        }
        return findById(workspaceId, policyId).orElseThrow();
    }

    @Transactional
    public void ensureDefaults(UUID workspaceId) {
        createDefault(workspaceId, "Linked intent", "Require an explicit issue or requirement reference",
            "REQUIRE_LINKED_INTENT", "HIGH", "INTENT");
        createDefault(workspaceId, "Test evidence", "Require tests or successful checks for source changes",
            "REQUIRE_TEST_EVIDENCE", "HIGH", "TEST_EVIDENCE");
        createDefault(workspaceId, "Successful checks", "Require configured checks to complete successfully",
            "REQUIRE_SUCCESSFUL_CHECKS", "HIGH", "CHECK_EVIDENCE");
        createDefault(workspaceId, "Owner approval", "Require a matching CODEOWNER approval",
            "REQUIRE_OWNER_APPROVAL", "MEDIUM", "OWNER_APPROVAL");
        createDefault(workspaceId, "API compatibility", "Reject removed OpenAPI paths or operations",
            "REQUIRE_API_COMPATIBILITY", "CRITICAL", "API_COMPATIBILITY");
        createDefault(workspaceId, "API changelog", "Require release notes for breaking OpenAPI changes",
            "REQUIRE_API_CHANGELOG", "HIGH", "API_CHANGELOG");
        createDefault(workspaceId, "Rollback evidence", "Require rollback notes for schema migrations",
            "REQUIRE_ROLLBACK_PLAN", "HIGH", "ROLLBACK_EVIDENCE");
    }

    public List<EvidencePolicy> findByWorkspace(UUID workspaceId) {
        return jdbc.query(policySelect() + " WHERE policy.workspace_id = ? ORDER BY policy.created_at",
            policyMapper, workspaceId);
    }

    public List<EvidencePolicy> active(UUID workspaceId) {
        return jdbc.query(policySelect() + " WHERE policy.workspace_id = ? AND policy.enabled = true " +
            "AND version.version = policy.active_version ORDER BY policy.created_at", policyMapper, workspaceId);
    }

    public Optional<EvidencePolicy> findById(UUID workspaceId, UUID policyId) {
        return jdbc.query(policySelect() + " WHERE policy.workspace_id = ? AND policy.id = ? " +
            "ORDER BY version.version DESC LIMIT 1", policyMapper, workspaceId, policyId).stream().findFirst();
    }

    public boolean activate(UUID workspaceId, UUID policyId, int version, boolean enabled) {
        int exists = jdbc.queryForObject("SELECT count(*) FROM evidence_policy_versions version " +
            "JOIN evidence_policies policy ON policy.id = version.policy_id " +
            "WHERE policy.workspace_id = ? AND policy.id = ? AND version.version = ?", Integer.class,
            workspaceId, policyId, version);
        if (exists != 1) return false;
        return jdbc.update("UPDATE evidence_policies SET active_version = ?, enabled = ?, updated_at = now() " +
            "WHERE workspace_id = ? AND id = ?", version, enabled, workspaceId, policyId) == 1;
    }

    public boolean hasActiveException(UUID changeSetId, UUID policyVersionId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM evidence_policy_exceptions " +
            "WHERE change_set_id = ? AND policy_version_id = ? AND expires_at > now()", Integer.class,
            changeSetId, policyVersionId);
        return count != null && count > 0;
    }

    public void saveEvaluation(UUID changeSetId, UUID policyVersionId, String result,
            List<Map<String, Object>> evidence, String message) {
        jdbc.update("""
            INSERT INTO evidence_policy_evaluations (
                id, change_set_id, policy_version_id, result, evidence, message
            ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
            ON CONFLICT (change_set_id, policy_version_id) DO UPDATE SET
                result = EXCLUDED.result, evidence = EXCLUDED.evidence,
                message = EXCLUDED.message, evaluated_at = now()
            """, UUID.randomUUID(), changeSetId, policyVersionId, result, json.write(evidence), message);
    }

    public List<PolicyEvaluation> evaluations(UUID workspaceId, UUID changeSetId) {
        return jdbc.query("""
            SELECT evaluation.id, evaluation.change_set_id, evaluation.policy_version_id,
                   policy.name AS policy_name, version.version AS policy_version,
                   evaluation.result, evaluation.evidence::text AS evidence,
                   evaluation.message, evaluation.evaluated_at
            FROM evidence_policy_evaluations evaluation
            JOIN evidence_policy_versions version ON version.id = evaluation.policy_version_id
            JOIN evidence_policies policy ON policy.id = version.policy_id
            JOIN change_sets change ON change.id = evaluation.change_set_id
            WHERE change.workspace_id = ? AND change.id = ?
            ORDER BY policy.created_at
            """, evaluationMapper, workspaceId, changeSetId);
    }

    public UUID createException(UUID workspaceId, UUID changeSetId, UUID policyVersionId,
            String rationale, UUID approverId, java.time.Instant expiresAt) {
        Integer authorized = jdbc.queryForObject("SELECT count(*) FROM change_sets change " +
            "JOIN evidence_policy_versions version ON version.id = ? " +
            "JOIN evidence_policies policy ON policy.id = version.policy_id " +
            "WHERE change.id = ? AND change.workspace_id = ? AND policy.workspace_id = ?",
            Integer.class, policyVersionId, changeSetId, workspaceId, workspaceId);
        if (authorized == null || authorized != 1) throw new IllegalArgumentException("Policy and change must share a workspace");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO evidence_policy_exceptions " +
            "(id, change_set_id, policy_version_id, rationale, approved_by, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
            id, changeSetId, policyVersionId, rationale, approverId, java.sql.Timestamp.from(expiresAt));
        return id;
    }

    private void createDefault(UUID workspaceId, String name, String description, String ruleType,
            String severity, String category) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM evidence_policies WHERE workspace_id = ? AND name = ?",
            Integer.class, workspaceId, name);
        if (count == null || count == 0) {
            createVersion(workspaceId, name, description, ruleType, severity,
                Map.of("findingCategory", category), null, true);
        }
    }

    private String policySelect() {
        return """
            SELECT policy.id, policy.workspace_id, policy.name, policy.description,
                   policy.active_version, policy.enabled, version.id AS policy_version_id,
                   version.rule_type, version.severity, version.definition::text AS definition,
                   policy.created_at, policy.updated_at
            FROM evidence_policies policy
            LEFT JOIN evidence_policy_versions version ON version.policy_id = policy.id
                AND version.version = COALESCE(policy.active_version,
                    (SELECT MAX(latest.version) FROM evidence_policy_versions latest WHERE latest.policy_id = policy.id))
            """;
    }
}
