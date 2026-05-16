# MVP 백엔드 흐름 Flutter 인수인계

## 1. 문서 성격

이 문서는 최신 `develop` 기준 PetNose 백엔드 MVP 흐름을 Flutter 팀원과 졸업작품 검토자가 빠르게 이어받을 수 있도록 정리한 인수인계 문서다.

- 구현 변경 없이 현재 문서, 코드, 테스트 정합성을 확인한 결과를 기록한다.
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
- `POST /api/dogs/register`
- `GET /api/dogs/me`
- `GET /api/dogs/{dog_id}`
- `POST /api/adoption-posts`
- `GET /api/adoption-posts`
- `GET /api/adoption-posts/{post_id}`
- `GET /api/adoption-posts/me`
- `PATCH /api/adoption-posts/{post_id}/status`
- `POST /api/adoption-posts/{post_id}/handover-verifications`

### active canonical 범위

- active table set은 `users`, `dogs`, `dog_images`, `verification_logs`, `adoption_posts`다.
- active role은 `USER`, `ADMIN`뿐이다.
- MySQL이 account, dog, image metadata, verification history, adoption post의 source of truth다.
- Qdrant는 dog nose embedding vector index 전용이다.
- Firebase는 향후 선택적 chat/push 용도일 뿐 현재 백엔드 MVP 구현이 아니며 MySQL 대체물이 아니다.
- `dog_images.file_path`는 upload root 기준 상대 경로만 저장한다.
- `qdrant_point_id`, `verification_status`, `embedding_status`는 DB column이 아니라 API 계산 필드다.
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
- 도입하지 않는 table/API: `publisher_profiles`, `shelter_profiles`, `seller_profiles`, `auth_logs`, `reports`, `refresh_tokens`, `handover_verifications`
- Firebase chat/push backend 구현
- reservation, payment, contract, report/admin API

## 3. 전체 사용자 흐름

1. 회원가입: Flutter는 `POST /api/auth/register`로 public signup을 수행한다. public signup은 항상 `USER` 계정을 만든다.
2. 로그인: `POST /api/auth/login`으로 `access_token`, `token_type=Bearer`, `expires_in`, current user payload를 받는다.
3. 내 정보 조회: 로그인 후 `GET /api/users/me`로 current user와 profile readiness를 확인한다.
4. 작성자 표시 정보 수정: 분양글 생성 전에 `PATCH /api/users/me/profile`로 `display_name`을 준비한다. `contact_phone`, `region`은 선택값이다.
5. 강아지 비문 등록: `POST /api/dogs/register`에 Bearer JWT와 multipart `nose_image`를 보낸다. owner는 request `user_id`가 아니라 JWT principal에서 결정된다.
6. `registration_allowed=false` 중복 의심 분기: HTTP `200`과 함께 중복 의심 결과가 온다. Flutter는 중복 의심 화면으로 분기하고 해당 dog로 분양글 생성을 막는다.
7. `registration_allowed=true` 정상 등록 분기: HTTP `201`과 함께 `dog_id`, `qdrant_point_id`, verification/embedding 상태를 받는다. 이후 이 `dog_id`를 dog query와 adoption post 생성에 사용한다.
8. 내 강아지 목록/상세 조회: `GET /api/dogs/me`와 authenticated `GET /api/dogs/{dog_id}`로 owner dog 상태, `can_create_post`, active post 여부를 확인한다.
9. 분양글 생성: `POST /api/adoption-posts`는 current user가 소유한 `REGISTERED` dog이고 최신 verification이 `PASSED`이며 active post가 없고 user `display_name`이 유효할 때만 성공한다.
10. 공개 분양글 목록/상세 조회: public 사용자는 `GET /api/adoption-posts`, `GET /api/adoption-posts/{post_id}`로 비문 이미지 없이 공개 정보를 본다.
11. 내 분양글 관리: 로그인한 작성자는 `GET /api/adoption-posts/me`로 자신이 작성한 모든 상태의 post를 관리한다.
12. 상태 변경: 작성자는 `PATCH /api/adoption-posts/{post_id}/status`로 허용된 transition만 수행한다.
13. 인도 시점 비문 확인: authenticated user는 `POST /api/adoption-posts/{post_id}/handover-verifications`로 새 촬영 비문과 post의 expected dog를 stateless 비교한다.
14. 완료 처리: handover verification은 safety signal일 뿐 자동 완료가 아니다. 실제 완료는 post owner가 status를 `COMPLETED`로 변경하는 기존 status update flow로 처리한다.

## 4. Endpoint flow table

| Endpoint | Auth | Content type | 주요 request field | 주요 response field | 주요 error code | Flutter 화면/목적 |
|---|---|---|---|---|---|---|
| `POST /api/auth/register` | 불필요 | `application/json` | `email`, `password`, optional `display_name`, `contact_phone`, `region` | `user_id`, `email`, `role`, `display_name`, `contact_phone`, `region`, `is_active` | `VALIDATION_FAILED`, `EMAIL_ALREADY_EXISTS` | 회원가입 |
| `POST /api/auth/login` | 불필요 | `application/json` | `email`, `password` | `access_token`, `token_type`, `expires_in`, `user` | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `USER_INACTIVE` | 로그인, token 획득 |
| `GET /api/users/me` | 필요 | 없음 | header `Authorization: Bearer <JWT>` | `user_id`, `email`, `role`, `display_name`, `contact_phone`, `region`, `is_active` | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE` | 앱 시작 후 current user/profile readiness 확인 |
| `PATCH /api/users/me/profile` | 필요 | `application/json` | optional-present `display_name`, `contact_phone`, `region` 중 1개 이상 | `user_id`, `display_name`, `contact_phone`, `region` | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `VALIDATION_FAILED` | 작성자 표시 정보 보완 |
| `POST /api/dogs/register` | 필요 | `multipart/form-data` | `name`, `breed`, `gender`, optional `birth_date`, `description`, required `nose_image`, optional `profile_image` | `dog_id`, `registration_allowed`, `status`, `verification_status`, `embedding_status`, `qdrant_point_id`, `model`, `dimension`, `max_similarity_score`, `nose_image_url`, `profile_image_url`, `top_match`, `message` | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `NAME_REQUIRED`, `BREED_REQUIRED`, `NOSE_IMAGE_REQUIRED`, `VALIDATION_FAILED`, `INVALID_BIRTH_DATE`, `INVALID_NOSE_IMAGE`, `EMBED_SERVICE_UNAVAILABLE`, `EMPTY_EMBEDDING`, `EMBEDDING_DIMENSION_MISMATCH`, `QDRANT_SEARCH_FAILED`, `QDRANT_UPSERT_FAILED` | 강아지 비문 등록, 중복 의심 분기 |
| `GET /api/dogs/me` | 필요 | 없음 | query `page`, `size` | `items[].dog_id`, `name`, `breed`, `gender`, `status`, `verification_status`, `embedding_status`, `profile_image_url`, `has_active_post`, `active_post_id`, `can_create_post`, `created_at`, page envelope | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `INVALID_PAGE_REQUEST` | 내 강아지 목록, post 생성 가능 여부 |
| `GET /api/dogs/{dog_id}` | 선택 | 없음 | path `dog_id`, optional Bearer JWT | owner detail은 `nose_image_url` 포함 가능, public detail은 `profile_image_url`, `can_create_post=false`, `active_post_id` 포함 | `DOG_NOT_FOUND`, `DOG_NOT_ACCESSIBLE`, `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE` | 내 강아지 상세 또는 공개 강아지 상세 |
| `POST /api/adoption-posts` | 필요 | `application/json` | `dog_id`, `title`, `content`, optional `status` (`DRAFT`/`OPEN`) | `post_id`, `dog_id`, `title`, `content`, `status`, `published_at`, `created_at` | `UNAUTHORIZED`, `USER_INACTIVE`, `USER_PROFILE_REQUIRED`, `DOG_NOT_FOUND`, `DOG_OWNER_MISMATCH`, `DOG_NOT_VERIFIED`, `DUPLICATE_DOG_CANNOT_BE_POSTED`, `ACTIVE_POST_ALREADY_EXISTS`, `INVALID_POST_STATUS`, `VALIDATION_FAILED` | 분양글 생성 |
| `GET /api/adoption-posts` | 불필요 | 없음 | query optional `status=OPEN/RESERVED/COMPLETED`, `page`, `size` | `items[].post_id`, `dog_id`, `title`, `status`, dog summary, `profile_image_url`, `verification_status`, `author_display_name`, `author_region`, timestamps, page envelope | `INVALID_POST_STATUS`, `INVALID_PAGE_REQUEST` | 공개 분양글 피드 |
| `GET /api/adoption-posts/{post_id}` | 불필요 | 없음 | path `post_id` | `post_id`, `dog_id`, `title`, `content`, `status`, dog summary, `profile_image_url`, `verification_status`, `author_display_name`, `author_contact_phone`, `author_region`, timestamps | `POST_NOT_FOUND`, `POST_NOT_PUBLIC` | 공개 분양글 상세 |
| `GET /api/adoption-posts/me` | 필요 | 없음 | query optional owner `status=DRAFT/OPEN/RESERVED/COMPLETED/CLOSED`, `page`, `size` | `items[].post_id`, `dog_id`, `title`, `status`, dog summary, `profile_image_url`, `verification_status`, `published_at`, `closed_at`, timestamps, page envelope | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `INVALID_POST_STATUS`, `INVALID_PAGE_REQUEST` | 내 분양글 관리 |
| `PATCH /api/adoption-posts/{post_id}/status` | 필요 | `application/json` | path `post_id`, body `status` | `post_id`, `dog_id`, `title`, `content`, `status`, `published_at`, `closed_at`, timestamps | `UNAUTHORIZED`, `POST_NOT_FOUND`, `POST_OWNER_MISMATCH`, `USER_PROFILE_REQUIRED`, `DOG_NOT_FOUND`, `DOG_OWNER_MISMATCH`, `DOG_NOT_VERIFIED`, `DUPLICATE_DOG_CANNOT_BE_POSTED`, `ACTIVE_POST_ALREADY_EXISTS`, `INVALID_POST_STATUS`, `INVALID_STATUS_TRANSITION` | 작성자 상태 변경, 완료 처리 |
| `POST /api/adoption-posts/{post_id}/handover-verifications` | 필요 | `multipart/form-data` | path `post_id`, required `nose_image` | `post_id`, `expected_dog_id`, `matched`, `decision`, `similarity_score`, `threshold`, `ambiguous_threshold`, `top_match_is_expected`, `model`, `dimension`, `message` | `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_INACTIVE`, `POST_NOT_FOUND`, `POST_NOT_VERIFIABLE`, `DOG_NOT_FOUND`, `DOG_NOT_VERIFIED`, `NOSE_IMAGE_REQUIRED`, `INVALID_NOSE_IMAGE`, `EMBED_SERVICE_UNAVAILABLE`, `EMPTY_EMBEDDING`, `EMBEDDING_DIMENSION_MISMATCH`, `QDRANT_SEARCH_FAILED` | 인도 시점 비문 확인 |

## 5. Flutter state decisions

- 로그인 token 저장: `POST /api/auth/login` 응답의 `access_token`을 안전 저장소에 저장하고, `token_type`은 `Bearer`로 표시한다. 만료 시간은 `expires_in` 기준으로 다룬다.
- 인증 header: 인증 endpoint에는 `Authorization: Bearer <access_token>`을 붙인다. 특히 dog registration, dog owner query, user profile, owner post, handover verification은 token 없으면 실패한다.
- profile readiness: 분양글 생성 또는 `DRAFT` -> `OPEN` 전에는 `GET /api/users/me`의 `display_name`이 null/blank가 아닌지 확인한다. 부족하면 profile edit 화면으로 보낸다.
- `display_name`: profile update에서 trim 후 2자 이상 10자 이하, 한글 완성형/영문/숫자만 허용한다. 공백, 특수문자, emoji는 거부된다.
- `contact_phone`: profile update에서 trim 후 하이픈 없는 숫자 11자리만 허용한다.
- `region`: profile update에서 trim 후 blank는 거부되고 최대 100자다. backend는 district enum/list를 강제하지 않으므로 Flutter UI가 선택지를 제한한다.
- dog registration 중복 의심: Qdrant cosine score가 `0.70` 이상이면 중복 의심으로 판정한다. HTTP status가 `200`이고 `registration_allowed=false`이면 실패 toast가 아니라 중복 의심 flow다. `top_match`는 `dog_id`, `similarity_score`, `breed`만 표시 가능한 참고 정보다.
- 정상 등록: HTTP status가 `201`이고 `registration_allowed=true`이면 `dog_id`를 저장하거나 즉시 dog query를 다시 호출한다.
- `can_create_post`: Flutter는 `GET /api/dogs/me` 또는 owner detail의 `can_create_post`를 post 생성 버튼 활성화에 사용한다. 단, 최종 권한은 backend service가 다시 검증한다.
- 공개 목록/상세 privacy: public adoption post list/detail과 public dog detail에는 `nose_image_url`이 오지 않는 것이 정상이다. UI는 `profile_image_url` 중심으로 렌더링한다.
- handover decision 렌더링:
  - `MATCHED`: expected dog와 일치. 거래 전 확인 성공 signal로 표시한다.
  - `AMBIGUOUS`: 기준 근처지만 확정 불가. 재촬영을 안내한다.
  - `NOT_MATCHED`: 다른 dog가 top result이거나 expected dog score가 낮다. 거래 전 확인 필요 경고로 표시한다.
  - `NO_MATCH_CANDIDATE`: Qdrant 후보가 없다. 재촬영 또는 현장 확인 안내로 표시한다.
- dog registration duplicate detection과 handover verification은 current MVP에서 모두 `0.70`을 same-dog threshold로 사용한다. handover는 top match가 expected dog이고 score가 `0.70` 이상이면 `MATCHED`, top match가 expected dog가 아니거나 score가 `0.70` 미만이면 `NOT_MATCHED`, Qdrant 후보가 없으면 `NO_MATCH_CANDIDATE`다. 기본 `0.70 / 0.70` 정책에서는 `AMBIGUOUS`가 일반 runtime에서 기대되지 않으며, `top_match_is_expected=false`인 `NOT_MATCHED`는 UI에서 신중하게 표시한다.
- 완료 처리: handover `MATCHED`가 와도 backend는 자동 완료하지 않는다. 완료 버튼은 owner-only `PATCH /api/adoption-posts/{post_id}/status` with `COMPLETED` flow로 분리한다.

## 6. Privacy / exposure rules

- public adoption post list/detail은 `nose_image_url`을 노출하지 않는다.
- dog list인 `GET /api/dogs/me`는 `nose_image_url`을 노출하지 않는다.
- dog owner detail은 owner 자신의 dog에 한해 `nose_image_url`을 노출할 수 있다.
- dog public detail은 `nose_image_url`을 노출하지 않는다.
- handover verification response는 다른 dog의 `dog_id`, `top_matched_dog_id`, Qdrant payload details, `author_user_id`, `nose_image_url`을 노출하지 않는다.
- duplicate suspected registration의 `top_match`는 raw `nose_image_url`을 노출하지 않는다.
- public UI에서 비문 이미지를 기대하면 안 된다. 비문 이미지는 owner-scoped 등록/상세 또는 내부 verification pipeline의 대상이다.

## 7. Firebase/chat boundary for teammate

이 절은 chat 구현 설계가 아니라 경계선이다.

- Firebase/chat/push는 현재 백엔드 MVP 구현 범위가 아니다.
- Firebase는 MySQL source of truth를 대체하면 안 된다.
- Firebase는 `ADOPTER` 또는 `SHELTER` role을 도입하면 안 된다.
- Firebase 작업은 `users`, `dogs`, `adoption_posts` canonical table을 명시적 schema 결정 없이 바꾸면 안 된다.
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

- `adoption_posts.title`/`content` runtime Flyway constraint를 service policy와 DB level까지 맞출지 결정한다.
- markdown lint 또는 docs CI가 필요하면 별도 docs tooling으로 추가한다.

### Production hardening only

- active post uniqueness는 현재 service-level 정책이다. 동시성까지 강하게 보장하려면 DB/index/locking 전략을 별도 설계한다.
- Firebase chat design doc은 별도 branch에서 작성한다.
- 운영 push, 알림, moderation, report/admin 확장은 current MVP graduation flow 이후 별도 승인으로 다룬다.

## 9. Verification commands

이 branch에서 backend directory 기준으로 실행한 test command는 아래와 같다.

```powershell
gradle test --tests "*Auth*" --tests "*User*" --no-daemon
gradle test --tests "*DogRegistration*" --tests "*DogRegisterAuthPrincipal*" --no-daemon
gradle test --tests "*DogQuery*" --no-daemon
gradle test --tests "*AdoptionPost*" --no-daemon
gradle test --tests "*HandoverVerification*" --no-daemon
gradle clean test --no-daemon
```

문서 validation 단계에서 repository root 기준으로 실행할 command는 아래와 같다.

```powershell
git diff --name-only
git diff --stat
git diff --check
```

테스트 결과는 모두 `BUILD SUCCESSFUL`이었다. `git diff --check`도 출력 없이 통과했다.
