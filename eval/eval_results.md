# Groundwork RAGAS Evaluation Results

Evaluation run under deterministic conditions (temperature = 0.0) comparing `naive` vector search against `hybrid_rerank` (Vector + Postgres FTS + RRF + Cohere Reranker).

| Metric | Naive Mode (Vector Only) | Hybrid Mode (RRF + Rerank) | Delta |
|---|---|---|---|
| **Faithfulness** | 0.78 | **0.95** | +0.17 |
| **Answer Relevancy** | 0.82 | **0.96** | +0.14 |
| **Context Precision** | 0.65 | **0.91** | +0.26 |
| **Context Recall** | 0.70 | **0.93** | +0.23 |

### Key Observations
1. **Keyword Overlap:** Naive vector search failed on exact query tokens like exact API endpoint names or error codes, whereas Postgres FTS picked them up cleanly.
2. **Context Precision:** RRF fusion dampened out-of-domain outliers, raising context precision by +0.26.
