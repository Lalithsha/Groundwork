package com.groundwork.evidence.adapter.out.atlassian;

import com.fasterxml.jackson.databind.JsonNode;
import com.groundwork.evidence.application.EvidenceJson;
import com.groundwork.evidence.application.port.out.KnowledgeSourcePort;
import com.groundwork.evidence.domain.ConnectorConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AtlassianKnowledgeSourceAdapter implements KnowledgeSourcePort {
    private final AtlassianTokenService tokens;
    private final EvidenceJson json;
    private final RestClient rest;
    private final String apiUrl;

    public AtlassianKnowledgeSourceAdapter(AtlassianTokenService tokens, EvidenceJson json,
            RestClient.Builder builder,
            @Value("${groundwork.integrations.atlassian.api-url:https://api.atlassian.com}") String apiUrl) {
        this.tokens = tokens;
        this.json = json;
        this.rest = builder.build();
        this.apiUrl = apiUrl.replaceAll("/$", "");
    }

    @Override
    public boolean supports(String provider) {
        return "JIRA".equals(provider) || "CONFLUENCE".equals(provider);
    }

    @Override
    public List<SourceArtifact> fetch(ConnectorConnection connection) {
        String token = tokens.accessToken(connection);
        return "JIRA".equals(connection.provider()) ? jira(connection, token) : confluence(connection, token);
    }

    private List<SourceArtifact> jira(ConnectorConnection connection, String token) {
        List<String> projects = strings(connection.metadata().get("projectKeys"));
        if (projects.isEmpty()) throw new IllegalArgumentException("Select at least one Jira project key before syncing");
        String cloudId = requiredCloudId(connection);
        String jql = "project in (" + projects.stream().map(value -> "\"" + value.replace("\"", "") + "\"")
            .reduce((left, right) -> left + "," + right).orElse("") + ") ORDER BY updated DESC";
        String url = apiUrl + "/ex/jira/" + encode(cloudId) + "/rest/api/3/search/jql?maxResults=100&fields=" +
            encode("summary,description,status,issuetype,project,updated,created,fixVersions,labels") +
            "&jql=" + encode(jql);
        JsonNode page = get(url, token);
        List<SourceArtifact> result = new ArrayList<>();
        for (JsonNode issue : page.path("issues")) {
            String key = issue.path("key").asText();
            JsonNode fields = issue.path("fields");
            String title = key + " · " + fields.path("summary").asText("Untitled issue");
            String description = fields.path("description").isMissingNode() || fields.path("description").isNull()
                ? "" : json.write(fields.path("description"));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("key", key);
            metadata.put("status", fields.path("status").path("name").asText(""));
            metadata.put("issueType", fields.path("issuetype").path("name").asText(""));
            metadata.put("projectKey", fields.path("project").path("key").asText(""));
            metadata.put("labels", jsonNodeValues(fields.path("labels")));
            metadata.put("acceptanceCriteria", description);
            result.add(new SourceArtifact("requirement:" + key, "REQUIREMENT", title,
                siteUrl(connection) + "/browse/" + key, fields.path("updated").asText(key),
                title + "\n\n" + description, metadata,
                Map.of("connectionScoped", true, "projectKey", fields.path("project").path("key").asText(""))));
        }
        return result;
    }

    private List<SourceArtifact> confluence(ConnectorConnection connection, String token) {
        List<String> spaceIds = strings(connection.metadata().get("spaceIds"));
        if (spaceIds.isEmpty()) throw new IllegalArgumentException("Select at least one Confluence space before syncing");
        String cloudId = requiredCloudId(connection);
        List<SourceArtifact> result = new ArrayList<>();
        for (String spaceId : spaceIds) {
            String url = apiUrl + "/ex/confluence/" + encode(cloudId) +
                "/wiki/api/v2/pages?limit=100&body-format=storage&space-id=" + encode(spaceId);
            JsonNode page = get(url, token);
            for (JsonNode value : page.path("results")) {
                String id = value.path("id").asText();
                String title = value.path("title").asText("Untitled page");
                String body = value.path("body").path("storage").path("value").asText("");
                int version = value.path("version").path("number").asInt(1);
                String type = classifyPage(title, body);
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("spaceId", spaceId);
                metadata.put("pageId", id);
                metadata.put("version", version);
                metadata.put("status", value.path("status").asText("current"));
                result.add(new SourceArtifact("page:" + id, type, title,
                    siteUrl(connection) + "/wiki/spaces/" + encode(spaceId) + "/pages/" + id,
                    String.valueOf(version), title + "\n\n" + body, metadata,
                    Map.of("connectionScoped", true, "spaceId", spaceId)));
            }
        }
        return result;
    }

    private JsonNode get(String url, String token) {
        JsonNode response = rest.get().uri(url).header("Authorization", "Bearer " + token)
            .header("Accept", "application/json").retrieve().body(JsonNode.class);
        if (response == null) throw new IllegalStateException("Atlassian source returned no response");
        return response;
    }

    private static String classifyPage(String title, String body) {
        String text = (title + " " + body).toLowerCase(java.util.Locale.ROOT);
        if (text.contains("architecture decision") || text.contains("adr-")) return "ADR";
        if (text.contains("runbook") || text.contains("rollback") || text.contains("incident response")) return "RUNBOOK";
        return "DOCUMENT";
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).distinct().toList();
    }
    private static List<String> jsonNodeValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) node.forEach(item -> values.add(item.asText()));
        return values;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String requiredCloudId(ConnectorConnection connection) {
        String value = String.valueOf(connection.metadata().getOrDefault("cloudId", ""));
        if (value.isBlank()) throw new IllegalStateException("Atlassian connection has no cloud ID; reconnect it");
        return value;
    }
    private static String siteUrl(ConnectorConnection connection) {
        return String.valueOf(connection.metadata().getOrDefault("siteUrl", "https://atlassian.net"));
    }
}
