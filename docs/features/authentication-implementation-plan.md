# Authentication Implementation Plan

**Status:** Planned — no feature code has been created.

This blueprint describes how to implement the first authenticated OwlNest slice manually. File names may be refined during implementation, but boundaries and responsibilities should remain stable unless an ADR changes them.

## Delivery Stages

### Stage 1: Keycloak Development Infrastructure

Planned files and changes:

```text
compose.yaml                              # add Keycloak service and health/dependency wiring
.env / .env.example                      # realm, port, bootstrap admin, issuer, audience
docker/keycloak/owlnest-realm.json       # versioned development realm/client configuration
src/main/resources/application.yaml      # Resource Server issuer/audience/JWK settings
docs/features/authentication.md           # keep runtime contract current
```

Keycloak will run in development mode only. The realm import contains clients, roles, redirect URIs, and non-secret development configuration. Real credentials remain in `.env`.

Before implementation, choose a canonical issuer reachable from the Flutter targets. The containerized backend may use an internal JWK URL while still validating the external `iss` value.

### Stage 2: JWT Boundary

```text
src/main/java/dev/dkutko/owlnest/identity/
├── application/
│   ├── AuthenticatedIdentity.java        # provider-neutral identity record
│   └── CurrentIdentityProvider.java      # application port for the current request identity
└── infrastructure/security/
    ├── SecurityConfiguration.java        # endpoint rules and Resource Server setup
    └── SpringSecurityIdentityProvider.java # maps validated Jwt to AuthenticatedIdentity

src/test/java/dev/dkutko/owlnest/identity/infrastructure/security/
└── SecurityConfigurationTest.java
```

`AuthenticatedIdentity` contains only values needed by application code: provider, subject, email, verification state, and selected authorities. The application layer does not depend on `Jwt`, `SecurityContextHolder`, or Keycloak-specific classes.

`SecurityConfiguration` will provide a `SecurityFilterChain`: public health, authenticated `/api/v1/**`, stateless sessions, bearer JWT, and explicit `401/403` behavior. Do not add `@EnableMethodSecurity` until a use case actually needs method-level authorization.

### Stage 3: Local Account Persistence

```text
src/main/java/dev/dkutko/owlnest/identity/
├── domain/
│   ├── Account.java                      # local UUID and external identity mapping
│   └── AccountRepository.java            # persistence port
├── application/
│   └── EnsureAccountExistsService.java   # idempotent provisioning transaction
└── infrastructure/persistence/
    ├── SpringDataAccountRepository.java  # Spring Data implementation surface
    └── JpaAccountRepositoryAdapter.java  # adapts Spring Data to the domain port

src/main/resources/db/migration/
└── V1__create_identity_and_profile_tables.sql
```

Use one JPA-annotated account model rather than duplicating domain and persistence objects with identical fields. Retain a repository port and small adapter so application code does not depend directly on `JpaRepository`.

Provisioning alternatives:

1. **Explicit use-case call — chosen.** `/profile/me` calls provisioning. The transaction and write are visible and testable.
2. Custom `OncePerRequestFilter` — rejected initially. It hides a database write in infrastructure and runs for unrelated endpoints.
3. Keycloak event/webhook synchronization — deferred. It adds delivery/retry complexity before it is needed.

### Stage 4: Current Profile Endpoint

```text
src/main/java/dev/dkutko/owlnest/profile/
├── api/
│   ├── CurrentProfileController.java
│   └── ProfileResponse.java
├── application/
│   └── GetOrCreateCurrentProfileService.java
├── domain/
│   ├── Profile.java
│   └── ProfileRepository.java
└── infrastructure/persistence/
    ├── SpringDataProfileRepository.java
    └── JpaProfileRepositoryAdapter.java

src/test/java/dev/dkutko/owlnest/profile/
├── api/CurrentProfileControllerTest.java
└── application/GetOrCreateCurrentProfileServiceTest.java
```

The controller reads no raw token and contains no provisioning logic. It calls the application service and maps the returned profile to a response DTO. JPA entities are never serialized directly.

### Stage 5: Error Contract and Integration Verification

Planned only if Spring Security's standard error response is insufficient for the Flutter contract:

```text
src/main/java/dev/dkutko/owlnest/shared/api/
└── ApiErrorResponse.java

src/main/java/dev/dkutko/owlnest/identity/infrastructure/security/
├── RestAuthenticationEntryPoint.java     # missing/invalid token -> 401
└── RestAccessDeniedHandler.java           # insufficient authority -> 403
```

Prefer the standard `WWW-Authenticate` behavior first. Add a custom JSON body only when the client contract needs stable machine-readable error codes.

## Dependency Plan

No Gradle dependency change is required before implementation. The verified runtime graph already contains:

| Existing dependency | Planned use |
| --- | --- |
| `spring-boot-starter-security` | Security filter chain, stateless request authorization, `401/403`. |
| `spring-boot-starter-security-oauth2-resource-server` | Bearer token filter, `JwtDecoder`, issuer/JWK discovery, claim validation. |
| `spring-security-oauth2-jose` (transitive) | JWT parsing, signature validation, JWK support. |
| `spring-boot-starter-data-jpa` | Account/profile persistence through Hibernate and Spring Data. |
| `spring-boot-starter-flyway` | Versioned identity/profile schema migration. |
| `spring-boot-starter-validation` | Future profile update request validation. |
| `spring-boot-starter-webmvc` | `/api/v1/profile/me` REST endpoint. |
| Resource Server and Security test starters | Mock JWT and authorization behavior in MVC tests. |
| PostgreSQL Testcontainers | Real database integration and uniqueness tests. |

Dependencies intentionally not added:

- `spring-boot-starter-oauth2-client`: Flutter, not the backend, is the OAuth client.
- Keycloak Java adapter: standard Spring Security OIDC/JWT support is sufficient and avoids provider coupling.
- Spring Authorization Server: Keycloak issues tokens.
- Firebase Admin SDK: unrelated to the selected identity path; add later only for FCM.
- Spring Modulith: useful for module verification later, but not required to implement authentication.

## Annotation Plan

Each annotation will be added to `docs/annotations.md` when first implemented and explained before use:

| Annotation | Intended location and reason |
| --- | --- |
| `@Configuration` | Security configuration source processed while the application context starts. |
| `@Bean` | Registers `SecurityFilterChain` and any explicit converters/validators. |
| `@Service` | Application use-case implementation discovered as a singleton Spring bean. |
| `@Transactional` | Defines account/profile provisioning as one database transaction. |
| `@Entity`, `@Table`, `@Id`, `@Column` | Map account/profile Java state to the Flyway-created schema. |
| `@Repository` | Marks persistence adapters and enables persistence exception translation. |
| `@RestController`, `@RequestMapping`, `@GetMapping` | Expose the current-profile HTTP boundary. |

Avoid redundant annotations. In particular, Spring Boot can configure web security from a `SecurityFilterChain` bean without adding annotations solely out of habit.

## Manual Implementation Sequence

For each stage:

1. Update the relevant document and define acceptance criteria.
2. Add only the required configuration or classes.
3. Explain new annotations and framework lifecycle before using them.
4. Format and run static checks.
5. Run focused unit/MVC tests.
6. Run PostgreSQL Testcontainers integration tests.
7. Run the local Keycloak smoke flow when applicable.
8. Review the exact diff and architecture boundaries before committing.

The next implementation turn should start with Stage 1 only; it should not create all planned files at once.
