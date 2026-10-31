package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseEventRepository extends JpaRepository<CaseEventEntity, UUID> {

    List<CaseEventEntity> findByCaseIdOrderByOccurredAtAsc(Long caseId);

    Optional<CaseEventEntity> findByCommandKey(String commandKey);
}

