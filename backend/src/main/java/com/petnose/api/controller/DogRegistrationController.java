package com.petnose.api.controller;

import com.petnose.api.dto.registration.DogRegisterRequest;
import com.petnose.api.dto.registration.DogRegisterResponse;
import com.petnose.api.service.DogRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/dogs")
@RequiredArgsConstructor
public class DogRegistrationController {

    private final DogRegistrationService dogRegistrationService;

    @PostMapping(value = "/register", consumes = "multipart/form-data")
    public ResponseEntity<DogRegisterResponse> registerDog(
            @RequestParam("user_id") Long userId,
            @RequestParam("name") String name,
            @RequestParam("breed") String breed,
            @RequestParam("gender") String gender,
            @RequestParam(value = "birth_date", required = false) String birthDate,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("nose_image") MultipartFile noseImage,
            @RequestParam(value = "profile_image", required = false) MultipartFile profileImage
    ) {
        // TODO: 인증/인가 도입 후 user_id는 JWT principal에서 추출하도록 변경.
        DogRegisterResponse response = dogRegistrationService.register(
                new DogRegisterRequest(userId, name, breed, gender, birthDate, description, noseImage, profileImage)
        );

        if (response.registrationAllowed()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        // TODO: 추후 프론트 협의 후 DUPLICATE_SUSPECTED를 HTTP 409로 전환 가능.
        return ResponseEntity.ok(response);
    }
}
