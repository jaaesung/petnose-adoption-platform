package com.petnose.api.dto.registration;

public record QdrantSearchResult(
        String pointId,
        String dogId,
        double score,
        String breed,
        String noseImagePath
) {
}
