package com.groundwork.domain.model;

import java.time.Instant;
import java.util.UUID;

public record DocumentComparison(
    UUID id,
    UUID workspaceId,
    String docTitleA,
    String docTitleB,
    String comparisonResult,
    String diffSummary,
    Instant createdAt
) {}
