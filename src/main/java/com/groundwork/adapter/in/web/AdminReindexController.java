package com.groundwork.adapter.in.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reindex")
public class AdminReindexController {

    private final JdbcTemplate jdbcTemplate;

    public AdminReindexController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping
    public ResponseEntity<?> triggerReindex() {
        UUID jobId = UUID.randomUUID();
        try {
            String sql = "INSERT INTO reindex_jobs (id, status, started_at) VALUES (?, 'pending', ?)";
            jdbcTemplate.update(sql, jobId, Instant.now());
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Reindex job already in progress"));
        }

        runAsyncReindex(jobId);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "pending"));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable UUID jobId) {
        String sql = "SELECT status, started_at, completed_at, error_message FROM reindex_jobs WHERE id = ?";
        var result = jdbcTemplate.queryForList(sql, jobId);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result.get(0));
    }

    @Async
    public void runAsyncReindex(UUID jobId) {
        try {
            jdbcTemplate.update("UPDATE reindex_jobs SET status = 'running' WHERE id = ?", jobId);
            // Simulate corpus fetching and re-indexing chunk processing
            Thread.sleep(2000);
            jdbcTemplate.update("UPDATE reindex_jobs SET status = 'completed', completed_at = ? WHERE id = ?", Instant.now(), jobId);
        } catch (Exception e) {
            jdbcTemplate.update("UPDATE reindex_jobs SET status = 'failed', error_message = ? WHERE id = ?", e.getMessage(), jobId);
        }
    }
}
