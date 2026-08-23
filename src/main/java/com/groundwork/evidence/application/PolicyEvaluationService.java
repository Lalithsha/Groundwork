package com.groundwork.evidence.application;

import com.groundwork.evidence.domain.ChangeFinding;
import com.groundwork.evidence.domain.ChangeSet;
import com.groundwork.evidence.domain.EvidencePolicy;
import com.groundwork.evidence.domain.PolicyEvaluation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PolicyEvaluationService {
    private final PolicyRepository policies;
    private final ChangeSetRepository changes;

    public PolicyEvaluationService(PolicyRepository policies, ChangeSetRepository changes) {
        this.policies = policies;
        this.changes = changes;
    }

    public List<PolicyEvaluation> evaluate(ChangeSet change) {
        policies.ensureDefaults(change.workspaceId());
        List<ChangeFinding> findings = changes.findings(change.workspaceId(), change.id());
        for (EvidencePolicy policy : policies.active(change.workspaceId())) {
            if (policy.policyVersionId() == null) continue;
            PolicyPreview preview = decide(change, policy, findings);
            policies.saveEvaluation(change.id(), policy.policyVersionId(), preview.result(), preview.evidence(), preview.message());
        }
        return policies.evaluations(change.workspaceId(), change.id());
    }

    public List<PolicyPreview> dryRun(ChangeSet change) {
        policies.ensureDefaults(change.workspaceId());
        List<ChangeFinding> findings = changes.findings(change.workspaceId(), change.id());
        return policies.findByWorkspace(change.workspaceId()).stream()
            .filter(policy -> policy.policyVersionId() != null)
            .map(policy -> decide(change, policy, findings)).toList();
    }

    private PolicyPreview decide(ChangeSet change, EvidencePolicy policy, List<ChangeFinding> findings) {
        String category = String.valueOf(policy.definition().getOrDefault("findingCategory", ""));
        ChangeFinding finding = findings.stream().filter(value -> value.category().equals(category))
            .findFirst().orElse(null);
        String result;
        String message;
        List<Map<String, Object>> evidence = new ArrayList<>();
        if (policies.hasActiveException(change.id(), policy.policyVersionId())) {
            result = "EXEMPTED";
            message = policy.name() + " has an active approved exception.";
        } else if (finding == null) {
            result = "UNKNOWN";
            message = "Required evidence was not produced by the current analyzer.";
        } else {
            String state = String.valueOf(finding.details().getOrDefault("state",
                Boolean.TRUE.equals(finding.details().get("missing")) ? "MISSING" : "PRESENT"));
            result = "UNKNOWN".equals(state) || "STALE".equals(state) ? "UNKNOWN" :
                "MISSING".equals(state) ? "FAIL" : "PASS";
            message = finding.statement();
            evidence.add(Map.of("findingId", finding.id(), "findingKey", finding.findingKey(),
                "category", finding.category(), "evidenceStatus", finding.evidenceStatus(), "state", state));
        }
        return new PolicyPreview(policy.id(), policy.policyVersionId(), policy.name(), policy.activeVersion(),
            policy.enabled(), result, message, List.copyOf(evidence));
    }

    public record PolicyPreview(java.util.UUID policyId, java.util.UUID policyVersionId, String policyName,
                                Integer version, boolean enabled, String result, String message,
                                List<Map<String, Object>> evidence) {}
}
