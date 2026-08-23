package com.groundwork.evidence.domain;

import java.util.List;
import java.util.Map;

public record PullRequestSnapshot(
        List<ChangedFile> files,
        List<CheckResult> checks,
        List<ReviewResult> reviews,
        Map<String, Object> providerMetadata) {

    public PullRequestSnapshot {
        files = files == null ? List.of() : List.copyOf(files);
        checks = checks == null ? List.of() : List.copyOf(checks);
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    public record ChangedFile(String path, String status, int additions, int deletions, String patch) {}
    public record CheckResult(String name, String status, String conclusion, String url) {}
    public record ReviewResult(String reviewer, String state, String url) {}
}
