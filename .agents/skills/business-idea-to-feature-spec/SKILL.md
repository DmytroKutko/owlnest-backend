---
name: business-idea-to-feature-spec
description: Convert an informal or dictated OwlNest product idea into an explicit business specification and traceable acceptance criteria before technical design.
---

# Business Idea to Feature Specification

Use when a request is conversational, incomplete, lacks actors/permissions/rules, or has no approved feature contract. This is Gate A and the business half of Gate B; it must finish before Java, API, SQL, or Redis design.

## Business analysis

Give `business_analyst` the preserved original idea plus current product context. Require:

- original idea and business objective;
- actors, roles, goals, preconditions, and postconditions;
- primary, alternative, cancellation, and failure flows;
- stable business-rule IDs (`BR-001`);
- permissions matrix and object-ownership rules;
- states and allowed/forbidden transitions;
- validation and exception behavior in business language;
- examples, assumptions, unresolved questions, and explicit non-goals;
- Given/When/Then business acceptance scenarios.

Do not let the business analysis name endpoints, classes, tables, migrations, Redis keys, or Spring annotations. Separate user requirements from implementation suggestions.

## Missing decisions

Ask the user only when a missing choice materially changes product behavior and no safe assumption exists. Otherwise record `ASSUMPTION-N` with rationale and `QUESTION-N` with impact. Never hide an assumption.

## Requirements handoff

After root approves coherence, give the complete business output to `requirements_acceptance_analyst`. Require `MASTER_REQUEST`, numbered `AC-001` criteria, positive and negative cases, authorization/ownership, validation, errors, data consistency, concurrency/idempotency, pagination/filter/sort, compatibility, regression scope, and a verification method for each criterion.

Every `BR-*` must map to at least one `AC-*`; no criterion may silently weaken a business rule. Technical specialists receive the business spec, acceptance criteria, assumptions, non-goals, and unresolved questions together.
