# Schema / Entity / Service Constraint Audit

## 문서 성격

- 감사 보고서
- 원본 감사 본문은 follow-up docs/schema alignment 이전 snapshot
- 원본 감사 자체는 구현 변경 없음 / active canonical 변경 없음
- 현재 문서는 follow-up branch들의 canonical decision과 runtime migration alignment note를 포함

## Post-audit canonical decision for schema/runtime alignment

이 follow-up schema/runtime alignment는 감사 결과를 바탕으로 active canonical docs와 runtime MySQL schema를 current MVP service behavior 쪽으로 정렬한다.

- `adoption_posts.title`은 current JPA/service validation에 맞춰 canonical docs에서 200자 제한으로 정렬한다.
- `adoption_posts.content`는 current JPA/service validation에 맞춰 canonical docs에서 required/non-blank 정책으로 정렬한다.
- `dogs.breed`와 `dogs.gender`는 dog registration API에서는 required로 문서화하되, operational/import flexibility를 위해 DB-level nullability는 유지하는 결정으로 정리한다.
- `dog_images.mime_type`, `dog_images.file_size`, `dog_images.sha256`은 service-created row에서 기대되는 metadata로 문서화하되, migration/import flexibility를 위해 DB-level nullability는 유지하는 결정으로 정리한다.
- runtime Flyway는 새 `V2__align_adoption_post_content_constraints.sql` migration으로 `adoption_posts.title VARCHAR(200) NOT NULL`과 `adoption_posts.content TEXT NOT NULL`을 강제한다.
- runtime Flyway는 새 `V3__add_nose_verification_attempts.sql` migration으로 pre-post nose verification attempt를 저장한다.
- `AdoptionPost.status` JPA column length도 runtime schema와 같은 20자로 정렬한다.
- tests는 adoption post title/content boundary, nose verification attempt consumption, entity/runtime migration shape를 고정한다.

### 현재 branch 적용 후 상태

- active DBML과 documentation-only clean canonical SQL은 `adoption_posts.title`을 200자로, `adoption_posts.content`를 `NOT NULL` / required 정책으로 문서화한다.
- active DBML과 documentation-only clean canonical SQL은 `nose_verification_attempts`를 포함한다.
- runtime Flyway V1은 historical baseline으로 남고, V2/V3가 최종 runtime schema를 canonical policy에 맞춘다.
- 새로 구축되는 runtime DB는 V1+V2+V3 적용 후 title max 200, content NOT NULL, one-time nose verification attempt 제약을 가진다.
- current API/service policy는 title max 200, content non-blank, `nose_verification_id` required/one-time consumption을 enforce한다.
- 남은 production 판단은 active post uniqueness 같은 동시성 hardening이며, title/content schema mismatch는 resolved 상태다.

### Dog registration auth fallback hardening follow-up

- dog registration auth fallback hardening은 이 branch에서 resolved 상태다.
- Public dog registration API contract는 JWT principal ownership을 사용한다.
- DB schema 또는 migration change는 없다.

### 원본 감사 본문 읽는 법

- 아래 constraint matrix와 findings는 원본 감사 snapshot을 보존하되, `adoption_posts.title` / `adoption_posts.content` 관련 행은 현재 runtime V2 alignment 상태를 반영한다.
- Flyway V1만 보면 여전히 historical baseline 제약이 보이지만, runtime 최종 schema 기준은 V1+V2다.
- 원본 감사 당시의 recommended next action 중 title/content runtime migration과 boundary test는 이 branch에서 resolved 상태로 본다.

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
- `backend/src/main/resources/db/migration/V2__align_adoption_post_content_constraints.sql`
- `backend/src/main/resources/db/migration/V3__add_nose_verification_attempts.sql`

JPA 엔티티:

- `backend/src/main/java/com/petnose/api/domain/entity/User.java`
- `backend/src/main/java/com/petnose/api/domain/entity/Dog.java`
- `backend/src/main/java/com/petnose/api/domain/entity/DogImage.java`
- `backend/src/main/java/com/petnose/api/domain/entity/VerificationLog.java`
- `backend/src/main/java/com/petnose/api/domain/entity/NoseVerificationAttempt.java`
- `backend/src/main/java/com/petnose/api/domain/entity/AdoptionPost.java`

Enum:

- `backend/src/main/java/com/petnose/api/domain/enums/UserRole.java`
- `backend/src/main/java/com/petnose/api/domain/enums/DogGender.java`
- `backend/src/main/java/com/petnose/api/domain/enums/DogStatus.java`
- `backend/src/main/java/com/petnose/api/domain/enums/DogImageType.java`
- `backend/src/main/java/com/petnose/api/domain/enums/VerificationResult.java`
- `backend/src/main/java/com/petnose/api/domain/enums/NoseVerificationStatus.java`
- `backend/src/main/java/com/petnose/api/domain/enums/AdoptionPostStatus.java`

DTO / 요청 모델:

- `backend/src/main/java/com/petnose/api/dto/auth/RegisterRequest.java`
- `backend/src/main/java/com/petnose/api/dto/auth/LoginRequest.java`
- `backend/src/main/java/com/petnose/api/dto/user/UserProfileUpdateRequest.java`
- `backend/src/main/java/com/petnose/api/dto/registration/DogRegisterRequest.java`
- `backend/src/main/java/com/petnose/api/dto/nose/NoseVerificationRequest.java`
- `backend/src/main/java/com/petnose/api/dto/nose/NoseVerificationResponse.java`
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
- `backend/src/main/java/com/petnose/api/service/NoseVerificationService.java`
- `backend/src/main/java/com/petnose/api/service/DogQueryService.java`
- `backend/src/main/java/com/petnose/api/service/AdoptionPostService.java`
- `backend/src/main/java/com/petnose/api/service/HandoverVerificationService.java`
- `backend/src/main/java/com/petnose/api/service/FileStorageService.java`
- `backend/src/main/java/com/petnose/api/controller/DogRegistrationController.java`
- `backend/src/main/java/com/petnose/api/controller/NoseVerificationController.java`

관찰한 테스트:

- `backend/src/test/java/com/petnose/api/domain/entity/CanonicalEntityShapeTest.java`
- `backend/src/test/java/com/petnose/api/controller/AuthUserApiIntegrationTest.java`
- `backend/src/test/java/com/petnose/api/controller/DogRegistrationControllerTest.java`
- `backend/src/test/java/com/petnose/api/controller/DogRegisterAuthPrincipalIntegrationTest.java`
- `backend/src/test/java/com/petnose/api/controller/NoseVerificationControllerTest.java`
- `backend/src/test/java/com/petnose/api/service/NoseVerificationServiceTest.java`
- `backend/src/test/java/com/petnose/api/controller/DogQueryControllerTest.java`
- `backend/src/test/java/com/petnose/api/controller/AdoptionPostCreateControllerTest.java`
- `backend/src/test/java/com/petnose/api/service/AdoptionPostServiceTest.java`
- `backend/src/test/java/com/petnose/api/controller/AdoptionPostOwnerManagementControllerTest.java`
- `backend/src/test/java/com/petnose/api/controller/AdoptionPostPublicQueryControllerTest.java`
- `backend/src/test/java/com/petnose/api/controller/HandoverVerificationControllerTest.java`

## 요약 결론

- 핵심 active domain table 모델은 `users`, `dogs`, `dog_images`, `verification_logs`, `nose_verification_attempts`, `adoption_posts`로 정리되어 있다. 런타임 Flyway V1은 historical 5개 baseline table을 생성하고, V3가 `nose_verification_attempts`를 추가한다.
- active role은 `USER`, `ADMIN`만 유지되어 있다. legacy role/table/API가 active scope로 재도입된 흔적은 발견하지 못했다.
- 원본 감사 시점에는 `adoption_posts.title`, `adoption_posts.content`가 canonical docs와 current service policy 사이에서 명확히 어긋났다.
- current branch 이후에는 DBML, documentation-only clean canonical SQL, JPA entity, service validation, runtime Flyway final schema(V1+V2+V3), tests가 `adoption_posts.title` 200자 / `adoption_posts.content` required / pre-post nose verification attempt 정책으로 맞춰졌다.
- 생산 전 남은 결정은 active post uniqueness 등 service-level 정책을 DB/index/locking으로 더 강하게 보장할지 여부다.

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
| `owner_user_id` | `bigint not null` | `BIGINT NOT NULL`, FK users | 동일 | `nullable=false` ManyToOne | JWT principal-only ownership. public API의 request `user_id`는 owner 결정에 사용하지 않음 | auth principal integration tests가 principal-only success, ignored `user_id`, missing/invalid/expired JWT, missing user, inactive user 정책 확인 | 정렬됨 | 유지 |
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

### nose_verification_attempts

| field | DBML | clean canonical SQL | backend Flyway migration | JPA entity | service validation | API contract / tests | finding | recommended next action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `id` | `bigint pk increment` | `BIGINT AUTO_INCREMENT` | V3 생성 | `@Id`, identity | nose verification 성공/중복 분기에서 생성 | response `nose_verification_id`로 노출, create post request에서 사용 | 정렬됨 | 유지 |
| `requested_by_user_id` | `bigint not null` FK users | `BIGINT NOT NULL` FK users | V3 생성 | `nullable=false` | attempt owner와 current user 일치 검증 | tests가 owner mismatch를 `NOSE_VERIFICATION_OWNER_MISMATCH`로 확인 | 정렬됨 | 유지 |
| `nose_image_path` | `varchar(500) not null unique` | `VARCHAR(500) NOT NULL` unique | V3 생성 | `length=500`, nullable=false | pre-post `nose_image` 저장 경로, post 성공 시 `dog_images.NOSE`로 연결 | tests가 success 후 NOSE image row 생성을 확인 | 정렬됨 | 유지 |
| `nose_image_mime_type` | `varchar(100) null` | `VARCHAR(100) NULL` | V3 생성 | nullable | adoption post 단계에서 NOSE row metadata로 복사 | integration tests가 row 생성 확인 | 정렬됨 | 유지 |
| `nose_image_file_size` | `bigint null` | `BIGINT NULL` | V3 생성 | nullable | stored file read metadata | service tests 확인 | 정렬됨 | 유지 |
| `nose_image_sha256` | `char(64) null` | `CHAR(64) NULL` | V3 생성 | length 64, nullable | stored file read metadata | service tests 확인 | 정렬됨 | 유지 |
| `result` | enum not null | `VARCHAR(40) NOT NULL` + check | V3 생성 | enum string `nullable=false`, length 40 | `PASSED`만 post 생성 가능, `DUPLICATE_SUSPECTED`는 차단 | tests가 passed/duplicate/consumed/expired/owner 정책 확인 | 정렬됨 | 유지 |
| `similarity_score` | `decimal(6,5) null` | `DECIMAL(6, 5) NULL` | V3 생성 | `BigDecimal`, precision 6, scale 5 | 중복 검색 최고 점수 저장 | service tests 확인 | 정렬됨 | 유지 |
| `candidate_dog_id` | `char(36) null` FK dogs | `CHAR(36) NULL` FK dogs | V3 생성 | `length=36`, nullable | 필요 시 top candidate 저장 | response는 다른 dog nose URL을 노출하지 않음 | 정렬됨 | 유지 |
| `model`, `dimension`, `failure_reason` | nullable metadata | nullable columns | V3 생성 | nullable | attempt 결과 내부 추적 | app handoff에는 내부 구조 과노출 금지 | 정렬됨 | 유지 |
| `expires_at` | timestamp not null | `TIMESTAMP NOT NULL` | V3 생성 | `Instant`, nullable=false | 생성 후 24시간 유효, 만료 시 post 생성 차단 | tests가 `NOSE_VERIFICATION_EXPIRED` 확인 | 정렬됨 | 유지 |
| `consumed_at` | timestamp null | `TIMESTAMP NULL` | V3 생성 | `Instant`, nullable | post 생성 성공 시 set | tests가 성공 후 consumed 처리와 재사용 차단 확인 | 정렬됨 | 유지 |
| `consumed_by_post_id` | nullable FK adoption_posts | `BIGINT NULL` FK adoption_posts | V3 생성 | nullable `Long` | post 생성 성공 시 saved post id 저장 | tests가 consumed post id 확인 | 정렬됨 | 유지 |
| `created_at` | timestamp not null default current | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | V3 생성 | `Instant`, set on persist | entity lifecycle | entity shape test 확인 | 정렬됨 | 유지 |

### adoption_posts

| field | DBML | clean canonical SQL | backend Flyway migration | JPA entity | service validation | API contract / tests | finding | recommended next action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `author_user_id` | `bigint not null` | `BIGINT NOT NULL` FK users | 동일 | `nullable=false` ManyToOne | authenticated active user required, display_name non-blank required | tests가 author_user_id 미노출과 display_name 필요 정책 확인 | 정렬됨 | 유지 |
| `dog_id` | `char(36) not null` | `CHAR(36) NOT NULL` FK dogs | 동일 | `nullable=false` ManyToOne | create request dog 기본 정보로 생성된 dog id 저장 | tests가 post create success 후 dog/NOSE/PROFILE/log/post 생성을 확인 | 정렬됨 | 유지 |
| `title` | `varchar(200) not null` | `VARCHAR(200) NOT NULL` | V1 historical `VARCHAR(255)`, V2 final `VARCHAR(200) NOT NULL` | `nullable=false`, `length=200` | non-blank, trim 후 max 200 | API contract와 boundary tests가 200자 제한을 고정 | 정렬됨 | 유지 |
| `content` | `text not null` | `TEXT NOT NULL` | V1 historical `TEXT NULL`, V2 final `TEXT NOT NULL` | `nullable=false`, `TEXT` | non-blank required, trim 저장 | API contract와 tests가 content required 정책을 고정 | 정렬됨 | 유지 |
| `status` | enum not null default `DRAFT` | `VARCHAR(20) NOT NULL DEFAULT 'DRAFT'` + check | 동일 | enum `nullable=false`, `length=20`, default DRAFT | create는 DRAFT/OPEN만 허용, update transition 제한 | tests가 create/status/list/detail transition 확인 | 정렬됨 | 유지 |
| `published_at` | optional | `TIMESTAMP NULL` | 동일 | `LocalDateTime`, nullable | OPEN 생성/전환 시 설정 | tests가 OPEN일 때 값, DRAFT일 때 null 확인 | 정렬됨 | 유지 |
| `closed_at` | optional | `TIMESTAMP NULL` | 동일 | `LocalDateTime`, nullable | CLOSED/COMPLETED 시 설정 | tests가 terminal 상태에서 closed_at 확인 | 정렬됨 | 유지 |
| `created_at`, `updated_at` | timestamp | `TIMESTAMP`, `updated_at` auto update | 동일 | `LocalDateTime` lifecycle | service 별도 검증 없음 | adoption DTO/tests는 zone 없는 timestamp 문자열을 기대 | DB는 정렬됨. 전체 API에서는 dog 쪽 `Instant`와 post 쪽 `LocalDateTime` 표현이 다름 | 의도된 API 표현이면 contract에 명시. 아니면 timestamp 타입 정렬 브랜치 필요 |
| active post uniqueness | DB constraint 없음 | DB unique/index 없음 | 동일 | repository derived query만 있음 | create/update에서 `DRAFT/OPEN/RESERVED` active post 존재 여부 검사 | tests가 active post blocking 확인 | service-level 정책만 있고 DB 동시성 보장은 없음 | 생산 전 동시 생성 방지가 필요하면 DB/index 또는 locking 정책을 별도 설계 |

## Findings

### P1. `adoption_posts.title` 길이 기준 불일치 - resolved

- resolution: active DBML, clean canonical SQL, API contract, entity, service validation, boundary tests, and runtime Flyway final schema(V1+V2)가 200자 제한으로 정렬되었다.
- runtime note: V1은 historical baseline으로 남아 있고, V2가 `title VARCHAR(200) NOT NULL`을 적용한다.

### P1. `adoption_posts.content` nullability / required 정책 불일치 - resolved

- resolution: active DBML, clean canonical SQL, API contract, entity, service validation, tests, and runtime Flyway final schema(V1+V2)가 required / `NOT NULL` 정책으로 정렬되었다.
- runtime note: V1은 historical baseline으로 남아 있고, V2가 `content TEXT NOT NULL`을 적용한다.

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

Follow-up note for this profile validation hardening branch:

- 이 branch는 profile update blank string 저장 이슈를 해결한다.
- profile update에 더 엄격한 `display_name` / `contact_phone` API-level validation을 추가한다.
- DB column length와 nullable schema는 변경하지 않는다.
- 명시적 `null` clearing과 omitted-field preservation 동작은 유지한다.

### P2. active adoption post uniqueness는 service-level만 존재

- evidence: canonical DB에는 dog별 active post uniqueness constraint가 없다. `AdoptionPostService`와 repository query가 `DRAFT/OPEN/RESERVED` active post 존재 여부를 검사하고 tests가 해당 정책을 확인한다.
- impact: 일반 API 흐름에서는 차단되지만, 동시 요청이나 DB 직접 쓰기에서는 같은 dog에 active post가 중복될 여지가 있다.
- recommended fix options: MVP 트래픽에서 service-level 정책으로 충분한지 결정한다. 생산에서 강한 보장이 필요하면 generated column/index, locking, transaction isolation, 또는 상태별 unique strategy를 설계한다.
- safest next branch type: combined.

## Recommended Next Work

1. docs/schema canonical alignment patch
   - 완료했다.
   - active DBML, documentation-only clean SQL, API contract, final spec은 current service policy 쪽으로 정렬되었다.

2. backend boundary tests / entity-service alignment check
   - 완료했다.
   - title max 200과 content non-blank boundary를 controller tests로 고정했다.
   - entity/runtime migration shape는 `CanonicalEntityShapeTest`로 고정했다.

3. Flyway runtime alignment
   - 완료했다.
   - 이미 적용된 V1은 수정하지 않고, V2 migration으로 title/content 제약을 정렬했다.

4. user profile blank string hardening
   - profile update blank string을 null로 정규화할지 거부할지 결정하고 Auth/User API tests를 추가한다.

5. adoption post uniqueness hardening
   - production 동시성 요구가 있으면 service-level active post check를 DB/index/locking 정책과 함께 보강한다.

6. dog registration auth fallback hardening
   - 이 hardening은 resolved 상태다.
   - Dog registration은 public API contract에서 JWT principal ownership을 사용한다.
   - DB schema 또는 migration change는 없다.
