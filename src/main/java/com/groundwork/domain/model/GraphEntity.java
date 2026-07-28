package com.groundwork.domain.model;

import java.time.Instant;
import java.util.UUID;

public record GraphEntity(
    UUID id,
    UUID workspaceId,
    String name,
    String entityType,
    String description,
    Instant createdAt
) {}
