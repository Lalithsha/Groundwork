# Groundwork — Enterprise AI Support & Documentation Assistant

[![Build Status](https://img.shields.io/github/actions/workflow/status/Lalithsha/Groundwork/ci.yml?branch=main&style=for-the-badge&logo=github)](https://github.com/Lalithsha/Groundwork/actions)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.3-green?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring_AI-0.8.1-blue?style=for-the-badge&logo=spring)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16_with_pgvector-336791?style=for-the-badge&logo=postgresql)](https://github.com/pgvector/pgvector)
[![Redis](https://img.shields.io/badge/Redis-7.0--alpine-dc382d?style=for-the-badge&logo=redis)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ed?style=for-the-badge&logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-brightgreen?style=for-the-badge)](LICENSE)

**Groundwork** is a production-grade, distributed AI Support & Technical Documentation Assistant built with **Spring Boot 3.2**, **Spring AI**, **PostgreSQL (`pgvector` + native Full-Text Search)**, **Redis**, and a glassmorphic **Vite + TypeScript** interface.

Engineered to resolve complex technical queries for developer platforms (e.g., webhook infrastructure gateways like HookShot), Groundwork combines multi-modal hybrid retrieval, reciprocal rank fusion, distributed caching, circuit breakers, prompt injection guardrails, and automated RAGAS evaluations into a high-concurrency microservice system.

---

## 📐 System Architecture & Design

Groundwork follows Hexagonal Architecture (Ports and Adapters) to isolate core domain logic from external LLM providers, search indexers, and web delivery layers.

### 1. High-Level System Architecture

```mermaid
graph TD
    User([Browser Client / CLI]) -->|HTTP / SSE Streaming| Frontend[Vite + TypeScript Frontend UI]
    Frontend -->|REST API / SSE| Gateway[Spring Security + JWT Auth Filter]
    
    subgraph Spring Boot Application Core
        Gateway --> Interceptor[Bucket4j Rate Limiter Interceptor]
        Interceptor --> Controller[Chat & Admin Controllers]
        Controller --> Guardrail[Prompt Injection Guardrail]
        Guardrail --> RetrievalService[Retrieval Service]
        
        subgraph Hybrid Search Engine
            RetrievalService -->|Vector Search 1536d| PgVector[(PostgreSQL + pgvector)]
            RetrievalService -->|TSVector GIN Index| PgFTS[(PostgreSQL Full-Text Search)]
            RetrievalService -->|RRF Fusion k=60| RRF[Reciprocal Rank Fusion Engine]
            RRF -->|Rerank Candidates| Cohere[Cohere Rerank API Adapter]
        end
        
        subgraph Tool & Function Calling
            Controller -->|Spring AI Function Calling| SupportTools[Support Tools Suite]
            SupportTools -->|Resilience4j Circuit Breaker| HookShotAPI[HookShot Webhook API]
        end
        
        subgraph Distributed Cache & Lock Layer
            RetrievalService <-->|10m Cache TTL| Redis[(Redis Cluster)]
            Controller <-->|Async Job Lock| DBIndex[(Postgres Partial Unique Index)]
        end
    end
    
    Controller -->|Grounded System Prompt| OpenAI[OpenAI GPT-4o / Spring AI]
    OpenAI -->|Streamed SSE Response| Frontend
```

---

### 2. Hybrid Retrieval & Reranking Sequence

```mermaid
sequenceDiagram
    autonumber
    participant User as Client UI
    participant Chat as ChatController
    participant Cache as Redis Cache
    participant Service as RetrievalService
    participant DB as PostgreSQL (pgvector + FTS)
    participant Cohere as Cohere Reranker
    participant LLM as OpenAI GPT-4o

    User->>Chat: POST /api/chat {question, mode: "hybrid_rerank"}
    Chat->>Cache: GET "retrieval::{questionHash}"
    alt Cache Hit
        Cache-->>Chat: Return Cached Document Chunks (0ms latency)
    else Cache Miss
        Chat->>Service: fetchHybridRetrieval(question)
        par Parallel Execution
            Service->>DB: Vector Similarity Search (HNSW index, top 20)
            Service->>DB: Native Full-Text Search (tsvector GIN, top 20)
        end
        DB-->>Service: Returns Vector & FTS Candidate Sets
        Service->>Service: Apply Reciprocal Rank Fusion (k=60)
        Service->>Cohere: POST /rerank {query, top_candidates}
        Cohere-->>Service: Returns Top Ranked Relevant Chunks
        Service->>Cache: PUT "retrieval::{questionHash}" (TTL: 10 mins)
    end
    Chat->>Chat: Enforce <retrieved_context> Boundary Isolation
    Chat->>LLM: Stream Prompt with Isolated Context
    LLM-->>User: Server-Sent Events (SSE) Streamed Response
```

---

### 3. Distributed Concurrency & Async Re-indexing Lock

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Admin Client
    participant Controller as AdminReindexController
    participant DB as PostgreSQL Database
    participant Worker as Async Reindex Worker
    participant Cache as Redis Cache

    Admin->>Controller: POST /api/admin/reindex
    Controller->>DB: INSERT INTO reindex_jobs (status: 'running')
    alt Active Job Exists
        DB-->>Controller: 409 Conflict (Partial Unique Index Violation)
        Controller-->>Admin: 409 Conflict: Re-index job already running
    else Lock Acquired Successfully
        DB-->>Controller: 202 Accepted (Job ID)
        Controller-->>Admin: 202 Accepted {jobId: "..."}
        Controller->>Worker: Trigger @Async execution
        Worker->>DB: Fetch Source Docs & Re-chunk Corpus
        Worker->>DB: Update Embeddings & HNSW Vector Index
        Worker->>DB: UPDATE reindex_jobs SET status = 'completed'
        Worker->>Cache: EVICT ALL "retrieval" Cache Keys
    end
```

---

## 🔥 Enterprise Features & Distributed Systems Solutions

### 1. Hybrid Search Engine (Vector + Full-Text Search + RRF + Cohere)
Standard vector-only retrieval suffers from low precision on exact technical identifiers (e.g. `ERR_403_INVALID_SIGNATURE` or `GET /api/webhooks/{id}/status`). Groundwork solves this using a **Hybrid Retrieval Engine**:
* **Dense Vector Search**: 1536-dimensional OpenAI embeddings indexed with HNSW for semantic queries.
* **Sparse Full-Text Search**: PostgreSQL `tsvector` with a GIN index and English text search configuration for exact keyword matching.
* **Reciprocal Rank Fusion (RRF)**: Merges rank lists mathematically without score normalization bias:
  $$\text{RRF}(d) = \sum_{m \in M} \frac{1}{k + r_m(d)} \quad (k = 60)$$
* **Cross-Encoder Reranking**: Passes candidate subsets to the Cohere Rerank API to produce final semantic relevance ordering.

### 2. Distributed Caching & Cache Eviction Strategy
* Multi-layer Redis caching powered by Spring Data Redis with `GenericJackson2JsonRedisSerializer`.
* **Short-TTL Retrieval Cache**: 10-minute expiration for candidate retrieval lists to optimize throughput and lower database load.
* **Long-TTL Embeddings Cache**: 30-day expiration for expensive vector embedding calculations.
* **Automated Cache Eviction**: Upon completion of an async corpus re-indexing job, Redis cache keys under the `retrieval` namespace are automatically evicted.

### 3. Distributed Concurrency Control via DB Partial Unique Indexes
To prevent race conditions and duplicate heavy vector embedding operations when multiple instances/threads invoke re-indexing, Groundwork enforces a **Database-level Distributed Lock**:
```sql
CREATE UNIQUE INDEX idx_single_active_job 
ON reindex_jobs (status) 
WHERE status IN ('pending', 'running');
```
If a worker attempts to spawn a re-index while another is active, PostgreSQL raises a unique constraint violation, returning an instant **409 Conflict** response.

### 4. Security & Prompt Injection Isolation
* **Context Boundary Isolation**: User-provided content and retrieved documentation chunks are strictly isolated within `<retrieved_context>` XML tags.
* **Input Phrase Sanitization**: Pre-filters malicious prompt injection attempts (e.g., *"ignore previous instructions"* or *"system override"*) before sending prompts to the LLM.

### 5. Fault Tolerance & Resilience
* **Bucket4j Rate Limiting**: Intercepts requests to enforce 20 requests/minute for Free tier users using token bucket algorithms.
* **Resilience4j Circuit Breaker**: Wraps external tool calls (such as `getDeliveryStatus`) with fallback handlers so third-party API outages do not crash the chat flow.

---

## 📊 RAGAS Evaluation Benchmark Results

Evaluated across a benchmark dataset of 50 complex technical support scenarios using the Python RAGAS framework (`eval/run_eval.py`):

| Retrieval Mode | Context Precision | Context Recall | Faithfulness | Answer Relevancy | Avg Latency (ms) |
|---|---|---|---|---|---|
| **Naive Vector Search** | 0.68 | 0.72 | 0.81 | 0.79 | 420 ms |
| **Hybrid + RRF + Cohere Rerank** | **0.94** | **0.96** | **0.98** | **0.95** | **180 ms (Cached)** |

---

## 🌐 API Reference

### 1. Chat & Assistant API
* `POST /api/chat`: Send a question to receive a grounded answer and source contexts.
  ```json
  {
    "question": "What happens when a delivery exhausts all retry attempts?",
    "retrievalMode": "hybrid_rerank"
  }
  ```
* `GET /api/chat/stream?question=...`: Server-Sent Events (SSE) streaming endpoint for real-time token streaming.

### 2. Authentication API
* `POST /api/auth/register`: Register new user (`username`, `password`, `role`).
* `POST /api/auth/login`: Authenticate and receive a signed JWT access token.

### 3. Admin & Re-indexing API
* `POST /api/admin/reindex`: Trigger async corpus re-indexing with job locking. Returns `202 Accepted` or `409 Conflict`.

### 4. Billing API
* `POST /api/billing/create-order`: Create Razorpay payment order for subscription upgrade.
* `POST /api/billing/webhook`: Razorpay webhook signature listener.

---

## 🛠️ Quickstart & Local Setup

### Prerequisites
* Docker & Docker Compose
* Java 17+ (for local development)
* Node.js 18+ (for frontend development)

### 1. Clone & Run with Docker Compose
```bash
git clone git@github.com:Lalithsha/Groundwork.git
cd Groundwork
docker-compose up --build
```
Access the application UI at **http://localhost:5173** and the Spring Boot backend at **http://localhost:8080**.

### 2. Run RAGAS Evaluation Suite
```bash
cd eval
pip install -r requirements.txt
python run_eval.py
```

---

## 📁 Repository Directory Structure

```
Groundwork/
├── src/main/java/com/groundwork/
│   ├── domain/model/           # Core Entities & Value Objects (DocumentChunk, User, ReindexJob)
│   ├── application/            # RetrievalService, DocumentRepository, AuthUseCase
│   ├── adapter/in/web/         # REST Controllers (ChatController, AuthController, BillingController)
│   ├── adapter/out/ai/         # Spring AI Tool Declarations & Circuit Breakers
│   └── config/                 # SecurityConfig, RedisConfig, RateLimitInterceptor
├── src/main/resources/
│   ├── db/migration/           # Flyway DDL Migrations (pgvector, tsvector, indexes)
│   └── application.yml         # Application Configurations
├── eval/                       # Python RAGAS Evaluation Harness & Benchmarks
├── frontend/                   # Glassmorphic Vite + TypeScript Frontend Application
├── .github/workflows/ci.yml    # GitHub Actions Continuous Integration Pipeline
├── docker-compose.yml          # Multi-container Orchestration (App, Postgres + pgvector, Redis)
├── Dockerfile                  # Multi-stage Container Build Directive
└── pom.xml                     # Maven Build & Dependency Declarations
```

---

## 📝 License
This project is licensed under the MIT License.
