package com.petnose.api.domain.entity;

import com.petnose.api.domain.enums.DogImageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "dog_images")
public class DogImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dog_id", nullable = false, length = 36)
    private String dogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 20)
    private DogImageType imageType;

    @Column(name = "file_path", length = 500, nullable = false, unique = true)
    private String filePath;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    public void prePersist() {
        this.uploadedAt = Instant.now();
    }
}
