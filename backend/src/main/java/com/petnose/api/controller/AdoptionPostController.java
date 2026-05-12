package com.petnose.api.controller;

import com.petnose.api.dto.adoption.AdoptionPostCreateRequest;
import com.petnose.api.dto.adoption.AdoptionPostCreateResponse;
import com.petnose.api.service.AdoptionPostService;
import com.petnose.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/adoption-posts")
@RequiredArgsConstructor
public class AdoptionPostController {

    private final AuthService authService;
    private final AdoptionPostService adoptionPostService;

    @PostMapping
    public ResponseEntity<AdoptionPostCreateResponse> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody AdoptionPostCreateRequest request
    ) {
        Long currentUserId = authService.currentActiveUserId(authorizationHeader);
        AdoptionPostCreateResponse response = adoptionPostService.create(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
