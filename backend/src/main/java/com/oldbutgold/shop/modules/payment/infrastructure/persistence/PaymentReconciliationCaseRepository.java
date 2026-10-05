package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentReconciliationCaseRepository extends JpaRepository<PaymentReconciliationCaseEntity, Long> {

    Optional<PaymentReconciliationCaseEntity> findByDedupKey(String dedupKey);

    boolean existsByDedupKey(String dedupKey);

    List<PaymentReconciliationCaseEntity> findByOrderId(Long orderId);

    List<PaymentReconciliationCaseEntity> findByStatus(String status);
}
