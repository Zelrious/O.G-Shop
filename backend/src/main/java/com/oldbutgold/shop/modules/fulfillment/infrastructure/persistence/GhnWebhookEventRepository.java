package com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GhnWebhookEventRepository extends JpaRepository<GhnWebhookEventEntity, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<GhnWebhookEventEntity> findByIdempotencyKey(String idempotencyKey);

    List<GhnWebhookEventEntity> findTop50ByStatusOrderByReceivedAtAsc(String status);

    List<GhnWebhookEventEntity> findByOrderCode(String orderCode);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE GhnWebhookEventEntity e SET e.status = 'PROCESSING', e.processingStartedAt = :now, e.retryCount = e.retryCount + 1 " +
            "WHERE e.id = :eventId AND (" +
            "  e.status = 'PENDING' OR " +
            "  (e.status = 'PROCESSING' AND e.processingStartedAt <= :staleThreshold) OR " +
            "  (e.status = 'FAILED' AND e.retryCount < :maxRetries)" +
            ")"
    )
    int claimEventForProcessing(
            @org.springframework.data.repository.query.Param("eventId") Long eventId,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now,
            @org.springframework.data.repository.query.Param("staleThreshold") java.time.Instant staleThreshold,
            @org.springframework.data.repository.query.Param("maxRetries") int maxRetries
    );

    @org.springframework.data.jpa.repository.Query(
            "SELECT e.id FROM GhnWebhookEventEntity e WHERE " +
            "e.status = 'PENDING' OR " +
            "(e.status = 'PROCESSING' AND e.processingStartedAt <= :staleThreshold) OR " +
            "(e.status = 'FAILED' AND e.retryCount < :maxRetries) " +
            "ORDER BY e.receivedAt ASC"
    )
    List<Long> findRecoverableEventIds(
            @org.springframework.data.repository.query.Param("staleThreshold") java.time.Instant staleThreshold,
            @org.springframework.data.repository.query.Param("maxRetries") int maxRetries,
            org.springframework.data.domain.Pageable pageable
    );
}
