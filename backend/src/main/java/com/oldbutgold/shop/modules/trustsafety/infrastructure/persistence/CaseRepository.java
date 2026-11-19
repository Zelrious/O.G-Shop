package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseEntity, Long> {

    List<CaseEntity> findByOrderId(Long orderId);

    @Query("SELECT c FROM CaseEntity c WHERE c.orderId = :orderId AND c.state NOT IN ('CLOSED', 'REJECTED')")
    Optional<CaseEntity> findActiveCaseByOrderId(@Param("orderId") Long orderId);

    Optional<CaseEntity> findByCommandKey(String commandKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CaseEntity c WHERE c.id = :id")
    Optional<CaseEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT c.id FROM CaseEntity c, CaseRoundEntity r " +
           "WHERE c.id = r.caseId " +
           "AND c.state = 'POST_RETURN' " +
           "AND c.sellerResponseDueAt IS NOT NULL " +
           "AND c.sellerResponseDueAt <= :now " +
           "AND r.roundNo = 2 " +
           "AND r.state = 'OPEN' " +
           "AND r.validDamageRequestAt IS NULL " +
           "ORDER BY c.id ASC")
    List<Long> findOverduePostReturnCaseIds(@Param("now") java.time.Instant now);

    @Query("SELECT c.id FROM CaseEntity c, CaseRoundEntity r " +
           "WHERE c.id = r.caseId " +
           "AND c.state = 'POST_RETURN' " +
           "AND r.roundNo = 2 " +
           "AND r.stage = 'POST_RETURN_RESOLUTION' " +
           "AND r.state = 'OPEN' " +
           "AND r.completeSubmissionAt IS NOT NULL " +
           "AND r.completeSubmissionAt <= :slaThreshold " +
           "AND NOT EXISTS (SELECT 1 FROM CaseDecisionEntity d WHERE d.roundId = r.id AND d.isFinal = true) " +
           "AND NOT EXISTS (SELECT 1 FROM CaseEventEntity e WHERE e.caseId = c.id AND e.roundId = r.id AND e.eventType = 'ESCALATED') " +
           "ORDER BY c.id ASC")
    List<Long> findOverdueKtvSlaCaseIds(@Param("slaThreshold") java.time.Instant slaThreshold);

    @Query("""
        SELECT c FROM CaseEntity c
        WHERE ((:scope = 'AVAILABLE' AND c.assignedTo IS NULL) OR (:scope = 'MY_CASES' AND c.assignedTo = :ktvId))
          AND c.state IN ('OPEN', 'REVIEWING')
          AND EXISTS (
              SELECT 1 FROM CaseRoundEntity r1
              WHERE r1.caseId = c.id
                AND r1.roundNo = 1
                AND r1.state = 'OPEN'
          )
        ORDER BY c.createdAt ASC
    """)
    List<CaseEntity> findKtvWorkQueueRound1(
            @Param("scope") String scope,
            @Param("ktvId") Long ktvId
    );

    @Query("""
        SELECT c FROM CaseEntity c
        WHERE ((:scope = 'AVAILABLE' AND c.assignedTo IS NULL) OR (:scope = 'MY_CASES' AND c.assignedTo = :ktvId))
          AND c.state = 'POST_RETURN'
          AND c.validReturnedAt IS NOT NULL
          AND EXISTS (
              SELECT 1 FROM CaseRoundEntity r2
              WHERE r2.caseId = c.id
                AND r2.roundNo = 2
                AND r2.state = 'OPEN'
                AND r2.stage = 'POST_RETURN_RESOLUTION'
                AND (r2.validDamageRequestAt IS NOT NULL OR (c.sellerResponseDueAt IS NOT NULL AND :now > c.sellerResponseDueAt))
                AND NOT EXISTS (
                    SELECT 1 FROM CaseEventEntity ce
                    WHERE ce.caseId = c.id
                      AND ce.roundId = r2.id
                      AND ce.eventType = 'ESCALATED'
                )
          )
          AND NOT EXISTS (
              SELECT 1 FROM CaseEvidenceRequestEntity cer
              WHERE cer.caseId = c.id
                AND cer.status = 'PENDING'
                AND cer.deadlineAt >= :now
          )
        ORDER BY c.createdAt ASC
    """)
    List<CaseEntity> findKtvWorkQueueRound2(
            @Param("scope") String scope,
            @Param("ktvId") Long ktvId,
            @Param("now") java.time.Instant now
    );

    @Query(value = """
        SELECT c FROM CaseEntity c, CaseRoundEntity r
        WHERE c.id = r.caseId
          AND c.state = 'POST_RETURN'
          AND r.roundNo = 2
          AND r.state = 'OPEN'
          AND r.stage = 'POST_RETURN_RESOLUTION'
          AND (:ktvId IS NULL OR c.assignedTo = :ktvId)
          AND EXISTS (
              SELECT 1 FROM CaseEventEntity e
              WHERE e.caseId = c.id
                AND e.roundId = r.id
                AND e.eventType = 'ESCALATED'
          )
          AND NOT EXISTS (
              SELECT 1 FROM CaseDecisionEntity d
              WHERE d.roundId = r.id AND d.isFinal = true
          )
        ORDER BY r.completeSubmissionAt ASC, c.id ASC
    """, countQuery = """
        SELECT count(c) FROM CaseEntity c, CaseRoundEntity r
        WHERE c.id = r.caseId
          AND c.state = 'POST_RETURN'
          AND r.roundNo = 2
          AND r.state = 'OPEN'
          AND r.stage = 'POST_RETURN_RESOLUTION'
          AND (:ktvId IS NULL OR c.assignedTo = :ktvId)
          AND EXISTS (
              SELECT 1 FROM CaseEventEntity e
              WHERE e.caseId = c.id
                AND e.roundId = r.id
                AND e.eventType = 'ESCALATED'
          )
          AND NOT EXISTS (
              SELECT 1 FROM CaseDecisionEntity d
              WHERE d.roundId = r.id AND d.isFinal = true
          )
    """)
    Page<CaseEntity> findEscalatedCases(
            @Param("ktvId") Long ktvId,
            Pageable pageable
    );
}
