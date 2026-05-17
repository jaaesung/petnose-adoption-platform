package com.petnose.api.dto.registration;

import org.springframework.web.multipart.MultipartFile;

public record DogRegisterRequest(
        Long userId,
        String name,
        String breed,
        String gender,
        String birthDate,
        String description,
        MultipartFile noseImage
) {
}
