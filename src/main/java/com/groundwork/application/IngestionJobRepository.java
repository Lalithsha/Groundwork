package com.groundwork.application;

import com.groundwork.domain.model.IngestionJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class IngestionJobRepository {
    private static final String COLUMNS = """
        id, document_id, workspace_id, status, progress_current, progress_total,
        attempts, max_attempts, error_message, created_at, started_at, completed_at
        """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<IngestionJob> mapper = (rs, rowNum) -> new IngestionJob(
        rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
        rs.getObject("workspace_id", UUID.class), rs.getString("status"),
        rs.getInt("progress_current"), rs.getInt("progress_total"),
        rs.getInt("attempts"), rs.getInt("max_attempts"), rs.getString("error_message"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant(),
        rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant()
    );

    public IngestionJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public IngestionJob create(UUID documentId, UUID workspaceId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO ingestion_jobs (id, document_id, workspace_id) VALUES (?, ?, ?)",
            id, documentId, workspaceId);
        return findById(id).orElseThrow();
    }

    public Optional<IngestionJob> findActiveByDocumentId(UUID documentId) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM ingestion_jobs WHERE document_id = ? " +
            "AND status IN ('QUEUED', 'RUNNING', 'RETRYING') ORDER BY created_at DESC LIMIT 1", mapper, documentId)
            .stream().findFirst();
    }

    public Optional<IngestionJob> findById(UUID id) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM ingestion_jobs WHERE id = ?", mapper, id)
            .stream().findFirst();
    }

    public Optional<IngestionJob> claimNext(String workerId) {
        String sql = """
            WITH candidate AS (
                SELECT id FROM ingestion_jobs
                WHERE status IN ('QUEUED', 'RETRYING') AND available_at <= now()
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE ingestion_jobs job
            SET status = 'RUNNING', locked_at = now(), locked_by = ?,
                attempts = attempts + 1, started_at = COALESCE(started_at, now()), updated_at = now()
            FROM candidate
            WHERE job.id = candidate.id
            RETURNING job.id, job.document_id, job.workspace_id, job.status,
                      job.progress_current, job.progress_total, job.attempts,
                      job.max_attempts, job.error_message, job.created_at,
                      job.started_at, job.completed_at
            """;
        return jdbcTemplate.query(sql, mapper, workerId).stream().findFirst();
    }

    public void updateProgress(UUID id, int current, int total) {
        jdbcTemplate.update("UPDATE ingestion_jobs SET progress_current = ?, progress_total = ?, updated_at = now() WHERE id = ?",
            current, total, id);
    }

    public void complete(UUID id, int total) {
        jdbcTemplate.update("""
            UPDATE ingestion_jobs SET status = 'COMPLETED', progress_current = ?, progress_total = ?,
                completed_at = now(), locked_at = NULL, locked_by = NULL, error_message = NULL, updated_at = now()
            WHERE id = ?
            """, total, total, id);
    }

    public boolean retryOrFail(IngestionJob job, String message, int backoffSeconds) {
        boolean retry = job.attempts() < job.maxAttempts();
        if (retry) {
            jdbcTemplate.update("""
                UPDATE ingestion_jobs SET status = 'RETRYING', available_at = now() + (? * interval '1 second'),
                    locked_at = NULL, locked_by = NULL, error_message = ?, updated_at = now() WHERE id = ?
                """, backoffSeconds, truncate(message), job.id());
        } else {
            jdbcTemplate.update("""
                UPDATE ingestion_jobs SET status = 'FAILED', completed_at = now(), locked_at = NULL,
                    locked_by = NULL, error_message = ?, updated_at = now() WHERE id = ?
                """, truncate(message), job.id());
        }
        return retry;
    }

    public int recoverStale(int staleAfterSeconds) {
        int recovered = jdbcTemplate.update("""
            UPDATE ingestion_jobs
            SET status = CASE WHEN attempts < max_attempts THEN 'RETRYING' ELSE 'FAILED' END,
                available_at = now(), locked_at = NULL, locked_by = NULL,
                error_message = 'Worker lease expired; job recovered', updated_at = now(),
                completed_at = CASE WHEN attempts >= max_attempts THEN now() ELSE completed_at END
            WHERE status = 'RUNNING' AND locked_at < now() - (? * interval '1 second')
            """, staleAfterSeconds);
        jdbcTemplate.update("""
            UPDATE source_documents source SET status = 'FAILED',
                error_message = failed.error_message, updated_at = now()
            FROM ingestion_jobs failed
            WHERE failed.document_id = source.id AND failed.status = 'FAILED'
              AND source.status = 'PROCESSING'
            """);
        return recovered;
    }

    public boolean cancel(UUID id) {
        return jdbcTemplate.update("""
            UPDATE ingestion_jobs SET status = 'CANCELLED', completed_at = now(),
                locked_at = NULL, locked_by = NULL, updated_at = now()
            WHERE id = ? AND status IN ('QUEUED', 'RETRYING')
            """, id) == 1;
    }

    private String truncate(String message) {
        if (message == null) return "Unknown ingestion failure";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
