-- =============================================================================
-- V2__add_nose_registration_pipeline_schema.sql
-- 비문 등록 파이프라인(MVP) 지원을 위한 스키마 확장
-- =============================================================================

ALTER TABLE dogs
    ADD COLUMN description TEXT NULL AFTER birth_date,
    ADD COLUMN nose_verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' AFTER status,
    ADD COLUMN embedding_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' AFTER nose_verification_status,
    ADD COLUMN duplicate_candidate_dog_id CHAR(36) NULL AFTER embedding_status,
    ADD COLUMN duplicate_similarity_score DECIMAL(6, 5) NULL AFTER duplicate_candidate_dog_id,
    ADD COLUMN embedding_model VARCHAR(100) NULL AFTER duplicate_similarity_score,
    ADD COLUMN embedding_dimension INT NULL AFTER embedding_model,
    ADD COLUMN verified_at TIMESTAMP NULL AFTER embedding_dimension;

ALTER TABLE dogs
    ADD KEY idx_dogs_nose_verification_status (nose_verification_status),
    ADD KEY idx_dogs_embedding_status (embedding_status),
    ADD KEY idx_dogs_duplicate_candidate (duplicate_candidate_dog_id);

ALTER TABLE dogs
    ADD CONSTRAINT fk_dogs_duplicate_candidate
        FOREIGN KEY (duplicate_candidate_dog_id) REFERENCES dogs (id)
            ON DELETE SET NULL;

ALTER TABLE dog_images
    ADD COLUMN mime_type VARCHAR(100) NULL AFTER file_path,
    ADD COLUMN file_size BIGINT NULL AFTER mime_type,
    ADD COLUMN sha256 CHAR(64) NULL AFTER file_size;

CREATE TABLE IF NOT EXISTS verification_logs (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    dog_id           CHAR(36)      NOT NULL,
    dog_image_id     BIGINT        NOT NULL,
    requested_by_user_id BIGINT    NOT NULL,
    result           VARCHAR(40)   NOT NULL,
    similarity_score DECIMAL(6, 5) NULL,
    candidate_dog_id CHAR(36)      NULL,
    model            VARCHAR(100)  NULL,
    dimension        INT           NULL,
    failure_reason   VARCHAR(1000) NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_verification_logs_dog_id (dog_id),
    KEY idx_verification_logs_image_id (dog_image_id),
    KEY idx_verification_logs_user_id (requested_by_user_id),
    KEY idx_verification_logs_result (result),
    KEY idx_verification_logs_candidate (candidate_dog_id),
    CONSTRAINT fk_verification_logs_dog
        FOREIGN KEY (dog_id) REFERENCES dogs (id),
    CONSTRAINT fk_verification_logs_image
        FOREIGN KEY (dog_image_id) REFERENCES dog_images (id),
    CONSTRAINT fk_verification_logs_user
        FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_verification_logs_candidate
        FOREIGN KEY (candidate_dog_id) REFERENCES dogs (id)
            ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
