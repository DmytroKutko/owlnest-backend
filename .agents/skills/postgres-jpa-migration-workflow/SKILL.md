---
name: postgres-jpa-migration-workflow
description: Plan, implement, and independently validate an OwlNest PostgreSQL schema or JPA mapping change through Flyway and Testcontainers.
---

# PostgreSQL, JPA, and Migration Workflow

Use for tables, columns, constraints, indexes, queries, JPA mappings, locking, or transaction changes.

## Baseline

PostgreSQL 18 is the only test/runtime relational database. Flyway SQL files live in `src/main/resources/db/migration` and use `V<integer>__<snake_case_description>.sql`. Existing `V1` creates `identity_account` and `profile`; `V2` adds onboarding fields; `V3` creates the implemented single-post tables and interaction counters; `V4` adds append-only post comments and swaps the stored counter check to nonnegative `NOT VALID`; `V5` validates that check in a separate transaction/lock scope. Do not edit an already applied shared migration—add the next version after inspecting the directory immediately before writing.

Durable identifiers are UUID. Timestamps are `Instant`/`TIMESTAMPTZ` in UTC. Use PostgreSQL types, named constraints/indexes, explicit nullability, and deliberate FK deletion behavior. `identity_account(provider, external_subject)` and case-insensitive profile username uniqueness are database-enforced. PostgreSQL is the source of truth.

Current JPA domain entities use field annotations, protected no-arg constructors, private creation, and behavior methods. Repository services depend on a project interface; `*RepositoryImpl` delegates to a package-private `JpaRepository`. Flyway owns DDL and Hibernate runs `ddl-auto: validate` with Open Session in View disabled.

## Gates

Classify the transition before routing. A routine additive table/column for a known pattern, with no backfill, destructive behavior, existing-row validation, risky lock, new relationship/fetch behavior, or difficult race may stay `FAST`: the root checks the plan, `spring_backend_developer` writes SQL/code/tests, and `spring_code_reviewer` reviews schema/JPA/transaction parity.

Add only the specialist that owns a concrete risk:

1. `postgres_data_modeler` for non-routine constraints/indexes, deletion/data-loss choice, backfill, volume, locking, or rolling transition.
2. `persistence_jpa_reviewer` for new relationships, custom queries, fetch/N+1, projections, batching, equality, or locks.
3. `transaction_consistency_reviewer` for a new race, retry/idempotency rule, complex locking, or cross-store consistency.
4. `database_migration_reviewer` for compatibility-sensitive, lock/rewrite, backfill, destructive, or otherwise production-risky migrations.

Root approves the applicable design before the writer edits SQL/code. Validate on PostgreSQL Testcontainers; never rely on H2. Run focused migration/JPA tests and the broadest justified suite once after final corrections.

For large or concurrent tables, require evidence for index creation and backfill strategy rather than assuming a single blocking ALTER is safe.
