package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reward_ledger", schema = "og_compat")
public class RewardLedgerEntity {

    public static final String ENTRY_CREDIT_COMPLETION = "CREDIT_COMPLETION";
    public static final String ENTRY_DEBIT_CHECKOUT = "DEBIT_CHECKOUT";
    public static final String ENTRY_CORRECTION = "CORRECTION";

    @Id
    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType;

    @Column(name = "points_delta", nullable = false)
    private Long pointsDelta;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "command_key", nullable = false, length = 150)
    private String commandKey;

    protected RewardLedgerEntity() {}

    public RewardLedgerEntity(UUID entryId, Long userId, Long policyId, String entryType,
                              Long pointsDelta, Long orderId, UUID groupId, Long actorId,
                              String reason, Instant createdAt, String commandKey) {
        this.entryId = entryId != null ? entryId : UUID.randomUUID();
        this.userId = userId;
        this.policyId = policyId;
        this.entryType = entryType;
        this.pointsDelta = pointsDelta;
        this.orderId = orderId;
        this.groupId = groupId;
        this.actorId = actorId;
        this.reason = reason;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.commandKey = commandKey;
    }

    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID entryId) { this.entryId = entryId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long policyId) { this.policyId = policyId; }

    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }

    public Long getPointsDelta() { return pointsDelta; }
    public void setPointsDelta(Long pointsDelta) { this.pointsDelta = pointsDelta; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCommandKey() { return commandKey; }
    public void setCommandKey(String commandKey) { this.commandKey = commandKey; }
}

