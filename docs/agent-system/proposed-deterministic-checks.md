# Proposed Deterministic Checks

The repository currently has no ArchUnit, Checkstyle, SpotBugs, PMD, Error Prone, Sonar, or OpenAPI diff dependency. This bootstrap does not alter dependencies.

`scripts/validate-agent-system.py` safely checks source-text invariants that are objective today: controller-to-repository imports, controller imports of known `@Entity` types, domain dependencies on controller/repository/security/Spring infrastructure outside Jakarta Persistence, cross-feature repository imports, `@Transactional` outside services, migration naming, H2 absence, ignored secrets, and agent-system structure.

## Future ArchUnit proposal

If text checks become insufficient, consider a test-only ArchUnit dependency after explicit approval. Candidate rules:

- controllers depend on services/DTOs, not repositories;
- domain packages do not depend on controller, security, Redis, or Spring Data packages;
- cross-feature repository imports are forbidden;
- classes annotated `@Entity` are not controller return/import types;
- `@Transactional` belongs to approved service/application packages;
- Spring Data repository interfaces remain feature-internal.

Calibrate rules against current pragmatic JPA-domain entities before enforcing them. Do not ban Jakarta Persistence from `domain` while that accepted simplification remains.

## Migration immutability proposal

CI can compare checksums or Git diffs for migrations present on the protected base branch and reject modifications while allowing new versions. The exact base ref and release process are **NEEDS_CONFIRMATION**; a local script cannot know which migrations have reached shared environments.

## API compatibility proposal

Once the project exports a canonical OpenAPI artifact, CI can generate `/v3/api-docs/rest` and run an approved breaking-change comparator. No generated spec is currently tracked, so annotations plus integration assertions remain the evidence.

## Secret scanning proposal

Add a repository-approved scanner in CI when CI exists. Current deterministic checks only verify that `.env`, generated Postman environments, and key/certificate patterns are ignored/not tracked; content-aware secret detection needs tool selection and false-positive policy.
