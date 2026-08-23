package com.groundwork.evidence.application;

import com.groundwork.evidence.domain.AnalysisJob;
import com.groundwork.evidence.domain.ChangeSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DeterministicChangeAnalyzer {
    private static final Pattern INTENT_REFERENCE = Pattern.compile("(?s).*(#[0-9]+|[A-Z][A-Z0-9]+-[0-9]+).*");
    private static final Pattern TEST_PATH = Pattern.compile("(?i).*(^|/)(test|tests|__tests__)(/|$).*|.*(test|spec)\\.[^.]+$");
    private static final Pattern SOURCE_PATH = Pattern.compile("(?i).+\\.(java|kt|kts|js|jsx|ts|tsx|py|go|rs|cs|rb|php)$");
    private static final Pattern API_PATH = Pattern.compile("(?i).*(openapi|swagger).*(yaml|yml|json)$");
    private static final Pattern MIGRATION_PATH = Pattern.compile("(?i).*(db/migration|migrations?|schema).*");
    private static final Pattern CHANGELOG_PATH = Pattern.compile("(?i).*(changelog|changes|release.?notes).*\\.(md|txt|ya?ml|json)$");

    private final CodeOwnersMatcher codeOwners;
    private final OpenApiDiffAnalyzer openApi;

    public DeterministicChangeAnalyzer(CodeOwnersMatcher codeOwners, OpenApiDiffAnalyzer openApi) {
        this.codeOwners = codeOwners;
        this.openApi = openApi;
    }

    public List<ChangeSetRepository.ChangeFindingDraft> analyze(ChangeSet change, AnalysisJob job) {
        List<ChangeSetRepository.ChangeFindingDraft> findings = new ArrayList<>();
        List<Map<String, Object>> files = objectList(change.metadata().get("files"));
        List<Map<String, Object>> checks = objectList(change.metadata().get("checks"));
        List<Map<String, Object>> reviews = objectList(change.metadata().get("reviews"));
        List<String> paths = files.stream().map(file -> string(file.get("path"))).filter(value -> !value.isBlank()).toList();
        boolean sourceChanged = paths.stream().anyMatch(path -> SOURCE_PATH.matcher(path).matches() && !TEST_PATH.matcher(path).matches());
        boolean testsChanged = paths.stream().anyMatch(path -> TEST_PATH.matcher(path).matches());
        boolean checksSucceeded = !checks.isEmpty() && checks.stream().allMatch(this::successfulCheck);
        boolean intentLinked = INTENT_REFERENCE.matcher((change.title() == null ? "" : change.title()) + "\n" +
            (change.description() == null ? "" : change.description())).matches();

        findings.add(finding(change, job, "intent-link", "INTENT", intentLinked ? "INFO" : "HIGH",
            intentLinked ? "The change contains an explicit issue or requirement reference."
                : "No explicit issue or requirement reference was found.",
            Map.of("missing", !intentLinked, "satisfied", intentLinked)));

        boolean testEvidence = !sourceChanged || testsChanged || checksSucceeded;
        findings.add(finding(change, job, "test-evidence", "TEST_EVIDENCE", testEvidence ? "INFO" : "HIGH",
            testEvidence ? "Test evidence is present for the changed source paths."
                : "Source files changed without changed tests or successful check evidence.",
            Map.of("missing", !testEvidence, "satisfied", testEvidence, "sourceChanged", sourceChanged,
                "testsChanged", testsChanged, "checksSucceeded", checksSucceeded)));

        String checkState = checks.isEmpty() ? "UNKNOWN" : checksSucceeded ? "PRESENT" : "MISSING";
        findings.add(finding(change, job, "check-evidence", "CHECK_EVIDENCE",
            checksSucceeded ? "INFO" : checks.isEmpty() ? "MEDIUM" : "HIGH",
            checks.isEmpty() ? "Configured check results are unavailable."
                : checksSucceeded ? "All reported checks completed successfully."
                : "One or more reported checks are incomplete or unsuccessful.",
            Map.of("missing", !checksSucceeded, "satisfied", checksSucceeded, "state", checkState,
                "checks", checks)));

        boolean migrationChanged = paths.stream().anyMatch(path -> MIGRATION_PATH.matcher(path).matches());
        boolean rollbackDescribed = containsIgnoreCase(change.description(), "rollback");
        boolean rollbackSatisfied = !migrationChanged || rollbackDescribed;
        findings.add(finding(change, job, "rollback-evidence", "ROLLBACK_EVIDENCE",
            rollbackSatisfied ? "INFO" : "HIGH",
            rollbackSatisfied ? (migrationChanged ? "A rollback plan is described for the migration."
                : "No migration path requires rollback evidence.")
                : "A database or schema migration changed without a described rollback plan.",
            Map.of("missing", !rollbackSatisfied, "satisfied", rollbackSatisfied, "applicable", migrationChanged,
                "state", rollbackSatisfied ? "PRESENT" : "MISSING")));

        Map<String, Object> provider = objectMap(change.metadata().get("provider"));
        String codeownersContent = string(provider.get("codeowners"));
        Map<String, List<String>> affectedOwners = codeOwners.ownersFor(paths, codeownersContent);
        Set<String> approvedReviewers = new LinkedHashSet<>();
        for (Map<String, Object> review : reviews) {
            if ("APPROVED".equalsIgnoreCase(string(review.get("state")))) {
                approvedReviewers.add("@" + string(review.get("reviewer")).replaceFirst("^@", ""));
            }
        }
        Set<String> requiredOwners = new LinkedHashSet<>();
        affectedOwners.values().forEach(requiredOwners::addAll);
        boolean ownerApproval = requiredOwners.isEmpty() || requiredOwners.stream().anyMatch(approvedReviewers::contains);
        findings.add(finding(change, job, "owner-approval", "OWNER_APPROVAL", ownerApproval ? "INFO" : "MEDIUM",
            ownerApproval ? "Required ownership review is satisfied or no CODEOWNERS rule applies."
                : "Changed owned paths do not have an approval from a matching CODEOWNER.",
            Map.of("missing", !ownerApproval, "satisfied", ownerApproval,
                "state", ownerApproval ? "PRESENT" : "MISSING", "requiredOwners", requiredOwners,
                "approvedReviewers", approvedReviewers, "affectedPaths", affectedOwners)));

        List<Map<String, Object>> apiVersions = objectList(provider.get("openApiVersions"));
        boolean apiFileChanged = paths.stream().anyMatch(path -> API_PATH.matcher(path).matches());
        boolean apiDiffAvailable = !apiVersions.isEmpty();
        boolean breaking = false;
        List<String> breakingChanges = new ArrayList<>();
        List<String> additions = new ArrayList<>();
        for (Map<String, Object> version : apiVersions) {
            String base = string(version.get("base"));
            String head = string(version.get("head"));
            if (base.isBlank() || head.isBlank()) continue;
            OpenApiDiffAnalyzer.DiffResult diff = openApi.compare(base, head);
            breaking |= diff.breaking();
            breakingChanges.addAll(diff.breakingChanges());
            additions.addAll(diff.additions());
        }
        boolean compatibilitySatisfied = !breaking && (!apiFileChanged || apiDiffAvailable);
        String compatibilityState = !apiFileChanged ? "PRESENT" : !apiDiffAvailable ? "UNKNOWN" :
            breaking ? "MISSING" : "PRESENT";
        findings.add(finding(change, job, "api-compatibility", "API_COMPATIBILITY",
            breaking ? "CRITICAL" : !compatibilitySatisfied ? "MEDIUM" : "INFO",
            breaking ? "The OpenAPI change removes public paths or operations."
                : apiFileChanged && !apiDiffAvailable ? "The OpenAPI file changed but base/head contract content is unavailable."
                : apiFileChanged ? "No removed OpenAPI paths or operations were detected."
                : "No OpenAPI contract change was detected.",
            Map.of("missing", !compatibilitySatisfied, "satisfied", compatibilitySatisfied,
                "state", compatibilityState, "applicable", apiFileChanged,
                "breakingChanges", breakingChanges, "additions", additions)));

        boolean changelogPresent = paths.stream().anyMatch(path -> CHANGELOG_PATH.matcher(path).matches());
        boolean changelogSatisfied = !breaking || changelogPresent;
        findings.add(finding(change, job, "api-changelog", "API_CHANGELOG",
            changelogSatisfied ? "INFO" : "HIGH",
            changelogSatisfied ? (breaking ? "A changelog or release note accompanies the breaking API change."
                : "No breaking API change requires changelog evidence.")
                : "A breaking API change has no changed changelog or release note.",
            Map.of("missing", !changelogSatisfied, "satisfied", changelogSatisfied,
                "state", changelogSatisfied ? "PRESENT" : "MISSING", "applicable", breaking)));

        Map<String, List<String>> classifiedPaths = classify(paths);

        findings.add(finding(change, job, "changed-paths", "CHANGE_SCOPE", "INFO",
            paths.isEmpty() ? "Changed-file details are unavailable; impact analysis is partial."
                : "The change modifies " + paths.size() + " file(s).",
            Map.of("missing", paths.isEmpty(), "satisfied", !paths.isEmpty(),
                "state", paths.isEmpty() ? "UNKNOWN" : "PRESENT", "paths", paths,
                "classifications", classifiedPaths,
                "additions", sum(files, "additions"), "deletions", sum(files, "deletions"))));
        return List.copyOf(findings);
    }

    private ChangeSetRepository.ChangeFindingDraft finding(ChangeSet change, AnalysisJob job, String key,
            String category, String severity, String statement, Map<String, Object> details) {
        return new ChangeSetRepository.ChangeFindingDraft(change.id(), job.id(), key,
            "deterministic-change-analyzer", job.analyzerVersion(), category, severity, statement,
            true, "SUPPORTED", "HIGH", List.of(), details);
    }

    private boolean successfulCheck(Map<String, Object> check) {
        String status = string(check.get("status"));
        String conclusion = string(check.get("conclusion"));
        return "completed".equalsIgnoreCase(status) && Set.of("success", "neutral", "skipped")
            .contains(conclusion.toLowerCase(Locale.ROOT));
    }

    private int sum(List<Map<String, Object>> values, String key) {
        return values.stream().map(item -> item.get(key)).filter(Number.class::isInstance)
            .map(Number.class::cast).mapToInt(Number::intValue).sum();
    }

    private Map<String, List<String>> classify(List<String> paths) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String path : paths) {
            String lower = path.toLowerCase(Locale.ROOT);
            String category;
            if (TEST_PATH.matcher(path).matches()) category = "TEST";
            else if (MIGRATION_PATH.matcher(path).matches()) category = "DATABASE";
            else if (API_PATH.matcher(path).matches()) category = "API_CONTRACT";
            else if (lower.startsWith("frontend/") || lower.matches(".*\\.(tsx|jsx|css|scss)$")) category = "FRONTEND";
            else if (lower.matches(".*(dockerfile|docker-compose.*|k8s/.*|helm/.*|terraform/.*|\\.tf)$")) category = "INFRASTRUCTURE";
            else if (lower.matches(".*(application.*\\.(yml|yaml|properties)|\\.env.*|config/.*)$")) category = "CONFIGURATION";
            else if (lower.matches(".*\\.(md|adoc|rst)$")) category = "DOCUMENTATION";
            else if (SOURCE_PATH.matcher(path).matches()) category = "BACKEND_OR_LIBRARY";
            else category = "OTHER";
            result.computeIfAbsent(category, ignored -> new ArrayList<>()).add(path);
        }
        return result;
    }

    private boolean containsIgnoreCase(String value, String expected) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) if (item instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, nested) -> normalized.put(String.valueOf(key), nested));
            result.add(Collections.unmodifiableMap(normalized));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return Collections.unmodifiableMap(result);
    }

    private String string(Object value) { return value == null ? "" : String.valueOf(value); }
}
