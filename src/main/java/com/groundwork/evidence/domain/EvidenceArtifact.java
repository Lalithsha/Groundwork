package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvidenceArtifact(
        UUID id,
        UUID workspaceId,
        UUID connectionId,
        String sourceProvider,
        String externalId,
        String artifactType,
        String title,
        String canonicalUrl,
        String lifecycleState,
        Map<String, Object> sourceAcl,
        Instant createdAt,
        Instant updatedAt) {
}
