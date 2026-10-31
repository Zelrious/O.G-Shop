package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CaseDecisionRepository extends JpaRepository<CaseDecisionEntity, Long> {

    List<CaseDecisionEntity> findByRoundIdOrderByDecidedAtAsc(Long roundId);

    Optional<CaseDecisionEntity> findByCommandKey(String commandKey);
}

