# ADR 0001: Evolve Groundwork into an Engineering Evidence Platform

- Status: Accepted
- Date: 2026-08-24

## Context

Groundwork already provides multi-tenant document ingestion, hybrid retrieval, grounded answers, comparisons, extraction, and knowledge-graph views. General document chat and enterprise search are crowded categories, and manual uploads keep the product outside the engineering workflow where context is needed.

AI-assisted development increases the amount of code that humans must understand and verify. Reviewers need traceability from intent through implementation, tests, policy, approval, deployment, and release evidence.

## Decision

Groundwork will become the evidence and release-readiness layer for human- and AI-authored software changes. Documents remain supported as evidence artifacts. The first native workflow is a GitHub pull-request check that combines deterministic analysis, cited AI suggestions, explicit policies, and immutable release records.

## Consequences

- Existing retrieval and document capabilities are retained and generalized.
- GitHub is the first connector; Jira and Confluence follow the proven vertical slice.
- Chat is a secondary evidence-exploration surface rather than the product landing page.
- Deterministic facts and AI suggestions have separate data contracts and visual treatment.
- Product success requires user and operational evidence, not only feature completion.
