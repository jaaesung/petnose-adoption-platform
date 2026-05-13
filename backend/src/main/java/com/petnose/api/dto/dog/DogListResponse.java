package com.petnose.api.dto.dog;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DogListResponse(
        @JsonProperty("items")
        List<DogListItemResponse> items,
        @JsonProperty("page")
        int page,
        @JsonProperty("size")
        int size,
        @JsonProperty("total_count")
        long totalCount
) {
}
