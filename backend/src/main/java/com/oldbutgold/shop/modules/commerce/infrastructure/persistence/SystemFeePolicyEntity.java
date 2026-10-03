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
@Table(name = "system_fee_policies")
public class SystemFeePolicyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fee_policy_id")
    private Long id;

    @Column(name = "policy_code", nullable = false, length = 50, unique = true)
    private String policyCode;

    @Column(name = "policy_name", nullable = false, length = 120)
    private String policyName;

    @Column(name = "buyer_fee_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal buyerFeeRate;

    @Column(name = "buyer_fixed_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal buyerFixedFee;

    @Column(name = "seller_fee_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal sellerFeeRate;

    @Column(name = "seller_fixed_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal sellerFixedFee;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    public SystemFeePolicyEntity() {}

    public Long getId() { return id; }
    public String getPolicyCode() { return policyCode; }
    public String getStatus() { return status; }
    public BigDecimal getBuyerFeeRate() { return buyerFeeRate; }
    public BigDecimal getBuyerFixedFee() { return buyerFixedFee; }
    public BigDecimal getSellerFeeRate() { return sellerFeeRate; }
    public BigDecimal getSellerFixedFee() { return sellerFixedFee; }
}
