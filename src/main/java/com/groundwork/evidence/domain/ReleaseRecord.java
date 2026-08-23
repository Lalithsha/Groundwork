package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReleaseRecord(
        UUID id,
        UUID workspaceId,
        String name,
        String repositoryFullName,
        String baseRef,
        String headRef,
        String status,
        Map<String, Object> manifest,
        String manifestHash,
        Instant frozenAt,
        Instant createdAt) {
}
