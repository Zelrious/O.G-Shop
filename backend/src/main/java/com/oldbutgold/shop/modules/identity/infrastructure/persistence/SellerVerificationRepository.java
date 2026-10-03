package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SellerVerificationRepository extends JpaRepository<SellerVerificationEntity, Long> {
    Optional<SellerVerificationEntity> findFirstByUserIdAndStatus(long userId, String status);
    Optional<SellerVerificationEntity> findFirstByUserIdAndStatusIn(long userId, Collection<String> statuses);
    List<SellerVerificationEntity> findByUserIdInAndStatus(Collection<Long> userIds, String status);
}
