# PetNose App API Handoff

이 문서는 새 앱 MVP flow에서 Flutter가 호출해야 하는 API 순서만 정리한다. 상세 error code와 전체 response shape는 `docs/PETNOSE_MVP_API_CONTRACT.md`를 기준으로 한다.

## Canonical App Flow

1. 회원가입: `POST /api/auth/register`
2. 로그인: `POST /api/auth/login`
3. 분양글 작성 버튼
4. 비문 촬영
5. 비문 검증: `POST /api/nose-verifications`
6. `allowed=false`이면 작성 차단
7. `allowed=true`이면 `nose_verification_id`를 작성 form state에 저장
8. 분양글 작성 화면에서 dog 정보, title/content, `profile_image` 입력
9. 분양글 생성: `POST /api/adoption-posts`
10. 공개 분양글 생성 완료
11. 인도 시점 비문 확인: `POST /api/adoption-posts/{post_id}/handover-verifications`
12. `matched=true`이면 완료 버튼 활성화
13. 완료 처리: `PATCH /api/adoption-posts/{post_id}/status` with `COMPLETED`

## Nose Verification

```http
POST /api/nose-verifications
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

Form-data:

- `nose_image`: required

App-facing response fields:

```json
{
  "nose_verification_id": 1001,
  "allowed": true,
  "decision": "PASSED",
  "message": "비문 인증을 통과했습니다. 분양글 작성을 진행할 수 있습니다."
}
```

Duplicate suspected:

```json
{
  "nose_verification_id": 1002,
  "allowed": false,
  "decision": "DUPLICATE_SUSPECTED",
  "message": "기존 등록견과 동일 개체로 의심되어 분양글 작성이 제한됩니다."
}
```

Notes:

- 이 단계는 dog 또는 adoption post를 생성하지 않는다.
- `profile_image`는 받지 않는다.
- 앱은 `allowed=false`이면 분양글 작성을 막는다.
- 응답은 내부 검색 payload나 raw vector를 노출하지 않는다.

## Adoption Post Create

```http
POST /api/adoption-posts
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

Form-data:

- `nose_verification_id`: required
- `dog_name`: required
- `breed`: required
- `gender`: required, `MALE`, `FEMALE`, or `UNKNOWN`
- `birth_date`: optional, `YYYY-MM-DD`
- `dog_description`: optional
- `title`: required
- `content`: required
- `status`: optional, `DRAFT` or `OPEN`
- `profile_image`: required

Notes:

- request에 `dog_id`를 보내지 않는다.
- request에 `nose_image`를 보내지 않는다.
- `profile_image`는 공개 분양글 목록/상세의 대표 이미지다.
- `profile_image` 누락 시 `PROFILE_IMAGE_REQUIRED`를 반환한다.

## Deprecated Compatibility

`POST /api/dogs/register`는 backend compatibility endpoint로만 유지한다.

- 새 앱 MVP flow에서는 사용하지 않는다.
- 새 앱 MVP flow에서는 `profile_image`를 이 endpoint로 보내지 않는다.
- `profile_image`는 `POST /api/adoption-posts`에서 required로 등록한다.
- compatibility response의 `profile_image_url`은 `null`일 수 있다.

## Auth Phone Format

`contact_phone`은 하이픈 없이 `01012341234` 형식으로 보낸다.
