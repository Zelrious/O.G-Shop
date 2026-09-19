package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerVerificationRepository extends JpaRepository<SellerVerificationEntity, Long> {
    Optional<SellerVerificationEntity> findFirstByUserIdAndStatus(long userId, String status);
}
