package com.petnose.api.exception;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.LinkedHashMap;
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
        return of(errorCode, message, null);
    }

    public static ErrorResponse of(String errorCode, String message, Map<String, Object> extraDetails) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (extraDetails != null) {
            details.putAll(extraDetails);
        }
        details.put("timestamp", Instant.now().toString());
        return new ErrorResponse(errorCode, message, details);
    }
}
