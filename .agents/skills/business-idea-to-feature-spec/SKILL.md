---
name: business-idea-to-feature-spec
description: Convert a materially ambiguous OwlNest product idea into an explicit business specification and traceable acceptance criteria before technical design.
---

# Business Idea to Feature Specification

Use when a request materially lacks actors, permissions, state transitions, failure rules, or an approved feature contract. Conversational or dictated wording alone is not a trigger: when behavior is clear, the root records concise assumptions and acceptance criteria without spending an extra analyst pass.

## Business analysis

Give `business_analyst` one bounded pass with the preserved original idea, current product context, exact missing decisions, delivery target, and output cap. Require only applicable sections from:

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

Use `requirements_acceptance_analyst` only when several rules or edge cases need independent traceability and that work materially reduces ambiguity. Require `MASTER_REQUEST`, numbered `AC-001` criteria, only applicable positive/negative/risk cases, regression scope, and a verification method for each criterion.

Every `BR-*` must map to at least one `AC-*`; no criterion may silently weaken a business rule. Technical specialists receive the business spec, acceptance criteria, assumptions, non-goals, and unresolved questions together.
