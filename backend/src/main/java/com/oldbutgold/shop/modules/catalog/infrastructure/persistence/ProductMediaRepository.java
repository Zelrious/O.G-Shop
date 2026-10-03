package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductMediaRepository extends JpaRepository<ProductMediaEntity, Long> {
    List<ProductMediaEntity> findByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

    List<ProductMediaEntity> findByProductIdInOrderByDisplayOrderAscIdAsc(Collection<Long> productIds);

    java.util.Optional<ProductMediaEntity> findByIdAndProductId(Long id, Long productId);

    long countByProductIdAndMediaType(Long productId, String mediaType);

    long countByProductId(Long productId);
}
