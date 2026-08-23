package com.groundwork.evidence.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record WebhookDelivery(
        UUID id,
        UUID connectionId,
        String provider,
        String providerDeliveryId,
        String eventType,
        String eventAction,
        boolean signatureValid,
        String payloadHash,
        JsonNode payload,
        String status,
        String errorMessage,
        Instant receivedAt,
        Instant processedAt) {
}
