package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ChangeSet(
        UUID id,
        UUID workspaceId,
        UUID connectionId,
        String repositoryExternalId,
        String repositoryFullName,
        Integer pullRequestNumber,
        String externalChangeId,
        String title,
        String description,
        String authorLogin,
        String baseSha,
        String headSha,
        String sourceBranch,
        String targetBranch,
        String state,
        String canonicalUrl,
        String currentAnalysisStatus,
        Map<String, Object> metadata,
        Instant openedAt,
        Instant mergedAt,
        Instant createdAt,
        Instant updatedAt) {
}
