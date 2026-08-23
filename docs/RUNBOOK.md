# Operations runbook

## Release gates

1. `./scripts/verify.sh` passes on Java 21 and Node 20.
2. CI database integration test passes against PostgreSQL 16 + pgvector.
3. `docker compose config` succeeds with the production `.env`.
4. Flyway migrations are tested on a restored copy of production data.
5. Auth, workspace isolation, upload, job retry, grounded chat, citations, and logout are smoke-tested.
6. Evaluation and load reports are generated for the exact model/configuration being released.

Run the load scenario with `k6 run -e WORKSPACE_ID=<uuid> -e ACCESS_TOKEN=<jwt> performance/k6-smoke.js`. Tune virtual users and duration only after recording the corpus, model, host resources, and commit SHA.

## Deploy

Back up PostgreSQL, pull the reviewed revision, update secrets, run `docker compose build`, then `docker compose up -d`. Inspect `docker compose ps`, application logs, `/actuator/health`, and the browser `/healthz`. The public reverse proxy should provide HTTPS; only the frontend port needs external exposure.

## Signals

Use `/actuator/prometheus` for standard JVM, HTTP server, datasource, cache, and process metrics. Alert on health failure, HTTP 5xx/error-rate growth, latency saturation, PostgreSQL/Redis unavailability, disk pressure, and a growing count of queued/retrying/failed jobs. Correlate an API error with `X-Request-ID` in logs and `audit_events`.

## Job recovery

Workers automatically recover expired `RUNNING` leases. Inspect `ingestion_jobs` and `reindex_jobs` for attempts, lock owner/time, error, and progress. Queued/retrying jobs can be cancelled through their API endpoint. Re-uploading content whose last ingestion failed creates a new job for the existing source document.

## Backup and restore

Back up the entire PostgreSQL database, including Flyway history and vector data. Redis can be rebuilt but its persistent volume reduces cold-cache impact. Practice restore into an isolated instance, verify Flyway state, source/chunk counts, workspace memberships, and a known retrieval query before declaring recovery successful.

## Rollback

Application rollback is safe only when the prior version tolerates the additive schema. Do not reverse Flyway by deleting schema-history rows. Restore the pre-deploy database snapshot if a migration corrupts data. Because `V3` is additive, its new tables/columns can remain during an application rollback, but this must be tested for the target revision.

## Provider outage

New remote embeddings/chat may fail and durable jobs will retry with bounded backoff. Existing retrieval remains in PostgreSQL, but generation depends on the chat adapter; the chat contract returns a grounded fallback/explicit error rather than fabricated provider output. Never silently switch production to deterministic local embeddings because they are not semantic-quality vectors.
