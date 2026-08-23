# ADR 0003: Separate Deterministic Decisions from AI Advice

- Status: Accepted
- Date: 2026-08-24

## Context

Groundwork will analyze untrusted source content and use probabilistic models. A generated risk statement is not reliable enough to approve, reject, or block a software release without inspectable evidence.

## Decision

Changed files, links, ownership, check results, OpenAPI differences, evidence presence, hashes, and policy evaluation are deterministic. AI is used for semantic matching, summaries, potential impacts, contradictions, and reviewer questions.

Every AI finding records citations, evidence status, confidence, model/prompt/analyzer versions, and human review state. Unsupported findings cannot affect a deterministic policy result.

## Consequences

- The product remains useful when an AI provider is unavailable.
- Users can distinguish facts from suggestions.
- Evaluation reports must measure deterministic correctness separately from AI retrieval, citation, and groundedness quality.
