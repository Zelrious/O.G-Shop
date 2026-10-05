package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VnPayAttemptRepository extends JpaRepository<VnPayAttemptEntity, String> {
    @org.springframework.data.jpa.repository.Query("select a.orderId from VnPayAttemptEntity a where a.reference = :reference")
    Optional<Long> findOrderIdByReference(@org.springframework.data.repository.query.Param("reference") String reference);
    Optional<VnPayAttemptEntity> findFirstByOrderIdAndProcessedAtIsNullOrderByCreatedAtDesc(Long orderId);
}
