# Managed Media and Cloudflare R2

**Status:** Implemented for private profile avatars and post images. Managed video and messenger attachments remain deferred.

## Boundary

The `media` feature owns media reservations, immutable object metadata, R2 access, lifecycle state, and physical cleanup. PostgreSQL stores metadata and relationships; the private Cloudflare R2 bucket stores bytes. The backend never accepts the object body and never exposes R2 credentials.

The `profile` feature stores only the active `avatar_media_id` and calls the public media lifecycle service while the profile transaction is open. Profile and post HTTP projections expose an avatar reference as `{mediaId, deliveryUrl}`. Controller-owned mapping constructs the root-relative delivery URL; service projections carry only the UUID.

The existing `post_media.url` contract remains compatible and preserves arbitrary existing HTTPS rows. A post media item now has exactly one source: legacy `url`, or managed `mediaId` for `IMAGE`. Managed video is still rejected.

One private R2 bucket is partitioned by immutable key prefixes: new avatars use `managed/v1/avatars/{uuid}` and post images use `managed/v1/posts/{uuid}`. Existing object keys stay valid. `managed/v1/messages/` is reserved only in documentation until the messenger feature exists; it is not accepted by the API.

## Authenticated Flutter flow

1. `POST /api/v1/media/uploads` accepts `AVATAR` or `POST_IMAGE`, reserves immutable metadata, and returns a short-lived presigned `PUT` URL.
2. Flutter uploads bytes directly to R2 using the exact `Content-Type`, transport-generated `Content-Length`, and returned `If-None-Match: *` header.
3. `PUT /api/v1/media/{mediaId}/confirmation` performs R2 `HEAD` and accepts the object only when content type and length match the reservation and an ETag exists.
4. For an avatar, call `PUT /api/v1/profile/me/avatar`. For a post image, include `{"type":"IMAGE","mediaId":"..."}` in `POST /api/v1/posts` or full-replacement `PUT /api/v1/posts/{id}`. The owning transaction activates READY media and schedules detached media for delayed cleanup.
5. Responses expose `{mediaId,deliveryUrl}`, never the R2 key or durable R2 URL. Authenticated `POST` to `deliveryUrl` rechecks an active avatar/profile or active-post association and returns a short-lived presigned private `GET` URL.
6. `DELETE /api/v1/profile/me/avatar` detaches the avatar. `DELETE /api/v1/media/{mediaId}` cancels only owned pending or ready media.

All application endpoints require the OwlNest bearer token. Presigned R2 URLs are temporary bearer capabilities and must not be logged, persisted as durable media URLs, or sent to another user. R2 credentials require object read/write access to the single private bucket; administrative account permissions are unnecessary.

## Request examples

Reserve an avatar upload using the exact byte length of the selected file:

```http
POST /api/v1/media/uploads
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "purpose": "AVATAR",
  "contentType": "image/webp",
  "sizeBytes": 524288
}
```

The response contains `mediaId`, immutable declared metadata, and:

```json
{
  "state": "PENDING_UPLOAD",
  "upload": {
    "method": "PUT",
    "url": "https://<account>.r2.cloudflarestorage.com/...?X-Amz-...",
    "requiredHeaders": {
      "Content-Type": "image/webp",
      "If-None-Match": "*"
    },
    "expiresAt": "2026-07-19T12:15:00Z"
  }
}
```

After the direct upload, confirm and attach it:

```http
PUT /api/v1/media/{mediaId}/confirmation
Authorization: Bearer <access-token>

PUT /api/v1/profile/me/avatar
Authorization: Bearer <access-token>
Content-Type: application/json

{"mediaId":"<media-id>"}
```

For a post image, reserve with `"purpose":"POST_IMAGE"`, upload and confirm as above, then send:

```json
{"description":"New post","media":[{"type":"IMAGE","mediaId":"<media-id>"}]}
```

## Rules and lifecycle

- Purpose-specific allowlists and limits are enforced in HTTP validation, domain code, and PostgreSQL. Avatars accept JPEG, PNG, or WebP up to 10 MiB; post images accept the same types up to 20 MiB.
- One account may have at most 10 non-deleted managed objects and at most 100 MiB of declared bytes across `AWAITING_UPLOAD`, `READY`, `ACTIVE`, and `DELETION_PENDING`. Same-account reservations serialize with a PostgreSQL transaction advisory lock; exact limits are allowed, while the next reservation returns `429 media.storage_quota_exceeded` before presigning. A physical cleanup finalized as `DELETED` releases quota.
- Upload capabilities expire after at most 15 minutes. Confirmed but unattached media remains `READY` for 24 hours. Delivery capabilities expire after at most 5 minutes.
- `AWAITING_UPLOAD -> READY -> ACTIVE` is the successful avatar/post-image path.
- Expiry, cancellation, replacement, and removal transition to `DELETION_PENDING`; physical deletion later produces `DELETED`.
- Confirmation is idempotent after success. Avatar and post-image replacement use stable media lock order and one PostgreSQL transaction. Reusing the same already-active image in an idempotent full post replacement preserves it; detach and post soft-delete transition it to `DELETION_PENDING/DETACHED`.
- R2 reservation, presigning, `HEAD`, `GET` presigning, and `DELETE` do not execute inside PostgreSQL transactions.
- Disabled or unavailable R2 returns sanitized `503 media.storage_unavailable`; ownership failures use non-enumerating `404 media.not_found` where appropriate. The adapter treats only exact R2 `NoSuchKey` as object absence; a missing bucket or ambiguous `404` fails closed and cleanup retries instead of falsely finalizing deletion.

## Cleanup

Cleanup is enabled only with `R2_ENABLED=true`. Each scheduled run first expires bounded pending/ready rows, then claims at most 25 due rows with PostgreSQL `FOR UPDATE SKIP LOCKED` and a two-minute lease. R2 deletion happens outside the database transaction. Success finalizes `DELETED`; transient storage failure clears the lease and schedules exponential retry, capped at 60 minutes. Deleting an already absent R2 object is treated as success.

`MEDIA_CLEANUP_INITIAL_DELAY` defaults to `30s`; `MEDIA_CLEANUP_FIXED_DELAY` defaults to `1m`. PostgreSQL remains authoritative if R2 is temporarily unavailable.

## Configuration

Copy `.env.example` to ignored `.env`, create a private R2 bucket and a bucket-scoped Object Read & Write API token, then set:

```dotenv
R2_ENABLED=true
R2_ACCOUNT_ID=<cloudflare-account-id>
R2_ENDPOINT=https://<cloudflare-account-id>.r2.cloudflarestorage.com
R2_REGION=auto
R2_BUCKET_NAME=<private-bucket-name>
R2_ACCESS_KEY_ID=<token-access-key-id>
R2_SECRET_ACCESS_KEY=<token-secret-access-key>
```

The current endpoint validator intentionally accepts the default account endpoint only. Jurisdiction-specific R2 endpoints require an explicit future configuration change and tests. Browser direct upload also requires a bucket CORS policy allowing the Flutter Web origin, `PUT`, and the signed request headers. Native Flutter clients are not governed by browser CORS.

Restart the backend after changing `.env`. Readiness means the application and its database/Redis/Keycloak dependencies started; it does not continuously probe R2. Storage failure is reported by the media operation itself.

## Annotation notes

- `@Service` on the post-image lifecycle and delivery authorizer is discovered by Spring component scanning at startup and creates singleton application services; mutable request state must not live on these beans.
- `@Transactional(propagation = MANDATORY)` on post-image lifecycle methods is applied by Spring's transaction proxy at call time and rejects use outside the owning post transaction. Calls must cross the proxy; self-invocation would bypass the interceptor.
- DTO `@Schema` names are read by springdoc while generating OpenAPI and keep request/response post-media shapes distinct; they do not validate runtime requests.

## Verification

The normal test suite uses fake clients and does not contact Cloudflare. To run the opt-in write/read/delete smoke against the configured bucket without printing secrets:

```shell
set -a
source .env
set +a
R2_LIVE_TEST=true ./gradlew test --tests dev.dkutko.owlnest.media.config.R2LiveSmokeTest
```

The test writes a unique one-pixel PNG under `managed/smoke/`, verifies `PUT`, `HEAD`, presigned `GET`, and bytes, then deletes the object in `finally`.

## Non-goals

- migration/downloading of existing `post_media.url` rows;
- managed video or messenger attachment;
- image resizing, transcoding, virus scanning, moderation, or magic-byte inspection;
- a public R2 bucket or permanent public delivery URL;
- storing object bytes in PostgreSQL or the backend filesystem.
