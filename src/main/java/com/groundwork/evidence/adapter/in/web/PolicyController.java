package com.groundwork.evidence.adapter.in.web;

import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.evidence.application.ChangeSetRepository;
import com.groundwork.evidence.application.CurrentUserIdResolver;
import com.groundwork.evidence.application.PolicyEvaluationService;
import com.groundwork.evidence.application.PolicyRepository;
import com.groundwork.evidence.domain.EvidencePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PolicyController {
    private static final Set<String> RULE_TYPES = Set.of("REQUIRE_LINKED_INTENT", "REQUIRE_TEST_EVIDENCE",
        "REQUIRE_SUCCESSFUL_CHECKS", "REQUIRE_OWNER_APPROVAL", "REQUIRE_API_COMPATIBILITY",
        "REQUIRE_API_CHANGELOG", "REQUIRE_ROLLBACK_PLAN");
    private static final Set<String> SEVERITIES = Set.of("INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL");
    private final PolicyRepository policies;
    private final ChangeSetRepository changes;
    private final PolicyEvaluationService evaluation;
    private final CurrentUserIdResolver users;
    private final WorkspaceAccessService access;

    public PolicyController(PolicyRepository policies, ChangeSetRepository changes,
            PolicyEvaluationService evaluation, CurrentUserIdResolver users, WorkspaceAccessService access) {
        this.policies = policies;
        this.changes = changes;
        this.evaluation = evaluation;
        this.users = users;
        this.access = access;
    }

    @GetMapping("/workspaces/{workspaceId}/policies")
    public List<EvidencePolicy> list(@PathVariable UUID workspaceId) {
        access.requireViewer(workspaceId);
        policies.ensureDefaults(workspaceId);
        return policies.findByWorkspace(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/policies")
    @ResponseStatus(HttpStatus.CREATED)
    public EvidencePolicy create(@PathVariable UUID workspaceId, @Valid @RequestBody PolicyRequest request) {
        access.requireAdmin(workspaceId);
        String type = request.ruleType().toUpperCase(java.util.Locale.ROOT);
        String severity = request.severity().toUpperCase(java.util.Locale.ROOT);
        if (!RULE_TYPES.contains(type)) throw new IllegalArgumentException("Policy rule type is invalid");
        if (!SEVERITIES.contains(severity)) throw new IllegalArgumentException("Policy severity is invalid");
        Object category = request.definition().get("findingCategory");
        if (!(category instanceof String value) || value.isBlank() || request.definition().size() != 1) {
            throw new IllegalArgumentException("Policy definition must contain exactly one non-empty findingCategory");
        }
        return policies.createVersion(workspaceId, request.name(), request.description(), type, severity,
            request.definition(), users.optional().orElse(null), request.enabled());
    }

    @PatchMapping("/workspaces/{workspaceId}/policies/{policyId}/activation")
    public Map<String, Object> activate(@PathVariable UUID workspaceId, @PathVariable UUID policyId,
            @Valid @RequestBody ActivationRequest request) {
        access.requireAdmin(workspaceId);
        if (!policies.activate(workspaceId, policyId, request.version(), request.enabled())) {
            throw new IllegalArgumentException("Policy version was not found");
        }
        return Map.of("policyId", policyId, "version", request.version(), "enabled", request.enabled());
    }

    @PostMapping("/workspaces/{workspaceId}/changes/{changeId}/policy-exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> exception(@PathVariable UUID workspaceId, @PathVariable UUID changeId,
            @Valid @RequestBody ExceptionRequest request) {
        access.requireAdmin(workspaceId);
        var change = changes.findAuthorized(workspaceId, changeId)
            .orElseThrow(() -> new IllegalArgumentException("Change set was not found"));
        UUID id = policies.createException(workspaceId, change.id(), request.policyVersionId(),
            request.rationale(), users.optional().orElse(null), request.expiresAt());
        evaluation.evaluate(change);
        return Map.of("exceptionId", id, "expiresAt", request.expiresAt());
    }

    public record PolicyRequest(@NotBlank String name, String description, @NotBlank String ruleType,
            @NotBlank String severity, @NotNull Map<String, Object> definition, boolean enabled) {}
    public record ActivationRequest(int version, boolean enabled) {}
    public record ExceptionRequest(@NotNull UUID policyVersionId, @NotBlank String rationale,
                                   @NotNull @Future Instant expiresAt) {}
}
