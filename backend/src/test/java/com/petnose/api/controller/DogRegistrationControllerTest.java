package com.petnose.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.dto.registration.DogRegisterResponse;
import com.petnose.api.dto.registration.DuplicateCandidateResponse;
import com.petnose.api.exception.ApiException;
import com.petnose.api.service.AuthService;
import com.petnose.api.service.DogRegistrationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DogRegistrationController.class)
class DogRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DogRegistrationService dogRegistrationService;

    @MockBean
    private AuthService authService;

    @Test
    void registerDogReturnsCreatedWhenRegistrationAllowed() throws Exception {
        when(dogRegistrationService.register(ArgumentMatchers.any()))
                .thenReturn(new DogRegisterResponse(
                        "dog-1",
                        true,
                        "REGISTERED",
                        "VERIFIED",
                        "COMPLETED",
                        "dog-1",
                        "dog-nose-identification2:s101_224",
                        2048,
                        0.12345,
                        "/files/dogs/dog-1/nose/sample.png",
                        "/files/dogs/dog-1/profile/sample.png",
                        null,
                        "registered"
                ));

        MvcResult result = mockMvc.perform(validMultipartRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dog_id").value("dog-1"))
                .andExpect(jsonPath("$.registration_allowed").value(true))
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.verification_status").value("VERIFIED"))
                .andExpect(jsonPath("$.embedding_status").value("COMPLETED"))
                .andExpect(jsonPath("$.qdrant_point_id").value("dog-1"))
                .andExpect(jsonPath("$.model").value("dog-nose-identification2:s101_224"))
                .andExpect(jsonPath("$.dimension").value(2048))
                .andExpect(jsonPath("$.max_similarity_score").value(0.12345))
                .andExpect(jsonPath("$.nose_image_url").value("/files/dogs/dog-1/nose/sample.png"))
                .andExpect(jsonPath("$.profile_image_url").value("/files/dogs/dog-1/profile/sample.png"))
                .andExpect(jsonPath("$.top_match").doesNotExist())
                .andExpect(jsonPath("$.message").value("registered"))
                .andExpect(jsonPath("$.dogId").doesNotExist())
                .andExpect(jsonPath("$.registrationAllowed").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(responseBody(result));
        assertThat(body.fieldNames())
                .toIterable()
                .containsExactly(
                        "dog_id",
                        "registration_allowed",
                        "status",
                        "verification_status",
                        "embedding_status",
                        "qdrant_point_id",
                        "model",
                        "dimension",
                        "max_similarity_score",
                        "nose_image_url",
                        "profile_image_url",
                        "top_match",
                        "message"
                );
    }

    @Test
    void registerDogReturnsOkWhenDuplicateSuspected() throws Exception {
        when(dogRegistrationService.register(ArgumentMatchers.any()))
                .thenReturn(new DogRegisterResponse(
                        "dog-2",
                        false,
                        "DUPLICATE_SUSPECTED",
                        "DUPLICATE_SUSPECTED",
                        "SKIPPED_DUPLICATE",
                        null,
                        "dog-nose-identification2:s101_224",
                        2048,
                        0.98765,
                        "/files/dogs/dog-2/nose/sample.png",
                        null,
                        new DuplicateCandidateResponse("existing-dog-1", 0.98765, "Jindo"),
                        "duplicate suspected"
                ));

        MvcResult result = mockMvc.perform(validMultipartRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dog_id").value("dog-2"))
                .andExpect(jsonPath("$.registration_allowed").value(false))
                .andExpect(jsonPath("$.status").value("DUPLICATE_SUSPECTED"))
                .andExpect(jsonPath("$.verification_status").value("DUPLICATE_SUSPECTED"))
                .andExpect(jsonPath("$.embedding_status").value("SKIPPED_DUPLICATE"))
                .andExpect(jsonPath("$.qdrant_point_id").doesNotExist())
                .andExpect(jsonPath("$.model").value("dog-nose-identification2:s101_224"))
                .andExpect(jsonPath("$.dimension").value(2048))
                .andExpect(jsonPath("$.max_similarity_score").value(0.98765))
                .andExpect(jsonPath("$.nose_image_url").value("/files/dogs/dog-2/nose/sample.png"))
                .andExpect(jsonPath("$.profile_image_url").doesNotExist())
                .andExpect(jsonPath("$.top_match.dog_id").value("existing-dog-1"))
                .andExpect(jsonPath("$.top_match.similarity_score").value(0.98765))
                .andExpect(jsonPath("$.top_match.breed").value("Jindo"))
                .andExpect(jsonPath("$.top_match.nose_image_url").doesNotExist())
                .andExpect(jsonPath("$.message").value("duplicate suspected"))
                .andExpect(jsonPath("$.topMatch").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(responseBody(result));
        assertThat(body.has("qdrant_point_id")).isTrue();
        assertThat(body.get("qdrant_point_id").isNull()).isTrue();
        assertThat(body.get("top_match").fieldNames())
                .toIterable()
                .containsExactly("dog_id", "similarity_score", "breed");
        assertThat(body.get("top_match").has("nose_image_url")).isFalse();
    }

    @Test
    void registerDogUsesCanonicalErrorResponse() throws Exception {
        when(dogRegistrationService.register(ArgumentMatchers.any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 user_id 입니다."));

        mockMvc.perform(validMultipartRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 user_id 입니다."))
                .andExpect(jsonPath("$.details").value(nullValue()));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validMultipartRequest() {
        MockMultipartFile noseImage = new MockMultipartFile(
                "nose_image",
                "sample.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        return multipart("/api/dogs/register")
                .file(noseImage)
                .param("user_id", "1")
                .param("name", "Bori")
                .param("breed", "Jindo")
                .param("gender", "MALE")
                .param("birth_date", "2024-01-01")
                .param("description", "friendly");
    }

    private String responseBody(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
