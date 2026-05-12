package com.petnose.api.repository;

import com.petnose.api.domain.entity.DogImage;
import com.petnose.api.domain.enums.DogImageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DogImageRepository extends JpaRepository<DogImage, Long> {

    List<DogImage> findByDogIdInAndImageTypeOrderByDogIdAscUploadedAtDescIdDesc(
            Collection<String> dogIds,
            DogImageType imageType
    );
}
