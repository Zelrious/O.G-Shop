package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseEvidenceRequestRepository extends JpaRepository<CaseEvidenceRequestEntity, UUID> {

    Optional<CaseEvidenceRequestEntity> findByCommandKey(String commandKey);

    Optional<CaseEvidenceRequestEntity> findByFulfillmentCommandKey(String fulfillmentCommandKey);

    Optional<CaseEvidenceRequestEntity> findByIdAndCaseId(UUID id, Long caseId);

    Optional<CaseEvidenceRequestEntity> findByCaseIdAndStatus(Long caseId, String status);

    List<CaseEvidenceRequestEntity> findByCaseIdOrderByIssuedAtAsc(Long caseId);
}

