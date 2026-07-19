---
name: spring-security-gate
description: Gate an OwlNest authentication, authorization, ownership, sensitive-data, or public-endpoint change with independent negative testing.
---

# Spring Security Gate

Use for a new or changed authentication boundary, role/permission/ownership rule, admin behavior, sensitive data, upload, public endpoint, token handling, or Security/Keycloak configuration. Merely adding an endpoint under the existing authenticated `/api/v1/**` matcher does not trigger the full independent security gate.

## Current boundary

- Keycloak owns registration, credentials, verification, recovery, token issuance, and sessions. Flutter uses Authorization Code + PKCE. OwlNest never accepts or stores passwords.
- The backend is a stateless OAuth2 Resource Server. It validates configured issuer, audience `owlnest-api`, signature/JWK, and token time claims before controllers.
- `SpringSecurityIdentityProvider` maps a validated JWT to a provider-neutral identity; services use the local UUID from `identity_account`, never raw Keycloak `sub` as a business foreign key.
- `/actuator/health`, `/error`, Swagger UI, and `/v3/api-docs/**` are public. `/api/v1/**` is authenticated; all other paths are denied.
- CSRF is disabled because the API authenticates bearer headers, not browser cookies. Sessions, form login, HTTP Basic, logout, and request cache are disabled.
- No method security or role-specific authorization exists yet. Ownership checks must be explicit in service use cases when introduced.

## Gate evidence

Define an authorization matrix by actor/action/resource/state. Review endpoint and object-level checks, cross-user access, claim/role mapping, sensitive response fields, input/file handling, CORS/CSRF assumptions, secrets/configuration, token/PII logs, rate limiting where evidence requires it, and public health/docs exposure.

Select negative tests that the change can affect: no token, malformed/expired/wrong issuer/wrong audience, insufficient role, another user's object, guessed IDs, private fields, and forbidden state transitions as applicable. Use Spring Security test JWT only after separately validating production decoder configuration. When the trigger above applies, `security_authorization_reviewer` must independently return `SECURITY_PASS`; ordinary reuse of the established JWT boundary may be covered by focused 401 behavior plus Spring code review.
