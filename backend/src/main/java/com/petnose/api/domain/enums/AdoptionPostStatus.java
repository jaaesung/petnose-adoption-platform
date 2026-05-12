package com.petnose.api.domain.enums;

public enum AdoptionPostStatus {
    DRAFT,
    OPEN,
    RESERVED,
    COMPLETED,
    CLOSED;

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
        throw new com.petnose.api.exception.ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "INVALID_POST_STATUS",
                "생성 가능한 게시글 상태는 DRAFT 또는 OPEN입니다."
        );
    }
}
