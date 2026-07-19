# OwlNest Codex Agent System

This project-local system prevents informal product ideas from turning directly into unsafe Spring changes. It makes business behavior explicit, preserves the feature-first modular monolith, treats PostgreSQL as durable authority, constrains Redis, uses one production writer, and requires independent evidence before release.

The main Codex thread is the root Orchestrator. `.codex/config.toml` permits three concurrent child agent threads, one child level, and a 15-minute stalled-job guardrail for one subagent turn. The root dynamically selects from exactly 25 project agents; it does not run the whole team for every task.

## Agent catalog

| Agent | Purpose | Sandbox/write policy | Trigger | Mandatory output/verdict |
| --- | --- | --- | --- | --- |
| `business_analyst` | Turn incomplete ideas into business behavior | Read-only | material actor/permission/state/failure ambiguity | business spec; `READY_FOR_REQUIREMENTS` or `BLOCKED` |
| `requirements_acceptance_analyst` | Create numbered testable criteria | Read-only | several business rules need independent traceability | `MASTER_REQUEST`, `AC-*`, traceability, technical-spec readiness |
| `project_rules_scout` | Extract task rules and conflicts | Read-only | substantial instruction conflict or unfamiliar rule surface | checklist/evidence; `RULES_READY` or `BLOCKED` |
| `repository_domain_explorer` | Map actual paths/data/tests/regressions | Read-only | repository discovery is substantial or unfamiliar | repository/domain map; `DISCOVERY_READY` or `BLOCKED` |
| `spring_docs_researcher` | Verify version-specific framework behavior | Read-only | framework uncertainty/deprecation/lifecycle question | versions, primary references, confidence |
| `domain_architect` | Model domain concepts/invariants | Read-only | substantial business capability | domain package; `DOMAIN_DESIGN_PASS` or `BLOCKED` |
| `application_architect` | Design use cases/transactions/packages | Read-only | substantial cross-layer behavior | orchestration/work packages; application-design verdict |
| `api_contract_designer` | Design REST DTO/status/error/security contract | Read-only | new/non-routine contract or unresolved compatibility/error design | endpoint/schema package; `API_CONTRACT_PASS` or `BLOCKED` |
| `postgres_data_modeler` | Design PostgreSQL schema/index/transition | Read-only | non-routine constraint/index/backfill/lock/deletion transition | schema/migration strategy; PostgreSQL verdict |
| `persistence_jpa_reviewer` | Review mappings/queries/fetch/locking | Read-only | new relationship, custom query, fetch/N+1, pagination, or locking risk | mapping findings; `JPA_PASS`, changes, or blocked |
| `redis_cache_architect` | Accept/reject Redis and define consistency | Read-only | Redis/cache/temporary-state idea | key/TTL/failure plan; pass/rejected/blocked |
| `transaction_consistency_reviewer` | Review atomicity/races/retries/cross-store failure | Read-only | new race/idempotency/complex locking or multi-store behavior | consistency model and verdict |
| `security_authorization_reviewer` | Review auth, permissions, ownership, data exposure | Read-only, independent | security/owned/sensitive/public input | findings; `SECURITY_PASS`, changes, or blocked |
| `integration_resilience_reviewer` | Review timeouts/retries/idempotency/failures | Read-only | external/Redis/job/socket dependency | resilience plan and verdict |
| `performance_concurrency_reviewer` | Review queries/load/contention/concurrency | Read-only | data-heavy/high-frequency/concurrent work | evidence-based risks and verdict |
| `observability_production_reviewer` | Review health/logs/metrics/runtime diagnosis | Read-only | substantial/integration/cache/deployment impact | production evidence and verdict |
| `backend_test_engineer` | Design/write focused automated tests | Workspace write; tests/test support only | fixtures, failure, migration, or concurrency tests are independently complex | test plan/files/commands; `TESTS_READY` or `BLOCKED` |
| `spring_backend_developer` | Sole production Java/Flyway writer | Workspace write; approved implementation scope | approved work package | files/criteria/tests/store effects; ready or `BLOCKED` |
| `architecture_conformance_reviewer` | Independently check module/layer boundaries | Read-only | major new module/restructuring skeleton or material boundary risk | exact `ARCHITECTURE_PASS`, `CHANGES_REQUIRED`, or `BLOCKED` |
| `spring_code_reviewer` | Independently review Java/Spring correctness | Read-only | primary independent review for known-pattern implementation | evidence findings; code-review verdict |
| `database_migration_reviewer` | Independently review migration safety | Read-only | destructive/backfilled/lock/compatibility/data-loss-risk migration | exact migration verdict |
| `api_integration_qa` | Exercise API/stores/security/OpenAPI | Workspace write for ignored runtime outputs; no source edits | selected verification lane for non-routine API/integration risk | exact `API_QA_PASS`, `API_QA_FAIL`, or `API_QA_BLOCKED` |
| `functional_regression_qa` | Validate criteria and baseline regressions | Workspace write for ignored test outputs; no source edits | selected verification lane for broad acceptance/regression risk | exact `QA_PASS`, `QA_FAIL`, or `QA_BLOCKED` |
| `ci_deployment_engineer` | Maintain proportionate CI/deployment | Workspace write; CI/deployment surfaces only | CI/runtime/deployment work | pipeline/migration/health/rollback report |
| `release_gatekeeper` | Final evidence and diff gate | Workspace write for ignored verification outputs; no source edits | exceptional/high-risk final gate | exact `RELEASE_READY`, `RELEASE_NOT_READY`, or `RELEASE_BLOCKED` |

All custom agent files omit `model` and `model_reasoning_effort`, so they inherit the active parent session. Every agent is instructed not to spawn subagents and to treat each assignment as one bounded pass.

## Routing and gates

- `FAST` targets about 30 minutes/2-3 passes, `STANDARD` targets 60 minutes with a checkpoint by 90/3-5 passes, and `EXCEPTIONAL` checkpoints by 120 minutes/up to seven normal passes. These are planning controls, not permission to ship defects or reasons to polish already-correct code.
- Clear informal wording may be specified by the root; analysts are used for material business ambiguity or non-trivial traceability, not merely because the request was dictated.
- Known-pattern authenticated CRUD normally uses one writer, one independent reviewer, and, only when concrete risk warrants it, one focused QA/specialist pass.
- PostgreSQL, Redis, security, integrations, concurrency, observability, and deployment add specialist gates only for their concrete risk triggers from `workflow-routing.md`.
- New modules or major shapes retain the skeleton architecture gate.
- Finish when criteria and required evidence pass, architecture is correct, and no blocking finding remains. One targeted correction cycle is normal; a second is only for unresolved introduced blocking Critical/High findings. Non-blocking Medium/Low advice never triggers another cycle; three cycles are forbidden.
- Ordinary `FAST`/`STANDARD` release evidence is consolidated by the root. `release_gatekeeper` is reserved for exceptional or high-risk work.

## Informal idea conversion

When behavior is materially ambiguous, `business-idea-to-feature-spec` preserves the original idea, identifies actors/permissions/flows/rules/states/failures, records assumptions/questions/non-goals, and produces business scenarios. The requirements analyst is added only when several rules need independent `BR-*` to `AC-*` traceability.

## PostgreSQL and Redis review

Routine additive PostgreSQL changes may stay on the `FAST` writer/reviewer route with real PostgreSQL evidence. Risky transitions add only the relevant schema, JPA, consistency, or migration specialists. Applied migrations are immutable. Redis proposals still require Redis, consistency, and failure design because current ephemeral presence is not a precedent for general caching.

## Edit boundaries

`spring_backend_developer` is the single normal production-code/migration writer. `backend_test_engineer` edits tests only. `ci_deployment_engineer` edits declared CI/deployment surfaces only. QA and release roles may generate ignored build/runtime output but may not edit sources. Review/design roles are read-only.

## Inspect and maintain

- Agent definitions: `.codex/agents/*.toml`
- Concurrency/depth: `.codex/config.toml`
- Durable root rules: `AGENTS.md`
- Routed workflows: `.agents/skills/*/SKILL.md`
- Project evidence and contracts: this directory
- Deterministic validator: `scripts/validate-agent-system.py`

Run `python3 scripts/validate-agent-system.py` after edits, then tier-appropriate focused checks, the broadest justified build/test command at most once after the final relevant change, and `git diff --check`. See `maintenance-guide.md` for Codex-version checks and recalibration.

## Test on a real feature

Start a new Codex session (project instructions/config reload reliably there) and give a small known-pattern authenticated CRUD request. Confirm `FAST` classification, normally 2-3 passes, one writer, independent review, focused evidence, and a progress checkpoint around 30 minutes if still active. Confirm that advisory-only feedback ends without another cycle and that a blocking defect receives only targeted correction/revalidation.

## Temporarily simplify or disable

Ask root to use a named tier or smaller route for one task; mandatory triggered risk gates still apply. For a broader temporary override, use a clearly dated root `AGENTS.override.md` and remove it afterward. A repository must be trusted for project `.codex` layers. Change concurrency or the stalled-job guardrail only with measured evidence and user approval; keep `max_depth = 1`.

## Documentation index

- `project-profile.md`: verified technology and repository state
- `business-analysis-guide.md`: informal idea gate
- `domain-map.md`: implemented modules and execution paths
- `architecture-invariants.md`: allowed/forbidden dependencies
- `reference-features.md`: implemented examples versus Drafts
- `api-conventions.md`, `postgres-conventions.md`, `redis-conventions.md`, `security-conventions.md`
- `testing-strategy.md`, `observability-and-operations.md`
- `workflow-routing.md`, `handoff-contracts.md`, `validation-matrix.md`
- `maintenance-guide.md`, `proposed-deterministic-checks.md`
