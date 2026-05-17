package com.petnose.api.dto.nose;

import org.springframework.web.multipart.MultipartFile;

public record NoseVerificationRequest(
        Long userId,
        MultipartFile noseImage
) {
}
