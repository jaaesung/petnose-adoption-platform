package com.petnose.api.domain.entity;

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
@Table(name = "verification_logs")
public class VerificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dog_id", nullable = false, length = 36)
    private String dogId;

    @Column(name = "dog_image_id", nullable = false)
    private Long dogImageId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 40)
    private VerificationResult result;

    @Column(name = "similarity_score", precision = 6, scale = 5)
    private BigDecimal similarityScore;

    @Column(name = "candidate_dog_id", length = 36)
    private String candidateDogId;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "dimension")
    private Integer dimension;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
