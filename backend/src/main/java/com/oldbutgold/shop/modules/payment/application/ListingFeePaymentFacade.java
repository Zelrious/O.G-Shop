package com.oldbutgold.shop.modules.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface ListingFeePaymentFacade {
    /** Checks the immutable receipt, including command reuse for another charge. */
    boolean isReplay(UUID chargeId, BigDecimal amount, String commandKey);
    void confirmMock(UUID chargeId, BigDecimal amount, String commandKey);
}
