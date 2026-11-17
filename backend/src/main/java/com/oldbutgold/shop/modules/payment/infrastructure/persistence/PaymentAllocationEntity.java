package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payment_allocations", schema = "og_compat")
public class PaymentAllocationEntity {

    @Id
    @Column(name = "allocation_id", nullable = false)
    private UUID allocationId;

    @Column(name = "intent_id", nullable = false)
    private UUID intentId;

    @Column(name = "confirmation_id", nullable = false)
    private UUID confirmationId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected PaymentAllocationEntity() {}

    public PaymentAllocationEntity(UUID allocationId, UUID intentId, UUID confirmationId, Long orderId, BigDecimal amount) {
        this.allocationId = allocationId != null ? allocationId : UUID.randomUUID();
        this.intentId = intentId;
        this.confirmationId = confirmationId;
        this.orderId = orderId;
        this.amount = amount;
    }

    public UUID getAllocationId() { return allocationId; }
    public void setAllocationId(UUID allocationId) { this.allocationId = allocationId; }

    public UUID getIntentId() { return intentId; }
    public void setIntentId(UUID intentId) { this.intentId = intentId; }

    public UUID getConfirmationId() { return confirmationId; }
    public void setConfirmationId(UUID confirmationId) { this.confirmationId = confirmationId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

