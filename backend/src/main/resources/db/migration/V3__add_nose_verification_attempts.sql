-- =============================================================================
-- V3__add_nose_verification_attempts.sql
-- Introduce one-time pre-adoption nose verification attempts.
--
-- POST /api/nose-verifications stores the submitted nose image path and
-- duplicate-check result without creating a dog or adoption post.
-- POST /api/adoption-posts consumes one passed, unexpired attempt exactly once.
-- =============================================================================

CREATE TABLE nose_verification_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requested_by_user_id BIGINT NOT NULL,
    nose_image_path VARCHAR(500) NOT NULL,
    nose_image_mime_type VARCHAR(100) NULL,
    nose_image_file_size BIGINT NULL,
    nose_image_sha256 CHAR(64) NULL,
    result VARCHAR(40) NOT NULL,
    similarity_score DECIMAL(6, 5) NULL,
    candidate_dog_id CHAR(36) NULL,
    model VARCHAR(100) NULL,
    dimension INT NULL,
    failure_reason VARCHAR(1000) NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    consumed_by_post_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_nose_verification_attempts_nose_image_path (nose_image_path),
    KEY idx_nose_verification_attempts_requested_by_user_id (requested_by_user_id),
    KEY idx_nose_verification_attempts_result (result),
    KEY idx_nose_verification_attempts_expires_at (expires_at),
    KEY idx_nose_verification_attempts_consumed_by_post_id (consumed_by_post_id),
    KEY idx_nose_verification_attempts_candidate_dog_id (candidate_dog_id),
    CONSTRAINT fk_nose_verification_attempts_user
        FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_nose_verification_attempts_candidate_dog
        FOREIGN KEY (candidate_dog_id) REFERENCES dogs (id)
            ON DELETE SET NULL,
    CONSTRAINT fk_nose_verification_attempts_consumed_post
        FOREIGN KEY (consumed_by_post_id) REFERENCES adoption_posts (id),
    CONSTRAINT chk_nose_verification_attempts_result
        CHECK (result IN ('PENDING', 'PASSED', 'DUPLICATE_SUSPECTED', 'EMBED_FAILED', 'QDRANT_SEARCH_FAILED', 'QDRANT_UPSERT_FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
