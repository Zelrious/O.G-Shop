package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RewardPolicyRepository extends JpaRepository<RewardPolicyEntity, Long> {
    Optional<RewardPolicyEntity> findFirstByPolicyCodeOrderByPolicyIdAsc(String policyCode);
    Optional<RewardPolicyEntity> findByPolicyCode(String policyCode);
}

