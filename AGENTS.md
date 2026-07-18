# Repository Guidelines

## Root Orchestration

The main Codex thread is the root Orchestrator. It may route work to the 25 project agents in `.codex/agents/`; agents are direct children only and must not spawn further agents. Select the smallest team justified by task scope and risk. Do not run every agent for every task.

Before non-trivial implementation, the Orchestrator must:

1. Preserve the user's original request as `MASTER_REQUEST` and determine whether authoritative product behavior exists.
2. Invoke `business_analyst` for informal, dictated, incomplete, ambiguous, or rule-light feature ideas, then `requirements_acceptance_analyst`; record assumptions and unresolved questions explicitly.
3. Inspect Git status, current failures, accepted ADRs, relevant feature docs, reference implementations, and affected API/PostgreSQL/Redis/security/integration surfaces.
4. Produce numbered acceptance criteria and a baseline, then resolve applicable domain, application, API, data, JPA, Redis, transaction, security, resilience, observability, test, and deployment designs before implementation.
5. Use `spring_backend_developer` as the only normal production Java and Flyway writer. `backend_test_engineer` may edit tests; `ci_deployment_engineer` may edit only its declared CI/deployment surfaces.
6. For substantial new modules, review the minimum feature skeleton with `architecture_conformance_reviewer` before full implementation.
7. Launch applicable reviewers and QA independently from the writer. Route findings back to the same developer context with finding-to-fix evidence; allow at most three complete fix/review cycles by default.
8. Finish only when `release_gatekeeper` can cite observed evidence for every mandatory gate. Never report an unexecuted command as passed; distinguish pre-existing failures from introduced ones.

Routing minimums:

- Trivial change: Orchestrator, rules scout when relevant, developer, code review, focused validation.
- Informal feature: business analysis and acceptance gates before technical design.
- Standard backend feature: rules/discovery, applicable architects and contract/data specialists, tests, one developer, architecture/code review, API QA, regression QA, release gate.
- PostgreSQL/migration: data model, JPA, transaction, migration review; add performance review for volume/concurrency.
- Redis: Redis architecture, consistency, resilience, failure/stale-data tests; add performance review for contention or volume.
- Security-sensitive: independent security review plus negative, cross-user, and ownership tests.
- External integration: resilience review with explicit timeout, retry, idempotency, and partial-failure behavior.
- Deployment-impacting: observability and CI/deployment review with health, smoke, migration, and rollback evidence.

Detailed gates, routing, handoffs, and evidence contracts live in `docs/agent-system/` and the narrow skills under `.agents/skills/`. Read only the skills applicable to the current task. Treat accepted ADRs and implemented tests/code as stronger evidence than Draft plans. New project agent configuration is reliably loaded from the next Codex session; do not claim the current session reloaded it.

## Project Structure & Module Organization

This Java 21 Spring Boot modular monolith uses one Gradle application with feature-first modules under `src/main/java/dev/dkutko/owlnest`. Keep configuration in `src/main/resources`, Flyway migrations in `src/main/resources/db/migration`, and matching tests under `src/test/java`.

## Build, Test, and Development Commands

- `./gradlew bootRun` starts the application with Docker Compose dependencies.
- `./gradlew test` runs JUnit 5 and Testcontainers tests.
- `./gradlew build` tests and creates the artifact under `build/libs`.
- `docker compose up -d` starts PostgreSQL, Redis, and Keycloak manually; use `docker compose down` when finished.
- `./setup.sh` stops, rebuilds, and starts the complete backend, PostgreSQL, Redis, and Keycloak stack.

Copy `.env.example` to `.env` before the first run. Docker is required. Use the checked-in Gradle wrapper.

## Coding Style & Naming Conventions

Use four-space indentation, one public top-level type per file, and no wildcard imports. Use lowercase packages, `UpperCamelCase` classes, and `lowerCamelCase` members. Name components by responsibility and prefer constructor injection. No formatter or linter is configured; apply IDE formatting.

## Testing Guidelines

Use JUnit 5 and focused Spring test slices; reserve `@SpringBootTest` for integration coverage. Use a `Test` or `Tests` class suffix and behavior names such as `rejectsExpiredToken()`. Add regression coverage for fixes and run `./gradlew test` before a pull request.

## API Documentation

Swagger UI is at `http://localhost:8080/swagger-ui.html`. REST contract changes must update OpenAPI annotations, tests, Postman, and feature documentation together. Preserve the `REST API` and `WebSocket API (planned)` groups. Document future realtime channels with AsyncAPI, not fake REST endpoints.

## Commit & Pull Request Guidelines

Use focused imperative subjects such as `Configure Docker and PostgreSQL development stack`. Keep one concern per commit. Pull requests must explain changes and verification, link issues, call out migrations or contract changes, and include endpoint examples.

## Security & Configuration

Never commit credentials, `.env`, or generated Postman environments. Keep safe placeholders in `.env.example` and supply secrets through environment variables or an external secret manager.

## Agent-Specific Collaboration

Read `docs/project-context.md` before non-trivial work. Use small vertical slices, explain alternatives, and avoid premature infrastructure. Document plans, dependencies, tradeoffs, annotations, and verification. Explain each new Spring or Jakarta annotation's purpose, processor, lifecycle, scope, and pitfalls.
