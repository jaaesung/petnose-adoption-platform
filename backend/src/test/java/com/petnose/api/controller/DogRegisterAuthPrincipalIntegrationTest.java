package com.petnose.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.client.EmbedClient;
import com.petnose.api.client.QdrantDogVectorClient;
import com.petnose.api.domain.entity.Dog;
import com.petnose.api.domain.entity.DogImage;
import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.entity.VerificationLog;
import com.petnose.api.domain.enums.DogStatus;
import com.petnose.api.domain.enums.VerificationResult;
import com.petnose.api.dto.registration.QdrantSearchResult;
import com.petnose.api.repository.DogImageRepository;
import com.petnose.api.repository.DogRepository;
import com.petnose.api.repository.UserRepository;
import com.petnose.api.repository.VerificationLogRepository;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DogRegisterAuthPrincipalIntegrationTest {

    private static final String TEST_JWT_SECRET = "test-petnose-jwt-secret-change-me-32bytes";

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

    @MockBean
    private EmbedClient embedClient;

    @MockBean
    private QdrantDogVectorClient qdrantDogVectorClient;

    @BeforeEach
    void setUp() {
        when(embedClient.embed(any(byte[].class), anyString(), anyString()))
                .thenReturn(new EmbedClient.EmbedResponse(List.of(0.1, 0.2, 0.3), 128, "test-model"));
        when(qdrantDogVectorClient.search(anyList()))
                .thenReturn(List.of(new QdrantSearchResult("other-point", "other-dog", 0.12345, "Jindo", "dogs/other/nose.jpg")));
    }

    @Test
    void dogRegisterPrincipalOnlySuccessCreatesRowsAndUpsertsDogIdPoint() throws Exception {
        registerUser("principal-dog@example.com");
        User user = userRepository.findByEmail("principal-dog@example.com").orElseThrow();
        String accessToken = loginAccessToken("principal-dog@example.com");

        MvcResult result = mockMvc.perform(validDogMultipart(null)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registration_allowed").value(true))
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.verification_status").value("VERIFIED"))
                .andExpect(jsonPath("$.embedding_status").value("COMPLETED"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.dimension").value(128))
                .andExpect(jsonPath("$.max_similarity_score").value(0.12345))
                .andExpect(jsonPath("$.top_match").doesNotExist())
                .andReturn();

        Dog dog = onlyDog();
        assertThat(dog.getOwnerUserId()).isEqualTo(user.getId());
        assertThat(dog.getStatus()).isEqualTo(DogStatus.REGISTERED);

        DogImage noseImage = onlyDogImage();
        assertThat(noseImage.getDogId()).isEqualTo(dog.getId());
        assertThat(noseImage.getFilePath()).startsWith("dogs/").doesNotContain("/tmp", "\\tmp");

        VerificationLog verificationLog = onlyVerificationLog();
        assertThat(verificationLog.getDogId()).isEqualTo(dog.getId());
        assertThat(verificationLog.getDogImageId()).isEqualTo(noseImage.getId());
        assertThat(verificationLog.getRequestedByUserId()).isEqualTo(user.getId());
        assertThat(verificationLog.getResult()).isEqualTo(VerificationResult.PASSED);

        String responseDogId = objectMapper.readTree(responseBody(result)).get("dog_id").asText();
        String qdrantPointId = objectMapper.readTree(responseBody(result)).get("qdrant_point_id").asText();
        assertThat(responseDogId).isEqualTo(dog.getId());
        assertThat(qdrantPointId).isEqualTo(dog.getId());

        ArgumentCaptor<String> pointIdCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(qdrantDogVectorClient).upsert(pointIdCaptor.capture(), anyList(), payloadCaptor.capture());
        assertThat(pointIdCaptor.getValue()).isEqualTo(dog.getId());
        assertThat(payloadCaptor.getValue())
                .containsEntry("dog_id", dog.getId())
                .containsEntry("user_id", user.getId())
                .containsEntry("nose_image_path", noseImage.getFilePath());
    }

    @Test
    void dogRegisterUsesJwtPrincipalBeforeMismatchedFormUserId() throws Exception {
        registerUser("dog-owner-a@example.com");
        registerUser("dog-owner-b@example.com");
        User userA = userRepository.findByEmail("dog-owner-a@example.com").orElseThrow();
        User userB = userRepository.findByEmail("dog-owner-b@example.com").orElseThrow();
        String accessToken = loginAccessToken("dog-owner-a@example.com");

        mockMvc.perform(validDogMultipart(userB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registration_allowed").value(true));

        assertThat(onlyDog().getOwnerUserId()).isEqualTo(userA.getId());
    }

    @Test
    void dogRegisterAllowsOmittedUserIdWhenBearerTokenIsValid() throws Exception {
        registerUser("dog-token-only@example.com");
        User user = userRepository.findByEmail("dog-token-only@example.com").orElseThrow();
        String accessToken = loginAccessToken("dog-token-only@example.com");

        mockMvc.perform(validDogMultipart(null)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registration_allowed").value(true));

        assertThat(onlyDog().getOwnerUserId()).isEqualTo(user.getId());
    }

    @Test
    void dogRegisterRejectsInvalidTokenWithoutLegacyFallback() throws Exception {
        registerUser("dog-invalid-token@example.com");
        User fallbackUser = userRepository.findByEmail("dog-invalid-token@example.com").orElseThrow();

        mockMvc.perform(validDogMultipart(fallbackUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        assertPipelineRowsAbsent();
        verifyPipelineNotStarted();
    }

    @Test
    void dogRegisterRejectsMalformedAuthorizationWithoutLegacyFallback() throws Exception {
        registerUser("dog-malformed-auth@example.com");
        User fallbackUser = userRepository.findByEmail("dog-malformed-auth@example.com").orElseThrow();

        mockMvc.perform(validDogMultipart(fallbackUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Basic invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        assertPipelineRowsAbsent();
        verifyPipelineNotStarted();
    }

    @Test
    void dogRegisterRejectsExpiredTokenWithoutLegacyFallback() throws Exception {
        registerUser("dog-expired-token@example.com");
        User fallbackUser = userRepository.findByEmail("dog-expired-token@example.com").orElseThrow();
        String expiredToken = signedToken(fallbackUser.getId(), Instant.now().minusSeconds(60).getEpochSecond());

        mockMvc.perform(validDogMultipart(fallbackUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        assertPipelineRowsAbsent();
        verifyPipelineNotStarted();
    }

    @Test
    void dogRegisterRejectsMissingUserFromTokenBeforePipelineStarts() throws Exception {
        String tokenForMissingUser = signedToken(999_999L, Instant.now().plusSeconds(3600).getEpochSecond());

        mockMvc.perform(validDogMultipart(null)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenForMissingUser))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_NOT_FOUND"));

        assertPipelineRowsAbsent();
        verifyPipelineNotStarted();
    }

    @Test
    void dogRegisterRejectsInactiveUserBeforePipelineStarts() throws Exception {
        registerUser("dog-inactive@example.com");
        String accessToken = loginAccessToken("dog-inactive@example.com");
        User user = userRepository.findByEmail("dog-inactive@example.com").orElseThrow();
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(validDogMultipart(null)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("USER_INACTIVE"));

        assertPipelineRowsAbsent();
        verifyPipelineNotStarted();
    }

    @Test
    void dogRegisterRejectsMissingAuthorizationBeforePipelineStarts() throws Exception {
        mockMvc.perform(validDogMultipart(null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        assertPipelineRowsAbsent();
        verifyPipelineNotStarted();
    }

    @Test
    void dogRegisterRejectsLegacyUserIdOnlyFallbackBeforePipelineStarts() throws Exception {
        registerUser("dog-user-id-only@example.com");
        User fallbackUser = userRepository.findByEmail("dog-user-id-only@example.com").orElseThrow();

        mockMvc.perform(validDogMultipart(fallbackUser.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        assertPipelineRowsAbsent();
        verifyPipelineNotStarted();
    }

    @Test
    void dogRegisterValidationStillRunsAfterAuthorizationPasses() throws Exception {
        registerUser("dog-validation@example.com");
        String accessToken = loginAccessToken("dog-validation@example.com");

        mockMvc.perform(dogMultipart(null, noseImage("nose.jpg"), null, "Jindo", "MALE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("NAME_REQUIRED"));

        mockMvc.perform(dogMultipart(null, noseImage("nose.jpg"), "Bori", null, "MALE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("BREED_REQUIRED"));

        mockMvc.perform(dogMultipart(null, null, "Bori", "Jindo", "MALE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("NOSE_IMAGE_REQUIRED"));

        mockMvc.perform(dogMultipart(null, noseImage("nose.jpg"), "Bori", "Jindo", "INVALID")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));

        assertPipelineRowsAbsent();
        verifyPipelineNotStarted();
    }

    @Test
    void dogRegisterDuplicateSuspectedBehaviorUnchangedWithBearerToken() throws Exception {
        registerUser("dog-duplicate@example.com");
        String accessToken = loginAccessToken("dog-duplicate@example.com");
        when(qdrantDogVectorClient.search(anyList()))
                .thenReturn(List.of(new QdrantSearchResult("candidate-point", "candidate-dog", 0.98765, "Maltese", "dogs/candidate/nose.jpg")));

        MvcResult result = mockMvc.perform(validDogMultipart(null)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration_allowed").value(false))
                .andExpect(jsonPath("$.status").value("DUPLICATE_SUSPECTED"))
                .andExpect(jsonPath("$.verification_status").value("DUPLICATE_SUSPECTED"))
                .andExpect(jsonPath("$.embedding_status").value("SKIPPED_DUPLICATE"))
                .andExpect(jsonPath("$.qdrant_point_id").doesNotExist())
                .andExpect(jsonPath("$.top_match.dog_id").value("candidate-dog"))
                .andExpect(jsonPath("$.top_match.similarity_score").value(0.98765))
                .andExpect(jsonPath("$.top_match.breed").value("Maltese"))
                .andExpect(jsonPath("$.top_match.nose_image_url").doesNotExist())
                .andReturn();

        assertThat(objectMapper.readTree(responseBody(result)).get("qdrant_point_id").isNull()).isTrue();
        assertThat(onlyDog().getStatus()).isEqualTo(DogStatus.DUPLICATE_SUSPECTED);
        assertThat(dogImageRepository.count()).isEqualTo(1);
        assertThat(onlyVerificationLog().getResult()).isEqualTo(VerificationResult.DUPLICATE_SUSPECTED);
        verify(qdrantDogVectorClient, never()).upsert(anyString(), anyList(), any());
    }

    private Dog onlyDog() {
        List<Dog> dogs = dogRepository.findAll();
        assertThat(dogs).hasSize(1);
        return dogs.get(0);
    }

    private DogImage onlyDogImage() {
        List<DogImage> images = dogImageRepository.findAll();
        assertThat(images).hasSize(1);
        return images.get(0);
    }

    private VerificationLog onlyVerificationLog() {
        List<VerificationLog> logs = verificationLogRepository.findAll();
        assertThat(logs).hasSize(1);
        return logs.get(0);
    }

    private MockHttpServletRequestBuilder validDogMultipart(Long userId) {
        return dogMultipart(userId, noseImage("nose.jpg"), "Bori", "Jindo", "MALE")
                .param("birth_date", "2024-01-01")
                .param("description", "friendly");
    }

    private MockHttpServletRequestBuilder dogMultipart(
            Long userId,
            MockMultipartFile noseImage,
            String name,
            String breed,
            String gender
    ) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/dogs/register");
        if (noseImage != null) {
            builder.file(noseImage);
        }
        if (name != null) {
            builder.param("name", name);
        }
        if (breed != null) {
            builder.param("breed", breed);
        }
        if (gender != null) {
            builder.param("gender", gender);
        }
        if (userId != null) {
            builder.param("user_id", userId.toString());
        }
        return builder;
    }

    private MockMultipartFile noseImage(String filename) {
        return new MockMultipartFile(
                "nose_image",
                filename,
                "image/jpeg",
                new byte[]{1, 2, 3}
        );
    }

    private void registerUser(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "password123"
                        ))))
                .andExpect(status().isCreated());
    }

    private String loginAccessToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(responseBody(result))
                .get("access_token")
                .asText();
    }

    private String responseBody(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String signedToken(Long userId, long expiresAt) throws Exception {
        String header = base64Url(json(Map.of("alg", "HS256", "typ", "JWT")).getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(json(Map.of(
                "sub", userId.toString(),
                "email", "token-user@example.com",
                "role", "USER",
                "iat", Instant.now().getEpochSecond(),
                "exp", expiresAt
        )).getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return signingInput + "." + base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void verifyPipelineNotStarted() {
        verify(embedClient, never()).embed(any(byte[].class), anyString(), anyString());
        verify(qdrantDogVectorClient, never()).search(anyList());
        verify(qdrantDogVectorClient, never()).upsert(anyString(), anyList(), any());
    }

    private void assertPipelineRowsAbsent() {
        assertThat(dogRepository.count()).isZero();
        assertThat(dogImageRepository.count()).isZero();
        assertThat(verificationLogRepository.count()).isZero();
    }
}
