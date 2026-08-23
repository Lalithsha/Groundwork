# Groundwork

Groundwork is a workspace-scoped document intelligence application. It ingests PDF, Markdown, and text files, creates durable chunk embeddings, performs PostgreSQL full-text plus pgvector retrieval, and answers questions with stable source citations. It also exposes structured extraction, document comparison, review, and knowledge-graph workflows through a TypeScript web client.

## Current capabilities

- Durable PostgreSQL ingestion/reindex jobs with leases, retries, cancellation, progress, and crash recovery.
- Token-aware chunking, configurable OpenAI-compatible embeddings, and a deterministic local test adapter.
- Hybrid vector/full-text retrieval using reciprocal-rank fusion and optional Cohere reranking.
- Grounded chat responses, insufficient-evidence refusal, document scoping, citations, and typed SSE events.
- JWT access tokens, rotating refresh tokens, BCrypt passwords, workspace roles (`OWNER`, `ADMIN`, `EDITOR`, `VIEWER`), CORS controls, rate limiting, and mutation audit events.
- PDF/TXT/Markdown validation and extraction with limits for file size and PDF page count.
- Vite/TypeScript UI with authentication, workspace selection, upload progress, citations, comparisons, reviews, artifacts, and D3 graph rendering.
- Flyway migrations, Prometheus/Actuator endpoints, CI, deterministic unit tests, and an opt-in database integration test.

Billing is intentionally disabled. The old simulated payment implementation is not production-safe and cannot be enabled under the `prod` profile.

## Architecture

The application uses ports and adapters for AI providers and keeps job state in PostgreSQL. Redis is an optimization for retrieval caching and distributed rate-limit counters; source documents, chunks, memberships, and job state remain in PostgreSQL.

```text
Browser -> nginx -> Spring Security/controllers -> application services
                                            |-> PostgreSQL + pgvector
                                            |-> Redis cache/rate counters
                                            |-> OpenAI-compatible chat/embedding APIs
                                            `-> optional Cohere reranker
```

See [Architecture](docs/ARCHITECTURE.md), [Security](docs/SECURITY.md), [Runbook](docs/RUNBOOK.md), and the checked [OpenAPI contract](docs/openapi.json).

## Production-style local start

Requirements: Docker with Compose and live chat/embedding provider keys.

```bash
cp .env.example .env
# Replace every placeholder in .env.
docker compose config
docker compose up --build -d
```

Open `http://localhost:8081`. The backend is also bound to localhost at `http://localhost:8080`; health is available at `/actuator/health`.

Compose enables the `prod` profile. Startup fails closed if authentication is disabled, CORS is wildcarded, secrets are placeholders, local embeddings are selected, or billing is enabled.

## Developer workflow

Use Java 21, Maven, Node 20, PostgreSQL 16 with pgvector, and Redis 7.

```bash
./mvnw test
cd frontend && npm ci && npm run build
```

Or run both deterministic checks:

```bash
./scripts/verify.sh
```

The database integration test runs only when `RUN_DATABASE_INTEGRATION_TESTS=true`; CI supplies PostgreSQL and Redis services. Local embeddings are deterministic lexical vectors for development and tests, not a semantic production model.

## Important configuration

| Variable | Purpose |
|---|---|
| `SECURITY_ENABLED` | Require JWT authentication; mandatory in `prod`. |
| `JWT_SIGNING_SECRET` | Unique signing secret, at least 48 characters in `prod`. |
| `ALLOWED_ORIGINS` | Comma-separated browser origins; no wildcard in `prod`. |
| `EMBEDDING_PROVIDER` | `local` for deterministic tests or `openai-compatible` for production. |
| `EMBEDDING_API_KEY`, `EMBEDDING_BASE_URL`, `EMBEDDING_MODEL` | Embedding provider settings. |
| `GEMINI_API_KEY`, `AI_BASE_URL`, `AI_MODEL` | Chat provider settings. |
| `COHERE_API_KEY` | Optional reranking; RRF still works when absent. |

All migrations are additive under `src/main/resources/db/migration`. Back up PostgreSQL before upgrading and follow the rollback guidance in the runbook.

## Evaluation

`eval/run_eval.py` uploads a versioned fixture, waits for ingestion, executes both retrieval modes, and writes a timestamped JSON report with answer-term recall and citation coverage. It requires a running stack and, for meaningful semantic answers, live providers. No benchmark score is claimed until a generated report is committed with its environment metadata.

```bash
python3 eval/run_eval.py --workspace-id <uuid> --token <jwt>
```

A k6 smoke/load scenario is available at `performance/k6-smoke.js`; its thresholds are release targets until a versioned report is generated in the deployment environment.

## Project assessment

- [Assessment](PROJECT_ASSESSMENT.md)
- [Roadmap to 10/10](ROADMAP_TO_10.md)
- [Implementation handbook](HANDBOOK.md)

## License

MIT
