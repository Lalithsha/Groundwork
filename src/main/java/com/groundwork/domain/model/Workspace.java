package com.groundwork.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Workspace(
    UUID id,
    String name,
    String description,
    Instant createdAt,
    Instant updatedAt
) {}
