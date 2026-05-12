package com.petnose.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @JsonProperty("email")
        @NotBlank
        @Size(max = 255)
        String email,
        @JsonProperty("password")
        @NotBlank
        @Size(min = 8, max = 255)
        String password,
        @JsonProperty("display_name")
        @Size(max = 100)
        String displayName,
        @JsonProperty("contact_phone")
        @Size(max = 50)
        String contactPhone,
        @JsonProperty("region")
        @Size(max = 100)
        String region
) {
}
