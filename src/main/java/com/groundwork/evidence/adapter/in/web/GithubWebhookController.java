package com.groundwork.evidence.adapter.in.web;

import com.groundwork.evidence.application.GithubWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations/github")
public class GithubWebhookController {
    private final GithubWebhookService webhooks;

    public GithubWebhookController(GithubWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestBody String payload,
            HttpServletRequest request) {
        var accepted = webhooks.accept(deliveryId, eventType, signature, payload);
        URI location = URI.create(request.getRequestURI() + "/deliveries/" + accepted.delivery().id());
        return ResponseEntity.status(accepted.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED)
            .location(location)
            .body(Map.of("deliveryId", accepted.delivery().id(), "duplicate", accepted.duplicate(),
                "status", accepted.delivery().status()));
    }
}
