# Profile and Online Presence Implementation Plan

**Status:** Implemented and verified on 2026-07-17.

## Goal

Complete the first profile slice and add a minimal, explicit online-presence capability without coupling session state to Keycloak authentication.

Authentication answers who the caller is. Presence answers whether at least one OwlNest client for that account has reported recent foreground activity. Keycloak remains the sole owner of credentials and token issuance; PostgreSQL remains the source of truth for accounts and profiles; Redis stores only short-lived presence data.

Google identity brokering is intentionally deferred until the core social features are complete.

## API Contract

### Current profile

`GET /api/v1/profile/me` continues to return the complete private profile for the authenticated account.

`PUT /api/v1/profile/me` continues to create or fully replace OwlNest-owned profile fields. It is used both for initial onboarding and later editing.

### Public profile

`GET /api/v1/profiles/{accountId}` returns an onboarded user's public profile to an authenticated caller:

```json
{
  "accountId": "a93dddbc-0f4a-4bd7-a42b-3656a4f6c954",
  "username": "john.doe",
  "displayName": "John Doe",
  "bio": "Building OwlNest",
  "presenceStatus": "ONLINE"
}
```

Email, email-verification state, birth date, and gender are private and are never included in this response. A missing or incomplete profile returns `404`.

### Presence heartbeat

`POST /api/v1/presence/heartbeat` requires a valid bearer token and returns `204 No Content`. The authenticated account is marked online for 90 seconds. Flutter should call it immediately when the application enters the foreground and every 30 seconds while foregrounded.

Presence values are:

- `ONLINE`: the Redis key exists;
- `OFFLINE`: the key has expired or was never created;
- `UNKNOWN`: Redis is unavailable while a profile is being read.

A heartbeat returns `503` when Redis is unavailable. Core authentication and profile editing must continue to work without Redis.

## Storage and Runtime

Redis runs as an ephemeral local container on port `6379`. No Redis volume is used because online status must naturally disappear after restarts.

Each heartbeat writes one key:

```text
presence:account:{accountId} -> last activity timestamp, TTL 90 seconds
```

No passwords, tokens, email addresses, or profile documents are stored in Redis.

## Implemented Files and Dependencies

- Add `spring-boot-starter-data-redis`; use its default Lettuce client and auto-configured `StringRedisTemplate`.
- Add the Redis service and backend connection settings to `compose.yaml`, `.env.example`, and `setup.sh`.
- Add a logical `presence` feature with `controller`, `service`, and `repository` packages.
- Keep the repository contract in `PresenceRepository` and the Redis-backed implementation in `RedisPresenceRepository`.
- Add a public-profile query service and controller under the existing `profile` feature.
- Add a Redis `GenericContainer` service connection for integration tests.
- Update OpenAPI tests, feature documentation, and architecture documentation.

## Alternatives and Tradeoffs

- **Keycloak login/logout events — rejected.** A successful login does not prove that the client is still active, and access tokens can outlive the foreground application session.
- **PostgreSQL-only presence — rejected.** Frequent heartbeats would create unnecessary durable writes and cleanup work.
- **WebSocket-only presence — deferred.** It will be a good signal after live messaging exists, but introducing the messaging protocol solely for presence would enlarge this slice.
- **Per-device keys — deferred.** One account-level TTL key correctly represents “at least one recently active client” because every active device refreshes the same key. No explicit logout deletion is performed, avoiding false offline status when another device remains active.

The chosen REST heartbeat is small and testable, but it requires Flutter integration and presence can lag real activity by up to the TTL.

## New Annotation Behavior

- `@ResponseStatus(HttpStatus.NO_CONTENT)` is processed by Spring MVC when the heartbeat controller returns successfully and sets the HTTP status to `204`; it belongs on this no-body endpoint, not on service classes.
- `@ServiceConnection(name = "redis")` is processed only by Spring Boot's test support. It derives Redis connection details from the Testcontainers bean and overrides normal connection properties for the test application context. It must not appear in production configuration.
- Existing `@Service`, `@Repository`, `@RestController`, `@RestControllerAdvice`, and transactional annotations keep their established project roles; no `@RedisHash` entity lifecycle is introduced.

## Verification

- Integration tests cover authorization, safe public fields, missing/incomplete profiles, profile replacement, heartbeat TTL, online status, and offline status.
- OpenAPI tests assert both new endpoints and their response contracts.
- `./gradlew test` verifies the complete application with PostgreSQL and Redis Testcontainers.
- `./setup.sh` rebuilds the current source and starts PostgreSQL, Redis, Keycloak, and the backend.
- Runtime checks verify backend health, Redis `PING`, Keycloak discovery, and the Docker port mapping.
