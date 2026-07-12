# OwlNest Backend Documentation

This directory is the source of truth for product scope, architecture, feature contracts, and architectural decisions.

## Local Setup

Copy `.env.example` to `.env`, choose a local PostgreSQL password, and ensure Docker Desktop is running.

### IntelliJ development

Select the shared `OwlNest Backend (Local)` run configuration. The backend runs as a local Java process for debugging, while Spring Boot automatically starts only the PostgreSQL service from `compose.yaml`.

The equivalent terminal command is:

```shell
cp .env.example .env
./gradlew bootRun
```

### Full Docker stack

Run both the backend and PostgreSQL as containers:

```shell
docker compose --profile full-stack up --build
```

Stop the stack without deleting database data:

```shell
docker compose --profile full-stack down
```

Docker Compose reads `.env`; Spring Boot then receives database connection details through its Docker Compose integration. Spring Boot does not automatically treat `.env` as an application configuration file. Application secrets must come from environment variables or an external secret manager, never committed `application*.yaml` files. PostgreSQL data is persisted in the named `postgres_data` volume.

The backend JVM and tests use UTC regardless of the developer machine timezone. Persist timestamps as UTC values and convert them only at the API/client presentation boundary.

## Current Documents

- [Project Context](project-context.md) — product vision and collaboration rules.
- [Architecture](architecture.md) — proposed modular architecture and delivery roadmap. Status: **Draft**.
- [Annotation Glossary](annotations.md) — annotations currently present in the codebase.
- [ADR-0001](decisions/0001-feature-first-modular-monolith.md) — accepted feature-first modular-monolith structure.

## Documentation Conventions

- Mark proposals as **Draft** until they are explicitly accepted.
- Record important choices and rejected alternatives in `docs/decisions/` as short Architecture Decision Records (ADRs).
- Add one feature document under `docs/features/` before or alongside each implementation slice.
- Keep API payload examples and business rules near the owning feature document.
- Update documentation in the same change when a contract or architectural boundary changes.
