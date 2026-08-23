package com.groundwork.evidence.application;

import com.groundwork.application.Hashing;
import com.groundwork.evidence.domain.ChangeSet;
import com.groundwork.evidence.domain.ReleaseRecord;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReleaseRecordService {
    private final ChangeSetRepository changes;
    private final PolicyRepository policies;
    private final ReleaseRecordRepository releases;
    private final CurrentUserIdResolver users;
    private final CanonicalJson canonicalJson;

    public ReleaseRecordService(ChangeSetRepository changes, PolicyRepository policies,
            ReleaseRecordRepository releases, CurrentUserIdResolver users, CanonicalJson canonicalJson) {
        this.changes = changes;
        this.policies = policies;
        this.releases = releases;
        this.users = users;
        this.canonicalJson = canonicalJson;
    }

    public ReleaseRecord freeze(UUID workspaceId, String name, String repositoryFullName,
            String baseRef, String headRef, List<UUID> changeSetIds) {
        if (changeSetIds == null || changeSetIds.isEmpty()) {
            throw new IllegalArgumentException("At least one change set is required");
        }
        List<ChangeSet> selected = changeSetIds.stream().distinct()
            .map(id -> changes.findAuthorized(workspaceId, id)
                .orElseThrow(() -> new IllegalArgumentException("Change set is outside the workspace")))
            .sorted(Comparator.comparing(ChangeSet::repositoryFullName).thenComparing(ChangeSet::headSha))
            .toList();
        List<Map<String, Object>> manifestChanges = new ArrayList<>();
        List<ReleaseRecordRepository.ChangeEvidence> evidence = new ArrayList<>();
        boolean ready = true;
        for (ChangeSet change : selected) {
            var evaluations = policies.evaluations(workspaceId, change.id());
            boolean blocked = evaluations.stream().anyMatch(value -> "FAIL".equals(value.result())) ||
                evaluations.stream().anyMatch(value -> "UNKNOWN".equals(value.result()));
            ready &= !blocked && "COMPLETED".equals(change.currentAnalysisStatus());
            Map<String, Object> entry = changeEntry(workspaceId, change);
            manifestChanges.add(entry);
            String digest = Hashing.sha256(canonicalJson.write(entry));
            evidence.add(new ReleaseRecordRepository.ChangeEvidence(change.id(), digest,
                Map.of("headSha", change.headSha(), "repository", change.repositoryFullName())));
        }
        Instant frozenAt = Instant.now();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", "groundwork-release-evidence/v1");
        manifest.put("name", name);
        manifest.put("repository", repositoryFullName);
        manifest.put("baseRef", baseRef == null ? "" : baseRef);
        manifest.put("headRef", headRef);
        manifest.put("frozenAt", frozenAt.toString());
        manifest.put("ready", ready);
        manifest.put("changes", manifestChanges);
        String hash = Hashing.sha256(canonicalJson.write(manifest));
        return releases.create(workspaceId, name, repositoryFullName, baseRef, headRef,
            ready ? "READY" : "DRAFT", manifest, hash, users.optional().orElse(null), evidence);
    }

    public Verification verify(UUID workspaceId, UUID releaseId) {
        ReleaseRecord release = releases.findAuthorized(workspaceId, releaseId)
            .orElseThrow(() -> new IllegalArgumentException("Release record was not found"));
        String calculated = Hashing.sha256(canonicalJson.write(release.manifest()));
        List<Map<String, Object>> storedEvidence = releases.evidence(workspaceId, releaseId);
        List<Map<String, Object>> integrity = new ArrayList<>();
        boolean evidenceValid = true;
        for (Map<String, Object> item : storedEvidence) {
            UUID changeSetId = (UUID) item.get("changeSetId");
            String storedDigest = String.valueOf(item.get("evidenceDigest"));
            ChangeSet change = changes.findAuthorized(workspaceId, changeSetId).orElse(null);
            String currentDigest = change == null ? "missing" : Hashing.sha256(canonicalJson.write(changeEntry(workspaceId, change)));
            boolean itemValid = storedDigest.equals(currentDigest);
            evidenceValid &= itemValid;
            integrity.add(Map.of("changeSetId", changeSetId, "storedDigest", storedDigest,
                "currentDigest", currentDigest, "valid", itemValid));
        }
        boolean manifestValid = release.manifestHash().equals(calculated);
        return new Verification(manifestValid && evidenceValid, manifestValid, evidenceValid,
            release.manifestHash(), calculated, storedEvidence, integrity);
    }

    private Map<String, Object> changeEntry(UUID workspaceId, ChangeSet change) {
        var findings = changes.findings(workspaceId, change.id());
        var evaluations = policies.evaluations(workspaceId, change.id());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("changeSetId", change.id().toString());
        entry.put("repository", change.repositoryFullName());
        entry.put("pullRequestNumber", change.pullRequestNumber());
        entry.put("title", change.title());
        entry.put("baseSha", change.baseSha());
        entry.put("headSha", change.headSha());
        entry.put("analysisStatus", change.currentAnalysisStatus());
        entry.put("findings", findings.stream().map(finding -> Map.of(
            "key", finding.findingKey(), "category", finding.category(), "severity", finding.severity(),
            "evidenceStatus", finding.evidenceStatus(), "statement", finding.statement())).toList());
        entry.put("policies", evaluations.stream().map(evaluation -> Map.of(
            "name", evaluation.policyName(), "version", evaluation.policyVersion(),
            "result", evaluation.result(), "message", evaluation.message())).toList());
        return entry;
    }

    public record Verification(boolean valid, boolean manifestValid, boolean evidenceValid,
                               String storedHash, String calculatedHash, List<Map<String, Object>> evidence,
                               List<Map<String, Object>> evidenceIntegrity) {}
}
