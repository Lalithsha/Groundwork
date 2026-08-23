package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.UUID;

public record AnalysisJob(
        UUID id,
        UUID changeSetId,
        String headSha,
        String analyzerVersion,
        String status,
        int attempts,
        int maxAttempts,
        String lastError,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt) {
}
