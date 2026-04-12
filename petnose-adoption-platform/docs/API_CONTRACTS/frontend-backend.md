# API 계약 — Flutter ↔ Spring Boot

> 이 문서는 초기 API 계약 초안입니다. 실제 구현이 확정되면 이 문서를 갱신하거나 Swagger/OpenAPI 문서로 대체합니다.

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
  "user_id": "uuid",
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
Authorization: Bearer <JWT>

Form fields:
  name: string
  breed: string
  gender: string  // MALE | FEMALE
  birth_date: string (YYYY-MM-DD, 선택)
  nose_image: file (JPEG/PNG)

Response 201:
{
  "dog_id": "uuid",
  "name": "초코",
  "status": "REGISTERED",
  "embedding_status": "COMPLETED"  // COMPLETED | PENDING | FAILED
}

Response 400: 입력값 오류
Response 422: 이미지 처리 실패 (임베딩 서비스 오류)
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
      "user_id": "uuid",
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
      "post_id": "uuid",
      "title": "말티즈 분양합니다",
      "dog": { "dog_id": "...", "breed": "말티즈", "name": "초코" },
      "author": { "user_id": "...", "shelter_name": "행복보호소" },
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
  "post_id": "uuid",
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
