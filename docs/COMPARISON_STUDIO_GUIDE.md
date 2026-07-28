# ⚔️ Groundwork v2 — Multi-Document Comparison Studio Guide

---

## 📌 1. Overview & Purpose

The **Multi-Document Comparison Studio** is Groundwork v2's **AI-powered Semantic Diffing & Conflict Detection Engine**. 

Moving beyond naive character-by-character text diffing (such as `git diff` or standard text comparers), the Comparison Studio compares **two documents** (`Document A: Base` vs `Document B: Target`) based on **mathematical vector semantics and logical meaning**.

### 🎯 Core Capabilities:
1. **Semantic Similarity Calculation:** Computes a 0–100% structural alignment percentage score between two document versions.
2. **Clause Categorization:** Automatically categorizes text differences into 3 clear operational types:
   - 🟢 **Added Clauses (`+`)**: New requirements, features, or configurations introduced in Document B.
   - 🔴 **Removed Clauses (`-`)**: Deprecated, dropped, or missing requirements from Document A.
   - 🟧 **Conflicting Clauses (`~ Divergence`)**: Direct logical contradictions between Document A and Document B.
3. **AI Conflict Resolution & Synthesis:** Generates a senior-engineer narrative explaining *why* the documents differ, *what will break*, and *how to resolve the conflict*.

---

## ❓ 2. Why Does This Feature Exist?

### The Core Enterprise Problem: Documentation Drift
In enterprise software engineering, product design, legal compliance, and architecture, **documentation constantly drifts apart**:
* **Architecture Specs vs. Implementation:** An architect drafts an API contract specification (`Doc A`), but weeks later the development team implements something slightly different (`Doc B`).
* **Version Upgrades & Breaking Changes:** System specs evolve from v1 to v2. Identifying breaking changes manually across 50-page PDFs is slow, expensive, and error-prone.
* **Vendor Contract & RFP Audits:** Comparing competing vendor proposals (`Vendor_A_Quote.pdf` vs `Vendor_B_Quote.pdf`) to spot hidden SLA discrepancies or pricing caps.

### Why Standard Text Diffing Tools (`git diff`) Fail:
Standard text comparers compare **literal characters**:
- If `Doc A` states *"Must use HTTPS encryption"* and `Doc B` states *"SSL/TLS encryption is required"*, standard `git diff` flags the entire line as **DELETED** and **ADDED**, even though both lines mean **the exact same thing**.
- Standard text comparers **cannot detect logic contradictions**. If `Doc A` specifies `Vector Metric: Cosine Distance` and `Doc B` specifies `Vector Metric: Inner Product Dot Distance`, standard diff tools will not alert you that this divergence will break your production database search engine!

Groundwork's Comparison Studio solves this by analyzing **semantic meaning and structural logic**, flagging true contradictions while ignoring trivial rephrasings.

---

## 🔬 3. Component Breakdown & UI Visualizer Guide

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ DOCUMENT A (BASE)           DOCUMENT B (TARGET)           COMPARISON MODE             │
│ [ D2O_SHARED_HMI_API... ▼ ]  [ D2O_SHARED_HMI_API... ▼ ]  [ ⚡ Semantic & Requirement... ▼] │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### 1. Document Input Selectors
* **`DOCUMENT A (BASE)`**: The reference benchmark document (e.g., initial specification contract, API v1, or design standard).
* **`DOCUMENT B (TARGET)`**: The new or candidate document being evaluated against Document A (e.g., implementation code, API v2, or updated vendor proposal).
* **`COMPARISON MODE`**:
  * **Semantic & Requirement Diff**: Focuses on business rules, concurrency limits, and requirements.
  * **API & Technical Spec Diff**: Focuses on HTTP paths, request parameters, JSON return types, and database engines.
  * **Conflict & Risk Audit**: Scans strictly for contradictions that introduce security vulnerabilities or operational risks.

---

### 2. KPI Metric Cards

| Metric Card | Meaning & Purpose |
|---|---|
| 🔵 **`SEMANTIC SIMILARITY`** | Overall structural alignment percentage (e.g., `84.5% match`). |
| 🟢 **`ADDED CLAUSES (+)`** | Count of new requirements or sections introduced in Document B. |
| 🔴 **`REMOVED CLAUSES (-)`** | Count of requirements present in Document A that were dropped in Document B. |
| 🟧 **`CONFLICTING CLAUSES`** | **Critical Indicator:** Count of direct semantic contradictions between Document A and B. |

---

### 3. Inline Diff Visualizer (Color-Coded Lines)

```diff
- 1.2 Max query batch size default limit is capped at 10 concurrent requests.
+ 1.2 Max query batch size default limit is scaled to 50 concurrent requests with Redis token bucket.
~ 2.1 [DIVERGENCE] Doc A specifies cosine similarity distance; Doc B mandates inner product dot distance.
```

* 🔴 **Red Lines (`.diff-line-remove`)**: Clauses present in Document A that were dropped or removed in Document B.
* 🟢 **Green Lines (`.diff-line-add`)**: New clauses or specifications introduced in Document B.
* 🟧 **Amber Lines (`.diff-line-mod`)**: **Direct Contradictions!** Highlights exact semantic divergences between Doc A and Doc B (e.g., Cosine Distance vs Inner Product Distance).

---

### 4. AI Conflict Resolution & Synthesis Box
Renders an executive summary synthesized by Spring AI / Gemini:
- Summarizes the root cause of the document divergence.
- Explains the operational impact of conflicting clauses.
- Provides a recommended consensus resolution for the engineering team.

---

## 💼 4. Real-World Enterprise Use Cases

### 🏢 Use Case 1: Architecture Spec vs. Implementation Audit
* **Scenario:** A team drafts `API_Contract_v1.md` restricting query batch size to 10 concurrent requests. The engineering team writes `API_Implementation.md` scaling batch size to 50.
* **Result:** Comparison Studio flags `Section 1.2` in green/red and highlights the divergence before deployment.

### 🔌 Use Case 2: API Version Upgrades & Breaking Changes
* **Scenario:** Upgrading a platform service from `API_v1.0.pdf` to `API_v2.0.pdf`.
* **Result:** Instantly lists all deprecated endpoints, parameter schema modifications, and auth mechanism changes.

### 📄 Use Case 3: Vendor Proposal & RFP Evaluation
* **Scenario:** Comparing competing proposals (`Vendor_A_Proposal.pdf` vs `Vendor_B_Proposal.pdf`).
* **Result:** Highlights pricing caps, backup SLA differences, and missing compliance clauses.

### 🛡️ Use Case 4: Security & Regulatory Compliance Audits
* **Scenario:** Comparing internal system design docs against updated ISO 27001 / SOC2 security mandates.
* **Result:** Identifies non-compliant technical practices (e.g., missing RBAC parameters or unencrypted data paths).

---

## 📡 5. REST API Technical Reference

### Execute Document Comparison (`POST /api/compare`)

#### Request Payload:
```json
{
  "docATitle": "D2O_SHARED_HMI_API_CONTRACT.md",
  "docBTitle": "D2O_SHARED_HMI_API_IMPLEMENTATION.md",
  "mode": "semantic"
}
```

#### Response Payload:
```json
{
  "id": "e4f81a02-83b1-4c22-921d-91024bc10214",
  "docATitle": "D2O_SHARED_HMI_API_CONTRACT.md",
  "docBTitle": "D2O_SHARED_HMI_API_IMPLEMENTATION.md",
  "similarityScore": 84.5,
  "addedCount": 4,
  "removedCount": 2,
  "conflictCount": 1,
  "summary": "Comparison between Doc A and Doc B shows 84.5% structural alignment. Key discrepancy identified in Section 2.1 regarding vector distance metric.",
  "differences": [
    {
      "type": "REMOVED",
      "section": "1.2",
      "text": "Max query batch size default limit is capped at 10 concurrent requests."
    },
    {
      "type": "ADDED",
      "section": "1.2",
      "text": "Max query batch size default limit is scaled to 50 concurrent requests with Redis token bucket."
    },
    {
      "type": "DIVERGENCE",
      "section": "2.1",
      "text": "Doc A specifies cosine similarity distance; Doc B mandates inner product dot distance."
    }
  ]
}
```
