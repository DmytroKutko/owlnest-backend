# Postman

Generate the ignored local Postman environment from `.env`:

```shell
./API/generate-postman-environment.sh
```

Import both `OwlNest.postman_collection.json` and the generated `OwlNest.local.postman_environment.json`, then select **OwlNest Local** in Postman. `./setup.sh` also regenerates the environment automatically when `jq` is installed.

The generated environment contains local credentials. It is ignored by Git and created with owner-only permissions; do not share or commit it.

The collection can create a local Keycloak user and obtain tokens without opening a browser. This uses Keycloak Admin REST API plus Direct Access Grant and is intended only for local development. The real Flutter client will use the browser-based Authorization Code flow with PKCE.

Recommended local order:

1. `Infrastructure / Backend Health`
2. `Infrastructure / Keycloak Discovery`
3. `Development User Setup / Get Admin Token`
4. `Development User Setup / Create Test User`
5. `Authentication / Development Email-Password Login`
6. `Profile / Get Current Profile`

Credentials remain only in `.env` and the ignored local Postman environment; they are not committed in the collection. A collection-level pre-request script checks the variables required by each request and reports a clear error if the environment or a generated token is missing.

The test-user JSON receives email, password, first name, and last name from the selected environment. Keycloak's current default User Profile requires first and last name before password login can complete.

`Development Email-Password Login` uses Keycloak Direct Access Grant for local/Postman testing only. Flutter must use Authorization Code flow with PKCE and must never collect a password for forwarding to the backend.

## What Each Request Does

- `Get Admin Token` reads the local admin credentials from the selected environment and saves `adminAccessToken` automatically.
- `Create Test User` calls the Keycloak Admin API. `201` means the user was created; `409` means that email already exists.
- `Development Email-Password Login` exchanges the test user's email and password for access and refresh tokens and saves both automatically.
- `Refresh Access Token` replaces expired tokens without asking for the password again.
- `Get Current Profile` sends `Authorization: Bearer {{accessToken}}` to OwlNest Backend and provisions the local PostgreSQL account/profile on the first request.

If `Create Test User` returns `409`, keep the password previously assigned to that user or choose another email. Creating the same user again does not replace its password.

Changing `KEYCLOAK_ADMIN_PASSWORD` in `.env` does not change an administrator already stored in the persistent `keycloak_data` volume. Reset that user or recreate the local volume deliberately when rotating the development admin password.
