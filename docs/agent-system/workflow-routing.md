# Workflow Routing

The main thread is the root Orchestrator. It creates direct subagents only; `max_depth = 1` prevents child fan-out. `.codex/config.toml` limits the root to three concurrent child agent threads and uses 900 seconds as a stalled-job guardrail for one subagent turn. This guardrail prevents runaway work; it is not the feature completion criterion. Only `spring_backend_developer` normally edits production Java or Flyway migrations.

## Delivery-control contract

Classify before the first delegation. Classification follows uncertainty and risk, not file count.

| `DELIVERY_TIER` | Qualifying shape | Active-time target/checkpoint | Normal pass range | Child concurrency |
| --- | --- | ---: | ---: | ---: |
| `FAST` | one known-pattern endpoint or CRUD; existing JWT security; routine additive table/column; no new ownership, Redis, integration, or difficult concurrency | target 30 minutes; checkpoint if exceeded | 2-3 | 2 |
| `STANDARD` | several related use cases; new but bounded business/ownership rule; non-routine migration or compatibility work; no new infrastructure | target 60 minutes; checkpoint by 90 | 3-5 | 3 |
| `EXCEPTIONAL` | WebSocket, external integration, Redis/multi-store consistency, difficult concurrency, major module, or deployment impact | target 90 minutes; checkpoint by 120 | up to 7 | 3 |

Trivial text and obvious one-line corrections stay in the root and normally use zero passes. A pass is any started subagent turn, including a follow-up, fix, or re-review. Interrupted, timed-out, blocked, and partial turns count. Messages that only clarify an already-running assignment do not create another pass.

Active time starts at classification and excludes waiting for user input or an approval. Before delegation, record:

```text
MASTER_REQUEST:
DELIVERY_TIER:
ACTIVE_TIME_TARGET_AND_CHECKPOINT:
EXPECTED_SUBAGENT_PASS_RANGE:
PASSES_USED:
PASS_TARGET_MINUTES:
AFFECTED_SURFACES:
MANDATORY_RISK_GATES:
NOT_APPLICABLE_GATES:
NEXT_USER_CHECKPOINT:
```

Show the user the tier and route before the first pass. Provide a progress checkpoint at each phase boundary and at least every 15 active minutes. Time and pass ranges are observability and planning controls, not quality gates. At the checkpoint or when the normal pass range is consumed, start no optional work and report why any blocking correction still continues. User approval is required for scope expansion, a materially different solution, or work after two unsuccessful blocking-finding correction cycles—not merely because the clock or normal pass range was crossed. A user-requested follow-up feature is a new scope and receives a fresh classification.

## Minimal routing

The root owns inexpensive classification, acceptance criteria, focused repository inspection, deduplication, and final reporting. Delegate only a concrete question that benefits from an independent context.

- Clear conversational or dictated requirements do not require `business_analyst`. Use it only when actors, permissions, state transitions, or failure behavior are materially ambiguous.
- The root may write concise `AC-*`. Use `requirements_acceptance_analyst` when several business rules or edge cases need independent traceability.
- Choose `project_rules_scout` or `repository_domain_explorer` for the unresolved question. Use both only when instruction conflicts and repository mapping are independently substantial.
- Do not invoke both domain and application architects for a known feature pattern. Use the one that answers the actual open design question.
- For `FAST`, the production writer normally adds its focused tests. Add `backend_test_engineer` only when fixtures, concurrency, migration, or failure-mode testing is a separate hard problem.
- Use one verification lane after implementation: root-observed focused tests, `api_integration_qa`, or `functional_regression_qa`. Do not run both QA agents for the same unchanged evidence.

Typical routes:

| Change | Route | Expected passes |
| --- | --- | ---: |
| Known-pattern authenticated endpoint/CRUD | root criteria/discovery -> `spring_backend_developer` -> `spring_code_reviewer`; optional `api_integration_qa` replaces or consumes the third pass | 2-3 |
| Bounded ownership or compatibility change | one analyst/explorer if needed -> one designer only for an unresolved decision -> writer -> one risk-matched independent reviewer -> optional correction or QA -> root final evidence | 3-5 |
| WebSocket, Redis/multi-store, external service, or deployment | one bounded requirements/discovery pass -> at most two distinct design specialists in parallel -> writer -> one risk-matched review/QA pass -> `release_gatekeeper`; reserve the seventh pass for correction | up to 7 |

## Risk triggers

| Impact | Independent specialist is required when | Evidence that remains mandatory |
| --- | --- | --- |
| Authentication/security | new public/private boundary, role/permission/ownership rule, sensitive field, token/configuration behavior, upload, or cross-user exposure | negative and cross-user cases appropriate to the change; `SECURITY_PASS` for the triggered gate |
| PostgreSQL/migration | destructive transition, backfill, existing-row constraint, compatibility window, risky lock/rewrite, cascade/data-loss choice, or non-trivial race | real PostgreSQL validation, schema/JPA parity, data-loss and forward-fix evidence; migration verdict when triggered |
| JPA/query | new relationship/fetch shape, custom query, pagination, locking, or N+1 risk | mapping/query evidence and focused tests |
| Redis/multi-store | any new Redis use or changed TTL/invalidation/fallback/cross-store behavior | Redis, consistency, and resilience design plus failure tests |
| External integration/WebSocket/job | new remote/socket/job boundary or changed retry/idempotency/partial-failure behavior | resilience design and failure evidence |
| Deployment/runtime | CI, container, health/readiness, migration rollout, configuration, or hosted environment changes | observability/deployment, smoke, and rollback/forward-fix evidence |
| Performance/concurrency | demonstrated high volume, contention, fan-out, or race risk | query/load/concurrency evidence proportionate to the risk |

Existing bearer-token protection on `/api/v1/**` does not by itself trigger a security design agent. A routine additive migration with no backfill, destructive behavior, risky lock, or new concurrency rule does not by itself trigger data model, JPA, transaction, migration, and performance agents; the writer, reviewer, and PostgreSQL-focused tests may cover it.

## Bounded pass contract

Every assignment contains one outcome, exact scope or owned files, supplied evidence, criteria/risks to cover, a target shorter than the 15-minute stalled-job guardrail, and a concise output limit. Agents must not rescan the whole repository when paths are supplied, repeat baseline work, run unrelated suites, introduce new requirements, or start another agent/cycle. On timeout they return the best current evidence and their normal complete, changes-required, partial, or blocked status. A continuation is justified only by remaining blocking work.

Reuse evidence. One agent owns each expensive command; other agents inspect its recorded command, exit code, and output while relevant code is unchanged. If a later edit can affect that evidence, rerun only the smallest invalidated command.

## Skeleton and writer

For a major new module or restructuring, the writer creates only the feature/package boundary, public service contracts, DTO/entity split, repository ports, and planned transaction/security boundary. `architecture_conformance_reviewer` must pass before behavior expands. Existing-module `FAST` work skips this gate. Never run two production writers.

## Review, correction, and quality stop policy

The initial reviewer is independent from the writer. Independence comes from role and evidence separation, not a brand-new context on every recheck.

1. Classify every finding with `BLOCKING: YES | NO`. `YES` means it invalidates an acceptance criterion, runtime correctness, security/ownership, data safety, required architecture, compilation, migration, or a required test. Preferences and hypothetical improvements are not blocking findings.
2. If no blocking finding remains and required focused evidence passes, the implementation is complete. Do not start a polish pass merely because a reviewer can imagine a cleaner implementation.
3. Fix blocking findings and revalidate only the finding IDs and evidence invalidated by the correction. One targeted correction cycle is normal. A second is allowed only when an introduced `CRITICAL`/`HIGH` blocking finding remains. After two unsuccessful targeted cycles, stop with evidence and user choices; three cycles are forbidden.
4. Non-blocking `MEDIUM`/`LOW` findings may be fixed inside an already-active writer pass when trivial. Otherwise record them as advisory follow-up or accepted residual improvement. They never trigger another writer, reviewer, QA, full-suite, or release pass.
5. Reuse the same reviewer context only when independent targeted revalidation is materially needed; otherwise the root checks the invalidated focused evidence. Never rerun discovery, architecture design, full API QA, or the full regression suite unless the blocking correction invalidated it.

## Proportionate verification

- `FAST`: focused changed-module/API/migration tests plus diff/config checks. Run a full suite or build at most once, only when the change can affect broad wiring or the repository's final verification rules require it.
- `STANDARD`: focused tests during development, then the broadest justified suite once after the last relevant correction.
- `EXCEPTIONAL`: stage focused failure/concurrency/integration evidence and run final broad verification once; do not make each reviewer rerun it.

Formatting, static analysis, Testcontainers, build, and runtime checks must be reported accurately. `NOT_CONFIGURED`, `NOT_RUN`, unavailable infrastructure, or documentation-only evidence is never converted to PASS.

## Completion and checkpoints

The root performs acceptance traceability, changed-file review, architecture-boundary check, command audit, diff check, and residual-risk report for every tier. Use `release_gatekeeper` for `EXCEPTIONAL`, deployment-impacting, irreversible-data, new security-boundary, or otherwise high-risk changes; it is optional for ordinary `FAST` and `STANDARD` work.

Stop successfully as soon as the criteria and required evidence pass, architecture boundaries hold, and no blocking finding remains. Stop as blocked after two unsuccessful targeted correction cycles, repeated external failure, or a decision that cannot be made safely. A time/pass checkpoint reports completed work, observed commands/results, open blocking findings, advisory improvements, unverified criteria, and the smallest next step; it does not force an otherwise unnecessary iteration or permit an unsafe release.
