# Validation Matrix

Use this matrix to map acceptance criteria and task impacts to evidence. Add rows for feature-specific behavior; do not delete required negative cases silently.

| Surface | Design/review gate | Minimum evidence |
| --- | --- | --- |
| Business behavior | business + requirements analysts | approved `BR-*`/`AC-*` traceability and Given/When/Then scenarios |
| Architecture | architecture reviewer | package/dependency/DTO/transaction checklist and `ARCHITECTURE_PASS` |
| Java/Spring | Spring code reviewer | diff review with proxy/lifecycle/null/error/concurrency evidence and code-review PASS |
| REST contract | API designer + API QA | runtime statuses/payloads, validation/security, generated OpenAPI, Postman/docs sync, `API_QA_PASS` |
| PostgreSQL schema | data modeler + migration reviewer | query-driven design, migration history/safety, real PostgreSQL migration/JPA validation, `MIGRATION_PASS` |
| JPA/query behavior | JPA reviewer | mapping/schema parity, fetch/query/N+1/locking evidence and JPA PASS |
| Transactions/concurrency | consistency reviewer | atomicity/race/idempotency/partial-failure model plus focused concurrency/rollback tests |
| Redis | Redis architect + resilience/consistency | key/value/TTL/invalidation/fallback contract, Testcontainers TTL/failure/concurrency evidence |
| Security/ownership | security reviewer | actor matrix, no-token/invalid/forbidden/cross-user/private-field cases, `SECURITY_PASS` |
| External integration | resilience reviewer | configured timeouts, retry classification, idempotency, malformed/slow/down cases |
| Performance | performance reviewer | query/index plan, bounded queries/no N+1, EXPLAIN/load/profile evidence when risk warrants |
| Observability | observability reviewer | health/readiness, safe logs/metrics, failure diagnosis, startup/shutdown evidence |
| CI/deployment | CI engineer | wrapper build/tests, artifact, secrets names, migration, health/smoke, rollback |
| Regression | functional QA | baseline comparison, all `AC-*`, focused/full suite, unexpected files, `QA_PASS` |
| Release | release gatekeeper | complete gate matrix, Git/diff audit, observed commands, `RELEASE_READY` |

## Baseline evidence

Record branch/status, user-owned dirty files, focused/full tests, environment/tool failures, current API/schema/key contracts, and relevant reference features before edits. A failure is `PRE_EXISTING`, `INTRODUCED`, or `UNCLASSIFIED` with evidence.

## PASS evidence rule

A PASS contains scope, exact command or inspection evidence, observed result, criteria covered, and remaining limitations. `NOT_RUN`, unavailable infrastructure, another agent's assertion, or documentation alone is not PASS.

## Current bootstrap validation command

```shell
python3 scripts/validate-agent-system.py
```

It validates agent/skill/config structure and objective architecture invariants. It does not compile Java or start stores. Pair it with Gradle tests/build and `git diff --check`.
