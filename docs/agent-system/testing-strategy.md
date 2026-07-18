# Testing Strategy

## Current stack

Tests use JUnit 5, AssertJ, Mockito, Spring Test/MockMvc, Spring Security test support, Spring Boot Testcontainers, PostgreSQL 18, and Redis 7. `TestcontainersConfiguration` exposes both stores with `@ServiceConnection`. There is no H2 dependency.

Current coverage:

- `SpringSecurityIdentityProviderTest`: provider-neutral JWT claim mapping and missing authentication;
- `PresenceServiceTest`: Redis read failure becomes `UNKNOWN`;
- `CurrentProfileControllerIntegrationTests`: 401s, first/repeated provisioning, onboarding/replacement, username conflict, public privacy, incomplete/missing profile, heartbeat auth/TTL/online/offline;
- `OpenApiDocumentationIntegrationTests`: REST paths, operation IDs, security schemes, response schemas, planned WebSocket group, and Swagger UI;
- `OwlnestBackendApplicationTests`: full context startup.

## Test selection

- Plain JUnit unit test for domain/value/codec/service logic without framework lifecycle.
- Focused Spring MVC, security, JPA, or Redis slice when it exercises the required boundary with less context.
- `@SpringBootTest` for cross-layer behavior, Flyway/JPA parity, security filter chain, generated OpenAPI, and store integration.
- PostgreSQL Testcontainers for SQL, constraints, locking, transactions, migrations, native queries, and concurrency. Never substitute H2.
- Redis Testcontainers for serialization, TTL/expiry, invalidation, failure, and concurrency.
- Runtime/API smoke against Keycloak/Compose when mock JWT cannot prove issuer/client/network behavior.

Use `Test` or `Tests` suffix and behavior names such as `rejectsExpiredToken()`. Add regression coverage for every corrected defect. Prefer test data isolated by unique subjects/UUIDs; explicitly clean shared Redis/database state if a future test cannot remain isolated.

## Evidence workflow

1. Before changes: record Git status, focused baseline, full `./gradlew test`, environment limitations, and existing failures.
2. Map every acceptance criterion to unit/slice/integration/contract/security/concurrency/manual verification.
3. When practical, demonstrate that a new regression test fails for the intended reason before implementation.
4. After each work package: run focused tests and record exact command/result.
5. After implementation: independent API QA, full regression, `./gradlew build`, agent-system validation, `git diff --check`, and complete diff audit.

Gradle 9.5.1 must launch on Java 17+ and then resolves Java 21. A stale Java 11 launcher failure occurs before project compilation and must be reported as environment/baseline evidence, not a code failure.

## Current gaps

No formatter, linter, coverage threshold, architecture-test library, migration-only test task, performance suite, or live Keycloak automated test is configured. Do not claim these checks ran. Add infrastructure only for an approved need and keep it proportionate.
