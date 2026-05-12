package com.petnose.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.domain.entity.AdoptionPost;
import com.petnose.api.domain.entity.Dog;
import com.petnose.api.domain.entity.DogImage;
import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.entity.VerificationLog;
import com.petnose.api.domain.enums.AdoptionPostStatus;
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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdoptionPostCreateControllerTest {

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
    void createOpenPostWhenOwnedRegisteredDogHasLatestPassedVerification() throws Exception {
        User user = saveUser("초코 보호자", true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        saveVerificationLog(user, dog, VerificationResult.PASSED);

        createPost(tokenFor(user), body(dog.getId(), "말티즈 초코 가족을 찾습니다", "활발하고 사람을 좋아하는 아이입니다.", "OPEN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.post_id").isNumber())
                .andExpect(jsonPath("$.dog_id").value(dog.getId()))
                .andExpect(jsonPath("$.title").value("말티즈 초코 가족을 찾습니다"))
                .andExpect(jsonPath("$.content").value("활발하고 사람을 좋아하는 아이입니다."))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.published_at").isNotEmpty())
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.author_user_id").doesNotExist());

        AdoptionPost saved = adoptionPostRepository.findAll().getFirst();
        assertThat(saved.getAuthorUserId()).isEqualTo(user.getId());
        assertThat(saved.getStatus()).isEqualTo(AdoptionPostStatus.OPEN);
        assertThat(saved.getPublishedAt()).isNotNull();
    }

    @Test
    void createDefaultsToDraftWhenStatusIsOmitted() throws Exception {
        User user = saveUser("초코 보호자", true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        saveVerificationLog(user, dog, VerificationResult.PASSED);

        createPost(tokenFor(user), body(dog.getId(), "임시 제목", "임시 내용", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.published_at").value(nullValue()));

        AdoptionPost saved = adoptionPostRepository.findAll().getFirst();
        assertThat(saved.getStatus()).isEqualTo(AdoptionPostStatus.DRAFT);
        assertThat(saved.getPublishedAt()).isNull();
    }

    @Test
    void createDraftPostWhenDraftStatusIsRequested() throws Exception {
        User user = saveUser("초코 보호자", true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        saveVerificationLog(user, dog, VerificationResult.PASSED);

        createPost(tokenFor(user), body(dog.getId(), "임시 제목", "임시 내용", "DRAFT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.published_at").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVED", "COMPLETED", "CLOSED", "UNKNOWN", "open", "Draft"})
    void rejectInvalidCreateStatus(String requestedStatus) throws Exception {
        User user = saveUser("초코 보호자", true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        saveVerificationLog(user, dog, VerificationResult.PASSED);

        createPost(tokenFor(user), body(dog.getId(), "제목", "내용", requestedStatus))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_POST_STATUS"))
                .andExpect(jsonPath("$.details.timestamp").exists());

        assertThat(adoptionPostRepository.count()).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectWhenDisplayNameIsMissing(String displayName) throws Exception {
        User user = saveUser(displayName, true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        saveVerificationLog(user, dog, VerificationResult.PASSED);

        createPost(tokenFor(user), body(dog.getId(), "제목", "내용", "OPEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("USER_PROFILE_REQUIRED"));

        assertThat(adoptionPostRepository.count()).isZero();
    }

    @Test
    void rejectWhenDogDoesNotExist() throws Exception {
        User user = saveUser("초코 보호자", true);

        createPost(tokenFor(user), body(UUID.randomUUID().toString(), "제목", "내용", "OPEN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("DOG_NOT_FOUND"));
    }

    @Test
    void rejectWhenDogOwnerDoesNotMatchCurrentUser() throws Exception {
        User owner = saveUser("원래 보호자", true);
        User currentUser = saveUser("다른 사용자", true);
        Dog dog = saveDog(owner, DogStatus.REGISTERED);
        saveVerificationLog(owner, dog, VerificationResult.PASSED);

        createPost(tokenFor(currentUser), body(dog.getId(), "제목", "내용", "OPEN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("DOG_OWNER_MISMATCH"));
    }

    @Test
    void rejectDuplicateSuspectedDogStatus() throws Exception {
        User user = saveUser("초코 보호자", true);
        Dog dog = saveDog(user, DogStatus.DUPLICATE_SUSPECTED);
        saveVerificationLog(user, dog, VerificationResult.PASSED);

        createPost(tokenFor(user), body(dog.getId(), "제목", "내용", "OPEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("DUPLICATE_DOG_CANNOT_BE_POSTED"));

        assertThat(adoptionPostRepository.count()).isZero();
    }

    @Test
    void rejectRegisteredDogWhenLatestVerificationResultIsNotPassed() throws Exception {
        User user = saveUser("초코 보호자", true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        saveVerificationLog(user, dog, VerificationResult.PENDING);

        createPost(tokenFor(user), body(dog.getId(), "제목", "내용", "OPEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("DOG_NOT_VERIFIED"));
    }

    @Test
    void rejectWhenLatestVerificationResultIsDuplicateEvenIfPastResultPassed() throws Exception {
        User user = saveUser("초코 보호자", true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        saveVerificationLog(user, dog, VerificationResult.PASSED);
        saveVerificationLog(user, dog, VerificationResult.DUPLICATE_SUSPECTED);

        createPost(tokenFor(user), body(dog.getId(), "제목", "내용", "OPEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("DUPLICATE_DOG_CANNOT_BE_POSTED"));
    }

    @Test
    void rejectWhenVerificationLogDoesNotExist() throws Exception {
        User user = saveUser("초코 보호자", true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);

        createPost(tokenFor(user), body(dog.getId(), "제목", "내용", "OPEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("DOG_NOT_VERIFIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "OPEN", "RESERVED"})
    void rejectWhenActivePostAlreadyExists(String existingStatus) throws Exception {
        User user = saveUser("초코 보호자", true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        saveVerificationLog(user, dog, VerificationResult.PASSED);
        saveAdoptionPost(user, dog, AdoptionPostStatus.valueOf(existingStatus));

        createPost(tokenFor(user), body(dog.getId(), "새 제목", "새 내용", "OPEN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("ACTIVE_POST_ALREADY_EXISTS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "COMPLETED"})
    void allowCreateWhenOnlyInactivePostExists(String existingStatus) throws Exception {
        User user = saveUser("초코 보호자", true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        saveVerificationLog(user, dog, VerificationResult.PASSED);
        saveAdoptionPost(user, dog, AdoptionPostStatus.valueOf(existingStatus));

        createPost(tokenFor(user), body(dog.getId(), "새 제목", "새 내용", "OPEN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(adoptionPostRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectWhenAuthorizationHeaderIsMissing() throws Exception {
        createPost(null, body(UUID.randomUUID().toString(), "제목", "내용", "OPEN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectTokenWithoutExpiration() throws Exception {
        createPost(tokenWithoutExpiration(1L), body(UUID.randomUUID().toString(), "제목", "내용", "OPEN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectInactiveCurrentUser() throws Exception {
        User user = saveUser("초코 보호자", false);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        saveVerificationLog(user, dog, VerificationResult.PASSED);

        createPost(tokenFor(user), body(dog.getId(), "제목", "내용", "OPEN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("USER_INACTIVE"));
    }

    private ResultActions createPost(String token, String body) throws Exception {
        var request = post("/api/adoption-posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private String body(String dogId, String title, String content, String status) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("dog_id", dogId);
        body.put("title", title);
        body.put("content", content);
        if (status != null) {
            body.put("status", status);
        }
        return objectMapper.writeValueAsString(body);
    }

    private User saveUser(String displayName, boolean active) {
        User user = new User();
        user.setEmail("user-%d@example.com".formatted(++sequence));
        user.setPasswordHash("hash");
        user.setRole(UserRole.USER);
        user.setDisplayName(displayName);
        user.setContactPhone("010-0000-0000");
        user.setRegion("Seoul");
        user.setActive(active);
        return userRepository.save(user);
    }

    private Dog saveDog(User owner, DogStatus status) {
        Dog dog = new Dog();
        dog.setId(UUID.randomUUID().toString());
        dog.setOwnerUserId(owner.getId());
        dog.setName("초코");
        dog.setBreed("Maltese");
        dog.setStatus(status);
        return dogRepository.save(dog);
    }

    private VerificationLog saveVerificationLog(User user, Dog dog, VerificationResult result) {
        DogImage image = new DogImage();
        image.setDogId(dog.getId());
        image.setImageType(DogImageType.NOSE);
        image.setFilePath("dogs/%s/%s-nose.jpg".formatted(dog.getId(), UUID.randomUUID()));
        image.setMimeType("image/jpeg");
        image.setFileSize(100L);
        image.setSha256(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        dogImageRepository.save(image);

        VerificationLog log = new VerificationLog();
        log.setDogId(dog.getId());
        log.setDogImageId(image.getId());
        log.setRequestedByUserId(user.getId());
        log.setResult(result);
        return verificationLogRepository.save(log);
    }

    private AdoptionPost saveAdoptionPost(User user, Dog dog, AdoptionPostStatus status) {
        AdoptionPost post = new AdoptionPost();
        post.setAuthorUserId(user.getId());
        post.setDogId(dog.getId());
        post.setTitle("기존 글");
        post.setContent("기존 내용");
        post.setStatus(status);
        if (status == AdoptionPostStatus.OPEN) {
            post.setPublishedAt(LocalDateTime.now());
        }
        return adoptionPostRepository.save(post);
    }

    private String tokenFor(User user) throws Exception {
        String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encodeJson(Map.of(
                "sub", user.getId().toString(),
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        ));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private String tokenWithoutExpiration(Long userId) throws Exception {
        String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encodeJson(Map.of("sub", userId.toString()));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
    }
}
