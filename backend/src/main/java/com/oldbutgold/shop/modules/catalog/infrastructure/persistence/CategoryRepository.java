package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    default List<CategoryEntity> findForProduct(ProductEntity product) {
        Map<Long, CategoryEntity> categories = findAllById(product.getCategoryIds()).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, category -> category));
        return orderedForProduct(product, categories);
    }

    static List<CategoryEntity> orderedForProduct(ProductEntity product, Map<Long, CategoryEntity> categories) {
        return product.getCategoryIds().stream().map(id -> {
            CategoryEntity category = categories.get(id);
            if (category == null) throw new IllegalStateException("Danh mục của sản phẩm không tồn tại.");
            return category;
        }).toList();
    }
    List<CategoryEntity> findByActiveTrueOrderByDisplayOrderAscCategoryNameAsc();
    Optional<CategoryEntity> findByIdAndActiveTrue(Long id);
    Optional<CategoryEntity> findBySlug(String slug);
}
