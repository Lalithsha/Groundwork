package com.groundwork.config;

import com.groundwork.application.DocumentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Configuration
public class CorpusSeedLoader {

    record SeedDoc(String title, String sourceType, String content) {}

    @Bean
    public CommandLineRunner initCorpus(DocumentRepository repository) {
        return args -> {
            List<SeedDoc> seeds = List.of(
                new SeedDoc(
                    "HookShot Architecture & Webhook Overview",
                    "readme",
                    "HookShot is an event-driven webhook delivery gateway with dead-letter queue (DLQ) support and exponential backoff retry semantics. It ensures reliable event delivery across distributed microservices."
                ),
                new SeedDoc(
                    "Webhook Delivery Retry & DLQ Policy",
                    "api_doc",
                    "When a webhook endpoint returns a non-2xx status code or times out, HookShot retries up to 5 attempts. Initial backoff starts at 5 seconds, doubling per attempt. After 5 failed retries, the payload is automatically routed to the Dead Letter Queue (DLQ)."
                ),
                new SeedDoc(
                    "Delivery Status Lookup API",
                    "api_doc",
                    "To query the real-time delivery status of a payload, call GET /api/webhooks/{deliveryId}/status. The status can be DELIVERED, RETRYING, FAILED, or DLQ."
                ),
                new SeedDoc(
                    "Support FAQ — Rate Limiting & Throttling",
                    "faq",
                    "Free tier users are rate limited to 20 requests per minute using Bucket4j token bucket algorithm. Paid tier users have unlimited burst capacity up to their monthly quota."
                )
            );

            for (SeedDoc seed : seeds) {
                String hash = sha256(seed.content());
                repository.save(seed.title(), seed.content(), seed.sourceType(), hash);
            }
            System.out.println("✅ Phase 0: Corpus seed data initialized successfully.");
        };
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }
}
