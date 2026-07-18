---
name: redis-consistency-workflow
description: Decide and validate an OwlNest Redis use case with explicit key, value, TTL, invalidation, fallback, concurrency, and consistency rules.
---

# Redis Consistency Workflow

Use for Redis, caching, ephemeral state, rate limiting, locks, pub/sub, streams, sessions, or idempotency keys. The first decision is whether Redis is needed.

## Established behavior

The only implemented use is ephemeral online presence:

- key `presence:account:{accountId}`;
- value `Instant.toString()` through `StringRedisTemplate`;
- TTL 90 seconds, refreshed by a 30-second foreground heartbeat contract;
- Redis 7 Alpine with persistence disabled and no volume;
- `POST /api/v1/presence/heartbeat` returns 503 with `presence.unavailable` when Redis writes fail;
- public profile reads degrade to `PresenceStatus.UNKNOWN` when Redis reads fail;
- absent/expired key means `OFFLINE`;
- PostgreSQL account/profile data remains authoritative.

Do not infer a general cache, session, lock, or messaging policy from presence.

## Required design

Route to `redis_cache_architect`, `transaction_consistency_reviewer`, and `integration_resilience_reviewer`; add performance review for high volume, locks, streams, pub/sub, or contention. Require use-case classification, key namespace/cardinality, value schema/serializer, TTL/refresh, eviction, invalidation, source of truth, read/write sequence, stale/flushed/unavailable behavior, fallback, duplicates, stampede prevention, multi-instance behavior, sensitive-data classification, metrics, and cleanup.

Approve the plan before code. Test with Redis Testcontainers: TTL, expiry, refresh, miss, stale strategy, failure behavior, serialization compatibility, concurrency, and PostgreSQL/Redis partial failure. Never claim atomic cross-store behavior unless a real protocol establishes it.
