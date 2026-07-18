---
name: project-documentation-sync
description: Keep OwlNest product, architecture, API, database, Redis, annotation, Postman, and operations documentation synchronized without duplicating authority.
---

# Project Documentation Sync

Use whenever behavior, architecture, API contracts, schema, Redis behavior, security, configuration, operations, annotations, or workflow guidance changes.

## Authority order

1. User's current approved decision and accepted ADRs.
2. Implemented code, migrations, configuration, and passing contract/integration tests.
3. Implemented feature documents and `docs/README.md` index.
4. Draft architecture/feature plans, which remain proposals until accepted.

Do not silently resolve a conflict. Mark uncertain claims `NEEDS_CONFIRMATION`, cite the conflicting evidence, and avoid copying a Draft rule into durable orchestration as fact.

## Owning documents

- Product direction and collaboration: `docs/project-context.md`.
- Difficult-to-reverse decisions: accepted ADR under `docs/decisions/`.
- Feature behavior/examples/non-goals: owning `docs/features/*.md`.
- REST workflow: controller annotations, OpenAPI integration test, Postman collection, API README, and owning feature doc in one change.
- New Spring/Jakarta annotations: `docs/annotations.md` with purpose, processor, lifecycle, scope, pitfalls, and tests.
- Current system architecture: accepted ADRs plus `docs/agent-system/architecture-invariants.md` and `domain-map.md`; keep future roadmaps labeled Draft.
- Database/Redis/security/testing/operations conventions: corresponding `docs/agent-system/*.md` file.
- Agent routing/handoffs: `.agents/skills/**`, concise `AGENTS.md`, and `docs/agent-system/`.

Update `docs/README.md` when adding a durable project document. Prefer links to one owning source over duplicated prose. Verify every path/class/table/endpoint/key claim against the repository and run `python3 scripts/validate-agent-system.py` after agent-system documentation changes.
