package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, Long> {

    Optional<PaymentAttemptEntity> findByTxnRef(String txnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PaymentAttemptEntity a WHERE a.txnRef = :txnRef")
    Optional<PaymentAttemptEntity> findByTxnRefForUpdate(@Param("txnRef") String txnRef);

    List<PaymentAttemptEntity> findByOrderId(Long orderId);

    List<PaymentAttemptEntity> findByPaymentId(Long paymentId);

    Optional<PaymentAttemptEntity> findByPaymentIdAndStatus(Long paymentId, String status);

    List<PaymentAttemptEntity> findByIntentId(java.util.UUID intentId);

    Optional<PaymentAttemptEntity> findByIntentIdAndStatus(java.util.UUID intentId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PaymentAttemptEntity a WHERE a.intentId = :intentId AND a.status = :status")
    Optional<PaymentAttemptEntity> findByIntentIdAndStatusForUpdate(@Param("intentId") java.util.UUID intentId, @Param("status") String status);
}
