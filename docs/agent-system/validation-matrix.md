# Validation Matrix

Use this matrix to map acceptance criteria and task impacts to evidence. Add rows for feature-specific behavior; do not delete required negative cases silently.

| Surface | Design/review gate | Minimum evidence |
| --- | --- | --- |
| Delivery control | root Orchestrator | tier rationale, time/pass target, passes used, checkpoint status, and reason for any quality-required continuation or scope expansion |
| Business behavior | root or business/requirements analysts when ambiguity warrants | approved `BR-*`/`AC-*` traceability proportionate to rule complexity |
| Architecture | root/code review for known patterns; architecture reviewer for new modules/restructuring | package/dependency/DTO/transaction checklist and specialist verdict when triggered |
| Java/Spring | Spring code reviewer | diff review with proxy/lifecycle/null/error/concurrency evidence and code-review PASS |
| REST contract | root/writer/reviewer for known pattern; API designer or API QA when contract risk warrants | runtime statuses/payloads, validation/security, generated OpenAPI, Postman/docs sync, specialist verdict when triggered |
| PostgreSQL schema | writer/reviewer for routine additive change; data modeler + migration reviewer for risky transitions | query-driven design, migration history/safety, real PostgreSQL migration/JPA validation, specialist verdict when triggered |
| JPA/query behavior | code review for established mappings; JPA reviewer for relationship/query/fetch/locking risk | mapping/schema parity, fetch/query/N+1/locking evidence and specialist verdict when triggered |
| Transactions/concurrency | code review for established single-store pattern; consistency reviewer for new race or cross-store behavior | atomicity/race/idempotency/partial-failure model plus focused concurrency/rollback tests when applicable |
| Redis | Redis architect + resilience/consistency | key/value/TTL/invalidation/fallback contract, Testcontainers TTL/failure/concurrency evidence |
| Security/ownership | existing JWT boundary may use code review; security reviewer for new boundary/permission/ownership/sensitive-data behavior | applicable no-token/invalid/forbidden/cross-user/private-field cases and `SECURITY_PASS` when triggered |
| External integration | resilience reviewer | configured timeouts, retry classification, idempotency, malformed/slow/down cases |
| Performance | performance reviewer | query/index plan, bounded queries/no N+1, EXPLAIN/load/profile evidence when risk warrants |
| Observability | observability reviewer | health/readiness, safe logs/metrics, failure diagnosis, startup/shutdown evidence |
| CI/deployment | CI engineer | wrapper build/tests, artifact, secrets names, migration, health/smoke, rollback |
| Regression | root or functional QA, selected by tier/risk | baseline comparison, all `AC-*`, focused tests and one broad suite when justified, unexpected files, specialist verdict when triggered |
| Release | root for ordinary FAST/STANDARD; release gatekeeper for exceptional/high-risk work | complete applicable gate matrix, Git/diff audit, observed commands, specialist verdict when triggered |

## Baseline evidence

Record branch/status, user-owned dirty files, focused baseline evidence, environment/tool failures, current affected contracts, and the nearest reference feature before edits. Do not run a full suite as baseline for `FAST` unless broad pre-existing behavior is genuinely in doubt. A failure is `PRE_EXISTING`, `INTRODUCED`, or `UNCLASSIFIED` with evidence.

## PASS evidence rule

A PASS contains scope, exact command or inspection evidence, observed result, criteria covered, and remaining limitations. `NOT_RUN`, unavailable infrastructure, another agent's assertion, or documentation alone is not PASS.

Observed command evidence may be reused across roles while relevant code is unchanged. Rerun only commands invalidated by a later edit; identical full-suite runs by several agents add no evidence.

A release decision is quality-based: criteria and required evidence pass, architecture boundaries hold, and no `BLOCKING: YES` finding remains. Non-blocking Medium/Low advice is recorded without another implementation or verification cycle.

## Current bootstrap validation command

```shell
python3 scripts/validate-agent-system.py
```

It validates agent/skill/config structure and objective architecture invariants. It does not compile Java or start stores. Pair it with Gradle tests/build and `git diff --check`.
