# API Documentation

## Local Endpoints

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- REST OpenAPI JSON: `http://localhost:8080/v3/api-docs/rest`
- WebSocket placeholder JSON: `http://localhost:8080/v3/api-docs/websocket`

Swagger UI defaults to `REST API`. Its top-bar dropdown also contains `WebSocket API (planned)` so the REST/realtime boundary remains visible before messaging work begins.

## REST Contract

Springdoc generates the REST specification from Spring MVC mappings, Bean Validation constraints, DTOs, and Swagger annotations. `OpenApiConfiguration` limits this group to `/api/**` and defines both Keycloak Authorization Code + PKCE and manual bearer-token security schemes.

When a REST endpoint, payload, response, validation rule, status code, or authentication requirement changes, update all of the following in the same change:

1. controller and DTO OpenAPI annotations;
2. integration assertions against `/v3/api-docs/rest`;
3. `API/OwlNest.postman_collection.json`;
4. the owning feature document and examples.

## Authentication in Swagger

Login and token refresh are Keycloak OIDC protocol operations, not OwlNest REST endpoints. They therefore appear in the `keycloakOAuth2` security scheme instead of under a fabricated `/api/v1/auth` controller:

- authorization endpoint: `http://localhost:8081/realms/owlnest/protocol/openid-connect/auth`;
- token and refresh endpoint: `http://localhost:8081/realms/owlnest/protocol/openid-connect/token`;
- discovery: `http://localhost:8081/realms/owlnest/.well-known/openid-configuration`.

Swagger **Authorize** uses the dedicated public `owlnest-swagger` client and Authorization Code + PKCE; no client secret is stored in the repository or browser configuration. The same dialog retains `bearerAuth` for manually pasting an access token obtained through Postman.

The Keycloak token endpoint exchanges an authorization code when logging in and accepts `grant_type=refresh_token` when an OAuth client refreshes an expired access token. It is documented as both `tokenUrl` and `refreshUrl`, but not rendered as an OwlNest REST operation because the backend neither owns nor proxies it.

The documentation endpoints are intentionally unauthenticated, but Try it out calls still pass through the normal Spring Security filter chain. Before a public deployment, decide explicitly whether to keep the documentation public, protect it, or disable it.

## WebSocket Boundary

No WebSocket endpoint, channel, or message is implemented yet. The second Swagger group is intentionally empty and marked `planned`; it must not contain invented HTTP operations.

OpenAPI describes HTTP operations, while the future messaging contract is asynchronous and bidirectional. When messenger development starts, add an AsyncAPI document covering the WebSocket server, handshake/authentication, channels, client/server messages, payload schemas, errors, and versioning. Swagger may link to that document, but AsyncAPI becomes the source of truth for realtime contracts.
