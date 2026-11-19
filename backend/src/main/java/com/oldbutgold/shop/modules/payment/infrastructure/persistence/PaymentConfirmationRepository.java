package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentConfirmationRepository extends JpaRepository<PaymentConfirmationEntity, UUID> {
    List<PaymentConfirmationEntity> findByIntentId(UUID intentId);
    Optional<PaymentConfirmationEntity> findByAttemptId(Long attemptId);
    Optional<PaymentConfirmationEntity> findByIntentIdAndDisposition(UUID intentId, String disposition);
    Optional<PaymentConfirmationEntity> findByProviderAndProviderTransactionId(String provider, String providerTransactionId);
}

