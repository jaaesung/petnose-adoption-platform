package com.petnose.api.domain.entity;

import com.petnose.api.domain.enums.NoseVerificationStatus;
import com.petnose.api.domain.enums.VerificationResult;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "nose_verification_attempts")
public class NoseVerificationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "nose_image_path", nullable = false, length = 500)
    private String noseImagePath;

    @Column(name = "nose_image_mime_type", length = 100)
    private String noseImageMimeType;

    @Column(name = "nose_image_file_size")
    private Long noseImageFileSize;

    @Column(name = "nose_image_sha256", length = 64)
    private String noseImageSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VerificationResult result;

    @Column(name = "similarity_score", precision = 6, scale = 5)
    private BigDecimal similarityScore;

    @Column(name = "candidate_dog_id", length = 36)
    private String candidateDogId;

    @Column(length = 100)
    private String model;

    @Column
    private Integer dimension;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "consumed_by_post_id")
    private Long consumedByPostId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public NoseVerificationStatus getStatus() {
        if (result == VerificationResult.PASSED) {
            return NoseVerificationStatus.VERIFIED;
        }
        if (result == VerificationResult.DUPLICATE_SUSPECTED) {
            return NoseVerificationStatus.DUPLICATE_SUSPECTED;
        }
        if (result == VerificationResult.PENDING) {
            return NoseVerificationStatus.PENDING;
        }
        return NoseVerificationStatus.FAILED;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
