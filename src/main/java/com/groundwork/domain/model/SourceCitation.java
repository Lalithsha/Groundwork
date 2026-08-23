package com.groundwork.domain.model;

import java.io.Serializable;
import java.util.UUID;

public record SourceCitation(
    String citationId,
    UUID chunkId,
    UUID documentId,
    String documentTitle,
    String sectionTitle,
    Integer pageNumber,
    double score
) implements Serializable {}
