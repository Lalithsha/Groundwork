package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IntegrationEvent(
        UUID id,
        UUID workspaceId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        Map<String, Object> payload,
        String status,
        int attempts,
        int maxAttempts,
        String lastError,
        Instant createdAt,
        Instant completedAt) {
}
