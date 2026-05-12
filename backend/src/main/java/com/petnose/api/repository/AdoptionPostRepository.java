package com.petnose.api.repository;

import com.petnose.api.domain.entity.AdoptionPost;
import com.petnose.api.domain.enums.AdoptionPostStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface AdoptionPostRepository extends JpaRepository<AdoptionPost, Long> {

    boolean existsByDogIdAndStatusIn(String dogId, Collection<AdoptionPostStatus> statuses);
}
