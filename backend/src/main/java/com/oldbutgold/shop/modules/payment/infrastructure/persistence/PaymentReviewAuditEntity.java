package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_review_audits")
public class PaymentReviewAuditEntity {

    public static final String DECISION_APPROVED_SAFE = "APPROVED_SAFE";
    public static final String DECISION_REJECTED_FRAUD = "REJECTED_FRAUD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_audit_id")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "attempt_id")
    private Long attemptId;

    @Column(name = "decided_by", nullable = false, length = 50)
    private String decidedBy;

    @Column(nullable = false, length = 30)
    private String decision;

    @Column(name = "reason_note", nullable = false, columnDefinition = "TEXT")
    private String reasonNote;

    @Column(name = "vnp_verification_ref", length = 100)
    private String vnpVerificationRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentReviewAuditEntity() {}

    public PaymentReviewAuditEntity(Long orderId,
                                    Long paymentId,
                                    Long attemptId,
                                    String decidedBy,
                                    String decision,
                                    String reasonNote,
                                    String vnpVerificationRef,
                                    Instant createdAt) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.attemptId = attemptId;
        this.decidedBy = decidedBy;
        this.decision = decision;
        this.reasonNote = reasonNote;
        this.vnpVerificationRef = vnpVerificationRef;
        this.createdAt = createdAt;
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

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getDecision() {
        return decision;
    }

    public String getReasonNote() {
        return reasonNote;
    }

    public String getVnpVerificationRef() {
        return vnpVerificationRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
