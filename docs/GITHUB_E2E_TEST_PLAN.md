# GitHub end-to-end test plan

This plan uses [issue #14](https://github.com/Lalithsha/Groundwork/issues/14) and
[pull request #13](https://github.com/Lalithsha/Groundwork/pull/13) as a stable,
connected fixture. Keep the issue and pull request open until the final lifecycle
test.

## Fixture and expected evidence

Pull request #13 changes:

- `.github/CODEOWNERS`, assigning the changed paths to `@Lalithsha`;
- `docs/HEALTH_TELEMETRY.md`, including rollout and rollback guidance;
- `HealthTelemetryVerificationTest.java`, providing changed test evidence.

The pull request title and body reference issue `#14` and requirement `PROJ-101`.
Groundwork's deterministic analyzer should therefore produce:

| Finding | Expected state | Reason |
| --- | --- | --- |
| Linked intent | `PRESENT` / policy `PASS` | The PR explicitly references `#14` and `PROJ-101`. |
| Test evidence | `PRESENT` / policy `PASS` | A Java test file is part of the change. |
| Successful checks | `MISSING` / policy `FAIL` while any GitHub check fails | PR #13 currently has failed security checks. This is the intended blocking-path fixture. |
| Owner approval | `MISSING` / policy `FAIL` until a matching CODEOWNER approves | A comment is not an approval. The repository owner cannot approve their own PR on GitHub, so use a policy exception to test that workflow if no second collaborator is available. |
| API compatibility | `PRESENT` / policy `PASS` | No OpenAPI file changes. |
| API changelog | `PRESENT` / policy `PASS` | No breaking API change was detected. |
| Rollback evidence | `PRESENT` / policy `PASS` | No database migration requires rollback evidence; the PR also documents rollback. |
| Changed scope | `PRESENT` | Three changed files should be classified as configuration/other, documentation, and test. |

The overall `Groundwork evidence` GitHub Check should conclude `failure` while an
enabled policy is `FAIL`, `neutral` if no policy fails but at least one is
`UNKNOWN`, and `success` only when every enabled policy passes or is exempted.

## 1. GitHub ingestion and change queue

1. Open pull request #13 on GitHub and edit its description by adding and then
   removing a harmless space. Save it to emit a fresh `pull_request` webhook.
2. Open Groundwork at `http://localhost:8081/changes`.
3. Wait for the asynchronous ingestion and analysis workers, then refresh.

Expected behavior:

- One change row identifies `Lalithsha/Groundwork`, PR `#13`, its title, source
  branch `feature/github-evidence-verification`, and target branch `main`.
- The analysis progresses from queued/running to `COMPLETED` (or a partial state
  if the optional AI provider is unavailable). Deterministic findings must still
  be present when AI analysis is degraded.
- Re-delivering the same GitHub delivery must be idempotent; it must not create a
  duplicate change for the same PR/head revision.

## 2. Change detail and policy decisions

Open the PR #13 row and wait until analysis is no longer running.

Expected behavior:

- The header links back to GitHub and shows the current head SHA.
- The changed-file count is `3` before this test-plan commit is ingested and `4`
  afterward.
- Findings and policies match the table above. In particular, failed GitHub
  security checks make `Successful checks` fail; the application must not claim
  that all CI passed.
- `Dry-run policies` computes a preview without persisting new policy state.
- `Evaluate` persists current decisions. `Reanalyze` queues a new analysis and
  eventually replaces the current result for the latest head SHA.
- Expanding a finding shows its exact JSON evidence. `Confirm` or `Dismiss`
  records reviewer feedback and refreshes the finding. There is currently no
  separate Edit action.

## 3. GitHub Check Run

On GitHub, open PR #13 and select **Checks**.

Expected behavior:

- A check named `Groundwork evidence` exists for the current head commit.
- Its summary reports evaluated policy, failed, and unknown counts.
- Its conclusion matches Groundwork's enabled policy decisions. At the time this
  plan was written, `failure` is expected because GitHub security checks failed
  and CODEOWNER approval is absent.
- The details link points to the Groundwork change record. With a local base URL,
  it works only on the machine running Groundwork.

## 4. Evidence catalog and graph

Open `http://localhost:8081/evidence` and search for `telemetry`, `rollback`,
`#14`, and `PROJ-101` separately.

Expected behavior:

- Results are grounded in persisted artifacts and show stable source identity,
  version/provenance, and relevance information.
- The graph connects the repository and PR change. The PR's explicit references
  should be represented as intent/requirement evidence when normalization has
  extracted them.
- A missing query returns an honest empty/insufficient result, not invented
  evidence.

## 5. Policies and exception API

Open `http://localhost:8081/policies`.

Expected behavior:

- Seven default policies appear with immutable version numbers and activation
  state: linked intent, test evidence, successful checks, owner approval, API
  compatibility, API changelog, and rollback evidence.
- Disabling a policy changes it to dry-run mode; re-enable the same version after
  observing the behavior.
- Time-bounded exceptions are supported by the backend endpoint
  `POST /api/workspaces/{workspaceId}/changes/{changeId}/policy-exceptions` and
  require an administrator, policy-version ID, rationale, and future expiry.
  The current frontend does not expose a **Request Exception** button, so do not
  expect the click flow shown in older test notes.
- After a valid exception is created and policies are evaluated, the applicable
  decision becomes `EXEMPTED`; after expiry it evaluates normally again.

## 6. Release record

Open `http://localhost:8081/releases`, select PR #13, name the release, and click
**Freeze 1 change**.

Expected behavior:

- A release card appears with frozen time, status, repository, and SHA-256
  manifest hash.
- JSON, HTML, and PDF downloads succeed and describe the same frozen change and
  policy evidence.
- Creating another record after evidence changes produces a different immutable
  snapshot; the earlier record is not rewritten.

## 7. Sources and grounded chat

Open `http://localhost:8081/chat` (Sources & chat) and ask a question about an
uploaded/indexed source document.

Expected behavior:

- A supported answer includes citations to indexed source passages.
- If the PR files have not been ingested into the document corpus, questions
  such as “What is PR #13's rollback plan?” may correctly return insufficient
  evidence. The chat corpus and GitHub change-evidence catalog are separate
  retrieval paths in the current implementation.

## 8. Lifecycle cleanup test (run last)

Only after all other checks, merge or close PR #13 and close issue #14 as
appropriate.

Expected behavior:

- A subsequent GitHub webhook updates the change lifecycle without deleting its
  historical evidence or previously frozen release records.
- The GitHub issue and PR retain their native cross-reference and audit history.
