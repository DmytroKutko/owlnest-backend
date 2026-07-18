# API Conventions

## HTTP boundary

- Business REST routes use `/api/v1/**` and require a validated bearer JWT.
- Feature controllers live under `<feature>/controller`, declare JSON media types, delegate to service classes, and return DTO records.
- Use plural resource paths for public collections/resources (`/profiles/{accountId}`) and a scoped singular current resource (`/profile/me`) where already established.
- HTTP requests map to request records. Convert them into service commands when normalization or boundary separation adds value.
- Never deserialize directly into or serialize a JPA entity.

Current operation IDs are `getCurrentProfile`, `completeProfileOnboarding`, `getPublicProfile`, `heartbeatPresence`, `createPost`, `getPost`, `replacePost`, `deletePost`, `createPostComment`, `listPostComments`, `setPostLiked`, `clearPostLiked`, `setPostBookmarked`, `clearPostBookmarked`, `setPostReposted`, and `clearPostReposted`. New IDs must be stable and unique.

## Validation and normalization

`@Valid` activates request DTO constraints before controller invocation. Current profile rules demonstrate `@NotBlank`, `@Size`, `@Pattern`, and `@Past`. Bean Validation protects the transport boundary; commands/domain methods and database constraints protect non-HTTP callers and stored invariants.

Document required/optional fields, blank/null semantics, normalization, enum values, date/time format, maximum lengths, and full-replacement versus patch behavior. Current `PUT /profile/me` is full replacement: required fields must be supplied and omitted optional fields are cleared.

## Responses and errors

Use concrete response DTOs for success. Use `204 No Content` only for successful operations with no body. Feature-scoped `@RestControllerAdvice` maps expected application exceptions to `ProblemDetail` with stable machine-readable `code` values.

Current feature codes:

| Status | Code | Meaning |
| --- | --- | --- |
| 404 | `profile.not_found` | missing or incomplete public profile |
| 404 | `post.not_found` | missing or soft-deleted post |
| 403 | `post.access_denied` | post write attempted by a non-author |
| 409 | `profile.username_conflict` | case-insensitive username conflict |
| 503 | `presence.unavailable` | heartbeat store write unavailable |

Missing/malformed bearer tokens use Spring Security's standard 401 and `WWW-Authenticate` behavior; do not document a JSON error body that does not exist. Profile/presence invalid DTO input uses framework validation behavior. The post feature deliberately maps validation and malformed post identifiers to Problem Details code `request.validation_failed`; any cross-feature validation format still requires a separate contract decision.

The implemented comment collection establishes one narrow pagination reference: only `limit` (default 20, range 1..100) and an opaque post-bound `v1.` cursor are accepted; repeated/unknown parameters fail with `request.validation_failed`; results use `(createdAt,id)` oldest-first keyset traversal and nested `page`/`links` metadata. It is live traversal, not a stable snapshot. Do not generalize it automatically to feeds or alternate sorting/filtering. ETag and generic request idempotency still have no implemented convention. Desired-state post interaction routes are idempotent, while every successful comment POST creates a distinct row.

## Authorization and privacy

OpenAPI declares `keycloakOAuth2` and `bearerAuth` as alternatives, but annotations do not enforce runtime security. `SecurityFilterChain` performs authentication; services must perform roles, permissions, and object ownership when needed.

Maintain separate private/public projections. `ProfileResponse` may expose the authenticated caller's email, verification, birth date, gender, and onboarding status. `PublicProfileResponse` must not.

## OpenAPI and Postman gate

`OpenApiConfiguration` exposes:

- `/v3/api-docs/rest` for `/api/**`;
- `/v3/api-docs/websocket`, intentionally empty and marked planned;
- Swagger UI at `/swagger-ui.html` with `REST API` primary.

Any REST contract change must update controller/DTO annotations, `OpenApiDocumentationIntegrationTests`, `API/OwlNest.postman_collection.json`, relevant API README workflow, and the owning feature document. Run tests against generated JSON. Future WebSocket channels/messages require AsyncAPI.

## Contract review checklist

Confirm route/method, operation ID, request/response fields and nullability, validation/normalization, success status/headers, every error status/body/code, authentication/authorization/ownership, idempotency/concurrency, pagination/filter/sort, backward compatibility, examples, privacy, OpenAPI generation, Postman, and regression tests.
