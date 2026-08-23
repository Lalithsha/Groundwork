package com.groundwork.evidence.application;

import com.groundwork.evidence.domain.AnalysisJob;
import com.groundwork.evidence.domain.ChangeFinding;
import com.groundwork.evidence.domain.ChangeSet;
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
public class ChangeSetRepository {
    public static final String ANALYZER_VERSION = "groundwork-change-v1";
    private static final String CHANGE_COLUMNS = """
        id, workspace_id, connection_id, repository_external_id, repository_full_name,
        pull_request_number, external_change_id, title, description, author_login, base_sha,
        head_sha, source_branch, target_branch, state, canonical_url, current_analysis_status,
        metadata::text AS metadata, opened_at, merged_at, created_at, updated_at
        """;
    private static final String JOB_COLUMNS = """
        id, change_set_id, head_sha, analyzer_version, status, attempts, max_attempts,
        last_error, started_at, completed_at, created_at
        """;

    private final JdbcTemplate jdbc;
    private final EvidenceJson json;
    private final RowMapper<ChangeSet> changeMapper;
    private final RowMapper<AnalysisJob> jobMapper;
    private final RowMapper<ChangeFinding> findingMapper;

    public ChangeSetRepository(JdbcTemplate jdbc, EvidenceJson json) {
        this.jdbc = jdbc;
        this.json = json;
        this.changeMapper = (rs, rowNum) -> new ChangeSet(
            rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
            rs.getObject("connection_id", UUID.class), rs.getString("repository_external_id"),
            rs.getString("repository_full_name"), rs.getObject("pull_request_number", Integer.class),
            rs.getString("external_change_id"), rs.getString("title"), rs.getString("description"),
            rs.getString("author_login"), rs.getString("base_sha"), rs.getString("head_sha"),
            rs.getString("source_branch"), rs.getString("target_branch"), rs.getString("state"),
            rs.getString("canonical_url"), rs.getString("current_analysis_status"),
            json.map(rs.getString("metadata")), instant(rs.getTimestamp("opened_at")),
            instant(rs.getTimestamp("merged_at")), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
        this.jobMapper = (rs, rowNum) -> new AnalysisJob(
            rs.getObject("id", UUID.class), rs.getObject("change_set_id", UUID.class),
            rs.getString("head_sha"), rs.getString("analyzer_version"), rs.getString("status"),
            rs.getInt("attempts"), rs.getInt("max_attempts"), rs.getString("last_error"),
            instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("completed_at")),
            rs.getTimestamp("created_at").toInstant());
        this.findingMapper = (rs, rowNum) -> new ChangeFinding(
            rs.getObject("id", UUID.class), rs.getObject("change_set_id", UUID.class),
            rs.getObject("analysis_job_id", UUID.class), rs.getString("finding_key"),
            rs.getString("analyzer"), rs.getString("analyzer_version"), rs.getString("category"),
            rs.getString("severity"), rs.getString("statement"), rs.getBoolean("deterministic"),
            rs.getString("evidence_status"), rs.getString("confidence"),
            json.list(rs.getString("citations")), json.map(rs.getString("details")),
            rs.getString("review_status"), rs.getString("review_reason"),
            instant(rs.getTimestamp("reviewed_at")), rs.getTimestamp("created_at").toInstant());
    }

    @Transactional
    public ChangeSet upsertAndQueue(ChangeInput input) {
        UUID id = findExact(input.workspaceId(), input.repositoryExternalId(), input.externalChangeId(), input.headSha())
            .map(ChangeSet::id).orElseGet(UUID::randomUUID);
        if (input.pullRequestNumber() != null) {
            jdbc.update("""
                UPDATE change_sets SET current_analysis_status = 'STALE', updated_at = now()
                WHERE workspace_id = ? AND repository_external_id = ? AND pull_request_number = ?
                  AND head_sha <> ? AND current_analysis_status <> 'STALE'
                """, input.workspaceId(), input.repositoryExternalId(), input.pullRequestNumber(), input.headSha());
            jdbc.update("""
                UPDATE analysis_jobs job SET status = 'SUPERSEDED', completed_at = now()
                FROM change_sets change
                WHERE job.change_set_id = change.id AND change.workspace_id = ?
                  AND change.repository_external_id = ? AND change.pull_request_number = ?
                  AND job.head_sha <> ? AND job.status IN ('QUEUED', 'RETRYING')
                """, input.workspaceId(), input.repositoryExternalId(), input.pullRequestNumber(), input.headSha());
        }
        jdbc.update("""
            INSERT INTO change_sets (
                id, workspace_id, connection_id, repository_external_id, repository_full_name,
                pull_request_number, external_change_id, title, description, author_login,
                base_sha, head_sha, source_branch, target_branch, state, canonical_url,
                current_analysis_status, metadata, opened_at, merged_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', CAST(? AS jsonb), ?, ?)
            ON CONFLICT (workspace_id, repository_external_id, external_change_id, head_sha) DO UPDATE SET
                title = EXCLUDED.title, description = EXCLUDED.description, author_login = EXCLUDED.author_login,
                state = EXCLUDED.state, canonical_url = EXCLUDED.canonical_url,
                source_branch = EXCLUDED.source_branch, target_branch = EXCLUDED.target_branch,
                metadata = EXCLUDED.metadata, merged_at = EXCLUDED.merged_at, updated_at = now(),
                current_analysis_status = CASE
                    WHEN change_sets.current_analysis_status = 'COMPLETED' THEN 'COMPLETED' ELSE 'QUEUED' END
            """, id, input.workspaceId(), input.connectionId(), input.repositoryExternalId(),
            input.repositoryFullName(), input.pullRequestNumber(), input.externalChangeId(), input.title(),
            input.description(), input.authorLogin(), input.baseSha(), input.headSha(), input.sourceBranch(),
            input.targetBranch(), input.state(), input.canonicalUrl(), json.write(input.metadata()),
            timestamp(input.openedAt()), timestamp(input.mergedAt()));
        ChangeSet change = findExact(input.workspaceId(), input.repositoryExternalId(), input.externalChangeId(), input.headSha())
            .orElseThrow();
        queueAnalysis(change.id(), change.headSha(), ANALYZER_VERSION);
        return change;
    }

    public AnalysisJob queueAnalysis(UUID changeSetId, String headSha, String analyzerVersion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO analysis_jobs (id, change_set_id, head_sha, analyzer_version)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (change_set_id, head_sha, analyzer_version) DO UPDATE SET
                status = CASE
                    WHEN analysis_jobs.status IN ('FAILED', 'CANCELLED', 'COMPLETED', 'PARTIAL')
                    THEN 'QUEUED' ELSE analysis_jobs.status END,
                attempts = CASE
                    WHEN analysis_jobs.status IN ('FAILED', 'CANCELLED', 'COMPLETED', 'PARTIAL')
                    THEN 0 ELSE analysis_jobs.attempts END,
                available_at = CASE
                    WHEN analysis_jobs.status IN ('FAILED', 'CANCELLED', 'COMPLETED', 'PARTIAL')
                    THEN now() ELSE analysis_jobs.available_at END,
                started_at = CASE
                    WHEN analysis_jobs.status IN ('FAILED', 'CANCELLED', 'COMPLETED', 'PARTIAL')
                    THEN NULL ELSE analysis_jobs.started_at END,
                completed_at = CASE
                    WHEN analysis_jobs.status IN ('FAILED', 'CANCELLED', 'COMPLETED', 'PARTIAL')
                    THEN NULL ELSE analysis_jobs.completed_at END,
                last_error = CASE
                    WHEN analysis_jobs.status IN ('FAILED', 'CANCELLED', 'COMPLETED', 'PARTIAL')
                    THEN NULL ELSE analysis_jobs.last_error END
            """, id, changeSetId, headSha, analyzerVersion);
        jdbc.update("UPDATE change_sets SET current_analysis_status = 'QUEUED', updated_at = now() WHERE id = ?",
            changeSetId);
        return findJob(changeSetId, headSha, analyzerVersion).orElseThrow();
    }

    public List<ChangeSet> findByWorkspace(UUID workspaceId, String state, int limit) {
        return jdbc.query("SELECT " + CHANGE_COLUMNS + " FROM change_sets WHERE workspace_id = ? " +
            "AND (CAST(? AS text) IS NULL OR state = CAST(? AS text)) ORDER BY updated_at DESC LIMIT ?", changeMapper,
            workspaceId, state, state, limit);
    }

    public Optional<ChangeSet> findAuthorized(UUID workspaceId, UUID id) {
        return jdbc.query("SELECT " + CHANGE_COLUMNS + " FROM change_sets WHERE id = ? AND workspace_id = ?",
            changeMapper, id, workspaceId).stream().findFirst();
    }

    public Optional<ChangeSet> findById(UUID id) {
        return jdbc.query("SELECT " + CHANGE_COLUMNS + " FROM change_sets WHERE id = ?", changeMapper, id)
            .stream().findFirst();
    }

    public Optional<ChangeSet> findExact(UUID workspaceId, String repositoryExternalId,
            String externalChangeId, String headSha) {
        return jdbc.query("SELECT " + CHANGE_COLUMNS + " FROM change_sets WHERE workspace_id = ? " +
            "AND repository_external_id = ? AND external_change_id = ? AND head_sha = ?", changeMapper,
            workspaceId, repositoryExternalId, externalChangeId, headSha).stream().findFirst();
    }

    public Optional<AnalysisJob> findJob(UUID changeSetId, String headSha, String analyzerVersion) {
        return jdbc.query("SELECT " + JOB_COLUMNS + " FROM analysis_jobs WHERE change_set_id = ? " +
            "AND head_sha = ? AND analyzer_version = ?", jobMapper, changeSetId, headSha, analyzerVersion)
            .stream().findFirst();
    }

    public Optional<AnalysisJob> claimAnalysis(String workerId) {
        return jdbc.query("""
            WITH candidate AS (
                SELECT id FROM analysis_jobs
                WHERE status IN ('QUEUED', 'RETRYING') AND available_at <= now()
                ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1
            )
            UPDATE analysis_jobs job SET status = 'RUNNING', attempts = attempts + 1,
                locked_at = now(), locked_by = ?, started_at = COALESCE(started_at, now())
            FROM candidate WHERE job.id = candidate.id
            RETURNING job.id, job.change_set_id, job.head_sha, job.analyzer_version, job.status,
                      job.attempts, job.max_attempts, job.last_error, job.started_at,
                      job.completed_at, job.created_at
            """, jobMapper, workerId).stream().findFirst();
    }

    @Transactional
    public void saveFinding(ChangeFindingDraft draft) {
        jdbc.update("""
            INSERT INTO change_findings (
                id, change_set_id, analysis_job_id, finding_key, analyzer, analyzer_version,
                category, severity, statement, deterministic, evidence_status, confidence,
                citations, details
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
            ON CONFLICT (change_set_id, analyzer_version, finding_key) DO UPDATE SET
                analysis_job_id = EXCLUDED.analysis_job_id,
                category = EXCLUDED.category,
                severity = EXCLUDED.severity,
                statement = EXCLUDED.statement,
                deterministic = EXCLUDED.deterministic,
                evidence_status = EXCLUDED.evidence_status,
                confidence = EXCLUDED.confidence,
                citations = EXCLUDED.citations,
                details = EXCLUDED.details,
                created_at = now()
            """, UUID.randomUUID(), draft.changeSetId(), draft.analysisJobId(), draft.findingKey(),
            draft.analyzer(), draft.analyzerVersion(), draft.category(), draft.severity(), draft.statement(),
            draft.deterministic(), draft.evidenceStatus(), draft.confidence(), json.write(draft.citations()),
            json.write(draft.details()));
    }

    public List<ChangeFinding> findings(UUID workspaceId, UUID changeSetId) {
        return jdbc.query("""
            SELECT finding.id, finding.change_set_id, finding.analysis_job_id, finding.finding_key,
                   finding.analyzer, finding.analyzer_version, finding.category, finding.severity,
                   finding.statement, finding.deterministic, finding.evidence_status, finding.confidence,
                   finding.citations::text AS citations, finding.details::text AS details,
                   finding.review_status, finding.review_reason, finding.reviewed_at, finding.created_at
            FROM change_findings finding
            JOIN change_sets change ON change.id = finding.change_set_id
            WHERE change.workspace_id = ? AND change.id = ?
            ORDER BY CASE finding.severity WHEN 'CRITICAL' THEN 5 WHEN 'HIGH' THEN 4
                     WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 2 ELSE 1 END DESC, finding.created_at
            """, findingMapper, workspaceId, changeSetId);
    }

    @Transactional
    public boolean reviewFinding(UUID workspaceId, UUID changeSetId, UUID findingId, UUID reviewerId,
            String status, String reason, String reasonCode) {
        int updated = jdbc.update("""
            UPDATE change_findings finding SET review_status = ?, review_reason = ?, reviewed_by = ?, reviewed_at = now()
            FROM change_sets change
            WHERE finding.change_set_id = change.id AND finding.id = ? AND change.id = ? AND change.workspace_id = ?
            """, status, reason, reviewerId, findingId, changeSetId, workspaceId);
        if (updated == 1) {
            jdbc.update("""
                INSERT INTO finding_feedback_events
                    (id, workspace_id, change_set_id, finding_id, actor_user_id, action, reason_code, comment)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), workspaceId, changeSetId, findingId, reviewerId,
                status, reasonCode, reason);
        }
        return updated == 1;
    }

    public List<Map<String, Object>> findingFeedback(UUID workspaceId, UUID changeSetId) {
        return jdbc.query("""
            SELECT event.id, event.finding_id, event.action, event.reason_code, event.comment,
                   event.created_at, actor.email AS actor_email
            FROM finding_feedback_events event
            LEFT JOIN users actor ON actor.id = event.actor_user_id
            WHERE event.workspace_id = ? AND event.change_set_id = ?
            ORDER BY event.created_at DESC
            """, (rs, rowNum) -> {
                Map<String, Object> value = new java.util.LinkedHashMap<>();
                value.put("id", rs.getObject("id", UUID.class));
                value.put("findingId", rs.getObject("finding_id", UUID.class));
                value.put("action", rs.getString("action"));
                value.put("reasonCode", rs.getString("reason_code"));
                value.put("comment", rs.getString("comment"));
                value.put("actor", rs.getString("actor_email"));
                value.put("createdAt", rs.getTimestamp("created_at").toInstant());
                return value;
            }, workspaceId, changeSetId);
    }

    @Transactional
    public void completeAnalysis(AnalysisJob job, boolean partial) {
        String status = partial ? "PARTIAL" : "COMPLETED";
        jdbc.update("UPDATE analysis_jobs SET status = ?, completed_at = now(), locked_at = NULL, " +
            "locked_by = NULL, last_error = NULL WHERE id = ?", status, job.id());
        jdbc.update("UPDATE change_sets SET current_analysis_status = ?, updated_at = now() WHERE id = ?",
            status, job.changeSetId());
    }

    @Transactional
    public boolean retryOrFail(AnalysisJob job, String message) {
        boolean retry = job.attempts() < job.maxAttempts();
        if (retry) {
            int seconds = Math.min(60, 1 << Math.min(job.attempts(), 5));
            jdbc.update("UPDATE analysis_jobs SET status = 'RETRYING', available_at = now() + (? * interval '1 second'), " +
                "locked_at = NULL, locked_by = NULL, last_error = ? WHERE id = ?", seconds, truncate(message), job.id());
        } else {
            jdbc.update("UPDATE analysis_jobs SET status = 'FAILED', completed_at = now(), locked_at = NULL, " +
                "locked_by = NULL, last_error = ? WHERE id = ?", truncate(message), job.id());
            jdbc.update("UPDATE change_sets SET current_analysis_status = 'FAILED', updated_at = now() WHERE id = ?",
                job.changeSetId());
        }
        return retry;
    }

    public int recoverStale(int staleSeconds) {
        return jdbc.update("""
            UPDATE analysis_jobs SET status = CASE WHEN attempts < max_attempts THEN 'RETRYING' ELSE 'FAILED' END,
                available_at = now(), locked_at = NULL, locked_by = NULL,
                last_error = 'Analysis worker lease expired; job recovered',
                completed_at = CASE WHEN attempts >= max_attempts THEN now() ELSE completed_at END
            WHERE status = 'RUNNING' AND locked_at < now() - (? * interval '1 second')
            """, staleSeconds);
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private String truncate(String value) {
        if (value == null) return "Change analysis failed";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public record ChangeInput(UUID workspaceId, UUID connectionId, String repositoryExternalId,
            String repositoryFullName, Integer pullRequestNumber, String externalChangeId, String title,
            String description, String authorLogin, String baseSha, String headSha, String sourceBranch,
            String targetBranch, String state, String canonicalUrl, Map<String, Object> metadata,
            Instant openedAt, Instant mergedAt) {}

    public record ChangeFindingDraft(UUID changeSetId, UUID analysisJobId, String findingKey,
            String analyzer, String analyzerVersion, String category, String severity, String statement,
            boolean deterministic, String evidenceStatus, String confidence,
            List<Map<String, Object>> citations, Map<String, Object> details) {}
}
