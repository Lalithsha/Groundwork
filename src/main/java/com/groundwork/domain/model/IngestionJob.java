package com.groundwork.domain.model;

import java.time.Instant;
import java.util.UUID;

public record IngestionJob(
    UUID id,
    UUID documentId,
    UUID workspaceId,
    String status,
    int progressCurrent,
    int progressTotal,
    int attempts,
    int maxAttempts,
    String errorMessage,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt
) {}
