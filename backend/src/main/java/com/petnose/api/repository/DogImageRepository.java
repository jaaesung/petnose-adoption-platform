package com.petnose.api.repository;

import com.petnose.api.domain.entity.DogImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DogImageRepository extends JpaRepository<DogImage, Long> {
}
