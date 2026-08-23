package com.groundwork.evidence.application;

import com.groundwork.evidence.application.port.out.SourceControlPort;
import com.groundwork.evidence.domain.AnalysisJob;
import com.groundwork.evidence.domain.PolicyEvaluation;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChangeAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(ChangeAnalysisService.class);
    private final ChangeSetRepository changes;
    private final DeterministicChangeAnalyzer deterministic;
    private final GroundedChangeAnalysisService grounded;
    private final PolicyEvaluationService policyEvaluation;
    private final ConnectorRepository connections;
    private final SourceControlPort sourceControl;
    private final MeterRegistry metrics;
    private final String publicBaseUrl;

    public ChangeAnalysisService(ChangeSetRepository changes, DeterministicChangeAnalyzer deterministic,
            GroundedChangeAnalysisService grounded, PolicyEvaluationService policyEvaluation,
            ConnectorRepository connections, SourceControlPort sourceControl, MeterRegistry metrics,
            @Value("${groundwork.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.changes = changes;
        this.deterministic = deterministic;
        this.grounded = grounded;
        this.policyEvaluation = policyEvaluation;
        this.connections = connections;
        this.sourceControl = sourceControl;
        this.metrics = metrics;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    public void analyze(AnalysisJob job) {
        long started = System.nanoTime();
        var change = changes.findById(job.changeSetId())
            .orElseThrow(() -> new IllegalStateException("Change set no longer exists"));
        deterministic.analyze(change, job).forEach(changes::saveFinding);
        GroundedChangeAnalysisService.Outcome ai = grounded.analyze(change, job);
        ai.findings().forEach(changes::saveFinding);
        List<PolicyEvaluation> evaluations = policyEvaluation.evaluate(change);
        changes.completeAnalysis(job, ai.partial());
        publishCheck(change, evaluations, ai.message());
        metrics.timer("groundwork.change.analysis", "result", ai.partial() ? "partial" : "completed")
            .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    private void publishCheck(com.groundwork.evidence.domain.ChangeSet change,
            List<PolicyEvaluation> evaluations, String aiMessage) {
        if (change.connectionId() == null) return;
        connections.findById(change.connectionId()).ifPresent(connection -> {
            long failed = evaluations.stream().filter(value -> "FAIL".equals(value.result())).count();
            long unknown = evaluations.stream().filter(value -> "UNKNOWN".equals(value.result())).count();
            String conclusion = failed > 0 ? "failure" : unknown > 0 ? "neutral" : "success";
            String summary = "Groundwork evaluated " + evaluations.size() + " evidence policies: " +
                failed + " failed, " + unknown + " unknown. " + aiMessage;
            try {
                sourceControl.publishCheck(connection, change.repositoryFullName(), change.headSha(),
                    new SourceControlPort.CheckPublication(change.id().toString(), "Groundwork evidence",
                        "completed", conclusion, failed > 0 ? "Evidence gaps require attention" :
                        "Change evidence evaluated", summary,
                        publicBaseUrl + "/changes/" + change.id()));
            } catch (RuntimeException exception) {
                metrics.counter("groundwork.github.check.publication", "result", "failed").increment();
                log.warn("Could not publish GitHub Check for change {}: {}", change.id(), exception.getMessage());
            }
        });
    }
}
