package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_fund_components", schema = "og_compat")
public class OrderFundComponentEntity {

    public static final String TYPE_GOODS_CASH = "GOODS_CASH";
    public static final String TYPE_SHIPPING_CASH = "SHIPPING_CASH";
    public static final String TYPE_PLATFORM_GOODS_SUBSIDY = "PLATFORM_GOODS_SUBSIDY";
    public static final String TYPE_PLATFORM_SHIPPING_SUBSIDY = "PLATFORM_SHIPPING_SUBSIDY";
    public static final String TYPE_LATE_PAYMENT_CASH = "LATE_PAYMENT_CASH";

    @Id
    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "allocation_id")
    private UUID allocationId;

    @Column(name = "late_confirmation_id")
    private UUID lateConfirmationId;

    @Column(name = "component_type", nullable = false, length = 30)
    private String componentType;

    @Column(name = "confirmed_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal confirmedAmount = BigDecimal.ZERO;

    @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "released_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal releasedAmount = BigDecimal.ZERO;

    @Column(name = "funding_reference", length = 150)
    private String fundingReference;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    protected OrderFundComponentEntity() {}

    public OrderFundComponentEntity(UUID componentId, Long orderId, UUID allocationId,
                                    UUID lateConfirmationId, String componentType,
                                    BigDecimal confirmedAmount, String fundingReference,
                                    Instant confirmedAt) {
        this.componentId = componentId != null ? componentId : UUID.randomUUID();
        this.orderId = orderId;
        this.allocationId = allocationId;
        this.lateConfirmationId = lateConfirmationId;
        this.componentType = componentType;
        this.confirmedAmount = confirmedAmount != null ? confirmedAmount : BigDecimal.ZERO;
        this.reservedAmount = BigDecimal.ZERO;
        this.refundedAmount = BigDecimal.ZERO;
        this.releasedAmount = BigDecimal.ZERO;
        this.fundingReference = fundingReference;
        this.confirmedAt = confirmedAt != null ? confirmedAt : Instant.now();
        this.version = 0L;
    }

    public UUID getComponentId() { return componentId; }
    public void setComponentId(UUID componentId) { this.componentId = componentId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public UUID getAllocationId() { return allocationId; }
    public void setAllocationId(UUID allocationId) { this.allocationId = allocationId; }

    public UUID getLateConfirmationId() { return lateConfirmationId; }
    public void setLateConfirmationId(UUID lateConfirmationId) { this.lateConfirmationId = lateConfirmationId; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public BigDecimal getConfirmedAmount() { return confirmedAmount; }
    public void setConfirmedAmount(BigDecimal confirmedAmount) { this.confirmedAmount = confirmedAmount; }

    public BigDecimal getReservedAmount() { return reservedAmount; }
    public void setReservedAmount(BigDecimal reservedAmount) { this.reservedAmount = reservedAmount; }

    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }

    public BigDecimal getReleasedAmount() { return releasedAmount; }
    public void setReleasedAmount(BigDecimal releasedAmount) { this.releasedAmount = releasedAmount; }

    public String getFundingReference() { return fundingReference; }
    public void setFundingReference(String fundingReference) { this.fundingReference = fundingReference; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

