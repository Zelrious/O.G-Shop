package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RewardLedgerRepository extends JpaRepository<RewardLedgerEntity, UUID> {
    List<RewardLedgerEntity> findByUserId(Long userId);
    Optional<RewardLedgerEntity> findByGroupIdAndEntryType(UUID groupId, String entryType);
    Optional<RewardLedgerEntity> findByCommandKey(String commandKey);
}

