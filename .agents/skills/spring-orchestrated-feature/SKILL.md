---
name: spring-orchestrated-feature
description: Route and gate a substantial OwlNest Spring Boot feature or cross-layer refactor from specification through independent release evidence.
---

# Spring Orchestrated Feature

Use for non-trivial features or refactors that cross a controller, service, domain, persistence, Redis, security, integration, or deployment boundary. Do not use the full workflow for isolated text or obvious one-line corrections.

## Required context

Read `AGENTS.md`, `docs/project-context.md`, `docs/agent-system/project-profile.md`, `workflow-routing.md`, `handoff-contracts.md`, and `validation-matrix.md`. Read the owning feature doc and accepted ADRs. Preserve `MASTER_REQUEST` verbatim and mark Draft material as proposal.

## Classify first

Record complexity (`trivial`, `standard`, `substantial`) and impacts: API, PostgreSQL/migration, JPA, Redis, transaction/concurrency, security/ownership, external integration, observability, deployment, documentation. Select only agents justified by those impacts.

## Gates

1. **Business specification:** for informal or incomplete ideas, route to `business_analyst`; preserve actors, permissions, flows, rules, states, assumptions, questions, and non-goals.
2. **Requirements and baseline:** route to `requirements_acceptance_analyst`, `project_rules_scout`, and `repository_domain_explorer`; create numbered criteria, Git/test baseline, reference feature, regression scope, and specialist list.
3. **Technical package:** use only applicable domain, application, API, PostgreSQL, JPA, Redis, consistency, security, resilience, performance, and observability agents. Root resolves contradictions before code.
4. **Skeleton:** for a new module or substantial shape, `spring_backend_developer` creates minimum packages/contracts; `architecture_conformance_reviewer` must pass before behavior expands.
5. **Data/Redis design:** approve schema/JPA/migration and Redis key/TTL/serialization/invalidation/failure/concurrency before implementation.
6. **Implementation:** one `spring_backend_developer` context implements ordered packages. `backend_test_engineer` edits only tests. After each package capture changed files, criteria, commands/results, assumptions, and store effects.
7. **Independent review:** launch fresh applicable reviewers and QA. Read-heavy reviews may run in parallel; production writers may not.
8. **Fix loop:** map every finding ID to root cause, file, test, database/Redis impact, command, and observed result. Return to the same developer context; rerun fresh reviews. Stop after three full cycles unless root documents a reason.
9. **Release:** `functional_regression_qa` validates criteria against baseline; `release_gatekeeper` checks every required PASS and the full diff.

Completion requires observed evidence, not summaries. Missing mandatory evidence means `RELEASE_NOT_READY` or `RELEASE_BLOCKED`.
