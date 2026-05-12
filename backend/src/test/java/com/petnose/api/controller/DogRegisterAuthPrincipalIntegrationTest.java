package com.petnose.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.client.EmbedClient;
import com.petnose.api.client.QdrantDogVectorClient;
import com.petnose.api.domain.entity.Dog;
import com.petnose.api.domain.entity.DogImage;
import com.petnose.api.domain.entity.User;
import com.petnose.api.dto.registration.QdrantSearchResult;
import com.petnose.api.repository.DogImageRepository;
import com.petnose.api.repository.DogRepository;
import com.petnose.api.repository.UserRepository;
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
    void dogRegisterKeepsLegacyUserIdFallbackWithoutAuthorization() throws Exception {
        registerUser("legacy-dog@example.com");
        User legacyUser = userRepository.findByEmail("legacy-dog@example.com").orElseThrow();

        mockMvc.perform(validDogMultipart(legacyUser.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registration_allowed").value(true))
                .andExpect(jsonPath("$.qdrant_point_id").exists());

        Dog dog = onlyDog();
        assertThat(dog.getOwnerUserId()).isEqualTo(legacyUser.getId());
        DogImage noseImage = dogImageRepository.findAll().get(0);
        assertThat(noseImage.getFilePath()).startsWith("dogs/").doesNotContain("/tmp", "\\tmp");
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

        assertThat(dogRepository.count()).isZero();
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

        assertThat(dogRepository.count()).isZero();
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

        assertThat(dogRepository.count()).isZero();
        verifyPipelineNotStarted();
    }

    @Test
    void dogRegisterRejectsMissingAuthAndMissingUserId() throws Exception {
        mockMvc.perform(validDogMultipart(null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields[0]").value("user_id"));

        assertThat(dogRepository.count()).isZero();
        verifyPipelineNotStarted();
    }

    private Dog onlyDog() {
        List<Dog> dogs = dogRepository.findAll();
        assertThat(dogs).hasSize(1);
        return dogs.get(0);
    }

    private MockHttpServletRequestBuilder validDogMultipart(Long userId) {
        MockMultipartFile noseImage = new MockMultipartFile(
                "nose_image",
                "nose.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        MockHttpServletRequestBuilder builder = multipart("/api/dogs/register")
                .file(noseImage)
                .param("name", "Bori")
                .param("breed", "Jindo")
                .param("gender", "MALE")
                .param("birth_date", "2024-01-01")
                .param("description", "friendly");
        if (userId != null) {
            builder.param("user_id", userId.toString());
        }
        return builder;
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
}
