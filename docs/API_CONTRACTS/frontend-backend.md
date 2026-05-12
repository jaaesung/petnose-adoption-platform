# API 계약 — Flutter ↔ Spring Boot

> 이 문서는 초기 API 계약 초안입니다. 실제 구현이 확정되면 이 문서를 갱신하거나 Swagger/OpenAPI 문서로 대체합니다.

## MVP Canonical Baseline (2026-04)

- 사용자 PK(`users.id`)는 **BIGINT** 기준입니다. API 예시의 `user_id`도 숫자형으로 표기합니다.
- 강아지 PK(`dogs.id`)는 **UUID 문자열** 기준입니다.
- 보호소/분양자 프로필 테이블 명칭은 **`shelter_profiles`** 로 통일합니다.
- Spring ↔ Python 임베딩 응답 필드는 **`dimension`** 을 사용합니다(`dim` 사용 금지).
- TODO: 사용자 외부 노출 ID를 UUID로 분리할지(`public_id`)는 추후 도메인 설계 단계에서 결정합니다.

Base URL: `http://<host>/api`  
인증: `Authorization: Bearer <JWT>` 헤더

---

## 1. 인증

### 회원가입

```
POST /api/auth/register
Content-Type: application/json

Request:
{
  "email": "user@example.com",
  "password": "plaintext (TLS 필수)",
  "role": "ADOPTER"  // ADOPTER | SHELTER
}

Response 201:
{
  "user_id": 101,
  "email": "user@example.com",
  "role": "ADOPTER"
}

Response 409: 이미 존재하는 이메일
```

### 로그인

```
POST /api/auth/login
Content-Type: application/json

Request:
{
  "email": "user@example.com",
  "password": "plaintext"
}

Response 200:
{
  "access_token": "JWT",
  "token_type": "Bearer",
  "expires_in": 3600
}

Response 401: 인증 실패
```

---

## 2. 강아지 등록

### 강아지 기본 정보 + 비문 이미지 등록

```
POST /api/dogs/register
Content-Type: multipart/form-data
Authorization: Bearer <JWT>  // TODO: 현재 MVP에서는 user_id multipart field 임시 사용

Form fields:
  user_id: number (임시, 추후 JWT principal로 전환)
  name: string
  breed: string
  gender: string  // MALE | FEMALE | UNKNOWN
  birth_date: string (YYYY-MM-DD, optional)
  description: string (optional)
  nose_image: file (JPEG/PNG, required)
  profile_image: file (JPEG/PNG, optional)

Response 201:
{
  "dog_id": "uuid",
  "registration_allowed": true,
  "status": "REGISTERED",
  "verification_status": "VERIFIED",
  "embedding_status": "COMPLETED",
  "qdrant_point_id": "uuid",
  "model": "mock-v1",
  "dimension": 128,
  "max_similarity_score": 0.41,
  "nose_image_url": "/files/dogs/{uuid}/nose/20260508_010203_nose.jpg",
  "message": "중복 의심 개체가 없어 등록이 완료되었습니다."
}

Response 200 (중복 의심):
{
  "dog_id": "new-dog-id",
  "registration_allowed": false,
  "status": "DUPLICATE_SUSPECTED",
  "verification_status": "DUPLICATE_SUSPECTED",
  "embedding_status": "SKIPPED_DUPLICATE",
  "model": "mock-v1",
  "dimension": 128,
  "max_similarity_score": 0.99782,
  "top_match": {
    "dog_id": "existing-dog-id",
    "similarity_score": 0.99782,
    "breed": "말티즈",
    "nose_image_url": "/files/dogs/existing-dog-id/nose/..."
  },
  "message": "기존 등록견과 동일 개체로 의심되어 등록이 제한됩니다."
}

Response 400: 입력값 오류
Response 404: user_id 미존재
Response 422: 이미지/임베딩 입력 오류 (Python 400)
Response 503: 임베딩 서비스/Qdrant 연동 실패
```

---

## 3. 비문 인증 (동일 개체 확인)

```
POST /api/dogs/verify
Content-Type: multipart/form-data
Authorization: Bearer <JWT>

Form fields:
  nose_image: file

Response 200:
{
  "matched": true,
  "similarity_score": 0.94,
  "dog": {
    "dog_id": "uuid",
    "name": "초코",
    "breed": "말티즈",
    "owner": {
      "user_id": 101,
      "email": "owner@example.com"
    }
  }
}

Response 200 (매칭 없음):
{
  "matched": false,
  "similarity_score": null,
  "dog": null
}

Response 422: 이미지 처리 실패
```

---

## 4. 입양 게시글

### 목록 조회

```
GET /api/posts?page=0&size=20&status=OPEN
Authorization: Bearer <JWT>

Response 200:
{
  "content": [
    {
      "post_id": 5001,
      "title": "말티즈 분양합니다",
      "dog": { "dog_id": "...", "breed": "말티즈", "name": "초코" },
      "author": { "user_id": 101, "shelter_name": "행복보호소" },
      "status": "OPEN",
      "created_at": "2026-04-12T10:00:00Z"
    }
  ],
  "total_elements": 100,
  "total_pages": 5
}
```

### 게시글 작성

```
POST /api/posts
Content-Type: application/json
Authorization: Bearer <JWT>

Request:
{
  "dog_id": "uuid",
  "title": "말티즈 분양합니다",
  "content": "상세 내용..."
}

Response 201:
{
  "post_id": 5001,
  "status": "OPEN"
}
```

---

## 공통 에러 응답

```json
{
  "error": "ERROR_CODE",
  "message": "사람이 읽을 수 있는 메시지",
  "timestamp": "ISO8601"
}
```

HTTP 상태 코드: 400 (입력 오류), 401 (미인증), 403 (권한 없음), 404 (리소스 없음), 500 (서버 오류)
