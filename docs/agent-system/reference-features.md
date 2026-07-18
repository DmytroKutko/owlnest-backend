# Reference Features

Use implemented references to guide new work. A Draft plan can inform discussion but cannot prove a convention.

## Reference 1: authenticated current profile

Best for: JWT boundary, local account provisioning, service transactions, project repository adapters, private DTOs, and PostgreSQL integration tests.

Evidence path:

```text
CurrentProfileController
  -> GetOrCreateCurrentProfileService (@Transactional)
  -> CurrentIdentityProvider / EnsureAccountExistsService
  -> AccountRepository + ProfileRepository
  -> Account/Profile JPA entities
  -> Flyway V1/V2
```

Supporting files: `identity/security/SecurityConfiguration.java`, `identity/service/**`, `profile/controller/CurrentProfileController.java`, `profile/service/GetOrCreateCurrentProfileService.java`, repositories, migrations, and `CurrentProfileControllerIntegrationTests.java`.

## Reference 2: profile onboarding and public projection

Best for: request records, Jakarta validation, command normalization, entity behavior, full-replacement PUT, feature exceptions, `ProblemDetail`, DTO/entity separation, and privacy-safe projections.

The request validates username/display name/bio/birth date. `CompleteProfileOnboardingCommand` normalizes strings; `Profile.completeOnboarding` owns state mutation. `ProfileExceptionHandler` scopes 404/409 mapping to profile controllers. `PublicProfileResponse` deliberately omits private fields.

Contract sources: `docs/features/profile-onboarding.md`, controller annotations, `/v3/api-docs/rest` assertions, and Postman.

## Reference 3: Redis presence

Best for: explicit Redis port/adapter, namespace, TTL, serialization, graceful read degradation, write failure mapping, Redis Testcontainers, and documentation of client refresh cadence.

Evidence: `presence/**`, Redis settings in `application.yaml`, Redis service in `compose.yaml`, Testcontainers service connection, TTL/API integration tests, and `docs/features/profile-presence-implementation-plan.md`.

Do not generalize presence into caching, sessions, locks, or pub/sub. It is a narrow ephemeral use case.

## Reference 4: OpenAPI boundary

Best for: stable operation IDs, security schemes, REST group filtering, public documentation endpoints, and generated-contract tests.

Evidence: `foundation/openapi/OpenApiConfiguration.java`, controller Swagger annotations, `OpenApiDocumentationIntegrationTests.java`, `docs/api-documentation.md`, and `API/OwlNest.postman_collection.json`.

## Reference 5: append-only post comments

Best for: an authenticated nested collection, exact Unicode preservation, server-owned author/timestamps, active-parent locking, denormalized counter consistency, oldest-first keyset cursors, bounded cross-feature safe-summary hydration, and forward constraint migration.

Evidence path:

```text
PostCommentController
  -> CreatePostCommentService / ListPostCommentsService
  -> Post + PostComment
  -> PostRepository + PostCommentRepository
  -> GetProfileSummaryService batch safe projection
  -> Flyway V4/V5
```

Use this narrowly. Normal comment POST is append-only and non-idempotent; desired-state interaction PUT is a different contract. The `v1.` cursor is post-bound and oldest-first, not a generic feed pagination policy. There is no comment edit/delete/reply/moderation behavior, Redis path, event, or notification.

## Draft-only material

- `docs/architecture.md` contains accepted direction plus future Draft roadmap.
- `docs/features/posts-architecture-plan.md` is the implemented source for the single-post CRUD/card, comments, and like/bookmark/repost slice.

Treat only the implemented post package, Flyway V3–V5, generated OpenAPI, tests, and that feature document as current post evidence. Feed/post-list pagination, managed media upload, views, and comment mutation/moderation still require fresh business and technical gates; do not infer them from older Draft discussions.
