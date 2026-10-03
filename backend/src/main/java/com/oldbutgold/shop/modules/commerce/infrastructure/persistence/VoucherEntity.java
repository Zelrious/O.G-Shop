package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "vouchers")
public class VoucherEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voucher_id")
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "voucher_type", nullable = false, length = 30)
    private String voucherType;

    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount_amount", precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "sponsor_type", nullable = false, length = 20)
    private String sponsorType = "PLATFORM";

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "total_usage_limit")
    private Integer totalUsageLimit;

    @Column(name = "current_usage_count", nullable = false)
    private int currentUsageCount = 0;

    @Column(name = "max_usage_per_user", nullable = false)
    private int maxUsagePerUser = 1;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VoucherEntity() {}

    public void incrementUsage() {
        this.currentUsageCount++;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getVoucherType() { return voucherType; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMaxDiscountAmount() { return maxDiscountAmount; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public String getSponsorType() { return sponsorType; }
    public Long getSellerId() { return sellerId; }
    public Integer getTotalUsageLimit() { return totalUsageLimit; }
    public int getCurrentUsageCount() { return currentUsageCount; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public boolean isActive() { return isActive; }
}
