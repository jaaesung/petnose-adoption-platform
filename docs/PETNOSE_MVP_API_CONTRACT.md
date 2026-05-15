# PetNose MVP API 계약

## 문서 범위

이 문서는 simplified DBML v2 canonical model 기준의 active MVP API contract다.

Base URL: `http://<host>/api`

이 문서는 current backend implementation에 맞춘 Flutter MVP flow를 기록한다. Firebase, chat, push, expanded profile table, report API, refresh token, non-canonical role concept을 추가하지 않는다.

## Canonical Response Rules

- JSON response field는 `snake_case`를 사용한다.
- 공통 error response는 아래 shape를 사용한다.

```json
{
  "error_code": "VALIDATION_FAILED",
  "message": "입력값 검증에 실패했습니다.",
  "details": {
    "timestamp": "2026-05-13T00:00:00Z"
  }
}
```

- MVP role은 `USER`와 `ADMIN`만 사용한다.
- `users`가 `display_name`, `contact_phone`, `region`, `is_active`를 직접 가진다.
- MySQL은 source of truth다. Qdrant는 nose embedding vector index일 뿐이다.
- `dog_images.file_path`는 upload root 기준 상대 경로를 저장한다.
- `qdrant_point_id`, `verification_status`, `embedding_status`는 API-calculated field이며 DB column이 아니다.
- public adoption post list/detail response는 `nose_image_url`을 노출하지 않는다.
- owner-scoped dog registration response는 새로 제출한 dog 자신의 `nose_image_url`을 반환할 수 있다.
- `top_match`는 raw `nose_image_url`을 노출하지 않는다.
- handover verification response는 `nose_image_url`, `top_matched_dog_id`, 다른 dog의 `dog_id`, Qdrant payload details, `author_user_id`를 노출하지 않는다.

## Flutter MVP Flow Readiness

| Step | Endpoint or branch | Current status | Flutter dependency |
| --- | --- | --- | --- |
| 1 | `POST /api/dogs/register` | 구현됨 | 아래 registration result field를 반환한다. |
| 2 | `registration_allowed=false` | 구현됨 | duplicate suspected 화면으로 분기하고 post creation을 막는다. |
| 3 | `registration_allowed=true` | 구현됨 | post creation에 `dog_id`를 사용한다. |
| 4 | `GET /api/users/me` | 구현됨 | profile readiness field를 읽는다. |
| 5 | `PATCH /api/users/me/profile` | 구현됨 | 누락된 `display_name`과 선택적 phone/region을 채운다. |
| 6 | `POST /api/adoption-posts` | 구현됨 | verified owner dog로 `DRAFT` 또는 `OPEN` post를 만든다. |
| 7 | `GET /api/adoption-posts` | 구현됨 | nose image 없이 public post list를 렌더링한다. |
| 8 | `GET /api/adoption-posts/{post_id}` | 구현됨 | nose image 없이 public post detail을 렌더링한다. |
| 9 | `GET /api/adoption-posts/me` | 구현됨 | current user의 post만 나열한다. |
| 10 | `PATCH /api/adoption-posts/{post_id}/status` | 구현됨 | owner-only post status management를 수행한다. |
| 11 | `POST /api/adoption-posts/{post_id}/handover-verifications` | 구현됨 | stateless handover-time identity check로 MVP trust/safety flow에 포함된다. |

handover verification endpoint는 MVP trust/safety flow의 일부다. 이 contract 밖의 더 넓은 API 확장은 follow-up scope로 남긴다.

## Users

### Current User 조회

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

`display_name`, `contact_phone`, `region`은 `null`일 수 있지만 field name 자체는 response contract에 포함된다. `created_at`은 current `GET /api/users/me` response에 포함하지 않는다.

### User Profile 수정

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

- `display_name`, `contact_phone`, `region` 중 하나 이상은 있어야 한다.
- 생략한 field는 현재 값을 유지한다.
- 명시적 `null`은 해당 profile field를 비운다.
- length limit은 canonical `users` column을 따른다: `display_name <= 150`, `contact_phone <= 30`, `region <= 100`.
- adoption post creation에는 non-blank `display_name`이 필요하다.

## Dog Registration

### Nose Image로 Dog 등록

```http
POST /api/dogs/register
Content-Type: multipart/form-data
Authorization: Bearer <JWT>
```

Authentication policy:

- JWT principal을 우선한다.
- `Authorization: Bearer <JWT>` header가 있으면 JWT principal이 제출된 `user_id`보다 우선한다.
- JWT가 invalid 또는 expired이면 request는 실패하며 `user_id`로 fallback하지 않는다.
- JWT header가 없을 때만 `user_id`가 temporary local/dev fallback으로 남는다.

Form fields:

- `user_id`: number, full principal-only registration 전까지 temporary local/dev fallback
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

- `registration_allowed`는 `false`다.
- `status`는 `DUPLICATE_SUSPECTED`다.
- `verification_status`는 `DUPLICATE_SUSPECTED`다.
- `embedding_status`는 `SKIPPED_DUPLICATE`다.
- `qdrant_point_id`는 `null`이다.
- `max_similarity_score`는 반환된 match 중 가장 높은 score다.
- `top_match`는 `dog_id`, `similarity_score`, `breed`만 포함한다.
- `top_match`는 `nose_image_url`을 포함하지 않는다.
- top-level `nose_image_url`은 owner-scoped registration response에서 새로 제출한 dog image이며 public exposure가 아니다.
- `message`는 Flutter duplicate suspected screen copy로 사용할 수 있다.

Calculation policy:

- `qdrant_point_id`는 normal registration에서 `dog_id`, duplicate suspected registration에서 `null`로 계산한다.
- `verification_status`는 latest verification result에서 계산한다.
- `embedding_status`는 latest verification result에서 계산한다.
- similarity, duplicate candidate, model, dimension, failure metadata는 verification history에 저장한다.
- embedding vector는 Qdrant에만 저장한다.

Errors:

- `400`: invalid request fields
- `401`: JWT auth가 필요한 곳에서 missing, malformed, invalid, or expired JWT
- `403`: inactive user
- `404`: user or dog image metadata not found
- `422`: image or embedding input rejected
- `503`: embedding service or Qdrant unavailable

## Adoption Posts

### Adoption Post 생성

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

- JWT principal이 필요하다.
- dog는 current user 소유여야 한다.
- post 생성 전 `users.display_name`은 non-blank여야 한다.
- dog는 `REGISTERED`여야 한다.
- latest verification result는 `PASSED`여야 한다.
- `DUPLICATE_SUSPECTED`, `REJECTED`, `INACTIVE` 상태의 dog는 대상이 될 수 없다.
- 같은 dog에 `DRAFT`, `OPEN`, `RESERVED` 상태의 active post가 이미 있으면 안 된다.
- create는 `DRAFT` 또는 `OPEN`을 받는다. `status`를 생략하면 기본값은 `DRAFT`다.

### 공개 분양글 목록(Public Adoption Post List)

```http
GET /api/adoption-posts?status=OPEN&page=0&size=20
```

Query parameters:

- `status`: optional, 기본값은 `OPEN`; 지원 값은 `OPEN`, `RESERVED`, `COMPLETED`.
- `page`: optional, zero-based page number, 기본값은 `0`.
- `size`: optional page size, 기본값은 `20`, 최대 `100`.

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

- public list `status` 기본값은 `OPEN`이다.
- public list는 `OPEN`, `RESERVED`, `COMPLETED`를 받는다.
- `DRAFT`와 `CLOSED`는 public list status로 유효하지 않다.
- `verification_status`는 Flutter display를 위해 포함한다.
- `profile_image_url`은 노출할 수 있다.
- `nose_image_url`은 노출하지 않는다.
- invalid `status`는 `INVALID_POST_STATUS`를 반환한다.
- invalid `page` 또는 `size`는 `INVALID_PAGE_REQUEST`를 반환한다.

### 공개 분양글 상세(Public Adoption Post Detail)

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

- detail은 `OPEN`, `RESERVED`, `COMPLETED` post에 대해서만 public이다.
- `verification_status`는 Flutter display를 위해 포함한다.
- `profile_image_url`은 노출할 수 있다.
- `nose_image_url`은 노출하지 않는다.
- missing post는 `POST_NOT_FOUND`를 반환한다.
- non-public post는 `POST_NOT_PUBLIC`을 반환한다.

### Current User의 Adoption Posts 목록

```http
GET /api/adoption-posts/me?status=OPEN&page=0&size=20
Authorization: Bearer <JWT>
```

Query parameters:

- `status`: optional; 지원 값은 `DRAFT`, `OPEN`, `RESERVED`, `COMPLETED`, `CLOSED`.
- `page`: optional, zero-based page number, 기본값은 `0`.
- `size`: optional page size, 기본값은 `20`, 최대 `100`.

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

- Bearer JWT authorization이 필요하다.
- response는 current user가 소유한 post만 반환한다.
- `status`를 생략하면 current user가 소유한 모든 status를 반환한다.
- `nose_image_url`은 노출하지 않는다.
- invalid `status`는 `INVALID_POST_STATUS`를 반환한다.
- invalid `page` 또는 `size`는 `INVALID_PAGE_REQUEST`를 반환한다.
- missing, malformed, invalid, or expired JWT는 `UNAUTHORIZED`를 반환한다.

### Adoption Post Status 수정

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

- Bearer JWT authorization이 필요하다.
- post owner만 status를 수정할 수 있다.
- `COMPLETED`와 `CLOSED`는 terminal state다.
- `COMPLETED`는 `dogs.status`를 `ADOPTED`로 설정한다.
- `CLOSED`는 `dogs.status`를 `ADOPTED`로 설정하지 않는다.
- same-status PATCH는 no-op으로 구현되어 현재 post status response를 반환한다.
- `DRAFT` -> `OPEN`은 post creation과 같은 publish eligibility를 검증한다.
- missing post는 `POST_NOT_FOUND`를 반환한다.
- non-owner update는 `POST_OWNER_MISMATCH`를 반환한다.
- invalid `status`는 `INVALID_POST_STATUS`를 반환한다.
- invalid transition은 `INVALID_STATUS_TRANSITION`을 반환한다.
- missing, malformed, invalid, or expired JWT는 `UNAUTHORIZED`를 반환한다.

### 인도 시점 비문 확인(Handover-Time Dog Nose Verification)

```http
POST /api/adoption-posts/{post_id}/handover-verifications
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

Form-data:

- `nose_image`: file, required

Purpose:

- stateless handover-time identity verification이다.
- 새로 촬영한 dog nose image가 `adoption_posts.dog_id`에 연결된 dog와 일치하는지 확인한다.
- persistent record를 만들거나 수정하지 않는다.
- handover image를 저장하지 않는다.
- dog를 생성하지 않는다.
- `dog_images` row를 생성하지 않는다.
- current MVP implementation에서는 `verification_logs` row를 생성하지 않는다.
- `adoption_posts.status` 또는 `dogs.status`를 변경하지 않는다.
- 자동으로 adoption completion을 수행하지 않는다.
- adoption completion은 기존 owner-only status update action으로 남는다.

Authorization:

- Bearer JWT authorization이 필요하다.
- current user는 active 상태여야 한다.
- missing, malformed, invalid, or expired JWT는 `UNAUTHORIZED`를 반환한다.
- token이 deleted 또는 missing user로 resolve되면 `USER_NOT_FOUND`를 반환한다.
- inactive current user는 HTTP `403`과 `USER_INACTIVE`를 반환한다.
- current MVP에는 reservation/applicant table이 없으므로 이 endpoint는 owner-only가 아니다. handover verification을 위한 authenticated user-facing safety check다.
- `ADOPTER` role 또는 reservation ownership rule을 도입하지 않는다.

Post status policy:

- Allowed statuses: `OPEN`, `RESERVED`.
- Rejected statuses: `DRAFT`, `COMPLETED`, `CLOSED`.
- rejected status는 HTTP `400`과 `POST_NOT_VERIFIABLE`을 반환한다.

Expected dog policy:

- `expected_dog_id`는 `adoption_posts.dog_id`다.
- expected dog가 존재하지 않으면 `DOG_NOT_FOUND`를 반환한다.
- expected dog는 `REGISTERED`여야 한다.
- expected dog가 `REGISTERED`가 아니면 `DOG_NOT_VERIFIED`를 반환한다.

Normal `200` decision values:

- `MATCHED`
- `AMBIGUOUS`
- `NOT_MATCHED`
- `NO_MATCH_CANDIDATE`

Response `200`, matched:

```json
{
  "post_id": 501,
  "expected_dog_id": "dog-uuid",
  "matched": true,
  "decision": "MATCHED",
  "similarity_score": 0.98231,
  "threshold": 0.92,
  "ambiguous_threshold": 0.88,
  "top_match_is_expected": true,
  "model": "dog-nose-identification2:s101_224",
  "dimension": 2048,
  "message": "분양글에 등록된 강아지와 일치합니다."
}
```

Response `200`, ambiguous:

```json
{
  "post_id": 501,
  "expected_dog_id": "dog-uuid",
  "matched": false,
  "decision": "AMBIGUOUS",
  "similarity_score": 0.90112,
  "threshold": 0.92,
  "ambiguous_threshold": 0.88,
  "top_match_is_expected": true,
  "model": "dog-nose-identification2:s101_224",
  "dimension": 2048,
  "message": "유사도가 기준에 근접하지만 확정하기 어렵습니다. 비문 이미지를 다시 촬영해주세요."
}
```

Response `200`, not matched:

```json
{
  "post_id": 501,
  "expected_dog_id": "dog-uuid",
  "matched": false,
  "decision": "NOT_MATCHED",
  "similarity_score": 0.42103,
  "threshold": 0.92,
  "ambiguous_threshold": 0.88,
  "top_match_is_expected": false,
  "model": "dog-nose-identification2:s101_224",
  "dimension": 2048,
  "message": "분양글에 등록된 강아지와 일치하지 않습니다. 거래 전 확인이 필요합니다."
}
```

Response `200`, no match candidate:

```json
{
  "post_id": 501,
  "expected_dog_id": "dog-uuid",
  "matched": false,
  "decision": "NO_MATCH_CANDIDATE",
  "similarity_score": null,
  "threshold": 0.92,
  "ambiguous_threshold": 0.88,
  "top_match_is_expected": false,
  "model": "dog-nose-identification2:s101_224",
  "dimension": 2048,
  "message": "일치 후보를 찾지 못했습니다. 비문 이미지를 다시 촬영해주세요."
}
```

Decision algorithm:

- `expected_dog_id = adoption_posts.dog_id`
- `top_result = first Qdrant search result`

Qdrant result가 없는 경우:

- `decision = NO_MATCH_CANDIDATE`
- `matched = false`
- `similarity_score = null`
- `top_match_is_expected = false`

`top_result.dog_id == expected_dog_id`이고 `score >= match_threshold`인 경우:

- `decision = MATCHED`
- `matched = true`
- `top_match_is_expected = true`

`top_result.dog_id == expected_dog_id`이고 `ambiguous_threshold <= score < match_threshold`인 경우:

- `decision = AMBIGUOUS`
- `matched = false`
- `top_match_is_expected = true`

그 외의 경우:

- `decision = NOT_MATCHED`
- `matched = false`
- `top_match_is_expected = true` only if `top_result.dog_id == expected_dog_id`
- `top_match_is_expected = false` if `top_result.dog_id` differs from `expected_dog_id`

Privacy rules:

- public/user-facing response는 `nose_image_url`을 노출하지 않는다.
- handover verification response는 `top_matched_dog_id`를 노출하지 않는다.
- handover verification response는 다른 dog의 `dog_id`를 노출하지 않는다.
- handover verification response는 Qdrant payload details를 노출하지 않는다.
- handover verification response는 `author_user_id`를 노출하지 않는다.
- `expected_dog_id`는 adoption post workflow가 이미 dog를 참조하므로 노출할 수 있다.
- `similarity_score`, `threshold`, `ambiguous_threshold`, `model`, `dimension`, `top_match_is_expected`는 노출할 수 있다.

Config defaults:

- `match_threshold = 0.92`
- `ambiguous_threshold = 0.88`
- `top_k = 5`
- 이 값들은 runtime configuration이며 DB field가 아니다.

Failure behavior:

- missing 또는 empty `nose_image`는 common error response와 `NOSE_IMAGE_REQUIRED`를 반환한다.
- invalid handover image bytes 또는 embed upstream rejection은 common error response와 `INVALID_NOSE_IMAGE`를 반환한다.
- unavailable embed service는 common error response와 `EMBED_SERVICE_UNAVAILABLE`을 반환한다.
- empty embedding output은 common error response와 `EMPTY_EMBEDDING`을 반환한다.
- embedding dimension mismatch는 common error response와 `EMBEDDING_DIMENSION_MISMATCH`를 반환한다.
- Qdrant search failure는 common error response와 `QDRANT_SEARCH_FAILED`를 반환한다.

### Adoption Post Error Codes

- `POST_NOT_FOUND`: adoption post가 존재하지 않는다.
- `POST_NOT_PUBLIC`: adoption post는 존재하지만 public visible 상태가 아니다.
- `POST_NOT_VERIFIABLE`: adoption post는 존재하지만 current status에서 handover verification을 수행할 수 없다.
- `POST_OWNER_MISMATCH`: current user가 post owner가 아니다.
- `DOG_NOT_FOUND`: adoption post가 참조하는 dog가 존재하지 않는다.
- `DOG_NOT_VERIFIED`: expected dog가 required verified/registered eligibility를 만족하지 않는다.
- `INVALID_POST_STATUS`: 지원하지 않거나 malformed status value다.
- `INVALID_STATUS_TRANSITION`: 요청한 status transition이 허용되지 않는다.
- `INVALID_PAGE_REQUEST`: page 또는 size parameter가 supported range 밖이다.
- `NOSE_IMAGE_REQUIRED`: handover nose image multipart field가 없거나 비어 있다.
- `INVALID_NOSE_IMAGE`: handover nose image를 처리할 수 없다.
- `EMBED_SERVICE_UNAVAILABLE`: embedding service에 접근할 수 없거나 사용할 수 없다.
- `EMPTY_EMBEDDING`: embedding service가 vector를 반환하지 않았다.
- `EMBEDDING_DIMENSION_MISMATCH`: embedding dimension이 configured Qdrant vector dimension과 맞지 않는다.
- `QDRANT_SEARCH_FAILED`: Qdrant vector search가 실패했다.
- `UNAUTHORIZED`: JWT authorization이 missing, malformed, invalid, or expired 상태다.
- `USER_NOT_FOUND`: JWT subject가 existing user로 매핑되지 않는다.
- `USER_INACTIVE`: JWT subject가 inactive user로 매핑된다.

### Local Verification Examples

`<JWT>`와 `<post_id>`를 local test value로 바꿔 사용한다.

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

curl -X POST "http://localhost/api/adoption-posts/<post_id>/handover-verifications" \
  -H "Authorization: Bearer <JWT>" \
  -F "nose_image=@/path/to/current-handover-nose.jpg"
```

## JWT Principal Follow-up

`POST /api/dogs/register`는 controller boundary에서 이미 JWT로 owner를 먼저 resolve하지만, 아직 `user_id` local/dev fallback을 유지하고 owner id를 `DogRegistrationService`에 전달한다. 이 branch는 `DogRegistrationService`를 변경하지 않는다.

MVP/dev usage를 넘어 adoption post creation을 hardening하기 전에는 dog registration에서 `user_id` fallback을 제거하거나 explicit local profile 뒤로 제한한다. 이렇게 해야 dog owner가 `POST /api/adoption-posts`와 같은 JWT principal 기준으로 확립된다.

## Removed APIs and Concepts

old separate publisher/profile variant와 report/token extension 영역은 current MVP v2 contract에 포함하지 않는다. 아래 항목을 도입하지 않는다.

- `SHELTER` or `ADOPTER` roles
- `publisher_profiles`
- `shelter_profiles`
- `seller_profiles`
- `auth_logs`
- `reports`
- `refresh_tokens`
- Firebase, chat, push, reservation, payment, contract, or admin dashboard APIs
