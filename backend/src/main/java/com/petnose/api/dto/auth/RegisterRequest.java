package com.petnose.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
        @NotBlank
        @Size(max = 150)
        String displayName,
        @JsonProperty("contact_phone")
        @NotBlank
        @Pattern(regexp = "^010[0-9]{8}$")
        @Size(max = 30)
        String contactPhone,
        @JsonProperty("region")
        @NotBlank
        @Size(max = 100)
        String region
) {
}
