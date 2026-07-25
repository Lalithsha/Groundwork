package com.groundwork.domain.model;

import java.util.List;

public record ChatResponseDto(
    String answer,
    List<DocumentChunk> retrievedContexts,
    String retrievalMode
) {}
