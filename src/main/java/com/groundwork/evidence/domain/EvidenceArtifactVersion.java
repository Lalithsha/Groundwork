package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvidenceArtifactVersion(
        UUID id,
        UUID artifactId,
        String sourceVersion,
        String contentHash,
        String content,
        Map<String, Object> metadata,
        Instant validFrom,
        Instant validTo,
        String embeddingModel,
        String embeddingVersion,
        Instant createdAt) {
}
