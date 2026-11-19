package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CaseRoundRepository extends JpaRepository<CaseRoundEntity, Long> {

    List<CaseRoundEntity> findByCaseIdOrderByRoundNoAsc(Long caseId);

    Optional<CaseRoundEntity> findByCaseIdAndRoundNo(Long caseId, Integer roundNo);

    @Query("SELECT r FROM CaseRoundEntity r WHERE r.caseId = :caseId ORDER BY r.roundNo DESC LIMIT 1")
    Optional<CaseRoundEntity> findLatestRoundByCaseId(@Param("caseId") Long caseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CaseRoundEntity r WHERE r.id = :id")
    Optional<CaseRoundEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CaseRoundEntity r WHERE r.caseId = :caseId AND r.roundNo = :roundNo")
    Optional<CaseRoundEntity> findByCaseIdAndRoundNoForUpdate(@Param("caseId") Long caseId, @Param("roundNo") Integer roundNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CaseRoundEntity r WHERE r.caseId = :caseId ORDER BY r.roundNo DESC LIMIT 1")
    Optional<CaseRoundEntity> findLatestRoundByCaseIdForUpdate(@Param("caseId") Long caseId);
}

