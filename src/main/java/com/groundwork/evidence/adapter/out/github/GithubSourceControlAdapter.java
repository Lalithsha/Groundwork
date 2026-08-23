package com.groundwork.evidence.adapter.out.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.evidence.application.port.out.SourceControlPort;
import com.groundwork.evidence.domain.ConnectorConnection;
import com.groundwork.evidence.domain.PullRequestSnapshot;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GithubSourceControlAdapter implements SourceControlPort {
    private final GithubAppAuthentication authentication;
    private final RestClient rest;
    private final ObjectMapper mapper;

    public GithubSourceControlAdapter(
            GithubAppAuthentication authentication,
            RestClient.Builder builder,
            ObjectMapper mapper,
            @org.springframework.beans.factory.annotation.Value(
                "${groundwork.integrations.github.api-url:https://api.github.com}") String apiUrl) {
        this.authentication = authentication;
        this.rest = builder.baseUrl(apiUrl)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
        this.mapper = mapper;
    }

    @Override
    public Optional<PullRequestSnapshot> fetchPullRequest(ConnectorConnection connection,
            String repositoryFullName, int pullRequestNumber, String baseSha, String headSha) {
        Optional<String> token = authentication.installationToken(connection.externalAccountId());
        if (token.isEmpty()) return Optional.empty();
        String repositoryPath = repositoryPath(repositoryFullName);
        String filesJson = get(token.get(), repositoryPath + "/pulls/" + pullRequestNumber + "/files?per_page=100");
        String checksJson = get(token.get(), repositoryPath + "/commits/" + safeRef(headSha) + "/check-runs?per_page=100");
        String reviewsJson = get(token.get(), repositoryPath + "/pulls/" + pullRequestNumber + "/reviews?per_page=100");
        try {
            List<PullRequestSnapshot.ChangedFile> files = new ArrayList<>();
            for (JsonNode node : mapper.readTree(filesJson)) {
                files.add(new PullRequestSnapshot.ChangedFile(
                    node.path("filename").asText(), node.path("status").asText(),
                    node.path("additions").asInt(), node.path("deletions").asInt(),
                    node.path("patch").asText(null)));
            }
            List<PullRequestSnapshot.CheckResult> checks = new ArrayList<>();
            JsonNode checkRoot = mapper.readTree(checksJson);
            for (JsonNode node : checkRoot.path("check_runs")) {
                checks.add(new PullRequestSnapshot.CheckResult(
                    node.path("name").asText(), node.path("status").asText(),
                    node.path("conclusion").asText(null), node.path("html_url").asText(null)));
            }
            List<PullRequestSnapshot.ReviewResult> reviews = new ArrayList<>();
            for (JsonNode node : mapper.readTree(reviewsJson)) {
                reviews.add(new PullRequestSnapshot.ReviewResult(
                    node.path("user").path("login").asText("unknown"), node.path("state").asText(),
                    node.path("html_url").asText(null)));
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", "github-api");
            metadata.put("headSha", headSha);
            metadata.put("codeowners", fetchCodeOwners(token.get(), repositoryFullName, headSha));
            List<Map<String, Object>> openApiVersions = new ArrayList<>();
            files.stream().map(PullRequestSnapshot.ChangedFile::path)
                .filter(this::isOpenApiPath).limit(3).forEach(path -> {
                    String base = getContent(token.get(), repositoryFullName, path, baseSha);
                    String head = getContent(token.get(), repositoryFullName, path, headSha);
                    if (base != null && head != null) {
                        openApiVersions.add(Map.of("path", path, "base", base, "head", head));
                    }
                });
            metadata.put("openApiVersions", openApiVersions);
            return Optional.of(new PullRequestSnapshot(files, checks, reviews, metadata));
        } catch (Exception exception) {
            throw new IllegalStateException("GitHub pull-request response was invalid", exception);
        }
    }

    @Override
    public void publishCheck(ConnectorConnection connection, String repositoryFullName, String headSha,
            CheckPublication publication) {
        Optional<String> token = authentication.installationToken(connection.externalAccountId());
        if (token.isEmpty()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", publication.name());
        body.put("head_sha", headSha);
        body.put("external_id", publication.externalId());
        body.put("status", publication.status());
        if (publication.conclusion() != null) body.put("conclusion", publication.conclusion());
        if (publication.detailsUrl() != null) body.put("details_url", publication.detailsUrl());
        body.put("output", Map.of("title", publication.title(), "summary", publication.summary()));
        rest.post().uri(repositoryPath(repositoryFullName) + "/check-runs")
            .header("Authorization", "Bearer " + token.get())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().toBodilessEntity();
    }

    @Override
    public boolean isAvailable() {
        return authentication.isConfigured();
    }

    private String get(String token, String uri) {
        return rest.get().uri(uri).header("Authorization", "Bearer " + token)
            .retrieve().body(String.class);
    }

    private String fetchCodeOwners(String token, String repository, String ref) {
        for (String path : List.of(".github/CODEOWNERS", "CODEOWNERS", "docs/CODEOWNERS")) {
            String content = getContent(token, repository, path, ref);
            if (content != null) return content;
        }
        return "";
    }

    private String getContent(String token, String repository, String path, String ref) {
        try {
            String encodedPath = java.util.Arrays.stream(path.split("/"))
                .map(segment -> org.springframework.web.util.UriUtils.encodePathSegment(segment,
                    java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("/"));
            String encodedRef = org.springframework.web.util.UriUtils.encodeQueryParam(safeRef(ref),
                java.nio.charset.StandardCharsets.UTF_8);
            String response = rest.get().uri(repositoryPath(repository) + "/contents/" + encodedPath + "?ref=" + encodedRef)
                .header("Authorization", "Bearer " + token).retrieve().body(String.class);
            JsonNode root = mapper.readTree(response);
            if (!"base64".equals(root.path("encoding").asText())) return null;
            byte[] decoded = java.util.Base64.getMimeDecoder().decode(root.path("content").asText());
            if (decoded.length > 2_000_000) return null;
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean isOpenApiPath(String path) {
        return path != null && path.toLowerCase(java.util.Locale.ROOT)
            .matches(".*(openapi|swagger).*(yaml|yml|json)$");
    }

    private String repositoryPath(String repository) {
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("GitHub repository must be in owner/name form");
        }
        return "/repos/" + repository;
    }

    private String safeRef(String ref) {
        if (ref == null || !ref.matches("[A-Za-z0-9._/-]{1,255}") || ref.contains("..")) {
            throw new IllegalArgumentException("GitHub ref is invalid");
        }
        return ref;
    }
}
