package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_ipn_events", schema = "og_compat")
public class PaymentIpnEventEntity {

    public static final String OUTCOME_APPLIED_SUCCESS = "APPLIED_SUCCESS";
    public static final String OUTCOME_APPLIED_FAILED = "APPLIED_FAILED";
    public static final String OUTCOME_DUPLICATE_ACKNOWLEDGED = "DUPLICATE_ACKNOWLEDGED";
    public static final String OUTCOME_CONFLICT_IGNORED = "CONFLICT_IGNORED";
    public static final String OUTCOME_CONFLICT_CHARGE_REQUIRES_RECONCILIATION = "CONFLICT_CHARGE_REQUIRES_RECONCILIATION";
    public static final String OUTCOME_FLAGGED_SUSPICIOUS = "FLAGGED_SUSPICIOUS";
    public static final String OUTCOME_APPLIED_EXPIRED_ORDER = "APPLIED_EXPIRED_ORDER";
    public static final String OUTCOME_APPLIED_DUPLICATE_CHARGE = "APPLIED_DUPLICATE_CHARGE";
    public static final String OUTCOME_APPLIED_PENDING_REVIEW = "APPLIED_PENDING_REVIEW";
    public static final String OUTCOME_REJECTED_AMOUNT_MISMATCH = "REJECTED_AMOUNT_MISMATCH";
    public static final String OUTCOME_REJECTED_CURRENCY_MISMATCH = "REJECTED_CURRENCY_MISMATCH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "receipt_id", nullable = false, unique = true)
    private Long receiptId;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "txn_ref", nullable = false, length = 100)
    private String txnRef;

    @Column(name = "vnp_transaction_no", length = 100)
    private String vnpTransactionNo;

    @Column(name = "vnp_response_code", length = 10)
    private String vnpResponseCode;

    @Column(name = "vnp_transaction_status", length = 10)
    private String vnpTransactionStatus;

    @Column(name = "vnp_curr_code", length = 10)
    private String vnpCurrCode;

    @Column(name = "vnp_amount_text", columnDefinition = "TEXT")
    private String vnpAmountText;

    @Column(name = "vnp_amount_raw")
    private Long vnpAmountRaw;

    @Column(name = "vnp_amount", precision = 19, scale = 2)
    private BigDecimal vnpAmount;

    @Column(name = "vnp_bank_code", length = 50)
    private String vnpBankCode;

    @Column(name = "vnp_bank_tran_no", length = 100)
    private String vnpBankTranNo;

    @Column(name = "vnp_card_type", length = 20)
    private String vnpCardType;

    @Column(name = "vnp_pay_date", length = 20)
    private String vnpPayDate;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "processing_outcome", nullable = false, length = 50)
    private String processingOutcome;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected PaymentIpnEventEntity() {}

    public PaymentIpnEventEntity(Long receiptId,
                                Long attemptId,
                                Long orderId,
                                Long paymentId,
                                String txnRef,
                                String vnpTransactionNo,
                                String vnpResponseCode,
                                String vnpTransactionStatus,
                                String vnpCurrCode,
                                String vnpAmountText,
                                Long vnpAmountRaw,
                                BigDecimal vnpAmount,
                                String vnpBankCode,
                                String vnpBankTranNo,
                                String vnpCardType,
                                String vnpPayDate,
                                String ipAddress,
                                String processingOutcome,
                                Instant receivedAt) {
        this.receiptId = receiptId;
        this.attemptId = attemptId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.txnRef = txnRef;
        this.vnpTransactionNo = vnpTransactionNo;
        this.vnpResponseCode = vnpResponseCode;
        this.vnpTransactionStatus = vnpTransactionStatus;
        this.vnpCurrCode = vnpCurrCode;
        this.vnpAmountText = vnpAmountText;
        this.vnpAmountRaw = vnpAmountRaw;
        this.vnpAmount = vnpAmount;
        this.vnpBankCode = vnpBankCode;
        this.vnpBankTranNo = vnpBankTranNo;
        this.vnpCardType = vnpCardType;
        this.vnpPayDate = vnpPayDate;
        this.ipAddress = ipAddress;
        this.processingOutcome = processingOutcome;
        this.receivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public String getVnpTransactionNo() {
        return vnpTransactionNo;
    }

    public String getVnpResponseCode() {
        return vnpResponseCode;
    }

    public String getVnpTransactionStatus() {
        return vnpTransactionStatus;
    }

    public String getVnpCurrCode() {
        return vnpCurrCode;
    }

    public String getVnpAmountText() {
        return vnpAmountText;
    }

    public Long getVnpAmountRaw() {
        return vnpAmountRaw;
    }

    public BigDecimal getVnpAmount() {
        return vnpAmount;
    }

    public String getVnpBankCode() {
        return vnpBankCode;
    }

    public String getVnpBankTranNo() {
        return vnpBankTranNo;
    }

    public String getVnpCardType() {
        return vnpCardType;
    }

    public String getVnpPayDate() {
        return vnpPayDate;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getProcessingOutcome() {
        return processingOutcome;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
