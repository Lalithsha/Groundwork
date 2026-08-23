# Groundwork product evolution implementation report

Date: 2026-08-24

This report maps the detailed [10/10 plan](../GROUNDWORK_10_OUT_OF_10_PLAN.md) to repository evidence. “Implemented” means code/config/tests/docs exist locally; it does not imply a deployed production service or validated customer outcome.

## Direction chosen

Groundwork moved from a document-focused RAG assistant to an engineering evidence and release-readiness platform. PDF/Markdown/text intelligence remains useful, but now feeds a wider temporal evidence catalog alongside GitHub, Jira, Confluence, OpenAPI, checks, ownership, incidents, approvals, and releases.

## Phase status

| Phase | Status | Delivered evidence |
|---|---|---|
| 0 — validation/scope | Software complete; external validation pending | product hypothesis, ICP, interview protocol, pilot scorecard, scope/non-goals, product events |
| 1 — product shell | Implemented | React app, workspace context, dashboard/navigation, capabilities endpoint, ADRs, modular boundaries |
| 2 — GitHub thin slice | Implemented with live-credential gate | GitHub App auth/adapter, signed idempotent webhook inbox/outbox, PR normalization, analysis queue, check publication port, demo webhook |
| 3 — temporal evidence | Implemented | artifact identities/versions/digests/relationships, connector lifecycle, sync cursors/runs, reconciliation, search, legacy document bridge |
| 4 — deterministic impact | Implemented | intent/scope/test/check/CODEOWNERS/migration/OpenAPI/changelog analyzers, present/missing/unknown state, 30-scenario benchmark |
| 5 — grounded AI | Implemented with provider-quality gate | hybrid FTS/pgvector retrieval, cited evidence context, structured output validation, deterministic fallback, explicit AI/deterministic separation |
| 6 — policy/releases | Implemented | versioned rules, activation, dry run, evaluations, feedback, time-bounded exceptions, approvals, canonical release digests and JSON/HTML/PDF exports |
| 7 — Jira/Confluence | Implemented with live-OAuth gate | OAuth state/code exchange/refresh boundary, encrypted tokens, selected resource sync, manual connector evidence, sync runs and revocation |
| 8 — UX/accessibility | Implemented and locally reviewed | typed React Query client, responsive desktop/mobile UI, semantic forms/headings, focus states, skip link, reduced motion, loading/empty/error states |
| 9 — quality/operations | Implemented locally; environment drills pending | unit/architecture/database integration tests, dependency audit, secret scan, CodeQL/Trivy workflows, Prometheus, OTLP tracing, k6 scenario, threat/runbook docs |
| 10 — deploy/pilot/story | Deployment assets/docs implemented; external execution pending | non-root multi-stage container, Compose health checks, fail-closed production config, demo seed/guide, implementation narrative, validation gates |

## Key design decisions

- Modular monolith and PostgreSQL outbox before adding distributed infrastructure.
- PostgreSQL/pgvector is authoritative; Redis is an optimization.
- Artifact identity is stable, content versions are immutable, and relationships attach to identities.
- Deterministic findings are release-policy facts; AI is a cited suggestion boundary.
- Unknown evidence is distinct from missing evidence.
- Reconciliation only tombstones after a fully successful provider scan.
- Connector secrets are AES-GCM encrypted with authenticated context and explicit key versions.
- Release exports recalculate canonical evidence digests to detect mutation.

See ADRs 0001–0003 for rationale and consequences.

## Verification performed

- Frontend TypeScript/Vite production build passed on the upgraded, zero-advisory npm dependency tree.
- Backend unit and ArchUnit suite passed, including 30 versioned benchmark scenarios.
- Real PostgreSQL 16/pgvector integration tests passed for document ingest/vector retrieval and seeded PR → evidence graph → findings → policies.
- Desktop and 390×844 mobile authentication/product shell layouts were visually reviewed in a real Chromium session.

Run commands and current totals can change as the repository evolves; CI is the canonical repeatable check.

## Remaining non-code proof

The repository intentionally makes no false claim for user interviews, live connector authorization, deployed availability, independent security review, target-environment SLOs/load, restore drills, or pilot outcomes. Complete those gates using [PRODUCT_VALIDATION.md](PRODUCT_VALIDATION.md) and [RUNBOOK.md](RUNBOOK.md) before calling the product 10/10 in the market or using outcome metrics on a resume.
