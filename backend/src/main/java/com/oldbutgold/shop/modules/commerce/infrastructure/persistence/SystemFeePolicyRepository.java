package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemFeePolicyRepository extends JpaRepository<SystemFeePolicyEntity, Long> {
    Optional<SystemFeePolicyEntity> findFirstByStatusOrderByEffectiveFromDesc(String status);
}
