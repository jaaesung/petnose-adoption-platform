package com.petnose.api.dto.dog;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

public record DogOwnerDetailResponse(
        @JsonProperty("dog_id")
        String dogId,
        @JsonProperty("name")
        String name,
        @JsonProperty("breed")
        String breed,
        @JsonProperty("gender")
        String gender,
        @JsonProperty("birth_date")
        LocalDate birthDate,
        @JsonProperty("description")
        String description,
        @JsonProperty("status")
        String status,
        @JsonProperty("verification_status")
        String verificationStatus,
        @JsonProperty("embedding_status")
        String embeddingStatus,
        @JsonProperty("nose_image_url")
        String noseImageUrl,
        @JsonProperty("profile_image_url")
        String profileImageUrl,
        @JsonProperty("has_active_post")
        boolean hasActivePost,
        @JsonProperty("active_post_id")
        Long activePostId,
        @JsonProperty("can_create_post")
        boolean canCreatePost,
        @JsonProperty("created_at")
        Instant createdAt,
        @JsonProperty("updated_at")
        Instant updatedAt
) {
}
