# Operations runbook

## Release gates

1. `./scripts/verify.sh` passes on Java 21 and Node 22.
2. `RUN_DATABASE_INTEGRATION_TESTS=true ./mvnw -Dtest=DatabaseIntegrationTest test` passes against PostgreSQL 16/pgvector and Redis 7.
3. The 30-scenario deterministic benchmark passes exactly.
4. `docker compose config` and `docker build .` pass with reviewed configuration.
5. Flyway migrations are tested on a restored production-like database.
6. Auth, tenant isolation, GitHub replay, connector sync failure, revocation, policy exception, release verification, and exports are smoke-tested.
7. Target-environment load, backup/restore, and provider-outage reports are attached to the release.
8. High/critical dependency, CodeQL, Trivy, and secret-scan findings are resolved or explicitly accepted.

## Deploy

Back up PostgreSQL, record the current image/commit, update secrets, run `docker compose build`, and then `docker compose up -d`. Inspect Compose status, application logs, `/actuator/health`, `/actuator/prometheus`, and the frontend `/healthz`. Only TLS ingress/frontend should be public; PostgreSQL, Redis, OTLP, and backend management endpoints stay private.

## Signals and objectives

Prometheus exposes HTTP/JVM/datasource/process metrics plus integration queue depth, event results/latency, connector sync outcomes, change-analysis outcomes, and product events. OTLP exports sampled traces when `TRACING_ENABLED=true` and a collector endpoint is configured. Correlate failures using `X-Request-ID`, trace ID, webhook delivery ID, connector sync run, change ID, or analysis job ID.

Initial objectives (targets until measured in the real environment):

- webhook acknowledgment p95 under 500 ms and success above 99.9%;
- change detail/search p95 under 1 s at expected pilot load;
- no durable job older than five minutes without an alert;
- connector sync and analysis failure alerts on sustained error rate;
- zero cross-tenant authorization failures and zero invalid release digest verifications.

Run `k6 run -e WORKSPACE_ID=<uuid> -e ACCESS_TOKEN=<jwt> performance/k6-smoke.js`. Record commit, data volume, host resources, VUs, duration, and raw output. Never present configured thresholds as measured results.

## Durable worker recovery

Integration, ingestion, reindex, and analysis workers claim with `FOR UPDATE SKIP LOCKED`, record an owner/time, retry within configured limits, and recover stale leases. Inspect the relevant job/outbox table before intervening. Fix the cause, then use the supported reanalysis/sync endpoint; do not hand-edit state unless following an incident-approved SQL procedure with a backup.

Webhook deliveries are immutable inbox records. A duplicate delivery with the same digest is accepted idempotently; a conflicting digest is rejected. Failed outbox events retain the error/attempt count for diagnosis.

## Connector failure and revocation

A failed provider fetch creates a durable failed sync run and degrades the connection. Item-level failures produce a partial run and prevent destructive reconciliation. After permissions/credentials recover, run the sync endpoint and verify counts. Revocation deletes ciphertext and marks source artifacts inaccessible; reconnect as a new authorization rather than restoring old ciphertext.

## Provider/model outage

Existing deterministic analysis and stored retrieval remain usable. Remote embeddings/chat may fail and durable jobs retry with bounds. AI analysis must downgrade/fallback with no fabricated claims; do not silently switch production embeddings to the local deterministic adapter. Verify connector/provider status and queued work after recovery.

## Backup and restore drill

Back up the entire PostgreSQL cluster/database including Flyway history, evidence versions, outbox, audit events, release records, and vector data. Redis is rebuildable. Restore into an isolated environment and verify:

- Flyway checksum/state and row counts by workspace;
- active connector rows and credential key versions (without printing ciphertext);
- one known evidence search and graph traversal;
- one webhook replay remains idempotent;
- one completed change retains findings/policies;
- release digest verification succeeds;
- workers can claim newly queued work.

Document recovery time and recovery point. A backup is not proven until this drill succeeds.

## Rollback

Application rollback is safe only when the prior image tolerates all applied additive migrations. Never delete Flyway history or reverse a migration in place. If data transformation is corrupt, stop writes and restore the pre-deploy snapshot. Verify releases and connector state after rollback.

## Credential rotation

Follow the connector key procedure in [SECURITY.md](SECURITY.md). Rotate JWT, GitHub webhook, GitHub App, Atlassian OAuth, database, and provider credentials through the deployment secret manager. JWT key rotation currently invalidates active access sessions; plan the user impact.

## Incident checklist

1. Declare severity/owner and stop further harmful writes if necessary.
2. Capture time, commit/image, health, logs, metrics, traces, and relevant durable IDs.
3. Revoke compromised connectors/tokens and rotate secrets.
4. Restore service using retry/replay/rollback procedures.
5. Verify tenant boundaries, evidence accessibility, and release digests.
6. Write an incident artifact and link follow-up changes so Groundwork can surface the learning.
