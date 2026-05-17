package com.petnose.api.repository;

import com.petnose.api.domain.entity.NoseVerificationAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NoseVerificationAttemptRepository extends JpaRepository<NoseVerificationAttempt, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from NoseVerificationAttempt attempt where attempt.id = :id")
    Optional<NoseVerificationAttempt> findByIdForUpdate(@Param("id") Long id);
}
