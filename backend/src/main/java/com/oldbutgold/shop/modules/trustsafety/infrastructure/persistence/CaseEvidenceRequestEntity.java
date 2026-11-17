package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity mapping to public.case_evidence_requests table (also exposed via og_compat.case_evidence_requests view).
 * Tracks KTV requests for additional damage evidence in Round 2 and Seller resubmission fulfillment.
 * DDL: V38__bd5_damage_exemption_resubmission.sql.
 */
@Entity
@Table(name = "case_evidence_requests", schema = "public")
public class CaseEvidenceRequestEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FULFILLED = "FULFILLED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @Column(name = "request_id", nullable = false)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "ktv_id", nullable = false)
    private Long ktvId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_items", columnDefinition = "jsonb")
    private String requestedItems;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expired_notified_at")
    private Instant expiredNotifiedAt;

    @Column(name = "command_key", nullable = false, length = 150)
    private String commandKey;

    @JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "fulfillment_command_key", length = 150)
    private String fulfillmentCommandKey;

    @JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "fulfillment_payload_hash", length = 64)
    private String fulfillmentPayloadHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CaseEvidenceRequestEntity() {
    }

    public CaseEvidenceRequestEntity(
            UUID id,
            Long caseId,
            Long roundId,
            Long ktvId,
            String reason,
            String requestedItems,
            Instant issuedAt,
            Instant deadlineAt,
            String commandKey,
            String payloadHash
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.caseId = caseId;
        this.roundId = roundId;
        this.ktvId = ktvId;
        this.status = STATUS_PENDING;
        this.reason = reason;
        this.requestedItems = requestedItems;
        this.issuedAt = issuedAt != null ? issuedAt : Instant.now();
        this.deadlineAt = deadlineAt;
        this.commandKey = commandKey;
        this.payloadHash = payloadHash;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public Long getRoundId() {
        return roundId;
    }

    public void setRoundId(Long roundId) {
        this.roundId = roundId;
    }

    public Long getKtvId() {
        return ktvId;
    }

    public void setKtvId(Long ktvId) {
        this.ktvId = ktvId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
        this.updatedAt = Instant.now();
    }

    public String getRequestedItems() {
        return requestedItems;
    }

    public void setRequestedItems(String requestedItems) {
        this.requestedItems = requestedItems;
        this.updatedAt = Instant.now();
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(Instant deadlineAt) {
        this.deadlineAt = deadlineAt;
        this.updatedAt = Instant.now();
    }

    public Instant getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(Instant fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
        this.updatedAt = Instant.now();
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
        this.updatedAt = Instant.now();
    }

    public Instant getExpiredNotifiedAt() {
        return expiredNotifiedAt;
    }

    public void setExpiredNotifiedAt(Instant expiredNotifiedAt) {
        this.expiredNotifiedAt = expiredNotifiedAt;
        this.updatedAt = Instant.now();
    }

    public String getCommandKey() {
        return commandKey;
    }

    public void setCommandKey(String commandKey) {
        this.commandKey = commandKey;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getFulfillmentCommandKey() {
        return fulfillmentCommandKey;
    }

    public void setFulfillmentCommandKey(String fulfillmentCommandKey) {
        this.fulfillmentCommandKey = fulfillmentCommandKey;
    }

    public String getFulfillmentPayloadHash() {
        return fulfillmentPayloadHash;
    }

    public void setFulfillmentPayloadHash(String fulfillmentPayloadHash) {
        this.fulfillmentPayloadHash = fulfillmentPayloadHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
