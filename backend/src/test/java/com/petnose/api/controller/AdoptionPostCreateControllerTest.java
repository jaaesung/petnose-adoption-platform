package com.petnose.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.client.EmbedClient;
import com.petnose.api.client.QdrantDogVectorClient;
import com.petnose.api.domain.entity.AdoptionPost;
import com.petnose.api.domain.entity.Dog;
import com.petnose.api.domain.entity.DogImage;
import com.petnose.api.domain.entity.NoseVerificationAttempt;
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
import com.petnose.api.repository.NoseVerificationAttemptRepository;
import com.petnose.api.repository.UserRepository;
import com.petnose.api.repository.VerificationLogRepository;
import com.petnose.api.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @Autowired
    private NoseVerificationAttemptRepository noseVerificationAttemptRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @MockBean
    private EmbedClient embedClient;

    @MockBean
    private QdrantDogVectorClient qdrantDogVectorClient;

    private int sequence;

    @BeforeEach
    void setUp() {
        noseVerificationAttemptRepository.deleteAll();
        adoptionPostRepository.deleteAll();
        verificationLogRepository.deleteAll();
        dogImageRepository.deleteAll();
        dogRepository.deleteAll();
        userRepository.deleteAll();
        reset(embedClient, qdrantDogVectorClient);
        when(embedClient.embed(any(byte[].class), anyString(), anyString()))
                .thenReturn(new EmbedClient.EmbedResponse(List.of(0.1, 0.2, 0.3), 128, "dog-nose-identification2:s101_224"));
        sequence = 0;
    }

    @Test
    void createOpenPostConsumesPassedAttemptCreatesDogImagesAndExposesProfileImagePublicly() throws Exception {
        User user = saveUser("초코 보호자", true);
        NoseVerificationAttempt attempt = saveNoseVerificationAttempt(user, VerificationResult.PASSED, Instant.now().plusSeconds(3600), null);

        ResultActions result = createPost(tokenFor(user), attempt.getId(), "말티즈 초코 가족을 찾습니다", "활발하고 사람을 좋아하는 아이입니다.", "OPEN");

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.post_id").isNumber())
                .andExpect(jsonPath("$.dog_id").isString())
                .andExpect(jsonPath("$.title").value("말티즈 초코 가족을 찾습니다"))
                .andExpect(jsonPath("$.content").value("활발하고 사람을 좋아하는 아이입니다."))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.published_at").isNotEmpty())
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.author_user_id").doesNotExist());

        AdoptionPost saved = adoptionPostRepository.findAll().getFirst();
        Dog dog = dogRepository.findById(saved.getDogId()).orElseThrow();
        assertThat(saved.getAuthorUserId()).isEqualTo(user.getId());
        assertThat(saved.getStatus()).isEqualTo(AdoptionPostStatus.OPEN);
        assertThat(dog.getOwnerUserId()).isEqualTo(user.getId());
        assertThat(dog.getName()).isEqualTo("초코");
        assertThat(dog.getBreed()).isEqualTo("Maltese");
        assertThat(dog.getDescription()).isEqualTo("밝은 아이입니다.");
        assertThat(dog.getStatus()).isEqualTo(DogStatus.REGISTERED);

        List<DogImage> noseImages = dogImageRepository.findByDogIdInAndImageTypeOrderByDogIdAscUploadedAtDescIdDesc(
                Set.of(dog.getId()),
                DogImageType.NOSE
        );
        List<DogImage> profileImages = dogImageRepository.findByDogIdInAndImageTypeOrderByDogIdAscUploadedAtDescIdDesc(
                Set.of(dog.getId()),
                DogImageType.PROFILE
        );
        assertThat(noseImages).hasSize(1);
        assertThat(noseImages.getFirst().getFilePath()).isEqualTo(attempt.getNoseImagePath());
        assertThat(profileImages).hasSize(1);
        assertThat(profileImages.getFirst().getFilePath()).contains("/profile/");

        VerificationLog log = verificationLogRepository.findFirstByDogIdOrderByCreatedAtDescIdDesc(dog.getId()).orElseThrow();
        assertThat(log.getDogImageId()).isEqualTo(noseImages.getFirst().getId());
        assertThat(log.getResult()).isEqualTo(VerificationResult.PASSED);

        NoseVerificationAttempt consumed = noseVerificationAttemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(consumed.getConsumedAt()).isNotNull();
        assertThat(consumed.getConsumedByPostId()).isEqualTo(saved.getId());

        ArgumentCaptor<String> pointIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(qdrantDogVectorClient).upsert(pointIdCaptor.capture(), anyList(), payloadCaptor.capture());
        assertThat(pointIdCaptor.getValue()).isEqualTo(dog.getId());
        assertThat(payloadCaptor.getValue()).containsEntry("dog_id", dog.getId());

        String profileUrl = "/files/" + profileImages.getFirst().getFilePath();
        mockMvc.perform(get("/api/adoption-posts")
                        .param("status", "OPEN")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].profile_image_url").value(profileUrl))
                .andExpect(jsonPath("$.items[0].nose_image_url").doesNotExist());

        mockMvc.perform(get("/api/adoption-posts/{post_id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile_image_url").value(profileUrl))
                .andExpect(jsonPath("$.nose_image_url").doesNotExist());
    }

    @Test
    void createRequiresProfileImage() throws Exception {
        User user = saveUser("초코 보호자", true);
        NoseVerificationAttempt attempt = saveNoseVerificationAttempt(user, VerificationResult.PASSED, Instant.now().plusSeconds(3600), null);

        createPost(tokenFor(user), attempt.getId(), "제목", "내용", "OPEN", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("PROFILE_IMAGE_REQUIRED"))
                .andExpect(jsonPath("$.details").value(nullValue()));

        assertThat(adoptionPostRepository.count()).isZero();
        assertThat(dogRepository.count()).isZero();
    }

    @Test
    void createDefaultsToDraftWhenStatusIsOmitted() throws Exception {
        User user = saveUser("초코 보호자", true);
        NoseVerificationAttempt attempt = saveNoseVerificationAttempt(user, VerificationResult.PASSED, Instant.now().plusSeconds(3600), null);

        createPost(tokenFor(user), attempt.getId(), "임시 제목", "임시 내용", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.published_at").value(nullValue()));

        AdoptionPost saved = adoptionPostRepository.findAll().getFirst();
        assertThat(saved.getStatus()).isEqualTo(AdoptionPostStatus.DRAFT);
        assertThat(saved.getPublishedAt()).isNull();
    }

    @Test
    void rejectDuplicateSuspectedNoseVerificationAttempt() throws Exception {
        User user = saveUser("초코 보호자", true);
        NoseVerificationAttempt attempt = saveNoseVerificationAttempt(user, VerificationResult.DUPLICATE_SUSPECTED, Instant.now().plusSeconds(3600), null);

        createPost(tokenFor(user), attempt.getId(), "제목", "내용", "OPEN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("DUPLICATE_DOG_CANNOT_BE_POSTED"));

        assertThat(adoptionPostRepository.count()).isZero();
        assertThat(dogRepository.count()).isZero();
    }

    @Test
    void rejectWhenNoseVerificationWasAlreadyConsumed() throws Exception {
        User user = saveUser("초코 보호자", true);
        NoseVerificationAttempt attempt = saveNoseVerificationAttempt(user, VerificationResult.PASSED, Instant.now().plusSeconds(3600), Instant.now());

        createPost(tokenFor(user), attempt.getId(), "제목", "내용", "OPEN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("NOSE_VERIFICATION_ALREADY_CONSUMED"));
    }

    @Test
    void rejectWhenNoseVerificationBelongsToOtherUser() throws Exception {
        User owner = saveUser("원래 보호자", true);
        User currentUser = saveUser("다른 사용자", true);
        NoseVerificationAttempt attempt = saveNoseVerificationAttempt(owner, VerificationResult.PASSED, Instant.now().plusSeconds(3600), null);

        createPost(tokenFor(currentUser), attempt.getId(), "제목", "내용", "OPEN")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("NOSE_VERIFICATION_OWNER_MISMATCH"));
    }

    @Test
    void rejectWhenNoseVerificationExpired() throws Exception {
        User user = saveUser("초코 보호자", true);
        NoseVerificationAttempt attempt = saveNoseVerificationAttempt(user, VerificationResult.PASSED, Instant.now().minusSeconds(1), null);

        createPost(tokenFor(user), attempt.getId(), "제목", "내용", "OPEN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("NOSE_VERIFICATION_EXPIRED"));
    }

    @Test
    void rejectWhenAuthorizationHeaderIsMissing() throws Exception {
        createPost(null, 999L, "제목", "내용", "OPEN")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectInactiveCurrentUser() throws Exception {
        User user = saveUser("초코 보호자", false);
        NoseVerificationAttempt attempt = saveNoseVerificationAttempt(user, VerificationResult.PASSED, Instant.now().plusSeconds(3600), null);

        createPost(tokenFor(user), attempt.getId(), "제목", "내용", "OPEN")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("USER_INACTIVE"));
    }

    private ResultActions createPost(String token, Long noseVerificationId, String title, String content, String status) throws Exception {
        return createPost(token, noseVerificationId, title, content, status, profileImage());
    }

    private ResultActions createPost(
            String token,
            Long noseVerificationId,
            String title,
            String content,
            String status,
            MockMultipartFile profileImage
    ) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/adoption-posts");
        request.contentType(MediaType.MULTIPART_FORM_DATA);
        if (noseVerificationId != null) {
            request.param("nose_verification_id", String.valueOf(noseVerificationId));
        }
        request.param("dog_name", "초코");
        request.param("breed", "Maltese");
        request.param("gender", "UNKNOWN");
        request.param("dog_description", "밝은 아이입니다.");
        if (title != null) {
            request.param("title", title);
        }
        if (content != null) {
            request.param("content", content);
        }
        if (status != null) {
            request.param("status", status);
        }
        if (profileImage != null) {
            request.file(profileImage);
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private User saveUser(String displayName, boolean active) {
        User user = new User();
        user.setEmail("user-%d@example.com".formatted(++sequence));
        user.setPasswordHash("hash");
        user.setRole(UserRole.USER);
        user.setDisplayName(displayName);
        user.setContactPhone("01012341234");
        user.setRegion("Seoul");
        user.setActive(active);
        return userRepository.save(user);
    }

    private NoseVerificationAttempt saveNoseVerificationAttempt(
            User user,
            VerificationResult result,
            Instant expiresAt,
            Instant consumedAt
    ) {
        FileStorageService.StoredFile stored = fileStorageService.storeNoseVerificationImage(
                UUID.randomUUID().toString(),
                noseImage()
        );

        NoseVerificationAttempt attempt = new NoseVerificationAttempt();
        attempt.setRequestedByUserId(user.getId());
        attempt.setNoseImagePath(stored.relativePath());
        attempt.setNoseImageMimeType(stored.mimeType());
        attempt.setNoseImageFileSize(stored.fileSize());
        attempt.setNoseImageSha256(stored.sha256());
        attempt.setResult(result);
        attempt.setCandidateDogId(result == VerificationResult.DUPLICATE_SUSPECTED ? "existing-dog" : null);
        attempt.setModel("dog-nose-identification2:s101_224");
        attempt.setDimension(128);
        attempt.setExpiresAt(expiresAt);
        attempt.setConsumedAt(consumedAt);
        attempt.setConsumedByPostId(consumedAt == null ? null : 999L);
        return noseVerificationAttemptRepository.save(attempt);
    }

    private static MockMultipartFile noseImage() {
        return new MockMultipartFile("nose_image", "nose.jpg", MediaType.IMAGE_JPEG_VALUE, "nose-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile profileImage() {
        return new MockMultipartFile("profile_image", "profile.jpg", MediaType.IMAGE_JPEG_VALUE, "profile-bytes".getBytes(StandardCharsets.UTF_8));
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

    private String encodeJson(Map<String, Object> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
    }
}
