# ADR 0002: Use a Modular Monolith and PostgreSQL Outbox

- Status: Accepted
- Date: 2026-08-24

## Context

The existing application is a Spring Boot service backed by PostgreSQL and Redis. The new connector and analysis workflow needs idempotent webhooks, durable background work, replay, leases, and failure recovery. It does not yet have measured traffic requiring independently scaled services or a dedicated broker.

## Decision

Keep one Spring Boot deployable with explicit evidence-platform packages. Persist accepted webhook deliveries and an outbox event in one transaction. Leased workers normalize events and enqueue versioned analysis jobs using `FOR UPDATE SKIP LOCKED`.

PostgreSQL remains the source of truth for artifacts, versions, relationships, jobs, policies, and releases. Redis remains an optional cache/rate-limit optimization.

## Consequences

- Delivery acknowledgement is decoupled from external API and AI latency.
- Accepted events survive process restarts and can be replayed safely.
- Transactions and tenant joins remain simple and inspectable.
- Kafka or service extraction requires a later ADR backed by queue throughput, replay, isolation, or independent-scaling measurements.
