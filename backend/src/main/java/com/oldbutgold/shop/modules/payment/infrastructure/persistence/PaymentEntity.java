package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class PaymentEntity {
    public static final String METHOD_BANK_TRANSFER = "BANK_TRANSFER_MOCK";
    public static final String METHOD_E_WALLET = "E_WALLET_MOCK";
    public static final String METHOD_COD = "COD_MOCK";
    public static final String METHOD_VNPAY = "VNPAY";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_HELD = "HELD";
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_REFUND_PENDING = "REFUND_PENDING";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_UNDER_REVIEW = "UNDER_REVIEW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "transaction_code", length = 100, unique = true)
    private String transactionCode;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "held_at")
    private Instant heldAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "refund_amount", precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PaymentEntity() {}

    public PaymentEntity(Long orderId, BigDecimal amount, String paymentMethod, Instant now) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền thanh toán phải lớn hơn 0.");
        }
        this.orderId = orderId;
        this.amount = amount;
        this.currency = "VND";
        this.paymentMethod = paymentMethod;
        this.status = STATUS_PENDING;
        this.createdAt = now;
    }

    public void markHeld(String transactionCode, Instant now) {
        this.status = STATUS_HELD;
        this.transactionCode = transactionCode;
        this.paidAt = now;
        this.heldAt = now;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        this.status = STATUS_FAILED;
        this.paidAt = null;
        this.heldAt = null;
        this.releasedAt = null;
        this.refundedAt = null;
        this.failureReason = (reason != null && !reason.isBlank()) ? reason : "Giao dịch thanh toán thất bại";
        this.updatedAt = now;
    }

    public void markProviderRefundPending(String transactionCode, Instant now) {
        this.status = STATUS_REFUND_PENDING;
        this.transactionCode = transactionCode;
        this.paidAt = now;
        this.refundAmount = this.amount;
        this.refundReason = "Provider payment after order cancellation/deadline; reconciliation required";
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void markRefunded(BigDecimal refundAmount, String reason, Instant now) {
        if (!STATUS_HELD.equals(this.status) && !STATUS_REFUND_PENDING.equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể hoàn tiền cho giao dịch đang HELD hoặc REFUND_PENDING.");
        }
        this.status = STATUS_REFUNDED;
        this.refundAmount = refundAmount != null ? refundAmount : this.amount;
        this.refundReason = (reason != null && !reason.isBlank()) ? reason : "Hoàn tiền do hủy đơn";
        this.refundedAt = now;
        this.updatedAt = now;
    }

    public void markUnderReview(String transactionCode, Instant now) {
        if (!STATUS_PENDING.equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể chuyển sang UNDER_REVIEW từ trạng thái PENDING. Trạng thái hiện tại: " + this.status);
        }
        this.status = STATUS_UNDER_REVIEW;
        this.transactionCode = transactionCode;
        this.paidAt = now;
        this.heldAt = null;
        this.releasedAt = null;
        this.refundedAt = null;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void approveFromReview(Instant now) {
        if (!STATUS_UNDER_REVIEW.equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể duyệt hoàn tất ký quỹ khi giao dịch đang ở trạng thái UNDER_REVIEW. Trạng thái hiện tại: " + this.status);
        }
        this.status = STATUS_HELD;
        this.heldAt = now;
        this.updatedAt = now;
    }

    public void markRefundPending(BigDecimal refundAmount, String reason, Instant now) {
        if (!STATUS_HELD.equals(this.status) && !STATUS_UNDER_REVIEW.equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể chuyển sang REFUND_PENDING từ trạng thái HELD hoặc UNDER_REVIEW. Trạng thái hiện tại: " + this.status);
        }
        this.status = STATUS_REFUND_PENDING;
        this.refundAmount = refundAmount != null ? refundAmount : this.amount;
        this.refundReason = (reason != null && !reason.isBlank()) ? reason : "Chờ hoàn tiền do từ chối giao dịch rà soát gian lận";
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getTransactionCode() { return transactionCode; }
    public String getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public Long getVersion() { return version; }
    public Instant getHeldAt() { return heldAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public String getRefundReason() { return refundReason; }
    public Instant getRefundedAt() { return refundedAt; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
