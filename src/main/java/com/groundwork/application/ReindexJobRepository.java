package com.groundwork.application;

import com.groundwork.domain.model.ReindexJob;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ReindexJobRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ReindexJob> mapper = (rs, rowNum) -> new ReindexJob(
        rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
        rs.getString("status"), rs.getInt("progress_current"), rs.getInt("progress_total"),
        rs.getInt("attempts"), rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant(),
        rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant());

    public ReindexJobRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<ReindexJob> create(UUID workspaceId) {
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update("INSERT INTO reindex_jobs (id, workspace_id, status) VALUES (?, ?, 'pending')", id, workspaceId);
            return findById(id);
        } catch (DataIntegrityViolationException exception) {
            return Optional.empty();
        }
    }

    public Optional<ReindexJob> findById(UUID id) {
        return jdbcTemplate.query("""
            SELECT id, workspace_id, status, progress_current, progress_total, attempts,
                   error_message, created_at, started_at, completed_at
            FROM reindex_jobs WHERE id = ?
            """, mapper, id).stream().findFirst();
    }

    public Optional<ReindexJob> claimNext(String workerId) {
        return jdbcTemplate.query("""
            WITH candidate AS (
                SELECT id FROM reindex_jobs
                WHERE status IN ('pending', 'retrying') AND available_at <= now()
                ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1
            )
            UPDATE reindex_jobs job
            SET status = 'running', locked_at = now(), locked_by = ?, attempts = attempts + 1,
                started_at = COALESCE(started_at, now())
            FROM candidate WHERE job.id = candidate.id
            RETURNING job.id, job.workspace_id, job.status, job.progress_current,
                      job.progress_total, job.attempts, job.error_message, job.created_at,
                      job.started_at, job.completed_at
            """, mapper, workerId).stream().findFirst();
    }

    public void progress(UUID id, int current, int total) {
        jdbcTemplate.update("UPDATE reindex_jobs SET progress_current = ?, progress_total = ? WHERE id = ?", current, total, id);
    }

    public void complete(UUID id, int total) {
        jdbcTemplate.update("""
            UPDATE reindex_jobs SET status = 'completed', progress_current = ?, progress_total = ?,
                completed_at = now(), locked_at = NULL, locked_by = NULL WHERE id = ?
            """, total, total, id);
    }

    public void fail(UUID id, String message) {
        jdbcTemplate.update("""
            UPDATE reindex_jobs SET status = 'failed', error_message = ?, completed_at = now(),
                locked_at = NULL, locked_by = NULL WHERE id = ?
            """, message == null ? "Unknown reindex failure" : message.substring(0, Math.min(1000, message.length())), id);
    }

    public boolean retryOrFail(ReindexJob job, String message, int backoffSeconds, int maxAttempts) {
        String safeMessage = message == null ? "Unknown reindex failure" : message.substring(0, Math.min(1000, message.length()));
        if (job.attempts() < maxAttempts) {
            jdbcTemplate.update("""
                UPDATE reindex_jobs SET status = 'retrying', available_at = now() + (? * interval '1 second'),
                    locked_at = NULL, locked_by = NULL, error_message = ? WHERE id = ?
                """, backoffSeconds, safeMessage, job.id());
            return true;
        }
        fail(job.id(), safeMessage);
        return false;
    }

    public int recoverStale(int staleAfterSeconds, int maxAttempts) {
        int recovered = jdbcTemplate.update("""
            UPDATE reindex_jobs
            SET status = CASE WHEN attempts < ? THEN 'retrying' ELSE 'failed' END,
                available_at = now(), locked_at = NULL, locked_by = NULL,
                error_message = 'Worker lease expired; job recovered',
                completed_at = CASE WHEN attempts >= ? THEN now() ELSE completed_at END
            WHERE status = 'running' AND locked_at < now() - (? * interval '1 second')
            """, maxAttempts, maxAttempts, staleAfterSeconds);
        jdbcTemplate.update("""
            UPDATE source_documents source SET status = 'FAILED',
                error_message = 'Reindex worker lease expired', updated_at = now()
            FROM reindex_jobs failed
            WHERE failed.status = 'failed'
              AND source.workspace_id IS NOT DISTINCT FROM failed.workspace_id
              AND source.status = 'PROCESSING'
              AND NOT EXISTS (
                  SELECT 1 FROM ingestion_jobs ingestion
                  WHERE ingestion.document_id = source.id AND ingestion.status IN ('QUEUED', 'RUNNING', 'RETRYING')
              )
            """);
        return recovered;
    }

    public boolean cancel(UUID id) {
        return jdbcTemplate.update("""
            UPDATE reindex_jobs SET status = 'cancelled', completed_at = now(),
                locked_at = NULL, locked_by = NULL
            WHERE id = ? AND status IN ('pending', 'retrying')
            """, id) == 1;
    }
}
