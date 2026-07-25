package com.groundwork.domain.model;

import java.util.UUID;

public record DocumentChunk(
    UUID id,
    String title,
    String content,
    String sourceType,
    String contentHash,
    double score
) {}
