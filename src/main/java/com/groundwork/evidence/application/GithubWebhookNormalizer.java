package com.groundwork.evidence.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.groundwork.evidence.application.port.out.SourceControlPort;
import com.groundwork.evidence.domain.ChangeSet;
import com.groundwork.evidence.domain.ConnectorConnection;
import com.groundwork.evidence.domain.PullRequestSnapshot;
import com.groundwork.evidence.domain.WebhookDelivery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GithubWebhookNormalizer {
    private static final Pattern GITHUB_ISSUE = Pattern.compile("(?<![A-Za-z0-9])#(\\d+)");
    private static final Pattern JIRA_KEY = Pattern.compile("\\b([A-Z][A-Z0-9]+-\\d+)\\b");

    private final ConnectorRepository connections;
    private final ChangeSetRepository changes;
    private final EvidenceCatalogRepository catalog;
    private final EvidenceIndexingService indexing;
    private final SourceControlPort sourceControl;

    public GithubWebhookNormalizer(ConnectorRepository connections, ChangeSetRepository changes,
            EvidenceCatalogRepository catalog, EvidenceIndexingService indexing, SourceControlPort sourceControl) {
        this.connections = connections;
        this.changes = changes;
        this.catalog = catalog;
        this.indexing = indexing;
        this.sourceControl = sourceControl;
    }

    public NormalizationResult normalize(UUID workspaceId, WebhookDelivery delivery) {
        if (!Set.of("pull_request", "pull_request_review").contains(delivery.eventType())) {
            return new NormalizationResult(null, 0, "Event acknowledged without change analysis");
        }
        ConnectorConnection connection = connections.findById(delivery.connectionId())
            .filter(value -> value.workspaceId().equals(workspaceId))
            .orElseThrow(() -> new IllegalStateException("Webhook connection is unavailable"));
        JsonNode root = delivery.payload();
        JsonNode repository = required(root, "repository");
        JsonNode pullRequest = required(root, "pull_request");
        String repositoryId = repository.path("id").asText();
        String fullName = repository.path("full_name").asText();
        int number = pullRequest.path("number").asInt(root.path("number").asInt());
        String headSha = requiredText(pullRequest.path("head"), "sha");
        String baseSha = requiredText(pullRequest.path("base"), "sha");

        PullRequestSnapshot snapshot = sourceControl.fetchPullRequest(connection, fullName, number, baseSha, headSha)
            .orElseGet(() -> snapshotFromPayload(root));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("files", snapshot.files());
        metadata.put("checks", snapshot.checks());
        metadata.put("reviews", snapshot.reviews());
        metadata.put("provider", snapshot.providerMetadata());
        metadata.put("additions", pullRequest.path("additions").asInt(0));
        metadata.put("deletions", pullRequest.path("deletions").asInt(0));
        metadata.put("changedFiles", pullRequest.path("changed_files").asInt(snapshot.files().size()));
        metadata.put("draft", pullRequest.path("draft").asBoolean(false));

        String state = state(pullRequest, delivery.eventAction());
        ChangeSet change = changes.upsertAndQueue(new ChangeSetRepository.ChangeInput(
            workspaceId, connection.id(), repositoryId, fullName, number, String.valueOf(number),
            pullRequest.path("title").asText("Pull request #" + number),
            pullRequest.path("body").asText(""), pullRequest.path("user").path("login").asText("unknown"),
            baseSha, headSha, pullRequest.path("head").path("ref").asText(),
            pullRequest.path("base").path("ref").asText(), state,
            pullRequest.path("html_url").asText(), metadata, parseInstant(pullRequest.path("created_at").asText()),
            parseInstant(pullRequest.path("merged_at").asText(null))));

        var repositoryArtifact = indexing.upsert(workspaceId, connection.id(), "GITHUB",
            "repository:" + repositoryId, "REPOSITORY", fullName, repository.path("html_url").asText(),
            Map.of("visibility", repository.path("visibility").asText("unknown")),
            repository.path("updated_at").asText(headSha), repository.path("description").asText(""),
            Map.of("defaultBranch", repository.path("default_branch").asText("main")));
        String prExternalId = "pull-request:" + repositoryId + ":" + number;
        var pullRequestArtifact = indexing.upsert(workspaceId, connection.id(), "GITHUB", prExternalId,
            "PULL_REQUEST", change.title(), change.canonicalUrl(), Map.of("repository", fullName), headSha,
            change.title() + "\n\n" + (change.description() == null ? "" : change.description()),
            metadata);
        catalog.relate(workspaceId, pullRequestArtifact.artifact().id(), repositoryArtifact.artifact().id(),
            "CHANGES", "EXPLICIT", Map.of("source", "github-webhook", "deliveryId", delivery.providerDeliveryId()), 1.0);

        int linked = linkReferences(workspaceId, connection.id(), repositoryId, fullName,
            pullRequestArtifact.artifact().id(), change.title() + "\n" + change.description());
        connections.markSyncSuccess(connection.id());
        return new NormalizationResult(change.id(), linked, "Pull request normalized and analysis queued");
    }

    private int linkReferences(UUID workspaceId, UUID connectionId, String repositoryId, String fullName,
            UUID pullRequestArtifactId, String text) {
        Set<String> references = new LinkedHashSet<>();
        Matcher github = GITHUB_ISSUE.matcher(text == null ? "" : text);
        while (github.find()) references.add("github:#" + github.group(1));
        Matcher jira = JIRA_KEY.matcher(text == null ? "" : text);
        while (jira.find()) references.add("jira:" + jira.group(1));
        for (String reference : references) {
            boolean jiraReference = reference.startsWith("jira:");
            String display = reference.substring(reference.indexOf(':') + 1);
            String provider = jiraReference ? "JIRA" : "GITHUB";
            String external = jiraReference ? "requirement:" + display :
                "issue:" + repositoryId + ":" + display.substring(1);
            String url = jiraReference ? null : "https://github.com/" + fullName + "/issues/" + display.substring(1);
            var existing = catalog.findByExternalId(workspaceId, provider, external);
            var requirement = existing.orElseGet(() -> indexing.upsert(workspaceId,
                jiraReference ? null : connectionId, provider, external,
                jiraReference ? "REQUIREMENT" : "ISSUE", display, url, Map.of(), display,
                "Referenced requirement " + display, Map.of("placeholder", true)).artifact());
            catalog.relate(workspaceId, pullRequestArtifactId, requirement.id(),
                "IMPLEMENTS", "EXPLICIT", Map.of("matchedReference", display), 1.0);
        }
        return references.size();
    }

    private PullRequestSnapshot snapshotFromPayload(JsonNode root) {
        List<PullRequestSnapshot.ChangedFile> files = new ArrayList<>();
        for (JsonNode file : root.path("groundwork").path("files")) {
            files.add(new PullRequestSnapshot.ChangedFile(file.path("path").asText(),
                file.path("status").asText("modified"), file.path("additions").asInt(),
                file.path("deletions").asInt(), file.path("patch").asText(null)));
        }
        List<PullRequestSnapshot.CheckResult> checks = new ArrayList<>();
        for (JsonNode check : root.path("groundwork").path("checks")) {
            checks.add(new PullRequestSnapshot.CheckResult(check.path("name").asText(),
                check.path("status").asText(), check.path("conclusion").asText(null),
                check.path("url").asText(null)));
        }
        List<PullRequestSnapshot.ReviewResult> reviews = new ArrayList<>();
        for (JsonNode review : root.path("groundwork").path("reviews")) {
            reviews.add(new PullRequestSnapshot.ReviewResult(review.path("reviewer").asText(),
                review.path("state").asText(), review.path("url").asText(null)));
        }
        List<Map<String, Object>> openApiVersions = new ArrayList<>();
        for (JsonNode version : root.path("groundwork").path("openApiVersions")) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("path", version.path("path").asText());
            value.put("base", version.path("base").asText());
            value.put("head", version.path("head").asText());
            openApiVersions.add(value);
        }
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("source", "webhook-payload");
        provider.put("codeowners", root.path("groundwork").path("codeowners").asText(""));
        provider.put("openApiVersions", openApiVersions);
        return new PullRequestSnapshot(files, checks, reviews, provider);
    }

    private JsonNode required(JsonNode root, String name) {
        JsonNode node = root.path(name);
        if (node.isMissingNode() || node.isNull()) throw new IllegalArgumentException("GitHub payload is missing " + name);
        return node;
    }

    private String requiredText(JsonNode root, String name) {
        String value = root.path(name).asText();
        if (value.isBlank()) throw new IllegalArgumentException("GitHub payload is missing " + name);
        return value;
    }

    private String state(JsonNode pullRequest, String action) {
        if (pullRequest.path("merged").asBoolean(false)) return "MERGED";
        if (pullRequest.path("draft").asBoolean(false)) return "DRAFT";
        if ("closed".equals(action) || "closed".equalsIgnoreCase(pullRequest.path("state").asText())) return "CLOSED";
        return "OPEN";
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException exception) { return null; }
    }

    public record NormalizationResult(UUID changeSetId, int linkedRequirements, String message) {}
}
