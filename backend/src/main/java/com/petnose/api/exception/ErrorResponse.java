package com.petnose.api.exception;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        @JsonProperty("error_code")
        String errorCode,
        @JsonProperty("message")
        String message,
        @JsonProperty("details")
        Map<String, Object> details
) {
    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, Map.of("timestamp", Instant.now().toString()));
    }
}
