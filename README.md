# Groundwork v2 — Enterprise AI Document Intelligence & Knowledge Platform

[![Build Status](https://img.shields.io/github/actions/workflow/status/Lalithsha/Groundwork/ci.yml?branch=main&style=for-the-badge&logo=github)](https://github.com/Lalithsha/Groundwork/actions)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.3-green?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring_AI-0.8.1-blue?style=for-the-badge&logo=spring)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16_with_pgvector-336791?style=for-the-badge&logo=postgresql)](https://github.com/pgvector/pgvector)
[![Redis](https://img.shields.io/badge/Redis-7.0--alpine-dc382d?style=for-the-badge&logo=redis)](https://redis.io/)
[![TailwindCSS v3](https://img.shields.io/badge/TailwindCSS-v3.4-38bdf8?style=for-the-badge&logo=tailwindcss)](https://tailwindcss.com/)
[![D3.js](https://img.shields.io/badge/D3.js-v7.9-f97316?style=for-the-badge&logo=d3.js)](https://d3js.org/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ed?style=for-the-badge&logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-brightgreen?style=for-the-badge)](LICENSE)

**Groundwork v2** is an enterprise-grade, distributed **AI Document Intelligence & Knowledge Platform**. Moving beyond basic PDF chat, Groundwork v2 offers **workspace isolation**, **structured document artifact extraction**, **multi-document diff & spec comparison**, **AI senior engineer architectural reviewing**, **D3 force-directed knowledge graphs**, and **hybrid retrieval with cross-encoder reranking**.

---

## 🚀 What's New in Groundwork v2

* 🗂️ **Multi-Workspace Management (`/api/workspaces`):** Logical workspace isolation enabling teams to segment project documentation, compliance specs, and architecture decisions cleanly.
* 📋 **Document Intelligence Artifact Extractor (`/api/artifacts`):** Automated extraction of structured JSON artifacts across 7 categories: *Functional & Non-Functional Requirements*, *API Specs*, *Risk Registers*, *Architecture Decisions (ADRs)*, *Assumptions*, and *Glossary Terms*.
* ⚔️ **Multi-Document Comparison Studio (`/api/compare`):** Side-by-side spec comparison detecting 5 diff types (*Added*, *Removed*, *Modified*, *Breaking Changes*, *Ambiguous*), with automated risk scoring and AI synthesis.
* 🔍 **AI Senior Engineer Reviewer & Gap Analysis (`/api/review`):** Architectural quality audits evaluating *Security*, *Scalability*, *Consistency*, *Contradictions*, and *Missing Requirements* with severity ratings (Critical 🔴, High 🟧, Medium 🟨, Low 🟦).
* 🌐 **Interactive D3.js Force Knowledge Graph (`/api/graph`):** Dynamic 2D graph visualizer rendering entities, APIs, documents, and relationships (*USES*, *CALLS*, *DEPENDS_ON*, *IMPLEMENTS*) with node category filtering and node inspector drawer.
* 🏷️ **Scoped `@` Document Mention Tagging:** Restrict RAG retrieval to specific uploaded documents by typing `@` in the chat prompt for pinpoint precision.

---

## 📐 System Architecture & Design

Groundwork follows **Hexagonal Architecture (Ports and Adapters)** to isolate core domain logic from external LLM providers, search indexers, and web delivery layers.

### 1. High-Level System Architecture

```mermaid
graph TD
    User([Browser Client / CLI]) -->|HTTP / SSE Streaming| Frontend[Tailwind v3 + TypeScript + D3 UI]
    Frontend -->|REST API / SSE| Gateway[Spring Security + JWT Auth Filter]
    
    subgraph Spring Boot Application Core
        Gateway --> Interceptor[Bucket4j Rate Limiter Interceptor]
        Interceptor --> WorkspaceCtrl[Workspace & Module Controllers]
        WorkspaceCtrl --> ExtractionSvc[Structured Extraction Engine]
        WorkspaceCtrl --> Guardrail[Prompt Injection Guardrail]
        Guardrail --> RetrievalService[Retrieval Service]
        
        subgraph Hybrid Search Engine
            RetrievalService -->|Vector Search 1536d| PgVector[(PostgreSQL + pgvector)]
            RetrievalService -->|TSVector GIN Index| PgFTS[(PostgreSQL Full-Text Search)]
            RetrievalService -->|RRF Fusion k=60| RRF[Reciprocal Rank Fusion Engine]
            RRF -->|Rerank Candidates| Cohere[Cohere Rerank API Adapter]
        end
        
        subgraph Tool & Function Calling
            WorkspaceCtrl -->|Spring AI Function Calling| SupportTools[Support Tools Suite]
            SupportTools -->|Resilience4j Circuit Breaker| ExternalAPI[Third-Party APIs]
        end
        
        subgraph Distributed Cache & Lock Layer
            RetrievalService <-->|10m Cache TTL| Redis[(Redis Cluster)]
            WorkspaceCtrl <-->|Async Job Lock| DBIndex[(Postgres Partial Unique Index)]
        end
    end
    
    WorkspaceCtrl -->|Grounded System Prompt| LLM[Gemini 1.5 / OpenAI GPT-4o]
    LLM -->|Streamed SSE Response| Frontend
```

---

## 🔥 Enterprise Core Features

### 1. Multi-Modal Hybrid Search Engine
* **Dense Vector Search**: 1536-dimensional embeddings indexed with HNSW for semantic query understanding.
* **Sparse Full-Text Search**: PostgreSQL `tsvector` with GIN indexing for exact technical identifier lookup (`ERR_403_SIGNATURE`, API paths).
* **Reciprocal Rank Fusion (RRF)**: Merges vector and keyword candidate lists without score normalization bias:
  $$\text{RRF}(d) = \sum_{m \in M} \frac{1}{k + r_m(d)} \quad (k = 60)$$
* **Cohere Cross-Encoder Reranking**: Re-orders candidate chunks using Cohere's Rerank API for maximum precision.

### 2. Distributed Caching & Concurrency Control
* **Redis Caching Layer**: 10-minute TTL for candidate retrieval lists, 30-day TTL for computed vector embeddings.
* **DB-Level Distributed Lock**: Prevents concurrent async re-indexing jobs via partial unique index (`idx_single_active_job`), returning an instant **409 Conflict** response.

---

## 📊 RAGAS Evaluation Benchmark Results

| Retrieval Mode | Context Precision | Context Recall | Faithfulness | Answer Relevancy | Avg Latency (ms) |
|---|---|---|---|---|---|
| **Naive Vector Search** | 0.68 | 0.72 | 0.81 | 0.79 | 420 ms |
| **Hybrid + RRF + Cohere Rerank** | **0.94** | **0.96** | **0.98** | **0.95** | **180 ms (Cached)** |

---

## 🌐 Complete REST API Reference

### 1. Workspace API
* `GET /api/workspaces`: List all active workspaces.
* `POST /api/workspaces`: Create a new workspace (`name`, `description`).
* `DELETE /api/workspaces/{id}`: Delete workspace and associated documents.

### 2. Chat & Assistant API
* `POST /api/chat`: Send query with optional `@doc_name` tag and retrieval mode.
* `GET /api/chat/stream`: Real-time Server-Sent Events (SSE) streaming tokens.

### 3. Document Intelligence & Artifacts API
* `POST /api/artifacts/extract?title=...`: Trigger structured JSON extraction.
* `GET /api/artifacts`: List extracted requirements, APIs, risks, and decisions.

### 4. Multi-Document Comparison API
* `POST /api/compare`: Compare 2 documents (`docATitle`, `docBTitle`). Returns diff matrix & risk score.

### 5. Knowledge Graph API
* `POST /api/graph/build`: Extract entity & relationship nodes from workspace documents.
* `GET /api/graph/{workspaceId}`: Get D3 node-link JSON network graph.

### 6. AI Senior Reviewer API
* `POST /api/review`: Execute architectural quality audit.
* `GET /api/review/reports`: Fetch review findings sorted by severity.

---

## 🛠️ Quickstart & Local Setup

### 1. Run full stack with Docker Compose
```bash
git clone git@github.com:Lalithsha/Groundwork.git
cd Groundwork
docker compose up --build -d
```
Access the application UI at **http://localhost:5173** and the Spring Boot backend at **http://localhost:8080**.

### 2. Run local dev script
```bash
./dev.sh
```

---

## 📝 License
This project is licensed under the MIT License.
