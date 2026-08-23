package com.groundwork.evidence.application;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DemoEvidenceService {
    private final ConnectorRepository connections;
    private final GithubWebhookService webhooks;
    private final EvidenceJson json;
    private final EvidenceIndexingService indexing;
    private final EvidenceCatalogRepository catalog;
    private final ProductAnalyticsService analytics;

    public DemoEvidenceService(ConnectorRepository connections, GithubWebhookService webhooks, EvidenceJson json,
            EvidenceIndexingService indexing, EvidenceCatalogRepository catalog, ProductAnalyticsService analytics) {
        this.connections = connections;
        this.webhooks = webhooks;
        this.json = json;
        this.indexing = indexing;
        this.catalog = catalog;
        this.analytics = analytics;
    }

    public DemoResult seed(UUID workspaceId) {
        String installationId = demoInstallationId(workspaceId);
        var connection = connections.upsert(workspaceId, "GITHUB", installationId,
            "Groundwork public demo repository", List.of("contents:read", "pull_requests:read", "checks:write"),
            Map.of("demo", true, "repository", "groundwork/demo-api"), null);
        var jira = connections.upsert(workspaceId, "JIRA", "demo-atlassian",
            "Jira · Groundwork demo", List.of("read:jira-work"),
            Map.of("demo", true, "projectKeys", List.of("PROJ"), "siteUrl", "https://example.atlassian.net"), null);
        var confluence = connections.upsert(workspaceId, "CONFLUENCE", "demo-atlassian",
            "Confluence · Groundwork demo", List.of("read:confluence-content.all"),
            Map.of("demo", true, "spaceIds", List.of("ENG"), "siteUrl", "https://example.atlassian.net"), null);
        var requirement = indexing.upsert(workspaceId, jira.id(), "JIRA", "requirement:PROJ-42", "REQUIREMENT",
            "PROJ-42 · Replace legacy customer lookup", "https://example.atlassian.net/browse/PROJ-42",
            Map.of("connectionScoped", true, "projectKey", "PROJ"), "7",
            "Replace the legacy GET /customers/{id} endpoint without breaking existing consumers.\n\n" +
                "Acceptance criteria:\n- Existing customers can still be fetched during migration.\n" +
                "- A compatibility notice and migration window are documented.\n- Database changes include a rollback plan.",
            Map.of("status", "In Review", "acceptanceCriteriaCount", 3));
        var adr = indexing.upsert(workspaceId, confluence.id(), "CONFLUENCE", "page:adr-019", "ADR",
            "ADR-019 · Customer API compatibility", "https://example.atlassian.net/wiki/spaces/ENG/pages/adr-019",
            Map.of("connectionScoped", true, "spaceId", "ENG"), "3",
            "Decision: retain GET /customers/{id} for one release while POST /customers is introduced. " +
                "Removal requires usage evidence, an API changelog, and a consumer migration plan.",
            Map.of("status", "Accepted", "owner", "API Platform"));
        var incident = indexing.upsert(workspaceId, confluence.id(), "CONFLUENCE", "page:inc-2026-04", "INCIDENT",
            "INC-2026-04 · Customer lookup regression", "https://example.atlassian.net/wiki/spaces/ENG/pages/inc-2026-04",
            Map.of("connectionScoped", true, "spaceId", "ENG"), "2",
            "A previous customer lookup rollout returned 404 for cached identifiers. The runbook requires " +
                "a reversible database migration, endpoint compatibility checks, and a staged rollout.",
            Map.of("severity", "SEV-2", "resolved", true));
        catalog.relate(workspaceId, requirement.artifact().id(), adr.artifact().id(), "GOVERNED_BY", "EXPLICIT",
            Map.of("seed", "groundwork-demo"), 1.0);
        catalog.relate(workspaceId, requirement.artifact().id(), incident.artifact().id(), "REFERENCES", "EXPLICIT",
            Map.of("seed", "groundwork-demo"), 1.0);
        String baseOpenApi = """
            openapi: 3.0.3
            info: {title: Demo API, version: 1.0.0}
            paths:
              /customers/{id}:
                get:
                  responses:
                    '200': {description: Customer}
              /health:
                get:
                  responses:
                    '200': {description: Healthy}
            """;
        String headOpenApi = """
            openapi: 3.0.3
            info: {title: Demo API, version: 2.0.0}
            paths:
              /customers:
                post:
                  responses:
                    '201': {description: Created}
              /health:
                get:
                  responses:
                    '200': {description: Healthy}
            """;
        Instant now = Instant.now();
        Map<String, Object> payload = Map.of(
            "action", "opened",
            "installation", Map.of("id", Long.parseLong(installationId)),
            "number", 42,
            "repository", Map.of(
                "id", 9001, "full_name", "groundwork/demo-api",
                "html_url", "https://github.com/groundwork/demo-api",
                "visibility", "public", "default_branch", "main",
                "description", "Seeded repository used to demonstrate Groundwork change evidence",
                "updated_at", now.toString()),
            "pull_request", Map.ofEntries(
                Map.entry("number", 42),
                Map.entry("title", "PROJ-42 Remove legacy customer lookup"),
                Map.entry("body", "Implements PROJ-42. Replaces the legacy lookup with customer creation. Review API compatibility before release."),
                Map.entry("user", Map.of("login", "demo-developer")),
                Map.entry("head", Map.of("sha", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "ref", "feature/customer-v2")),
                Map.entry("base", Map.of("sha", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "ref", "main")),
                Map.entry("state", "open"), Map.entry("draft", false), Map.entry("merged", false),
                Map.entry("html_url", "https://github.com/groundwork/demo-api/pull/42"),
                Map.entry("created_at", now.toString()), Map.entry("additions", 96),
                Map.entry("deletions", 44), Map.entry("changed_files", 3)),
            "groundwork", Map.of(
                "files", List.of(
                    Map.of("path", "src/main/java/com/demo/CustomerController.java", "status", "modified",
                        "additions", 54, "deletions", 30, "patch", "- @GetMapping(\"/{id}\")\n+ @PostMapping"),
                    Map.of("path", "src/main/resources/db/migration/V8__customer_v2.sql", "status", "added",
                        "additions", 36, "deletions", 0, "patch", "+ALTER TABLE customers ADD COLUMN external_key text;"),
                    Map.of("path", "openapi.yaml", "status", "modified", "additions", 6,
                        "deletions", 14, "patch", "-/customers/{id}:\n+/customers:")),
                "checks", List.of(Map.of("name", "build-and-test", "status", "completed",
                    "conclusion", "failure", "url", "https://github.com/groundwork/demo-api/actions/runs/1")),
                "reviews", List.of(Map.of("reviewer", "demo-reviewer", "state", "COMMENTED",
                    "url", "https://github.com/groundwork/demo-api/pull/42#review-1")),
                "codeowners", "src/** @platform-team\nopenapi.yaml @api-team",
                "openApiVersions", List.of(Map.of("path", "openapi.yaml", "base", baseOpenApi, "head", headOpenApi))));
        String body = json.write(payload);
        String deliveryId = UUID.randomUUID().toString();
        var result = webhooks.accept(deliveryId, "pull_request", webhooks.signForDemo(body), body);
        analytics.record(workspaceId, "demo_seeded", "CONNECTION", connection.id(), Map.of("scenario", "api-breaking-change"));
        return new DemoResult(connection.id(), jira.id(), confluence.id(), result.delivery().id(),
            result.duplicate(), result.delivery().status());
    }

    private String demoInstallationId(UUID workspaceId) {
        long mixed = workspaceId.getMostSignificantBits() ^ workspaceId.getLeastSignificantBits();
        long positive = mixed == Long.MIN_VALUE ? 1 : Math.abs(mixed);
        return String.valueOf(10_000_000L + positive % 9_000_000_000L);
    }

    public record DemoResult(UUID githubConnectionId, UUID jiraConnectionId, UUID confluenceConnectionId,
                             UUID deliveryId, boolean duplicate, String status) {}
}
