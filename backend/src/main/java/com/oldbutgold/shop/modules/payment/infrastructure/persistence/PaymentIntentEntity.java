package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intents", schema = "og_compat")
public class PaymentIntentEntity {

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_CONFIRMED = "CONFIRMED";
    public static final String STATE_EXPIRED = "EXPIRED";
    public static final String STATE_CANCELLED = "CANCELLED";

    public static final String PURPOSE_CHECKOUT_GROUP = "CHECKOUT_GROUP";
    public static final String PURPOSE_CHECKOUT = "CHECKOUT_GROUP";
    public static final String PURPOSE_LISTING_FEE = "LISTING_FEE";

    @Id
    @Column(name = "intent_id", nullable = false)
    private UUID intentId;

    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose = PURPOSE_CHECKOUT_GROUP;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "listing_charge_id")
    private UUID listingChargeId;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod = "VNPAY";

    @Column(name = "state", nullable = false, length = 20)
    private String state = STATE_PENDING;

    @Column(name = "deadline")
    private Instant deadline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "command_key", nullable = false, length = 150)
    private String commandKey;

    protected PaymentIntentEntity() {}

    public PaymentIntentEntity(UUID intentId, String purpose, UUID groupId, UUID listingChargeId,
                               BigDecimal expectedAmount, String currency, String paymentMethod,
                               String state, Instant deadline, Instant createdAt, String commandKey) {
        this.intentId = intentId;
        this.purpose = purpose != null ? purpose : PURPOSE_CHECKOUT;
        this.groupId = groupId;
        this.listingChargeId = listingChargeId;
        this.expectedAmount = expectedAmount;
        this.currency = currency != null ? currency : "VND";
        this.paymentMethod = paymentMethod != null ? paymentMethod : "VNPAY";
        this.state = state != null ? state : STATE_PENDING;
        this.deadline = deadline;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.commandKey = commandKey;
    }

    public UUID getIntentId() { return intentId; }
    public void setIntentId(UUID intentId) { this.intentId = intentId; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }

    public UUID getListingChargeId() { return listingChargeId; }
    public void setListingChargeId(UUID listingChargeId) { this.listingChargeId = listingChargeId; }

    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCommandKey() { return commandKey; }
    public void setCommandKey(String commandKey) { this.commandKey = commandKey; }
}

