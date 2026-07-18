# Workflow Routing

The main thread is the root Orchestrator. It creates direct subagents only; `max_depth = 1` prevents child fan-out. Read-only work may run in parallel when scopes do not overlap. Only `spring_backend_developer` normally edits production Java or Flyway migrations.

## Classification

Before routing, score task shape and impacts:

| Class | Typical shape | Workflow |
| --- | --- | --- |
| Trivial | isolated text/message or obvious one-line, no runtime/contract impact | root, optional rules scout, developer, code review, focused validation |
| Standard | one vertical slice with known behavior and limited layers | requirements, rules/discovery, applicable design specialists, tests, one writer, independent review/QA/release |
| Substantial | new module, ambiguous product behavior, several stores/integrations, concurrency/security/deployment risk | all Gates A-I, skeleton review, staged packages, independent specialist gates |

Never route by file count alone. A one-line security rule or migration can be high risk.

## Informal feature flow

```text
MASTER_REQUEST
 -> business_analyst
 -> root business-spec approval (assumptions/questions visible)
 -> requirements_acceptance_analyst
 -> rules scout + repository explorer + baseline
 -> applicable technical design
 -> implementation/review/release
```

No Java/API/SQL/Redis design begins before the business specification is coherent.

## Standard backend flow

Minimum substantial route:

1. `business_analyst` when needed and `requirements_acceptance_analyst`.
2. `project_rules_scout` and `repository_domain_explorer`.
3. `domain_architect` and `application_architect`.
4. `api_contract_designer` for REST changes.
5. `postgres_data_modeler` and `persistence_jpa_reviewer` for durable data.
6. Applicable Redis, consistency, security, resilience, performance, observability specialists.
7. `backend_test_engineer`; `spring_backend_developer` as single production writer.
8. `architecture_conformance_reviewer` and `spring_code_reviewer` independently.
9. `api_integration_qa` and `functional_regression_qa`.
10. `release_gatekeeper`.

Small standard slices may omit specialists whose impact is `NOT_APPLICABLE`, with a recorded reason.

## Impact add-ons

| Impact | Mandatory route/evidence |
| --- | --- |
| PostgreSQL/migration | data modeler, JPA reviewer, consistency reviewer, migration reviewer; performance for volume/concurrency; PostgreSQL Testcontainers |
| Redis | Redis architect, consistency, resilience; performance for contention/high volume/locks/streams; TTL/stale/unavailable tests |
| Security/ownership | security reviewer; negative, cross-user, role, and ownership cases; independent `SECURITY_PASS` |
| External integration | resilience reviewer; timeout/retry/idempotency/partial-failure plan and tests or documented blocker |
| Data-heavy/concurrent | performance and consistency reviewers; constraints/locks/query plan/concurrency evidence |
| Substantial/runtime | observability reviewer; health/log/metric/diagnostic evidence |
| CI/deployment | observability + CI/deployment engineer; build artifact, migration, health, smoke, rollback |
| Framework uncertainty | Spring docs researcher with locked versions and primary references |

## Skeleton gate

For new modules or major restructuring, the writer first creates only the feature/package boundary, public service contracts, DTO/entity split, repository ports, and planned transaction/security boundary. `architecture_conformance_reviewer` must return `ARCHITECTURE_PASS` before behavior expands.

## Work packages

Each sequential package has an ID, covered `AC-*`, exact files, tests, commands/results, annotations/lifecycle explanations, PostgreSQL effects, Redis effects, assumptions, and risks. Do not run two production writers.

## Review and fix cycle

Independent reviewers inspect code/evidence rather than developer summaries. Findings use the common contract. Root deduplicates and routes actionable findings to the same writer. Fix report maps finding -> root cause -> file -> test -> store impact -> command/result. Run fresh reviewer/QA turns. Stop after three complete cycles by default; unresolved blocking findings yield honest non-ready status.

## Completion

`release_gatekeeper` selects required gates from impact, inspects Git/diff, and verifies observed PASS evidence. Missing, not-run, or assumed evidence cannot become PASS.
