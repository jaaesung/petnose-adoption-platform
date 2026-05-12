package com.petnose.api.dto.adoption;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AdoptionPostListResponse(
        @JsonProperty("items")
        List<AdoptionPostListItemResponse> items,
        @JsonProperty("page")
        int page,
        @JsonProperty("size")
        int size,
        @JsonProperty("total_count")
        long totalCount
) {
}
