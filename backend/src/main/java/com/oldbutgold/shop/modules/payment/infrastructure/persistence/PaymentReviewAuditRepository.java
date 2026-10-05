package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentReviewAuditRepository extends JpaRepository<PaymentReviewAuditEntity, Long> {

    List<PaymentReviewAuditEntity> findByOrderId(Long orderId);

    List<PaymentReviewAuditEntity> findByPaymentId(Long paymentId);
}
