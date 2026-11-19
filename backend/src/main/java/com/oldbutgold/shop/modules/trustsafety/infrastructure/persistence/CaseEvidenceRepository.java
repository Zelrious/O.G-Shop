package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseEvidenceRepository extends JpaRepository<CaseEvidenceEntity, UUID> {

    List<CaseEvidenceEntity> findByRoundIdAndDeletedAtIsNullOrderBySubmittedAtAsc(Long roundId);

    List<CaseEvidenceEntity> findByRoundIdOrderBySubmittedAtAsc(Long roundId);
}

