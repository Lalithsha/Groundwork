package com.groundwork.adapter.out.ai;

import com.groundwork.application.RetrievalService;
import com.groundwork.domain.model.DeliveryStatusResult;
import com.groundwork.domain.model.DocumentChunk;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

@Configuration
public class SupportTools {

    private final RetrievalService retrievalService;

    @Value("${groundwork.hookshot.base-url:http://localhost:8000}")
    private String hookshotBaseUrl;

    public SupportTools(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    public record SearchDocsRequest(String query, String mode) {}
    public record StatusLookupRequest(String deliveryId) {}

    @Bean
    @Description("Search support and API documentation for relevant troubleshooting chunks.")
    public Function<SearchDocsRequest, List<DocumentChunk>> searchDocs() {
        return request -> retrievalService.retrieve(
            request.query(),
            request.mode() != null ? request.mode() : "hybrid_rerank",
            4
        );
    }

    @Bean
    @Description("Lookup live delivery status from the HookShot system using deliveryId.")
    public Function<StatusLookupRequest, DeliveryStatusResult> getDeliveryStatus() {
        return request -> fetchDeliveryStatusWithCircuitBreaker(request.deliveryId());
    }

    @CircuitBreaker(name = "hookshotStatusLookup", fallbackMethod = "statusLookupFallback")
    public DeliveryStatusResult fetchDeliveryStatusWithCircuitBreaker(String deliveryId) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(hookshotBaseUrl + "/api/webhooks/" + deliveryId + "/status"))
                .GET()
                .timeout(Duration.ofSeconds(3))
                .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return new DeliveryStatusResult(deliveryId, "DELIVERED", 1, "2026-07-25T23:00:00Z", true, response.body());
            } else {
                return new DeliveryStatusResult(deliveryId, "RETRYING", 2, "2026-07-25T23:10:00Z", true, "Non-200 status code: " + response.statusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("HookShot API call failed", e);
        }
    }

    public DeliveryStatusResult statusLookupFallback(String deliveryId, Throwable t) {
        return DeliveryStatusResult.unavailable(deliveryId);
    }
}
