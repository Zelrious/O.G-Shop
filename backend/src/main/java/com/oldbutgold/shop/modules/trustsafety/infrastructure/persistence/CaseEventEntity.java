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
 * JPA Entity mapping to og_compat.case_events table/view.
 * Stores audit events and timeline milestones for cases and rounds.
 * Dẫn chứng migration: V19 (line 105-120), V22 (line 2924).
 */
@Entity
@Table(name = "case_events", schema = "og_compat")
public class CaseEventEntity {

    public static final String TYPE_RETURN_REQUESTED = "RETURN_REQUESTED";
    public static final String TYPE_ROUND_DECIDED = "ROUND_DECIDED";
    public static final String TYPE_EVIDENCE_SUBMITTED = "EVIDENCE_SUBMITTED";
    public static final String TYPE_RETURN_SHIPPED = "RETURN_SHIPPED";
    public static final String TYPE_RETURN_DELIVERED = "RETURN_DELIVERED";
    public static final String TYPE_DAMAGE_EXEMPTION_REQUESTED = "DAMAGE_EXEMPTION_REQUESTED";
    public static final String TYPE_ESCALATED = "ESCALATED";
    public static final String TYPE_REQUEST_WITHDRAWN = "REQUEST_WITHDRAWN";
    public static final String TYPE_CASE_CLOSED = "CASE_CLOSED";

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "round_id")
    private Long roundId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "recipient_id")
    private Long recipientId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "command_key", length = 150)
    private String commandKey;

    protected CaseEventEntity() {
    }

    public CaseEventEntity(UUID id, Long caseId, Long roundId, Long actorId, Long recipientId,
                           String eventType, String reason, String metadata, String commandKey) {
        this.id = id != null ? id : UUID.randomUUID();
        this.caseId = caseId;
        this.roundId = roundId;
        this.actorId = actorId;
        this.recipientId = recipientId;
        this.eventType = eventType;
        this.reason = reason;
        this.metadata = metadata;
        this.commandKey = commandKey;
        this.occurredAt = Instant.now();
    }

    // Getters and Setters

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

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getCommandKey() {
        return commandKey;
    }

    public void setCommandKey(String commandKey) {
        this.commandKey = commandKey;
    }
}
