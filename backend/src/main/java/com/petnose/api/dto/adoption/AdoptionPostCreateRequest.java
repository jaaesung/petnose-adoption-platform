package com.petnose.api.dto.adoption;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdoptionPostCreateRequest(
        @JsonProperty("dog_id")
        String dogId,
        @JsonProperty("title")
        String title,
        @JsonProperty("content")
        String content,
        @JsonProperty("status")
        String status
) {
}
