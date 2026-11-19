package com.oldbutgold.shop.modules.commerce.application;

import java.math.BigDecimal;

public interface CommerceDisputeFacade {
    void markOrderDisputed(Long orderId);
    void resolveDisputeRelease(Long orderId, String reason);
    void resolveDisputeRefund(Long orderId, BigDecimal refundAmount, String reason);
    void resolveDisputeReturnApproved(Long orderId, String reason);
}
