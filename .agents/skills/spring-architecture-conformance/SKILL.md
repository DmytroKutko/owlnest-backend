---
name: spring-architecture-conformance
description: Design or review OwlNest module/package boundaries, dependency direction, service transactions, repository access, and DTO/entity separation.
---

# Spring Architecture Conformance

Use for new feature modules, cross-module changes, package moves, controller/service/repository restructuring, or architecture review.

## Established invariants

- One Java 21 Spring Boot application and one Gradle project; logical feature-first modular monolith under `dev.dkutko.owlnest`.
- Implemented feature roots are `identity`, `profile`, `presence`, and `post`; `foundation` owns shared technical OpenAPI configuration. Draft modules are not implemented conventions.
- Inside a feature, create only needed `controller`, `service`, `domain`, `repository`, or explicit technical packages such as `security`.
- Controllers own HTTP mapping, validation, DTO translation, and error mapping; they call services and never repositories.
- Services own use-case orchestration and normal `@Transactional` entry points.
- Domain owns state and rules. JPA annotations on domain entities are an accepted pragmatic simplification; do not add a duplicate persistence model without evidence.
- Services depend on project repository interfaces. `*RepositoryImpl` adapts to package-private Spring Data interfaces.
- Cross-feature calls use public service contracts or approved events, never another module's repository or Spring Data type.
- REST controllers return response DTOs/records, never JPA entities. Keep private and public profile projections separate.
- Constructor injection only in production components; singleton beans hold no request-specific mutable state.
- Flyway owns schema; Hibernate `ddl-auto: validate` only checks parity.

## Review sequence

1. Map package ownership and dependency edges.
2. Verify controller -> service -> repository interface/domain direction.
3. Check cross-module imports and transport/persistence leakage.
4. Check transaction annotations on public proxied service methods and note nested or self-invoked calls.
5. Compare API DTOs with entities and public/private data boundaries.
6. Compare repository implementation and mappings with Flyway.
7. Compare skeleton/implementation with the implemented `identity/profile/presence/post` slices, including append-only post comments, not Draft `feed` or future comment mutation/moderation plans.
8. Run `python3 scripts/validate-agent-system.py` for deterministic unambiguous checks.

Return the common finding contract and the required architecture verdict. A skeleton must pass before substantial implementation continues.
