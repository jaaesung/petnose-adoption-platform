package com.petnose.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.domain.entity.User;
import com.petnose.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Value("${auth.jwt.secret}")
    private String secret;

    @Value("${auth.jwt.access-token-ttl-seconds:3600}")
    private long accessTokenTtlSeconds;

    public String createAccessToken(User user) {
        long now = Instant.now().getEpochSecond();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getId().toString());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole().name());
        payload.put("iat", now);
        payload.put("exp", now + accessTokenTtlSeconds);

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;

        return signingInput + "." + sign(signingInput);
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public Long parseUserId(String token) {
        Map<String, Object> claims = parseClaims(token);
        Object subject = claims.get("sub");
        if (!(subject instanceof String subjectString)) {
            throw invalidToken();
        }

        try {
            return Long.valueOf(subjectString);
        } catch (NumberFormatException e) {
            throw invalidToken();
        }
    }

    private Map<String, Object> parseClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw invalidToken();
            }

            String signingInput = parts[0] + "." + parts[1];
            String expectedSignature = sign(signingInput);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.US_ASCII),
                    parts[2].getBytes(StandardCharsets.US_ASCII))) {
                throw invalidToken();
            }

            Map<String, Object> header = objectMapper.readValue(decode(parts[0]), CLAIMS_TYPE);
            if (!"HS256".equals(header.get("alg")) || !"JWT".equals(header.get("typ"))) {
                throw invalidToken();
            }

            Map<String, Object> claims = objectMapper.readValue(decode(parts[1]), CLAIMS_TYPE);
            long expiresAt = asLong(claims.get("exp"));
            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw unauthorized();
            }
            return claims;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw invalidToken();
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException("JWT JSON 직렬화에 실패했습니다.", e);
        }
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 서명에 실패했습니다.", e);
        }
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw invalidToken();
    }

    private ApiException invalidToken() {
        return unauthorized();
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효하지 않은 access token 입니다.");
    }
}
