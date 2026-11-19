package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;

@Service
@Transactional
public class DefaultCommerceDisputeFacade implements CommerceDisputeFacade {

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public DefaultCommerceDisputeFacade(
            OrderRepository orderRepository,
            ShipmentRepository shipmentRepository,
            PaymentRepository paymentRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @Override
    public void markOrderDisputed(Long orderId) {
        Instant now = clock.instant();
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));
        order.markDisputed(now);
        orderRepository.save(order);
    }

    @Override
    public void resolveDisputeRelease(Long orderId, String reason) {
        Instant now = clock.instant();
        // Thứ tự khóa tài nguyên bất biến: Order -> Shipment -> Payment
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));
        ShipmentEntity shipment = shipmentRepository.findByOrderIdForUpdate(orderId).orElse(null);
        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(orderId).orElse(null);

        order.markCompleted(now);
        orderRepository.save(order);

        if (payment != null && !PaymentEntity.STATUS_RELEASED.equals(payment.getStatus())) {
            payment.markReleased(now);
            paymentRepository.save(payment);
        }
    }

    @Override
    public void resolveDisputeRefund(Long orderId, BigDecimal refundAmount, String reason) {
        Instant now = clock.instant();
        // Thứ tự khóa tài nguyên bất biến: Order -> Shipment -> Payment
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));
        ShipmentEntity shipment = shipmentRepository.findByOrderIdForUpdate(orderId).orElse(null);
        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(orderId).orElse(null);

        order.markRefunded(now);
        orderRepository.save(order);

        if (payment != null && !PaymentEntity.STATUS_REFUNDED.equals(payment.getStatus())) {
            BigDecimal actualRefund = refundAmount != null ? refundAmount : order.getTotalAmount();
            payment.markRefunded(actualRefund, reason, now);
            paymentRepository.save(payment);
        }
    }

    @Override
    public void resolveDisputeReturnApproved(Long orderId, String reason) {
        Instant now = clock.instant();
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));
        order.markDisputed(now);
        orderRepository.save(order);
    }
}
