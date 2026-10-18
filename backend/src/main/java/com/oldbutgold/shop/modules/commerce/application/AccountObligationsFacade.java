package com.oldbutgold.shop.modules.commerce.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface AccountObligationsFacade {
    record OrderObligation(long orderId,String role,String status,BigDecimal total,Instant createdAt,boolean open) {}
    List<OrderObligation> orders(long userId);
}
