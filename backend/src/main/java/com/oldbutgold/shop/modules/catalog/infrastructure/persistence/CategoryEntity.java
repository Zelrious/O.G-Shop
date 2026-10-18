package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "categories", schema = "og_compat")
public class CategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(name = "parent_category_id")
    private Long parentCategoryId;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CategoryEntity() {
    }

    public CategoryEntity(String categoryName, String slug, String description, boolean active, int displayOrder, Instant createdAt) {
        this.categoryName = categoryName;
        this.slug = slug;
        this.description = description;
        this.active = active;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getParentCategoryId() { return parentCategoryId; }
    public String getCategoryName() { return categoryName; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
