package com.petnose.api.dto.nose;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.petnose.api.dto.registration.DuplicateCandidateResponse;

import java.time.Instant;

public record NoseVerificationResponse(
        @JsonProperty("nose_verification_id")
        Long noseVerificationId,
        @JsonProperty("registration_allowed")
        boolean registrationAllowed,
        @JsonProperty("status")
        String status,
        @JsonProperty("verification_status")
        String verificationStatus,
        @JsonProperty("embedding_status")
        String embeddingStatus,
        @JsonProperty("max_similarity_score")
        Double maxSimilarityScore,
        @JsonProperty("nose_image_url")
        String noseImageUrl,
        @JsonProperty("top_match")
        DuplicateCandidateResponse topMatch,
        @JsonProperty("expires_at")
        Instant expiresAt,
        @JsonProperty("message")
        String message
) {
    @JsonProperty("allowed")
    public boolean allowed() {
        return registrationAllowed;
    }

    @JsonProperty("decision")
    public String decision() {
        return status;
    }
}
