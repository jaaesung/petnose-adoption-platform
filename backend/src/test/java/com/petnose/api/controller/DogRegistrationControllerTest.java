package com.petnose.api.controller;

import com.petnose.api.dto.registration.DogRegisterResponse;
import com.petnose.api.exception.ApiException;
import com.petnose.api.service.DogRegistrationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DogRegistrationController.class)
class DogRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DogRegistrationService dogRegistrationService;

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

        mockMvc.perform(validMultipartRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registration_allowed").value(true))
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.verification_status").value("VERIFIED"))
                .andExpect(jsonPath("$.embedding_status").value("COMPLETED"))
                .andExpect(jsonPath("$.qdrant_point_id").value("dog-1"));
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
                        null,
                        "duplicate suspected"
                ));

        mockMvc.perform(validMultipartRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration_allowed").value(false))
                .andExpect(jsonPath("$.status").value("DUPLICATE_SUSPECTED"))
                .andExpect(jsonPath("$.verification_status").value("DUPLICATE_SUSPECTED"))
                .andExpect(jsonPath("$.embedding_status").value("SKIPPED_DUPLICATE"))
                .andExpect(jsonPath("$.qdrant_point_id").doesNotExist());
    }

    @Test
    void registerDogUsesCanonicalErrorResponse() throws Exception {
        when(dogRegistrationService.register(ArgumentMatchers.any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 user_id 입니다."));

        mockMvc.perform(validMultipartRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 user_id 입니다."))
                .andExpect(jsonPath("$.details.timestamp").exists());
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
}
