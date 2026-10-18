package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_ipn_raw_receipts", schema = "og_compat")
public class PaymentIpnRawReceiptEntity {

    public static final String VERIFICATION_UNVERIFIED = "UNVERIFIED";
    public static final String VERIFICATION_MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String VERIFICATION_SIGNATURE_INVALID = "SIGNATURE_INVALID";
    public static final String VERIFICATION_SIGNATURE_VALID_UNLINKED = "SIGNATURE_VALID_UNLINKED";
    public static final String VERIFICATION_LINKED = "LINKED";

    public static final String PROCESSING_RECEIVED = "RECEIVED";
    public static final String PROCESSING_PROCESSED = "PROCESSED";
    public static final String PROCESSING_PROCESSING_FAILED = "PROCESSING_FAILED";
    public static final String PROCESSING_RETRYABLE_ERROR = "RETRYABLE_ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receipt_id")
    private Long id;

    @Column(name = "raw_query_params", nullable = false, columnDefinition = "TEXT")
    private String rawQueryParams;

    @Column(name = "raw_query_original_length", nullable = false)
    private int rawQueryOriginalLength;

    @Column(name = "raw_query_truncated", nullable = false)
    private boolean rawQueryTruncated = false;

    @Column(name = "raw_query_sanitized", nullable = false)
    private boolean rawQuerySanitized = false;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "raw_query_sha256", nullable = false, length = 64)
    private String rawQuerySha256;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "vnp_txn_ref", length = 100)
    private String vnpTxnRef;

    @Column(name = "vnp_transaction_no", length = 100)
    private String vnpTransactionNo;

    @Column(name = "vnp_amount_text", columnDefinition = "TEXT")
    private String vnpAmountText;

    @Column(name = "verification_status", nullable = false, length = 30)
    private String verificationStatus = VERIFICATION_UNVERIFIED;

    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus = PROCESSING_RECEIVED;

    @Column(name = "processing_error", length = 200)
    private String processingError;

    protected PaymentIpnRawReceiptEntity() {}

    public PaymentIpnRawReceiptEntity(String rawQueryParams,
                                     int rawQueryOriginalLength,
                                     boolean rawQueryTruncated,
                                     boolean rawQuerySanitized,
                                     String rawQuerySha256,
                                     String ipAddress,
                                     Instant receivedAt) {
        this.rawQueryParams = rawQueryParams;
        this.rawQueryOriginalLength = rawQueryOriginalLength;
        this.rawQueryTruncated = rawQueryTruncated;
        this.rawQuerySanitized = rawQuerySanitized;
        this.rawQuerySha256 = rawQuerySha256;
        this.ipAddress = ipAddress;
        this.receivedAt = receivedAt;
        this.verificationStatus = VERIFICATION_UNVERIFIED;
        this.processingStatus = PROCESSING_RECEIVED;
    }

    public void setForensicFields(String vnpTxnRef, String vnpTransactionNo, String vnpAmountText) {
        this.vnpTxnRef = vnpTxnRef;
        this.vnpTransactionNo = vnpTransactionNo;
        this.vnpAmountText = vnpAmountText;
    }

    public void markMalformed(String error) {
        this.verificationStatus = VERIFICATION_MALFORMED_REQUEST;
        this.processingStatus = PROCESSING_PROCESSED;
        this.processingError = error != null && error.length() > 200 ? error.substring(0, 200) : error;
    }

    public void markSignatureInvalid() {
        this.verificationStatus = VERIFICATION_SIGNATURE_INVALID;
        this.processingStatus = PROCESSING_PROCESSED;
    }

    public void markSignatureValidUnlinked() {
        this.verificationStatus = VERIFICATION_SIGNATURE_VALID_UNLINKED;
        this.processingStatus = PROCESSING_PROCESSED;
    }

    public void markLinked() {
        this.verificationStatus = VERIFICATION_LINKED;
    }

    public void markProcessed() {
        this.processingStatus = PROCESSING_PROCESSED;
    }

    public void markProcessingFailed(String error) {
        this.processingStatus = PROCESSING_PROCESSING_FAILED;
        this.processingError = error != null && error.length() > 200 ? error.substring(0, 200) : error;
    }

    public void markRetryableError(String error) {
        this.processingStatus = PROCESSING_RETRYABLE_ERROR;
        this.processingError = error != null && error.length() > 200 ? error.substring(0, 200) : error;
    }

    public Long getId() {
        return id;
    }

    public String getRawQueryParams() {
        return rawQueryParams;
    }

    public int getRawQueryOriginalLength() {
        return rawQueryOriginalLength;
    }

    public boolean isRawQueryTruncated() {
        return rawQueryTruncated;
    }

    public boolean isRawQuerySanitized() {
        return rawQuerySanitized;
    }

    public String getRawQuerySha256() {
        return rawQuerySha256;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getVnpTxnRef() {
        return vnpTxnRef;
    }

    public String getVnpTransactionNo() {
        return vnpTransactionNo;
    }

    public String getVnpAmountText() {
        return vnpAmountText;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public String getProcessingError() {
        return processingError;
    }
}
