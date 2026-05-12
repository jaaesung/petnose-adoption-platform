package com.petnose.api.service;

import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.enums.UserRole;
import com.petnose.api.dto.auth.LoginRequest;
import com.petnose.api.dto.auth.LoginResponse;
import com.petnose.api.dto.auth.RegisterRequest;
import com.petnose.api.dto.user.UserMeResponse;
import com.petnose.api.exception.ApiException;
import com.petnose.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MAX_CONTACT_PHONE_LENGTH = 50;
    private static final int MAX_REGION_LENGTH = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public UserMeResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String password = required(request.password(), "password");
        if (password.length() < 8) {
            throw badRequest("password는 8자 이상이어야 합니다.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 email 입니다.");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(UserRole.USER);
        user.setDisplayName(optional(request.displayName(), MAX_DISPLAY_NAME_LENGTH, "display_name"));
        user.setContactPhone(optional(request.contactPhone(), MAX_CONTACT_PHONE_LENGTH, "contact_phone"));
        user.setRegion(optional(request.region(), MAX_REGION_LENGTH, "region"));
        user.setActive(true);

        try {
            return UserMeResponse.from(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 email 입니다.");
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String password = required(request.password(), "password");

        User user = userRepository.findByEmail(email)
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "비활성화된 사용자입니다.");
        }

        return new LoginResponse(
                jwtTokenService.createAccessToken(user),
                "Bearer",
                jwtTokenService.getAccessTokenTtlSeconds(),
                UserMeResponse.from(user)
        );
    }

    @Transactional(readOnly = true)
    public UserMeResponse me(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        Long userId = jwtTokenService.parseUserId(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 사용자입니다."));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "비활성화된 사용자입니다.");
        }
        return UserMeResponse.from(user);
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authorization Bearer token이 필요합니다.");
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.startsWith(prefix) || authorizationHeader.length() <= prefix.length()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authorization Bearer token이 필요합니다.");
        }
        return authorizationHeader.substring(prefix.length()).trim();
    }

    private String normalizeEmail(String rawEmail) {
        String email = required(rawEmail, "email").toLowerCase(Locale.ROOT);
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw badRequest("email은 255자 이하여야 합니다.");
        }
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw badRequest("올바른 email 형식이 아닙니다.");
        }
        return email;
    }

    private String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw badRequest(fieldName + "은 필수입니다.");
        }
        return value.trim();
    }

    private String optional(String value, int maxLength, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw badRequest(fieldName + "은 " + maxLength + "자 이하여야 합니다.");
        }
        return trimmed;
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "email 또는 password가 올바르지 않습니다.");
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
}
