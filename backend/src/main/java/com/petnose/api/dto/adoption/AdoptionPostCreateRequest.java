package com.petnose.api.dto.adoption;

import org.springframework.web.multipart.MultipartFile;

public record AdoptionPostCreateRequest(
        Long noseVerificationId,
        String dogName,
        String breed,
        String gender,
        String birthDate,
        String description,
        String title,
        String content,
        String status,
        MultipartFile profileImage
) {
}
