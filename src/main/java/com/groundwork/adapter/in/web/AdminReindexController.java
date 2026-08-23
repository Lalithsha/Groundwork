package com.groundwork.adapter.in.web;

import com.groundwork.application.ReindexJobRepository;
import com.groundwork.domain.model.ReindexJob;
import com.groundwork.application.WorkspaceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reindex")
public class AdminReindexController {
    private final ReindexJobRepository jobs;
    private final WorkspaceAccessService access;

    public AdminReindexController(ReindexJobRepository jobs, WorkspaceAccessService access) {
        this.jobs = jobs;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<?> triggerReindex(@RequestParam(required = false) UUID workspaceId) {
        access.requireAdmin(workspaceId);
        return jobs.create(workspaceId)
            .<ResponseEntity<?>>map(job -> ResponseEntity.accepted().body(job))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "A reindex job is already active")));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ReindexJob> getJobStatus(@PathVariable UUID jobId) {
        var job = jobs.findById(jobId);
        if (job.isEmpty()) return ResponseEntity.notFound().build();
        access.requireAdmin(job.get().workspaceId());
        return ResponseEntity.ok(job.get());
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<?> cancel(@PathVariable UUID jobId) {
        var job = jobs.findById(jobId);
        if (job.isEmpty()) return ResponseEntity.notFound().build();
        access.requireAdmin(job.get().workspaceId());
        if (!jobs.cancel(jobId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Only pending jobs can be cancelled"));
        }
        return ResponseEntity.noContent().build();
    }
}
