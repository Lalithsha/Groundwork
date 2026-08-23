package com.groundwork.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ReindexJob(UUID id, UUID workspaceId, String status, int progressCurrent,
                         int progressTotal, int attempts, String errorMessage,
                         Instant createdAt, Instant startedAt, Instant completedAt) {}
