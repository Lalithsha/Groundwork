package com.groundwork.config;

import com.groundwork.application.DocumentRepository;
import com.groundwork.application.Hashing;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "groundwork.seed.enabled", havingValue = "true")
public class CorpusSeedLoader {
    record SeedDocument(String title, String sourceType, String content) {}

    @Bean
    CommandLineRunner seedDemonstrationCorpus(DocumentRepository repository) {
        return args -> seedDocuments().forEach(document -> repository.save(
            document.title(), document.content(), document.sourceType(), Hashing.sha256(document.content())));
    }

    private List<SeedDocument> seedDocuments() {
        return List.of(
            new SeedDocument("HookShot Architecture & Webhook Overview", "readme",
                "HookShot is an event-driven webhook delivery gateway with dead-letter queue support and exponential backoff."),
            new SeedDocument("Webhook Delivery Retry & DLQ Policy", "api_doc",
                "HookShot retries failed deliveries five times. After the final failure, it routes the payload to the dead-letter queue."),
            new SeedDocument("Delivery Status Lookup API", "api_doc",
                "Call GET /api/webhooks/{deliveryId}/status to retrieve DELIVERED, RETRYING, FAILED, or DLQ status."),
            new SeedDocument("Support FAQ — Rate Limiting", "faq",
                "Free-tier users are limited to twenty requests per minute with a token-bucket policy.")
        );
    }
}
