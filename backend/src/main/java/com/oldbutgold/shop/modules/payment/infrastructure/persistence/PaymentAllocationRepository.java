package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocationEntity, UUID> {
    List<PaymentAllocationEntity> findByIntentId(UUID intentId);
    Optional<PaymentAllocationEntity> findByOrderId(Long orderId);
}

