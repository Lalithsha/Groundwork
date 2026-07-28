package com.groundwork.domain.model;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeArtifact(
    UUID id,
    UUID workspaceId,
    String title,
    String artifactType,
    String content,
    String structuredData,
    Instant createdAt,
    Instant updatedAt
) {}
