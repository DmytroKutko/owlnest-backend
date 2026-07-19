# Дописи: реалізовані single-post, global list і comments

**Статус:** Implemented — 2026-07-18.

Цей документ описує фактичний контракт backend-зрізу дописів, authenticated global list, managed post images і append-only коментарів. Personalized/saved lists, comment edit/delete/replies/moderation, managed video та messenger media залишаються окремими майбутніми фічами.

## Межа фічі

Feature package `post` володіє:

- створенням, читанням, повною заміною та soft delete одного допису;
- ordered labels та ordered media references;
- idempotent desired-state взаємодіями like, private bookmark і repost;
- створенням і oldest-first keyset pagination коментарів active post;
- card projection з safe author summary, counters, viewer state, timestamps і клієнтськими links.
- newest-first global list з live keyset pagination.

Усі endpoints потребують bearer token. Усі active posts видимі будь-якому автентифікованому account незалежно від onboarding state. Лише автор може замінити або видалити active post. Missing і soft-deleted posts повертають `404 post.not_found`; чужі PUT/DELETE active post — `403 post.access_denied`.

## HTTP API

| Метод | Шлях | Успіх |
| --- | --- | --- |
| `POST` | `/api/v1/posts` | `201`, card і `Location` |
| `GET` | `/api/v1/posts` | `200`, усі active posts |
| `GET` | `/api/v1/posts/{id}` | `200`, card |
| `PUT` | `/api/v1/posts/{id}` | `200`, повністю замінена card |
| `DELETE` | `/api/v1/posts/{id}` | `204`, soft delete |
| `POST` | `/api/v1/posts/{id}/comments` | `201`, comment і `Location` |
| `GET` | `/api/v1/posts/{id}/comments` | `200`, oldest-first bounded page |
| `PUT` / `DELETE` | `/api/v1/posts/{id}/likes` | `204`, like set/clear |
| `PUT` / `DELETE` | `/api/v1/posts/{id}/bookmark` | `204`, private bookmark set/clear |
| `PUT` / `DELETE` | `/api/v1/posts/{id}/repost` | `204`, repost set/clear |

Repeated interaction requests є idempotent: relation і counter змінюються лише під час фактичного переходу стану. Self-like та self-repost дозволені. Bookmark не має публічного counter.

`links.comments` у post card і надалі дорівнює `/api/v1/posts/{id}#comments`: це Flutter same-post navigation hook, а не REST collection URL. REST collection має окремий шлях `/api/v1/posts/{id}/comments`, а `counters.comments` містить реальну кількість persisted comments.

Personalized/saved lists, recommendation ranking, views endpoint, community membership, comment mutation/moderation і three-dot menu actions не входять у реалізований контракт.

## Global post list

`GET /api/v1/posts` authenticated і повертає exact `PostResponse` cards для всіх active posts. Порядок fixed `(createdAt DESC, id DESC)`. `limit` має default `20`, range `1..100`; optional opaque cursor продовжує live keyset traversal.

Envelope має `items`, `page {limit,hasMore,nextCursor}` і `links {self,next}`; terminal continuation fields дорівнюють `null`. Response має `Cache-Control: private, no-store`. Cursor містить тільки versioned opaque `(createdAt,id)` position і не є authorization boundary.

## Comments contract

Create request містить тільки exact plain text:

```json
{"text": "Коментар без серверних полів у request"}
```

Текст required/nonblank, не містить `NUL` або unpaired UTF-16 surrogate і має максимум 5 000 Unicode code points. Backend не trim/normalize/перетворює валідний текст. `id`, `postId`, author і `createdAt` завжди server-owned. Будь-який authenticated account може коментувати active post; missing або soft-deleted post повертає `404 post.not_found`.

Comment response містить `id`, `postId`, exact `text`, safe `author {accountId,nickname,displayName,avatarUrl,avatar}`, `createdAt` і `links {self,post,collection}`. `avatar` дорівнює `null` або містить managed `mediaId` та authenticated root-relative `deliveryUrl`; legacy `avatarUrl` збережено для сумісності. Email, verification, birth date, gender, onboarding state та presence не експонуються.

`GET /api/v1/posts/{id}/comments` приймає лише `limit` (default `20`, range `1..100`) та optional opaque `cursor`. Невідомі або повторені query parameters і malformed/post-mismatched cursor повертають `400 request.validation_failed`. Response має форму:

```json
{
  "items": [],
  "page": {"limit": 20, "hasMore": false, "nextCursor": null},
  "links": {
    "self": "/api/v1/posts/{id}/comments?limit=20",
    "next": null,
    "post": "/api/v1/posts/{id}"
  }
}
```

Порядок стабільний oldest-first за `(createdAt, id)`. Cursor має versioned `v1.` transport і прив'язаний до конкретного post. Це live keyset traversal, не snapshot. Comment POST навмисно не idempotent: кожен успішний request створює окремий comment; після неоднозначного network timeout клієнт не повинен автоматично повторювати його як desired-state PUT.

## Write contract

```json
{
  "title": "Необов'язковий заголовок",
  "description": "Обов'язковий текст допису",
  "labels": ["Community Post", "Spring"],
  "media": [
    {"type": "IMAGE", "url": "https://cdn.example.com/photo.jpg"},
    {"type": "IMAGE", "mediaId": "47c62a2c-ae5f-48d1-b05c-126cc1292392"},
    {"type": "VIDEO", "url": "https://cdn.example.com/video.mp4"}
  ]
}
```

Правила:

- `title` optional, максимум 200 Unicode code points; blank нормалізується в `null`;
- `description` required/nonblank, максимум 20 000 Unicode code points;
- `postType` не є write-полем: backend визначає `COMMUNITY`, якщо нормалізований email автора закінчується на `@owlnest.com`, інакше `PERSONAL`;
- `COMMUNITY` є server-derived presentation classification, а не доказом membership/affiliation;
- `labels`: 0–5, кожен trimmed і довжиною 1–50 Unicode code points, порядок і дублікати зберігаються;
- `media`: 0–10 ordered елементів; кожен має рівно одне з `url` або `mediaId`;
- managed `mediaId` дозволений лише для `IMAGE` і має посилатися на власний unexpired `READY` `POST_IMAGE`; missing/cross-owner повертає sanitized `404 media.not_found`, wrong purpose/not-ready — `409`;
- media URL має бути синтаксично валідним absolute HTTPS URI до 2048 Unicode code points; валідні Unicode, IPv6 literal і user-info форми не канонізуються та зберігаються без зміни;
- relative URI, не-HTTPS scheme, blank URL, malformed URI, `NUL` та unpaired UTF-16 surrogates повертають `400`;
- `NUL` та unpaired UTF-16 surrogates у текстових полях не підтримуються; інші валідні interior control characters зберігаються без зміни;
- backend зберігає URL як недовірені metadata й ніколи не fetch/proxy/probe їх;
- Flutter не повинен пересилати OwlNest bearer token на media host;
- `id`, author, counters, viewer state, permissions і timestamps визначає сервер. Невідомі/server-owned JSON fields не можуть їх перевизначити.

PUT є full replacement усіх author-editable полів. Omitted labels або media стають `[]`; server-derived `postType` не редагується та не перераховується при зміні email автора.

Для compatibility legacy payload із `postType` обробляється як unknown field і ігнорується; його значення не може вплинути ані на створення, ані на заміну поста. Актуальні клієнти мають прибрати поле зі своїх request models.

## Card response

```json
{
  "id": "47c62a2c-ae5f-48d1-b05c-126cc1292392",
  "title": null,
  "description": "Текст допису",
  "author": {
    "accountId": "a93dddbc-0f4a-4bd7-a42b-3656a4f6c954",
    "nickname": "john.doe",
    "displayName": "John Doe",
    "avatarUrl": null
  },
  "postType": "PERSONAL",
  "labels": [],
  "media": [],
  "counters": {"likes": 0, "comments": 0, "reposts": 0},
  "viewerState": {
    "liked": false,
    "bookmarked": false,
    "reposted": false,
    "isAuthor": true,
    "canEdit": true,
    "canDelete": true
  },
  "timestamps": {
    "createdAt": "2026-07-18T09:30:00Z",
    "updatedAt": "2026-07-18T09:30:00Z"
  },
  "links": {
    "self": "/api/v1/posts/47c62a2c-ae5f-48d1-b05c-126cc1292392",
    "comments": "/api/v1/posts/47c62a2c-ae5f-48d1-b05c-126cc1292392#comments"
  }
}
```

Backend повертає absolute `Instant`; elapsed-time formatting належить Flutter. Author projection навмисно виключає email, birth date, gender, onboarding state і presence. До explicit onboarding default author є privacy-neutral: generated `user_<id>` і `OwlNest user`; identity-provider name/email claims не стають публічними через post card. `avatarUrl` залишається nullable legacy полем, а managed avatar представлено окремим `{mediaId, deliveryUrl}` без прямого R2 URL. Write-path використовує email лише під час створення для класифікації та не зберігає його в post; read-path отримує лише UUID через safe profile projection. Post module не залежить від media repository або storage adapter.

Managed post item повертається як `{"type":"IMAGE","url":null,"managed":{"mediaId":"...","deliveryUrl":"/api/v1/media/.../delivery"}}`. Flutter викликає delivery endpoint з OwlNest bearer token, а потім завантажує bytes за короткоживучим R2 GET URL без OwlNest token. Повний PUT зберігає вже attached active IDs, активує нові READY IDs і ставить від’єднані у delayed cleanup; soft delete поста також блокує delivery та від’єднує managed images.

## Дані й узгодженість

Flyway `V3__create_post_tables.sql` створює:

- `post` — aggregate root, soft-delete marker і like/comment/repost counters;
- `post_label(post_id, position)` і `post_media(post_id, position)`;
- `post_like(post_id, account_id)`, `post_bookmark(...)`, `post_repost(...)`.

Forward-only `V7__attach_managed_media_to_posts.sql` робить legacy URL nullable, додає generated `POST_IMAGE` purpose, composite FK, exactly-one-source/type checks і unique attachment. Вона не backfill-ить і не змінює existing HTTPS rows.

V7 є stop-the-world transition, а не rolling-compatible schema: після першого managed-only row попередній binary не може безпечно читати або від’єднувати такий media item. Для local Compose потрібно спочатку прибрати старий backend instance, застосувати Flyway під час старту нового й лише тоді дозволяти writes. Перед застосуванням до великої `post_media` потрібні вимірювання rewrite/index/constraint duration, disk headroom і maintenance window; вже застосовану V7 не редагують — наступні зміни робляться тільки forward migration.

Forward-only `V4__create_post_comments.sql` додає append-only `post_comment`, foreign keys, text constraint, indexes `(post_id, created_at, id)` та `(author_id)`. Вона коротко замінює zero-only `post.comment_count` constraint на `>= 0 NOT VALID` без scan existing rows. Окрема `V5__validate_post_comment_count.sql` виконує validation у новій Flyway transaction/lock scope, щоб початковий сильний ALTER lock не утримувався під час scan.

PostgreSQL є єдиним source of truth; Redis не використовується. `Post` — scalar JPA entity, ordered content та interaction rows обробляються bounded JDBC queries. Card scalars, labels і media читаються одним SQL statement, тому concurrent full replacement не створює змішаних версій. Усі existing-post mutations спочатку беруть `PESSIMISTIC_WRITE` lock active post row. Relation transition і like/repost counter delta виконуються в одній транзакції; composite primary keys є фінальним захистом від дублікатів.

Global list виконує один bounded JDBC card statement над максимум `limit + 1` candidates, а потім один batch profile-summary query для унікальних author IDs. `ListPostsService` transaction є read-write, тому current account/profile може provision за existing правилом.

Comment creation використовує той самий active-post lock. Під lock PostgreSQL обирає strictly increasing per-post `createdAt`, comment row і `Post.recordCommentCreated()` commit/rollback в одній транзакції. Сторінка читається active-post-rooted bounded query та одним batch-safe profile query без unbounded association/N+1. Redis, events, outbox і notifications не входять у цей path.

Перший account/profile provisioning використовує PostgreSQL transaction advisory locks лише після lookup miss, з обов'язковим recheck. Established users не беруть ці locks. Lock order: identity miss → profile miss → post row.

Soft delete не видаляє content/interactions фізично, але всі user-facing reads і mutations фільтрують `deleted_at IS NULL`. Hard delete не є API operation; `ON DELETE CASCADE` підготовлений лише для майбутнього контрольованого purge.

## Верифікація

Контракт покривають PostgreSQL/Testcontainers + MockMvc тести:

- CRUD, ownership, soft delete, authentication і stable Problem Details codes;
- boundary/negative validation, ordered labels/media та safe server-owned projection;
- viewer-specific state, interaction idempotency і counter consistency;
- comment auth/privacy/text boundaries, exact preservation, active-post behavior і real card counter;
- oldest-first cursor pagination, page isolation, malformed/foreign cursor rejection і bounded profile hydration;
- global newest-first pagination, deterministic tie order, exact card reuse й terminal continuation;
- concurrent comment creates, create/delete race, rollback, V4/V5 catalog/JPA parity і upgrade з pre-existing V3 rows;
- generated OpenAPI operation ID та відсутність неімплементованих list routes;
- PostgreSQL schema/constraints і concurrent interaction transitions.

Formatter/linter/coverage gate у repository не налаштовані. Верифікація підпорядковується tier/risk правилам у `docs/agent-system/workflow-routing.md`: focused post tests під час розробки, найширший виправданий suite/build один раз після фінальної правки, `git diff --check` та лише ті незалежні reviews, які активовані фактичним architecture/data/security/API ризиком.
