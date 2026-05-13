package com.petnose.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.domain.entity.AdoptionPost;
import com.petnose.api.domain.entity.Dog;
import com.petnose.api.domain.entity.DogImage;
import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.entity.VerificationLog;
import com.petnose.api.domain.enums.AdoptionPostStatus;
import com.petnose.api.domain.enums.DogGender;
import com.petnose.api.domain.enums.DogImageType;
import com.petnose.api.domain.enums.DogStatus;
import com.petnose.api.domain.enums.UserRole;
import com.petnose.api.domain.enums.VerificationResult;
import com.petnose.api.repository.AdoptionPostRepository;
import com.petnose.api.repository.DogImageRepository;
import com.petnose.api.repository.DogRepository;
import com.petnose.api.repository.UserRepository;
import com.petnose.api.repository.VerificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DogQueryControllerTest {

    private static final String JWT_SECRET = "test-petnose-jwt-secret-change-me-32bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DogRepository dogRepository;

    @Autowired
    private DogImageRepository dogImageRepository;

    @Autowired
    private VerificationLogRepository verificationLogRepository;

    @Autowired
    private AdoptionPostRepository adoptionPostRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int sequence;

    @BeforeEach
    void setUp() {
        adoptionPostRepository.deleteAll();
        verificationLogRepository.deleteAll();
        dogImageRepository.deleteAll();
        dogRepository.deleteAll();
        userRepository.deleteAll();
        sequence = 0;
    }

    @Test
    void myDogsReturnsOnlyCurrentOwnerDogsWithPageEnvelopeAndProfileUrl() throws Exception {
        User owner = saveUser("Owner", true);
        User other = saveUser("Other", true);
        Dog dog = saveDog(owner, "Bori", DogStatus.REGISTERED);
        Dog otherDog = saveDog(other, "OtherDog", DogStatus.REGISTERED);
        String profilePath = saveProfileImage(dog, "profile.jpg").getFilePath();
        saveVerificationLog(owner, dog, VerificationResult.PASSED);
        saveVerificationLog(other, otherDog, VerificationResult.PASSED);

        MvcResult result = mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_count").value(1))
                .andExpect(jsonPath("$.totalCount").doesNotExist())
                .andExpect(jsonPath("$.items[0].dog_id").value(dog.getId()))
                .andExpect(jsonPath("$.items[0].name").value("Bori"))
                .andExpect(jsonPath("$.items[0].breed").value("Maltese"))
                .andExpect(jsonPath("$.items[0].gender").value("MALE"))
                .andExpect(jsonPath("$.items[0].birth_date").value("2023-01-01"))
                .andExpect(jsonPath("$.items[0].status").value("REGISTERED"))
                .andExpect(jsonPath("$.items[0].verification_status").value("VERIFIED"))
                .andExpect(jsonPath("$.items[0].embedding_status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].profile_image_url").value("/files/" + profilePath))
                .andExpect(jsonPath("$.items[0].has_active_post").value(false))
                .andExpect(jsonPath("$.items[0].active_post_id").value(nullValue()))
                .andExpect(jsonPath("$.items[0].can_create_post").value(true))
                .andExpect(jsonPath("$.items[0].created_at").isNotEmpty())
                .andExpect(jsonPath("$.items[0].nose_image_url").doesNotExist())
                .andReturn();

        assertThat(responseBody(result))
                .doesNotContain(otherDog.getId(), "nose_image_url", "dogId", "totalCount");
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT",
            "OPEN",
            "RESERVED"
    })
    void myDogsMapsActivePostAndDisablesCreate(String statusValue) throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, "ActiveDog", DogStatus.REGISTERED);
        saveVerificationLog(owner, dog, VerificationResult.PASSED);
        AdoptionPost activePost = saveAdoptionPost(owner, dog, AdoptionPostStatus.valueOf(statusValue));

        mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].has_active_post").value(true))
                .andExpect(jsonPath("$.items[0].active_post_id").value(activePost.getId()))
                .andExpect(jsonPath("$.items[0].can_create_post").value(false));
    }

    @Test
    void myDogsMapsDuplicateLatestVerificationAndDisablesCreate() throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, "DuplicateDog", DogStatus.REGISTERED);
        DogImage noseImage = saveNoseImage(dog, "duplicate-nose.jpg");
        saveVerificationLog(owner, dog, noseImage, VerificationResult.PASSED);
        saveVerificationLog(owner, dog, noseImage, VerificationResult.DUPLICATE_SUSPECTED);

        mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].verification_status").value("DUPLICATE_SUSPECTED"))
                .andExpect(jsonPath("$.items[0].embedding_status").value("SKIPPED_DUPLICATE"))
                .andExpect(jsonPath("$.items[0].can_create_post").value(false));
    }

    @Test
    void myDogsDefaultsMissingLatestVerificationToPendingAndCannotCreatePost() throws Exception {
        User owner = saveUser("Owner", true);
        saveDog(owner, "PendingDog", DogStatus.REGISTERED);

        mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].verification_status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].embedding_status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].profile_image_url").value(nullValue()))
                .andExpect(jsonPath("$.items[0].can_create_post").value(false));
    }

    @Test
    void myDogsSelectsLatestVerificationByCreatedAtThenId() throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, "TieBreakerDog", DogStatus.REGISTERED);
        DogImage noseImage = saveNoseImage(dog, "tie-breaker-nose.jpg");
        VerificationLog oldPassed = saveVerificationLog(owner, dog, noseImage, VerificationResult.PASSED);
        VerificationLog lowerIdFailed = saveVerificationLog(owner, dog, noseImage, VerificationResult.EMBED_FAILED);
        VerificationLog higherIdUpsertFailed = saveVerificationLog(owner, dog, noseImage, VerificationResult.QDRANT_UPSERT_FAILED);

        setVerificationLogCreatedAt(oldPassed, Instant.parse("2026-01-01T00:00:00Z"));
        Instant latestTieTime = Instant.parse("2026-01-02T00:00:00Z");
        setVerificationLogCreatedAt(lowerIdFailed, latestTieTime);
        setVerificationLogCreatedAt(higherIdUpsertFailed, latestTieTime);

        mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].verification_status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].embedding_status").value("QDRANT_SYNC_FAILED"))
                .andExpect(jsonPath("$.items[0].can_create_post").value(false));
    }

    @Test
    void myDogsMapsQdrantUpsertFailureToSyncFailedEmbeddingStatus() throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, "QdrantDog", DogStatus.REGISTERED);
        saveVerificationLog(owner, dog, VerificationResult.QDRANT_UPSERT_FAILED);

        mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].verification_status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].embedding_status").value("QDRANT_SYNC_FAILED"))
                .andExpect(jsonPath("$.items[0].can_create_post").value(false));
    }

    @Test
    void myDogsSelectsLatestActivePostByCreatedAtThenId() throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, "LatestPostDog", DogStatus.REGISTERED);
        saveVerificationLog(owner, dog, VerificationResult.PASSED);
        AdoptionPost oldDraft = saveAdoptionPost(owner, dog, AdoptionPostStatus.DRAFT);
        AdoptionPost lowerIdOpen = saveAdoptionPost(owner, dog, AdoptionPostStatus.OPEN);
        AdoptionPost higherIdReserved = saveAdoptionPost(owner, dog, AdoptionPostStatus.RESERVED);

        setAdoptionPostCreatedAt(oldDraft, LocalDateTime.of(2026, 1, 1, 0, 0));
        LocalDateTime latestTieTime = LocalDateTime.of(2026, 1, 2, 0, 0);
        setAdoptionPostCreatedAt(lowerIdOpen, latestTieTime);
        setAdoptionPostCreatedAt(higherIdReserved, latestTieTime);

        mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].has_active_post").value(true))
                .andExpect(jsonPath("$.items[0].active_post_id").value(higherIdReserved.getId()))
                .andExpect(jsonPath("$.items[0].can_create_post").value(false));
    }

    @Test
    void myDogsIgnoresCompletedAndClosedPostsForActivePost() throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, "InactivePostDog", DogStatus.REGISTERED);
        saveVerificationLog(owner, dog, VerificationResult.PASSED);
        saveAdoptionPost(owner, dog, AdoptionPostStatus.COMPLETED);
        saveAdoptionPost(owner, dog, AdoptionPostStatus.CLOSED);

        mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].has_active_post").value(false))
                .andExpect(jsonPath("$.items[0].active_post_id").value(nullValue()))
                .andExpect(jsonPath("$.items[0].can_create_post").value(true));
    }

    @Test
    void myDogsDoesNotDuplicateFilesPrefixForProfileImage() throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, "PrefixedProfileDog", DogStatus.REGISTERED);
        saveImage(dog, DogImageType.PROFILE, "/files/dogs/%s/profile/prefixed.jpg".formatted(dog.getId()));
        saveVerificationLog(owner, dog, VerificationResult.PASSED);

        mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].profile_image_url").value("/files/dogs/%s/profile/prefixed.jpg".formatted(dog.getId())));
    }

    @Test
    void ownerDetailIncludesNoseImageAndComputedFields() throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, "OwnerDog", DogStatus.REGISTERED);
        DogImage noseImage = saveNoseImage(dog, "owner-nose.jpg");
        String profilePath = saveProfileImage(dog, "owner-profile.jpg").getFilePath();
        saveVerificationLog(owner, dog, noseImage, VerificationResult.PASSED);

        MvcResult result = mockMvc.perform(get("/api/dogs/{dog_id}", dog.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dog_id").value(dog.getId()))
                .andExpect(jsonPath("$.name").value("OwnerDog"))
                .andExpect(jsonPath("$.breed").value("Maltese"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.birth_date").value("2023-01-01"))
                .andExpect(jsonPath("$.description").value("Likes people and walks."))
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.verification_status").value("VERIFIED"))
                .andExpect(jsonPath("$.embedding_status").value("COMPLETED"))
                .andExpect(jsonPath("$.nose_image_url").value("/files/" + noseImage.getFilePath()))
                .andExpect(jsonPath("$.profile_image_url").value("/files/" + profilePath))
                .andExpect(jsonPath("$.has_active_post").value(false))
                .andExpect(jsonPath("$.active_post_id").value(nullValue()))
                .andExpect(jsonPath("$.can_create_post").value(true))
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.updated_at").isNotEmpty())
                .andReturn();

        assertThat(responseBody(result)).doesNotContain("dogId", "noseImageUrl");
    }

    @Test
    void publicDetailForNonOwnerOmitsNoseImageAndDisablesCreate() throws Exception {
        User owner = saveUser("Owner", true);
        User viewer = saveUser("Viewer", true);
        Dog dog = saveDog(owner, "PublicDog", DogStatus.REGISTERED);
        saveNoseImage(dog, "public-nose.jpg");
        String profilePath = saveProfileImage(dog, "public-profile.jpg").getFilePath();
        saveVerificationLog(owner, dog, VerificationResult.PASSED);
        AdoptionPost post = saveAdoptionPost(owner, dog, AdoptionPostStatus.OPEN);

        MvcResult result = mockMvc.perform(get("/api/dogs/{dog_id}", dog.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dog_id").value(dog.getId()))
                .andExpect(jsonPath("$.description").value("Likes people and walks."))
                .andExpect(jsonPath("$.verification_status").value("VERIFIED"))
                .andExpect(jsonPath("$.embedding_status").value("COMPLETED"))
                .andExpect(jsonPath("$.profile_image_url").value("/files/" + profilePath))
                .andExpect(jsonPath("$.has_active_post").value(true))
                .andExpect(jsonPath("$.active_post_id").value(post.getId()))
                .andExpect(jsonPath("$.can_create_post").value(false))
                .andExpect(jsonPath("$.nose_image_url").doesNotExist())
                .andReturn();

        assertThat(responseBody(result)).doesNotContain("nose_image_url", "noseImageUrl");
    }

    @ParameterizedTest
    @CsvSource({
            "OPEN",
            "RESERVED"
    })
    void publicDetailWithoutTokenAllowsOpenOrReservedPost(String statusValue) throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, statusValue + "PublicDog", DogStatus.REGISTERED);
        saveNoseImage(dog, "public-nose.jpg");
        saveVerificationLog(owner, dog, VerificationResult.PASSED);
        saveAdoptionPost(owner, dog, AdoptionPostStatus.valueOf(statusValue));

        MvcResult result = mockMvc.perform(get("/api/dogs/{dog_id}", dog.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dog_id").value(dog.getId()))
                .andExpect(jsonPath("$.has_active_post").value(true))
                .andExpect(jsonPath("$.active_post_id").isNumber())
                .andExpect(jsonPath("$.can_create_post").value(false))
                .andExpect(jsonPath("$.nose_image_url").doesNotExist())
                .andReturn();

        assertThat(responseBody(result)).doesNotContain("nose_image_url");
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT",
            "COMPLETED",
            "CLOSED"
    })
    void publicDetailRejectsDogWithOnlyNonPublicDogDetailPost(String statusValue) throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, statusValue + "OnlyDog", DogStatus.REGISTERED);
        saveVerificationLog(owner, dog, VerificationResult.PASSED);
        saveAdoptionPost(owner, dog, AdoptionPostStatus.valueOf(statusValue));

        mockMvc.perform(get("/api/dogs/{dog_id}", dog.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("DOG_NOT_ACCESSIBLE"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void publicDetailRejectsDogWithoutPublicPost() throws Exception {
        User owner = saveUser("Owner", true);
        Dog dog = saveDog(owner, "PrivateDog", DogStatus.REGISTERED);
        saveVerificationLog(owner, dog, VerificationResult.PASSED);

        mockMvc.perform(get("/api/dogs/{dog_id}", dog.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("DOG_NOT_ACCESSIBLE"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void detailReturnsDogNotFound() throws Exception {
        mockMvc.perform(get("/api/dogs/{dog_id}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("DOG_NOT_FOUND"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void myDogsRequiresValidBearerToken() throws Exception {
        mockMvc.perform(get("/api/dogs/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @CsvSource({
            "-1,20",
            "0,0",
            "0,101"
    })
    void myDogsRejectsInvalidPageRequest(int page, int size) throws Exception {
        User owner = saveUser("Owner", true);

        mockMvc.perform(get("/api/dogs/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner))
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_PAGE_REQUEST"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    private User saveUser(String displayName, boolean active) {
        User user = new User();
        user.setEmail("dog-query-%d@example.com".formatted(++sequence));
        user.setPasswordHash("hash");
        user.setRole(UserRole.USER);
        user.setDisplayName(displayName);
        user.setContactPhone("010-0000-0000");
        user.setRegion("Seoul");
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    private Dog saveDog(User owner, String name, DogStatus status) {
        Dog dog = new Dog();
        dog.setId(UUID.randomUUID().toString());
        dog.setOwnerUserId(owner.getId());
        dog.setName(name);
        dog.setBreed("Maltese");
        dog.setGender(DogGender.MALE);
        dog.setBirthDate(LocalDate.of(2023, 1, 1));
        dog.setDescription("Likes people and walks.");
        dog.setStatus(status);
        return dogRepository.saveAndFlush(dog);
    }

    private DogImage saveProfileImage(Dog dog, String filename) {
        return saveImage(dog, DogImageType.PROFILE, "dogs/%s/profile/%s".formatted(dog.getId(), filename));
    }

    private DogImage saveNoseImage(Dog dog, String filename) {
        return saveImage(dog, DogImageType.NOSE, "dogs/%s/nose/%s".formatted(dog.getId(), filename));
    }

    private DogImage saveImage(Dog dog, DogImageType imageType, String filePath) {
        DogImage image = new DogImage();
        image.setDogId(dog.getId());
        image.setImageType(imageType);
        image.setFilePath(filePath);
        image.setMimeType("image/jpeg");
        image.setFileSize(100L);
        image.setSha256(sha256());
        return dogImageRepository.saveAndFlush(image);
    }

    private VerificationLog saveVerificationLog(User user, Dog dog, VerificationResult result) {
        return saveVerificationLog(user, dog, saveNoseImage(dog, "%s.jpg".formatted(UUID.randomUUID())), result);
    }

    private VerificationLog saveVerificationLog(User user, Dog dog, DogImage image, VerificationResult result) {
        VerificationLog log = new VerificationLog();
        log.setDogId(dog.getId());
        log.setDogImageId(image.getId());
        log.setRequestedByUserId(user.getId());
        log.setResult(result);
        return verificationLogRepository.saveAndFlush(log);
    }

    private void setVerificationLogCreatedAt(VerificationLog log, Instant createdAt) {
        jdbcTemplate.update(
                "update verification_logs set created_at = ? where id = ?",
                Timestamp.from(createdAt),
                log.getId()
        );
    }

    private AdoptionPost saveAdoptionPost(User author, Dog dog, AdoptionPostStatus status) {
        AdoptionPost post = new AdoptionPost();
        post.setAuthorUserId(author.getId());
        post.setDogId(dog.getId());
        post.setTitle("Adoption post");
        post.setContent("Friendly dog looking for a family.");
        post.setStatus(status);
        if (status == AdoptionPostStatus.OPEN || status == AdoptionPostStatus.RESERVED || status == AdoptionPostStatus.COMPLETED) {
            post.setPublishedAt(LocalDateTime.now());
        }
        if (status == AdoptionPostStatus.CLOSED) {
            post.setClosedAt(LocalDateTime.now());
        }
        return adoptionPostRepository.saveAndFlush(post);
    }

    private void setAdoptionPostCreatedAt(AdoptionPost post, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "update adoption_posts set created_at = ? where id = ?",
                Timestamp.valueOf(createdAt),
                post.getId()
        );
    }

    private String tokenFor(User user) throws Exception {
        String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encodeJson(Map.of(
                "sub", user.getId().toString(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "iat", Instant.now().getEpochSecond(),
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        ));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sha256() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String responseBody(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
