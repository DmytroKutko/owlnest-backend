# Observability and Operations

## Current baseline

- Spring Boot Actuator is a runtime dependency.
- `/actuator/health` and children are public through Spring Security.
- Compose health checks PostgreSQL with `pg_isready`, Keycloak readiness, and Redis `PING`.
- The backend waits on those dependencies in the `full-stack` profile and has an explicit Compose healthcheck against Actuator readiness.
- Redis connections have 2-second connect and command timeouts.
- `setup.sh` validates Compose, rebuilds, starts/waits, configures Keycloak, prints service mappings, and generates the ignored Postman environment.
- The Docker image uses Temurin 21 and a non-root `owlnest` user.
- JVM/test timezone is UTC.

Actuator readiness/liveness probes are enabled for Compose, but there is no explicit exposure list, health-group dependency membership, custom health indicator, Micrometer business metric, tracing, correlation ID, structured logging, audit log, graceful-shutdown setting, or alert/runbook configuration.

## Feature production gate

For substantial features, background work, Redis, integrations, or deployment changes, define:

- startup configuration validation and failure messages;
- liveness versus readiness and which dependency failures should remove readiness;
- operation counters, latency, failures, retries, queue/cache/store metrics, and cardinality limits;
- log events with correlation/request IDs and stable operation/error fields;
- redaction for credentials, bearer tokens, email, birth date, and private payloads;
- trace boundaries for database, Redis, and external calls when tracing is adopted;
- business audit events for security-sensitive state changes, distinct from debug logs;
- graceful shutdown and in-flight/background work behavior;
- dashboards/alerts/runbook only for real operational signals;
- smoke checks and diagnostic evidence for partial failures.

## Deployment uncertainties

The repository has no CI, staging/production target, production database/Redis/Keycloak topology, TLS hostnames, secrets manager, migration runner, backup/restore policy, log/metric backend, on-call owner, or rollback procedure. Every one is `NEEDS_CONFIRMATION`; agent outputs may propose options but must not present one as selected.
