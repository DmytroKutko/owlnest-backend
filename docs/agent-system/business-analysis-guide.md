# Business Analysis Guide

OwlNest has product direction and several implemented feature contracts, but no complete product requirements catalog. New requests may arrive as dictated ideas. Business analysis therefore precedes technical design whenever actors, permissions, rules, states, failure behavior, or scope are incomplete.

## Source discipline

Preserve the user's exact idea as `ORIGINAL_IDEA` and `MASTER_REQUEST`. Read `docs/project-context.md`, accepted ADRs, implemented feature docs, current code/tests, and relevant Draft plans. A Draft can reveal questions or options; it cannot supply approved behavior silently.

Separate three things:

1. **Requirement:** behavior the user/product explicitly needs.
2. **Suggestion:** a proposed implementation such as Redis, an endpoint, or a table.
3. **Assumption:** temporary behavior selected to make progress because evidence is missing.

## Analysis sequence

1. State business objective and measurable outcome.
2. Identify actors, roles, goals, resource ownership, and trust boundaries.
3. Describe the happy path in user language.
4. Add alternative, cancellation, retry, duplicate, timeout, and failure flows.
5. Extract stable rules (`BR-001`) with preconditions and postconditions.
6. Build an actor/action/resource/state permissions matrix.
7. Model states and only allowed transitions; record terminal/reversible states.
8. Define validation semantics and business errors without choosing HTTP or SQL yet.
9. Give concrete examples and Given/When/Then scenarios.
10. List non-goals, assumptions with rationale, unresolved questions with impact, and contradictions.

Do not design packages, classes, endpoints, schemas, migrations, Redis keys, queues, or annotations during this phase.

## Assumption policy

Use a documented assumption when it is reversible, consistent with established product direction, and does not create security/data-loss/contract risk. Ask the user when a missing decision materially changes visibility, money, permissions, deletion, irreversible data, legal/privacy obligations, or external behavior. If the user is unavailable, return `BLOCKED` rather than inventing a dangerous rule.

## Business output template

```text
ORIGINAL_IDEA
BUSINESS_OBJECTIVE
ACTORS_AND_ROLES
PRIMARY_FLOWS
ALTERNATIVE_AND_FAILURE_FLOWS
BUSINESS_RULES (BR-###)
PERMISSIONS_MATRIX
STATE_TRANSITIONS
VALIDATION_RULES
ASSUMPTIONS (ASSUMPTION-###)
UNRESOLVED_QUESTIONS (QUESTION-### + impact)
NON_GOALS
BUSINESS_ACCEPTANCE_SCENARIOS
VERDICT: READY_FOR_REQUIREMENTS | BLOCKED
```

## Requirements handoff quality

`requirements_acceptance_analyst` turns the root-approved business output into `AC-###` criteria. Every rule maps to a criterion; every criterion states verification evidence. Cover positive, negative, authorization, ownership, validation, consistency, concurrency/idempotency, errors, compatibility, and regression only where relevant. Mark irrelevant categories `NOT_APPLICABLE` with reason rather than adding fictional requirements.
