package com.groundwork;

import com.groundwork.application.DocumentIngestionService;
import com.groundwork.application.IngestionJobRepository;
import com.groundwork.application.RetrievalService;
import com.groundwork.application.WorkspaceRepository;
import com.groundwork.evidence.application.ChangeAnalysisService;
import com.groundwork.evidence.application.ChangeSetRepository;
import com.groundwork.evidence.application.DemoEvidenceService;
import com.groundwork.evidence.application.EvidenceCatalogRepository;
import com.groundwork.evidence.application.GithubWebhookNormalizer;
import com.groundwork.evidence.application.IntegrationEventRepository;
import com.groundwork.evidence.application.PolicyRepository;
import com.groundwork.evidence.application.ReleaseRecordExportService;
import com.groundwork.evidence.application.ReleaseRecordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_INTEGRATION_TESTS", matches = "true")
class DatabaseIntegrationTest {
    @Autowired DocumentIngestionService ingestion;
    @Autowired IngestionJobRepository jobs;
    @Autowired RetrievalService retrieval;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DemoEvidenceService demo;
    @Autowired IntegrationEventRepository events;
    @Autowired GithubWebhookNormalizer normalizer;
    @Autowired ChangeSetRepository changes;
    @Autowired ChangeAnalysisService analysis;
    @Autowired PolicyRepository policies;
    @Autowired EvidenceCatalogRepository evidence;
    @Autowired ReleaseRecordService releases;
    @Autowired ReleaseRecordExportService releaseExports;

    @Test
    void ingestsEmbedsAndRetrievesDocumentEndToEnd() {
        var queued = ingestion.queue(null, "retry-policy.md", "text/markdown", "readme",
            "# Retry Policy\nWebhook delivery retries five times before entering the dead letter queue.");
        var job = jobs.findById(queued.job().id()).orElseThrow();

        ingestion.process(job);

        var results = retrieval.retrieve("What happens after webhook retries?", "hybrid", 4);
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().title()).isEqualTo("retry-policy.md");
        assertThat(results.getFirst().documentId()).isEqualTo(queued.document().id());
    }

    @Test
    void processesSeededPullRequestIntoCrossSourceEvidencePoliciesAndFindings() {
        var workspace = workspaces.save("Evidence integration", "Database-backed evidence flow");
        demo.seed(workspace.id());

        var event = events.claim("integration-test").orElseThrow();
        var delivery = events.findDelivery(event.aggregateId()).orElseThrow();
        var normalization = normalizer.normalize(workspace.id(), delivery);
        events.complete(event);

        var job = changes.claimAnalysis("integration-test").orElseThrow();
        analysis.analyze(job);
        var change = changes.findAuthorized(workspace.id(), normalization.changeSetId()).orElseThrow();
        var findings = changes.findings(workspace.id(), change.id());
        var evaluations = policies.evaluations(workspace.id(), change.id());
        var requirement = evidence.findByExternalId(workspace.id(), "JIRA", "requirement:PROJ-42").orElseThrow();

        assertThat(change.currentAnalysisStatus()).isIn("COMPLETED", "PARTIAL");
        assertThat(findings).extracting("category").contains("API_COMPATIBILITY", "ROLLBACK_EVIDENCE", "CHECK_EVIDENCE");
        assertThat(evaluations).isNotEmpty();
        assertThat(evaluations).extracting("result").contains("FAIL");
        assertThat(requirement.title()).contains("Replace legacy customer lookup");
        assertThat(evidence.relationships(workspace.id(), requirement.id()))
            .extracting("relationshipType").contains("GOVERNED_BY", "REFERENCES", "IMPLEMENTS");

        var release = releases.freeze(workspace.id(), "integration-release", "acme/customer-api",
            "main", change.headSha(), java.util.List.of(change.id()));
        var verification = releases.verify(workspace.id(), release.id());
        assertThat(release.status()).isEqualTo("DRAFT");
        assertThat(verification.valid()).isTrue();
        assertThat(releaseExports.html(release, verification)).contains("integration-release", "Integrity");
        assertThat(new String(releaseExports.pdf(release, verification), 0, 4,
            java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
