package com.groundwork.application;

import com.groundwork.domain.model.IngestionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
public class IngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final IngestionJobRepository jobs;
    private final DocumentIngestionService ingestion;
    private final SourceDocumentRepository documents;
    private final String workerId;
    private final int staleAfterSeconds;

    public IngestionWorker(IngestionJobRepository jobs, DocumentIngestionService ingestion,
            SourceDocumentRepository documents, @Value("${groundwork.ingestion.worker-id:}") String configuredId,
            @Value("${groundwork.ingestion.stale-after-seconds:300}") int staleAfterSeconds) {
        this.jobs = jobs;
        this.ingestion = ingestion;
        this.documents = documents;
        this.workerId = configuredId == null || configuredId.isBlank()
            ? ManagementFactory.getRuntimeMXBean().getName() : configuredId;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    @Scheduled(fixedDelayString = "${groundwork.ingestion.poll-delay-ms:1000}")
    public void poll() {
        int recovered = jobs.recoverStale(staleAfterSeconds);
        if (recovered > 0) log.warn("Recovered {} stale ingestion job(s)", recovered);
        jobs.claimNext(workerId).ifPresent(this::processSafely);
    }

    private void processSafely(IngestionJob job) {
        try {
            ingestion.process(job);
        } catch (Exception exception) {
            int backoffSeconds = Math.min(60, 1 << Math.min(job.attempts(), 5));
            boolean retrying = jobs.retryOrFail(job, exception.getMessage(), backoffSeconds);
            if (!retrying) documents.markFailed(job.documentId(), exception.getMessage());
            log.warn("Ingestion job {} {}: {}", job.id(), retrying ? "will retry" : "failed", exception.getMessage());
        }
    }
}
