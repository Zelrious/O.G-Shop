package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_reconciliation_cases")
public class PaymentReconciliationCaseEntity {

    public static final String CASE_EXPIRED_ORDER = "EXPIRED_ORDER";
    public static final String CASE_DUPLICATE_CHARGE = "DUPLICATE_CHARGE";
    public static final String CASE_LATE_SUCCESS_ON_FAILED = "LATE_SUCCESS_ON_FAILED";
    public static final String CASE_MISMATCH_CHARGE = "MISMATCH_CHARGE";
    public static final String CASE_REFUND_PENDING = "REFUND_PENDING";

    public static final String SOURCE_IPN_EVENT = "IPN_EVENT";
    public static final String SOURCE_REVIEW_DECISION = "REVIEW_DECISION";

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_RESOLVED = "RESOLVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "attempt_id")
    private Long attemptId;

    @Column(name = "case_type", nullable = false, length = 40)
    private String caseType;

    @Column(nullable = false, length = 20)
    private String status = STATUS_OPEN;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "review_audit_id")
    private Long reviewAuditId;

    @Column(name = "vnp_transaction_no", length = 100)
    private String vnpTransactionNo;

    @Column(name = "dedup_key", nullable = false, unique = true, length = 150)
    private String dedupKey;

    @Column(name = "assigned_to", length = 50)
    private String assignedTo;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected PaymentReconciliationCaseEntity() {}

    public static PaymentReconciliationCaseEntity fromIpnEvent(Long orderId,
                                                               Long paymentId,
                                                               Long attemptId,
                                                               String caseType,
                                                               Long eventId,
                                                               String vnpTransactionNo,
                                                               String dedupKey,
                                                               Instant createdAt) {
        PaymentReconciliationCaseEntity entity = new PaymentReconciliationCaseEntity();
        entity.orderId = orderId;
        entity.paymentId = paymentId;
        entity.attemptId = attemptId;
        entity.caseType = caseType;
        entity.sourceType = SOURCE_IPN_EVENT;
        entity.eventId = eventId;
        entity.reviewAuditId = null;
        entity.vnpTransactionNo = vnpTransactionNo;
        entity.dedupKey = dedupKey;
        entity.status = STATUS_OPEN;
        entity.createdAt = createdAt;
        return entity;
    }

    public static PaymentReconciliationCaseEntity fromReviewDecision(Long orderId,
                                                                     Long paymentId,
                                                                     Long attemptId,
                                                                     Long reviewAuditId,
                                                                     String vnpTransactionNo,
                                                                     String dedupKey,
                                                                     Instant createdAt) {
        PaymentReconciliationCaseEntity entity = new PaymentReconciliationCaseEntity();
        entity.orderId = orderId;
        entity.paymentId = paymentId;
        entity.attemptId = attemptId;
        entity.caseType = CASE_REFUND_PENDING;
        entity.sourceType = SOURCE_REVIEW_DECISION;
        entity.eventId = null;
        entity.reviewAuditId = reviewAuditId;
        entity.vnpTransactionNo = vnpTransactionNo;
        entity.dedupKey = dedupKey;
        entity.status = STATUS_OPEN;
        entity.createdAt = createdAt;
        return entity;
    }

    public void resolve(String resolutionNote, String assignedTo, Instant now) {
        this.status = STATUS_RESOLVED;
        this.resolutionNote = resolutionNote;
        this.assignedTo = assignedTo;
        this.resolvedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public String getCaseType() {
        return caseType;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getReviewAuditId() {
        return reviewAuditId;
    }

    public String getVnpTransactionNo() {
        return vnpTransactionNo;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
