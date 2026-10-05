package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentIpnEventRepository extends JpaRepository<PaymentIpnEventEntity, Long> {

    List<PaymentIpnEventEntity> findByAttemptId(Long attemptId);

    List<PaymentIpnEventEntity> findByOrderId(Long orderId);

    Optional<PaymentIpnEventEntity> findByReceiptId(Long receiptId);

    long countByAttemptId(Long attemptId);

    boolean existsByAttemptIdAndVnpTransactionNo(Long attemptId, String vnpTransactionNo);
}
