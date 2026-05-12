package com.petnose.api.domain.entity;

import com.petnose.api.domain.enums.DogImageType;
import com.petnose.api.domain.enums.DogStatus;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    void userProfileColumnLengthsMatchActiveCanonical() throws Exception {
        assertThat(columnLength("displayName")).isEqualTo(150);
        assertThat(columnLength("contactPhone")).isEqualTo(30);
        assertThat(columnLength("region")).isEqualTo(100);
    }

    @Test
    void flywayBaselineUsesActiveCanonicalUserProfileColumnLengths() throws Exception {
        String baseline = resourceText("db/migration/V1__baseline.sql");

        assertThat(baseline).contains(
                "display_name VARCHAR(150) NULL",
                "contact_phone VARCHAR(30) NULL",
                "region VARCHAR(100) NULL"
        );
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

    private static int columnLength(String fieldName) throws Exception {
        return User.class.getDeclaredField(fieldName)
                .getAnnotation(Column.class)
                .length();
    }

    private String resourceText(String path) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(inputStream).as(path).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String join(String first, String second) {
        return first + second;
    }
}
