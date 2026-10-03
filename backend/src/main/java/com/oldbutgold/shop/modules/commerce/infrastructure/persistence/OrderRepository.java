package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Page<OrderEntity> findByBuyerIdOrderByCreatedAtDesc(Long buyerId, Pageable pageable);

    Page<OrderEntity> findByBuyerIdOrderByCreatedAtDescIdDesc(Long buyerId, Pageable pageable);

    Page<OrderEntity> findByBuyerIdAndStatusOrderByCreatedAtDesc(Long buyerId, String status, Pageable pageable);

    Page<OrderEntity> findByBuyerIdAndStatusOrderByCreatedAtDescIdDesc(Long buyerId, String status, Pageable pageable);

    Page<OrderEntity> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    Page<OrderEntity> findBySellerIdOrderByCreatedAtDescIdDesc(Long sellerId, Pageable pageable);

    Page<OrderEntity> findBySellerIdAndStatusOrderByCreatedAtDesc(Long sellerId, String status, Pageable pageable);

    Page<OrderEntity> findBySellerIdAndStatusOrderByCreatedAtDescIdDesc(Long sellerId, String status, Pageable pageable);

    Optional<OrderEntity> findByIdAndBuyerId(Long id, Long buyerId);

    Optional<OrderEntity> findByIdAndSellerId(Long id, Long sellerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT o FROM OrderEntity o WHERE o.status = 'PAYMENT_PENDING' AND o.paymentDueAt < :now")
    List<OrderEntity> findOverduePendingOrders(@Param("now") Instant now);
}
