package com.groundwork.evidence.application;

import com.groundwork.evidence.domain.AnalysisJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
public class ChangeAnalysisWorker {
    private static final Logger log = LoggerFactory.getLogger(ChangeAnalysisWorker.class);
    private final ChangeSetRepository jobs;
    private final ChangeAnalysisService analysis;
    private final String workerId;
    private final int staleAfterSeconds;

    public ChangeAnalysisWorker(ChangeSetRepository jobs, ChangeAnalysisService analysis,
            @Value("${groundwork.analysis.worker-id:}") String configuredWorkerId,
            @Value("${groundwork.analysis.stale-after-seconds:300}") int staleAfterSeconds) {
        this.jobs = jobs;
        this.analysis = analysis;
        this.workerId = configuredWorkerId == null || configuredWorkerId.isBlank()
            ? "analysis-" + ManagementFactory.getRuntimeMXBean().getName() : configuredWorkerId;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    @Scheduled(fixedDelayString = "${groundwork.analysis.poll-delay-ms:750}")
    public void poll() {
        int recovered = jobs.recoverStale(staleAfterSeconds);
        if (recovered > 0) log.warn("Recovered {} stale analysis job(s)", recovered);
        jobs.claimAnalysis(workerId).ifPresent(this::processSafely);
    }

    private void processSafely(AnalysisJob job) {
        try { analysis.analyze(job); }
        catch (Exception exception) {
            boolean retry = jobs.retryOrFail(job, exception.getMessage());
            log.warn("Change analysis {} {}: {}", job.id(), retry ? "will retry" : "failed", exception.getMessage());
        }
    }
}
