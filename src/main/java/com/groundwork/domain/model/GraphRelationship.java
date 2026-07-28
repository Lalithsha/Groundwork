package com.groundwork.domain.model;

import java.time.Instant;
import java.util.UUID;

public record GraphRelationship(
    UUID id,
    UUID workspaceId,
    UUID sourceEntityId,
    UUID targetEntityId,
    String relationshipType,
    String description,
    Instant createdAt
) {}
