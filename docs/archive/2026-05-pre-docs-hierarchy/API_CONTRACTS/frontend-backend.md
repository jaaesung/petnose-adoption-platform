> 보관 문서(Archive)
>
> 이 문서는 과거 설계/초안 기록입니다.
> 현재 구현 기준으로 사용하지 마세요.
> 현재 기준은 `docs/README.md`와 `docs/PROJECT_KNOWLEDGE_INDEX.md`에서 시작하세요.
> active canonical 문서와 충돌하면 active canonical 문서가 우선합니다.

# API Contract - Flutter / Spring Boot

This document mirrors the active MVP API contract. The primary canonical copy is `docs/PETNOSE_MVP_API_CONTRACT.md`.

Base URL: `http://<host>/api`

## Canonical Notes

- `users.id` is a numeric id.
- `dogs.id` is a UUID string.
- Roles are `USER` and `ADMIN`.
- Author display fields are stored directly on `users`.
- Dog image URLs are derived from `dog_images.file_path`.
- Qdrant point id is calculated from `dogs.id`; it is not a DB column.

## Users

Planned profile API:

```http
GET /api/users/me
PATCH /api/users/me/profile
```

Profile response fields may include:

```json
{
  "user_id": 101,
  "email": "user@example.com",
  "role": "USER",
  "display_name": "초코 보호자",
  "contact_phone": "01012341234",
  "region": "서울"
}
```

The profile update API is planned and not yet implemented.

## Dog Registration

```http
POST /api/dogs/register
Content-Type: multipart/form-data
```

Fields:

- `user_id`
- `name`
- `breed`
- `gender`
- `birth_date`
- `description`
- `nose_image`

Archived note: active MVP no longer sends profile images to `POST /api/dogs/register`; profile upload moved to `POST /api/adoption-posts`.

Normal response:

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
  "nose_image_url": "/files/dogs/{uuid}/nose/file.jpg",
  "profile_image_url": "/files/dogs/{uuid}/profile/file.jpg",
  "top_match": null,
  "message": "중복 의심 개체가 없어 등록이 완료되었습니다."
}
```

Duplicate suspected response:

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
  "nose_image_url": "/files/dogs/new-dog-id/nose/file.jpg",
  "profile_image_url": null,
  "top_match": {
    "dog_id": "existing-dog-id",
    "similarity_score": 0.99782,
    "breed": "말티즈"
  },
  "message": "기존 등록견과 동일 개체로 의심되어 등록이 제한됩니다."
}
```

`qdrant_point_id`, `verification_status`, and `embedding_status` are calculated response fields.

## Adoption Posts

Planned APIs:

```http
POST /api/adoption-posts
GET /api/adoption-posts
```

Post creation should require an author `display_name` and an eligible dog state.
