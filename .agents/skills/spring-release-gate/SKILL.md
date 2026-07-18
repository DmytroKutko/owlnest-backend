---
name: spring-release-gate
description: Make an evidence-based OwlNest release decision after implementation, independent reviews, QA, tests, documentation, and diff audit.
---

# Spring Release Gate

Use at the end of every non-trivial implementation or deployment-impacting change.

## Required inputs

Collect `MASTER_REQUEST`, approved assumptions/non-goals, numbered acceptance criteria, preserved baseline, technical package, work-package reports, finding/fix map, and every applicable independent verdict. A reviewer summary without concrete evidence is not a verdict.

## Gate matrix

Always require acceptance traceability, architecture review, Spring code review, focused/full regression evidence, documentation sync, Git status/full diff, unexpected-file audit, and build or application-start evidence proportionate to risk.

Conditionally require:

- API QA and generated OpenAPI/Postman checks for REST changes;
- PostgreSQL design, JPA, consistency, migration review, and real PostgreSQL tests for data changes;
- Redis design, TTL/invalidation/failure/consistency tests for Redis changes;
- independent security PASS and negative/cross-user tests for security or owned resources;
- resilience review and failure tests for integrations;
- performance/concurrency evidence for high-volume or concurrent behavior;
- observability, CI/deployment, health, smoke, migration, and rollback evidence for operational changes.

Run or inspect `python3 scripts/validate-agent-system.py`, project formatting status, static analysis status (`NOT_CONFIGURED` is accurate), relevant tests, `./gradlew build`, `git diff --check`, `git status --short`, and the complete diff. Distinguish baseline failures from introduced failures.

`RELEASE_READY` is allowed only when every mandatory gate is PASS and no critical finding remains. Use `RELEASE_NOT_READY` for actionable failures and `RELEASE_BLOCKED` when required evidence cannot be obtained because of an external limitation.
