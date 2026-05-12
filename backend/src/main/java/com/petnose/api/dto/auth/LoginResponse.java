package com.petnose.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.petnose.api.dto.user.UserMeResponse;

public record LoginResponse(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("token_type")
        String tokenType,
        @JsonProperty("expires_in")
        long expiresIn,
        @JsonProperty("user")
        UserMeResponse user
) {
}
