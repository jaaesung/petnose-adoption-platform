# PetNose MVP 최종 프로젝트 명세

## Canonical Baseline

이 문서는 simplified DBML v2 기준의 active PetNose MVP canonical model을 정의한다.

active domain table은 아래 5개다.

1. `users`
2. `dogs`
3. `dog_images`
4. `verification_logs`
5. `adoption_posts`

MySQL은 account, dog, image metadata, verification history, adoption post data의 source of truth다. Qdrant는 vector search index일 뿐이다. image binary는 파일로 저장하고, MySQL에는 `dog_images.file_path`에 상대 경로만 저장한다.

## MVP v2에서 제외된 범위

과거 separate profile, auth history, report, token, image quality/crop, Firebase, chat, push, reservation, payment, contract, report/admin dashboard, non-canonical role extension 영역은 current canonical MVP v2 schema 또는 API surface에 포함하지 않는다.

## Users

`users`는 account identity와 author display information을 직접 저장한다.

Columns:

- `id`
- `email`
- `password_hash`
- `role`
- `display_name`
- `contact_phone`
- `region`
- `is_active`
- `created_at`

허용 role은 `USER`와 `ADMIN`뿐이다.

`display_name`은 nullable이다. 분양글 작성 전에 비문 등록과 검증이 먼저 일어나므로 signup과 dog verification 단계에서 author display name을 요구하지 않는다. 다만 adoption post를 생성하기 전에는 service가 `users.display_name` 존재 여부를 검증해야 한다.

## Dogs

`dogs`는 기본 강아지 정보와 lifecycle/service status를 저장한다.

Columns:

- `id`
- `owner_user_id`
- `name`
- `breed`
- `gender`
- `birth_date`
- `description`
- `status`
- `created_at`
- `updated_at`

`dogs.status`는 dog lifecycle/service status다.

- `PENDING`
- `REGISTERED`
- `DUPLICATE_SUSPECTED`
- `REJECTED`
- `ADOPTED`
- `INACTIVE`

`DUPLICATE_SUSPECTED`는 adoption post creation을 막아야 하므로 `dogs.status`에 남긴다. similarity, duplicate candidate, model, dimension 같은 detailed verification information은 `verification_logs`에 속한다.

`dogs`는 `qdrant_point_id`를 저장하지 않는다. Qdrant point id는 항상 `dogs.id`와 같다.

## Dog Images

`dog_images`는 image metadata와 relative file path만 저장한다.

허용 `image_type` 값:

- `NOSE`
- `PROFILE`

Columns:

- `id`
- `dog_id`
- `image_type`
- `file_path`
- `mime_type`
- `file_size`
- `sha256`
- `uploaded_at`

Image file은 MySQL BLOB column에 저장하지 않는다.

## Verification Logs

`verification_logs`는 nose verification attempt와 result의 source of truth다.

Columns:

- `id`
- `dog_id`
- `dog_image_id`
- `requested_by_user_id`
- `result`
- `similarity_score`
- `candidate_dog_id`
- `model`
- `dimension`
- `failure_reason`
- `created_at`

허용 `result` 값:

- `PENDING`
- `PASSED`
- `DUPLICATE_SUSPECTED`
- `EMBED_FAILED`
- `QDRANT_SEARCH_FAILED`
- `QDRANT_UPSERT_FAILED`

Embedding vector 자체는 MySQL에 저장하지 않는다. vector는 Qdrant에만 저장한다.

## Adoption Posts

`adoption_posts`는 adoption listing content와 publishing state를 저장한다.

Columns:

- `id`
- `author_user_id`
- `dog_id`
- `title`
- `content`
- `status`
- `published_at`
- `closed_at`
- `created_at`
- `updated_at`

허용 `status` 값:

- `DRAFT`
- `OPEN`
- `RESERVED`
- `COMPLETED`
- `CLOSED`

Adoption post를 생성하거나 `OPEN`으로 전환하기 전에는 유효한 author `display_name`과 post 작성이 가능한 dog가 필요하다. `DUPLICATE_SUSPECTED`, `REJECTED`, `INACTIVE` 상태의 dog는 post 작성 대상이 될 수 없다.

## 인도 시점 비문 확인(Handover-Time Dog Nose Verification)

인도 시점 비문 확인은 adoption post publication과 status management 이후의 MVP trust/safety flow에 포함된다.

이 기능은 실제 인도 장소에서 만난 강아지가 adoption post에 등록된 강아지와 같은 개체인지 확인하는 질문에 답한다.

handover verification API는 dog registration과 의도적으로 다르다.

- `POST /api/dogs/register`는 dog를 등록하고 adoption post creation 전에 duplicate suspicion detection을 수행한다.
- `POST /api/adoption-posts/{post_id}/handover-verifications`는 dog를 등록하지 않는다.
- 이 API는 새로 촬영한 nose image를 기존 adoption post에 연결된 dog와 비교한다.
- expected dog는 `adoption_posts.dog_id`다.
- Spring Boot가 Python Embed와 Qdrant 호출을 오케스트레이션한다. Flutter는 Python Embed, Qdrant, MySQL을 직접 호출하지 않는다.

MVP handover verification check는 stateless다.

- handover image를 저장하지 않는다.
- dog를 생성하지 않는다.
- `dog_images` row를 생성하지 않는다.
- current MVP implementation에서는 `verification_logs` row를 생성하지 않는다.
- `adoption_posts.status`를 변경하지 않는다.
- `dogs.status`를 변경하지 않는다.
- 자동으로 adoption completion을 수행하지 않는다.
- adoption completion은 별도의 owner-only status update action으로 남는다.

이 기능은 reservation, payment, contract, Firebase, chat, push, report/admin, `SHELTER`, `ADOPTER` concept을 추가하지 않는다.

## Dog Registration Pipeline Policy

`POST /api/dogs/register`는 일부 response field가 이제 `dogs`에 저장되지 않고 계산되더라도 기존 response contract를 유지한다.

계산되는 response field:

- `qdrant_point_id`
  - normal registration: `dog_id`
  - duplicate suspected: `null`
- `verification_status`
  - latest `verification_logs.result`에서 계산
- `embedding_status`
  - latest `verification_logs.result`에서 계산

Normal registration은 Qdrant에 point id `dogs.id`로 vector를 저장한다. Duplicate suspected registration은 새 dog에 대한 active Qdrant point를 upsert하지 않아야 한다.

## 최종 MVP 사용자 흐름

1. `USER`가 `POST /api/dogs/register`로 nose image와 함께 dog를 등록한다.
2. `registration_allowed=false`인 duplicate suspicion은 adoption post creation을 막는다.
3. 검증된 dog는 adoption post 생성에 사용할 수 있다.
4. public user는 nose image 노출 없이 adoption post를 볼 수 있다.
5. 인도 시점에는 authenticated user가 새로 촬영한 nose image를 업로드해 post의 expected dog와 stateless verification을 수행할 수 있다.
6. matched handover verification result는 safety signal일 뿐이다.
7. completion은 기존 owner-only post status update flow로 처리한다.
