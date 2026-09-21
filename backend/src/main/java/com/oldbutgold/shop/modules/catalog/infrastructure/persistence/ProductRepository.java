package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {
    Optional<ProductEntity> findByIdAndStatusAndDeletedAtIsNull(Long id, String status);

    Optional<ProductEntity> findByIdAndSellerIdAndDeletedAtIsNull(Long id, Long sellerId);

    Page<ProductEntity> findBySellerIdAndDeletedAtIsNull(Long sellerId, Pageable pageable);

    List<ProductEntity> findBySellerIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long sellerId);
}
