package com.groundwork.domain.model;

import java.util.List;

public record ChatResponseDto(
    String answer,
    List<DocumentChunk> retrievedContexts,
    String retrievalMode,
    List<SourceCitation> citations,
    String evidenceStatus,
    String requestId
) {
    public ChatResponseDto(String answer, List<DocumentChunk> retrievedContexts, String retrievalMode) {
        this(answer, retrievedContexts, retrievalMode, List.of(), "UNKNOWN", null);
    }
}
