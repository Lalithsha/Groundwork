package com.groundwork.domain.model;

import java.time.Instant;
import java.util.UUID;

public record SourceDocument(
    UUID id,
    UUID workspaceId,
    String title,
    String mediaType,
    String sourceType,
    String contentHash,
    String status,
    int version,
    String embeddingModel,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {}
