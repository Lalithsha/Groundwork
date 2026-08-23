package com.groundwork.evidence.domain;

import java.util.Map;
import java.util.UUID;

public record EvidenceSearchHit(
        UUID artifactId,
        UUID versionId,
        String artifactType,
        String title,
        String canonicalUrl,
        String sourceVersion,
        String content,
        Map<String, Object> metadata,
        double score,
        String retrievalStage) {
}
