# Repository Guidelines

## Project Structure & Module Organization

This is a Java 21 Spring Boot modular monolith. Keep one Gradle application initially, but organize production code under `src/main/java/dev/dkutko/owlnest` as feature-first logical modules. Configuration belongs in `src/main/resources`, with Flyway migrations in `src/main/resources/db/migration`. Tests mirror production packages under `src/test/java`.

## Build, Test, and Development Commands

- `./gradlew bootRun` starts the application; Docker Compose supplies PostgreSQL.
- `./gradlew test` runs JUnit 5 tests through Testcontainers.
- `./gradlew build` compiles the project, runs tests, and creates the application artifact under `build/libs`.
- `./gradlew clean` removes generated build output.
- `docker compose up -d` starts PostgreSQL manually; use `docker compose down` when finished.
- `docker compose --profile full-stack up --build` runs the backend and PostgreSQL as containers.

Before the first run, copy `.env.example` to `.env`. Docker must be running for development and integration tests. Use the checked-in wrapper instead of system Gradle.

## Coding Style & Naming Conventions

Use four-space indentation, one public top-level type per file, and no wildcard imports. Use lowercase packages, `UpperCamelCase` classes, and `lowerCamelCase` methods and fields. Name components by responsibility, such as `UserController` or `UserRepository`. Prefer constructor injection. No formatter or linter is configured; match nearby code and apply IDE formatting.

## Testing Guidelines

Use JUnit 5 and Spring Boot's focused test support where possible; reserve `@SpringBootTest` for application-level integration coverage. Name test classes with a `Test` or `Tests` suffix and test methods after observable behavior, for example `rejectsExpiredToken()`. Add regression tests with every bug fix. Run `./gradlew test` before opening a pull request.

## Commit & Pull Request Guidelines

Git history is unavailable, so no established pattern can be confirmed. Use focused commits with imperative subjects such as `Add user profile endpoint`. Pull requests must explain the change and verification, link issues, and call out migrations, configuration, or API contract changes. Include request/response examples for endpoints.

## Security & Configuration

Never commit credentials or `.env`. Keep safe placeholders in `.env.example` and supply secrets through environment variables or an external secret manager. Pin `postgres:latest` before production use.

## Agent-Specific Collaboration

Read `docs/project-context.md` before non-trivial work. Work in small vertical slices, explain choices and alternatives, and avoid premature infrastructure. Before implementation, document the file plan, dependency use, tradeoffs, annotations, and verification. For each new Spring or Jakarta annotation, explain its purpose, processor, lifecycle timing, scope, and pitfalls.
