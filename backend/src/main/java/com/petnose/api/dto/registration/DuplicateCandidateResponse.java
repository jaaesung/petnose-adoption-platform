package com.petnose.api.dto.registration;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DuplicateCandidateResponse(
        @JsonProperty("dog_id")
        String dogId,
        @JsonProperty("similarity_score")
        Double similarityScore,
        @JsonProperty("breed")
        String breed,
        @JsonProperty("nose_image_url")
        String noseImageUrl
) {
}
