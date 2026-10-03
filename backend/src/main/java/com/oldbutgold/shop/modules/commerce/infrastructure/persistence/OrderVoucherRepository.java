package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderVoucherRepository extends JpaRepository<OrderVoucherEntity, Long> {
    List<OrderVoucherEntity> findByOrderId(Long orderId);

    Optional<OrderVoucherEntity> findByOrderIdAndVoucherId(Long orderId, Long voucherId);
}
