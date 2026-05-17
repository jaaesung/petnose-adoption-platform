package com.petnose.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.dto.nose.NoseVerificationRequest;
import com.petnose.api.dto.nose.NoseVerificationResponse;
import com.petnose.api.dto.registration.DuplicateCandidateResponse;
import com.petnose.api.exception.ApiException;
import com.petnose.api.service.AuthService;
import com.petnose.api.service.NoseVerificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoseVerificationController.class)
class NoseVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NoseVerificationService noseVerificationService;

    @MockBean
    private AuthService authService;

    @Test
    void verifyReturnsCreatedWhenNoseVerificationPassed() throws Exception {
        Instant expiresAt = Instant.parse("2026-05-18T01:02:03Z");
        when(authService.currentActiveUserId("Bearer test-token")).thenReturn(42L);
        when(noseVerificationService.verify(ArgumentMatchers.any()))
                .thenReturn(new NoseVerificationResponse(
                        100L,
                        true,
                        "PASSED",
                        "VERIFIED",
                        "COMPLETED",
                        0.12345,
                        "/files/nose-verifications/attempt/nose/sample.png",
                        null,
                        expiresAt,
                        "verified"
                ));

        MvcResult result = mockMvc.perform(validMultipartRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nose_verification_id").value(100))
                .andExpect(jsonPath("$.dog_id").doesNotExist())
                .andExpect(jsonPath("$.registration_allowed").value(true))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.decision").value("PASSED"))
                .andExpect(jsonPath("$.verification_status").value("VERIFIED"))
                .andExpect(jsonPath("$.embedding_status").value("COMPLETED"))
                .andExpect(jsonPath("$.max_similarity_score").value(0.12345))
                .andExpect(jsonPath("$.nose_image_url").value("/files/nose-verifications/attempt/nose/sample.png"))
                .andExpect(jsonPath("$.top_match").value(nullValue()))
                .andExpect(jsonPath("$.expires_at").value("2026-05-18T01:02:03Z"))
                .andExpect(jsonPath("$.message").value("verified"))
                .andExpect(jsonPath("$.qdrant_point_id").doesNotExist())
                .andExpect(jsonPath("$.model").doesNotExist())
                .andExpect(jsonPath("$.dimension").doesNotExist())
                .andExpect(jsonPath("$.profile_image_url").doesNotExist())
                .andExpect(jsonPath("$.noseVerificationId").doesNotExist())
                .andReturn();

        ArgumentCaptor<NoseVerificationRequest> requestCaptor = ArgumentCaptor.forClass(NoseVerificationRequest.class);
        verify(noseVerificationService).verify(requestCaptor.capture());
        NoseVerificationRequest request = requestCaptor.getValue();
        assertThat(request.userId()).isEqualTo(42L);
        assertThat(request.noseImage()).isNotNull();
        assertThat(request.noseImage().getOriginalFilename()).isEqualTo("sample.png");

        JsonNode body = objectMapper.readTree(responseBody(result));
        assertThat(body.fieldNames())
                .toIterable()
                .contains(
                        "nose_verification_id",
                        "registration_allowed",
                        "allowed",
                        "status",
                        "decision",
                        "verification_status",
                        "embedding_status",
                        "max_similarity_score",
                        "nose_image_url",
                        "top_match",
                        "expires_at",
                        "message"
                );
    }

    @Test
    void verifyReturnsOkWhenDuplicateSuspected() throws Exception {
        when(authService.currentActiveUserId("Bearer test-token")).thenReturn(42L);
        when(noseVerificationService.verify(ArgumentMatchers.any()))
                .thenReturn(new NoseVerificationResponse(
                        101L,
                        false,
                        "DUPLICATE_SUSPECTED",
                        "DUPLICATE_SUSPECTED",
                        "SKIPPED_DUPLICATE",
                        0.98765,
                        "/files/nose-verifications/attempt/nose/sample.png",
                        new DuplicateCandidateResponse("existing-dog-1", 0.98765, "Jindo"),
                        Instant.parse("2026-05-18T01:02:03Z"),
                        "duplicate suspected"
                ));

        MvcResult result = mockMvc.perform(validMultipartRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nose_verification_id").value(101))
                .andExpect(jsonPath("$.dog_id").doesNotExist())
                .andExpect(jsonPath("$.registration_allowed").value(false))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.decision").value("DUPLICATE_SUSPECTED"))
                .andExpect(jsonPath("$.top_match.dog_id").value("existing-dog-1"))
                .andExpect(jsonPath("$.top_match.similarity_score").value(0.98765))
                .andExpect(jsonPath("$.top_match.breed").value("Jindo"))
                .andExpect(jsonPath("$.top_match.nose_image_url").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(responseBody(result));
        assertThat(body.get("top_match").fieldNames())
                .toIterable()
                .containsExactly("dog_id", "similarity_score", "breed");
    }

    @Test
    void verifyUsesCanonicalErrorResponse() throws Exception {
        when(authService.currentActiveUserId("Bearer test-token")).thenReturn(42L);
        when(noseVerificationService.verify(ArgumentMatchers.any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "NOSE_IMAGE_REQUIRED", "nose_image는 필수입니다."));

        mockMvc.perform(validMultipartRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("NOSE_IMAGE_REQUIRED"))
                .andExpect(jsonPath("$.message").value("nose_image는 필수입니다."))
                .andExpect(jsonPath("$.details").value(nullValue()));
    }

    @Test
    void verifyRejectsMissingAuthorizationBeforeService() throws Exception {
        when(authService.currentActiveUserId(null))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authorization Bearer token이 필요합니다."));

        mockMvc.perform(validMultipartRequestWithoutAuthorization())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        verify(noseVerificationService, never()).verify(any());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validMultipartRequest() {
        return validMultipartRequestWithoutAuthorization()
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validMultipartRequestWithoutAuthorization() {
        MockMultipartFile noseImage = new MockMultipartFile(
                "nose_image",
                "sample.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        return multipart("/api/nose-verifications")
                .file(noseImage);
    }

    private String responseBody(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
