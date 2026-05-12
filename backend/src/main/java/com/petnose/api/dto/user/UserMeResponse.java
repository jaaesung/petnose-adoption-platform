package com.petnose.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.petnose.api.domain.entity.User;

public record UserMeResponse(
        @JsonProperty("user_id")
        Long userId,
        @JsonProperty("email")
        String email,
        @JsonProperty("role")
        String role,
        @JsonProperty("display_name")
        String displayName,
        @JsonProperty("contact_phone")
        String contactPhone,
        @JsonProperty("region")
        String region,
        @JsonProperty("is_active")
        boolean active
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getDisplayName(),
                user.getContactPhone(),
                user.getRegion(),
                user.isActive()
        );
    }
}
