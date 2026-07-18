# Дописи: реалізований single-post зріз

**Статус:** Implemented — 2026-07-18.

Цей документ описує фактичний контракт першого backend-зрізу дописів. Discovery feed, списки bookmarks/reposts, comments persistence і media upload залишаються окремими майбутніми фічами.

## Межа фічі

Feature package `post` володіє:

- створенням, читанням, повною заміною та soft delete одного допису;
- ordered labels та ordered media references;
- idempotent desired-state взаємодіями like, private bookmark і repost;
- card projection з safe author summary, counters, viewer state, timestamps і клієнтськими links.

Усі endpoints потребують bearer token. Усі active posts видимі будь-якому автентифікованому account незалежно від onboarding state. Лише автор може замінити або видалити active post. Missing і soft-deleted posts повертають `404 post.not_found`; чужі PUT/DELETE active post — `403 post.access_denied`.

## HTTP API

| Метод | Шлях | Успіх |
| --- | --- | --- |
| `POST` | `/api/v1/posts` | `201`, card і `Location` |
| `GET` | `/api/v1/posts/{id}` | `200`, card |
| `PUT` | `/api/v1/posts/{id}` | `200`, повністю замінена card |
| `DELETE` | `/api/v1/posts/{id}` | `204`, soft delete |
| `PUT` / `DELETE` | `/api/v1/posts/{id}/likes` | `204`, like set/clear |
| `PUT` / `DELETE` | `/api/v1/posts/{id}/bookmark` | `204`, private bookmark set/clear |
| `PUT` / `DELETE` | `/api/v1/posts/{id}/repost` | `204`, repost set/clear |

Repeated interaction requests є idempotent: relation і counter змінюються лише під час фактичного переходу стану. Self-like та self-repost дозволені. Bookmark не має публічного counter.

Comments endpoint у цьому зрізі не реалізований. `links.comments` дорівнює `/api/v1/posts/{id}#comments`; Flutter використовує цей same-post hook, щоб відкрити секцію та composer. `counters.comments` поки завжди `0`.

Feed/list/pagination, views endpoint, comment rows/endpoints, community membership, moderation і three-dot menu actions не входять у реалізований контракт.

## Write contract

```json
{
  "title": "Необов'язковий заголовок",
  "description": "Обов'язковий текст допису",
  "postType": "COMMUNITY",
  "labels": ["Community Post", "Spring"],
  "media": [
    {"type": "IMAGE", "url": "https://cdn.example.com/photo.jpg"},
    {"type": "VIDEO", "url": "https://cdn.example.com/video.mp4"}
  ]
}
```

Правила:

- `title` optional, максимум 200 Unicode code points; blank нормалізується в `null`;
- `description` required/nonblank, максимум 20 000 Unicode code points;
- `postType`: `PERSONAL` або `COMMUNITY`, default `PERSONAL`;
- `COMMUNITY` є presentation classification, а не доказом membership/affiliation;
- `labels`: 0–5, кожен trimmed і довжиною 1–50 Unicode code points, порядок і дублікати зберігаються;
- `media`: 0–10 ordered елементів `IMAGE`/`VIDEO`;
- media URL має бути синтаксично валідним absolute HTTPS URI до 2048 Unicode code points; валідні Unicode, IPv6 literal і user-info форми не канонізуються та зберігаються без зміни;
- relative URI, не-HTTPS scheme, blank URL, malformed URI, `NUL` та unpaired UTF-16 surrogates повертають `400`;
- `NUL` та unpaired UTF-16 surrogates у текстових полях не підтримуються; інші валідні interior control characters зберігаються без зміни;
- backend зберігає URL як недовірені metadata й ніколи не fetch/proxy/probe їх;
- Flutter не повинен пересилати OwlNest bearer token на media host;
- `id`, author, counters, viewer state, permissions і timestamps визначає сервер. Невідомі/server-owned JSON fields не можуть їх перевизначити.

PUT є full replacement усіх author-editable полів. Omitted `postType`, labels або media стають відповідно `PERSONAL`, `[]`, `[]`.

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

Backend повертає absolute `Instant`; elapsed-time formatting належить Flutter. Author projection навмисно виключає email, birth date, gender, onboarding state і presence. До explicit onboarding default author є privacy-neutral: generated `user_<id>` і `OwlNest user`; identity-provider name/email claims не стають публічними через post card. Поточна profile schema не має avatar, тому `avatarUrl` дорівнює `null`.

## Дані й узгодженість

Flyway `V3__create_post_tables.sql` створює:

- `post` — aggregate root, soft-delete marker і like/comment/repost counters;
- `post_label(post_id, position)` і `post_media(post_id, position)`;
- `post_like(post_id, account_id)`, `post_bookmark(...)`, `post_repost(...)`.

PostgreSQL є єдиним source of truth; Redis не використовується. `Post` — scalar JPA entity, ordered content та interaction rows обробляються bounded JDBC queries. Card scalars, labels і media читаються одним SQL statement, тому concurrent full replacement не створює змішаних версій. Усі existing-post mutations спочатку беруть `PESSIMISTIC_WRITE` lock active post row. Relation transition і like/repost counter delta виконуються в одній транзакції; composite primary keys є фінальним захистом від дублікатів.

Перший account/profile provisioning використовує PostgreSQL transaction advisory locks лише після lookup miss, з обов'язковим recheck. Established users не беруть ці locks. Lock order: identity miss → profile miss → post row.

Soft delete не видаляє content/interactions фізично, але всі user-facing reads і mutations фільтрують `deleted_at IS NULL`. Hard delete не є API operation; `ON DELETE CASCADE` підготовлений лише для майбутнього контрольованого purge.

## Верифікація

Контракт покривають PostgreSQL/Testcontainers + MockMvc тести:

- CRUD, ownership, soft delete, authentication і stable Problem Details codes;
- boundary/negative validation, ordered labels/media та safe server-owned projection;
- viewer-specific state, interaction idempotency і counter consistency;
- generated OpenAPI operation IDs та відсутність неімплементованих routes;
- PostgreSQL schema/constraints і concurrent interaction transitions.

Formatter/linter/coverage gate у repository не налаштовані; актуальний gate — IDE formatting, compilation, full `./gradlew test`, `./gradlew build`, `git diff --check` і незалежні architecture/code/data/security/QA reviews.
