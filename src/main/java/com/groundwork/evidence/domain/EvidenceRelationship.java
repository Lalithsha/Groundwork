package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvidenceRelationship(
        UUID id,
        UUID workspaceId,
        UUID sourceArtifactId,
        UUID targetArtifactId,
        String relationshipType,
        String provenanceType,
        Map<String, Object> provenance,
        Double confidence,
        Instant validFrom,
        Instant validTo) {
}
