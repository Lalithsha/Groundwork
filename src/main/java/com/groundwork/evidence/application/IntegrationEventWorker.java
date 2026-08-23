package com.groundwork.evidence.application;

import com.groundwork.evidence.domain.IntegrationEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
public class IntegrationEventWorker {
    private static final Logger log = LoggerFactory.getLogger(IntegrationEventWorker.class);
    private final IntegrationEventRepository events;
    private final GithubWebhookNormalizer github;
    private final MeterRegistry metrics;
    private final String workerId;
    private final int staleAfterSeconds;

    public IntegrationEventWorker(IntegrationEventRepository events, GithubWebhookNormalizer github,
            MeterRegistry metrics, @Value("${groundwork.integrations.worker-id:}") String configuredWorkerId,
            @Value("${groundwork.integrations.stale-after-seconds:300}") int staleAfterSeconds) {
        this.events = events;
        this.github = github;
        this.metrics = metrics;
        this.workerId = configuredWorkerId == null || configuredWorkerId.isBlank()
            ? "integration-" + ManagementFactory.getRuntimeMXBean().getName() : configuredWorkerId;
        this.staleAfterSeconds = staleAfterSeconds;
        metrics.gauge("groundwork.integration.queue.depth", events, IntegrationEventRepository::queueDepth);
    }

    @Scheduled(fixedDelayString = "${groundwork.integrations.poll-delay-ms:500}")
    public void poll() {
        int recovered = events.recoverStale(staleAfterSeconds);
        if (recovered > 0) log.warn("Recovered {} stale integration event(s)", recovered);
        events.claim(workerId).ifPresent(this::processSafely);
    }

    private void processSafely(IntegrationEvent event) {
        long started = System.nanoTime();
        try {
            if ("NORMALIZE_GITHUB_WEBHOOK".equals(event.eventType())) {
                var delivery = events.findDelivery(event.aggregateId())
                    .orElseThrow(() -> new IllegalStateException("Webhook delivery no longer exists"));
                github.normalize(event.workspaceId(), delivery);
            }
            events.complete(event);
            metrics.counter("groundwork.integration.events", "result", "completed", "type", event.eventType()).increment();
        } catch (Exception exception) {
            boolean retry = events.retryOrFail(event, exception.getMessage());
            metrics.counter("groundwork.integration.events", "result", retry ? "retry" : "failed",
                "type", event.eventType()).increment();
            log.warn("Integration event {} {}: {}", event.id(), retry ? "will retry" : "failed", exception.getMessage());
        } finally {
            metrics.timer("groundwork.integration.processing", "type", event.eventType())
                .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
}
