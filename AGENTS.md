# Repository Guidelines

## Project Structure & Module Organization

This Java 21 Spring Boot modular monolith uses one Gradle application with feature-first modules under `src/main/java/dev/dkutko/owlnest`. Keep configuration in `src/main/resources`, Flyway migrations in `src/main/resources/db/migration`, and matching tests under `src/test/java`.

## Build, Test, and Development Commands

- `./gradlew bootRun` starts the application with Docker Compose dependencies.
- `./gradlew test` runs JUnit 5 and Testcontainers tests.
- `./gradlew build` tests and creates the artifact under `build/libs`.
- `docker compose up -d` starts PostgreSQL and Keycloak manually; use `docker compose down` when finished.
- `./setup.sh` stops, rebuilds, and starts the complete backend, PostgreSQL, and Keycloak stack.

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
