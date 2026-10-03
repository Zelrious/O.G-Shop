package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<AddressEntity, Long> {
    List<AddressEntity> findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(Long userId);

    Optional<AddressEntity> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    Optional<AddressEntity> findByUserIdAndIsDefaultTrueAndDeletedAtIsNull(Long userId);
}
