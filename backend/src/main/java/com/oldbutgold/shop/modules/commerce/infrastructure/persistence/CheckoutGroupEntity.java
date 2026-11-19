package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_groups", schema = "og_compat")
public class CheckoutGroupEntity {

    public static final String STATE_READY = "READY";
    public static final String STATE_PAYMENT_PENDING = "PAYMENT_PENDING";
    public static final String STATE_PAID = "PAID";
    public static final String STATE_EXPIRED = "EXPIRED";
    public static final String STATE_CANCELLED = "CANCELLED";

    @Id
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "command_key", nullable = false, length = 150)
    private String commandKey;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "request_digest", length = 64)
    private String requestDigest;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "address_snapshot", columnDefinition = "jsonb")
    private String addressSnapshot;

    @Column(name = "delivery_method", length = 20)
    private String deliveryMethod;

    @Column(name = "carrier", length = 100)
    private String carrier;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "expected_total", precision = 19, scale = 2)
    private BigDecimal expectedTotal;

    @Column(name = "state", nullable = false, length = 20)
    private String state = STATE_READY;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Column(name = "reward_policy_id")
    private Long rewardPolicyId;

    protected CheckoutGroupEntity() {}

    public CheckoutGroupEntity(UUID groupId, Long buyerId, String commandKey, String requestDigest,
                               String addressSnapshot, String deliveryMethod, String carrier,
                               String paymentMethod, BigDecimal expectedTotal, String state,
                               Instant createdAt, Instant paymentDueAt, Long rewardPolicyId) {
        this.groupId = groupId;
        this.buyerId = buyerId;
        this.commandKey = commandKey;
        this.requestDigest = requestDigest;
        this.addressSnapshot = addressSnapshot;
        this.deliveryMethod = deliveryMethod;
        this.carrier = carrier;
        this.paymentMethod = paymentMethod;
        this.expectedTotal = expectedTotal;
        this.state = state != null ? state : STATE_READY;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.paymentDueAt = paymentDueAt;
        this.rewardPolicyId = rewardPolicyId;
    }

    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }

    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }

    public String getCommandKey() { return commandKey; }
    public void setCommandKey(String commandKey) { this.commandKey = commandKey; }

    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String requestDigest) { this.requestDigest = requestDigest; }

    public String getAddressSnapshot() { return addressSnapshot; }
    public void setAddressSnapshot(String addressSnapshot) { this.addressSnapshot = addressSnapshot; }

    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public BigDecimal getExpectedTotal() { return expectedTotal; }
    public void setExpectedTotal(BigDecimal expectedTotal) { this.expectedTotal = expectedTotal; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPaymentDueAt() { return paymentDueAt; }
    public void setPaymentDueAt(Instant paymentDueAt) { this.paymentDueAt = paymentDueAt; }

    public Long getRewardPolicyId() { return rewardPolicyId; }
    public void setRewardPolicyId(Long rewardPolicyId) { this.rewardPolicyId = rewardPolicyId; }
}
