package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRevisionRepository extends JpaRepository<ProductRevisionEntity, Long> {
    Optional<ProductRevisionEntity> findFirstByProductIdAndStateOrderByRevisionNoDesc(Long productId, String state);
    Optional<ProductRevisionEntity> findFirstByProductIdOrderByRevisionNoDesc(Long productId);
}

