package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_vouchers", schema = "og_compat")
public class OrderVoucherEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_voucher_id")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "voucher_id", nullable = false)
    private Long voucherId;

    @Column(name = "voucher_code_snapshot", nullable = false, length = 50)
    private String voucherCodeSnapshot;

    @Column(name = "voucher_type_snapshot", nullable = false, length = 30)
    private String voucherTypeSnapshot;

    @Column(name = "sponsor_type_snapshot", nullable = false, length = 20)
    private String sponsorTypeSnapshot;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    protected OrderVoucherEntity() {}

    public OrderVoucherEntity(Long orderId, Long voucherId, String voucherCodeSnapshot,
                              String voucherTypeSnapshot, String sponsorTypeSnapshot,
                              BigDecimal discountAmount, Instant appliedAt) {
        this.orderId = orderId;
        this.voucherId = voucherId;
        this.voucherCodeSnapshot = voucherCodeSnapshot;
        this.voucherTypeSnapshot = voucherTypeSnapshot;
        this.sponsorTypeSnapshot = sponsorTypeSnapshot;
        this.discountAmount = discountAmount;
        this.appliedAt = appliedAt;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getVoucherId() { return voucherId; }
    public String getVoucherCodeSnapshot() { return voucherCodeSnapshot; }
    public String getVoucherTypeSnapshot() { return voucherTypeSnapshot; }
    public String getSponsorTypeSnapshot() { return sponsorTypeSnapshot; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public Instant getAppliedAt() { return appliedAt; }
}
