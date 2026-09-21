package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    List<CategoryEntity> findByActiveTrueOrderByDisplayOrderAscCategoryNameAsc();
    Optional<CategoryEntity> findByIdAndActiveTrue(Long id);
    Optional<CategoryEntity> findBySlug(String slug);
}
