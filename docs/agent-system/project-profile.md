# OwlNest Project Profile

**Evidence snapshot:** 2026-07-18. Re-run discovery after structural or dependency changes.

## Repository state

OwlNest Backend is one Java application in one Gradle project. Feature packages use `controller/service/domain/repository` responsibility names, with explicit technical packages such as `identity.security`; the implemented `presence` feature uses Redis only for ephemeral status. Agent workflows must inspect and preserve any user-owned working-tree changes they encounter rather than assuming a clean baseline.

## Runtime and build

| Item | Established value | Evidence |
| --- | --- | --- |
| Java language/toolchain | 21 | `build.gradle.kts` |
| Gradle wrapper | 9.5.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Spring Boot | 4.1.0 | `build.gradle.kts`; resolved runtime graph |
| Spring Framework | 7.0.8 | resolved `runtimeClasspath` |
| Spring Security | 7.1.0 | resolved `runtimeClasspath` |
| Hibernate ORM | 7.4.1.Final | resolved `runtimeClasspath` |
| Flyway | 12.4.0 | resolved `runtimeClasspath` |
| Lettuce | 7.5.2.RELEASE | resolved `runtimeClasspath` |
| PostgreSQL runtime/container | JDBC driver managed by Boot; PostgreSQL 18 container | `build.gradle.kts`, `compose.yaml`, `TestcontainersConfiguration` |
| Redis runtime/container | Spring Data Redis/Lettuce; Redis 7 Alpine | `build.gradle.kts`, `compose.yaml`, `TestcontainersConfiguration` |
| Keycloak | 26.7.0 local container | `compose.yaml` |

The shell may start Gradle under stale Java 11 even though Java 17 is installed. Gradle 9.5.1 requires a Java 17+ launcher and then resolves the declared Java 21 toolchain. A historical pre-bootstrap `./gradlew test --no-daemon` failure was caused by that launcher mismatch. For the current snapshot, the wrapper was started with the installed Java 17 launcher and the full 106-test PostgreSQL/Redis Testcontainers suite passed in 51 seconds.

## Architecture

The accepted style is a feature-first modular monolith: one deployable Spring Boot process, one Gradle build, one PostgreSQL database, and logical feature roots under `src/main/java/dev/dkutko/owlnest`. `ADR-0001` is accepted. The current package convention inside a feature is `controller`, `service`, `domain`, and `repository`, created only as needed; `security` is an explicit technical package inside `identity`. `foundation` owns cross-cutting OpenAPI configuration.

Implemented feature roots:

- `identity`: validated JWT mapping, local account provisioning, account persistence, and the HTTP security filter chain;
- `profile`: private/current and public profile use cases, DTOs, validation, domain behavior, and PostgreSQL persistence;
- `presence`: authenticated heartbeat and Redis-backed short-lived status;
- `post`: authenticated single-post CRUD/card projection, ordered labels/media references, soft delete, and idempotent like/bookmark/repost interactions;
- `foundation.openapi`: REST and planned-WebSocket OpenAPI groups.

`feed`, `socialgraph`, managed media storage, comments persistence, `messaging`, and `notification` remain roadmap concepts. `docs/features/posts-architecture-plan.md` documents the implemented single-post slice and explicitly excludes feed/comments endpoints.

Controllers delegate to service classes and return DTO records. Services orchestrate use cases and normally own transaction boundaries. Services depend on small project repository interfaces; `*RepositoryImpl` delegates to a package-private Spring Data interface. `Account` and `Profile` are both domain objects and JPA entities—an accepted pragmatic simplification rather than a separate persistence model.

## Domain and data ownership

Keycloak owns credentials, registration, verification, reset, tokens, and sessions. PostgreSQL owns local identity mappings and product profiles. Redis owns only ephemeral presence.

`identity_account` uses a UUID primary key and a unique `(provider, external_subject)` mapping. `profile.account_id` is both primary key and `ON DELETE CASCADE` foreign key to the local account. Username uniqueness is enforced case-insensitively through `uq_profile_username_lower`. Profile onboarding adds optional birth date/gender and a non-null completion flag.

Current domain behavior includes account creation/claim refresh, default profile creation, full profile onboarding/replacement, incomplete-profile hiding, safe public projections, presence status degradation, post CRUD/soft deletion, and idempotent post interactions. Feed and comments persistence remain future rules.

## API conventions

Implemented business routes are:

| Method | Path | Operation ID | Storage |
| --- | --- | --- | --- |
| GET | `/api/v1/profile/me` | `getCurrentProfile` | PostgreSQL |
| PUT | `/api/v1/profile/me` | `completeProfileOnboarding` | PostgreSQL |
| GET | `/api/v1/profiles/{accountId}` | `getPublicProfile` | PostgreSQL + Redis status |
| POST | `/api/v1/presence/heartbeat` | `heartbeatPresence` | PostgreSQL provisioning + Redis |
| POST | `/api/v1/posts` | `createPost` | PostgreSQL |
| GET | `/api/v1/posts/{id}` | `getPost` | PostgreSQL |
| PUT | `/api/v1/posts/{id}` | `replacePost` | PostgreSQL |
| DELETE | `/api/v1/posts/{id}` | `deletePost` | PostgreSQL |
| PUT / DELETE | `/api/v1/posts/{id}/likes` | `setPostLiked` / `clearPostLiked` | PostgreSQL |
| PUT / DELETE | `/api/v1/posts/{id}/bookmark` | `setPostBookmarked` / `clearPostBookmarked` | PostgreSQL |
| PUT / DELETE | `/api/v1/posts/{id}/repost` | `setPostReposted` / `clearPostReposted` | PostgreSQL |

Spring MVC controllers use request/response records, Jakarta Bean Validation, Swagger annotations, and feature-scoped `@RestControllerAdvice`. Expected feature errors use `ProblemDetail` plus a stable `code`. Standard Spring Security handles missing/malformed bearer tokens. REST changes synchronize controller annotations, OpenAPI integration tests, Postman, and feature docs. The empty `WebSocket API (planned)` OpenAPI group is a boundary marker; AsyncAPI will own realtime contracts.

## Persistence and transactions

Flyway SQL in `src/main/resources/db/migration` is the schema source. Hibernate uses `ddl-auto: validate`; Open Session in View is disabled. Applied migrations are forward-only and must not be rewritten.

`@Transactional` appears on public identity/profile/post use-case methods; read-only query services declare `readOnly = true` when they cannot provision state. Default Spring propagation lets account provisioning join an outer transaction when invoked through its separate bean proxy. Existing-post writes use a shared pessimistic post-row lock, while composite interaction keys and affected-row checks keep relation transitions and counters consistent.

**NEEDS_CONFIRMATION:** `GetPublicProfileService.getByAccountId` calls `PresenceService`/Redis before the read-only PostgreSQL transaction returns. Future work should decide whether this external store read belongs outside the database transaction. The bootstrap does not alter behavior.

The repository has no `@Version`, retry framework, outbox, or general idempotency layer. Posts use an explicit pessimistic lock; database unique constraints remain the final defense for interaction, identity, and username uniqueness. First account/profile creation uses miss-only transaction advisory locks with rechecks; established paths stay lock-free.

## Redis

`RedisPresenceRepository` uses `StringRedisTemplate` with `presence:account:{accountId}` keys, `Instant.toString()` values, and a 90-second TTL. Flutter is expected to refresh every 30 seconds while foregrounded. Redis has 2-second connect/command timeouts, no persistence, and no volume. Heartbeat write failure maps to `503 presence.unavailable`; public-profile read failure degrades to `UNKNOWN`; missing/expired keys are `OFFLINE`.

There are no cache annotations, Redis repositories, sessions, locks, rate limits, pub/sub, streams, or other namespaces. Redis is not a source of truth.

## Security

`SecurityConfiguration` disables CSRF, form login, HTTP Basic, logout, request cache, and server sessions. Health, error, Swagger UI, and OpenAPI are public; `/api/v1/**` requires authentication; other routes are denied. JWT issuer, audience `owlnest-api`, and JWK URL are configured separately. Post replace/delete enforce local author ownership in their transactional services; no method-security roles or moderation roles exist yet.

The local realm allows registration, keeps email verification disabled until SMTP exists, and defines bearer-only API plus public Flutter, Postman, and Swagger clients. Direct Access Grant exists only for the local Postman client.

## Testing

JUnit 5 tests use behavior names and `Test`/`Tests` suffixes. Full-context `@SpringBootTest` + MockMvc covers security, API behavior, PostgreSQL persistence, Redis TTL/status, and generated OpenAPI. Plain unit tests cover JWT claim mapping and Redis read degradation. `TestcontainersConfiguration` provides PostgreSQL 18 and Redis 7 through `@ServiceConnection`. No H2 dependency exists.

There is no configured formatter, Checkstyle, SpotBugs, PMD, Error Prone, Sonar, ArchUnit, or code-coverage gate. IDE formatting plus compilation/tests is the current Java validation baseline.

## Observability and operations

Actuator is included and `/actuator/health` is public. Compose has dependency health checks for PostgreSQL, Keycloak, and Redis. The application has no explicit metrics/tracing/correlation/audit logging configuration, no readiness/liveness group configuration, and no custom health indicators. The Docker image is a Temurin 21 multi-stage build that runs as non-root.

There is no checked-in CI workflow and no hosted deployment target. Local operations use Docker Compose and `setup.sh`; production database, Redis, Keycloak, TLS, secrets manager, migration execution, backups, monitoring, and rollback remain **NEEDS_CONFIRMATION**.

## Documentation authority

`docs/README.md` declares the docs directory authoritative for product scope and contracts. Use this practical precedence when documents differ: current user decision and accepted ADRs; implemented migrations/configuration/code plus passing tests; implemented feature docs; Draft architecture/feature plans. `docs/architecture.md` is Draft even though its feature-first direction is also accepted by ADR-0001 and reflected in code.
