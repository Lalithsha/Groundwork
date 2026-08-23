package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvidencePolicy(
        UUID id,
        UUID workspaceId,
        String name,
        String description,
        Integer activeVersion,
        boolean enabled,
        UUID policyVersionId,
        String ruleType,
        String severity,
        Map<String, Object> definition,
        Instant createdAt,
        Instant updatedAt) {
}
