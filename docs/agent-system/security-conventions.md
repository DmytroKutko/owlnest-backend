# Security Conventions

## Identity boundary

Keycloak is the OAuth2/OIDC identity provider. It owns registration, passwords, verification, recovery, access/refresh/ID tokens, signing keys, and identity-provider sessions. Flutter and Swagger use Authorization Code + PKCE. The Postman Direct Access Grant is local-development-only.

OwlNest Backend is a stateless OAuth2 Resource Server. It validates JWT signature, issuer, audience `owlnest-api`, expiration, not-before, and related decoder requirements before a controller runs. The configured external issuer and internal JWK URL are separate so container networking does not weaken issuer validation.

`SpringSecurityIdentityProvider` maps only a validated `JwtAuthenticationToken` to provider-neutral claims. Product services use local `identity_account.id`; business foreign keys never store raw Keycloak `sub`. Passwords and tokens must not enter OwlNest request DTOs, databases, Redis, or logs.

## HTTP policy

Current filter policy:

- public: `/actuator/health`, `/actuator/health/**`, `/error`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`;
- authenticated: `/api/v1/**`;
- denied: everything else.

The API disables form login, Basic auth, logout endpoint, request cache, and HTTP session creation. CSRF is disabled only because credentials arrive in bearer headers rather than cookies. Any future cookie-authenticated surface must revisit CSRF. No explicit CORS policy is configured.

## Authorization

Authentication is not authorization. No method security or application role checks exist yet. When a feature introduces permissions, roles, administration, or user-owned mutable resources:

1. define actor/action/resource/state matrix;
2. map trusted token claims/roles explicitly or use local authorization state;
3. enforce object ownership in the service/use case, not only in UI or path shape;
4. return 401 for unauthenticated and 403 for authenticated-but-forbidden behavior; do not reveal private resource existence unless approved;
5. test another user, guessed IDs, absent/wrong roles, forbidden states, and races;
6. run an independent security review.

## Sensitive data

The current public profile excludes email, verification status, birth date, gender, and onboarding state. Keep separate private/public DTOs. Redact bearer tokens, authorization headers, passwords, admin credentials, email/birth date where unnecessary, and Redis values/keys if future keys expose sensitive identifiers.

Secrets belong in `.env` locally (ignored), protected environment variables, or an external secret manager. `.env.example` contains safe placeholders. Generated Postman environments are ignored and owner-readable only.

## Known decisions and uncertainties

- Local Keycloak has `verifyEmail: false` because SMTP is absent. Do not enable mandatory verification/reset delivery without tested mail.
- Public Swagger/OpenAPI and health are deliberate locally; public deployment exposure is **NEEDS_CONFIRMATION**.
- Role-specific 403 behavior, CORS, rate limits, production TLS/issuer hostnames, Keycloak deployment, secret manager, and audit policy are **NEEDS_CONFIRMATION**.
