# Groundwork — Support & Docs AI Assistant

Groundwork is a production-grade AI Support & Documentation Assistant built with Spring Boot 3.x, Spring AI, PostgreSQL (`pgvector` + native FTS), Redis, and Vite + TypeScript.

## 🚀 Key Architectural Features

1. **Hybrid Retrieval & RRF Reranking (Phase 1):**
   - Combines vector similarity search with Postgres Full-Text Search (`tsvector` / GIN index).
   - Merges candidate sets using Reciprocal Rank Fusion (RRF): $score(doc) = \sum \frac{1}{k + rank}$.
   - Supports Cohere Rerank / Cross-encoder scoring.
   - Includes a togglable `retrievalMode` flag (`naive` vs `hybrid_rerank`) for baseline evaluation.

2. **RAGAS Evaluation Harness (Phase 2):**
   - Standalone Python evaluation suite in `eval/` running Faithfulness, Answer Relevancy, Context Precision, and Context Recall metrics across retrieval modes.

3. **Performance Caching & SSE Streaming (Phase 3):**
   - Redis caching for embeddings and short-TTL retrieval results.
   - Server-Sent Events (`/api/chat/stream`) token streaming interface.

4. **Resilience, Observability & Security (Phase 4):**
   - Resilience4j Circuit Breaker on the external HookShot status lookup tool call.
   - Bucket4j rate limiting & prompt injection context isolation (`<retrieved_context>`).
   - Prometheus metrics exported via `/actuator/prometheus`.

5. **Async Corpus Re-indexing (Phase 5):**
   - Background re-indexing with partial unique index locking (`WHERE status IN ('pending', 'running')`) preventing concurrent job collisions.

## 📂 Project Structure

```
Groundwork/
├── src/main/java/com/groundwork/
│   ├── domain/model/           # DocumentChunk, DeliveryStatusResult, ChatResponseDto
│   ├── application/            # RetrievalService, DocumentRepository
│   ├── adapter/in/web/         # ChatController, AdminReindexController
│   ├── adapter/out/ai/         # SupportTools (Spring AI Function Calling + Circuit Breaker)
│   └── config/                 # SecurityConfig, Resilience4j, Redis Config
├── src/main/resources/
│   ├── db/migration/           # V1__init_schema.sql (pgvector, tsvector, indexes)
│   └── application.yml
├── eval/                       # Python RAGAS Evaluation Harness
├── frontend/                   # Vite + TypeScript Frontend UI
├── docker-compose.yml
├── Dockerfile
└── pom.xml
```

## 🛠️ Getting Started

### 1. Run via Docker Compose
```bash
docker-compose up --build
```

### 2. Run Backend Locally
```bash
./mvnw spring-boot:run
```

### 3. Run RAGAS Evaluation
```bash
cd eval
pip install -r requirements.txt
python run_eval.py
```
