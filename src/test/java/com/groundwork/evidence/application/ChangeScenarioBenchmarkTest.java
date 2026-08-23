package com.groundwork.evidence.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.evidence.domain.AnalysisJob;
import com.groundwork.evidence.domain.ChangeSet;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeScenarioBenchmarkTest {
    private static final String BASE_API = "openapi: 3.0.3\npaths:\n  /customers/{id}:\n    get: {}\n";
    private static final String SAFE_API = BASE_API + "  /customers:\n    post: {}\n";
    private static final String BREAKING_API = "openapi: 3.0.3\npaths:\n  /customers:\n    post: {}\n";
    private final List<Scenario> scenarios = load();
    private final DeterministicChangeAnalyzer analyzer = new DeterministicChangeAnalyzer(
        new CodeOwnersMatcher(), new OpenApiDiffAnalyzer());

    @Test
    void benchmarkContainsThirtyNamedScenarios() {
        assertThat(scenarios).hasSize(30);
        assertThat(scenarios).extracting(Scenario::id).doesNotHaveDuplicates();
    }

    @TestFactory
    Stream<DynamicTest> evaluatesEveryVersionedScenario() {
        return scenarios.stream().map(scenario -> DynamicTest.dynamicTest(scenario.id(), () -> verify(scenario)));
    }

    private void verify(Scenario scenario) {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        UUID changeId = UUID.nameUUIDFromBytes(scenario.id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("files", scenario.paths().stream()
            .map(path -> Map.<String, Object>of("path", path, "status", "modified", "additions", 3, "deletions", 1))
            .toList());
        metadata.put("checks", checks(scenario.check()));
        metadata.put("reviews", scenario.ownerApproved()
            ? List.of(Map.of("reviewer", "platform", "state", "APPROVED")) : List.of());
        metadata.put("provider", provider(scenario));
        ChangeSet change = new ChangeSet(changeId, UUID.randomUUID(), UUID.randomUUID(), "repo-1",
            "acme/platform", 1, "1", scenario.title(), scenario.description(), "developer",
            "base", "head", "feature", "main", "OPEN", "https://example.test/pr/1", "RUNNING",
            metadata, now, null, now, now);
        AnalysisJob job = new AnalysisJob(UUID.randomUUID(), changeId, "head", "benchmark-v1", "RUNNING",
            1, 3, null, now, null, now);

        List<ChangeSetRepository.ChangeFindingDraft> findings = analyzer.analyze(change, job);
        Set<String> actualMissing = new LinkedHashSet<>();
        Set<String> actualUnknown = new LinkedHashSet<>();
        for (ChangeSetRepository.ChangeFindingDraft finding : findings) {
            if (Boolean.TRUE.equals(finding.details().get("missing"))) actualMissing.add(finding.category());
            if ("UNKNOWN".equals(finding.details().get("state"))) actualUnknown.add(finding.category());
        }

        assertThat(actualMissing).as("missing evidence categories").containsExactlyInAnyOrderElementsOf(scenario.expectedMissing());
        assertThat(actualUnknown).as("unknown evidence categories").containsExactlyInAnyOrderElementsOf(scenario.expectedUnknown());
    }

    private List<Map<String, Object>> checks(String outcome) {
        return switch (outcome) {
            case "PASS" -> List.of(Map.of("name", "ci", "status", "completed", "conclusion", "success"));
            case "SKIPPED" -> List.of(Map.of("name", "optional", "status", "completed", "conclusion", "skipped"));
            case "FAIL" -> List.of(Map.of("name", "ci", "status", "completed", "conclusion", "failure"));
            case "RUNNING" -> List.of(Map.of("name", "ci", "status", "in_progress", "conclusion", ""));
            default -> List.of();
        };
    }

    private Map<String, Object> provider(Scenario scenario) {
        Map<String, Object> provider = new LinkedHashMap<>();
        if (scenario.ownerRequired()) {
            provider.put("codeowners", "src/** @platform\nopenapi.yaml @platform\ngateway/** @platform");
        }
        List<Map<String, Object>> versions = new ArrayList<>();
        if ("SAFE".equals(scenario.apiDiff())) {
            versions.add(Map.of("path", "openapi.yaml", "base", BASE_API, "head", SAFE_API));
        } else if ("BREAKING".equals(scenario.apiDiff())) {
            versions.add(Map.of("path", "openapi.yaml", "base", BASE_API, "head", BREAKING_API));
        }
        provider.put("openApiVersions", versions);
        return provider;
    }

    private List<Scenario> load() {
        try (InputStream input = getClass().getResourceAsStream("/evidence/change-scenarios.json")) {
            if (input == null) throw new IllegalStateException("Benchmark scenarios are missing");
            return new ObjectMapper().readValue(input, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Benchmark scenarios could not be loaded", exception);
        }
    }

    record Scenario(String id, String title, String description, List<String> paths, String check,
                    String apiDiff, boolean ownerRequired, boolean ownerApproved,
                    List<String> expectedMissing, List<String> expectedUnknown) {
        Scenario {
            paths = paths == null ? List.of() : List.copyOf(paths);
            check = check == null ? "NONE" : check;
            apiDiff = apiDiff == null ? "NONE" : apiDiff;
            expectedMissing = expectedMissing == null ? List.of() : List.copyOf(expectedMissing);
            expectedUnknown = expectedUnknown == null ? List.of() : List.copyOf(expectedUnknown);
        }
    }
}
