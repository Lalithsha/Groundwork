package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChangeFinding(
        UUID id,
        UUID changeSetId,
        UUID analysisJobId,
        String findingKey,
        String analyzer,
        String analyzerVersion,
        String category,
        String severity,
        String statement,
        boolean deterministic,
        String evidenceStatus,
        String confidence,
        List<Map<String, Object>> citations,
        Map<String, Object> details,
        String reviewStatus,
        String reviewReason,
        Instant reviewedAt,
        Instant createdAt) {
}
