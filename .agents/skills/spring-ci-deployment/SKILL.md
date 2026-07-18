---
name: spring-ci-deployment
description: Design or change OwlNest CI, container build, environment, migration, health, smoke, deployment, and rollback behavior proportionately.
---

# Spring CI and Deployment

Use for CI workflows, Docker/runtime changes, hosted deployment, environment/secrets, health checks, or release automation.

## Current evidence

There is no checked-in CI workflow or hosted deployment target. `Dockerfile` builds with Eclipse Temurin 21, runs on a non-root `owlnest` user, and exposes 8080. `compose.yaml` provides PostgreSQL 18, Redis 7, Keycloak 26.7.0, and an optional `full-stack` backend. `setup.sh` validates, rebuilds, starts, waits, configures Keycloak, and generates the ignored Postman environment. Actuator is present and health is public, but the backend container has no explicit Compose healthcheck. These gaps are `NEEDS_CONFIRMATION`, not permission to select a platform.

## Workflow

Route deployment-impacting work to `observability_production_reviewer` and `ci_deployment_engineer`. Confirm target, environments, artifact/container registry, deployment model, migration timing, rolling compatibility, secrets provider, TLS/hostnames, PostgreSQL/Redis/Keycloak ownership, health/readiness, smoke tests, rollback/forward-fix, backups, and operational owner.

CI should use the checked-in wrapper, a Java 21 toolchain, `./gradlew test`/`build`, Testcontainers-capable runners, agent-system validation, and `git diff --check` where relevant. Cache only safe Gradle artifacts; never cache or print credentials. Supply secrets via protected environment variables or an external secret manager. Keep Kubernetes, Helm, Terraform, service mesh, and similar infrastructure out until a concrete requirement justifies them.

Deployment evidence must include build artifact identity, migration result, health and smoke result, configuration inputs (names only), and rollback readiness.
