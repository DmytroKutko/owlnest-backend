# OwlNest Backend Documentation

This directory is the source of truth for product scope, architecture, feature contracts, and architectural decisions.

## Local Setup

Copy `.env.example` to `.env`, choose a local PostgreSQL password, and ensure Docker is running:

```shell
cp .env.example .env
./gradlew bootRun
```

Docker Compose reads `.env`; Spring Boot then receives database connection details through its Docker Compose integration. Spring Boot does not automatically treat `.env` as an application configuration file. Application secrets must come from environment variables or an external secret manager, never committed `application*.yaml` files.

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
