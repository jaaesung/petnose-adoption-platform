# PetNose MVP API Contract

## Scope

This is the active MVP API contract for the simplified DBML v2 canonical model.

Base URL: `http://<host>/api`

This contract records the Flutter MVP flow against the current backend implementation. It does not introduce Firebase, chat, push, expanded profile tables, report APIs, refresh tokens, or any non-canonical role concept.

## Canonical Response Rules

- JSON response fields use `snake_case`.
- Common error responses use the shape below:

```json
{
  "error_code": "VALIDATION_FAILED",
  "message": "입력값 검증에 실패했습니다.",
  "details": {
    "timestamp": "2026-05-13T00:00:00Z"
  }
}
```

- MVP roles are `USER` and `ADMIN` only.
- `users` owns `display_name`, `contact_phone`, `region`, and `is_active` directly.
- MySQL is the source of truth. Qdrant is a nose embedding vector index only.
- `dog_images.file_path` stores a path relative to the upload root.
- `qdrant_point_id`, `verification_status`, and `embedding_status` are API-calculated fields, not DB columns.
- Public adoption post list/detail responses must not expose `nose_image_url`.
- Owner-scoped dog registration responses may return the newly submitted dog's own `nose_image_url`.
- `top_match` must not expose a raw `nose_image_url`.

## Flutter MVP Flow Readiness

| Step | Endpoint or branch | Current status | Flutter dependency |
| --- | --- | --- | --- |
| 1 | `POST /api/dogs/register` | Implemented | Returns registration result fields listed below. |
| 2 | `registration_allowed=false` | Implemented | Branch to duplicate suspected screen and block post creation. |
| 3 | `registration_allowed=true` | Implemented | Use `dog_id` for post creation. |
| 4 | `GET /api/users/me` | Implemented | Read profile readiness fields. |
| 5 | `PATCH /api/users/me/profile` | Implemented | Fill missing `display_name`, plus optional phone/region. |
| 6 | `POST /api/adoption-posts` | Implemented | Creates `DRAFT` or `OPEN` post for verified owner dog. |
| 7 | `GET /api/adoption-posts` | Implemented | Renders public post list without nose image. |
| 8 | `GET /api/adoption-posts/{post_id}` | Implemented | Renders public post detail without nose image. |
| 9 | `GET /api/adoption-posts/me` | Implemented | Lists only the current user's posts. |
| 10 | `PATCH /api/adoption-posts/{post_id}/status` | Implemented | Owner-only post status management. |

No requested Flutter-flow endpoint is currently marked as unimplemented in latest `develop`. This branch should keep any wider API expansion as a follow-up, not as new scope.

## Users

### Get Current User

```http
GET /api/users/me
Authorization: Bearer <JWT>
```

Response `200`:

```json
{
  "user_id": 101,
  "email": "user@example.com",
  "role": "USER",
  "display_name": "초코 보호자",
  "contact_phone": "010-0000-0000",
  "region": "서울",
  "is_active": true
}
```

Flutter-required fields:

- `user_id`
- `email`
- `role`
- `display_name`
- `contact_phone`
- `region`
- `is_active`

`display_name`, `contact_phone`, and `region` may be `null`, but the field names are part of the response contract. `created_at` is not part of the current `GET /api/users/me` response.

### Update User Profile

```http
PATCH /api/users/me/profile
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request body:

```json
{
  "display_name": "초코 보호자",
  "contact_phone": "010-0000-0000",
  "region": "서울"
}
```

Response `200`:

```json
{
  "user_id": 101,
  "display_name": "초코 보호자",
  "contact_phone": "010-0000-0000",
  "region": "서울"
}
```

Contract notes:

- At least one of `display_name`, `contact_phone`, or `region` must be present.
- Omitted fields keep their current value.
- Explicit `null` clears the corresponding profile field.
- Length limits follow the canonical `users` columns: `display_name <= 150`, `contact_phone <= 30`, `region <= 100`.
- Adoption post creation requires a non-blank `display_name`.

## Dog Registration

### Register Dog With Nose Image

```http
POST /api/dogs/register
Content-Type: multipart/form-data
Authorization: Bearer <JWT>
```

Authentication policy:

- JWT principal is preferred.
- If an `Authorization: Bearer <JWT>` header is present, the JWT principal wins over any submitted `user_id`.
- If the JWT is invalid or expired, the request fails and does not fall back to `user_id`.
- If the JWT header is absent, `user_id` remains a temporary local/dev fallback.

Form fields:

- `user_id`: number, temporary local/dev fallback until full principal-only registration
- `name`: string
- `breed`: string
- `gender`: `MALE`, `FEMALE`, or `UNKNOWN`
- `birth_date`: `YYYY-MM-DD`, optional
- `description`: string, optional
- `nose_image`: file, required
- `profile_image`: file, optional

Response `201`, normal registration:

```json
{
  "dog_id": "uuid",
  "registration_allowed": true,
  "status": "REGISTERED",
  "verification_status": "VERIFIED",
  "embedding_status": "COMPLETED",
  "qdrant_point_id": "uuid",
  "model": "dog-nose-identification2:s101_224",
  "dimension": 2048,
  "max_similarity_score": 0.41,
  "nose_image_url": "/files/dogs/{uuid}/nose/20260508_010203_nose.jpg",
  "profile_image_url": "/files/dogs/{uuid}/profile/20260508_010203_profile.jpg",
  "top_match": null,
  "message": "중복 의심 개체가 없어 등록이 완료되었습니다."
}
```

Flutter-required normal registration fields:

- `dog_id`
- `registration_allowed`
- `status`
- `verification_status`
- `embedding_status`
- `qdrant_point_id`
- `model`
- `dimension`
- `max_similarity_score`
- `nose_image_url`
- `profile_image_url`
- `top_match`
- `message`

Response `200`, duplicate suspected:

```json
{
  "dog_id": "new-dog-id",
  "registration_allowed": false,
  "status": "DUPLICATE_SUSPECTED",
  "verification_status": "DUPLICATE_SUSPECTED",
  "embedding_status": "SKIPPED_DUPLICATE",
  "qdrant_point_id": null,
  "model": "dog-nose-identification2:s101_224",
  "dimension": 2048,
  "max_similarity_score": 0.99782,
  "nose_image_url": "/files/dogs/new-dog-id/nose/...",
  "profile_image_url": null,
  "top_match": {
    "dog_id": "existing-dog-id",
    "similarity_score": 0.99782,
    "breed": "말티즈"
  },
  "message": "기존 등록견과 동일 개체로 의심되어 등록이 제한됩니다."
}
```

Duplicate suspected contract:

- `registration_allowed` is `false`.
- `status` is `DUPLICATE_SUSPECTED`.
- `verification_status` is `DUPLICATE_SUSPECTED`.
- `embedding_status` is `SKIPPED_DUPLICATE`.
- `qdrant_point_id` is `null`.
- `max_similarity_score` is the highest returned match score.
- `top_match` contains only `dog_id`, `similarity_score`, and `breed`.
- `top_match` must not contain `nose_image_url`.
- Top-level `nose_image_url` is the newly submitted dog image in an owner-scoped registration response and is not a public exposure.
- `message` is safe for Flutter duplicate suspected screen copy.

Calculation policy:

- `qdrant_point_id` is calculated as `dog_id` for normal registration and `null` for duplicate suspected registration.
- `verification_status` is calculated from the latest verification result.
- `embedding_status` is calculated from the latest verification result.
- Similarity, duplicate candidate, model, dimension, and failure metadata are stored in verification history.
- The embedding vector is stored only in Qdrant.

Errors:

- `400`: invalid request fields
- `401`: missing, malformed, invalid, or expired JWT where JWT auth is required
- `403`: inactive user
- `404`: user or dog image metadata not found
- `422`: image or embedding input rejected
- `503`: embedding service or Qdrant unavailable

## Adoption Posts

### Create Adoption Post

```http
POST /api/adoption-posts
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request body:

```json
{
  "dog_id": "uuid",
  "title": "말티즈 가족을 찾습니다",
  "content": "상세 내용...",
  "status": "OPEN"
}
```

Response `201`:

```json
{
  "post_id": 501,
  "dog_id": "uuid",
  "title": "말티즈 가족을 찾습니다",
  "content": "상세 내용...",
  "status": "OPEN",
  "published_at": "2026-05-13T10:00:00",
  "created_at": "2026-05-13T10:00:00"
}
```

Contract notes:

- JWT principal is required.
- The dog must belong to the current user.
- `users.display_name` must be non-blank before creating a post.
- The dog must be `REGISTERED`.
- The latest verification result must be `PASSED`.
- Dogs in `DUPLICATE_SUSPECTED`, `REJECTED`, or `INACTIVE` state are not eligible.
- A dog cannot already have an active post in `DRAFT`, `OPEN`, or `RESERVED`.
- Create accepts `DRAFT` or `OPEN`; omitted `status` defaults to `DRAFT`.

### List Public Adoption Posts

```http
GET /api/adoption-posts?status=OPEN&page=0&size=20
```

Query parameters:

- `status`: optional, defaults to `OPEN`; supported values are `OPEN`, `RESERVED`, and `COMPLETED`.
- `page`: optional, zero-based page number, defaults to `0`.
- `size`: optional page size, defaults to `20`, maximum `100`.

Public feed status display mapping:

- `OPEN` = 분양가능
- `RESERVED` = 예약중
- `COMPLETED` = 분양완료

Response `200`:

```json
{
  "items": [
    {
      "post_id": 501,
      "dog_id": "uuid",
      "title": "말티즈 가족을 찾습니다",
      "status": "OPEN",
      "dog_name": "초코",
      "breed": "말티즈",
      "gender": "MALE",
      "birth_date": "2024-01-01",
      "profile_image_url": "/files/dogs/{uuid}/profile/profile.jpg",
      "verification_status": "VERIFIED",
      "author_display_name": "초코 보호자",
      "author_region": "서울",
      "published_at": "2026-05-13T10:00:00",
      "created_at": "2026-05-13T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "total_count": 1
}
```

Contract notes:

- Public list status defaults to `OPEN`.
- Public list accepts `OPEN`, `RESERVED`, or `COMPLETED`.
- `DRAFT` and `CLOSED` are not valid public list statuses.
- `verification_status` is included for Flutter display.
- `profile_image_url` may be exposed.
- `nose_image_url` must not be exposed.
- Invalid `status` returns `INVALID_POST_STATUS`.
- Invalid `page` or `size` returns `INVALID_PAGE_REQUEST`.

### Get Public Adoption Post Detail

```http
GET /api/adoption-posts/{post_id}
```

Response `200`:

```json
{
  "post_id": 501,
  "dog_id": "uuid",
  "title": "말티즈 가족을 찾습니다",
  "content": "상세 내용...",
  "status": "OPEN",
  "dog_name": "초코",
  "breed": "말티즈",
  "gender": "MALE",
  "birth_date": "2024-01-01",
  "description": "사람을 좋아합니다.",
  "profile_image_url": "/files/dogs/{uuid}/profile/profile.jpg",
  "verification_status": "VERIFIED",
  "author_display_name": "초코 보호자",
  "author_contact_phone": "010-0000-0000",
  "author_region": "서울",
  "published_at": "2026-05-13T10:00:00",
  "created_at": "2026-05-13T10:00:00",
  "updated_at": "2026-05-13T10:00:00"
}
```

Contract notes:

- Detail is public only for `OPEN`, `RESERVED`, and `COMPLETED` posts.
- `verification_status` is included for Flutter display.
- `profile_image_url` may be exposed.
- `nose_image_url` must not be exposed.
- Missing posts return `POST_NOT_FOUND`.
- Non-public posts return `POST_NOT_PUBLIC`.

### List Current User's Adoption Posts

```http
GET /api/adoption-posts/me?status=OPEN&page=0&size=20
Authorization: Bearer <JWT>
```

Query parameters:

- `status`: optional; supported values are `DRAFT`, `OPEN`, `RESERVED`, `COMPLETED`, and `CLOSED`.
- `page`: optional, zero-based page number, defaults to `0`.
- `size`: optional page size, defaults to `20`, maximum `100`.

Response `200`:

```json
{
  "items": [
    {
      "post_id": 501,
      "dog_id": "uuid",
      "title": "말티즈 가족을 찾습니다",
      "status": "OPEN",
      "dog_name": "초코",
      "breed": "말티즈",
      "gender": "MALE",
      "birth_date": "2024-01-01",
      "profile_image_url": "/files/dogs/{uuid}/profile/profile.jpg",
      "verification_status": "VERIFIED",
      "published_at": "2026-05-13T10:00:00",
      "closed_at": null,
      "created_at": "2026-05-13T10:00:00",
      "updated_at": "2026-05-13T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "total_count": 1
}
```

Contract notes:

- Bearer JWT authorization is required.
- The response returns only posts owned by the current user.
- Omitting `status` returns all statuses owned by the current user.
- `nose_image_url` must not be exposed.
- Invalid `status` returns `INVALID_POST_STATUS`.
- Invalid `page` or `size` returns `INVALID_PAGE_REQUEST`.
- Missing, malformed, invalid, or expired JWT returns `UNAUTHORIZED`.

### Update Adoption Post Status

```http
PATCH /api/adoption-posts/{post_id}/status
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request body:

```json
{
  "status": "RESERVED"
}
```

Response `200`:

```json
{
  "post_id": 501,
  "dog_id": "uuid",
  "title": "말티즈 가족을 찾습니다",
  "content": "상세 내용...",
  "status": "RESERVED",
  "published_at": "2026-05-13T10:00:00",
  "closed_at": null,
  "created_at": "2026-05-13T10:00:00",
  "updated_at": "2026-05-13T10:05:00"
}
```

Allowed transitions:

- `DRAFT` -> `OPEN`
- `DRAFT` -> `CLOSED`
- `OPEN` -> `RESERVED`
- `OPEN` -> `COMPLETED`
- `OPEN` -> `CLOSED`
- `RESERVED` -> `OPEN`
- `RESERVED` -> `COMPLETED`
- `RESERVED` -> `CLOSED`

Contract notes:

- Bearer JWT authorization is required.
- Only the post owner may update status.
- `COMPLETED` and `CLOSED` are terminal states.
- `COMPLETED` sets `dogs.status` to `ADOPTED`.
- `CLOSED` does not set `dogs.status` to `ADOPTED`.
- Same-status PATCH is implemented as a no-op and returns the current post status response.
- `DRAFT` -> `OPEN` validates the same publish eligibility as post creation.
- Missing posts return `POST_NOT_FOUND`.
- Non-owner updates return `POST_OWNER_MISMATCH`.
- Invalid `status` returns `INVALID_POST_STATUS`.
- Invalid transitions return `INVALID_STATUS_TRANSITION`.
- Missing, malformed, invalid, or expired JWT returns `UNAUTHORIZED`.

### Adoption Post Error Codes

- `POST_NOT_FOUND`: adoption post does not exist.
- `POST_NOT_PUBLIC`: adoption post exists but is not publicly visible.
- `POST_OWNER_MISMATCH`: current user does not own the post.
- `INVALID_POST_STATUS`: unsupported or malformed status value.
- `INVALID_STATUS_TRANSITION`: requested status transition is not allowed.
- `INVALID_PAGE_REQUEST`: page or size parameter is outside the supported range.
- `UNAUTHORIZED`: JWT authorization is missing, malformed, invalid, expired, or resolves to no active user.

### Local Verification Examples

Replace `<JWT>` and `<post_id>` with local test values.

```bash
curl "http://localhost/api/adoption-posts?status=OPEN&page=0&size=20"
curl "http://localhost/api/adoption-posts?status=RESERVED&page=0&size=20"
curl "http://localhost/api/adoption-posts?status=COMPLETED&page=0&size=20"

curl -H "Authorization: Bearer <JWT>" \
  "http://localhost/api/adoption-posts/me?page=0&size=20"

curl -X PATCH "http://localhost/api/adoption-posts/<post_id>/status" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"status":"RESERVED"}'

curl -X PATCH "http://localhost/api/adoption-posts/<post_id>/status" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"status":"COMPLETED"}'
```

## JWT Principal Follow-up

`POST /api/dogs/register` already resolves the owner from JWT first at the controller boundary, but it still keeps the `user_id` local/dev fallback and passes an owner id into `DogRegistrationService`. This branch does not change `DogRegistrationService`.

Before hardening adoption post creation beyond MVP/dev usage, remove the `user_id` fallback from dog registration or gate it behind an explicit local profile. This keeps the dog owner established by the same JWT principal that `POST /api/adoption-posts` already requires.

## Removed APIs and Concepts

The old separate publisher/profile variants and report/token extension areas are not part of the current MVP v2 contract. Do not introduce:

- `SHELTER` or `ADOPTER` roles
- `publisher_profiles`
- `shelter_profiles`
- `seller_profiles`
- `auth_logs`
- `reports`
- `refresh_tokens`
- Firebase, chat, or push APIs
