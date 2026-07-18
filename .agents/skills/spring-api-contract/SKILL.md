---
name: spring-api-contract
description: Design or change an OwlNest REST contract, including DTOs, validation, errors, security, OpenAPI, Postman, and compatibility evidence.
---

# Spring API Contract

Use whenever an HTTP path, method, payload, status, validation rule, security requirement, pagination/filter/sort behavior, or error contract changes.

## Current conventions

- Versioned routes use `/api/v1/**`; the security filter requires JWT authentication for all of them.
- Controllers live in `<feature>/controller`, delegate to `<feature>/service`, and use request/response records rather than entities.
- Jakarta Bean Validation guards request DTOs; application/domain invariants remain enforced outside HTTP.
- Swagger annotations provide stable `operationId`, summary, response codes, and alternative `keycloakOAuth2`/`bearerAuth` requirements.
- Feature errors use RFC 9457-style Spring `ProblemDetail` with a stable `code` property. Current codes include `profile.username_conflict`, `profile.not_found`, `presence.unavailable`, `post.not_found`, `post.access_denied`, and `request.validation_failed`.
- Missing/invalid bearer tokens currently use Spring Security's standard `401`/`WWW-Authenticate` response with no custom JSON body.
- Current public APIs expose current/public profiles, presence heartbeat, single-post CRUD/card behavior, and desired-state like/bookmark/repost interactions. Public profile and post-author projections exclude email, verification, birth date, gender, and onboarding state. Feed/list and persisted comments remain separate capabilities until implemented.
- REST OpenAPI is `/v3/api-docs/rest`; keep the empty `WebSocket API (planned)` group. Future realtime channels use AsyncAPI, not fake REST operations.

## Contract package

Define endpoint table, schemas with nullability, examples, validation, authorization/ownership, idempotency, concurrency, pagination/filter/sort/cursor semantics, statuses/errors, compatibility, and API acceptance criteria. Prevent accidental entity exposure.

Any REST change must update together:

1. controller and DTO/OpenAPI annotations;
2. generated-contract assertions in `OpenApiDocumentationIntegrationTests`;
3. `API/OwlNest.postman_collection.json`;
4. `API/README.md` when workflow changes;
5. owning `docs/features/*.md` and annotation glossary for new annotations.

Verify runtime behavior and `/v3/api-docs/rest`; annotations alone are not proof.
