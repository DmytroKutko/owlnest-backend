# Postman

Generate the ignored local Postman environment from `.env`:

```shell
./API/generate-postman-environment.sh
```

Import both `OwlNest.postman_collection.json` and the generated `OwlNest.local.postman_environment.json`, then select **OwlNest Local** in Postman. `./setup.sh` also regenerates the environment automatically when `jq` is installed.

Interactive REST documentation is available at `http://localhost:8080/swagger-ui.html`. Swagger **Authorize** can sign in through the dedicated `owlnest-swagger` Keycloak client using Authorization Code + PKCE, or accept an access token obtained through Postman.

The generated environment contains local credentials. It is ignored by Git and created with owner-only permissions; do not share or commit it.

The collection can create a local Keycloak user and obtain tokens without opening a browser. This uses Keycloak Admin REST API plus Direct Access Grant and is intended only for local development. The real Flutter client will use the browser-based Authorization Code flow with PKCE.

Recommended local order:

1. `Infrastructure / Backend Health`
2. `Infrastructure / Keycloak Discovery`
3. `Development User Setup / Get Admin Token`
4. `Development User Setup / Create Test User`
5. `Authentication / Development Email-Password Login`
6. `Profile / Complete or Replace Current Profile`
7. `Profile / Get Current Profile`
8. `Presence / Heartbeat`
9. `Profile / Get Public Profile`
10. Optional avatar flow: `Media / Create Avatar Upload`, upload the selected file directly with the returned capability, `Media / Confirm Upload`, then `Profile / Set Current Avatar`
11. `Posts / Create Post`
12. `Posts / List Global Posts`
13. `Posts / Get Post`, optional comment/interaction requests, then `Posts / Delete Post`

Credentials remain only in `.env` and the ignored local Postman environment; they are not committed in the collection. A collection-level pre-request script checks the variables required by each request and reports a clear error if the environment or a generated token is missing.

The test-user JSON receives email, password, first name, and last name from the selected environment. Keycloak's current default User Profile requires first and last name before password login can complete.

`Development Email-Password Login` uses Keycloak Direct Access Grant for local/Postman testing only. Flutter must use Authorization Code flow with PKCE and must never collect a password for forwarding to the backend.

## What Each Request Does

- `Get Admin Token` reads the local admin credentials from the selected environment and saves `adminAccessToken` automatically.
- `Create Test User` calls the Keycloak Admin API. `201` means the user was created; `409` means that email already exists.
- `Development Email-Password Login` exchanges the test user's email and password for access and refresh tokens and saves both automatically.
- `Refresh Access Token` replaces expired tokens without asking for the password again.
- `Complete or Replace Current Profile` submits all OwlNest-owned username, display name, bio, birth date, and gender fields for onboarding or later editing.
- `Get Current Profile` sends `Authorization: Bearer {{accessToken}}` to OwlNest Backend, provisions the local PostgreSQL account/profile on first request, and saves `accountId` for subsequent collection requests.
- `Heartbeat` refreshes the authenticated account's Redis presence for 90 seconds.
- `Get Public Profile` returns only public profile fields and presence for `{{accountId}}`.
- `Create Avatar Upload` accepts only `AVATAR`, reserves exact metadata, and saves only `mediaId`; the client must immediately use the returned short-lived R2 URL and every required header without persisting the capability. Per-account outstanding storage is capped at 10 objects/100 MiB and excess reservations return `429`.
- `Confirm Upload` verifies the uploaded object's R2 content type, byte length, and ETag. `Set Current Avatar` atomically activates it and makes prior avatar cleanup eligible.
- `Create Avatar Delivery` rechecks the current active association and returns a short-lived private URL that the collection deliberately does not save. `Remove Current Avatar` detaches it; `Cancel Managed Media` applies only before activation.
- `Create Post` submits optional title, `PERSONAL`/`COMMUNITY` classification, ordered labels, and ordered image/video HTTPS references, then saves the returned `postId`.
- `Get Post` returns the safe author card, public like/comment/repost counters, viewer-specific flags, absolute timestamps, and a same-post `#comments` client hook.
- `List Global Posts` returns every active post with newest-first cursor pagination and saves its opaque `page.nextCursor` variable. Rerun it to continue, or clear `globalPostCursorQuery` to restart.
- `Create Post Comment` appends exact plain text to an active post, saves `commentId`, and returns only the safe author projection.
- `List Post Comments` returns an oldest-first page. It saves `page.nextCursor` into `commentCursorQuery`; rerun it to request the next page, or clear that collection variable to restart.
- `Replace Post` fully replaces author-editable content; the sample intentionally demonstrates a description-only post.
- Like, bookmark, and repost PUT/DELETE requests set or clear desired state idempotently. Bookmark is private and has no public counter.
- `Delete Post` soft-deletes the current user's post; run it last. Comment edit/delete/reply routes, personalized/saved post lists, and managed post attachment are not implemented in this slice.

To populate the complete local community demo after `./setup.sh`, set a strong `KEYCLOAK_SEED_USER_PASSWORD` in `.env` and run:

```shell
OWLNEST_LOCAL_SEED=true ./scripts/seed-local-community-demo.sh
```

The ignored owner-only inventory makes the versioned seed resumable. The script operates only against the fixed localhost backend/Keycloak realm, creates or reuses six marked fictional `@owlnest.com` identities, and reconciles 36 community posts, 24 likes, and 18 comments. It never stores credentials in Git, adopts unmarked Keycloak accounts, writes product rows directly, deletes unrelated local data, or treats a duplicate match as safe.

Post media URLs are untrusted metadata: the backend does not download or proxy them. A client fetching them must not attach the OwlNest bearer token to the media host. Managed avatar bytes use a separate private R2 flow documented in [`docs/features/managed-media-r2.md`](../docs/features/managed-media-r2.md); presigned capability URLs are secrets and must not be logged or persisted as durable avatar URLs.

If `Create Test User` returns `409`, keep the password previously assigned to that user or choose another email. Creating the same user again does not replace its password.

Changing `KEYCLOAK_ADMIN_PASSWORD` in `.env` does not change an administrator already stored in the persistent `keycloak_data` volume. Reset that user or recreate the local volume deliberately when rotating the development admin password.
