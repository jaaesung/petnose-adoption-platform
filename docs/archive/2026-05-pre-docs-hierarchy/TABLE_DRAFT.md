> 보관 문서(Archive)
>
> 이 문서는 과거 설계/초안 기록입니다.
> 현재 구현 기준으로 사용하지 마세요.
> 현재 기준은 `docs/README.md`와 `docs/PROJECT_KNOWLEDGE_INDEX.md`에서 시작하세요.
> active canonical 문서와 충돌하면 active canonical 문서가 우선합니다.

# Table Draft (MVP Canonical v2)

## Schema Rules

- DBMS: MySQL 8.0
- Storage engine: InnoDB
- Charset: `utf8mb4`
- Enum storage: `VARCHAR + CHECK`
- MySQL is the source of truth.
- Qdrant stores embedding vectors only.
- Image files are not stored in MySQL BLOB columns.

## Active Table List

1. `users`
2. `dogs`
3. `dog_images`
4. `verification_logs`
5. `adoption_posts`

## Relationships

- `users (1) - (N) dogs`
- `users (1) - (N) verification_logs`
- `users (1) - (N) adoption_posts`
- `dogs (1) - (N) dog_images`
- `dogs (1) - (N) verification_logs`
- `dogs (1) - (N) adoption_posts`
- `dog_images (1) - (N) verification_logs`

## `users`

- PK: `id BIGINT AUTO_INCREMENT`
- Unique: `email`
- Role: `USER`, `ADMIN`
- Profile fields: `display_name`, `contact_phone`, `region`
- `display_name` is nullable until adoption post creation.
- Audit: `created_at`

## `dogs`

- PK: `id CHAR(36)`
- FK: `owner_user_id -> users.id`
- Basic fields: `name`, `breed`, `gender`, `birth_date`, `description`
- Status: `PENDING`, `REGISTERED`, `DUPLICATE_SUSPECTED`, `REJECTED`, `ADOPTED`, `INACTIVE`
- Audit: `created_at`, `updated_at`

`dogs.status` is lifecycle/service state. Verification details are stored in `verification_logs`.

Qdrant point id is always equal to `dogs.id`; there is no separate MySQL column for it.

## `dog_images`

- PK: `id BIGINT AUTO_INCREMENT`
- FK: `dog_id -> dogs.id`
- Type: `NOSE`, `PROFILE`
- File metadata: `file_path`, `mime_type`, `file_size`, `sha256`
- Audit: `uploaded_at`

`file_path` is a relative path. Image binaries are stored outside MySQL.

## `verification_logs`

- PK: `id BIGINT AUTO_INCREMENT`
- FK:
  - `dog_id -> dogs.id`
  - `dog_image_id -> dog_images.id`
  - `requested_by_user_id -> users.id`
  - `candidate_dog_id -> dogs.id ON DELETE SET NULL`
- Result: `PENDING`, `PASSED`, `DUPLICATE_SUSPECTED`, `EMBED_FAILED`, `QDRANT_SEARCH_FAILED`, `QDRANT_UPSERT_FAILED`
- Verification data: `similarity_score`, `candidate_dog_id`, `model`, `dimension`, `failure_reason`
- Audit: `created_at`

Embedding vectors are not stored here. Vectors live only in Qdrant.

## `adoption_posts`

- PK: `id BIGINT AUTO_INCREMENT`
- FK:
  - `author_user_id -> users.id`
  - `dog_id -> dogs.id`
- Status: `DRAFT`, `OPEN`, `RESERVED`, `COMPLETED`, `CLOSED`
- Fields: `title`, `content`, `published_at`, `closed_at`
- Audit: `created_at`, `updated_at`

Before creating or opening a post, the service should require `users.display_name` and a dog status that allows posting.
