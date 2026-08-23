package com.groundwork.evidence.application;

import com.groundwork.evidence.domain.AnalysisJob;
import com.groundwork.evidence.domain.ChangeSet;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicChangeAnalyzerTest {
    @Test
    void findsBreakingContractFailedCheckMissingOwnerAndRollbackEvidence() {
        UUID changeId = UUID.randomUUID(); UUID jobId = UUID.randomUUID(); Instant now = Instant.now();
        String base = "openapi: 3.0.3\npaths:\n  /customers/{id}:\n    get: {}\n";
        String head = "openapi: 3.0.3\npaths:\n  /customers:\n    post: {}\n";
        Map<String, Object> metadata = Map.of(
            "files", List.of(
                Map.of("path", "src/main/java/Customer.java", "status", "modified", "additions", 4, "deletions", 2),
                Map.of("path", "src/main/resources/db/migration/V2__customer.sql", "status", "added", "additions", 6, "deletions", 0),
                Map.of("path", "openapi.yaml", "status", "modified", "additions", 2, "deletions", 3)),
            "checks", List.of(Map.of("name", "test", "status", "completed", "conclusion", "failure")),
            "reviews", List.of(Map.of("reviewer", "reviewer", "state", "COMMENTED")),
            "provider", Map.of("codeowners", "src/** @platform\nopenapi.yaml @api-team",
                "openApiVersions", List.of(Map.of("path", "openapi.yaml", "base", base, "head", head))));
        ChangeSet change = new ChangeSet(changeId, UUID.randomUUID(), UUID.randomUUID(), "repo-1",
            "acme/customers", 42, "42", "PROJ-42 remove lookup", "No release notes yet", "dev",
            "aaa", "bbb", "feature", "main", "OPEN", "https://example.test/pr/42", "RUNNING",
            metadata, now, null, now, now);
        AnalysisJob job = new AnalysisJob(jobId, changeId, "bbb", "v1", "RUNNING", 1, 3,
            null, now, null, now);

        var findings = new DeterministicChangeAnalyzer(new CodeOwnersMatcher(), new OpenApiDiffAnalyzer())
            .analyze(change, job);

        assertThat(finding(findings, "CHECK_EVIDENCE").details()).containsEntry("state", "MISSING");
        assertThat(finding(findings, "OWNER_APPROVAL").details()).containsEntry("missing", true);
        assertThat(finding(findings, "ROLLBACK_EVIDENCE").details()).containsEntry("missing", true);
        assertThat(finding(findings, "API_COMPATIBILITY").details()).containsEntry("state", "MISSING");
        assertThat(finding(findings, "API_CHANGELOG").details()).containsEntry("missing", true);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> classifications = (Map<String, List<String>>) finding(findings, "CHANGE_SCOPE")
            .details().get("classifications");
        assertThat(classifications).containsKeys("BACKEND_OR_LIBRARY", "DATABASE", "API_CONTRACT");
    }

    private ChangeSetRepository.ChangeFindingDraft finding(
            List<ChangeSetRepository.ChangeFindingDraft> findings, String category) {
        return findings.stream().filter(value -> value.category().equals(category)).findFirst().orElseThrow();
    }
}
