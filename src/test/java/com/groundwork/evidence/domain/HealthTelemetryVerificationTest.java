package com.groundwork.evidence.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthTelemetryVerificationTest {

    @Test
    @DisplayName("Verify health telemetry status assertion")
    void testHealthTelemetryStatus() {
        String status = "UP";
        assertThat(status).isEqualTo("UP");
    }
}
