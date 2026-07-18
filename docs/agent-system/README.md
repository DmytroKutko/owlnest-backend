# OwlNest Codex Agent System

This project-local system prevents informal product ideas from turning directly into unsafe Spring changes. It makes business behavior explicit, preserves the feature-first modular monolith, treats PostgreSQL as durable authority, constrains Redis, uses one production writer, and requires independent evidence before release.

The main Codex thread is the root Orchestrator. `.codex/config.toml` permits six concurrent open threads and one child level. The root dynamically selects from exactly 25 project agents; it does not run the whole team for every task.

## Agent catalog

| Agent | Purpose | Sandbox/write policy | Trigger | Mandatory output/verdict |
| --- | --- | --- | --- | --- |
| `business_analyst` | Turn incomplete ideas into business behavior | Read-only | informal/incomplete/rule-light request | business spec; `READY_FOR_REQUIREMENTS` or `BLOCKED` |
| `requirements_acceptance_analyst` | Create numbered testable criteria | Read-only | approved business spec or non-trivial request | `MASTER_REQUEST`, `AC-*`, traceability, technical-spec readiness |
| `project_rules_scout` | Extract task rules and conflicts | Read-only | project rules/architecture matter | checklist/evidence; `RULES_READY` or `BLOCKED` |
| `repository_domain_explorer` | Map actual paths/data/tests/regressions | Read-only | every non-trivial code task | repository/domain map; `DISCOVERY_READY` or `BLOCKED` |
| `spring_docs_researcher` | Verify version-specific framework behavior | Read-only | framework uncertainty/deprecation/lifecycle question | versions, primary references, confidence |
| `domain_architect` | Model domain concepts/invariants | Read-only | substantial business capability | domain package; `DOMAIN_DESIGN_PASS` or `BLOCKED` |
| `application_architect` | Design use cases/transactions/packages | Read-only | substantial cross-layer behavior | orchestration/work packages; application-design verdict |
| `api_contract_designer` | Design REST DTO/status/error/security contract | Read-only | REST behavior changes | endpoint/schema package; `API_CONTRACT_PASS` or `BLOCKED` |
| `postgres_data_modeler` | Design PostgreSQL schema/index/transition | Read-only | durable data changes | schema/migration strategy; PostgreSQL verdict |
| `persistence_jpa_reviewer` | Review mappings/queries/fetch/locking | Read-only | JPA or persistence change | mapping findings; `JPA_PASS`, changes, or blocked |
| `redis_cache_architect` | Accept/reject Redis and define consistency | Read-only | Redis/cache/temporary-state idea | key/TTL/failure plan; pass/rejected/blocked |
| `transaction_consistency_reviewer` | Review atomicity/races/retries/cross-store failure | Read-only | writes, concurrency, multi-store work | consistency model and verdict |
| `security_authorization_reviewer` | Review auth, permissions, ownership, data exposure | Read-only, independent | security/owned/sensitive/public input | findings; `SECURITY_PASS`, changes, or blocked |
| `integration_resilience_reviewer` | Review timeouts/retries/idempotency/failures | Read-only | external/Redis/job/socket dependency | resilience plan and verdict |
| `performance_concurrency_reviewer` | Review queries/load/contention/concurrency | Read-only | data-heavy/high-frequency/concurrent work | evidence-based risks and verdict |
| `observability_production_reviewer` | Review health/logs/metrics/runtime diagnosis | Read-only | substantial/integration/cache/deployment impact | production evidence and verdict |
| `backend_test_engineer` | Design/write focused automated tests | Workspace write; tests/test support only | non-trivial implementation | test plan/files/commands; `TESTS_READY` or `BLOCKED` |
| `spring_backend_developer` | Sole production Java/Flyway writer | Workspace write; approved implementation scope | approved work package | files/criteria/tests/store effects; ready or `BLOCKED` |
| `architecture_conformance_reviewer` | Independently check module/layer boundaries | Read-only | skeleton and completed implementation | exact `ARCHITECTURE_PASS`, `CHANGES_REQUIRED`, or `BLOCKED` |
| `spring_code_reviewer` | Independently review Java/Spring correctness | Read-only | after implementation/fix | evidence findings; code-review verdict |
| `database_migration_reviewer` | Independently review migration safety | Read-only | every migration | exact migration verdict |
| `api_integration_qa` | Exercise API/stores/security/OpenAPI | Workspace write for ignored runtime outputs; no source edits | API/integration changes | exact `API_QA_PASS`, `API_QA_FAIL`, or `API_QA_BLOCKED` |
| `functional_regression_qa` | Validate criteria and baseline regressions | Workspace write for ignored test outputs; no source edits | after implementation/fixes | exact `QA_PASS`, `QA_FAIL`, or `QA_BLOCKED` |
| `ci_deployment_engineer` | Maintain proportionate CI/deployment | Workspace write; CI/deployment surfaces only | CI/runtime/deployment work | pipeline/migration/health/rollback report |
| `release_gatekeeper` | Final evidence and diff gate | Workspace write for ignored verification outputs; no source edits | final non-trivial gate | exact `RELEASE_READY`, `RELEASE_NOT_READY`, or `RELEASE_BLOCKED` |

All custom agent files omit `model` and `model_reasoning_effort`, so they inherit the active parent session. Every agent is instructed not to spawn subagents.

## Routing and gates

- Trivial tasks use root + minimum implementation/review/validation.
- Informal features must pass business analysis and acceptance criteria first.
- Standard backend features add rules/discovery, only applicable design specialists, tests, one writer, independent architecture/code/API/regression checks, and release.
- PostgreSQL, Redis, security, integrations, concurrency, observability, and deployment each add their specialist gates from `workflow-routing.md`.
- New modules or major shapes pass a skeleton architecture gate before full implementation.
- Fixes return to the same writer with finding-to-fix traceability. Fresh reviewers rerun; three complete cycles is the default limit.

## Informal idea conversion

`business-idea-to-feature-spec` preserves the original idea, identifies actors/permissions/flows/rules/states/failures, records assumptions/questions/non-goals, and produces business scenarios. Root approves coherence. The requirements analyst then creates `AC-*` criteria with verification for every `BR-*`. Only then do technical agents design APIs, tables, Redis, or Java.

## PostgreSQL and Redis review

PostgreSQL changes require schema, JPA, consistency, migration, and real PostgreSQL evidence. Applied migrations are immutable. Redis proposals must first prove Redis is appropriate and then define namespace, value/serialization, TTL, invalidation, source of truth, stale/unavailable/flushed behavior, concurrency, and tests. Current Redis use is only ephemeral presence; it is not a precedent for general caching.

## Edit boundaries

`spring_backend_developer` is the single normal production-code/migration writer. `backend_test_engineer` edits tests only. `ci_deployment_engineer` edits declared CI/deployment surfaces only. QA and release roles may generate ignored build/runtime output but may not edit sources. Review/design roles are read-only.

## Inspect and maintain

- Agent definitions: `.codex/agents/*.toml`
- Concurrency/depth: `.codex/config.toml`
- Durable root rules: `AGENTS.md`
- Routed workflows: `.agents/skills/*/SKILL.md`
- Project evidence and contracts: this directory
- Deterministic validator: `scripts/validate-agent-system.py`

Run `python3 scripts/validate-agent-system.py` after edits, then project compilation/tests/build and `git diff --check`. See `maintenance-guide.md` for Codex-version checks and recalibration.

## Test on a real feature

Start a new Codex session (project instructions/config reload reliably there) and give a small informal feature idea. Confirm business/requirements routing, impact-specific specialist selection, one writer, independent review, and refusal to release without evidence. A dry run may stop after technical specification to avoid unnecessary product changes.

## Temporarily simplify or disable

Ask root to use a named minimal route for one task; mandatory risk gates still apply. For a broader temporary override, use a clearly dated root `AGENTS.override.md` and remove it afterward. A repository must be trusted for project `.codex` layers. Lower `max_threads` only for a documented resource reason; keep `max_depth = 1`. Do not delete agents to save tokens on one task—dynamic routing already avoids invoking them.

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
