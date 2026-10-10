package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "vnpay_payment_attempts", schema = "og_compat")
public class VnPayAttemptEntity {
    @Id @Column(name = "transaction_ref", length = 100) private String reference;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "payment_id", nullable = false) private Long paymentId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "provider_transaction_no", length = 100) private String providerTransactionNo;
    @Column(length = 20) private String outcome;
    @Column(name = "response_code", length = 2) private String responseCode;
    @Column(name = "transaction_status", length = 2) private String transactionStatus;
    protected VnPayAttemptEntity() {}
    public VnPayAttemptEntity(String ref, long orderId, long paymentId, BigDecimal amount, Instant expiresAt, Instant now) {
        this.reference = ref; this.orderId = orderId; this.paymentId = paymentId; this.amount = amount;
        this.expiresAt = expiresAt; this.createdAt = now;
    }
    public boolean isProcessed() { return processedAt != null; }
    public boolean matchesResult(String code, String status, String transaction) {
        return Objects.equals(responseCode, code) && Objects.equals(transactionStatus, status) && Objects.equals(providerTransactionNo, transaction);
    }
    public void finish(String outcome, String code, String status, String transaction, Instant now) {
        if (isProcessed()) throw new IllegalStateException("Attempt is already processed");
        this.outcome = outcome; responseCode = code; transactionStatus = status;
        providerTransactionNo = transaction; processedAt = now;
    }
    public String getReference() { return reference; }
    public Long getOrderId() { return orderId; }
    public Long getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getExpiresAt() { return expiresAt; }
}
