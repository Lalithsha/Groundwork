# Groundwork implementation handbook

This handbook reflects the implemented system. It intentionally avoids unmeasured latency, quality, scale, or security claims.

## Product workflow

1. A user registers or signs in and receives a short-lived JWT plus rotating refresh token.
2. The user creates/selects a workspace. Membership roles enforce viewer, editor, admin, and owner operations.
3. A PDF, Markdown, or text document is validated, extracted, deduplicated by content hash, and stored as a first-class source document.
4. A durable ingestion job chunks and embeds the document. Workers claim jobs using `FOR UPDATE SKIP LOCKED`, retry transient failures, and recover expired leases.
5. Chat embeds the question, combines pgvector and PostgreSQL full-text ranks with RRF, optionally reranks, and builds a prompt that treats retrieved text as untrusted evidence.
6. Answers include stable citation identifiers and an explicit evidence status. Streaming emits typed lifecycle, source, token, completion, and error events.

## Design rules

- Application services depend on chat and embedding ports, not a provider SDK.
- PostgreSQL is the source of truth; Redis is an optional performance layer.
- Workspace identifiers are explicit in repository queries and authorization checks.
- Background work is idempotent at the source-document/chunk replacement boundary.
- Production configuration fails closed on unsafe secrets, authentication, CORS, embeddings, and billing.
- Dynamic frontend output is escaped; provider and document text is never trusted as markup.
- Claims require generated evidence. The evaluation directory contains a runner, dataset version, and raw report format.

## API summary

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `/login`, `/refresh`, `/logout` |
| Workspaces | `GET/POST /api/workspaces` |
| Memberships | `GET/PUT/DELETE /api/workspaces/{workspaceId}/members` |
| Documents | `GET /api/documents`, `POST /api/documents/upload`, job status/cancel, delete by title |
| Chat | `POST /api/chat`, `GET /api/chat/stream` |
| Reindex | `POST /api/admin/reindex`, status/cancel by job ID |
| Intelligence | `/api/artifacts`, `/api/compare`, `/api/review`, `/api/graph` |

Requests that operate on tenant data require `workspaceId`. The web client sends `X-Request-ID`; the backend returns it and includes it in structured errors and audit events.

## Persistence

Flyway owns schema evolution. `V1` establishes users, tokens, subscriptions, chunks, and reindex jobs. `V2` adds workspaces and intelligence entities. `V3` adds first-class source documents, chunk provenance, durable ingestion fields, workspace memberships, reindex leases, audit events, and indexes while preserving legacy chunks.

Raw extracted source text is retained in PostgreSQL to support deterministic reindexing. Deployments with stricter retention requirements should add encryption/retention policy before accepting sensitive documents.

## Verification status

Deterministic backend unit/controller tests and the frontend production build run locally and in CI. CI also runs the opt-in database integration test against PostgreSQL with pgvector. Provider quality, load capacity, disaster recovery, and browser end-to-end flows require environment-specific execution; the runbook lists those release gates.

Related documents: [Architecture](docs/ARCHITECTURE.md), [Security](docs/SECURITY.md), [Runbook](docs/RUNBOOK.md), [Assessment](PROJECT_ASSESSMENT.md), and [Roadmap](ROADMAP_TO_10.md).
