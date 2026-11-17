package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, UUID> {
    List<InventoryReservationEntity> findByOrderItemId(Long orderItemId);
    Optional<InventoryReservationEntity> findByCommandKey(String commandKey);
}

