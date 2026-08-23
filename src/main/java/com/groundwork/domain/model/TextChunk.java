package com.groundwork.domain.model;

public record TextChunk(
    int index,
    String content,
    int tokenCount,
    String sectionTitle,
    Integer pageNumber
) {}
