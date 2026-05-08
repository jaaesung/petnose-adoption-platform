package com.petnose.api.repository;

import com.petnose.api.domain.entity.Dog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DogRepository extends JpaRepository<Dog, String> {
}
