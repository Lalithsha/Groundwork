package com.groundwork.evidence.adapter.in.web;

import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.evidence.application.ChangeSetRepository;
import com.groundwork.evidence.application.CurrentUserIdResolver;
import com.groundwork.evidence.application.PolicyEvaluationService;
import com.groundwork.evidence.application.PolicyRepository;
import com.groundwork.evidence.domain.ChangeFinding;
import com.groundwork.evidence.domain.ChangeSet;
import com.groundwork.evidence.domain.PolicyEvaluation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChangeEvidenceController {
    private final ChangeSetRepository changes;
    private final PolicyRepository policies;
    private final PolicyEvaluationService evaluation;
    private final WorkspaceAccessService access;
    private final CurrentUserIdResolver users;
    private final com.groundwork.evidence.application.ProductAnalyticsService analytics;

    public ChangeEvidenceController(ChangeSetRepository changes, PolicyRepository policies,
            PolicyEvaluationService evaluation, WorkspaceAccessService access, CurrentUserIdResolver users,
            com.groundwork.evidence.application.ProductAnalyticsService analytics) {
        this.changes = changes;
        this.policies = policies;
        this.evaluation = evaluation;
        this.access = access;
        this.users = users;
        this.analytics = analytics;
    }

    @GetMapping("/workspaces/{workspaceId}/changes")
    public List<ChangeSet> list(@PathVariable UUID workspaceId,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "100") int limit) {
        access.requireViewer(workspaceId);
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        return changes.findByWorkspace(workspaceId, state, limit);
    }

    @GetMapping("/changes/{changeId}")
    public ChangeDetail detail(@PathVariable UUID changeId) {
        ChangeSet change = requireChange(changeId, "VIEWER");
        return new ChangeDetail(change, changes.findings(change.workspaceId(), change.id()),
            policies.evaluations(change.workspaceId(), change.id()),
            changes.findingFeedback(change.workspaceId(), change.id()));
    }

    @PostMapping("/changes/{changeId}/reanalyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> reanalyze(@PathVariable UUID changeId) {
        ChangeSet change = requireChange(changeId, "EDITOR");
        var job = changes.queueAnalysis(change.id(), change.headSha(), ChangeSetRepository.ANALYZER_VERSION);
        return Map.of("jobId", job.id(), "status", job.status());
    }

    @PostMapping("/changes/{changeId}/evaluate-policies")
    public List<PolicyEvaluation> evaluate(@PathVariable UUID changeId) {
        ChangeSet change = requireChange(changeId, "EDITOR");
        return evaluation.evaluate(change);
    }

    @PostMapping("/changes/{changeId}/policies/dry-run")
    public List<PolicyEvaluationService.PolicyPreview> dryRun(@PathVariable UUID changeId) {
        ChangeSet change = requireChange(changeId, "EDITOR");
        return evaluation.dryRun(change);
    }

    @PatchMapping("/changes/{changeId}/findings/{findingId}")
    public Map<String, Object> reviewFinding(@PathVariable UUID changeId, @PathVariable UUID findingId,
            @Valid @RequestBody FindingReviewRequest request) {
        ChangeSet change = requireChange(changeId, "EDITOR");
        String status = request.status().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("CONFIRMED", "DISMISSED", "EDITED").contains(status)) {
            throw new IllegalArgumentException("Finding review status is invalid");
        }
        boolean updated = changes.reviewFinding(change.workspaceId(), change.id(), findingId,
            users.optional().orElse(null), status, request.reason(), request.reasonCode());
        if (!updated) throw new IllegalArgumentException("Finding was not found for the change");
        analytics.record(change.workspaceId(), "finding_reviewed", "FINDING", findingId,
            Map.of("status", status, "reasonCode", request.reasonCode() == null ? "" : request.reasonCode()));
        return Map.of("findingId", findingId, "reviewStatus", status);
    }

    private ChangeSet requireChange(UUID changeId, String role) {
        ChangeSet change = changes.findById(changeId)
            .orElseThrow(() -> new IllegalArgumentException("Change set was not found"));
        access.requireRole(change.workspaceId(), role);
        return change;
    }

    public record ChangeDetail(ChangeSet change, List<ChangeFinding> findings,
                               List<PolicyEvaluation> policies, List<Map<String, Object>> feedback) {}
    public record FindingReviewRequest(@NotBlank String status, String reason, String reasonCode) {}
}
