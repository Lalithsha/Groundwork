package com.groundwork.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ReviewReport(
    UUID id,
    UUID workspaceId,
    String title,
    String status,
    Double score,
    String feedback,
    String reportData,
    Instant createdAt,
    Instant updatedAt
) {}
