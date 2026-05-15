# Schema / Entity / Service Constraint Audit

## 문서 성격

- 감사 보고서
- 구현 변경 없음
- active canonical 변경 없음
- 다음 수정 브랜치 결정을 위한 자료

## 검토 기준

이번 감사는 active canonical 문서와 현재 develop 브랜치의 런타임 구현만 기준으로 삼았다. 보관 문서는 active 구현 기준으로 사용하지 않았다.

항상 먼저 읽은 문서:

- `docs/README.md`
- `docs/PROJECT_KNOWLEDGE_INDEX.md`

그다음 읽은 canonical/reference 문서:

- `docs/PETNOSE_MVP_FINAL_PROJECT_SPEC.md`
- `docs/PETNOSE_MVP_API_CONTRACT.md`
- `docs/db/petnose_mvp_schema.dbml`
- `docs/db/V20260508__mvp_canonical_schema.sql`
- `docs/reference/DB_MIGRATION_STRATEGY.md`

런타임 DB 마이그레이션:

- `backend/src/main/resources/db/migration/V1__baseline.sql`

JPA 엔티티:

- `backend/src/main/java/com/petnose/api/domain/entity/User.java`
- `backend/src/main/java/com/petnose/api/domain/entity/Dog.java`
- `backend/src/main/java/com/petnose/api/domain/entity/DogImage.java`
- `backend/src/main/java/com/petnose/api/domain/entity/VerificationLog.java`
- `backend/src/main/java/com/petnose/api/domain/entity/AdoptionPost.java`

Enum:

- `backend/src/main/java/com/petnose/api/domain/enums/UserRole.java`
- `backend/src/main/java/com/petnose/api/domain/enums/DogGender.java`
- `backend/src/main/java/com/petnose/api/domain/enums/DogStatus.java`
- `backend/src/main/java/com/petnose/api/domain/enums/DogImageType.java`
- `backend/src/main/java/com/petnose/api/domain/enums/VerificationResult.java`
- `backend/src/main/java/com/petnose/api/domain/enums/AdoptionPostStatus.java`

DTO / 요청 모델:

- `backend/src/main/java/com/petnose/api/dto/auth/RegisterRequest.java`
- `backend/src/main/java/com/petnose/api/dto/auth/LoginRequest.java`
- `backend/src/main/java/com/petnose/api/dto/user/UserProfileUpdateRequest.java`
- `backend/src/main/java/com/petnose/api/dto/registration/DogRegisterRequest.java`
- `backend/src/main/java/com/petnose/api/dto/adoption/AdoptionPostCreateRequest.java`
- `backend/src/main/java/com/petnose/api/dto/adoption/AdoptionPostStatusUpdateRequest.java`
- `backend/src/main/java/com/petnose/api/dto/dog/DogListItemResponse.java`
- `backend/src/main/java/com/petnose/api/dto/dog/DogOwnerDetailResponse.java`
- `backend/src/main/java/com/petnose/api/dto/dog/DogPublicDetailResponse.java`
- `backend/src/main/java/com/petnose/api/dto/dog/DogImageResponse.java`
- `backend/src/main/java/com/petnose/api/dto/dog/DogVerificationLogResponse.java`

서비스 / 컨트롤러:

- `backend/src/main/java/com/petnose/api/service/AuthService.java`
- `backend/src/main/java/com/petnose/api/service/DogRegistrationService.java`
- `backend/src/main/java/com/petnose/api/service/DogQueryService.java`
- `backend/src/main/java/com/petnose/api/service/AdoptionPostService.java`
- `backend/src/main/java/com/petnose/api/service/HandoverVerificationService.java`
- `backend/src/main/java/com/petnose/api/service/FileStorageService.java`
- `backend/src/main/java/com/petnose/api/controller/DogRegistrationController.java`

관찰한 테스트:

- `backend/src/test/java/com/petnose/api/domain/entity/CanonicalEntityShapeTest.java`
- `backend/src/test/java/com/petnose/api/controller/AuthUserApiIntegrationTest.java`
- `backend/src/test/java/com/petnose/api/controller/DogRegistrationControllerTest.java`
- `backend/src/test/java/com/petnose/api/controller/DogRegisterAuthPrincipalIntegrationTest.java`
- `backend/src/test/java/com/petnose/api/controller/DogQueryControllerTest.java`
- `backend/src/test/java/com/petnose/api/controller/AdoptionPostCreateControllerTest.java`
- `backend/src/test/java/com/petnose/api/service/AdoptionPostServiceTest.java`
- `backend/src/test/java/com/petnose/api/controller/AdoptionPostOwnerManagementControllerTest.java`
- `backend/src/test/java/com/petnose/api/controller/AdoptionPostPublicQueryControllerTest.java`
- `backend/src/test/java/com/petnose/api/controller/HandoverVerificationControllerTest.java`

## 요약 결론

- 핵심 5개 active domain table 모델은 유지되어 있다. active 테이블은 `users`, `dogs`, `dog_images`, `verification_logs`, `adoption_posts`로 정리되어 있으며, 런타임 Flyway V1도 clean canonical SQL과 같은 5개 테이블을 생성한다.
- active role은 `USER`, `ADMIN`만 유지되어 있다. legacy role/table/API가 active scope로 재도입된 흔적은 발견하지 못했다.
- DBML, clean canonical SQL, 런타임 Flyway V1은 주요 필드 제약이 서로 잘 맞는다.
- JPA/service/API/test와 비교하면 일부 필드는 의도적인 service-level 정책으로 볼 수 있으나, `adoption_posts.title`, `adoption_posts.content`는 canonical schema와 현재 서비스 정책이 명확히 어긋난다.
- 생산 전에는 문서/스키마 canonical 결정 브랜치를 먼저 만들고, 그 결정에 따라 backend entity/service/test 또는 추가 Flyway migration을 분리해서 정렬하는 것이 안전하다.

## Constraint Matrix

### users

| field | DBML | clean canonical SQL | backend Flyway migration | JPA entity | service validation | API contract / tests | finding | recommended next action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `email` | `varchar(255) not null unique` | `VARCHAR(255) NOT NULL`, unique key | 동일 | `nullable=false`, `unique=true`, `length=255` | register/login 모두 blank 금지, trim/lowercase, max 255 | Auth contract와 tests가 unique, lowercase, password 미노출 흐름 확인 | 정렬됨 | 유지 |
| `password_hash` | `varchar(255) not null` | `VARCHAR(255) NOT NULL` | 동일 | `nullable=false`, `length=255` | BCrypt hash 저장, plain password는 길이와 blank 검증 | 응답 DTO/test에서 password hash 미노출 확인 | 정렬됨 | 유지 |
| `role` | `USER/ADMIN`, default `USER` | check `USER`,`ADMIN`, default `USER` | 동일 | `UserRole.USER`, `UserRole.ADMIN`만 존재, default USER | register는 항상 USER | contract/tests가 register role USER 확인 | 정렬됨 | 유지 |
| `display_name` | `varchar(150) null`, adoption post 전 필요 | `VARCHAR(150) NULL` | 동일 | `length=150`, nullable | register는 blank to null, profile update는 길이만 검증, post 생성 시 non-blank 요구 | API contract가 nullable/profile update와 post 생성 전 non-blank 정책을 설명, tests가 post 생성 시 blank 거부 확인 | schema nullable + service 정책은 의도된 형태. 단 profile update blank 허용은 관련 hardening 이슈 | profile update blank 정책을 별도 code/test 브랜치에서 결정 |
| `contact_phone` | `varchar(30) null` | `VARCHAR(30) NULL` | 동일 | `length=30`, nullable | register는 blank to null, profile update는 길이만 검증 | contract/tests가 nullable과 max length 확인 | 정렬됨 | 유지 또는 blank normalization 정책만 별도 결정 |
| `region` | `varchar(100) null` | `VARCHAR(100) NULL` | 동일 | `length=100`, nullable | register는 blank to null, profile update는 길이만 검증 | contract/tests가 nullable과 max length 확인 | 정렬됨 | 유지 또는 blank normalization 정책만 별도 결정 |
| `is_active` | `boolean not null default true` | `BOOLEAN NOT NULL DEFAULT TRUE` | 동일 | primitive `boolean`, `nullable=false` | login/current user에서 inactive 차단 | tests가 inactive user login/me 차단 확인 | 정렬됨 | 유지 |

### dogs

| field | DBML | clean canonical SQL | backend Flyway migration | JPA entity | service validation | API contract / tests | finding | recommended next action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `id` | `char(36) pk`, UUID, Qdrant point id와 동일 | `CHAR(36) NOT NULL PRIMARY KEY` | 동일 | `@Id`, `length=36` | registration에서 UUID 생성, Qdrant point id로 사용 | contract/tests가 `qdrant_point_id`를 계산 필드로 확인 | 정렬됨. JPA nullable 표기는 `@Id`로 충분 | 유지 |
| `owner_user_id` | `bigint not null` | `BIGINT NOT NULL`, FK users | 동일 | `nullable=false` ManyToOne | JWT principal 우선, auth header 없을 때 local/dev `user_id` fallback | auth principal integration tests가 fallback/override/invalid JWT 정책 확인 | 정렬됨 | 현재 fallback 제거는 별도 비목표이므로 유지 |
| `name` | `varchar(100) not null` | `VARCHAR(100) NOT NULL` | 동일 | `nullable=false`, `length=100` | non-blank required, trim | registration API/tests가 required form field로 사용 | 정렬됨 | 유지 |
| `breed` | `varchar(100) null` | `VARCHAR(100) NULL` | 동일 | `length=100`, nullable | registration service가 non-blank required, trim | API form field는 required처럼 정의되어 있고 tests는 값을 항상 제공 | DB/entity nullable과 service/API required 정책 불일치. 현재는 service-level required 정책으로 작동 | canonical에서 service-only required로 명시하거나 DB `NOT NULL` 변경 여부를 다음 브랜치에서 결정 |
| `gender` | enum nullable, 값 `MALE/FEMALE/UNKNOWN` | `VARCHAR(10) NULL` + check, default 없음 | 동일 | enum nullable, `length=10` | `DogGender.from()`이 null/blank 거부, `UNKNOWN`은 명시 값으로 허용 | API form field는 required처럼 정의, tests는 값을 제공 | DB/entity nullable과 service/API required 정책 불일치. DB default `UNKNOWN`은 없음 | service-only required로 문서화할지, DB `NOT NULL` + explicit/default 정책으로 갈지 결정 |
| `birth_date` | optional | `DATE NULL` | 동일 | `LocalDate`, nullable | optional parse | contract/tests에서 optional | 정렬됨 | 유지 |
| `description` | optional | `TEXT NULL` | 동일 | `TEXT`, nullable | blank to null | contract에서 optional | 정렬됨 | 유지 |
| `status` | enum not null default `PENDING` | `VARCHAR(30) NOT NULL DEFAULT 'PENDING'` | 동일 | `nullable=false`, default `PENDING` | registration 생성 PENDING, 검증 결과에 따라 REGISTERED/DUPLICATE_SUSPECTED/REJECTED | query/post tests가 status 기반 정책 확인 | 정렬됨 | 유지 |
| `created_at`, `updated_at` | timestamp, created/updated | `TIMESTAMP`, `updated_at` auto update | 동일 | `Instant` | entity lifecycle callbacks | dog DTO/tests는 `Instant` 기반 `Z` timestamp 사용 | 정렬됨 | 유지 |

### dog_images

| field | DBML | clean canonical SQL | backend Flyway migration | JPA entity | service validation | API contract / tests | finding | recommended next action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `dog_id` | `char(36) not null` FK dogs | `CHAR(36) NOT NULL` | 동일 | `nullable=false` ManyToOne | registration에서 dog 생성 후 image 저장 | tests가 dog image row 생성 확인 | 정렬됨 | 유지 |
| `image_type` | `NOSE/PROFILE`, not null | `VARCHAR(20) NOT NULL` + check | 동일 | enum `nullable=false`, `length=20` | nose required, profile optional | registration/query tests가 nose 공개 제한, profile URL 허용 확인 | 정렬됨 | 유지 |
| `file_path` | `varchar(500) not null unique`, upload-root-relative | `VARCHAR(500) NOT NULL`, unique | 동일 | `nullable=false`, `unique=true`, `length=500` | `FileStorageService`가 upload root relative path 생성 | contract/tests가 상대 경로 정책 확인 | 정렬됨 | 유지 |
| `mime_type` | `varchar(100) null` | `VARCHAR(100) NULL` | 동일 | `length=100`, nullable | service는 이미지 content type을 required로 검증하고 저장 | tests는 metadata nullability를 직접 검증하지 않음 | DB/entity nullable과 service-created row required 성격이 다름. 유연성으로 볼 수 있음 | service-created row는 항상 채운다고 문서화하거나 DB `NOT NULL` 변경 결정 |
| `file_size` | `bigint null` | `BIGINT NULL` | 동일 | nullable `Long` | service가 multipart size 저장 | tests는 metadata nullability를 직접 검증하지 않음 | 위와 동일 | 위와 동일 |
| `sha256` | `char(64) null` | `CHAR(64) NULL` | 동일 | `length=64`, nullable | service가 bytes sha256 계산 저장 | tests는 metadata nullability를 직접 검증하지 않음 | 위와 동일 | 위와 동일 |
| `uploaded_at` | timestamp not null default current | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | 동일 | `Instant`, set on persist | entity lifecycle | API는 image uploaded_at을 노출 | 정렬됨 | 유지 |

### verification_logs

| field | DBML | clean canonical SQL | backend Flyway migration | JPA entity | service validation | API contract / tests | finding | recommended next action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `dog_id` | `char(36) not null` | `CHAR(36) NOT NULL` | 동일 | `nullable=false` ManyToOne | registration에서 pending log 생성 | tests가 log 생성/조회 흐름 확인 | 정렬됨 | 유지 |
| `dog_image_id` | `bigint not null` | `BIGINT NOT NULL` | 동일 | `nullable=false` ManyToOne | nose image 기준으로 log 생성 | tests가 handover에서 기존 log 수 불변 확인 | 정렬됨 | 유지 |
| `requested_by_user_id` | `bigint not null` | `BIGINT NOT NULL` | 동일 | `nullable=false` ManyToOne | owner user 저장 | entity shape tests 확인 | 정렬됨 | 유지 |
| `result` | enum not null | `VARCHAR(40) NOT NULL` + check | 동일 | enum `nullable=false`, `length=40` | PENDING/PASSED/DUPLICATE/실패 결과 설정 | tests가 verification_status 계산 확인 | 정렬됨 | 유지 |
| `similarity_score` | `decimal(6,5) null` | `DECIMAL(6, 5) NULL` | 동일 | `BigDecimal`, precision 6, scale 5 | service가 `setScale(5, HALF_UP)` | API 응답은 `Double`로 노출 | persistence는 정렬됨. API 숫자 표현은 고정 소수 자릿수 문자열을 보장하지 않음 | API가 숫자 타입이면 현상 유지. 고정 5자리 표시가 필요하면 contract/test 별도 결정 |
| `candidate_dog_id` | `char(36) null` | `CHAR(36) NULL` | 동일 | `length=36`, nullable | duplicate일 때만 설정 | top_match 응답에는 dog_id/similarity_score/breed만 노출 | 정렬됨 | 유지 |
| `model` | `varchar(100) null` | `VARCHAR(100) NULL` | 동일 | `length=100`, nullable | service가 모델명 저장 | handover response도 model 노출 | 정렬됨 | 유지 |
| `dimension` | `int null` | `INT NULL` | 동일 | `Integer`, nullable | service가 embedding dimension 저장 | handover response도 dimension 노출 | 정렬됨 | 유지 |
| `failure_reason` | `varchar(1000) null` | `VARCHAR(1000) NULL` | 동일 | `length=1000`, nullable | 실패 시 저장 | API error와 별개로 내부 log | 정렬됨 | 유지 |
| `created_at` | timestamp not null default current | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | 동일 | `Instant`, set on persist | entity lifecycle | API dog verification log DTO는 `Instant` | 정렬됨 | 유지 |

### adoption_posts

| field | DBML | clean canonical SQL | backend Flyway migration | JPA entity | service validation | API contract / tests | finding | recommended next action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `author_user_id` | `bigint not null` | `BIGINT NOT NULL` FK users | 동일 | `nullable=false` ManyToOne | authenticated active user required, display_name non-blank required | tests가 author_user_id 미노출과 display_name 필요 정책 확인 | 정렬됨 | 유지 |
| `dog_id` | `char(36) not null` | `CHAR(36) NOT NULL` FK dogs | 동일 | `nullable=false` ManyToOne | owner dog, REGISTERED, latest PASSED, active post 없음 | tests가 owner/status/latest verification/active post 정책 확인 | 정렬됨 | 유지 |
| `title` | `varchar(255) not null` | `VARCHAR(255) NOT NULL` | 동일 | `nullable=false`, `length=200` | non-blank, trim 후 max 200 | API contract는 요청 필드를 두지만 200/255 길이 기준은 명확하지 않음. tests는 길이 경계를 직접 검증하지 않음 | 명확한 제약 불일치 | canonical 길이를 200으로 낮출지, code/service를 255로 넓힐지 결정. 현재 서비스 정책 유지가 목적이면 docs/schema/runtime 정렬 브랜치가 필요 |
| `content` | `text null` | `TEXT NULL` | 동일 | `nullable=false`, `TEXT` | non-blank required, trim 저장 | API contract는 요청 필드를 두지만 DB nullable과 service required 차이를 명시하지 않음. tests는 성공 생성과 profile/display/status 정책 중심 | 명확한 nullability 불일치 | content required가 MVP 정책이면 canonical DBML/SQL/API contract를 `NOT NULL`/required로 정렬. nullable이 의도라면 entity/service/test 수정 필요 |
| `status` | enum not null default `DRAFT` | `VARCHAR(20) NOT NULL DEFAULT 'DRAFT'` + check | 동일 | enum `nullable=false`, `length=32`, default DRAFT | create는 DRAFT/OPEN만 허용, update transition 제한 | tests가 create/status/list/detail transition 확인 | 값 집합은 정렬됨. JPA length 32와 DB length 20은 현재 enum 값에서는 영향 작음 | 선택적 cleanup으로 entity length 20 정렬 가능 |
| `published_at` | optional | `TIMESTAMP NULL` | 동일 | `LocalDateTime`, nullable | OPEN 생성/전환 시 설정 | tests가 OPEN일 때 값, DRAFT일 때 null 확인 | 정렬됨 | 유지 |
| `closed_at` | optional | `TIMESTAMP NULL` | 동일 | `LocalDateTime`, nullable | CLOSED/COMPLETED 시 설정 | tests가 terminal 상태에서 closed_at 확인 | 정렬됨 | 유지 |
| `created_at`, `updated_at` | timestamp | `TIMESTAMP`, `updated_at` auto update | 동일 | `LocalDateTime` lifecycle | service 별도 검증 없음 | adoption DTO/tests는 zone 없는 timestamp 문자열을 기대 | DB는 정렬됨. 전체 API에서는 dog 쪽 `Instant`와 post 쪽 `LocalDateTime` 표현이 다름 | 의도된 API 표현이면 contract에 명시. 아니면 timestamp 타입 정렬 브랜치 필요 |
| active post uniqueness | DB constraint 없음 | DB unique/index 없음 | 동일 | repository derived query만 있음 | create/update에서 `DRAFT/OPEN/RESERVED` active post 존재 여부 검사 | tests가 active post blocking 확인 | service-level 정책만 있고 DB 동시성 보장은 없음 | 생산 전 동시 생성 방지가 필요하면 DB/index 또는 locking 정책을 별도 설계 |

## Findings

### P1. `adoption_posts.title` 길이 기준 불일치

- evidence: DBML, clean canonical SQL, Flyway V1은 `title`을 `VARCHAR(255) NOT NULL`로 둔다. `AdoptionPost` 엔티티는 `length=200`이고, `AdoptionPostService`는 trim 후 200자를 초과하면 `VALIDATION_FAILED`로 거부한다. API contract와 tests에는 200/255 경계가 명시적으로 고정되어 있지 않다.
- impact: DB에는 201-255자 제목을 저장할 수 있지만 API/service는 거부한다. canonical schema만 보는 작업자와 backend 구현을 보는 작업자가 서로 다른 제한을 전제로 삼을 수 있다.
- recommended fix options: 현재 backend 정책을 유지한다면 DBML/clean SQL/API contract를 200자로 정렬하고, 런타임 DB에도 강제해야 한다면 별도 Flyway migration을 추가한다. 반대로 DB canonical 255가 맞다면 entity/service/test를 255로 넓힌다.
- safest next branch type: combined. 먼저 docs/schema canonical 결정, 이후 필요하면 code/test 또는 schema/migration을 분리한다.

### P1. `adoption_posts.content` nullability / required 정책 불일치

- evidence: DBML, clean canonical SQL, Flyway V1은 `content TEXT NULL`이다. `AdoptionPost` 엔티티는 `nullable=false`이고, `AdoptionPostService`는 content non-blank를 요구한다. API contract는 content 필드를 포함하지만 DB nullable과 service required 차이를 별도 설명하지 않는다.
- impact: DB 직접 쓰기나 향후 import 경로에서는 null content가 들어갈 수 있지만, 현재 API/service/JPA 정책은 required로 작동한다. canonical required 여부가 불명확하다.
- recommended fix options: MVP에서 게시글 본문이 필수라면 DBML/clean SQL/API contract를 `NOT NULL`/required로 정렬하고, 런타임 DB 강제가 필요하면 새 migration을 만든다. 본문 nullable이 의도라면 entity/service/test를 nullable 허용으로 바꾼다.
- safest next branch type: combined. canonical 결정을 먼저 문서화한 뒤 schema/migration 또는 code/test 변경을 분리한다.

### P2. `dogs.breed` service-required vs DB nullable

- evidence: DBML, clean SQL, Flyway V1, JPA는 `breed`를 nullable로 둔다. `DogRegistrationService`는 non-blank breed를 요구하고 trim해서 저장한다. 등록 API와 tests는 breed를 사실상 required form field로 사용한다.
- impact: 현재 API 등록 경로에서는 breed가 항상 필요하지만 DB 모델은 알 수 없음/미입력 row를 허용한다. import, admin backfill, 미래 partial registration을 허용하려는 설계라면 괜찮지만, canonical required 필드라면 DB 제약이 약하다.
- recommended fix options: breed를 service-only required로 유지하려면 API contract와 project spec에 이 차이를 명시한다. 모든 dog row에서 필수라면 DBML/SQL/Flyway migration/JPA를 `NOT NULL`로 정렬한다.
- safest next branch type: docs-only 또는 combined. 먼저 정책 결정이 필요하다.

### P2. `dogs.gender` service-required vs DB nullable/default 없음

- evidence: DBML/SQL/Flyway는 `gender` nullable이고 default가 없다. enum에는 `UNKNOWN`이 있다. JPA도 nullable이다. `DogGender.from()`과 registration service는 null/blank를 거부하되 `UNKNOWN`은 명시 값으로 허용한다.
- impact: API 등록 경로는 gender required처럼 동작하지만 DB는 null row를 허용한다. DB default `UNKNOWN`이 없으므로 DB 직접 insert 시 null과 unknown이 다른 의미가 된다.
- recommended fix options: 현재 정책을 유지한다면 "registration API에서는 required, DB에서는 legacy/import 유연성을 위해 nullable"이라고 명시한다. 모든 row에 성별 상태가 필요하다면 `NOT NULL DEFAULT 'UNKNOWN'` 또는 `NOT NULL` + API explicit required 중 하나를 선택한다.
- safest next branch type: docs-only 또는 schema/migration.

### P2. `dog_images` metadata nullability 정책 차이

- evidence: DBML/SQL/Flyway/JPA는 `mime_type`, `file_size`, `sha256`을 nullable로 둔다. `FileStorageService`는 이미지 content type을 검증하고, file size와 sha256을 계산해서 `DogRegistrationService`가 저장한다.
- impact: 현재 service-created dog image row에는 metadata가 채워지지만, DB는 metadata 없는 row도 허용한다. 운영상 파일 무결성 감사를 DB 제약으로 보장하려면 부족하다.
- recommended fix options: 외부 import/마이그레이션 유연성이 필요하면 nullable을 유지하고 "service-created row에서는 항상 채움"이라고 문서화한다. 무결성을 DB가 보장해야 한다면 `NOT NULL` 변경과 기존 데이터 점검 migration을 검토한다.
- safest next branch type: docs-only 또는 schema/migration.

### P3. `verification_logs.similarity_score` persistence는 정렬, API 숫자 표현만 주의

- evidence: active DBML, clean SQL, Flyway V1은 모두 `DECIMAL(6,5)`이다. JPA는 `BigDecimal` precision 6 scale 5이고, registration service는 `setScale(5, HALF_UP)`로 저장한다. API 응답 DTO는 `Double`을 사용한다.
- impact: DB/entity/service 정렬은 양호하다. 다만 JSON 숫자는 trailing zero를 보장하지 않으므로 클라이언트가 고정 5자리 문자열을 기대하면 혼선이 생길 수 있다.
- recommended fix options: API contract에서 숫자 타입이며 표시 자릿수는 클라이언트 책임이라고 명시하거나, 고정 문자열이 필요할 때만 DTO/test 변경을 검토한다.
- safest next branch type: docs-only.

### P2. timestamp 타입과 JSON 표현 혼재

- evidence: `User`, `Dog`, `DogImage`, `VerificationLog`는 `Instant`를 사용한다. `AdoptionPost`는 `LocalDateTime`을 사용한다. API/tests도 dog query 계열은 `Z`가 붙는 timestamp를, adoption post 계열은 zone 없는 timestamp를 기대한다. 별도 Jackson timezone/format 설정은 확인하지 못했다.
- impact: 현재 tests 기준으로는 의도된 동작처럼 보이지만, public API 전체에서는 timestamp 표현이 섞인다. 모바일/외부 클라이언트가 시간대를 해석할 때 혼동할 수 있다.
- recommended fix options: 현행 표현을 유지한다면 API contract에 리소스별 timestamp 타입을 명시한다. 통일이 목표라면 별도 code/test 브랜치에서 `Instant` 또는 명시 timezone 정책으로 정렬한다.
- safest next branch type: docs-only 후 code/test.

### P2. profile update blank string 정책은 schema alignment가 아닌 hardening 이슈

- evidence: register는 profile field blank를 null로 정규화한다. profile update는 field presence와 length만 검증하고 blank string을 저장할 수 있다. adoption post 생성은 author `display_name`이 non-blank가 아니면 거부한다. API contract는 현재 profile update가 blank string을 거부하지 않는다고 설명한다.
- impact: 사용자가 profile update로 blank display name을 저장하면 이후 adoption post 생성이 막힐 수 있다. schema 불일치라기보다 사용자 경험과 validation hardening 이슈다.
- recommended fix options: profile update에서도 blank to null 또는 blank reject 중 하나를 선택하고 Auth/User API tests를 보강한다.
- safest next branch type: code/test.

### P2. active adoption post uniqueness는 service-level만 존재

- evidence: canonical DB에는 dog별 active post uniqueness constraint가 없다. `AdoptionPostService`와 repository query가 `DRAFT/OPEN/RESERVED` active post 존재 여부를 검사하고 tests가 해당 정책을 확인한다.
- impact: 일반 API 흐름에서는 차단되지만, 동시 요청이나 DB 직접 쓰기에서는 같은 dog에 active post가 중복될 여지가 있다.
- recommended fix options: MVP 트래픽에서 service-level 정책으로 충분한지 결정한다. 생산에서 강한 보장이 필요하면 generated column/index, locking, transaction isolation, 또는 상태별 unique strategy를 설계한다.
- safest next branch type: combined.

## Recommended Next Work

1. docs/schema canonical alignment patch
   - `adoption_posts.title` 200 vs 255, `content` nullable vs required를 먼저 canonical 정책으로 결정한다.
   - `dogs.breed`, `dogs.gender`, `dog_images` metadata의 nullable/service-required 차이를 문서에 명시할지 DB 제약으로 강제할지 결정한다.

2. backend entity/service/test alignment patch
   - canonical 결정이 backend 현행 정책과 다르면 entity/service/API tests를 맞춘다.
   - 현행 backend 정책을 유지한다면 API contract에 길이/required 정책을 명확히 추가하고 tests에 경계값을 보강한다.

3. optional Flyway migration
   - 런타임 MySQL에서도 `adoption_posts.title` 길이 또는 `content` `NOT NULL`을 강제해야 한다면 새 migration을 추가한다.
   - 이미 적용된 Flyway 파일은 수정하지 않는다.

4. user profile blank string hardening
   - profile update blank string을 null로 정규화할지 거부할지 결정하고 Auth/User API tests를 추가한다.

5. adoption post uniqueness hardening
   - production 동시성 요구가 있으면 service-level active post check를 DB/index/locking 정책과 함께 보강한다.

6. dog registration auth fallback hardening
   - 현재 local/dev `user_id` fallback은 이번 감사 범위에서 유지한다. 제거 또는 환경별 제한은 별도 보안 hardening 브랜치에서 다룬다.
