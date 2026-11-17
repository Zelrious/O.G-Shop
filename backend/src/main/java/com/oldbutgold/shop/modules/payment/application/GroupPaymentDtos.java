package com.oldbutgold.shop.modules.payment.application;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class GroupPaymentDtos {

    public record CreateGroupPaymentRequest(
            @NotNull UUID groupId
    ) {}

    public record GroupPaymentResponse(
            UUID intentId,
            Long attemptId,
            String txnRef,
            BigDecimal amount,
            String currency,
            String paymentUrl,
            Instant expiresAt
    ) {}

    public record GroupPaymentIpnResult(
            String rspCode,
            String message,
            UUID intentId,
            String disposition
    ) {}
}

