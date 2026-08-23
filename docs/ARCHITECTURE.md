# Architecture

## Boundaries

- `adapter/in/web`: HTTP translation, validation, and status codes.
- `application`: use cases, retrieval policy, chunking, job orchestration, and tenant authorization.
- `application/port/out`: provider contracts for chat and embeddings.
- `adapter/out/ai`: deterministic and OpenAI-compatible provider adapters.
- `domain/model`: immutable transport/domain records.
- Flyway migrations: persistence contract and additive upgrades.

## Retrieval path

The query is embedded once. PostgreSQL independently returns vector and full-text candidates scoped by workspace and optional mentioned document. Reciprocal-rank fusion combines positions using `1 / (60 + rank)`. An optional reranker may reorder the fused candidate list. The answer prompt assigns citation IDs and requires an insufficient-evidence response when sources do not support the answer.

## Job lifecycle

Ingestion uses `QUEUED -> RUNNING -> COMPLETED`, with `RETRYING`, `FAILED`, and `CANCELLED` terminal/side paths. Reindex uses equivalent lowercase states for compatibility with the original schema. Claims use row locks with skip-locked semantics. A worker lease timestamp allows another poller to recover abandoned work after the configured threshold.

Chunk replacement occurs only after every embedding batch succeeds, so a failed reindex does not partially replace the searchable document. The retrieval cache is cleared after a successful replacement.

## Decisions

1. PostgreSQL and pgvector remain one transactional source of truth to keep workspace joins, provenance, jobs, and chunks consistent.
2. Provider-neutral ports replace direct framework coupling and allow deterministic offline tests.
3. Raw extracted text is retained for reindexing; this trades storage/privacy obligations for reproducibility.
4. Billing is disabled instead of simulating orders or accepting unsigned webhooks.
5. Docker Compose is the default single-server topology. Horizontal deployment requires shared PostgreSQL/Redis, secret management, TLS ingress, and tested worker capacity.
