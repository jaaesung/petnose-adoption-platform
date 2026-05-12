package com.petnose.api.domain.entity;

import com.petnose.api.domain.enums.DogImageType;
import com.petnose.api.domain.enums.DogStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalEntityShapeTest {

    @Test
    void dogEntityDoesNotExposeRemovedRegistrationSnapshotFields() {
        Set<String> fields = declaredFieldNames(Dog.class);

        assertThat(fields).doesNotContain(
                join("qdrant", "PointId"),
                join("nose", "VerificationStatus"),
                join("embedding", "Status"),
                join("duplicate", "CandidateDogId"),
                join("duplicate", "SimilarityScore"),
                join("embedding", "Model"),
                join("embedding", "Dimension"),
                join("verified", "At")
        );
    }

    @Test
    void userEntityMatchesSimplifiedProfileShape() {
        Set<String> fields = declaredFieldNames(User.class);

        assertThat(fields).contains("displayName", "contactPhone", "region", "createdAt");
        assertThat(fields).doesNotContain(join("updated", "At"), join("last", "LoginAt"));
    }

    @Test
    void verificationLogEntityDoesNotExposeUpdatedAtOrEmbeddingVector() {
        Set<String> fields = declaredFieldNames(VerificationLog.class);

        assertThat(fields).contains("result", "similarityScore", "candidateDogId", "model", "dimension", "failureReason");
        assertThat(fields).doesNotContain(join("updated", "At"), "embedding", join("embedding", "Vector"), "vector");
    }

    @Test
    void canonicalEnumsMatchSimplifiedDbmlV2() {
        assertThat(DogStatus.values()).containsExactly(
                DogStatus.PENDING,
                DogStatus.REGISTERED,
                DogStatus.DUPLICATE_SUSPECTED,
                DogStatus.REJECTED,
                DogStatus.ADOPTED,
                DogStatus.INACTIVE
        );
        assertThat(DogImageType.values()).containsExactly(DogImageType.NOSE, DogImageType.PROFILE);
    }

    @Test
    void publisherProfileEntityIsNotActive() {
        assertThatThrownBy(() -> Class.forName("com.petnose.api.domain.entity." + join("Publisher", "Profile")))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private static Set<String> declaredFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());
    }

    private static String join(String first, String second) {
        return first + second;
    }
}
