package com.groundwork.domain.model;

public record DeliveryStatusResult(
    String deliveryId,
    String status,
    int attempts,
    String lastAttemptAt,
    boolean available,
    String message
) {
    public static DeliveryStatusResult unavailable(String deliveryId) {
        return new DeliveryStatusResult(
            deliveryId,
            "UNKNOWN",
            0,
            null,
            false,
            "HookShot service is currently unreachable or timed out."
        );
    }
}
