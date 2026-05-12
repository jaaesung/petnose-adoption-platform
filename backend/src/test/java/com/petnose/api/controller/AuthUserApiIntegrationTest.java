package com.petnose.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.enums.UserRole;
import com.petnose.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthUserApiIntegrationTest {

    private static final String TEST_JWT_SECRET = "test-petnose-jwt-secret-change-me-32bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registerReturnsUserRoleAndStoresPasswordHash() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "Owner@Example.COM",
                                "password", "password123",
                                "display_name", "Bori Owner",
                                "contact_phone", "010-0000-0000",
                                "region", "Seoul"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.display_name").value("Bori Owner"))
                .andExpect(jsonPath("$.contact_phone").value("010-0000-0000"))
                .andExpect(jsonPath("$.region").value("Seoul"))
                .andExpect(jsonPath("$.is_active").value(true));

        User saved = userRepository.findByEmail("owner@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void loginReturnsAccessTokenAndMeReturnsCurrentUser() throws Exception {
        register("me@example.com", "password123", "Me User", "010-1111-2222", "Busan");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "me@example.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value(not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.user.user_id").exists())
                .andExpect(jsonPath("$.user.email").value("me@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.display_name").value("Me User"))
                .andExpect(jsonPath("$.user.contact_phone").value("010-1111-2222"))
                .andExpect(jsonPath("$.user.is_active").value(true))
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(responseBody(loginResult));
        String accessToken = loginBody.get("access_token").asText();

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(loginBody.get("user").get("user_id").asLong()))
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.display_name").value("Me User"))
                .andExpect(jsonPath("$.contact_phone").value("010-1111-2222"))
                .andExpect(jsonPath("$.region").value("Busan"))
                .andExpect(jsonPath("$.is_active").value(true));
    }

    @Test
    void registerMissingEmailReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void registerBlankPasswordReturnsValidationFailed() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", "blank-password@example.com");
        body.put("password", " ");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void registerMalformedJsonReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void registerDuplicateEmailReturnsEmailAlreadyExists() throws Exception {
        register("duplicate@example.com", "password123", "Duplicate", null, null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "duplicate@example.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void registerIgnoresRoleInPublicSignupAndAlwaysStoresUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "role-ignore@example.com",
                                "password", "password123",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        User saved = userRepository.findByEmail("role-ignore@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void loginWrongPasswordReturnsInvalidCredentials() throws Exception {
        register("wrong-password@example.com", "password123", "Wrong Password", null, null);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "wrong-password@example.com",
                                "password", "wrong-password"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void loginUnknownEmailReturnsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "unknown@example.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void loginInactiveUserReturnsUserInactive() throws Exception {
        saveUser("inactive-login@example.com", "password123", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "inactive-login@example.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("USER_INACTIVE"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void registerAllowsOptionalProfileFieldsToBeOmitted() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "optional@example.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.display_name").doesNotExist())
                .andExpect(jsonPath("$.contact_phone").doesNotExist())
                .andExpect(jsonPath("$.region").doesNotExist());

        User saved = userRepository.findByEmail("optional@example.com").orElseThrow();
        assertThat(saved.getDisplayName()).isNull();
        assertThat(saved.getContactPhone()).isNull();
        assertThat(saved.getRegion()).isNull();
    }

    @Test
    void meRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void meRejectsMalformedBearerHeader() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Token abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void meRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void meRejectsExpiredToken() throws Exception {
        User user = saveUser("expired@example.com", "password123", true);
        String expiredToken = signedToken(user.getId(), Instant.now().minusSeconds(60).getEpochSecond());

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void meReturnsUserNotFoundWhenTokenSubjectUserDoesNotExist() throws Exception {
        String token = signedToken(999999L, Instant.now().plusSeconds(3600).getEpochSecond());

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    @Test
    void meRejectsInactiveUserWithValidToken() throws Exception {
        register("inactive-me@example.com", "password123", "Inactive Me", null, null);
        String accessToken = loginAccessToken("inactive-me@example.com", "password123");

        User user = userRepository.findByEmail("inactive-me@example.com").orElseThrow();
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("USER_INACTIVE"))
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andExpect(jsonPath("$.details.timestamp").exists());
    }

    private void register(String email, String password, String displayName, String contactPhone, String region) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        if (displayName != null) {
            body.put("display_name", displayName);
        }
        if (contactPhone != null) {
            body.put("contact_phone", contactPhone);
        }
        if (region != null) {
            body.put("region", region);
        }

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated());
    }

    private String loginAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(responseBody(result))
                .get("access_token")
                .asText();
    }

    private User saveUser(String email, String password, boolean active) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(UserRole.USER);
        user.setActive(active);
        return userRepository.saveAndFlush(user);
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

    private String responseBody(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
