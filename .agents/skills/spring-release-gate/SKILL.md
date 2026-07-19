---
name: spring-release-gate
description: Make an evidence-based OwlNest release decision after implementation, independent reviews, QA, tests, documentation, and diff audit.
---

# Spring Release Gate

Use for `EXCEPTIONAL`, deployment-impacting, irreversible-data, new security-boundary, or otherwise high-risk changes. For ordinary `FAST` and `STANDARD` work, the root performs the same proportionate evidence/diff checklist without spending a release-gatekeeper pass.

## Required inputs

Collect `MASTER_REQUEST`, approved assumptions/non-goals, numbered acceptance criteria, preserved baseline, technical package, work-package reports, finding/fix map, and every applicable independent verdict. A reviewer summary without concrete evidence is not a verdict.

## Gate matrix

Always require delivery-tier/checkpoint status, acceptance traceability, proportionate architecture and Spring review, focused regression evidence, documentation sync, Git status/full diff, unexpected-file audit, and build or application-start evidence proportionate to risk.

Conditionally require:

- API QA and generated OpenAPI/Postman checks for REST changes;
- applicable PostgreSQL/JPA/consistency/migration specialists from the risk triggers, plus real PostgreSQL tests for data changes;
- Redis design, TTL/invalidation/failure/consistency tests for Redis changes;
- independent security PASS and negative/cross-user tests when the security/ownership gate is triggered;
- resilience review and failure tests for integrations;
- performance/concurrency evidence for high-volume or concurrent behavior;
- observability, CI/deployment, health, smoke, migration, and rollback evidence for operational changes.

Run or inspect `python3 scripts/validate-agent-system.py` when applicable, project formatting status, static analysis status (`NOT_CONFIGURED` is accurate), relevant tests, `git diff --check`, `git status --short`, and the complete diff. Run `./gradlew build` or the full suite once after the last relevant edit when the tier/risk requires it; reuse observed evidence instead of rerunning an unchanged command. Distinguish baseline failures from introduced failures.

`RELEASE_READY` is allowed only when every applicable mandatory gate is PASS and no blocking finding remains. Non-blocking Medium/Low advice is residual improvement, not a reason for another release cycle. A checkpoint must report any targeted blocking correction or scope expansion; time alone neither blocks a correct release nor permits an unsafe one.
