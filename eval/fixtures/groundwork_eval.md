# Delivery Operations Reference

When a delivery exhausts all five retry attempts, the system moves it to the dead-letter queue (DLQ). It is not discarded.

Retry delay starts at 5 seconds and doubles after each failed attempt, capped after five attempts.

To inspect one delivery, call `GET /api/webhooks/{deliveryId}/status` with the delivery identifier.

Free-tier API clients are limited to 20 requests per minute.
