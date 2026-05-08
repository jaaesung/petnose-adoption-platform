package com.petnose.api.domain.enums;

import java.util.Locale;

public enum DogGender {
    MALE,
    FEMALE,
    UNKNOWN;

    public static DogGender from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("gender는 필수입니다.");
        }
        try {
            return DogGender.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("gender는 MALE/FEMALE/UNKNOWN 중 하나여야 합니다.");
        }
    }
}
