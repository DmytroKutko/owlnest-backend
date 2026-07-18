# Implemented Domain Map

## Runtime entry and foundation

`OwlnestBackendApplication` scans `dev.dkutko.owlnest`. `foundation.openapi.OpenApiConfiguration` defines two springdoc groups: implemented `/api/**` REST and an empty planned `/ws/**` group.

Every `/api/v1/**` request first passes `identity.security.SecurityConfiguration`. A validated bearer JWT becomes a `JwtAuthenticationToken`; `SpringSecurityIdentityProvider` converts it into `AuthenticatedIdentity` without leaking Spring Security types into feature services.

## Identity module

**Owns:** external-to-local identity mapping and local account lifecycle.

| Role | Types |
| --- | --- |
| Domain/JPA | `identity.domain.Account` |
| Service contracts/logic | `AuthenticatedIdentity`, `CurrentIdentityProvider`, `EnsureAccountExistsService` |
| Security adapter | `SpringSecurityIdentityProvider`, `SecurityConfiguration` |
| Persistence port/adapter | `AccountRepository`, `AccountRepositoryImpl`, `SpringDataAccountRepository` |
| PostgreSQL | `identity_account` from `V1__create_identity_and_profile_tables.sql` |

`EnsureAccountExistsService.ensureExists` runs transactionally, finds `(provider, subject)`, refreshes email/verification/last-seen, or saves a new UUID account.

## Profile module

**Owns:** OwlNest product profile, onboarding/replacement, private/current projection, and safe public projection.

| Role | Types |
| --- | --- |
| HTTP | `CurrentProfileController`, `PublicProfileController`, request/response records, `ProfileExceptionHandler` |
| Services | `GetOrCreateCurrentProfileService`, `GetPublicProfileService`, commands/results/exceptions |
| Domain/JPA | `Profile`, `Gender` |
| Persistence port/adapter | `ProfileRepository`, `ProfileRepositoryImpl`, `SpringDataProfileRepository` |
| PostgreSQL | `profile` from Flyway V1/V2 |

Private/current flow:

```text
GET or PUT /api/v1/profile/me
  -> CurrentProfileController
  -> GetOrCreateCurrentProfileService
  -> CurrentIdentityProvider
  -> EnsureAccountExistsService -> identity_account
  -> ProfileRepository -> profile
  -> CurrentProfile -> ProfileResponse
```

PUT converts `ProfileOnboardingRequest` to a normalized command, checks case-insensitive username availability, mutates `Profile.completeOnboarding`, and commits through JPA dirty checking. The private response includes email, verification, birth date, gender, and onboarding state.

First-use account/profile creation uses miss-only PostgreSQL transaction advisory locks and rechecks to make concurrent provisioning deterministic. Established account/profile lookups do not acquire those locks. Until explicit onboarding, public-facing author defaults are the generated `user_<id>` and neutral `OwlNest user`; JWT name/email claims remain private.

Public flow:

```text
GET /api/v1/profiles/{accountId}
  -> PublicProfileController
  -> GetPublicProfileService
  -> ProfileRepository (completed profiles only)
  -> PresenceService -> Redis
  -> PublicProfile -> PublicProfileResponse
```

The public response includes account ID, username, display name, bio, and presence. It excludes email, verification, birth date, gender, and onboarding state. Missing or incomplete profiles produce `profile.not_found`.

## Presence module

**Owns:** short-lived online status, not authentication or durable activity history.

| Role | Types |
| --- | --- |
| HTTP | `PresenceController`, `PresenceExceptionHandler` |
| Service | `PresenceService`, `PresenceStatus` |
| Port/adapter | `PresenceRepository`, `RedisPresenceRepository` |
| Redis | `presence:account:{accountId}` -> ISO Instant, TTL 90 seconds |

Heartbeat flow provisions/refreshes the local account, then writes Redis. A write failure returns `503`; a public-profile status read degrades to `UNKNOWN`.

## Post module

**Owns:** authenticated single-post lifecycle, ordered labels/media references, append-only comments, viewer interactions, public counters, and card assembly.

| Role | Types |
| --- | --- |
| HTTP | `PostController`, `PostCommentController`, `PostInteractionController`, request/response records, `PostExceptionHandler` |
| Services | CRUD services, create/list comment services, cursor codec, `PostInteractionService`, `PostCardQueryService`, commands/results/exceptions |
| Domain/JPA | scalar `Post`, append-only scalar `PostComment`, `PostType`, `PostMedia`, `PostMediaType` |
| Persistence | project ports/adapters plus package-private Spring Data repositories and bounded JDBC projections |
| PostgreSQL | post/content/interaction tables from Flyway V3, `post_comment` from V4, and final counter validation from V5 |

```text
POST/GET/PUT/DELETE /api/v1/posts[/<id>]
  -> post.controller
  -> transactional post.service
  -> post repository ports -> PostgreSQL
  -> ProfileSummary service -> safe author fields

PUT/DELETE /api/v1/posts/<id>/(likes|bookmark|repost)
  -> PostInteractionService
  -> active post row lock
  -> relation transition + conditional counter delta in one transaction

POST /api/v1/posts/<id>/comments
  -> CreatePostCommentService
  -> active post row lock
  -> monotonic append + comment counter increment in one transaction

GET /api/v1/posts/<id>/comments
  -> ListPostCommentsService
  -> bounded active-post keyset query
  -> batch safe profile summaries
```

`Post` owns its lifecycle and counters; `PostComment` is an independently paged scalar persistence root with only UUID foreign keys, not an unbounded JPA association. Labels/media and interaction memberships are bounded JDBC-managed rows, not exposed entities. PostgreSQL is authoritative; post code does not use Redis or external media clients. The card retains its Flutter `#comments` navigation hook while the REST comment collection uses `/comments`.

## Cross-module edges

- `profile.service` uses public `identity.service` contracts for current identity/account provisioning.
- `presence.service` uses public `identity.service` contracts.
- `profile.service.GetPublicProfileService` uses public `presence.service.PresenceService`.
- `post.service` uses public profile services for current-account provisioning and safe author summaries.
- No module imports another module's repository implementation or Spring Data interface.

## Not implemented

Social graph, post feed/list, comment edit/delete/replies/moderation, managed media upload/storage, notification, messaging, WebSocket delivery, FCM, R2, queues, schedulers, and external HTTP clients have no production packages. Their documentation is roadmap material. Do not cite proposed types, tables, endpoints, or rules as repository facts.
