# PetNose MVP API Contract

## Scope

This is the active MVP API contract for the simplified DBML v2 canonical model.

Base URL: `http://<host>/api`

Authentication is planned with `Authorization: Bearer <JWT>`. During local MVP verification, some endpoints may still accept a temporary `user_id` form field until auth is implemented.

## Users

### Get Current User

```http
GET /api/users/me
Authorization: Bearer <JWT>
```

Response fields may include:

```json
{
  "user_id": 101,
  "email": "user@example.com",
  "role": "USER",
  "display_name": "초코 보호자",
  "contact_phone": "010-0000-0000",
  "region": "서울",
  "is_active": true,
  "created_at": "2026-05-08T00:00:00Z"
}
```

### Update User Profile

Planned API. Not implemented yet.

Preferred shape:

```http
PATCH /api/users/me/profile
Authorization: Bearer <JWT>
Content-Type: application/json
```

```json
{
  "display_name": "초코 보호자",
  "contact_phone": "010-0000-0000",
  "region": "서울"
}
```

`display_name` is nullable at signup and dog verification time. Adoption post creation should require it.

## Dog Registration

### Register Dog With Nose Image

```http
POST /api/dogs/register
Content-Type: multipart/form-data
Authorization: Bearer <JWT>
```

Form fields:

- `user_id`: number, temporary local MVP field until JWT principal is used
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

Calculation policy:

- `qdrant_point_id` is not a DB column. It is calculated as `dog_id` for normal registration and `null` for duplicate suspected registration.
- `verification_status` is not a DB column. It is calculated from the latest `verification_logs.result`.
- `embedding_status` is not a DB column. It is calculated from the latest `verification_logs.result`.
- Similarity, duplicate candidate, model, dimension, and failure metadata are stored in `verification_logs`.
- The embedding vector is stored only in Qdrant.

Errors:

- `400`: invalid request fields
- `404`: user or dog image metadata not found
- `422`: image or embedding input rejected
- `503`: embedding service or Qdrant unavailable

## Adoption Posts

Planned API. Not implemented yet.

### Create Adoption Post

```http
POST /api/adoption-posts
Authorization: Bearer <JWT>
Content-Type: application/json
```

```json
{
  "dog_id": "uuid",
  "title": "말티즈 가족을 찾습니다",
  "content": "상세 내용..."
}
```

The service should require:

- `users.display_name` exists for the author.
- `dogs.status` allows posting.
- The dog belongs to the author or the author has an allowed admin workflow.

Allowed post statuses:

- `DRAFT`
- `OPEN`
- `RESERVED`
- `COMPLETED`
- `CLOSED`

## Removed APIs

The old separate publisher profile API and report API are not part of the current MVP v2 contract. Author display information is handled through the user profile shape above.
