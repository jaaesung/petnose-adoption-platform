package com.petnose.api.domain.entity;

import com.petnose.api.domain.enums.DogGender;
import com.petnose.api.domain.enums.DogStatus;
import com.petnose.api.domain.enums.EmbeddingStatus;
import com.petnose.api.domain.enums.NoseVerificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "dogs")
public class Dog {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String breed;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DogGender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DogStatus status;

    @Column(name = "qdrant_point_id", length = 255)
    private String qdrantPointId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nose_verification_status", nullable = false, length = 30)
    private NoseVerificationStatus noseVerificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", nullable = false, length = 30)
    private EmbeddingStatus embeddingStatus;

    @Column(name = "duplicate_candidate_dog_id", length = 36)
    private String duplicateCandidateDogId;

    @Column(name = "duplicate_similarity_score", precision = 6, scale = 5)
    private BigDecimal duplicateSimilarityScore;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "verified_at")
    private Instant verifiedAt;

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
