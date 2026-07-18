---
name: postgres-jpa-migration-workflow
description: Plan, implement, and independently validate an OwlNest PostgreSQL schema or JPA mapping change through Flyway and Testcontainers.
---

# PostgreSQL, JPA, and Migration Workflow

Use for tables, columns, constraints, indexes, queries, JPA mappings, locking, or transaction changes.

## Baseline

PostgreSQL 18 is the only test/runtime relational database. Flyway SQL files live in `src/main/resources/db/migration` and use `V<integer>__<snake_case_description>.sql`. Existing `V1` creates `identity_account` and `profile`; `V2` adds onboarding fields; `V3` creates the implemented single-post tables and interaction counters. Do not edit an already applied shared migration—add the next version after inspecting the directory immediately before writing.

Durable identifiers are UUID. Timestamps are `Instant`/`TIMESTAMPTZ` in UTC. Use PostgreSQL types, named constraints/indexes, explicit nullability, and deliberate FK deletion behavior. `identity_account(provider, external_subject)` and case-insensitive profile username uniqueness are database-enforced. PostgreSQL is the source of truth.

Current JPA domain entities use field annotations, protected no-arg constructors, private creation, and behavior methods. Repository services depend on a project interface; `*RepositoryImpl` delegates to a package-private `JpaRepository`. Flyway owns DDL and Hibernate runs `ddl-auto: validate` with Open Session in View disabled.

## Gates

1. `postgres_data_modeler`: query-driven schema, constraints/indexes, ownership, deletion, volume, locking, backfill, rolling transition.
2. `persistence_jpa_reviewer`: mapping parity, fetch/query behavior, N+1, projections, batching, equality, locks.
3. `transaction_consistency_reviewer`: atomic units, races, unique constraints, retry/idempotency, proxy validity.
4. Root approves design and migration sequence before `spring_backend_developer` writes SQL/code.
5. `database_migration_reviewer` independently checks history, production locks/rewrites, old/new coexistence, forward-fix, and data loss.
6. Validate on PostgreSQL Testcontainers; never rely on H2. Run focused tests, full `./gradlew test`, and JPA schema validation.

For large or concurrent tables, require evidence for index creation and backfill strategy rather than assuming a single blocking ALTER is safe.
