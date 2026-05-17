package com.petnose.api.controller;

import com.petnose.api.dto.nose.NoseVerificationRequest;
import com.petnose.api.dto.nose.NoseVerificationResponse;
import com.petnose.api.service.AuthService;
import com.petnose.api.service.NoseVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/nose-verifications")
@RequiredArgsConstructor
public class NoseVerificationController {

    private final AuthService authService;
    private final NoseVerificationService noseVerificationService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<NoseVerificationResponse> verify(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(value = "nose_image", required = false) MultipartFile noseImage
    ) {
        Long currentUserId = authService.currentActiveUserId(authorizationHeader);
        NoseVerificationResponse response = noseVerificationService.verify(
                new NoseVerificationRequest(currentUserId, noseImage)
        );

        if (response.registrationAllowed()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
