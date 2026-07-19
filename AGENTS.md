# Repository Guidelines

## Root Orchestration

The main Codex thread is the root Orchestrator. It may route work to the 25 project agents in `.codex/agents/`; agents are direct children and must not spawn further agents. Project configuration and policy allow no more than three child agents active at once. Parallelize only independent read-only work.

Before the first delegation, record `MASTER_REQUEST`, `DELIVERY_TIER`, active-time target/checkpoint, expected normal subagent-pass range, current pass count, affected surfaces, assumptions, and the smallest justified route. One pass is one started subagent turn, including a follow-up or re-review; a timeout or partial result still counts for visibility.

| Delivery tier | Typical scope | Active-time target/checkpoint | Normal subagent passes |
| --- | --- | --- | --- |
| `FAST` | one known-pattern endpoint or CRUD, existing JWT boundary, routine additive PostgreSQL change, no new ownership/integration/concurrency model | target 30 minutes; checkpoint if exceeded | 2-3 |
| `STANDARD` | several related use cases or a non-routine ownership, data, or compatibility rule | target 60 minutes; checkpoint by 90 | 3-5 |
| `EXCEPTIONAL` | WebSocket/external integration, Redis or multi-store consistency, difficult concurrency, major new module, or deployment change | target 90 minutes; checkpoint by 120 | up to 7 |

Trivial documentation or obvious one-line work stays in the root and normally uses no subagent. Time and pass numbers are planning signals, not permission to ship broken work or mandatory stop conditions. At a checkpoint, report completed work, evidence, remaining blocking risks, and the smallest next step; stop optional polishing. Continue a bounded, targeted correction automatically when a blocking correctness, security, data, test, or architecture finding remains. User approval is required only for scope expansion, a materially different solution, or work beyond the two permitted blocking-finding correction cycles.

Apply these routing rules:

1. Clear conversational or dictated wording does not by itself require analysts. The root may write concise acceptance criteria. Use `business_analyst` only for material product ambiguity and `requirements_acceptance_analyst` only when several rules need independent traceability.
2. The root inspects Git status, relevant instructions, the nearest implementation, and focused baseline evidence. Use either `project_rules_scout` or `repository_domain_explorer` only when that work is large enough to benefit; use both only for distinct unresolved questions.
3. Invoke a specialist only for a concrete risk that the writer and final reviewer cannot cover from an accepted pattern. Routine bearer authentication is not a new security design. A routine additive migration is not automatically a four-agent data workflow.
4. Use `spring_backend_developer` as the sole normal production Java/Flyway writer. For `FAST` work the same writer may add focused tests; add `backend_test_engineer` only when test design is independently complex.
5. Require independent security review for new authorization/ownership, sensitive-data, token/configuration, upload, or public-boundary behavior. Require data/migration specialists for destructive or compatibility-sensitive transitions, backfills, risky locks, or difficult concurrency. Redis, external integration, and deployment changes retain their applicable specialist gates.
6. New modules or major restructuring retain the skeleton architecture gate. A known-pattern endpoint inside an existing module does not.
7. Run one initial independent review. A finding is blocking only when it invalidates an acceptance criterion, runtime correctness, security/ownership, data safety, required architecture, compilation, migration, or a required test. Fix blocking findings and revalidate only the affected evidence. One targeted correction cycle is normal; a second is allowed only when a blocking `CRITICAL`/`HIGH` finding remains. After two unsuccessful targeted cycles, stop with evidence and ask the user instead of looping.
8. `MEDIUM`/`LOW` suggestions that do not affect the conditions above are advisory. They may be applied inside an already-active writer pass when trivial, otherwise record them as follow-up and finish. They must not trigger another writer pass, reviewer pass, QA lane, full suite, or release cycle. Reuse the same reviewer context when targeted revalidation is actually required; otherwise the root verifies the affected evidence.
9. The root performs the final evidence/diff check for `FAST` and ordinary `STANDARD` work. Add `release_gatekeeper` for `EXCEPTIONAL`, deployment-impacting, irreversible-data, new security-boundary, or otherwise high-risk work.

Every subagent assignment must be one bounded pass with a concrete question, owned files or read-only scope, evidence paths, a target shorter than the configured 15-minute stalled-job guardrail, and concise required output. A timed-out pass returns partial evidence; it may be continued only for remaining blocking work, not optional refinement. Agents must not broaden scope or begin another cycle. Prefer safe assumptions over blocking questions; ask the user only when behavior, destructive action, or scope expansion cannot be chosen safely.

Finish as soon as acceptance criteria are met, relevant focused verification passes, required architecture boundaries hold, and no blocking finding remains. Run the broadest justified suite at most once after the final blocking correction. Never report an unexecuted command as passed; distinguish pre-existing failures from introduced ones. Detailed routing and quality-based stop rules live in `docs/agent-system/workflow-routing.md`; new project agent configuration loads reliably in a new session.

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
