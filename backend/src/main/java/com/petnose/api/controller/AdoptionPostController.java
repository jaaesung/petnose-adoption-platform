package com.petnose.api.controller;

import com.petnose.api.dto.adoption.AdoptionPostCreateRequest;
import com.petnose.api.dto.adoption.AdoptionPostCreateResponse;
import com.petnose.api.dto.adoption.AdoptionPostDetailResponse;
import com.petnose.api.dto.adoption.AdoptionPostListResponse;
import com.petnose.api.dto.adoption.AdoptionPostOwnerListResponse;
import com.petnose.api.dto.adoption.AdoptionPostStatusUpdateRequest;
import com.petnose.api.dto.adoption.AdoptionPostStatusUpdateResponse;
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

    @GetMapping
    public AdoptionPostListResponse list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "size", required = false) String size
    ) {
        return adoptionPostService.findPublicPosts(status, page, size);
    }

    @GetMapping("/me")
    public AdoptionPostOwnerListResponse listMine(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "size", required = false) String size
    ) {
        Long currentUserId = authService.currentActiveUserId(authorizationHeader);
        return adoptionPostService.findOwnerPosts(currentUserId, status, page, size);
    }

    @GetMapping("/{post_id}")
    public AdoptionPostDetailResponse detail(@PathVariable("post_id") Long postId) {
        return adoptionPostService.findPublicPost(postId);
    }

    @PostMapping
    public ResponseEntity<AdoptionPostCreateResponse> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody AdoptionPostCreateRequest request
    ) {
        Long currentUserId = authService.currentActiveUserId(authorizationHeader);
        AdoptionPostCreateResponse response = adoptionPostService.create(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{post_id}/status")
    public AdoptionPostStatusUpdateResponse updateStatus(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable("post_id") Long postId,
            @RequestBody AdoptionPostStatusUpdateRequest request
    ) {
        Long currentUserId = authService.currentActiveUserId(authorizationHeader);
        return adoptionPostService.updateStatus(currentUserId, postId, request);
    }
}
