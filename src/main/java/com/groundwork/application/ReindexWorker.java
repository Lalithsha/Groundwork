package com.groundwork.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.lang.management.ManagementFactory;

@Component
public class ReindexWorker {
    private static final Logger log = LoggerFactory.getLogger(ReindexWorker.class);

    private final ReindexJobRepository jobs;
    private final SourceDocumentRepository documents;
    private final DocumentIngestionService ingestion;
    private final String workerId = "reindex-" + ManagementFactory.getRuntimeMXBean().getName();
    private final int maxAttempts;
    private final int staleAfterSeconds;

    public ReindexWorker(ReindexJobRepository jobs, SourceDocumentRepository documents,
            DocumentIngestionService ingestion,
            @Value("${groundwork.reindex.max-attempts:3}") int maxAttempts,
            @Value("${groundwork.reindex.stale-after-seconds:900}") int staleAfterSeconds) {
        this.jobs = jobs;
        this.documents = documents;
        this.ingestion = ingestion;
        this.maxAttempts = maxAttempts;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    @Scheduled(fixedDelayString = "${groundwork.reindex.poll-delay-ms:2000}")
    public void poll() {
        int recovered = jobs.recoverStale(staleAfterSeconds, maxAttempts);
        if (recovered > 0) log.warn("Recovered {} stale reindex job(s)", recovered);
        jobs.claimNext(workerId).ifPresent(job -> {
            try {
                var sources = documents.findByWorkspace(job.workspaceId()).stream()
                    .filter(document -> "READY".equals(document.status()) || "FAILED".equals(document.status()))
                    .toList();
                jobs.progress(job.id(), 0, sources.size());
                for (int index = 0; index < sources.size(); index++) {
                    ingestion.reindexDocument(sources.get(index).id());
                    jobs.progress(job.id(), index + 1, sources.size());
                }
                jobs.complete(job.id(), sources.size());
            } catch (Exception exception) {
                int backoffSeconds = Math.min(120, 1 << Math.min(job.attempts(), 6));
                boolean retrying = jobs.retryOrFail(job, exception.getMessage(), backoffSeconds, maxAttempts);
                log.warn("Reindex job {} {}: {}", job.id(), retrying ? "will retry" : "failed", exception.getMessage());
            }
        });
    }
}
