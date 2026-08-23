# Groundwork Roadmap to a 10/10 Project

> **Implementation status (2026-08-23):** The core product remediation in this roadmap has been implemented. The backend package and deterministic tests pass, the frontend production build passes, Compose configuration validates, and contract, secret-scan, evaluation, load, operations, and CI tooling is present. Remaining release gates are evidence-producing runs or deeper test investments: PostgreSQL/pgvector integration in CI (local Docker was unavailable), live model evaluation, browser/component/accessibility suites, load/soak execution, backup/restore rehearsal, and independent security testing. Those results must be recorded rather than inferred.

## Objective

Move Groundwork from a strong AI document-intelligence MVP to a credible, secure, measurable, and production-ready platform.

A "10/10" project is not one with the largest number of features. It is one whose core promises are real, tested, secure, observable, maintainable, and supported by reproducible evidence.

## Definition of Done

Groundwork can be considered a top-tier project when it satisfies all of the following:

- Every major capability advertised in the README works end to end.
- Semantic retrieval uses real stored and query embeddings.
- Answers are grounded in cited source chunks and evaluated for quality.
- Workspace and user isolation is enforced at the database and API layers.
- Background ingestion and reindexing are durable, retryable, and observable.
- Automated tests cover critical logic, integrations, and user journeys.
- Security controls have been threat-modeled and verified.
- Performance targets are measured under realistic load.
- A clean checkout can be built and run through documented commands.
- Documentation distinguishes measured capabilities from planned features.

## Current Baseline

The project already has a valuable product direction, a Spring Boot backend, PostgreSQL and pgvector schema support, Redis integration, a TypeScript frontend, document extraction, structured AI workflows, and knowledge-graph features.

The main blockers to a top score are:

1. Vector retrieval and embedding generation are incomplete.
2. Streaming and reindexing contain simulated behavior.
3. Automated test coverage is too small.
4. Workspace security and isolation need stronger guarantees.
5. Production configuration, observability, and failure handling need hardening.
6. Frontend structure and testing need improvement.
7. Benchmark and enterprise claims need reproducible evidence.

---

## Phase 0: Establish a Truthful, Reproducible Baseline

**Target duration:** 2–3 days
**Resulting maturity:** approximately 7/10

### Work

1. Add Maven Wrapper files and verify that `./mvnw test` works from a clean checkout.
2. Pin or document the supported Java, Node, Docker, PostgreSQL, and Redis versions.
3. Add a single verification command that runs backend tests, frontend checks, and production builds.
4. Add CI jobs for:
   - Backend compilation and tests
   - Frontend type checking and production build
   - Database migration validation
   - Dependency and secret scanning
5. Audit README claims and label capabilities as implemented, experimental, fallback, or planned.
6. Replace unverified benchmark numbers with either reproducible results or a clearly marked target table.
7. Add an architecture-decision record describing the chosen embedding model, dimensions, distance metric, and migration strategy.

### Acceptance Criteria

- A new developer can clone and validate the project with no globally installed Maven dependency.
- CI passes on a clean commit.
- The README contains no claims that cannot be demonstrated or reproduced.
- All required environment variables appear in `.env.example` without usable secrets.

---

## Phase 1: Build the Real Document Ingestion and Embedding Pipeline

**Target duration:** 1–2 weeks
**Resulting maturity:** approximately 7.8/10

This is the highest-priority phase because the platform's central promise depends on reliable ingestion and semantic indexing.

### 1.1 Define the Ingestion Model

Introduce explicit document and ingestion-job records rather than treating every chunk as an independent document.

Recommended entities:

- `documents`: ownership, workspace, title, type, checksum, status, timestamps
- `document_chunks`: document ID, sequence number, content, token count, metadata, embedding
- `ingestion_jobs`: document ID, status, progress, attempts, error details, timestamps
- `document_versions`: optional version lineage and source checksum

### 1.2 Improve File Validation

- Allow only explicitly supported extensions and MIME types.
- Validate MIME type from content, not only the filename.
- Enforce configurable size, page-count, and extracted-text limits.
- Reject encrypted or malformed PDFs with actionable errors.
- Prevent ZIP bombs and resource-exhaustion inputs if archive formats are later supported.
- Sanitize displayed filenames while preserving the original name as metadata.

### 1.3 Replace Character-Based Chunking

Implement a token-aware, structure-preserving chunker:

- Preserve headings, paragraphs, lists, tables, and page references.
- Use configurable target and overlap token counts.
- Avoid cutting sentences or code blocks where possible.
- Store chunk sequence, page range, section title, and source offsets.
- Make chunking deterministic so unchanged documents do not need re-embedding.

Suggested initial target: 400–700 tokens per chunk with 10–15% overlap, then tune using evaluation data.

### 1.4 Generate and Persist Embeddings

- Introduce an `EmbeddingPort` interface in the application layer.
- Implement provider adapters through Spring AI.
- Batch embedding calls to reduce latency and provider cost.
- Store provider, model, dimension, and embedding version.
- Validate vector dimensions before database insertion.
- Retry transient provider failures with exponential backoff and jitter.
- Mark permanent failures without losing the original uploaded document.
- Cache embeddings by normalized-content hash.

### 1.5 Make Ingestion Idempotent

- Use document and chunk checksums to prevent duplicate work.
- Define whether re-uploading a file creates a new version or replaces the old version.
- Ensure a failed ingestion can resume safely.
- Remove or supersede stale chunks atomically only after a new version completes.

### Acceptance Criteria

- Uploading a supported document creates a document record, structured chunks, and non-null embeddings.
- Re-uploading identical content does not call the embedding provider again.
- Failed embedding calls are retried and surfaced through job status.
- Every chunk contains enough metadata to cite its document, section, and page.
- Integration tests validate ingestion against a real PostgreSQL/pgvector container.

---

## Phase 2: Implement Genuine Hybrid Retrieval

**Target duration:** 1 week
**Resulting maturity:** approximately 8.3/10

### 2.1 Real Vector Search

Replace the current recency query in `DocumentRepository.searchVectorOnly()` with an actual pgvector distance query.

The retrieval flow should:

1. Generate an embedding for the normalized user query.
2. Filter candidates by authorized workspace and optional document IDs.
3. Use the same distance metric as the selected embedding/index configuration.
4. Return distance-derived relevance scores and chunk metadata.
5. Apply a configurable candidate limit and optional similarity threshold.

### 2.2 Correct Keyword Search

- Use PostgreSQL full-text search with stored language configuration.
- Add safe handling for identifiers, API paths, error codes, and quoted phrases.
- Consider trigram matching for filenames, acronyms, and misspellings.
- Validate query construction against syntax errors and expensive inputs.

### 2.3 Hybrid Fusion and Reranking

- Keep Reciprocal Rank Fusion but move constants into typed configuration.
- Preserve individual vector, keyword, fusion, and reranker scores for debugging.
- Deduplicate overlapping chunks from the same document section.
- Apply the external reranker only to a bounded candidate set.
- Define timeouts and graceful fallback when reranking is unavailable.
- Include workspace, document, and model version in cache keys.

### 2.4 Retrieval Explainability

In non-production or authorized diagnostic mode, expose:

- Retrieval mode
- Applied filters
- Selected chunks
- Per-stage rank and score
- Cache status
- Model versions
- Total stage latency

### Acceptance Criteria

- Semantic paraphrases retrieve the correct chunks even without exact keyword overlap.
- Exact identifiers and API paths are reliably found by keyword retrieval.
- Workspace filters are applied inside every database retrieval query.
- Reranker failure falls back to fused results without failing the request.
- Retrieval behavior is covered by deterministic integration tests.

---

## Phase 3: Make Answer Generation Grounded, Cited, and Truly Streamed

**Target duration:** 1 week
**Resulting maturity:** approximately 8.6/10

### 3.1 Grounded Answer Contract

Create a response contract containing:

- Answer text
- Source citations with document, page or section, and chunk ID
- Retrieval mode
- Confidence or evidence status
- Warnings when evidence is insufficient
- Request and trace IDs

The assistant should refuse to invent document-specific answers when no supporting context is retrieved.

### 3.2 Prompt Construction

- Separate trusted system instructions, user input, and untrusted document content.
- Give every retrieved chunk a stable citation identifier.
- Define maximum context size and deterministic truncation rules.
- Require claims to cite evidence identifiers.
- Add prompts and tests for conflicting documents, insufficient evidence, and malicious embedded instructions.

### 3.3 Real Streaming

Replace the mocked SSE text with the AI provider's streaming API.

Emit typed events such as:

- `retrieval_started`
- `sources`
- `token`
- `completed`
- `error`

Also:

- Cancel provider work when the client disconnects.
- Bound executor and connection resources.
- Add heartbeat and timeout behavior.
- Persist final usage, latency, and error metadata.

### Acceptance Criteria

- Streamed and non-streamed endpoints produce equivalent final answers.
- Every document-derived factual claim can be traced to a returned source.
- Client cancellation releases backend resources.
- Prompt-injection test documents cannot override system behavior.
- No-evidence queries return an explicit insufficient-evidence response.

---

## Phase 4: Replace Simulated Reindexing with Durable Background Jobs

**Target duration:** 4–6 days
**Resulting maturity:** approximately 8.8/10

### Work

- Remove the simulated `Thread.sleep()` reindex operation.
- Execute jobs through a dedicated worker or durable job mechanism.
- Claim jobs atomically to prevent duplicate processing.
- Track queued, running, retrying, completed, failed, and cancelled states.
- Record processed and total chunk counts.
- Add retry limits, exponential backoff, and a dead-letter state.
- Support reindexing by document, workspace, or embedding-model version.
- Keep old embeddings active until replacement embeddings are complete.
- Invalidate only affected cache entries after a successful commit.
- Add shutdown recovery so interrupted jobs resume or safely retry.

### Acceptance Criteria

- Restarting the application during reindexing does not lose the job.
- Concurrent requests cannot process the same job twice.
- Users can inspect accurate progress and failure reasons.
- A failed reindex does not make previously searchable documents unavailable.

---

## Phase 5: Enforce Multi-Tenant Security and Workspace Isolation

**Target duration:** 1–2 weeks
**Resulting maturity:** approximately 9/10

### 5.1 Authorization Model

Define explicit roles such as:

- Workspace owner
- Administrator
- Editor
- Viewer

Create membership tables and enforce authorization through application services, not only controller checks.

### 5.2 Workspace Isolation

- Derive workspace access from the authenticated principal.
- Never trust a workspace ID supplied by the client without authorization.
- Include workspace constraints in document, artifact, comparison, graph, review, and retrieval queries.
- Decide whether to add PostgreSQL Row-Level Security as defense in depth.
- Add cross-workspace leakage tests for every API family.

### 5.3 Authentication and Session Hardening

- Validate JWT issuer, audience, expiry, signature algorithm, and key rotation.
- Store refresh tokens securely and support revocation or rotation.
- Remove production-compatible default secrets.
- Apply environment-specific CORS policies instead of wildcard origins.
- Add login and sensitive-operation rate limits.

### 5.4 AI and Document Threat Model

Document and mitigate:

- Prompt injection inside uploaded content
- Sensitive data leakage between workspaces
- Malicious file uploads
- Denial of service through oversized prompts or documents
- Indirect data exfiltration through tools
- Excessive provider usage and cost abuse
- Sensitive text in logs, traces, or error messages

### Acceptance Criteria

- A user cannot infer the existence, title, content, artifact, graph node, or job status of another workspace's data.
- Automated tests cover horizontal and vertical privilege escalation.
- Production startup fails when required secrets or allowed origins are missing.
- Security-sensitive actions produce auditable events without logging document contents or credentials.

---

## Phase 6: Build a Serious Automated Test Strategy

**Target duration:** begins in Phase 0 and continues throughout
**Resulting maturity:** approximately 9.3/10

Test coverage should be risk-based. A raw percentage is useful but must not replace scenario coverage.

### Unit Tests

Cover:

- Token-aware chunking and metadata preservation
- RRF score calculation and deduplication
- Document mention parsing
- Prompt assembly and context limits
- Fallback extraction and comparison behavior
- Authorization decisions
- Cache-key generation
- Retry and state-transition rules

### Integration Tests

Use Testcontainers for PostgreSQL with pgvector and Redis. Test:

- Flyway migrations on an empty database
- Vector and full-text retrieval
- Repository workspace filters
- Cache population and invalidation
- Idempotent ingestion
- Background job claiming and recovery
- Authentication and authorization boundaries

### Contract Tests

- Validate request and response schemas for all public endpoints.
- Test AI, reranker, and embedding adapters against controlled mock servers.
- Ensure fallback behavior remains stable when providers time out or return malformed responses.

### End-to-End Tests

Automate the essential user journeys:

1. Register or sign in.
2. Create a workspace.
3. Upload a document.
4. Wait for ingestion.
5. Ask a question and inspect citations.
6. Compare two documents.
7. Build and inspect the knowledge graph.
8. Delete a document and verify it disappears from retrieval.

### Quality Gates

- No merge when critical tests fail.
- High coverage for application and security logic; set the exact threshold after measuring the baseline.
- Every production bug receives a regression test.
- Flaky tests are tracked and repaired rather than silently retried indefinitely.

---

## Phase 7: Refactor and Test the Frontend

**Target duration:** 1–2 weeks
**Resulting maturity:** approximately 9.5/10

### Architecture

Split the current interface into feature modules:

- Authentication
- Workspace navigation
- Document library and upload
- Chat and citations
- Artifact explorer
- Document comparison
- Review reports
- Knowledge graph
- Administration and job status

Add:

- A typed API client generated from or checked against an OpenAPI contract
- Central error normalization
- Predictable server-state and cache handling
- Shared accessible UI primitives
- Route-level error boundaries

### User Experience

- Display upload and ingestion progress separately.
- Explain why a document is not searchable yet.
- Show source citations with page and section previews.
- Provide clear empty, loading, partial-success, offline, and retry states.
- Preserve chat and filters when navigating between modules.
- Make graph and comparison views keyboard accessible.
- Validate responsive behavior across common screen sizes.

### Testing

- Unit-test formatting and state logic.
- Component-test uploads, citations, errors, and permissions.
- Add accessibility checks.
- Run Playwright end-to-end journeys in CI.
- Add visual regression tests for the principal screens and both themes.

### Acceptance Criteria

- Core workflows work with keyboard-only navigation.
- The UI does not display controls the current role cannot use.
- All API failure classes produce understandable recovery actions.
- Critical user journeys pass in browser automation.

---

## Phase 8: Production Reliability, Observability, and Performance

**Target duration:** 1–2 weeks
**Resulting maturity:** approximately 9.7/10

### Reliability

- Configure explicit connect, read, and total timeouts for external providers.
- Use bounded thread pools and queues.
- Apply retries only to safe, transient operations.
- Add circuit breakers and bulkheads based on measured failure modes.
- Make graceful degradation visible to users and operators.
- Verify graceful shutdown and in-flight job handling.

### Observability

Add structured metrics for:

- Upload size and extraction duration
- Chunk and embedding counts
- Ingestion success and failure rates
- Vector, keyword, fusion, and reranking latency
- Cache hit rate
- AI latency, token usage, and estimated cost
- Time to first streamed token
- Job queue depth and age
- Authorization denials and rate-limit events

Add trace propagation from HTTP request through retrieval, database, cache, and AI provider calls. Use correlation IDs in errors and logs.

### Performance and Load Testing

Define realistic datasets and workloads, then measure:

- Concurrent uploads
- Search latency at increasing corpus sizes
- Concurrent streamed chats
- Redis or reranker outage behavior
- Large-document memory use
- Background reindexing while serving queries

Initial service objectives can include:

- Cached retrieval p95 below 250 ms
- Uncached retrieval p95 below 1 second, excluding external model generation
- No cross-workspace results under any load
- 99.5% successful request rate in a defined normal-load test
- Bounded memory growth during large-document ingestion

These targets should be revised after baseline measurement.

### Acceptance Criteria

- A dashboard shows health, latency, errors, usage, and job backlog.
- Alerts correspond to user-visible failure conditions.
- Load-test reports are versioned and reproducible.
- Known provider outages result in controlled degradation rather than cascading failure.

---

## Phase 9: Build a Reproducible AI Quality Evaluation System

**Target duration:** 1 week initially, then continuous
**Resulting maturity:** approximately 9.8/10

### Evaluation Dataset

Create a versioned benchmark containing:

- Representative technical documents
- Exact-identifier questions
- Semantic paraphrase questions
- Multi-document questions
- Questions with no answer in the corpus
- Conflicting-document scenarios
- Prompt-injection documents
- Expected source chunks and answer criteria

Avoid using only synthetic, easy, or README-derived questions.

### Metrics

Track:

- Retrieval recall at K
- Mean reciprocal rank or nDCG
- Context precision
- Citation correctness
- Groundedness or faithfulness
- Answer relevance
- Correct refusal rate for unsupported questions
- Latency and cost per query

### Experiment Discipline

- Store model, prompt, chunker, retriever, and dataset versions with every run.
- Compare naive keyword, vector, hybrid, and reranked modes.
- Set regression thresholds in CI for deterministic retrieval metrics.
- Require human review for a representative sample of generative answers.

### Acceptance Criteria

- Every published quality number links to a reproducible evaluation configuration.
- Retrieval or prompt changes cannot silently reduce agreed quality thresholds.
- Quality, latency, and cost tradeoffs are visible in the same report.

---

## Phase 10: Documentation, Deployment, and Release Readiness

**Target duration:** 4–6 days
**Resulting maturity:** 10/10 project standard

### Documentation

- Keep the README focused on product value and quickstart.
- Maintain an accurate architecture document and threat model.
- Generate or validate OpenAPI documentation from runtime contracts.
- Add operator runbooks for database, Redis, AI-provider, and queue failures.
- Add backup, restore, migration, rollback, and incident-response procedures.
- Record important decisions as ADRs.

### Deployment

- Provide separate local, test, staging, and production profiles.
- Run database migrations as a controlled deployment step.
- Add readiness and liveness probes with correct semantics.
- Use immutable container images and non-root runtime users.
- Scan containers and dependencies in CI.
- Establish resource requests, limits, and autoscaling signals.
- Test backup restoration rather than only creating backups.

### Release Gate

A production release is permitted only when:

- CI, integration, end-to-end, security, and migration tests pass.
- AI quality and load-test results meet published thresholds.
- Rollback and data-restoration procedures have been exercised.
- Documentation matches the deployed behavior.
- No critical or high-severity security issue remains unresolved.

---

## Recommended Implementation Order

| Order | Milestone | Why It Comes Here |
|---:|---|---|
| 1 | Reproducible build and truthful baseline | Prevents new work from building on uncertain foundations |
| 2 | Ingestion and embeddings | Enables the core semantic-search capability |
| 3 | Real hybrid retrieval | Converts stored embeddings into useful results |
| 4 | Grounded answers and real streaming | Completes the primary user workflow |
| 5 | Durable reindexing | Makes indexing operationally reliable |
| 6 | Workspace authorization | Prevents serious multi-tenant data leakage |
| 7 | Expanded automated testing | Locks in behavior across the completed core |
| 8 | Frontend refactor and UX hardening | Makes the complete workflows maintainable and usable |
| 9 | Reliability and performance | Establishes production operating confidence |
| 10 | AI evaluation and release evidence | Proves quality claims and prevents regression |

Security and automated tests must be implemented continuously rather than postponed entirely until their named phases.

## Suggested First 12 Engineering Issues

1. Add Maven Wrapper and a full local verification script.
2. Add Testcontainers for PostgreSQL/pgvector and Redis.
3. Introduce first-class document, chunk, and ingestion-job schemas.
4. Implement token-aware chunking with source metadata.
5. Create an embedding application port and Spring AI provider adapter.
6. Persist versioned chunk embeddings during upload.
7. Replace recency-based vector retrieval with pgvector similarity search.
8. Add workspace constraints to every document retrieval query.
9. Add hybrid-retrieval tests for semantic and exact-identifier queries.
10. Return stable source citations with chat answers.
11. Replace mock SSE output with provider token streaming.
12. Replace simulated reindexing with a durable worker and progress model.

## Team and Timeline Estimate

For one experienced full-stack engineer, a credible implementation would likely require approximately **10–14 focused weeks**, excluding extended production observation and compliance work.

For a small team of three—backend/AI, frontend, and platform/quality—the core roadmap could be completed in approximately **5–8 weeks**, provided the phases are coordinated and testing is done alongside implementation.

These are planning ranges, not commitments. Actual effort depends on the selected AI providers, deployment environment, document formats, scale target, and security requirements.

## Final Scorecard

Use this scorecard before calling the project complete:

| Area | Weight | Required Evidence |
|---|---:|---|
| Core product correctness | 20% | End-to-end workflows and integration tests |
| Retrieval and AI quality | 20% | Versioned evaluation dataset and reproducible results |
| Security and isolation | 15% | Threat model and authorization/adversarial tests |
| Reliability and recovery | 10% | Failure tests, durable jobs, and recovery exercises |
| Automated quality | 10% | CI gates across unit, integration, contract, and E2E tests |
| Performance and scalability | 10% | Versioned load-test reports against stated objectives |
| User experience and accessibility | 5% | Browser tests and accessibility validation |
| Maintainability and architecture | 5% | Modular code, ADRs, and controlled dependencies |
| Documentation and reproducibility | 5% | Clean-checkout verification and accurate runbooks |

The project reaches a 10/10 standard only when every area has evidence, not merely implementation claims.
