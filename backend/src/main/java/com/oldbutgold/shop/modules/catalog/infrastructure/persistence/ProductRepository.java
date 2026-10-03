package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {
    Optional<ProductEntity> findByIdAndStatusAndDeletedAtIsNull(Long id, String status);

    Optional<ProductEntity> findByIdAndSellerIdAndDeletedAtIsNull(Long id, Long sellerId);

    Page<ProductEntity> findBySellerIdAndDeletedAtIsNull(Long sellerId, Pageable pageable);

    Page<ProductEntity> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

    List<ProductEntity> findBySellerIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long sellerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductEntity> findByIdForUpdate(@Param("id") Long id);
}
