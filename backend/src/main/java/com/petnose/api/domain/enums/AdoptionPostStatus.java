package com.petnose.api.domain.enums;

import com.petnose.api.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.EnumSet;
import java.util.Set;

public enum AdoptionPostStatus {
    DRAFT,
    OPEN,
    RESERVED,
    COMPLETED,
    CLOSED;

    private static final Set<AdoptionPostStatus> PUBLIC_STATUSES = EnumSet.of(OPEN, RESERVED, COMPLETED);

    public static AdoptionPostStatus fromCreateRequest(String value) {
        if (value == null) {
            return DRAFT;
        }
        if (DRAFT.name().equals(value)) {
            return DRAFT;
        }
        if (OPEN.name().equals(value)) {
            return OPEN;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_POST_STATUS",
                "생성 가능한 게시글 상태는 DRAFT 또는 OPEN입니다."
        );
    }

    public static AdoptionPostStatus fromPublicQuery(String value) {
        if (value == null) {
            return OPEN;
        }

        try {
            AdoptionPostStatus status = AdoptionPostStatus.valueOf(value);
            if (status.isPublicVisible()) {
                return status;
            }
        } catch (IllegalArgumentException ignored) {
            // handled below
        }

        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_POST_STATUS",
                "status must be one of OPEN, RESERVED, COMPLETED"
        );
    }

    public boolean isPublicVisible() {
        return PUBLIC_STATUSES.contains(this);
    }
}
