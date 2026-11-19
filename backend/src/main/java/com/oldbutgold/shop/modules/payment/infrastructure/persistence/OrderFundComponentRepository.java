package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderFundComponentRepository extends JpaRepository<OrderFundComponentEntity, UUID> {
    List<OrderFundComponentEntity> findByOrderId(Long orderId);
    Optional<OrderFundComponentEntity> findByOrderIdAndComponentType(Long orderId, String componentType);
}

