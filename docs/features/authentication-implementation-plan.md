# Authentication Implementation Plan

**Status:** Implemented and verified in the local Docker stack.

This blueprint describes how to implement the first authenticated OwlNest slice manually. File names may be refined during implementation, but boundaries and responsibilities should remain stable unless an ADR changes them.

## Delivery Stages

### Stage 1: Keycloak Development Infrastructure

Implemented files and changes:

```text
compose.yaml                              # add Keycloak service and health/dependency wiring
.env / .env.example                      # local ports and development credentials
docker/keycloak/owlnest-realm.json       # versioned development realm/client configuration
src/main/resources/application.yaml      # Resource Server issuer/audience/JWK settings
docs/features/authentication.md           # keep runtime contract current
```

Keycloak runs in development mode only. The realm import contains clients, roles, redirect URIs, and non-secret development configuration. Real credentials remain in `.env`.

The local canonical issuer is `http://localhost:8081/realms/owlnest`. The containerized backend uses the internal Keycloak JWK URL while still validating that external `iss` value. Device-reachable issuer configuration remains a separate deployment concern.

### Stage 2: JWT Boundary

```text
src/main/java/dev/dkutko/owlnest/identity/
├── service/
│   ├── AuthenticatedIdentity.java        # provider-neutral identity record used by services
│   └── CurrentIdentityProvider.java      # interface for the current request identity
└── security/
    ├── SecurityConfiguration.java        # endpoint rules and Resource Server setup
    └── SpringSecurityIdentityProvider.java # maps validated Jwt to AuthenticatedIdentity

src/test/java/dev/dkutko/owlnest/identity/security/
└── SpringSecurityIdentityProviderTest.java
```

`AuthenticatedIdentity` contains only values needed by service code: provider, subject, email, verification state, username, and standard name claims. Service classes do not depend on `Jwt`, `SecurityContextHolder`, or Keycloak-specific classes.

`SecurityConfiguration` provides a `SecurityFilterChain`: public health, authenticated `/api/v1/**`, stateless sessions, bearer JWT, and Spring Security's standard bearer-token error behavior. Do not add `@EnableMethodSecurity` or custom `403` handling until a use case actually needs role-based authorization.

### Stage 3: Local Account Persistence

```text
src/main/java/dev/dkutko/owlnest/identity/
├── domain/
│   └── Account.java                      # local UUID and external identity mapping
├── service/
│   └── EnsureAccountExistsService.java   # idempotent provisioning transaction
└── repository/
    ├── AccountRepository.java            # repository interface used by services
    ├── AccountRepositoryImpl.java        # repository implementation
    └── SpringDataAccountRepository.java  # Spring Data JPA interface

src/main/resources/db/migration/
└── V1__create_identity_and_profile_tables.sql
```

Use one JPA-annotated account model rather than duplicating domain and persistence objects with identical fields. Retain a small repository interface and implementation so service code does not depend directly on `JpaRepository`.

Provisioning alternatives:

1. **Explicit use-case call — chosen.** `/profile/me` calls provisioning. The transaction and write are visible and testable.
2. Custom `OncePerRequestFilter` — rejected initially. It hides a database write in infrastructure and runs for unrelated endpoints.
3. Keycloak event/webhook synchronization — deferred. It adds delivery/retry complexity before it is needed.

### Stage 4: Current Profile Endpoint

```text
src/main/java/dev/dkutko/owlnest/profile/
├── controller/
│   ├── CurrentProfileController.java
│   └── ProfileResponse.java
├── service/
│   ├── CurrentProfile.java
│   └── GetOrCreateCurrentProfileService.java
├── domain/
│   └── Profile.java
└── repository/
    ├── ProfileRepository.java
    ├── ProfileRepositoryImpl.java
    └── SpringDataProfileRepository.java

src/test/java/dev/dkutko/owlnest/
└── CurrentProfileControllerIntegrationTests.java
```

The controller reads no raw token and contains no provisioning logic. It calls the service and maps the returned profile to a response DTO. JPA entities are never serialized directly.

### Stage 5: Error Contract and Integration Verification

Planned only if Spring Security's standard error response is insufficient for the Flutter contract:

```text
src/main/java/dev/dkutko/owlnest/shared/controller/
└── ApiErrorResponse.java

src/main/java/dev/dkutko/owlnest/identity/security/
├── RestAuthenticationEntryPoint.java     # missing/invalid token -> 401
└── RestAccessDeniedHandler.java           # insufficient authority -> 403
```

Prefer the standard `WWW-Authenticate` behavior first. Add a custom JSON body only when the client contract needs stable machine-readable error codes.

## Dependency Use

No build dependency change was required for this slice. The verified runtime graph already contains:

| Existing dependency | Current or intended use |
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
- Keycloak-specific Java integration: standard Spring Security OIDC/JWT support is sufficient and avoids provider coupling.
- Spring Authorization Server: Keycloak issues tokens.
- Firebase Admin SDK: unrelated to the selected identity path; add later only for FCM.
- Spring Modulith: useful for module verification later, but not required to implement authentication.

## Annotation Use

Each introduced annotation is recorded in `docs/annotations.md` and was explained before use:

| Annotation | Intended location and reason |
| --- | --- |
| `@Configuration` | Security configuration source processed while the application context starts. |
| `@Bean` | Registers `SecurityFilterChain` and any explicit converters/validators. |
| `@Service` | Service/use-case implementation discovered as a singleton Spring bean. |
| `@Transactional` | Defines account/profile provisioning as one database transaction. |
| `@Entity`, `@Table`, `@Id`, `@Column` | Map account/profile Java state to the Flyway-created schema. |
| `@Repository` | Marks repository implementations and enables persistence exception translation. |
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

Stages 1–4 are implemented. Stage 5 intentionally keeps Spring Security's standard `401` response until the Flutter client requires a stable JSON error contract.
