# Redis Conventions

## Current accepted use

Redis is used only for ephemeral account presence. PostgreSQL remains authoritative for accounts, profiles, and all durable future business state.

| Property | Current contract | Evidence |
| --- | --- | --- |
| Use class | temporary presence | `presence` feature docs/code |
| Key | `presence:account:{accountId}` | `RedisPresenceRepository.KEY_PREFIX` |
| Value | ISO-8601 `Instant` text | `lastActivityAt.toString()` through `StringRedisTemplate` |
| TTL | 90 seconds | `PresenceService.ONLINE_TIME_TO_LIVE` |
| Client cadence | initial + every 30 seconds in foreground | feature doc/controller description |
| Miss/expiry | `OFFLINE` | `PresenceService.getStatus` |
| Read failure | `UNKNOWN`, profile still returned | `PresenceService.getStatus` |
| Write failure | exception -> HTTP 503 `presence.unavailable` | repository/exception advice |
| Timeouts | 2-second connect and command | `application.yaml` |
| Durability | none; no Redis volume, save/AOF disabled | `compose.yaml` |

No password, token, email, or profile document is stored in Redis. All active devices refresh the same account key. No logout deletion occurs, avoiding false offline state while another device is active.

## New-use decision gate

Every proposal must first classify the use: cache, session, rate limit, distributed lock, pub/sub, stream, temporary state, idempotency key, or other. Reject Redis unless it gives a concrete correctness/latency/load benefit that cannot be met more simply.

An approved plan must define:

- namespace, tenant/account scope, exact key fields, maximum cardinality, and cleanup;
- value schema, serializer, versioning, size, and sensitive-data classification;
- TTL source, refresh semantics, eviction assumptions, and no-TTL justification if exceptional;
- PostgreSQL/source-of-truth relationship;
- read/write sequence, cache-aside/write-through policy, invalidation triggers, and stale window;
- unavailable, slow, stale, flushed, partially inconsistent, or deserialization behavior;
- duplicate handling, stampede prevention, lock ownership/lease/renewal if applicable;
- multi-instance and concurrent request behavior;
- timeouts, retry policy, metrics/logging, and operator diagnostics;
- Testcontainers cases for TTL, expiry, refresh, failure, invalidation, serialization, concurrency, and cross-store partial failure.

Do not claim one Spring transaction makes PostgreSQL and Redis atomic. Prefer after-commit invalidation/update or explicit compensating/fallback semantics based on the approved consistency model.
