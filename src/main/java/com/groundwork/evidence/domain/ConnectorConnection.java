package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ConnectorConnection(
        UUID id,
        UUID workspaceId,
        String provider,
        String externalAccountId,
        String displayName,
        String status,
        List<String> scopes,
        Map<String, Object> metadata,
        Instant lastSyncedAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {
}
