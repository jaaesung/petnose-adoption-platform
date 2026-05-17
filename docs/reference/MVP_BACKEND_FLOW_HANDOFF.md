# MVP 백엔드 흐름 Flutter 인수인계

## 1. 문서 성격

이 문서는 최신 `develop` 기준 PetNose 백엔드 MVP 흐름을 Flutter 팀원과 졸업작품 검토자가 빠르게 이어받을 수 있도록 정리한 인수인계 문서다.

- 현재 문서, 코드, DB migration, 테스트 정합성을 기준으로 앱 연동에 필요한 흐름만 기록한다.
- Firebase/chat 구현 문서가 아니다.
- Flutter 연동, 시연 준비, API 호출 순서 확인용 문서다.
- active canonical 기준은 `docs/README.md`, `docs/PROJECT_KNOWLEDGE_INDEX.md`, `docs/PETNOSE_MVP_API_CONTRACT.md`, `docs/PETNOSE_MVP_FINAL_PROJECT_SPEC.md`를 우선한다.
- `docs/archive/**`는 active 구현 기준으로 사용하지 않는다.

## 2. 현재 MVP 백엔드 상태 요약

### 구현된 endpoint

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me`
- `PATCH /api/users/me/profile`
- `POST /api/nose-verifications`
- `POST /api/dogs/register`
- `GET /api/dogs/me`
- `GET /api/dogs/{dog_id}`
- `POST /api/adoption-posts`
- `GET /api/adoption-posts`
- `GET /api/adoption-posts/{post_id}`
- `GET /api/adoption-posts/me`
- `PATCH /api/adoption-posts/{post_id}/status`
- `POST /api/adoption-posts/{post_id}/handover-verifications`

### 앱 연동 경계

- Flutter는 Spring Boot HTTP API만 호출한다.
- active role은 `USER`, `ADMIN`뿐이다. public signup은 `USER`를 만든다.
- Firebase는 향후 선택적 chat/push 용도일 뿐 현재 백엔드 MVP 구현이 아니다.
- 업로드 파일은 backend가 저장하고, 앱은 response의 `/files/...` URL만 사용한다.
- `nose_verification_id`, `verification_status`, `embedding_status`, `can_create_post`는 앱 화면 분기에 사용할 수 있는 API field다.
- JSON response field는 `snake_case`를 유지한다.
- 공통 error response shape는 아래 형식이다.

```json
{
  "error_code": "...",
  "message": "...",
  "details": ...
}
```

### active scope가 아닌 항목

아래 항목은 current MVP active scope로 도입하지 않는다.

- 도입하지 않는 role: `SHELTER`, `ADOPTER`
- 도입하지 않는 기능: 보호소/입양자 전용 role, 별도 프로필 확장, 신고/관리자 API, refresh token, 인도 확인 이력 API
- Firebase chat/push backend 구현
- reservation, payment, contract, report/admin API

## 3. 전체 사용자 흐름

1. 회원가입: Flutter는 `POST /api/auth/register`로 public signup을 수행한다. public signup은 항상 `USER` 계정을 만든다.
2. 로그인: `POST /api/auth/login`으로 `access_token`, `token_type=Bearer`, `expires_in`, current user payload를 받는다.
3. 내 정보 조회: 로그인 후 `GET /api/users/me`로 current user와 profile readiness를 확인한다.
4. 작성자 표시 정보 확인/수정: signup에서 `display_name`, `contact_phone`, `region`은 필수지만, 변경이 필요하면 `PATCH /api/users/me/profile`을 사용한다.
5. 분양 전 비문 검증: `POST /api/nose-verifications`에 Bearer JWT와 multipart `nose_image`만 보낸다. 이 단계는 dog나 adoption post를 만들지 않는다.
6. `allowed=false` 중복 의심 분기: HTTP `200`과 함께 중복 의심 결과가 온다. Flutter는 중복 의심 화면으로 분기하고 분양글 생성을 막는다.
7. `allowed=true` 정상 검증 분기: HTTP `201`과 함께 `nose_verification_id`, 만료 시각을 받는다. 이후 분양글 생성에는 `nose_verification_id`를 사용한다.
8. 분양글 생성: `POST /api/adoption-posts`는 multipart로 `nose_verification_id`, `dog_name`, `breed`, `gender`, `title`, `content`, required `profile_image`, optional `birth_date`, `dog_description`, `status`를 보낸다. 성공 시 해당 검증은 재사용할 수 없다.
9. 내 강아지 목록/상세 조회: `GET /api/dogs/me`와 authenticated `GET /api/dogs/{dog_id}`로 owner dog 상태, `can_create_post`, active post 여부를 확인한다.
10. 공개 분양글 목록/상세 조회: public 사용자는 `GET /api/adoption-posts`, `GET /api/adoption-posts/{post_id}`로 비문 이미지 없이 공개 정보를 본다.
11. 내 분양글 관리: 로그인한 작성자는 `GET /api/adoption-posts/me`로 자신이 작성한 모든 상태의 post를 관리한다.
12. 상태 변경: 작성자는 `PATCH /api/adoption-posts/{post_id}/status`로 허용된 transition만 수행한다.
13. 인도 시점 비문 확인: authenticated user는 `POST /api/adoption-posts/{post_id}/handover-verifications`로 새 촬영 비문과 post의 expected dog를 stateless 비교한다.
14. 완료 처리: handover verification은 safety signal일 뿐 자동 완료가 아니다. 실제 완료는 post owner가 status를 `COMPLETED`로 변경하는 기존 status update flow로 처리한다.

`POST /api/dogs/register`는 기존 앱/테스트 호환을 위한 deprecated compatibility endpoint로 남아 있다. 신규 Flutter 작성 flow는 `POST /api/nose-verifications` -> `POST /api/adoption-posts` 순서를 사용한다.

## 4. Endpoint flow table

| Endpoint | Auth | Content type | 주요 request field | 주요 response field | 주요 error code | Flutter 화면/목적 |
|---|---|---|---|---|---|---|
| `POST /api/auth/register` | 불필요 | `application/json` | `email`, `password`, `display_name`, `contact_phone`, `region` | `user_id`, `email`, `role`, `display_name`, `contact_phone`, `region`, `is_active` | `VALIDATION_FAILED`, `EMAIL_ALREADY_EXISTS` | 회원가입 |
| `POST /api/auth/login` | 불필요 | `application/json` | `email`, `password` | `access_token`, `token_type`, `expires_in`, `user` | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `USER_INACTIVE` | 로그인, token 획득 |
| `GET /api/users/me` | 필요 | 없음 | header `Authorization: Bearer <JWT>` | `user_id`, `email`, `role`, `display_name`, `contact_phone`, `region`, `is_active` | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE` | 앱 시작 후 current user/profile readiness 확인 |
| `PATCH /api/users/me/profile` | 필요 | `application/json` | optional-present `display_name`, `contact_phone`, `region` 중 1개 이상 | `user_id`, `display_name`, `contact_phone`, `region` | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `VALIDATION_FAILED` | 작성자 표시 정보 보완 |
| `POST /api/nose-verifications` | 필요 | `multipart/form-data` | required `nose_image` | `nose_verification_id`, `allowed`, `decision`, `message` plus compatibility fields | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `NOSE_IMAGE_REQUIRED`, `INVALID_NOSE_IMAGE`, verification service error | 분양글 작성 전 비문 검증 |
| `POST /api/dogs/register` | 필요 | `multipart/form-data` | `name`, `breed`, `gender`, optional `birth_date`, `description`, required `nose_image` | `dog_id`, `registration_allowed`, `status`, `verification_status`, `embedding_status`, `profile_image_url=null`, `top_match`, `message` | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `NAME_REQUIRED`, `BREED_REQUIRED`, `NOSE_IMAGE_REQUIRED`, `VALIDATION_FAILED`, `INVALID_BIRTH_DATE`, `INVALID_NOSE_IMAGE`, verification service error | deprecated compatibility 등록 |
| `GET /api/dogs/me` | 필요 | 없음 | query `page`, `size` | `items[].dog_id`, `name`, `breed`, `gender`, `status`, `verification_status`, `embedding_status`, `profile_image_url`, `has_active_post`, `active_post_id`, `can_create_post`, `created_at`, page envelope | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `INVALID_PAGE_REQUEST` | 내 강아지 목록, post 생성 가능 여부 |
| `GET /api/dogs/{dog_id}` | 선택 | 없음 | path `dog_id`, optional Bearer JWT | owner detail은 `nose_image_url` 포함 가능, public detail은 `profile_image_url`, `can_create_post=false`, `active_post_id` 포함 | `DOG_NOT_FOUND`, `DOG_NOT_ACCESSIBLE`, `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE` | 내 강아지 상세 또는 공개 강아지 상세 |
| `POST /api/adoption-posts` | 필요 | `multipart/form-data` | `nose_verification_id`, `dog_name`, `breed`, `gender`, `title`, `content`, required `profile_image`, optional `birth_date`, `dog_description`, `status` (`DRAFT`/`OPEN`) | `post_id`, `dog_id`, `title`, `content`, `status`, `published_at`, `created_at` | `UNAUTHORIZED`, `USER_INACTIVE`, `USER_PROFILE_REQUIRED`, `PROFILE_IMAGE_REQUIRED`, `NOSE_VERIFICATION_NOT_FOUND`, `NOSE_VERIFICATION_OWNER_MISMATCH`, `NOSE_VERIFICATION_ALREADY_CONSUMED`, `NOSE_VERIFICATION_EXPIRED`, `DOG_NOT_VERIFIED`, `DUPLICATE_DOG_CANNOT_BE_POSTED`, `INVALID_POST_STATUS`, `VALIDATION_FAILED` | 분양글 생성 |
| `GET /api/adoption-posts` | 불필요 | 없음 | query optional `status=OPEN/RESERVED/COMPLETED`, `page`, `size` | `items[].post_id`, `dog_id`, `title`, `status`, dog summary, `profile_image_url`, `verification_status`, `author_display_name`, `author_region`, timestamps, page envelope | `INVALID_POST_STATUS`, `INVALID_PAGE_REQUEST` | 공개 분양글 피드 |
| `GET /api/adoption-posts/{post_id}` | 불필요 | 없음 | path `post_id` | `post_id`, `dog_id`, `title`, `content`, `status`, dog summary, `profile_image_url`, `verification_status`, `author_display_name`, `author_contact_phone`, `author_region`, timestamps | `POST_NOT_FOUND`, `POST_NOT_PUBLIC` | 공개 분양글 상세 |
| `GET /api/adoption-posts/me` | 필요 | 없음 | query optional owner `status=DRAFT/OPEN/RESERVED/COMPLETED/CLOSED`, `page`, `size` | `items[].post_id`, `dog_id`, `title`, `status`, dog summary, `profile_image_url`, `verification_status`, `published_at`, `closed_at`, timestamps, page envelope | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `INVALID_POST_STATUS`, `INVALID_PAGE_REQUEST` | 내 분양글 관리 |
| `PATCH /api/adoption-posts/{post_id}/status` | 필요 | `application/json` | path `post_id`, body `status` | `post_id`, `dog_id`, `title`, `content`, `status`, `published_at`, `closed_at`, timestamps | `UNAUTHORIZED`, `POST_NOT_FOUND`, `POST_OWNER_MISMATCH`, `USER_PROFILE_REQUIRED`, `DOG_NOT_FOUND`, `DOG_OWNER_MISMATCH`, `DOG_NOT_VERIFIED`, `DUPLICATE_DOG_CANNOT_BE_POSTED`, `ACTIVE_POST_ALREADY_EXISTS`, `INVALID_POST_STATUS`, `INVALID_STATUS_TRANSITION` | 작성자 상태 변경, 완료 처리 |
| `POST /api/adoption-posts/{post_id}/handover-verifications` | 필요 | `multipart/form-data` | path `post_id`, required `nose_image` | `post_id`, `expected_dog_id`, `matched`, `decision`, `similarity_score`, `message` | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `POST_NOT_FOUND`, `POST_NOT_VERIFIABLE`, `DOG_NOT_FOUND`, `DOG_NOT_VERIFIED`, `NOSE_IMAGE_REQUIRED`, `INVALID_NOSE_IMAGE`, verification service error | 인도 시점 비문 확인 |

## 5. Flutter state decisions

- 로그인 token 저장: `POST /api/auth/login` 응답의 `access_token`을 안전 저장소에 저장하고, `token_type`은 `Bearer`로 표시한다. 만료 시간은 `expires_in` 기준으로 다룬다.
- 인증 header: 인증 endpoint에는 `Authorization: Bearer <access_token>`을 붙인다. 특히 dog registration, dog owner query, user profile, owner post, handover verification은 token 없으면 실패한다.
- profile readiness: 분양글 생성 또는 `DRAFT` -> `OPEN` 전에는 `GET /api/users/me`의 `display_name`이 null/blank가 아닌지 확인한다. 부족하면 profile edit 화면으로 보낸다.
- `display_name`: profile update에서 trim 후 2자 이상 10자 이하, 한글 완성형/영문/숫자만 허용한다. 공백, 특수문자, emoji는 거부된다.
- `contact_phone`: signup/profile update에서 trim 후 `01012341234` 같은 `010` 시작 하이픈 없는 숫자 11자리만 허용한다.
- `region`: profile update에서 trim 후 blank는 거부되고 최대 100자다. backend는 district enum/list를 강제하지 않으므로 Flutter UI가 선택지를 제한한다.
- nose verification 중복 의심: backend가 기존 등록견과 동일 개체로 의심하면 HTTP status `200`과 `allowed=false`, `decision=DUPLICATE_SUSPECTED`를 반환한다. Flutter는 실패 toast가 아니라 중복 의심 flow로 분기한다. `top_match`는 참고 정보이며 비문 이미지를 포함하지 않는다.
- 정상 검증: HTTP status가 `201`이고 `allowed=true`, `decision=PASSED`이면 `nose_verification_id`를 다음 분양글 생성 form state에 보관한다. 이 값은 만료되거나 한 번 사용되면 다시 쓸 수 없다.
- 분양글 이미지: 대표/profile 이미지는 dog 등록 단계가 아니라 `POST /api/adoption-posts`의 required `profile_image`로 보낸다.
- `can_create_post`: Flutter는 `GET /api/dogs/me` 또는 owner detail의 `can_create_post`를 post 생성 버튼 활성화에 사용한다. 단, 최종 권한은 backend service가 다시 검증한다.
- 공개 목록/상세 privacy: public adoption post list/detail과 public dog detail에는 `nose_image_url`이 오지 않는 것이 정상이다. UI는 `profile_image_url` 중심으로 렌더링한다.
- handover decision 렌더링: Flutter는 먼저 `matched`로 분기한다. `matched=true`는 post의 expected dog와 같은 dog로 확인되었다는 뜻이고, `matched=false`는 같은 dog로 검증되지 않았다는 뜻이다. `decision`은 explanation/reason code다.
  - `MATCHED`: 같은 dog로 확인되었다.
  - `NOT_MATCHED`: 같은 dog로 확인되지 않았다.
  - `NO_MATCH_CANDIDATE`: 판단에 필요한 후보를 찾지 못했다. 재촬영 또는 현장 확인 안내로 표시한다.
- `AMBIGUOUS`는 enum compatibility로 남아 있지만 default MVP direct expected-dog handover runtime에서는 기대되지 않는다.
- handover는 post의 expected dog와 직접 비교하는 safety check다. completion은 여전히 별도 status update가 필요하다.
- 완료 처리: handover `MATCHED`가 와도 backend는 자동 완료하지 않는다. 완료 버튼은 owner-only `PATCH /api/adoption-posts/{post_id}/status` with `COMPLETED` flow로 분리한다.

## 6. Privacy / exposure rules

- public adoption post list/detail은 `nose_image_url`을 노출하지 않는다.
- dog list인 `GET /api/dogs/me`는 `nose_image_url`을 노출하지 않는다.
- dog owner detail은 owner 자신의 dog에 한해 `nose_image_url`을 노출할 수 있다.
- dog public detail은 `nose_image_url`을 노출하지 않는다.
- handover verification response는 다른 dog의 식별자, 내부 매칭 데이터, `author_user_id`, `nose_image_url`을 노출하지 않는다.
- duplicate suspected verification의 `top_match`는 raw `nose_image_url`을 노출하지 않는다.
- public UI에서 비문 이미지를 기대하면 안 된다. 비문 이미지는 owner-scoped 등록/상세 또는 내부 verification pipeline의 대상이다.

## 7. Firebase/chat boundary for teammate

이 절은 chat 구현 설계가 아니라 경계선이다.

- Firebase/chat/push는 현재 백엔드 MVP 구현 범위가 아니다.
- Firebase는 Spring API의 사용자/강아지/분양글 상태를 대체하면 안 된다.
- Firebase는 `ADOPTER` 또는 `SHELTER` role을 도입하면 안 된다.
- Firebase 작업은 기존 Spring API 계약을 명시적 결정 없이 바꾸면 안 된다.
- chat 담당자는 별도 승인 없이 reservation, payment, contract 개념을 추가하면 안 된다.
- identity mapping이 필요하면 Spring JWT의 user identity를 authority로 삼는 방향을 우선 검토한다.
- Firebase Auth/custom token이 필요하면 구현 전에 별도 design doc과 별도 branch를 만든다.
- push payload에는 민감한 개인정보와 raw message content를 가능한 넣지 않는다.
- Firestore room/message structure는 별도 문서에서 리뷰해야 한다.

## 8. Remaining follow-ups

### Must-have before Flutter demo

- Flutter에서 실제 화면 flow로 token 저장, profile readiness, dog registration 정상/중복 분기, post 생성, public list/detail, handover verification 결과 렌더링을 연결한다.
- local 또는 dev 환경에서 real-model E2E smoke evidence가 최근에 없으면 시연 전 별도 캡처한다.

### Nice-to-have

- markdown lint 또는 docs CI가 필요하면 별도 docs tooling으로 추가한다.

### Production hardening only

- active post uniqueness는 현재 service-level 정책이다. 동시성까지 강하게 보장하려면 DB/index/locking 전략을 별도 설계한다.
- Firebase chat design doc은 별도 branch에서 작성한다.
- 운영 push, 알림, moderation, report/admin 확장은 current MVP graduation flow 이후 별도 승인으로 다룬다.

## 9. Verification commands

이 branch에서 backend directory 기준으로 실행한 test command는 아래와 같다.

```powershell
gradle test --no-daemon
```

runtime migration smoke는 repository root 기준으로 임시 MySQL 8.0 DB에서 V1, V2, V3를 순서대로 적용해 확인했다. `nose_verification_attempts` table, FK/check/index, 분양글 제목/본문/상태 제약은 API contract와 일치했다.

```powershell
docker run --name petnose-migration-check-core-flow -e MYSQL_ROOT_PASSWORD=petnose_pw -e MYSQL_DATABASE=petnose -d mysql:8.0
docker cp backend\src\main\resources\db\migration\. petnose-migration-check-core-flow:/tmp/migration
docker exec petnose-migration-check-core-flow mysql -uroot -ppetnose_pw petnose -e "SOURCE /tmp/migration/V1__baseline.sql"
docker exec petnose-migration-check-core-flow mysql -uroot -ppetnose_pw petnose -e "SOURCE /tmp/migration/V2__align_adoption_post_content_constraints.sql"
docker exec petnose-migration-check-core-flow mysql -uroot -ppetnose_pw petnose -e "SOURCE /tmp/migration/V3__add_nose_verification_attempts.sql"
docker exec petnose-migration-check-core-flow mysql -uroot -ppetnose_pw petnose -e "SHOW COLUMNS FROM nose_verification_attempts; SHOW CREATE TABLE nose_verification_attempts\G; SHOW COLUMNS FROM adoption_posts;"
docker rm -f petnose-migration-check-core-flow
```

문서 validation 단계에서 repository root 기준으로 실행한 command는 아래와 같다.

```powershell
git diff --name-only
git diff --stat
git diff --check
```

테스트 결과는 `BUILD SUCCESSFUL`이었다. migration smoke도 SQL error 없이 통과했다.
