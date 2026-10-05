package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentAlertRequestRepository extends JpaRepository<PaymentAlertRequestEntity, Long> {

    Optional<PaymentAlertRequestEntity> findByCaseId(Long caseId);

    List<PaymentAlertRequestEntity> findByStatus(String status);
}
