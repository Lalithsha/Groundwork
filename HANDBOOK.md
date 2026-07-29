# 📖 Groundwork v2 — Master Project & Technical Handbook

---

## 📌 1. Executive Overview & Product Vision

### What is Groundwork?
**Groundwork v2** is an **Enterprise AI Document Intelligence, Architecture Quality Review, & Knowledge Engine Platform**. It bridges the gap between raw enterprise documentation (PDFs, Markdown contracts, architecture specs, API guides) and actionable, structured software engineering insights.

### Core Value Proposition:
1. **Eliminates Documentation Drift:** Detects when software implementations diverge from original architectural contracts or API specs.
2. **Automates Quality & Security Audits:** Evaluates specifications and code bases against enterprise engineering guidelines for security, performance, and compliance.
3. **Reduces Cognitive Overhead:** Converts 100-page technical PDFs into structured JSON artifacts (Requirements, OpenAPI contracts, Risk registers, ADRs).
4. **Visualizes Knowledge Relationships:** Renders an interactive D3.js force-directed 2D graph visualizing documents, text chunks, concept entities, and API endpoints.
5. **High-Precision Hybrid Retrieval:** Combines BM25 Full-Text Search with 1536-dimensional vector embeddings, Reciprocal Rank Fusion (RRF reranking), and `@` document-level retrieval scoping.

---

## 🏗️ 2. Enterprise System Architecture & Technology Stack

```
                                  ┌─────────────────────────────────────────────────────────────┐
                                  │                Groundwork v2 Web Interface                  │
                                  │      (TypeScript + TailwindCSS v3 + D3.js Knowledge Graph) │
                                  └──────────────────────────────┬──────────────────────────────┘
                                                                 │ HTTP / REST APIs
                                                                 ▼
                                  ┌─────────────────────────────────────────────────────────────┐
                                  │              Spring Boot 21 Backend Engine                  │
                                  │  (Workspace Routing + RAG Engine + Spring AI + Structured)  │
                                  └───────────────┬──────────────────────────────┬──────────────┘
                                                  │                              │
                        ┌─────────────────────────┴─────────┐          ┌─────────┴─────────────────────────┐
                        │   PostgreSQL 16 Vector Storage    │          │      Redis 7 Distributed Cache    │
                        │  (pgvector + Flyway V1/V2 DDL)   │          │  (RAG Answer Cache & Lock Purge) │
                        └───────────────────────────────────┘          └───────────────────────────────────┘
```

### Backend Engine Stack:
* **Framework:** Spring Boot 3.2.x with Java 21 LTS
* **AI Orchestration:** Spring AI (OpenAI / Gemini / Cohere Rerank models)
* **Database & Vector Engine:** PostgreSQL 16 + `pgvector` extension (Cosine & Dot Product vector distance indexes)
* **Caching & Distributed Locks:** Redis 7 Alpine
* **Schema Migrations:** Flyway DDL Version Control (`V1__init.sql`, `V2__groundwork_v2_schema.sql`)

### Frontend Interface Stack:
* **Logic & Build Tool:** TypeScript, Vite 5.x, HTML5
* **Design System:** Custom Vanilla CSS + TailwindCSS v3 (Monochrome Vercel/Linear dark theme & clean light theme switcher)
* **Data Visualization:** D3.js v7 Force-Directed 2D Graph Engine

---

## 🗄️ 3. Database Schema & DDL Specifications

The PostgreSQL database enforces logical workspace isolation across all entities using `workspace_id UUID` foreign key cascades.

```sql
-- Workspaces Table
CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Documents Table
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    source_type TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Knowledge Artifacts Table
CREATE TABLE knowledge_artifacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    artifact_type TEXT NOT NULL, -- 'requirement', 'api', 'risk', 'decision'
    content TEXT NOT NULL,
    structured_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Document Comparisons Table
CREATE TABLE document_comparisons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    doc_title_a TEXT NOT NULL,
    doc_title_b TEXT NOT NULL,
    comparison_result TEXT NOT NULL,
    diff_summary JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Graph Entities & Relationships Tables
CREATE TABLE graph_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    entity_type TEXT NOT NULL, -- 'Document', 'Chunk', 'Entity', 'API'
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE graph_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    source_entity_id UUID NOT NULL REFERENCES graph_entities(id) ON DELETE CASCADE,
    target_entity_id UUID NOT NULL REFERENCES graph_entities(id) ON DELETE CASCADE,
    relationship_type TEXT NOT NULL, -- 'CONTAINS_CHUNK', 'REFERENCES', 'DEFINES_ENDPOINT'
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Review Reports Table
CREATE TABLE review_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    score DOUBLE PRECISION,
    feedback TEXT,
    report_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 🧠 4. Deep Dive into Core Modules & Capabilities

### Module 1: Multi-Workspace Management System (`/api/workspaces`)
Allows enterprise teams to logically isolate documentation corpora into distinct project workspaces (`ws-groundwork-core`, `ws-fintech-specs`, `ws-security-compliance`, `ws-architecture-v2`).
* **Isolation:** Switching workspaces updates vector retrieval queries, artifact grids, diff comparisons, and knowledge graphs dynamically.

### Module 2: Hybrid RAG & Scoped Mention Tagging Engine (`/api/chat`)
* **Hybrid Search (Vector + BM25 + RRF):** Combines vector distance matching with BM25 full-text search, reranked via Reciprocal Rank Fusion ($k=60$).
* **Scoped `@` Document Mentions:** Users can restrict RAG retrieval to specific uploaded documents by typing `@DocumentTitle` in the chat input bar.

### Module 3: Document Intelligence Artifact Extractor (`/api/artifacts`)
Automates structured extraction of enterprise technical artifacts across 4 main categories:
1. 📋 **Functional & Non-Functional Requirements:** Business rules, SLAs, and performance caps.
2. 🔌 **API Specifications:** HTTP paths, request parameters, JSON types, and rate limits.
3. ⚠️ **Risk Registers:** Technical debt, security vulnerabilities, and single-points-of-failure.
4. 💡 **Architecture Decisions (ADRs):** Technical choices, candidate evaluation, and rationales.

### Module 4: Multi-Document Comparison Studio (`/api/compare`)
Side-by-side AI spec diffing engine comparing two document versions (`Base` vs `Target`):
* Computes structural alignment score (`84.5% match`).
* Categorizes changes into Added (`+`), Removed (`-`), and Conflicting (`~ Divergence`) clauses.
* Generates an executive AI Conflict Resolution summary explaining how to align diverging specs.

### Module 5: AI Senior Reviewer & Architectural Gap Analysis (`/api/review`)
Acts as an automated Senior Technical Architect reviewing documentation against enterprise guidelines:
* Evaluates Security, Scalability, Consistency, and Completeness.
* Assigns severity ratings: Critical 🔴, High 🟧, Medium 🟨, Low 🟦.
* Provides concrete refactoring recommendations.

### Module 6: Interactive D3.js Force Knowledge Graph Explorer (`/api/graph`)
* Renders a dynamic 2D force-directed node-link graph visualizing relationships (`CONTAINS_CHUNK`, `REFERENCES`, `DEFINES_ENDPOINT`).
* Features node category filtering (Docs, Chunks, Entities, APIs), search highlights, zoom/pan controls, and an inspector side drawer.

---

## 🎨 5. Design System, UI/UX Guidelines & Themes

### Vercel / Linear Enterprise Design Language:
* **Dark Mode (Default):** Deep Obsidian (`#09090b`), Slate Containers (`#121215`), Pure White Text (`#ffffff`), Glassmorphism Panels, and Subtle Borders (`border-zinc-800`).
* **Light Mode:** Slate Canvas (`#f8fafc`), Crisp White Cards (`#ffffff`), Dark Slate Typography (`#0f172a`), and Soft Slate Borders (`border-slate-200`).
* **Theme Persistence:** Stores user choice (`groundwork_theme`) in `localStorage` with fallback to `prefers-color-scheme`.

---

## 📡 6. Complete REST API Technical Reference

### 1. Workspace APIs
* `GET /api/workspaces` — Fetch all workspaces.
* `POST /api/workspaces` — Create new workspace `{ "name": "...", "description": "..." }`.

### 2. Document & Indexing APIs
* `GET /api/documents` — Fetch active workspace documents.
* `POST /api/documents` — Upload and index file into vector database.
* `DELETE /api/documents?title={title}` — Delete document from active workspace.

### 3. RAG Chat Assistant API
* `POST /api/chat` — Submit query to RAG engine.
  ```json
  {
    "question": "What is the batch size limit?",
    "retrievalMode": "hybrid_rerank",
    "workspace": "ws-groundwork-core"
  }
  ```

### 4. Document Intelligence Artifacts API
* `GET /api/artifacts?workspace={ws}` — Fetch extracted structured artifacts.
* `POST /api/artifacts/extract` — Trigger structured extraction across active workspace.

### 5. Multi-Document Comparison API
* `POST /api/compare` — Compare two documents.
  ```json
  {
    "docATitle": "API_CONTRACT_V1.md",
    "docBTitle": "API_IMPLEMENTATION.md",
    "mode": "semantic"
  }
  ```

### 6. AI Senior Reviewer API
* `POST /api/review` — Run architectural review.
  ```json
  {
    "scope": "all",
    "focus": "security"
  }
  ```

### 7. D3 Knowledge Graph API
* `GET /api/graph?workspace={ws}&filter={filter}` — Fetch nodes and relationships JSON.

---

## 🚀 7. Operations & Docker Setup Guide

### Build & Launch Stack:
```bash
# Clone Repository
git clone git@github.com:Lalithsha/Groundwork.git
cd Groundwork

# Start Full Stack (Backend + Postgres pgvector + Redis + Frontend)
docker compose up --build -d

# Verify Container Status
docker ps
```

### Access URLs:
* **Web Frontend:** `http://localhost:5173`
* **Spring Boot REST Backend:** `http://localhost:8080`
* **PostgreSQL pgvector:** `localhost:5433` (DB: `groundwork`, User: `postgres`)
* **Redis Cache:** `localhost:6380`

---

## 📜 8. Chronological Decisions Log

| Date | Phase | Decision & Architectural Rationale |
|---|---|---|
| 2026-07-25 | Phase 1 | Built core RAG backend with Spring Boot 3.2, pgvector, and BM25 hybrid search. |
| 2026-07-26 | Phase 2 | Created Groundwork v2 DDL schema (`V2__groundwork_v2_schema.sql`) adding workspaces, artifacts, comparisons, graph entities, and review reports. |
| 2026-07-27 | Phase 3 | Built TailwindCSS v3 frontend views: Document Intelligence Grid, Diff Studio, AI Senior Reviewer, and D3 Knowledge Graph. |
| 2026-07-28 | Phase 4 | Overhauled UI wireframe into an Executive Knowledge Hub with Figma-quality cards and dark obsidian theme. |
| 2026-07-29 | Phase 5 | Added persistent Light/Dark mode theme engine with Vercel monochrome palette and soft slate Light Mode contrast rules. |
