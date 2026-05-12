package com.petnose.api.repository;

import com.petnose.api.domain.entity.VerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VerificationLogRepository extends JpaRepository<VerificationLog, Long> {

    Optional<VerificationLog> findFirstByDogIdOrderByCreatedAtDescIdDesc(String dogId);

    List<VerificationLog> findByDogIdInOrderByDogIdAscCreatedAtDescIdDesc(Collection<String> dogIds);
}
