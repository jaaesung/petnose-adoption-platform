package com.petnose.api.dto.registration;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DogRegisterResponse(
        @JsonProperty("dog_id")
        String dogId,
        @JsonProperty("registration_allowed")
        boolean registrationAllowed,
        @JsonProperty("status")
        String status,
        @JsonProperty("verification_status")
        String verificationStatus,
        @JsonProperty("embedding_status")
        String embeddingStatus,
        @JsonProperty("qdrant_point_id")
        String qdrantPointId,
        @JsonProperty("model")
        String model,
        @JsonProperty("dimension")
        Integer dimension,
        @JsonProperty("max_similarity_score")
        Double maxSimilarityScore,
        @JsonProperty("nose_image_url")
        String noseImageUrl,
        @JsonProperty("top_match")
        DuplicateCandidateResponse topMatch,
        @JsonProperty("message")
        String message
) {
}
