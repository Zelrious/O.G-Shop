package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_SUCCESS_ON_EXPIRED_ORDER = "SUCCESS_ON_EXPIRED_ORDER";
    public static final String STATUS_SUCCESS_DUPLICATE_CHARGE = "SUCCESS_DUPLICATE_CHARGE";
    public static final String STATUS_FLAGGED_SUSPICIOUS = "FLAGGED_SUSPICIOUS";
    public static final String STATUS_SUCCESS_PENDING_REVIEW = "SUCCESS_PENDING_REVIEW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 100)
    private String txnRef;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(nullable = false, length = 30)
    private String provider = "VNPAY";

    @Column(nullable = false, length = 35)
    private String status = STATUS_PENDING;

    @Column(name = "vnp_amount_raw")
    private Long vnpAmountRaw;

    @Column(name = "vnp_amount", precision = 19, scale = 2)
    private BigDecimal vnpAmount;

    @Column(name = "vnp_transaction_no", length = 100)
    private String vnpTransactionNo;

    @Column(name = "vnp_response_code", length = 10)
    private String vnpResponseCode;

    @Column(name = "vnp_transaction_status", length = 10)
    private String vnpTransactionStatus;

    @Column(name = "vnp_bank_code", length = 50)
    private String vnpBankCode;

    @Column(name = "vnp_pay_date", length = 20)
    private String vnpPayDate;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "ipn_count", nullable = false)
    private int ipnCount = 0;

    @Column(name = "first_ipn_received_at")
    private Instant firstIpnReceivedAt;

    @Column(name = "last_ipn_received_at")
    private Instant lastIpnReceivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PaymentAttemptEntity() {}

    public PaymentAttemptEntity(Long orderId, Long paymentId, String txnRef, BigDecimal amount, Instant now) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.txnRef = txnRef;
        this.amount = amount;
        this.currency = "VND";
        this.provider = "VNPAY";
        this.status = STATUS_PENDING;
        this.createdAt = now;
    }

    public boolean isTerminal() {
        return STATUS_SUCCESS.equals(status)
                || STATUS_FAILED.equals(status)
                || STATUS_SUCCESS_ON_EXPIRED_ORDER.equals(status)
                || STATUS_SUCCESS_DUPLICATE_CHARGE.equals(status)
                || STATUS_FLAGGED_SUSPICIOUS.equals(status)
                || STATUS_SUCCESS_PENDING_REVIEW.equals(status);
    }

    public void incrementIpnCount(Instant now) {
        this.ipnCount++;
        if (this.firstIpnReceivedAt == null) {
            this.firstIpnReceivedAt = now;
        }
        this.lastIpnReceivedAt = now;
        this.updatedAt = now;
    }

    public void markSuperseded(Instant now) {
        if (!STATUS_PENDING.equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể chuyển trạng thái SUPERSEDED từ PENDING. Trạng thái hiện tại: " + this.status);
        }
        this.status = STATUS_SUPERSEDED;
        this.updatedAt = now;
    }

    public void markExpired(Instant now) {
        if (!STATUS_PENDING.equals(this.status) && !STATUS_SUPERSEDED.equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể chuyển trạng thái EXPIRED từ PENDING hoặc SUPERSEDED. Trạng thái hiện tại: " + this.status);
        }
        this.status = STATUS_EXPIRED;
        this.updatedAt = now;
    }

    public void markSuccess(Instant now) {
        if (isTerminal()) {
            throw new IllegalStateException("Không thể ghi đè trạng thái cuối của attempt: " + this.status);
        }
        this.status = STATUS_SUCCESS;
        this.updatedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        if (isTerminal()) {
            throw new IllegalStateException("Không thể ghi đè trạng thái cuối của attempt: " + this.status);
        }
        this.status = STATUS_FAILED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void markSuccessDuplicateCharge(Instant now) {
        if (isTerminal()) {
            throw new IllegalStateException("Không thể ghi đè trạng thái cuối của attempt: " + this.status);
        }
        this.status = STATUS_SUCCESS_DUPLICATE_CHARGE;
        this.updatedAt = now;
    }

    public void markSuccessOnExpiredOrder(Instant now) {
        if (isTerminal()) {
            throw new IllegalStateException("Không thể ghi đè trạng thái cuối của attempt: " + this.status);
        }
        this.status = STATUS_SUCCESS_ON_EXPIRED_ORDER;
        this.updatedAt = now;
    }

    public void markFlaggedSuspicious(Instant now) {
        if (isTerminal()) {
            throw new IllegalStateException("Không thể ghi đè trạng thái cuối của attempt: " + this.status);
        }
        this.status = STATUS_FLAGGED_SUSPICIOUS;
        this.updatedAt = now;
    }

    public void markSuccessPendingReview(Instant now) {
        if (isTerminal()) {
            throw new IllegalStateException("Không thể ghi đè trạng thái cuối của attempt: " + this.status);
        }
        this.status = STATUS_SUCCESS_PENDING_REVIEW;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getPaymentId() { return paymentId; }
    public String getTxnRef() { return txnRef; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getProvider() { return provider; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVnpAmountRaw() { return vnpAmountRaw; }
    public void setVnpAmountRaw(Long vnpAmountRaw) { this.vnpAmountRaw = vnpAmountRaw; }
    public BigDecimal getVnpAmount() { return vnpAmount; }
    public void setVnpAmount(BigDecimal vnpAmount) { this.vnpAmount = vnpAmount; }
    public String getVnpTransactionNo() { return vnpTransactionNo; }
    public void setVnpTransactionNo(String vnpTransactionNo) { this.vnpTransactionNo = vnpTransactionNo; }
    public String getVnpResponseCode() { return vnpResponseCode; }
    public void setVnpResponseCode(String vnpResponseCode) { this.vnpResponseCode = vnpResponseCode; }
    public String getVnpTransactionStatus() { return vnpTransactionStatus; }
    public void setVnpTransactionStatus(String vnpTransactionStatus) { this.vnpTransactionStatus = vnpTransactionStatus; }
    public String getVnpBankCode() { return vnpBankCode; }
    public void setVnpBankCode(String vnpBankCode) { this.vnpBankCode = vnpBankCode; }
    public String getVnpPayDate() { return vnpPayDate; }
    public void setVnpPayDate(String vnpPayDate) { this.vnpPayDate = vnpPayDate; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public int getIpnCount() { return ipnCount; }
    public void setIpnCount(int ipnCount) { this.ipnCount = ipnCount; }
    public Instant getFirstIpnReceivedAt() { return firstIpnReceivedAt; }
    public Instant getLastIpnReceivedAt() { return lastIpnReceivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
