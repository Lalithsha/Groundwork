package com.groundwork.domain.model;

import java.io.Serializable;
import java.util.UUID;

public record DocumentChunk(
    UUID id,
    UUID documentId,
    String title,
    String content,
    String sourceType,
    String contentHash,
    double score,
    int chunkIndex,
    String sectionTitle,
    Integer pageNumber
) implements Serializable {
    public DocumentChunk(UUID id, String title, String content, String sourceType, String contentHash, double score) {
        this(id, null, title, content, sourceType, contentHash, score, 0, null, null);
    }
}
