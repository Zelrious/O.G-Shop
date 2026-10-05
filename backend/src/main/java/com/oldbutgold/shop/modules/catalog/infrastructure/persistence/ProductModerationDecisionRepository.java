package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductModerationDecisionRepository extends JpaRepository<ProductModerationDecisionEntity, Long> {
    Optional<ProductModerationDecisionEntity> findByCommandKey(String commandKey);

    Optional<ProductModerationDecisionEntity> findFirstByProductIdOrderByCreatedAtDescIdDesc(Long productId);

    List<ProductModerationDecisionEntity> findByProductIdInOrderByCreatedAtDescIdDesc(Collection<Long> productIds);

    List<ProductModerationDecisionEntity> findByProductIdOrderByCreatedAtDescIdDesc(Long productId);
}
