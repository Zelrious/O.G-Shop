package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reward_policies", schema = "og_compat")
public class RewardPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "policy_code", nullable = false, length = 60)
    private String policyCode;

    @Column(name = "point_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal pointValue;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "policy_snapshot", columnDefinition = "jsonb", nullable = false)
    private String policySnapshot;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RewardPolicyEntity() {}

    public RewardPolicyEntity(String policyCode, BigDecimal pointValue, String policySnapshot, Long createdBy, Instant createdAt) {
        this.policyCode = policyCode;
        this.pointValue = pointValue;
        this.policySnapshot = policySnapshot;
        this.createdBy = createdBy;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long policyId) { this.policyId = policyId; }

    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }

    public BigDecimal getPointValue() { return pointValue; }
    public void setPointValue(BigDecimal pointValue) { this.pointValue = pointValue; }

    public String getPolicySnapshot() { return policySnapshot; }
    public void setPolicySnapshot(String policySnapshot) { this.policySnapshot = policySnapshot; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
