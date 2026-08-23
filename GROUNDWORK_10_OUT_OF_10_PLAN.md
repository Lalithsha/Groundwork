# Groundwork: 10/10 Product Evolution and Implementation Plan

**Status:** Proposed product direction and execution plan
**Date:** 2026-08-24
**Primary owner:** Solo full-stack engineer
**Planning horizon:** 16–20 focused weeks, followed by a measured pilot period

## Executive Decision

Groundwork should evolve from a document-intelligence application into an **evidence and release-readiness platform for human- and AI-authored software changes**.

The existing document ingestion, hybrid retrieval, grounded citations, structured extraction, comparison, workspace security, durable jobs, and knowledge graph are valuable foundations. They should not be discarded. Documents become one type of evidence alongside:

- GitHub issues, pull requests, commits, diffs, reviews, and check runs
- Jira requirements and acceptance criteria
- Confluence pages, ADRs, specifications, and runbooks
- OpenAPI contracts and version changes
- Test, coverage, security-scan, and build results
- Deployments, incidents, rollback plans, and approvals

The product's flagship question changes from:

> “What does this document say?”

to:

> “Why was this software change requested, what does it affect, what evidence supports it, is it ready to merge or release, and what is still missing?”

The working positioning is:

> **Groundwork is the evidence layer for AI-assisted software delivery. It connects intent, code changes, tests, decisions, and release proof so reviewers can understand and verify a change without reconstructing its context manually.**

This is a product evolution rather than a rewrite. Groundwork's current RAG capability becomes the evidence-retrieval subsystem inside a more valuable workflow.

The existing [Roadmap to 10/10](ROADMAP_TO_10.md) remains the engineering-remediation baseline for the document-intelligence foundation. This document supersedes its broader product direction and adds the next product, validation, integration, and portfolio phases; it does not remove the earlier quality gates.

---

## Why This Direction

### The user problem is larger than document search

Teams already have search and chat products. Atlassian Rovo and Microsoft Copilot Search connect many workplace sources, preserve permissions, and provide AI answers. Competing directly as another general-purpose knowledge assistant would leave Groundwork with weak differentiation.

The more specific and current problem is that faster AI-assisted coding creates more changes for humans to understand and verify. The missing layer is not another code generator. It is traceability and proof:

- Which requirement caused this pull request?
- Which acceptance criteria are satisfied?
- Which APIs, services, owners, and runbooks may be affected?
- Did the necessary tests, reviews, documentation, and rollback preparation happen?
- Which conclusions are deterministic, and which are AI suggestions?
- Can a reviewer inspect the evidence instead of trusting a generated summary?

Google's 2025 DORA research describes AI as an amplifier of an organization's existing strengths and weaknesses rather than a replacement for a sound delivery system. [Atlassian's 2026 AI-SDLC direction](https://www.atlassian.com/blog/company-news/ai-sdlc) makes the same market signal explicit: AI usage can rise without a matching increase in delivery velocity when intent, context, review, and governance remain fragmented.

### Market evidence

- Teams spend significant time searching for internal answers, validating the underlying knowledge problem: [Atlassian State of Teams 2025](https://www.atlassian.com/blog/state-of-teams-2025).
- Developers value quality, API design, and reliability more than the presence of AI features: [Stack Overflow 2025 Work Survey](https://survey.stackoverflow.co/2025/work).
- Developers remain concerned about AI accuracy, which makes citations, evidence, and deterministic checks meaningful product features: [Stack Overflow 2025 AI Survey](https://survey.stackoverflow.co/2025/ai).
- AI improves or degrades outcomes according to the surrounding engineering system: [DORA State of AI-assisted Software Development 2025](https://dora.dev/research/2025/dora-report/).
- GitHub Apps can publish rich, actionable pull-request checks, making the proposed workflow technically native to where developers already work: [GitHub Checks API](https://docs.github.com/en/rest/guides/using-the-rest-api-to-interact-with-checks).
- General enterprise search is already crowded and connector-heavy: [Atlassian Rovo connectors](https://www.atlassian.com/software/rovo/guides/admin-guide/rovo-connectors) and [Microsoft Copilot Search](https://learn.microsoft.com/en-us/copilot/microsoft-365/microsoft-365-copilot-search).

### Why this will be stronger on a resume

This direction demonstrates a coherent combination of skills that current Java, platform, full-stack, and applied-AI roles value:

- Java 21 and Spring Boot application design
- OAuth/GitHub App authentication and webhook security
- Idempotent event ingestion and durable background processing
- PostgreSQL relational, full-text, vector, and graph-style querying
- Multi-tenant authorization and encrypted connector credentials
- React and TypeScript product development
- Retrieval, structured LLM output, citations, and AI evaluation
- Policy engines and deterministic/AI decision boundaries
- Contract, integration, browser, security, and load testing
- Observability, incident recovery, deployment, and measurable SLOs
- User discovery, product validation, and outcome measurement

The project becomes memorable because it has one strong story rather than many unrelated AI features.

---

## Target Customer and Pain

### Initial ideal customer profile

A 20–200 person SaaS or product-engineering organization that:

- Uses GitHub pull requests and CI
- Uses AI coding assistants or coding agents
- Stores requirements and decisions in GitHub, Jira, Confluence, Markdown, or PDFs
- Has senior engineers spending time reconstructing context during review
- Has recurring problems with missing tests, documentation drift, unlinked requirements, or weak release evidence
- Is too small to build a dedicated internal developer-intelligence platform

The initial user is an engineering manager, tech lead, senior reviewer, release owner, or platform engineer. Developers are secondary users who want to know what proof is missing before requesting review.

### Jobs to be done

1. **Before requesting review:** tell me which required evidence is missing.
2. **During review:** show why the change exists, what it affects, and the sources supporting those conclusions.
3. **Before merge:** evaluate explicit team policies without treating an LLM opinion as a hard gate.
4. **Before release:** create a reproducible record connecting requirements, code, tests, approvals, and rollback evidence.
5. **After an incident:** show which prior changes, decisions, and missing signals may be related.
6. **During onboarding:** let a developer explore how requirements, code, APIs, decisions, tests, and incidents connect.

### Initial product promise

For a selected pull request, Groundwork will produce a cited change record containing:

- Intent and linked requirement
- Changed components and API contracts
- Relevant ADRs, specifications, owners, and runbooks
- Test, build, security, and review evidence
- Deterministic policy results
- AI-assisted risks and impact statements with source citations
- Missing-evidence findings
- Human approvals and final decision trail
- An immutable release snapshot when the change ships

---

## Product Principles

1. **Workflow before chat.** The primary surface is a pull-request change record, not an empty chat box.
2. **Evidence before confidence.** Every important generated claim links to a source or is labelled unsupported.
3. **Deterministic before probabilistic.** File changes, check status, ownership, policy rules, API diffs, and explicit links are calculated without an LLM.
4. **AI advises; policy and humans decide.** An LLM result alone must never block a merge or approve a release.
5. **Use existing tools.** Groundwork returns value through GitHub checks and source links instead of becoming another destination users must constantly update.
6. **Permissions follow the source.** A user must not discover evidence they could not access in its originating system.
7. **Version everything.** Artifact content, relationships, policies, prompts, models, and evaluations need reproducible versions.
8. **Modular monolith first.** Preserve Spring Boot and PostgreSQL until measured scale creates a reason to split services or introduce an event broker.
9. **Two excellent connectors beat ten shallow connectors.** GitHub is first; Jira and Confluence follow only after the flagship workflow works.
10. **Measured claims only.** Resume, README, and demo claims must point to versioned evidence.

---

## Product Scope

### Version 1 flagship workflows

#### 1. Pull-request evidence check

On pull-request open, synchronize, or ready-for-review events:

1. Verify and persist the webhook delivery.
2. Fetch the pull request, commits, files, reviews, linked issues, check runs, and repository metadata.
3. Identify affected components, owners, API contracts, documentation, and relevant evidence.
4. Run deterministic policies.
5. Retrieve related requirements, decisions, runbooks, incidents, and earlier changes.
6. Generate a structured impact analysis that cites retrieved evidence.
7. Publish a GitHub Check summary and link to the full Groundwork report.
8. Update the report idempotently when the pull request changes.

#### 2. Specification-drift detection

- Detect OpenAPI files and compare base/head versions.
- Identify added, removed, and breaking paths, operations, parameters, and schemas.
- Check for expected changelog, tests, owners, and linked requirement evidence.
- Show deterministic diffs separately from AI explanations.
- Later add route-to-contract verification for Spring Boot repositories as a focused language-specific analyzer.

#### 3. Release evidence record

- Select a release tag, commit range, or group of merged pull requests.
- Freeze the relevant artifact versions and policy results.
- Include tests, scans, approvals, deployment result, rollback plan, and unresolved exceptions.
- Export human-readable HTML/PDF and machine-readable JSON.
- Hash the record and its evidence manifest to detect later modification.

#### 4. Evidence explorer and grounded assistant

- Search requirements, ADRs, code-change metadata, API contracts, tests, incidents, and releases.
- Traverse explicit and inferred relationships.
- Ask questions that return citations and distinguish current from superseded evidence.
- Keep the existing document upload flow as a manual source connector.

### Explicit non-goals for Version 1

- Generating application code
- Autonomous merging or deployment
- Generic multi-agent orchestration
- Full static analysis for every programming language
- Ten or more SaaS connectors
- Replacing GitHub, Jira, Confluence, CI, or an incident platform
- Billing and subscription management
- Kubernetes or microservices added only for resume keywords
- AI-generated pass/fail release decisions

---

## Reuse, Change, and Retire

### Keep and extend

- Workspace RBAC, JWT sessions, audit events, and tenant-scoped repositories
- Durable ingestion and reindex workers
- PostgreSQL, pgvector, and full-text search
- Provider-neutral embedding and chat ports
- Hybrid retrieval, reranking, citations, and insufficient-evidence behavior
- Document versions, comparisons, structured extraction, and D3 graph concepts
- Production configuration validation, CI, Compose, metrics, runbooks, and evaluation scaffolding

### Refactor

- Generalize `SourceDocument` into a versioned evidence-artifact abstraction while retaining document-specific metadata.
- Generalize ingestion jobs into connector/synchronization jobs with a shared durable job lifecycle.
- Replace generic graph entities with typed, versioned relationships and provenance.
- Split the frontend into React feature modules and generated/validated API types.
- Replace “AI reviewer” language with explicit change-analysis and evidence-finding concepts.

### De-emphasize or remove from the primary UX

- A chat-first landing page
- Knowledge-graph visualization without a clear review or impact-analysis purpose
- Generic extraction templates that do not contribute to change evidence
- Billing until validated usage creates a real need
- Claims such as “enterprise-grade” until deployment evidence supports them

---

## Target Architecture

```text
GitHub App / Jira / Confluence / Manual Upload / CI
                         |
                         v
            Webhook + Sync Ingestion Layer
        verify -> deduplicate -> persist -> enqueue
                         |
                         v
          Connector Normalization and Versioning
                         |
           +-------------+--------------+
           |                            |
           v                            v
   PostgreSQL evidence model      Raw payload/object archive
   + FTS + pgvector + edges       with retention controls
           |
           v
  Deterministic analyzers -> Retrieval/graph expansion -> LLM analysis
           |                                      |
           +------------------+-------------------+
                              v
                  Findings + cited evidence
                              |
                              v
                  Policy evaluation engine
                              |
                   +----------+----------+
                   |                     |
                   v                     v
             React web UI          GitHub Check output
                   |
                   v
            Immutable release record
```

### Backend modules

Keep one deployable Spring Boot application, separated into clear modules:

| Module | Responsibility |
|---|---|
| Identity and workspaces | Users, sessions, roles, membership, audit |
| Connections | GitHub/Jira/Confluence installations, scopes, encrypted secrets |
| Event ingestion | Webhooks, sync cursors, deduplication, retries, dead letters |
| Evidence catalog | Artifact versions, source metadata, provenance, retention |
| Evidence graph | Typed relationships, temporal validity, graph traversal |
| Search and retrieval | FTS, vector search, graph-expanded retrieval, reranking |
| Change analysis | Deterministic analyzers, AI analysis, findings, citations |
| Policies | Versioned rules, exceptions, evaluations, approvals |
| Release records | Frozen evidence manifests, hashes, exports |
| Operations | Metrics, traces, health, usage, cost, administrative recovery |

### Core domain model

| Entity | Important fields |
|---|---|
| `connection` | workspace, provider, external installation, scopes, status, encrypted credential reference |
| `sync_cursor` | connection, resource type, cursor, last success, failure state |
| `webhook_delivery` | provider delivery ID, signature status, payload hash, received/processed timestamps |
| `artifact` | workspace, source, external ID, type, canonical URL, lifecycle state |
| `artifact_version` | artifact, source version, checksum, content, metadata, valid-from/to, embedding version |
| `relationship` | from/to artifact version, typed relation, explicit/inferred, confidence, provenance |
| `change_set` | repository, PR/commit range, base/head, state, author, timestamps |
| `evidence_item` | change set, artifact version, evidence type, collection method, immutable digest |
| `finding` | analyzer, severity, category, message, citations, confidence, status |
| `policy` | workspace, version, rule definition, severity, active period |
| `policy_evaluation` | change set, policy version, pass/fail/unknown, evidence, evaluated time |
| `approval` | change/release, actor, role, decision, rationale, timestamp |
| `release_record` | immutable manifest, commit range, evidence digests, decision, exported artifact |

Initial relationship types:

- `IMPLEMENTS`
- `CHANGES`
- `VALIDATES`
- `DOCUMENTS`
- `DEPENDS_ON`
- `OWNED_BY`
- `GOVERNED_BY`
- `SUPERSEDES`
- `DEPLOYED_AS`
- `CAUSED`
- `MITIGATED_BY`
- `APPROVED_BY`

Every inferred relationship must record the model/rule that produced it and remain visually distinguishable from an explicit source link.

### Event-processing design

1. A webhook endpoint verifies the provider signature and rejects oversized or stale requests.
2. The delivery ID is inserted under a unique constraint before work is acknowledged.
3. Raw payload metadata and a digest are retained according to policy.
4. An outbox record schedules normalization after the transaction commits.
5. A durable worker claims the event with a lease, retry policy, and dead-letter state.
6. Normalizers upsert artifacts and versions idempotently.
7. Analysis jobs are keyed by workspace, repository, change set, analyzer version, and head SHA.
8. A newer head SHA supersedes stale queued analyses without corrupting earlier records.
9. GitHub Check updates are retried independently from analysis so an external API failure does not lose results.

Do not introduce Kafka initially. PostgreSQL outbox plus the existing durable-worker approach is sufficient for the first measured workload. Record an ADR and revisit only when queue throughput, independent scaling, or replay requirements justify an event broker.

### API surface

Initial endpoints should include:

- `POST /api/integrations/github/webhook`
- `POST /api/workspaces/{id}/connections/github/installations`
- `GET /api/workspaces/{id}/connections`
- `POST /api/connections/{id}/sync`
- `GET /api/workspaces/{id}/changes`
- `GET /api/changes/{id}`
- `POST /api/changes/{id}/reanalyze`
- `GET /api/changes/{id}/findings`
- `GET /api/changes/{id}/evidence`
- `GET /api/workspaces/{id}/artifacts`
- `GET /api/artifacts/{id}/versions`
- `GET /api/workspaces/{id}/evidence/search`
- `POST /api/workspaces/{id}/policies`
- `POST /api/policies/{id}/versions`
- `POST /api/changes/{id}/exceptions`
- `POST /api/workspaces/{id}/releases`
- `GET /api/releases/{id}`
- `GET /api/releases/{id}/export`

The OpenAPI contract must be generated or validated in CI, and the frontend must use generated or schema-checked types.

---

## Deterministic and AI Analysis Boundary

### Deterministic analyzers

The following must not require an LLM:

- Changed files, line counts, commits, authors, and reviews
- GitHub issue/PR links and explicit references
- CI check state and required-check completion
- CODEOWNERS-based ownership
- OpenAPI structural and breaking-change diff
- Presence of required tests, changelog, rollback plan, or approvals
- Policy evaluation
- Hashes, versions, timestamps, and source URLs
- Relationship traversal over explicit edges

### AI-assisted analyzers

Use an LLM for tasks where language understanding adds value:

- Mapping a change to semantically related requirements or ADRs
- Summarizing business and technical intent
- Suggesting potential blast radius or compatibility risks
- Detecting possible contradictions between requirements and implementation notes
- Explaining deterministic OpenAPI changes in stakeholder-friendly language
- Proposing missing questions for a human reviewer

### Required AI output contract

Every generated finding must contain:

- A typed category
- A concise statement
- Evidence citation IDs
- Confidence calibrated as `LOW`, `MEDIUM`, or `HIGH`
- Analyzer, prompt, model, and retrieval versions
- An explicit `SUPPORTED`, `PARTIALLY_SUPPORTED`, or `UNSUPPORTED` evidence state
- A human-review status

Unsupported findings cannot affect policy pass/fail state. They may be shown as questions, never as facts.

---

## Frontend Direction

Migrate the current Vite/TypeScript interface incrementally to React rather than rewriting it all at once.

Recommended stack:

- React with TypeScript
- Vite
- React Router
- TanStack Query for server state
- A small accessible component layer; avoid a large design-system dependency initially
- D3 only for purposeful relationship and timeline views
- Playwright for browser workflows
- Vitest and Testing Library for component behavior

### Primary screens

1. **Workspace dashboard:** recent changes, evidence gaps, blocked policies, stale connections.
2. **Change queue:** filter by repository, author, risk, readiness, or missing evidence.
3. **Change detail:** intent, impact, files, API changes, findings, citations, policy results, checks, approvals, and event history.
4. **Evidence explorer:** artifact search, version history, provenance, and relationship traversal.
5. **Release record:** included changes, evidence completeness, exceptions, approvals, deployment, and export.
6. **Policies:** rule authoring, test mode, version history, activation, and exception workflow.
7. **Connections:** installation health, scopes, last sync, cursor, failure, and reconnect actions.
8. **Evaluation dashboard:** quality, latency, cost, and regression results for the portfolio/demo environment.

### UX requirements

- Show deterministic facts and AI suggestions with different visual treatment.
- Make every citation open the exact source or stored evidence version.
- Explain `PASS`, `FAIL`, and `UNKNOWN`; never hide missing data behind a score.
- Keep the current document library under “Sources,” not as the main product landing page.
- Support loading, partial-sync, stale-analysis, provider-outage, permission, and retry states.
- Meet keyboard navigation and WCAG 2.1 AA expectations for the flagship flow.

---

## Detailed Delivery Plan

The phases are stage-gated. A phase is complete only when its acceptance evidence exists.

### Phase 0 — Validate the problem and freeze scope

**Duration:** 1 week
**Goal:** Ensure the pivot is solving a painful workflow before a large build.

#### Work

- Interview at least 10 engineering managers, tech leads, reviewers, or platform engineers.
- Ask about the last difficult pull request or release, not whether they “like the idea.”
- Measure time spent reconstructing requirements, risks, ownership, tests, and rollback context.
- Rank the proposed findings by urgency and willingness to change workflow.
- Recruit three design partners willing to connect a non-sensitive or sandbox repository.
- Write a one-page problem brief with chosen persona, top three pains, current workaround, and success metric.
- Record the product pivot as an ADR.
- Freeze Version 1 around GitHub pull-request evidence checks, OpenAPI drift, and release records.

#### Exit criteria

- At least 6 of 10 interviewees rate the review-context/evidence problem at least 4/5.
- At least three agree to a follow-up prototype review.
- One flagship workflow and one primary persona are selected.
- If these criteria fail, narrow or change the problem before implementing more infrastructure.

### Phase 1 — Establish the new product shell

**Duration:** 1–2 weeks
**Goal:** Make the product direction visible without breaking the current application.

#### Backend

- Create module/package boundaries for connections, events, evidence, changes, policies, and releases.
- Add additive migrations for connections, webhook deliveries, artifacts, artifact versions, relationships, and change sets.
- Add feature flags for the new workflows.
- Add an architecture test to prevent adapters from leaking into domain/application modules.

#### Frontend

- Install React and migrate routing, authentication, workspace selection, and layout first.
- Preserve existing document/chat functionality behind routes.
- Add empty Change Queue, Connections, and Change Detail routes using typed mock contracts.
- Establish accessible UI primitives and consistent error/loading states.

#### Documentation

- Update README positioning without deleting the accurate description of existing capabilities.
- Add ADRs for the product pivot, modular monolith, PostgreSQL graph model, and deterministic/AI boundary.

#### Exit criteria

- Existing deterministic backend tests and frontend build still pass.
- Existing upload/chat flow remains usable.
- New routes render and API contracts validate.
- The repository tells one consistent product story.

### Phase 2 — Build the GitHub App thin vertical slice

**Duration:** 2 weeks
**Goal:** Demonstrate a real PR entering Groundwork and returning a native GitHub result.

#### Work

- Create a GitHub App with least-privilege repository permissions.
- Implement installation callback/registration and installation-token generation.
- Verify `X-Hub-Signature-256`, delivery ID, event type, installation, content type, timestamp, and payload limits.
- Persist webhook deliveries idempotently.
- Support `pull_request`, `pull_request_review`, `check_suite`, and relevant `workflow_run`/check events.
- Fetch and normalize repository, PR, commits, files, linked GitHub issues, reviews, and checks.
- Create a queued/in-progress/completed GitHub Check.
- Add connection-health and sync-status UI.
- Create a public sandbox repository and deterministic webhook fixtures.

#### Tests

- Signature validation and tampered-payload tests
- Duplicate and out-of-order delivery tests
- GitHub API contract tests through a controlled mock server
- Installation-token expiry and permission failure tests
- Browser test from seeded delivery to Change Detail page

#### Exit criteria

- Opening or updating a pull request creates exactly one current change analysis for its head SHA.
- Groundwork publishes a Check linking back to the report.
- Duplicate deliveries do not duplicate artifacts or analyses.
- The webhook endpoint acknowledges valid events within the defined latency budget.

### Phase 3 — Build the temporal evidence catalog and graph

**Duration:** 2 weeks
**Goal:** Turn connector data into explainable, versioned evidence rather than an unstructured data dump.

#### Work

- Implement artifact/version repositories with workspace scoping and optimistic concurrency.
- Store explicit source provenance and canonical URLs.
- Implement typed relationships and bounded graph traversal.
- Distinguish current, superseded, deleted, and inaccessible versions.
- Extend chunking/embedding to artifact versions, not only documents.
- Implement sync cursors, reconciliation jobs, tombstones, and partial failure recovery.
- Add graph-expanded retrieval with strict depth and candidate limits.
- Migrate existing documents into the new artifact model without breaking their IDs/API immediately.

#### Tests

- Version lineage and supersession
- Cross-workspace relationship leakage
- Reconciliation after missed/out-of-order events
- Deletion and permission revocation
- Retrieval over current versus superseded versions

#### Exit criteria

- Every result can explain where it came from and which version was used.
- Replaying the same connector history produces the same catalog state.
- Source deletion or access removal stops unauthorized retrieval.
- Graph expansion cannot escape the authorized workspace/source scope.

### Phase 4 — Deliver deterministic change impact and spec drift

**Duration:** 2 weeks
**Goal:** Create useful results before introducing more generative behavior.

#### Work

- Implement a change-analyzer port and versioned analyzer registry.
- Add changed-path classification for backend, frontend, database migration, config, infrastructure, docs, and tests.
- Parse CODEOWNERS and identify affected owners.
- Detect OpenAPI contracts and run structural/breaking-change comparison.
- Link explicit issue references, repository docs, ADR paths, and test/check evidence.
- Define evidence-completeness categories and `PRESENT`, `MISSING`, `STALE`, `UNKNOWN` states.
- Build the Change Detail timeline and evidence matrix.
- Publish concise deterministic findings to GitHub Checks.

#### Exit criteria

- A demo pull request that changes an API produces an exact structural diff.
- Missing tests, linked intent, owners, changelog, or rollback evidence are shown as individual explainable findings.
- Re-running an unchanged analyzer version is deterministic.
- No LLM is required for this phase to produce user value.

### Phase 5 — Add grounded AI change analysis

**Duration:** 1–2 weeks
**Goal:** Add semantic value without weakening trust.

#### Work

- Retrieve related requirements, ADRs, earlier changes, runbooks, and incidents using hybrid plus bounded graph retrieval.
- Add structured generation schemas for intent summary, potential impact, conflicts, and reviewer questions.
- Require citation IDs in every generated finding.
- Validate citations against retrieved evidence before persistence.
- Mark unsupported or invalid output and exclude it from policy decisions.
- Add prompt-injection fixtures from code comments, issues, documents, and webhook text.
- Track prompt/model/retriever/analyzer versions, token usage, latency, and cost.
- Add human feedback: confirm, dismiss, edit, or mark useful, with reason codes.

#### Exit criteria

- The same stored evidence can reproduce or explain an analysis configuration.
- Unsupported statements never appear as verified facts.
- Prompt-injection test content cannot override analysis instructions or request unrelated tool use.
- AI provider failure leaves deterministic analysis usable.

### Phase 6 — Implement policies, exceptions, and release records

**Duration:** 2 weeks
**Goal:** Convert findings into a controlled, auditable workflow.

#### Work

- Create a small versioned JSON/YAML policy DSL with a validated schema.
- Support rules over deterministic evidence only in Version 1.
- Initial rules:
  - Require linked issue/requirement.
  - Require successful configured checks.
  - Require owner approval for owned paths.
  - Require tests when source paths change.
  - Require API changelog/compatibility evidence for breaking OpenAPI changes.
  - Require rollback plan for database migration or configured high-risk paths.
- Provide dry-run mode before policy activation.
- Add time-bounded, reasoned policy exceptions with authorized approvers.
- Generate immutable release manifests and digests.
- Export JSON and a readable HTML/PDF evidence record.
- Publish pass/fail/neutral GitHub Checks based on deterministic policies; use `neutral`/`action_required` when data is incomplete according to team configuration.

#### Exit criteria

- Policy outcomes identify the exact rule version and evidence used.
- Historical results do not change when a policy is edited.
- Exceptions require role authorization, rationale, and audit history.
- A release record detects post-freeze evidence modification.

### Phase 7 — Add Jira and Confluence as one coherent integration

**Duration:** 2 weeks
**Goal:** Connect product intent and engineering decisions to code changes.

#### Work

- Implement OAuth installation, minimal scopes, token encryption, refresh, revocation, and reconnect flows.
- Synchronize selected Jira projects and Confluence spaces rather than entire organizations by default.
- Preserve source permissions and canonical URLs.
- Ingest Jira work items, descriptions, acceptance criteria, links, status, and versions.
- Ingest Confluence pages/versions for specifications, ADRs, and runbooks.
- Resolve explicit Jira keys in branches, commits, and pull requests.
- Add semantic suggestions for unlinked but potentially related requirements.
- Never auto-link a semantic suggestion as confirmed without user acceptance.

#### Exit criteria

- A PR linked to Jira shows requirement and acceptance-criteria evidence.
- A relevant Confluence ADR appears with source permissions and version.
- Revoking the connection stops refresh and removes unauthorized search access.
- Connector failures are visible and recoverable without corrupting existing evidence.

### Phase 8 — Complete product UX and accessibility

**Duration:** 1–2 weeks, with work beginning earlier
**Goal:** Make the system understandable to a reviewer in under five minutes.

#### Work

- Finish React migration and remove obsolete monolithic UI code.
- Implement dashboard, change queue, detail, explorer, release, policy, and connection flows.
- Add evidence preview, exact source/version links, and relationship explanations.
- Add responsive layouts, keyboard navigation, focus management, accessible status text, and contrast checks.
- Add visual regression snapshots for the principal demo states.
- Add an opinionated seeded demo workspace with realistic requirements, ADRs, PRs, tests, an API change, and an incident.

#### Exit criteria

- A first-time reviewer can identify intent, impact, missing evidence, and policy status without training.
- Core workflows pass Playwright on desktop and a common mobile viewport.
- Automated accessibility checks pass, followed by a manual keyboard review.
- Empty, loading, stale, partial, denied, error, and recovery states are handled.

### Phase 9 — Prove security, reliability, quality, and performance

**Duration:** 2 weeks initially, then continuous
**Goal:** Replace “production-ready” claims with reproducible evidence.

#### Security

- Threat-model connector impersonation, webhook replay, token theft, tenant leakage, prompt injection, source-permission drift, malicious diffs, and export leakage.
- Encrypt connector credentials with key versioning and rotation procedure.
- Avoid retaining installation tokens; generate short-lived tokens as needed.
- Add authorization tests for every artifact, relationship, finding, policy, change, and release API.
- Run dependency, container, secret, and static scans in CI.
- Obtain an independent review or structured peer security assessment.

#### Reliability and operations

- Add OpenTelemetry traces across webhook, worker, database, provider, and GitHub API calls.
- Measure queue age, event failures, sync lag, analysis latency, external rate limits, token usage, cost, and Check publication failures.
- Add circuit breakers/timeouts only where failure testing justifies them.
- Test provider, Redis, GitHub API, and database interruption behavior.
- Exercise backup, restore, migration, rollback, and abandoned-job recovery.

#### Quality evaluation

- Build a versioned benchmark repository with at least 30 representative change scenarios.
- Include exact links, missing links, API breakage, conflicting ADRs, stale documents, missing tests, prompt injection, no-evidence cases, and permission restrictions.
- Measure deterministic correctness separately from AI quality.
- Add human-labelled expected evidence and finding categories.

#### Initial release thresholds

These are initial targets and must be revised after a measured baseline:

| Measure | Initial target |
|---|---:|
| Deterministic policy correctness | 100% on versioned fixtures |
| Webhook deduplication correctness | 100% on replay/out-of-order suite |
| Relevant-evidence recall@10 | >= 85% |
| Citation correctness | >= 95% |
| Unsupported generated claims | <= 2% in reviewed benchmark |
| Correct no-evidence behavior | >= 95% |
| Webhook acknowledgement p95 | < 500 ms |
| Change analysis completion p95 | < 90 s excluding declared provider outage |
| Normal read API p95 | < 500 ms under target load |
| Cross-workspace leakage | 0 in automated/adversarial tests |
| Normal-load successful requests | >= 99.5% during versioned load test |

#### Exit criteria

- Evaluation, load, restore, and security reports are committed with environment metadata.
- Dashboards and alerts map to user-visible failure modes.
- Known external outages degrade predictably.
- No critical/high unresolved security finding remains.

### Phase 10 — Deploy, pilot, measure, and package the portfolio story

**Duration:** 2 weeks to launch; 4–6 weeks of observation
**Goal:** Prove that the project creates user value and that the engineer can operate it.

#### Deployment

- Deploy a staging and production/demo environment with TLS, managed secrets, PostgreSQL backups, and object retention controls.
- Use immutable images, non-root containers, health/readiness checks, and controlled migrations.
- Protect the public demo with seeded data, quotas, provider budgets, and safe reset automation.

#### Pilot

- Onboard three design partners with a sandbox or non-sensitive repository.
- Baseline time spent gathering review context before using Groundwork.
- Track activated connections, analyzed PRs, repeat weekly reviewers, report opens from GitHub, accepted/dismissed findings, and policy exceptions.
- Conduct a weekly qualitative review and record product changes driven by evidence.

#### Portfolio package

- A 90-second overview video and a 5-minute technical walkthrough
- One architecture diagram and three high-value sequence diagrams
- Public ADRs explaining trade-offs, especially why the system remains a modular monolith
- A threat model and security-review summary
- Versioned AI evaluation and load-test reports
- A case study showing the before/after review workflow
- A public demo repository where a pull request triggers Groundwork
- A concise README with verified metrics
- A clean issue/PR/commit history showing incremental engineering decisions

#### Product outcome targets

- At least three teams complete onboarding.
- At least two teams use Groundwork weekly for four consecutive weeks.
- Median context-gathering time decreases by at least 30% in the pilot workflow.
- At least 60% of opened Groundwork reports lead to a useful reviewer action, confirmed finding, resolved evidence gap, or explicit dismissal reason.
- At least one design partner asks to continue using the product after the pilot.

If repeat usage does not occur, do not add more AI features. Review workflow friction, signal quality, and target persona first.

---

## Recommended First 32 Engineering Issues

1. Record the evidence-layer product pivot as an ADR.
2. Define the Version 1 persona, workflow, and outcome metric.
3. Add React while preserving the current Vite application behavior.
4. Introduce route-level feature modules and typed API contracts.
5. Add connection and encrypted-credential schemas.
6. Add webhook-delivery, outbox, sync-cursor, and dead-letter schemas.
7. Implement GitHub webhook signature verification and payload limits.
8. Implement idempotent delivery persistence and replay tests.
9. Implement GitHub App installation-token generation.
10. Build GitHub API adapter contract tests with a mock server.
11. Normalize repository, issue, pull request, commit, review, and check data.
12. Publish queued/in-progress/completed GitHub Checks.
13. Add artifact, artifact-version, and provenance schemas.
14. Add typed, temporal relationship schemas.
15. Migrate existing documents into evidence artifacts.
16. Extend hybrid retrieval to all supported artifact versions.
17. Implement change-set and analysis-job lifecycle.
18. Implement changed-path classification.
19. Parse CODEOWNERS and affected ownership.
20. Implement OpenAPI structural and breaking-change analysis.
21. Implement evidence presence/staleness states.
22. Build the Change Queue and Change Detail UI.
23. Add structured, cited AI change-analysis output.
24. Add prompt-injection and unsupported-citation validation.
25. Implement human finding feedback and reason codes.
26. Define and validate the policy DSL.
27. Add deterministic policy evaluation and dry-run mode.
28. Add authorized, expiring exceptions and approvals.
29. Generate immutable release manifests and exports.
30. Add Jira/Confluence OAuth and selected-scope sync.
31. Build the benchmark repository and evaluation harness.
32. Deploy the seeded demo, publish reports, and start pilots.

Each issue should include user value, data/API changes, security considerations, tests, observability, acceptance criteria, and documentation updates.

---

## Test Strategy

### Unit and architecture tests

- Domain state transitions and invariants
- Policy rule parsing/evaluation
- Evidence state and temporal-version logic
- OpenAPI diff classification
- CODEOWNERS matching
- RRF and graph-expansion limits
- Package/module dependency rules

### Integration tests

- PostgreSQL/pgvector migrations and queries
- Outbox claiming, leases, retries, dead letters, and recovery
- Tenant filtering across every repository
- Connector credential encryption/rotation
- Artifact versioning and relationship traversal
- Release snapshot immutability

### Contract tests

- GitHub/Jira/Confluence controlled mock servers
- OAuth expiry, revocation, rate limit, timeout, and malformed response handling
- AI/embedding/reranker adapters
- OpenAPI request/response compatibility

### End-to-end tests

1. Install/connect a seeded GitHub App fixture.
2. Open a pull request.
3. Receive and deduplicate the webhook.
4. Analyze changed code and OpenAPI.
5. Display intent, evidence, findings, and policies.
6. Publish the GitHub Check.
7. Push a new commit and supersede the stale analysis.
8. Add missing evidence and observe the updated result.
9. Merge and include the change in a release record.
10. Verify workspace and source-permission isolation throughout.

### Adversarial and failure tests

- Forged/replayed/out-of-order webhooks
- Cross-workspace identifier guessing
- Prompt injection in issue, code comment, documentation, and diff content
- Source access revoked after indexing
- Provider outage or malformed structured output
- GitHub rate limit and expired installation token
- Redis unavailable
- Worker crash after claim and before completion
- Concurrent analysis for multiple head SHAs
- Export access and tamper detection

---

## Security and Privacy Requirements

- Use least-privilege GitHub App and OAuth scopes.
- Verify webhook signatures with constant-time comparison.
- Deduplicate using provider delivery IDs and payload digests.
- Encrypt persistent connector credentials and record key versions.
- Never log connector tokens, raw private evidence, prompts containing secrets, or document bodies.
- Apply workspace/source authorization inside repository queries, not only controllers.
- Preserve source permission metadata and refresh it.
- Define retention and deletion for raw payloads, artifacts, embeddings, exports, and audit records.
- Treat every external source as untrusted prompt content.
- Restrict AI adapters to analysis only; no arbitrary tools or network actions.
- Require explicit user authorization for all external writes.
- Audit connection changes, policy changes, exceptions, approvals, exports, and administrative recovery.

PostgreSQL row-level security can be considered as defense in depth after repository-level isolation is complete and tested. It should not substitute for application authorization.

---

## Observability and SLOs

### Required metrics

- Webhook accepted, rejected, duplicated, replayed, and processing latency
- Queue depth, oldest job age, retries, dead letters, and lease recovery
- Connector sync lag and permission-sync age
- Artifacts/versions/relationships by type and source
- Analysis duration by analyzer and stage
- Retrieval latency, recall evaluation, cache hit rate, and candidate counts
- AI latency, errors, tokens, cost, and unsupported-output rate
- GitHub Check publication latency/failures
- Policy pass/fail/unknown and exception rate
- Finding confirmation/dismissal/usefulness
- Release export generation and verification
- Authorization denials and rate-limit events

### Operational objectives

- No accepted webhook is lost.
- External-provider failure does not corrupt existing evidence.
- A failed analysis can be safely replayed.
- A stale analysis is never presented as current without a warning.
- Every user-visible error includes a correlation ID and recovery action.
- Backup restoration and job recovery are rehearsed, not assumed.

---

## Validation and Analytics Plan

### Events to collect

- `connection_activated`
- `first_change_analyzed`
- `github_check_opened`
- `change_report_viewed`
- `evidence_source_opened`
- `finding_confirmed`
- `finding_dismissed`
- `evidence_gap_resolved`
- `policy_exception_requested`
- `release_record_created`
- `weekly_active_reviewer`

Analytics must avoid storing private evidence content. Use identifiers, categories, durations, and reason codes.

### Interview questions

- Tell me about the last change that was difficult to review or release.
- Where did you look for its requirement, design decision, owners, test proof, and rollback plan?
- What was missing, and who did you need to interrupt?
- Which mistakes have reached production because context or evidence was incomplete?
- What would make a PR check helpful rather than noisy?
- Which result could be advisory, and which must be deterministic?
- Would your team connect a repository to test this? What security concern would block that?

Avoid asking whether the participant “would use an AI product.” Observe actual workflow and commitments.

---

## 10/10 Scorecard

| Area | Weight | Required proof |
|---|---:|---|
| Real user problem and validation | 15% | Interviews, three pilots, repeated use, measured time reduction |
| Flagship workflow usefulness | 15% | PR-to-Check-to-release flow used on realistic repositories |
| Correctness and evidence quality | 15% | Versioned deterministic and AI evaluation reports |
| Architecture and maintainability | 10% | Modular boundaries, ADRs, clean migrations, controlled dependencies |
| Security and tenant isolation | 10% | Threat model, adversarial tests, token handling, independent review |
| Reliability and recovery | 10% | Durable events/jobs, outage tests, restore and replay drills |
| Automated testing | 10% | Unit, architecture, integration, contract, E2E, accessibility, load gates |
| UX and accessibility | 5% | First-time task success, browser tests, WCAG review |
| Deployment and observability | 5% | Live environment, SLOs, dashboards, alerts, runbooks |
| Portfolio communication | 5% | Demo, case study, measured resume bullets, clean history |

Groundwork reaches a 10/10 project standard only when the proof column is satisfied. Feature completion alone is not sufficient.

---

## Decision Gates and Scope Control

### Gate 1: Problem validation

Do not proceed beyond the GitHub thin slice without interview evidence that review context/evidence is painful.

### Gate 2: Workflow validation

Do not build Jira/Confluence connectors until users can obtain value from a GitHub-only change record.

### Gate 3: Signal quality

Do not add more AI analyzers if reviewers dismiss findings as noisy. Improve evidence quality and precision first.

### Gate 4: Operational maturity

Do not call the product production-ready until live-provider evaluation, E2E, load, restore, and security evidence exists.

### Gate 5: Scale architecture

Do not add Kafka, Kubernetes, Neo4j, or microservices without measured constraints that PostgreSQL, the modular monolith, and current workers cannot meet.

---

## Primary Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Product becomes another noisy PR bot | Deterministic evidence first, user feedback, precision thresholds, configurable policies |
| Scope expands across the entire SDLC | One persona, one PR workflow, two high-quality source integrations |
| AI invents impact or requirements | Citation validation, evidence states, no AI-only gates, human confirmation |
| Connector permissions leak information | Least privilege, source ACL sync, revocation tests, tenant filters |
| OAuth and webhooks consume the schedule | GitHub first, mock fixtures, thin vertical slice, defer secondary connectors |
| React migration breaks existing value | Route-by-route migration with existing workflow regression tests |
| No real users participate | Recruit design partners in Phase 0; stop and revisit positioning if commitments fail |
| Project looks generated or overengineered | Incremental issues/PRs, ADRs, measured trade-offs, live debugging/demo capability |
| Infrastructure keywords drive bad choices | ADR-required additions based on measured needs, not resume fashion |

---

## Portfolio and Resume Presentation

### Recommended headline

> Built Groundwork, an evidence and release-readiness platform that connects requirements, pull requests, API changes, tests, decisions, and approvals into cited, policy-checked change records.

### Resume bullets after measurement

Use the following structure, replacing placeholders only with measured values:

- Built a multi-tenant Java 21/Spring Boot and React platform that processed **N** GitHub change events with idempotent webhooks, durable jobs, and **X ms** p95 acknowledgement latency.
- Designed hybrid PostgreSQL FTS/pgvector plus evidence-graph retrieval, achieving **X%** recall@10 and **Y%** citation correctness on a versioned change-impact benchmark.
- Implemented deterministic OpenAPI/policy checks and grounded AI analysis, reducing pilot review-context gathering time by **X%** across **N** repositories.
- Secured GitHub/Jira connections with least-privilege OAuth, encrypted credentials, tenant-scoped queries, and adversarial isolation/replay tests.
- Operated a deployed system with OpenTelemetry, SLO dashboards, load tests, backup/restore drills, and documented incident runbooks.

Never use placeholder or target values on the actual resume.

### Five-minute demo story

1. Open a pull request that changes an API.
2. Show the Groundwork GitHub Check appear.
3. Open the report and show linked requirement, changed contract, owners, tests, and missing rollback/changelog evidence.
4. Open exact citations and distinguish deterministic findings from AI suggestions.
5. Add the missing evidence, push a commit, and show the updated policy result.
6. Merge the PR and produce a tamper-evident release record.
7. Briefly show evaluation, traces, and architecture decisions.

This demo proves product value, full-stack integration, AI responsibility, security, and operational depth in one coherent flow.

---

## Final Definition of Done

Groundwork is a 10/10 portfolio project when all of the following are true:

- The product has a precise target user and a validated painful workflow.
- A live GitHub pull request triggers an idempotent analysis and native Check.
- Intent, code, APIs, tests, decisions, approvals, and releases are represented as versioned evidence.
- Deterministic policies and AI suggestions are technically and visually separated.
- Generated claims are cited, evaluated, and safe under insufficient evidence.
- At least GitHub and one requirement/decision source work end to end with permission handling.
- The React UI makes the flagship workflow clear and accessible.
- Unit, integration, contract, browser, adversarial, and load tests protect the system.
- Webhook replay, provider outage, worker crash, restore, and connector revocation have been exercised.
- Quality, latency, reliability, security, and user-outcome claims have versioned evidence.
- The project has a live demo, a realistic seeded repository, a case study, a short video, and an architecture narrative the author can defend.
- At least two pilot teams use it repeatedly and one wants to continue.

Until those conditions exist, describe Groundwork as a strong production-oriented portfolio platform, not a proven enterprise product.

---

## Implementation Update — 2026-08-24

All repository-controlled phases in this plan have been implemented end to end. This includes the product
shell, temporal evidence catalog, connector lifecycle, durable webhook/outbox processing, GitHub and Atlassian
boundaries, deterministic and grounded analysis, policy/exception/approval workflows, release records/exports,
responsive React experience, benchmark/integration/architecture tests, dependency/security automation,
metrics/tracing, performance scenario, deployment container, demo seed, and operating documentation.

The following gates remain deliberately open because completing them requires real people, credentials, or
infrastructure and cannot be truthfully manufactured in a source repository:

- customer interviews, design-partner commitments, repeated pilot use, and measured user outcomes;
- live GitHub App and Atlassian OAuth authorization in accounts owned by the operator;
- a public/staging deployment, target-environment SLO/load results, and a completed backup/restore drill;
- independent penetration/tenant-isolation review and remediation evidence;
- a recorded demo/case study using approved non-sensitive data.

The exact implementation mapping is maintained in `docs/IMPLEMENTATION_REPORT.md`; the protocol for closing
the external gates is in `docs/PRODUCT_VALIDATION.md`. Therefore the accurate current state is **software
implementation complete, external product and operational proof pending**.
