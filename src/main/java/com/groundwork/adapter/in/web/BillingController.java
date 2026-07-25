package com.groundwork.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${groundwork.razorpay.key-id:rzp_test_demo_key}")
    private String keyId;

    public BillingController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateOrderRequest(String userId) {}
    public record WebhookPayload(String event, Map<String, Object> payload) {}

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        String orderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        return ResponseEntity.ok(Map.of(
            "orderId", orderId,
            "amount", 199900,
            "currency", "INR",
            "keyId", keyId
        ));
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> body, @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        String event = (String) body.get("event");
        if ("payment.captured".equals(event)) {
            Map<String, Object> payload = (Map<String, Object>) body.get("payload");
            Map<String, Object> payment = (Map<String, Object>) payload.get("payment");
            Map<String, Object> entity = (Map<String, Object>) payment.get("entity");
            String email = (String) entity.get("email");

            if (email != null) {
                jdbcTemplate.update(
                    "UPDATE subscriptions SET tier = 'PAID' WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                    email
                );
            }
        }
        return ResponseEntity.ok(Map.of("status", "processed"));
    }
}
