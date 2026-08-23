package com.groundwork.evidence.application;

import com.groundwork.evidence.domain.IntegrationEvent;
import com.groundwork.evidence.domain.WebhookDelivery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IntegrationEventRepository {
    private final JdbcTemplate jdbc;
    private final EvidenceJson json;
    private final RowMapper<IntegrationEvent> eventMapper;
    private final RowMapper<WebhookDelivery> deliveryMapper;

    public IntegrationEventRepository(JdbcTemplate jdbc, EvidenceJson json) {
        this.jdbc = jdbc;
        this.json = json;
        this.eventMapper = (rs, rowNum) -> new IntegrationEvent(
            rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
            rs.getString("aggregate_type"), rs.getObject("aggregate_id", UUID.class),
            rs.getString("event_type"), json.map(rs.getString("payload")), rs.getString("status"),
            rs.getInt("attempts"), rs.getInt("max_attempts"), rs.getString("last_error"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant());
        this.deliveryMapper = (rs, rowNum) -> new WebhookDelivery(
            rs.getObject("id", UUID.class), rs.getObject("connection_id", UUID.class),
            rs.getString("provider"), rs.getString("provider_delivery_id"), rs.getString("event_type"),
            rs.getString("event_action"), rs.getBoolean("signature_valid"), rs.getString("payload_hash"),
            json.tree(rs.getString("payload")), rs.getString("status"), rs.getString("error_message"),
            rs.getTimestamp("received_at").toInstant(),
            rs.getTimestamp("processed_at") == null ? null : rs.getTimestamp("processed_at").toInstant());
    }

    @Transactional
    public AcceptResult accept(UUID workspaceId, UUID connectionId, String provider, String deliveryId,
            String eventType, String eventAction, String payloadHash, String payload) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
            INSERT INTO webhook_deliveries (
                id, connection_id, provider, provider_delivery_id, event_type, event_action,
                signature_valid, payload_hash, payload
            ) VALUES (?, ?, ?, ?, ?, ?, true, ?, CAST(? AS jsonb))
            ON CONFLICT (provider, provider_delivery_id) DO NOTHING
            """, id, connectionId, provider, deliveryId, eventType, eventAction, payloadHash, payload);
        if (inserted == 0) {
            WebhookDelivery existing = findDelivery(provider, deliveryId).orElseThrow();
            if (!existing.payloadHash().equals(payloadHash)) {
                throw new IllegalStateException("Webhook delivery ID was reused with a different payload");
            }
            return new AcceptResult(existing, true);
        }
        jdbc.update("""
            INSERT INTO integration_outbox (
                id, workspace_id, aggregate_type, aggregate_id, event_type, payload
            ) VALUES (?, ?, 'WEBHOOK_DELIVERY', ?, 'NORMALIZE_GITHUB_WEBHOOK', CAST(? AS jsonb))
            """, UUID.randomUUID(), workspaceId, id, json.write(Map.of("provider", provider)));
        return new AcceptResult(findDelivery(provider, deliveryId).orElseThrow(), false);
    }

    public Optional<WebhookDelivery> findDelivery(String provider, String deliveryId) {
        return jdbc.query("""
            SELECT id, connection_id, provider, provider_delivery_id, event_type, event_action,
                   signature_valid, payload_hash, payload::text AS payload, status, error_message,
                   received_at, processed_at
            FROM webhook_deliveries WHERE provider = ? AND provider_delivery_id = ?
            """, deliveryMapper, provider, deliveryId).stream().findFirst();
    }

    public Optional<WebhookDelivery> findDelivery(UUID id) {
        return jdbc.query("""
            SELECT id, connection_id, provider, provider_delivery_id, event_type, event_action,
                   signature_valid, payload_hash, payload::text AS payload, status, error_message,
                   received_at, processed_at
            FROM webhook_deliveries WHERE id = ?
            """, deliveryMapper, id).stream().findFirst();
    }

    public Optional<IntegrationEvent> claim(String workerId) {
        return jdbc.query("""
            WITH candidate AS (
                SELECT id FROM integration_outbox
                WHERE status IN ('QUEUED', 'RETRYING') AND available_at <= now()
                ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1
            )
            UPDATE integration_outbox event SET status = 'RUNNING', attempts = attempts + 1,
                locked_at = now(), locked_by = ?
            FROM candidate WHERE event.id = candidate.id
            RETURNING event.id, event.workspace_id, event.aggregate_type, event.aggregate_id,
                      event.event_type, event.payload::text AS payload, event.status, event.attempts,
                      event.max_attempts, event.last_error, event.created_at, event.completed_at
            """, eventMapper, workerId).stream().findFirst();
    }

    @Transactional
    public void complete(IntegrationEvent event) {
        jdbc.update("UPDATE integration_outbox SET status = 'COMPLETED', completed_at = now(), " +
            "locked_at = NULL, locked_by = NULL, last_error = NULL WHERE id = ?", event.id());
        if ("WEBHOOK_DELIVERY".equals(event.aggregateType())) {
            jdbc.update("UPDATE webhook_deliveries SET status = 'PROCESSED', processed_at = now(), " +
                "error_message = NULL WHERE id = ?", event.aggregateId());
        }
    }

    @Transactional
    public boolean retryOrFail(IntegrationEvent event, String message) {
        boolean retry = event.attempts() < event.maxAttempts();
        if (retry) {
            int seconds = Math.min(120, 1 << Math.min(event.attempts(), 6));
            jdbc.update("UPDATE integration_outbox SET status = 'RETRYING', " +
                "available_at = now() + (? * interval '1 second'), locked_at = NULL, locked_by = NULL, " +
                "last_error = ? WHERE id = ?", seconds, truncate(message), event.id());
        } else {
            jdbc.update("UPDATE integration_outbox SET status = 'FAILED', completed_at = now(), locked_at = NULL, " +
                "locked_by = NULL, last_error = ? WHERE id = ?", truncate(message), event.id());
            if ("WEBHOOK_DELIVERY".equals(event.aggregateType())) {
                jdbc.update("UPDATE webhook_deliveries SET status = 'FAILED', processed_at = now(), " +
                    "error_message = ? WHERE id = ?", truncate(message), event.aggregateId());
            }
        }
        return retry;
    }

    public int recoverStale(int staleSeconds) {
        return jdbc.update("""
            UPDATE integration_outbox
            SET status = CASE WHEN attempts < max_attempts THEN 'RETRYING' ELSE 'FAILED' END,
                available_at = now(), locked_at = NULL, locked_by = NULL,
                last_error = 'Integration worker lease expired; event recovered',
                completed_at = CASE WHEN attempts >= max_attempts THEN now() ELSE completed_at END
            WHERE status = 'RUNNING' AND locked_at < now() - (? * interval '1 second')
            """, staleSeconds);
    }

    public int queueDepth() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM integration_outbox " +
            "WHERE status IN ('QUEUED', 'RETRYING', 'RUNNING')", Integer.class);
        return count == null ? 0 : count;
    }

    private String truncate(String message) {
        if (message == null) return "Integration event failed";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record AcceptResult(WebhookDelivery delivery, boolean duplicate) {}
}
