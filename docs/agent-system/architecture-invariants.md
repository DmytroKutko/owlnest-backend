# Architecture Invariants

These rules are established by accepted ADRs, current code, and implemented feature documentation. Proposed rules are labeled separately.

## Required structure

1. Keep one Java 21 Spring Boot application and one Gradle project until physical isolation has a concrete accepted reason.
2. Organize business capabilities as top-level packages under `dev.dkutko.owlnest`; do not create global root `controller`, `service`, or `repository` packages.
3. Create only feature-internal packages needed now. Do not scaffold future modules.
4. Keep HTTP mappings, request validation, DTO translation, and expected HTTP error mapping in `controller`.
5. Controllers call services, never repositories or Spring Data interfaces, and never return JPA entities.
6. Keep use-case sequencing and normal PostgreSQL transaction boundaries in public `service` methods invoked through Spring proxies.
7. Keep business state/rules in `domain`. JPA annotations on domain entities are accepted; Spring MVC, Redis, and security infrastructure do not belong there.
8. Services depend on project repository interfaces. Repository adapters hide Spring Data/JPA/Redis details.
9. Cross-feature access uses public service contracts or approved events, never another feature's repository or internal persistence type.
10. Use constructor injection in production components and no request-specific mutable singleton state.
11. Flyway is the schema authority; Hibernate validates only. Never rewrite an applied shared migration.
12. PostgreSQL is authoritative for durable data. Redis use requires an explicit key/value/TTL/invalidation/failure/consistency contract.
13. Keep JPA entities separate from REST request/response contracts and preserve private/public projection boundaries.
14. External calls require timeouts and failure behavior and should not occupy long PostgreSQL transactions.
15. REST changes synchronize OpenAPI annotations, generated-contract tests, Postman, and feature docs.

## Package dependency checklist

Allowed examples:

```text
profile.controller -> profile.service
profile.service -> profile.domain + profile.repository
profile.repository adapter -> Spring Data JPA
profile.service -> identity.service / presence.service public contracts
```

Forbidden examples:

```text
controller -> any repository
controller -> JPA entity response
domain -> controller/security/Redis/Spring Data
profile -> identity.repository
feed -> post.repository
service -> raw Jwt or SecurityContextHolder
```

## Transaction review

Confirm annotation is on a public method called through a managed bean proxy; self-invocation does not activate interception. Confirm one atomic PostgreSQL use case owns the boundary and unchecked exceptions roll it back. Review nested service calls and cross-store effects explicitly.

**NEEDS_CONFIRMATION:** the current public-profile read performs a Redis lookup before its read-only PostgreSQL transaction returns. Treat this as existing behavior and a review point for future related changes, not as permission for more external calls inside transactions.

## Proposed enforcement

`python3 scripts/validate-agent-system.py` enforces only unambiguous source rules without new dependencies. ArchUnit could provide richer bytecode-level checks, but adding it would change test dependencies; proposals are in `proposed-deterministic-checks.md`.
