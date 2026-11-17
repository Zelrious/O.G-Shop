package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_confirmations", schema = "og_compat")
public class PaymentConfirmationEntity {

    public static final String DISPOSITION_ALLOCATABLE = "ALLOCATABLE";
    public static final String DISPOSITION_RECONCILIATION = "RECONCILIATION";

    @Id
    @Column(name = "confirmation_id", nullable = false)
    private UUID confirmationId;

    @Column(name = "intent_id", nullable = false)
    private UUID intentId;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "ipn_event_id")
    private Long ipnEventId;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider = "VNPAY";

    @Column(name = "provider_transaction_id", nullable = false, length = 150)
    private String providerTransactionId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "disposition", nullable = false, length = 20)
    private String disposition = DISPOSITION_ALLOCATABLE;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    protected PaymentConfirmationEntity() {}

    public PaymentConfirmationEntity(UUID confirmationId, UUID intentId, Long attemptId,
                                     Long ipnEventId, String provider, String providerTransactionId,
                                     BigDecimal amount, String disposition, Instant confirmedAt) {
        this.confirmationId = confirmationId != null ? confirmationId : UUID.randomUUID();
        this.intentId = intentId;
        this.attemptId = attemptId;
        this.ipnEventId = ipnEventId;
        this.provider = provider != null ? provider : "VNPAY";
        this.providerTransactionId = providerTransactionId;
        this.amount = amount;
        this.disposition = disposition != null ? disposition : DISPOSITION_ALLOCATABLE;
        this.confirmedAt = confirmedAt != null ? confirmedAt : Instant.now();
    }

    public UUID getConfirmationId() { return confirmationId; }
    public void setConfirmationId(UUID confirmationId) { this.confirmationId = confirmationId; }

    public UUID getIntentId() { return intentId; }
    public void setIntentId(UUID intentId) { this.intentId = intentId; }

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public Long getIpnEventId() { return ipnEventId; }
    public void setIpnEventId(Long ipnEventId) { this.ipnEventId = ipnEventId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getDisposition() { return disposition; }
    public void setDisposition(String disposition) { this.disposition = disposition; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}

