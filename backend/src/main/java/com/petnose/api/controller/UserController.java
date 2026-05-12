package com.petnose.api.controller;

import com.petnose.api.dto.user.UserMeResponse;
import com.petnose.api.dto.user.UserProfileResponse;
import com.petnose.api.dto.user.UserProfileUpdateRequest;
import com.petnose.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping("/me")
    public UserMeResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.me(authorization);
    }

    @PatchMapping("/me/profile")
    public UserProfileResponse updateProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody UserProfileUpdateRequest request
    ) {
        return authService.updateProfile(authorization, request);
    }
}
