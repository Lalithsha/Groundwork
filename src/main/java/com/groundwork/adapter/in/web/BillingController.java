package com.groundwork.adapter.in.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@ConditionalOnProperty(name = "groundwork.billing.enabled", havingValue = "true")
public class BillingController {

    public record CreateOrderRequest(String userId) {}

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(501).body(Map.of(
            "error", "Billing provider integration is not configured",
            "message", "Enable billing only after replacing the placeholder controller with a verified provider adapter"
        ));
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> body, @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        return ResponseEntity.status(501).body(Map.of("error", "Billing webhook integration is disabled"));
    }
}
