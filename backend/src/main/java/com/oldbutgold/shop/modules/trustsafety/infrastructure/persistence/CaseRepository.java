package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseEntity, Long> {

    List<CaseEntity> findByOrderId(Long orderId);

    @Query("SELECT c FROM CaseEntity c WHERE c.orderId = :orderId AND c.state NOT IN ('CLOSED', 'REJECTED')")
    Optional<CaseEntity> findActiveCaseByOrderId(@Param("orderId") Long orderId);

    Optional<CaseEntity> findByCommandKey(String commandKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CaseEntity c WHERE c.id = :id")
    Optional<CaseEntity> findByIdForUpdate(@Param("id") Long id);
}
