# ADR-0002: Use Keycloak as the Identity Provider

**Status:** Accepted

**Date:** 2026-07-12

## Context

OwlNest requires registration, login, logout, token refresh, email verification, and later social login. The Flutter client needs access tokens for protected API calls, while the backend needs a stable identity for profiles, posts, friendships, and messages.

The backend already includes Spring Security OAuth2 Resource Server support. That role validates access tokens but does not manage passwords or issue tokens.

## Decision

Use Keycloak as the OAuth2/OpenID Connect identity provider. Flutter is a public OAuth client and will use Authorization Code flow with PKCE. OwlNest Backend remains a stateless Resource Server that validates JWT signature, issuer, audience, expiry, and other required claims.

Keycloak owns credentials and token lifecycle. OwlNest owns a local account UUID, product profile, authorization decisions, and business data. A unique `(provider, external_subject)` mapping connects a local account to Keycloak's `sub` claim.

Do not add a Keycloak-specific Java adapter. Use Spring Security's standard OIDC/JWT support and Keycloak's discovery/JWK endpoints.

## Alternatives

### Firebase Authentication

Advantages: excellent Flutter SDK, managed infrastructure, and fast mobile onboarding. Disadvantages: stronger provider coupling and less direct experience with standard self-hosted OIDC infrastructure. FCM does not require Firebase Authentication, so push notifications do not force this choice.

### Spring Authorization Server

Advantages: full control and deeper Spring learning. Disadvantages: OwlNest would need to operate security-sensitive credential, key, client, consent, refresh, revocation, recovery, and verification flows. This is a separate product-sized responsibility and is outside the first version.

### Custom JWT endpoints in OwlNest Backend

Advantages: initially appears simple. Disadvantages: mixes identity with business code and invites incomplete password, token-rotation, revocation, and recovery implementations. Rejected.

## Consequences

- Local development will add a Keycloak Docker service and a versioned development realm import.
- Flutter owns browser-based login/registration/logout and secure token storage.
- Backend endpoints do not implement `/login`, `/register`, `/refresh`, or `/logout`.
- The backend must validate both issuer and audience and distinguish `401 Unauthorized` from `403 Forbidden`.
- A canonical development issuer must be chosen that works consistently for Flutter, local backend, and containerized backend.
- Replacing Keycloak later affects the identity adapter, while local account IDs and business foreign keys remain stable.

## References

- [Spring Security Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Keycloak OpenID Connect flows](https://www.keycloak.org/securing-apps/oidc-layers)
- [Spring Authorization Server](https://docs.spring.io/spring-authorization-server/reference/index.html)
