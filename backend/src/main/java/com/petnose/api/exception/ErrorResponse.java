package com.petnose.api.exception;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ErrorResponse(
        @JsonProperty("error")
        String error,
        @JsonProperty("message")
        String message,
        @JsonProperty("timestamp")
        Instant timestamp
) {
}
