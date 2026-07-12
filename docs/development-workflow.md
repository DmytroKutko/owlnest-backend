# Feature Development Workflow

Use this workflow for every non-trivial OwlNest feature.

## 1. Investigate

Read the current implementation, architecture, contracts, migrations, and relevant tests. Confirm the problem and avoid coding from assumptions.

## 2. Propose the Boundary

Describe the user flow, owning feature module, neighboring modules, data ownership, API surface, and explicit non-goals. Compare meaningful alternatives with project-specific advantages and disadvantages.

## 3. Audit Dependencies

List which existing dependencies will be used and how. Add a dependency only if the current slice requires it and existing APIs cannot provide the capability. Explain why plausible alternatives are not added.

## 4. Provide a File Blueprint

Before implementation, list every planned file or modified configuration surface with one responsibility per item. Distinguish files needed now from later stages so the first change remains small.

## 5. Explain Framework Behavior

For every new Spring or Jakarta annotation, explain its purpose, processor, startup/request/transaction timing, scope, correct use, and pitfalls. Also explain important behavior that comes from configuration or conventions rather than annotations.

## 6. Agree, Then Implement Incrementally

Treat architecture proposals as Draft until accepted. After agreement, implement one vertical slice at a time. Do not scaffold empty future modules or speculative abstractions.

## 7. Verify and Review

Review all changed files and module boundaries. Run formatting, static analysis, focused tests, integration tests, and proportionate runtime smoke checks. Report remaining risks before committing.

## Expected Planning Output

Each feature should have:

- a feature contract in `docs/features/`;
- an implementation blueprint when the change is non-trivial;
- an ADR in `docs/decisions/` for significant or difficult-to-reverse choices;
- updated annotation documentation as implementation introduces annotations;
- a short verified completion summary.
