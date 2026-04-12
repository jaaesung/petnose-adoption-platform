# DB 테이블 설계 초안

> 이 문서는 1차 설계 초안입니다. 컬럼을 확정하지 않았으며, 구현 진행에 따라 변경됩니다.

---

## 테이블 후보 목록

### `users`

사용자 계정 정보를 저장합니다.

| 컬럼 후보 | 설명 |
|---|---|
| id | PK, UUID 또는 BIGINT |
| email | 로그인 식별자, 유니크 |
| password_hash | bcrypt 해시 |
| role | `ADOPTER`, `SHELTER`, `ADMIN` 등 |
| created_at | 가입 시각 |
| is_active | 탈퇴/정지 여부 |

### `seller_profiles`

보호소 또는 분양자 프로필입니다. `users`와 1:1 관계입니다.

| 컬럼 후보 | 설명 |
|---|---|
| id | PK |
| user_id | FK → users.id |
| shelter_name | 보호소/단체명 |
| contact | 연락처 |
| address | 주소 |
| verified | 인증 여부 |

### `dogs`

등록된 강아지 개체 정보입니다.

| 컬럼 후보 | 설명 |
|---|---|
| id | PK, UUID 권장 (Qdrant point_id와 맞추기 위해) |
| owner_user_id | FK → users.id (등록자) |
| name | 강아지 이름 |
| breed | 품종 |
| gender | 성별 |
| birth_date | 생년월일 (대략) |
| status | `REGISTERED`, `LOST`, `ADOPTED` 등 |
| qdrant_point_id | Qdrant 포인트 ID (null이면 임베딩 미완료) |
| created_at | 등록 시각 |

### `dog_images`

강아지 이미지 경로를 별도 관리합니다. 비문 이미지와 일반 사진을 함께 관리합니다.

| 컬럼 후보 | 설명 |
|---|---|
| id | PK |
| dog_id | FK → dogs.id |
| image_type | `NOSE`, `PROFILE`, `EXTRA` |
| file_path | 서버 내 저장 경로 (`uploads/` 기준) |
| uploaded_at | 업로드 시각 |

### `auth_logs`

인증 시도 이력을 기록합니다. 비문 인증 결과 추적에 사용합니다.

| 컬럼 후보 | 설명 |
|---|---|
| id | PK |
| requested_by | FK → users.id |
| target_dog_id | 조회 대상 강아지 (nullable) |
| matched_dog_id | 매칭된 강아지 (nullable) |
| similarity_score | Qdrant 유사도 점수 |
| result | `MATCHED`, `NOT_FOUND`, `ERROR` |
| created_at | 시도 시각 |

### `adoption_posts`

입양 게시글입니다. 보호소 또는 개인이 작성합니다.

| 컬럼 후보 | 설명 |
|---|---|
| id | PK |
| author_user_id | FK → users.id |
| dog_id | FK → dogs.id |
| title | 게시글 제목 |
| content | 내용 |
| status | `OPEN`, `CLOSED`, `ADOPTED` |
| created_at | 작성 시각 |
| updated_at | 수정 시각 |

### `reports`

부적절한 게시글 또는 사용자 신고입니다.

| 컬럼 후보 | 설명 |
|---|---|
| id | PK |
| reporter_user_id | FK → users.id |
| target_type | `POST`, `USER` |
| target_id | 신고 대상 ID |
| reason | 신고 사유 |
| status | `PENDING`, `RESOLVED` |
| created_at | 신고 시각 |

---

## 관계 요약

```
users 1 ── 1 seller_profiles
users 1 ── N dogs
dogs  1 ── N dog_images
dogs  1 ── 1 adoption_posts (일반적으로)
users 1 ── N auth_logs
users 1 ── N reports
```

---

> 실제 DDL은 구현 단계에서 `backend/src/main/resources/` 또는 Flyway 마이그레이션으로 관리합니다.
