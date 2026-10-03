package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<VoucherEntity, Long> {
    Optional<VoucherEntity> findByCodeAndIsActiveTrue(String code);

    List<VoucherEntity> findByIsActiveTrueAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
            Instant now1, Instant now2
    );
}
