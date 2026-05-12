package com.petnose.api.controller;

import com.petnose.api.dto.registration.DogRegisterRequest;
import com.petnose.api.dto.registration.DogRegisterResponse;
import com.petnose.api.exception.ApiException;
import com.petnose.api.service.AuthService;
import com.petnose.api.service.DogRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dogs")
@RequiredArgsConstructor
public class DogRegistrationController {

    private final AuthService authService;
    private final DogRegistrationService dogRegistrationService;

    @PostMapping(value = "/register", consumes = "multipart/form-data")
    public ResponseEntity<DogRegisterResponse> registerDog(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(value = "user_id", required = false) Long userId,
            @RequestParam("name") String name,
            @RequestParam("breed") String breed,
            @RequestParam("gender") String gender,
            @RequestParam(value = "birth_date", required = false) String birthDate,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("nose_image") MultipartFile noseImage,
            @RequestParam(value = "profile_image", required = false) MultipartFile profileImage
    ) {
        Long ownerUserId = resolveOwnerUserId(authorization, userId);
        DogRegisterResponse response = dogRegistrationService.register(
                new DogRegisterRequest(ownerUserId, name, breed, gender, birthDate, description, noseImage, profileImage)
        );

        if (response.registrationAllowed()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        // TODO: 추후 프론트 협의 후 DUPLICATE_SUSPECTED를 HTTP 409로 전환 가능.
        return ResponseEntity.ok(response);
    }

    private Long resolveOwnerUserId(String authorization, Long legacyUserId) {
        if (authorization != null) {
            return authService.currentActiveUserId(authorization);
        }
        if (legacyUserId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "Authorization Bearer token 또는 user_id가 필요합니다.",
                    Map.of("fields", List.of("user_id"))
            );
        }
        return legacyUserId;
    }
}
