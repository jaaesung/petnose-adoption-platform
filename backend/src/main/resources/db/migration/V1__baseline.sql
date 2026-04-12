-- =============================================================================
-- V1__baseline.sql — PetNose 초기 스키마 baseline
-- =============================================================================
-- 이 파일은 Flyway 마이그레이션의 시작점입니다.
-- 이후 스키마 변경은 절대로 이 파일을 수정하지 말고,
-- V2__<description>.sql, V3__<description>.sql 형태로 새 파일을 추가하세요.
--
-- IF NOT EXISTS 를 사용하여 기존 DB 에서 Flyway 를 처음 적용할 때도 안전합니다.
-- 엔티티 설계: docs/TABLE_DRAFT.md 참고
-- =============================================================================

-- ── 사용자 ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    -- 역할: ADOPTER(입양희망자) | SHELTER(보호소) | ADMIN(관리자)
    role          VARCHAR(20)  NOT NULL DEFAULT 'ADOPTER',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── 보호소/분양자 프로필 (users 와 1:1) ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shelter_profiles (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    shelter_name VARCHAR(255),
    contact      VARCHAR(100),
    address      VARCHAR(500),
    verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shelter_user_id (user_id),
    CONSTRAINT fk_shelter_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── 강아지 개체 정보 ───────────────────────────────────────────────────────────
-- id 는 UUID 문자열 사용 — Qdrant point_id 와 맞추기 위함
CREATE TABLE IF NOT EXISTS dogs (
    id              CHAR(36)     NOT NULL,
    owner_user_id   BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    breed           VARCHAR(100),
    gender          VARCHAR(10),
    birth_date      DATE,
    -- 상태: REGISTERED(등록) | LOST(실종) | ADOPTED(입양완료)
    status          VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED',
    -- Qdrant 포인트 ID: null 이면 비문 임베딩 미완료
    qdrant_point_id VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dogs_owner (owner_user_id),
    KEY idx_dogs_status (status),
    CONSTRAINT fk_dog_owner
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── 강아지 이미지 (비문 + 일반 사진) ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dog_images (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    dog_id      CHAR(36)      NOT NULL,
    -- 이미지 유형: NOSE(비문) | PROFILE(프로필) | EXTRA(기타)
    image_type  VARCHAR(20)   NOT NULL,
    file_path   VARCHAR(1000) NOT NULL,
    uploaded_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dog_images_dog_id (dog_id),
    CONSTRAINT fk_dog_image_dog
        FOREIGN KEY (dog_id) REFERENCES dogs (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── 비문 인증 시도 이력 ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS auth_logs (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    requested_by     BIGINT         NOT NULL,
    target_dog_id    CHAR(36),
    matched_dog_id   CHAR(36),
    -- Qdrant 유사도 점수 (0.00000 ~ 1.00000)
    similarity_score DECIMAL(6, 5),
    -- 결과: MATCHED | NOT_FOUND | ERROR
    result           VARCHAR(20)    NOT NULL,
    created_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_auth_logs_user (requested_by),
    KEY idx_auth_logs_created (created_at),
    CONSTRAINT fk_auth_log_user
        FOREIGN KEY (requested_by) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── 입양 게시글 ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS adoption_posts (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    author_user_id BIGINT       NOT NULL,
    dog_id         CHAR(36)     NOT NULL,
    title          VARCHAR(255) NOT NULL,
    content        TEXT,
    -- 상태: OPEN(모집중) | CLOSED(마감) | ADOPTED(입양완료)
    status         VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_posts_dog_id (dog_id),
    KEY idx_posts_status (status),
    CONSTRAINT fk_post_author
        FOREIGN KEY (author_user_id) REFERENCES users (id),
    CONSTRAINT fk_post_dog
        FOREIGN KEY (dog_id) REFERENCES dogs (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── 신고 ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reports (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT      NOT NULL,
    -- 신고 대상 유형: POST | USER
    target_type      VARCHAR(20) NOT NULL,
    target_id        BIGINT      NOT NULL,
    reason           TEXT,
    -- 처리 상태: PENDING | RESOLVED
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_reports_reporter (reporter_user_id),
    CONSTRAINT fk_report_reporter
        FOREIGN KEY (reporter_user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
