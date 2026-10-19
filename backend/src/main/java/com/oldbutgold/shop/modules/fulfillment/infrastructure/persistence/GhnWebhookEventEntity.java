package com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ghn_webhook_events")
public class GhnWebhookEventEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_DUPLICATE = "DUPLICATE";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    public static final String STATUS_UNMATCHED = "UNMATCHED";
    public static final String STATUS_IGNORED = "IGNORED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "order_code", length = 100)
    private String orderCode;

    @Column(name = "client_order_code", length = 100)
    private String clientOrderCode;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "event_time")
    private Instant eventTime;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 190)
    private String idempotencyKey;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "status", nullable = false, length = 30)
    private String status = STATUS_PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    protected GhnWebhookEventEntity() {}

    public GhnWebhookEventEntity(
            String orderCode,
            String clientOrderCode,
            String type,
            Instant eventTime,
            String idempotencyKey,
            String rawPayload,
            Instant now
    ) {
        this.orderCode = orderCode;
        this.clientOrderCode = clientOrderCode;
        this.type = type;
        this.eventTime = eventTime;
        this.idempotencyKey = idempotencyKey;
        this.rawPayload = rawPayload;
        this.status = STATUS_PENDING;
        this.retryCount = 0;
        this.receivedAt = now != null ? now : Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = STATUS_PENDING;
        }
    }

    public Long getId() { return id; }
    public String getOrderCode() { return orderCode; }
    public String getClientOrderCode() { return clientOrderCode; }
    public String getType() { return type; }
    public Instant getEventTime() { return eventTime; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRawPayload() { return rawPayload; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }

    public void markProcessing(Instant now) {
        this.status = STATUS_PROCESSING;
        this.processingStartedAt = now != null ? now : Instant.now();
    }

    public void markProcessed(Instant now) {
        this.status = STATUS_PROCESSED;
        this.processedAt = now != null ? now : Instant.now();
        this.lastError = null;
    }

    public void markUnmatched(String reason, Instant now) {
        this.status = STATUS_UNMATCHED;
        this.processedAt = now != null ? now : Instant.now();
        this.lastError = reason;
    }

    public void markIgnored(String reason, Instant now) {
        this.status = STATUS_IGNORED;
        this.processedAt = now != null ? now : Instant.now();
        this.lastError = reason;
    }

    public void markFailed(String error, int maxRetries, Instant now) {
        this.retryCount++;
        this.lastError = error;
        if (this.retryCount >= maxRetries) {
            this.status = STATUS_DEAD_LETTER;
        } else {
            this.status = STATUS_FAILED;
        }
        this.processedAt = now != null ? now : Instant.now();
    }
}
