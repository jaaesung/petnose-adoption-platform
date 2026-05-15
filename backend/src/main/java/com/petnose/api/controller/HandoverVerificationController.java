package com.petnose.api.controller;

import com.petnose.api.dto.adoption.HandoverVerificationResponse;
import com.petnose.api.service.AuthService;
import com.petnose.api.service.HandoverVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/adoption-posts/{post_id}/handover-verifications")
@RequiredArgsConstructor
public class HandoverVerificationController {

    private final AuthService authService;
    private final HandoverVerificationService handoverVerificationService;

    @PostMapping
    public HandoverVerificationResponse verify(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable("post_id") Long postId,
            @RequestPart(value = "nose_image", required = false) MultipartFile noseImage
    ) {
        authService.currentActiveUserId(authorizationHeader);
        return handoverVerificationService.verify(postId, noseImage);
    }
}
