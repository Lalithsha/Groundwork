package com.groundwork.evidence.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PolicyEvaluation(
        UUID id,
        UUID changeSetId,
        UUID policyVersionId,
        String policyName,
        int policyVersion,
        String result,
        List<Map<String, Object>> evidence,
        String message,
        Instant evaluatedAt) {
}
