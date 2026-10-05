package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DefaultCommercePaymentFacade implements CommercePaymentFacade {
    private final OrderRepository orders;
    public DefaultCommercePaymentFacade(OrderRepository orders) { this.orders = orders; }
    @Override public Optional<OrderPaymentSnapshot> lockOrder(long orderId) {
        return orders.findByIdForUpdate(orderId).map(o -> new OrderPaymentSnapshot(o.getId(), o.getBuyerId(),
                o.getTotalAmount(), o.getCurrency(), o.getStatus(), o.getPaymentDueAt()));
    }
    @Override public void markPaidHeld(long orderId, Instant now) {
        var order = orders.findByIdForUpdate(orderId).orElseThrow();
        order.markPaidHeld(now);
        orders.save(order);
    }
}
