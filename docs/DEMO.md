# Groundwork demo guide

## Goal

Show the complete product story in five minutes: an API-changing pull request is connected to its reason, decision, owner, tests, prior incident, and release requirements; Groundwork separates known, missing, and unknown evidence and creates a verifiable release record.

## Deterministic local setup

1. Start PostgreSQL/pgvector and Redis with `docker compose -f compose.dev.yml up -d`.
2. Run the Spring Boot API with local embeddings and security enabled.
3. Run the React frontend with `npm run dev`.
4. Register a local account and create a workspace.
5. Select **Load demo evidence** once.

The seed is idempotent enough for a fresh demo workspace and does not need GitHub, Atlassian, or model credentials. It creates:

- Jira requirement `PROJ-42`: replace the legacy customer lookup;
- ADR-019 describing the migration decision;
- a prior incident establishing timeout/rollback relevance;
- a GitHub pull request changing backend code, a migration, and OpenAPI;
- check/review/CODEOWNERS facts and explicit cross-source relationships.

## Five-minute narrative

1. **Dashboard (30 seconds):** explain that Groundwork measures evidence completeness, not code quality by model opinion.
2. **Change record (90 seconds):** open PR #42 and show intent, changed paths, check evidence, owner approval, rollback, API compatibility, and changelog findings. Point out present/missing/unknown labels.
3. **Evidence graph (60 seconds):** follow citations from the PR to PROJ-42, ADR-019, and the incident. Search for “rollback” to demonstrate hybrid retrieval.
4. **Policy workflow (45 seconds):** preview a policy, explain advisory versus enforced rules, record finding feedback, and show that an exception needs reason/expiry/actor.
5. **Release record (45 seconds):** freeze the change, export JSON/HTML/PDF, and verify digests. Explain how later evidence mutation is detected.
6. **Engineering proof (30 seconds):** show the 30-scenario benchmark, database integration test, architecture ADRs, metrics/traces, and threat model.

## Live integration variant

With reviewed credentials, install the GitHub App on a demo repository, configure the signed webhook, and connect selected Jira projects/Confluence spaces through OAuth. Open or synchronize a real PR and wait for the native check. Never expose secrets, raw OAuth responses, customer content, or private repository URLs in a recorded demo.

## Reset

Use a dedicated disposable workspace for each recorded run. Delete it through the authenticated workspace API/UI only after verifying that it contains no evidence needed for evaluation. For a fully disposable local environment, stop Compose and remove only the project-specific volumes after confirming their resolved names.

## Claims to avoid

Do not say “production proven,” “enterprise secure,” “X% faster,” or “used by teams” until the corresponding pilot, independent review, deployment, and measurement artifacts exist. The accurate current claim is: **a production-oriented, end-to-end evidence and release-readiness platform with deterministic local proof and explicit external validation gates.**
