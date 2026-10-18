package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_alert_requests", schema = "og_compat")
public class PaymentAlertRequestEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long id;

    @Column(name = "case_id", nullable = false, unique = true)
    private Long caseId;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected PaymentAlertRequestEntity() {}

    public PaymentAlertRequestEntity(Long caseId, String alertType, String severity, Instant createdAt) {
        this.caseId = caseId;
        this.alertType = alertType;
        this.severity = severity;
        this.status = STATUS_PENDING;
        this.attemptCount = 0;
        this.createdAt = createdAt;
    }

    public void markSent(Instant now) {
        this.status = STATUS_SENT;
        this.sentAt = now;
    }

    public void markFailed(String error) {
        this.status = STATUS_FAILED;
        this.lastError = error;
        this.attemptCount++;
    }

    public Long getId() {
        return id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public String getAlertType() {
        return alertType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
