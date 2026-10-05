package com.oldbutgold.shop.modules.commerce.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface CommercePaymentFacade {
    record OrderPaymentSnapshot(long orderId, long buyerId, BigDecimal amount, String currency, String status, Instant deadline) {
        public boolean payableAt(Instant now) { return "PAYMENT_PENDING".equals(status) && deadline != null && now.isBefore(deadline); }
    }
    Optional<OrderPaymentSnapshot> lockOrder(long orderId);
    void markPaidHeld(long orderId, Instant now);
}
