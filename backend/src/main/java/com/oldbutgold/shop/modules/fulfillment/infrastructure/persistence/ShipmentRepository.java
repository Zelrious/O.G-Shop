package com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<ShipmentEntity, Long> {
    Optional<ShipmentEntity> findByOrderId(Long orderId);
    Optional<ShipmentEntity> findByTrackingNumber(String trackingNumber);
    Optional<ShipmentEntity> findByClientOrderCode(String clientOrderCode);
    boolean existsByOrderId(Long orderId);

    @Query("SELECT s.orderId FROM ShipmentEntity s WHERE s.trackingNumber = :trackingNumber")
    Optional<Long> findOrderIdByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShipmentEntity s WHERE s.orderId = :orderId")
    Optional<ShipmentEntity> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShipmentEntity s SET s.claimToken = :newToken, s.claimExpiresAt = :newExpiry, s.updatedAt = :now " +
           "WHERE s.orderId = :orderId AND s.status = 'PENDING' AND s.trackingNumber IS NULL " +
           "AND (s.claimToken IS NULL OR s.claimExpiresAt IS NULL OR s.claimExpiresAt <= :now)")
    int acquireClaim(
            @Param("orderId") Long orderId,
            @Param("newToken") String newToken,
            @Param("newExpiry") Instant newExpiry,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShipmentEntity s SET s.claimToken = NULL, s.claimExpiresAt = NULL, s.updatedAt = :now " +
           "WHERE s.orderId = :orderId AND s.claimToken = :claimToken")
    int releaseClaim(
            @Param("orderId") Long orderId,
            @Param("claimToken") String claimToken,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShipmentEntity s SET s.nextReconciliationAt = :nextRecon, s.reconciliationAttempts = :attempts, " +
           "s.incidentNotes = :incidentNotes, s.claimToken = NULL, s.claimExpiresAt = NULL, s.updatedAt = :now " +
           "WHERE s.orderId = :orderId AND s.claimToken = :claimToken")
    int releaseClaimWithBackoff(
            @Param("orderId") Long orderId,
            @Param("claimToken") String claimToken,
            @Param("nextRecon") Instant nextRecon,
            @Param("attempts") int attempts,
            @Param("incidentNotes") String incidentNotes,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShipmentEntity s SET s.adminReviewRequired = true, s.rawGhnStatus = :rawStatus, " +
           "s.incidentNotes = :incidentNotes, s.reconciliationAttempts = :attempts, " +
           "s.claimToken = NULL, s.claimExpiresAt = NULL, s.updatedAt = :now " +
           "WHERE s.orderId = :orderId AND s.claimToken = :claimToken")
    int recordIncidentAndReleaseClaim(
            @Param("orderId") Long orderId,
            @Param("claimToken") String claimToken,
            @Param("rawStatus") String rawStatus,
            @Param("incidentNotes") String incidentNotes,
            @Param("attempts") int attempts,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShipmentEntity s SET s.trackingNumber = :trackingNumber, s.rawGhnStatus = :rawStatus, " +
           "s.incidentNotes = NULL, s.claimToken = NULL, s.claimExpiresAt = NULL, " +
           "s.nextReconciliationAt = NULL, s.reconciliationAttempts = 0, s.updatedAt = :now " +
           "WHERE s.orderId = :orderId AND s.claimToken = :claimToken " +
           "AND s.status = 'PENDING' AND (s.trackingNumber IS NULL OR s.trackingNumber = '')")
    int recordReconciledTracking(
            @Param("orderId") Long orderId,
            @Param("claimToken") String claimToken,
            @Param("trackingNumber") String trackingNumber,
            @Param("rawStatus") String rawStatus,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShipmentEntity s SET s.rawGhnStatus = :rawStatus, s.updatedAt = :now " +
           "WHERE s.orderId = :orderId AND s.status = 'PENDING'")
    int updateRawGhnStatusIfPending(
            @Param("orderId") Long orderId,
            @Param("rawStatus") String rawStatus,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShipmentEntity s SET s.rawGhnStatus = :rawStatus, s.updatedAt = :now " +
           "WHERE s.orderId = :orderId AND s.status NOT IN ('DELIVERED', 'RETURNED', 'FAILED')")
    int updateRawGhnStatusIfNotTerminal(
            @Param("orderId") Long orderId,
            @Param("rawStatus") String rawStatus,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShipmentEntity s SET s.adminReviewRequired = true, s.rawGhnStatus = :rawStatus, " +
           "s.incidentNotes = :notes, s.updatedAt = :now " +
           "WHERE s.orderId = :orderId AND s.status NOT IN ('DELIVERED', 'RETURNED', 'FAILED')")
    int recordIncidentIfNotTerminal(
            @Param("orderId") Long orderId,
            @Param("rawStatus") String rawStatus,
            @Param("notes") String notes,
            @Param("now") Instant now
    );

    @Query("SELECT s FROM ShipmentEntity s WHERE s.status NOT IN ('DELIVERED', 'RETURNED', 'FAILED') " +
           "AND s.trackingNumber IS NOT NULL AND s.trackingNumber <> '' " +
           "AND s.adminReviewRequired = false " +
           "ORDER BY s.updatedAt ASC")
    List<ShipmentEntity> findActiveShipmentsForReconciliation(Pageable pageable);

    @Query("SELECT s FROM ShipmentEntity s WHERE s.status = 'PENDING' " +
           "AND (s.trackingNumber IS NULL OR s.trackingNumber = '') " +
           "AND s.clientOrderCode IS NOT NULL AND s.clientOrderCode <> '' " +
           "AND s.adminReviewRequired = false " +
           "AND (s.claimToken IS NULL OR s.claimExpiresAt IS NULL OR s.claimExpiresAt <= :now) " +
           "AND (s.nextReconciliationAt IS NULL OR s.nextReconciliationAt <= :now) " +
           "AND s.createdAt <= :cooldownThreshold " +
           "ORDER BY s.updatedAt ASC")
    List<ShipmentEntity> findUntrackedPendingShipmentsForReconciliation(
            @Param("now") Instant now,
            @Param("cooldownThreshold") Instant cooldownThreshold,
            Pageable pageable
    );

    List<ShipmentEntity> findByAdminReviewRequiredTrueOrderByUpdatedAtDesc();
}
