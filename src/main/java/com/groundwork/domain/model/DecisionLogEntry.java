package com.groundwork.domain.model;

import java.time.Instant;
import java.util.UUID;

public record DecisionLogEntry(
    UUID id,
    UUID workspaceId,
    String decision,
    String rationale,
    String actor,
    Instant createdAt
) {}
