package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckoutGroupRepository extends JpaRepository<CheckoutGroupEntity, UUID> {
    Optional<CheckoutGroupEntity> findByCommandKey(String commandKey);
    Optional<CheckoutGroupEntity> findByRequestDigest(String requestDigest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM CheckoutGroupEntity g WHERE g.groupId = :groupId")
    Optional<CheckoutGroupEntity> findByIdForUpdate(@Param("groupId") UUID groupId);
}

