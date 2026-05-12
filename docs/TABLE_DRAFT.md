# TABLE DRAFT (MVP Final Baseline)

## 1. 스키마 기준

- DBMS: MySQL 8.0
- Storage engine: InnoDB
- 문자셋: `utf8mb4` (가능한 경우 `utf8mb4_0900_ai_ci`)
- soft delete: `deleted_at` 기준
- enum 저장: `VARCHAR + CHECK` (JPA는 `EnumType.STRING`)

## 2. 테이블 목록 (8개)

1. `users`
2. `refresh_tokens`
3. `shelter_profiles`
4. `dogs`
5. `dog_images`
6. `verification_logs`
7. `adoption_posts`
8. `reports`

## 3. 핵심 관계

- `users (1) - (N) refresh_tokens` (`ON DELETE CASCADE`)
- `users (1) - (1) shelter_profiles` (`user_id UNIQUE`, `ON DELETE RESTRICT`)
- `users (1) - (N) dogs`
- `dogs (1) - (N) dog_images`
- `dogs (1) - (N) adoption_posts`
- `dogs (1) - (N) verification_logs`
- `users (1) - (N) reports` (`reporter_user_id`)
- `users (1) - (N) reports` (`handled_by_user_id`, `ON DELETE SET NULL`)

주의:
- 강한 감사 추적이 필요한 도메인(`dogs`, `dog_images`, `verification_logs`, `adoption_posts`, `reports`)에는 hard delete cascade를 사용하지 않는다.
- `reports.target_type + target_id`는 polymorphic 구조이며 직접 FK를 걸지 않는다.

## 4. 테이블별 요약

### `users`
- PK: `id BIGINT AUTO_INCREMENT`
- Unique: `email`
- enum: `role` (`ADOPTER|SHELTER|ADMIN`), `status` (`ACTIVE|SUSPENDED|WITHDRAWN`)
- 감사 컬럼: `created_at`, `updated_at`, `deleted_at`

### `refresh_tokens`
- PK: `id BIGINT AUTO_INCREMENT`
- FK: `user_id -> users.id ON DELETE CASCADE`
- Unique: `token_hash (CHAR(64))`
- 만료/폐기 컬럼: `expires_at`, `revoked_at`

### `shelter_profiles`
- PK: `id BIGINT AUTO_INCREMENT`
- FK: `user_id -> users.id ON DELETE RESTRICT`
- Unique: `user_id`
- enum: `verification_status` (`PENDING|VERIFIED|REJECTED`)
- 감사 컬럼: `created_at`, `updated_at`, `deleted_at`

### `dogs`
- PK: `id CHAR(36)` (UUID 문자열)
- FK: `registered_by_user_id -> users.id`
- Self FK: `duplicate_candidate_dog_id -> dogs.id ON DELETE SET NULL`
- enum:
  - `sex` (`MALE|FEMALE|UNKNOWN`)
  - `status` (`DRAFT|ACTIVE|ADOPTED|HIDDEN|REJECTED`)
  - `nose_verification_status` (`NOT_REQUESTED|PENDING|VERIFIED|DUPLICATE_SUSPECTED|FAILED`)
  - `embedding_status` (`NOT_STARTED|PENDING|INDEXED|SKIPPED_DUPLICATE|FAILED`)
- Qdrant 연결 컬럼:
  - `qdrant_collection`
  - `qdrant_point_id`
  - `embedding_model`
  - `embedding_dimension`
- snapshot 컬럼:
  - `duplicate_candidate_dog_id`
  - `duplicate_similarity_score`
  - `verified_at`
- Unique: `(qdrant_collection, qdrant_point_id)`

### `dog_images`
- PK: `id BIGINT AUTO_INCREMENT`
- FK:
  - `dog_id -> dogs.id`
  - `uploaded_by_user_id -> users.id`
- enum:
  - `image_type` (`PROFILE|NOSE_ORIGINAL|NOSE_CROP|EXTRA`)
  - `storage_provider` (`LOCAL|S3`)
  - `image_status` (`ACTIVE|REJECTED|DELETED`)
  - `quality_status` (`NOT_CHECKED|PASSED|FAILED`)
- Unique: `relative_path`
- 원칙: 이미지 바이너리 저장 금지, 경로/메타데이터만 저장

### `verification_logs`
- PK: `id BIGINT AUTO_INCREMENT`
- FK:
  - `dog_id -> dogs.id`
  - `nose_image_id -> dog_images.id`
  - `requested_by_user_id -> users.id`
  - `duplicate_candidate_dog_id -> dogs.id ON DELETE SET NULL`
- Unique: `request_id` (nullable unique)
- enum: `verification_status` (`PENDING|VERIFIED|DUPLICATE_SUSPECTED|FAILED`)
- 이력 컬럼: score, threshold, elapsed(ms), failure code/message
- 역할: 인증 시도 이력 Source of Truth

### `adoption_posts`
- PK: `id BIGINT AUTO_INCREMENT`
- FK:
  - `dog_id -> dogs.id`
  - `author_user_id -> users.id`
- enum: `status` (`DRAFT|PUBLISHED|CLOSED|HIDDEN|DELETED`)
- 메타: `view_count`, `published_at`, `closed_at`
- 정책: 동일 dog의 동시 `PUBLISHED` 1개 제한은 서비스 레이어에서 검증

### `reports`
- PK: `id BIGINT AUTO_INCREMENT`
- FK:
  - `reporter_user_id -> users.id`
  - `handled_by_user_id -> users.id ON DELETE SET NULL`
- polymorphic target:
  - `target_type` (`USER|DOG|DOG_IMAGE|ADOPTION_POST|VERIFICATION_LOG`)
  - `target_id` (`VARCHAR(64)`)
- enum:
  - `reason_type` (`FAKE_REGISTRATION|DUPLICATE_SUSPECTED|INAPPROPRIATE_POST|IMAGE_ABUSE|OTHER`)
  - `status` (`PENDING|IN_REVIEW|RESOLVED|REJECTED`)

## 5. MySQL / 파일 / 벡터 경계

- MySQL에는 이미지 상대경로(`relative_path`)만 저장한다.
- 정적 파일 서빙은 Nginx의 `/files/{relative_path}`에서 처리한다.
- 벡터 값은 MySQL에 저장하지 않는다.
- 벡터는 Qdrant `dog_nose_embeddings` 컬렉션에 저장한다.

## 6. 제외된 후순위 테이블 (확장 후보)

이번 MVP baseline에는 아래 테이블을 만들지 않았다.

- `qdrant_sync_jobs`
- `admin_actions`
- `favorites`
- `adoption_applications`
- `post_images`
- `dog_breeds`
- `dog_verification_attempts`

