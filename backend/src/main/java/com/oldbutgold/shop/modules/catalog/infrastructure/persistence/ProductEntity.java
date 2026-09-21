package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import com.oldbutgold.shop.modules.catalog.application.ProductStateConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "products")
public class ProductEntity {
    public static final String CURRENCY_VND = "VND";
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_HIDDEN = "HIDDEN";
    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_SOLD = "SOLD";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final Set<String> EDITABLE_STATUSES = Set.of(STATUS_DRAFT, STATUS_ACTIVE, STATUS_HIDDEN);
    public static final Set<String> ALLOWED_CONDITIONS = Set.of("LIKE_NEW", "GOOD", "FAIR", "POOR", "FOR_PARTS");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "listed_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal listedPrice;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = CURRENCY_VND;

    @Column(name = "condition", nullable = false, length = 20)
    private String condition;

    @Column(name = "usage_duration", length = 100)
    private String usageDuration;

    @Column(columnDefinition = "text")
    private String defects;

    @Column(name = "repair_history", columnDefinition = "text")
    private String repairHistory;

    @Column(name = "included_accessories", columnDefinition = "text")
    private String includedAccessories;

    @Column(length = 255)
    private String location;

    @Column(nullable = false, length = 20)
    private String status = STATUS_DRAFT;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "reserved_order_id")
    private Long reservedOrderId;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ProductEntity() {
    }

    public ProductEntity(Long sellerId, Long categoryId, String title, String description,
                         BigDecimal listedPrice, String condition, String usageDuration,
                         String defects, String repairHistory, String includedAccessories,
                         String location, Instant now) {
        this.sellerId = sellerId;
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.listedPrice = listedPrice;
        this.currency = CURRENCY_VND;
        this.condition = condition;
        this.usageDuration = usageDuration;
        this.defects = defects;
        this.repairHistory = repairHistory;
        this.includedAccessories = includedAccessories;
        this.location = location;
        this.status = STATUS_DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(Long categoryId, String title, String description,
                              BigDecimal listedPrice, String condition, String usageDuration,
                              String defects, String repairHistory, String includedAccessories,
                              String location, Instant now) {
        if (!EDITABLE_STATUSES.contains(this.status)) {
            throw new ProductStateConflictException(
                    "Không thể chỉnh sửa sản phẩm khi đang ở trạng thái: " + this.status
            );
        }
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.listedPrice = listedPrice;
        this.condition = condition;
        this.usageDuration = usageDuration;
        this.defects = defects;
        this.repairHistory = repairHistory;
        this.includedAccessories = includedAccessories;
        this.location = location;
        this.updatedAt = now;
    }

    public void publish(Instant now) {
        if (STATUS_ACTIVE.equals(this.status)) {
            return; // idempotent
        }
        if (!STATUS_DRAFT.equals(this.status) && !STATUS_HIDDEN.equals(this.status)) {
            throw new ProductStateConflictException(
                    "Chỉ có thể đăng bán sản phẩm ở trạng thái DRAFT hoặc HIDDEN. Trạng thái hiện tại: " + this.status
            );
        }
        this.status = STATUS_ACTIVE;
        this.updatedAt = now;
    }

    public void hide(Instant now) {
        if (STATUS_HIDDEN.equals(this.status)) {
            return; // idempotent
        }
        if (!STATUS_ACTIVE.equals(this.status)) {
            throw new ProductStateConflictException(
                    "Chỉ có thể ẩn sản phẩm khi đang ở trạng thái ACTIVE. Trạng thái hiện tại: " + this.status
            );
        }
        this.status = STATUS_HIDDEN;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public Long getCategoryId() { return categoryId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getListedPrice() { return listedPrice; }
    public String getCurrency() { return currency; }
    public String getCondition() { return condition; }
    public String getUsageDuration() { return usageDuration; }
    public String getDefects() { return defects; }
    public String getRepairHistory() { return repairHistory; }
    public String getIncludedAccessories() { return includedAccessories; }
    public String getLocation() { return location; }
    public String getStatus() { return status; }
    public Instant getReservedUntil() { return reservedUntil; }
    public Long getReservedOrderId() { return reservedOrderId; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
