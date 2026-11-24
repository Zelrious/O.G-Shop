package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DefaultFulfillmentCommerceFacade implements FulfillmentCommerceFacade {

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DefaultFulfillmentCommerceFacade(
            OrderRepository orderRepository,
            AddressRepository addressRepository,
            @Autowired(required = false) JdbcTemplate jdbcTemplate
    ) {
        this.orderRepository = orderRepository;
        this.addressRepository = addressRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public DefaultFulfillmentCommerceFacade(OrderRepository orderRepository, AddressRepository addressRepository) {
        this(orderRepository, addressRepository, null);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<OrderFulfillmentSnapshot> getOrderSnapshot(long orderId) {
        return orderRepository.findById(orderId)
                .map(this::toSnapshot);
    }

    @Override
    public Optional<OrderFulfillmentSnapshot> lockOrderForFulfillment(long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .map(this::toSnapshot);
    }

    private OrderFulfillmentSnapshot toSnapshot(OrderEntity o) {
        AddressEntity sellerAddress = null;
        if (o.getSourceAddressId() != null) {
            sellerAddress = addressRepository.findById(o.getSourceAddressId()).orElse(null);
        }

        String fromName = sellerAddress != null ? sellerAddress.getRecipientName() : null;
        String fromPhone = sellerAddress != null ? sellerAddress.getPhoneNumber() : null;
        String fromAddress = sellerAddress != null ? sellerAddress.getDetailAddress() : null;
        String fromProvinceName = sellerAddress != null ? sellerAddress.getProvince() : null;
        String fromDistrictName = sellerAddress != null ? sellerAddress.getDistrict() : null;
        String fromWardName = sellerAddress != null ? sellerAddress.getWard() : null;
        Integer fromDistrictId = sellerAddress != null ? sellerAddress.getGhnDistrictId() : null;
        String fromWardCode = sellerAddress != null ? sellerAddress.getGhnWardCode() : null;

        return new OrderFulfillmentSnapshot(
                o.getId(),
                o.getBuyerId(),
                o.getSellerId(),
                o.getStatus(),
                o.getCompletedAt(),
                o.getCancelledAt(),
                o.getSourceAddressId(),
                fromName,
                fromPhone,
                fromAddress,
                fromProvinceName,
                fromDistrictName,
                fromWardName,
                fromDistrictId,
                fromWardCode,
                o.getShippingRecipientName(),
                o.getShippingPhoneNumber(),
                o.getShippingProvince(),
                o.getShippingDistrict(),
                o.getShippingWard(),
                o.getShippingDetailAddress(),
                o.getShippingGhnDistrictId(),
                o.getShippingGhnWardCode(),
                o.getWorkflowModel(),
                o.getShippingFee()
        );
    }

    @Override
    public void markShipped(long orderId, Instant shippedAt) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));
        order.markShipped(shippedAt);
        orderRepository.save(order);
    }

    @Override
    public void markDelivered(long orderId, Instant deliveredAt) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));
        order.markDelivered(deliveredAt);
        orderRepository.saveAndFlush(order);
        syncUc83DeliveryTimestamps(order, deliveredAt);
    }

    @Override
    public void markDeliveredDirectFromCarrier(long orderId, Instant deliveredAt) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));
        order.markDeliveredDirectFromCarrier(deliveredAt);
        orderRepository.saveAndFlush(order);
        syncUc83DeliveryTimestamps(order, deliveredAt);
    }

    private void syncUc83DeliveryTimestamps(OrderEntity order, Instant deliveredAt) {
        if (!"UC83".equals(order.getWorkflowModel()) || jdbcTemplate == null || deliveredAt == null) {
            return;
        }
        Instant returnDueAt = Boolean.TRUE.equals(order.getReturnAllowed())
                ? deliveredAt.plus(Duration.ofDays(3))
                : null;
        jdbcTemplate.update(
                "UPDATE og_compat.orders SET valid_delivered_at = ?, return_due_at = ? WHERE order_id = ?",
                Timestamp.from(deliveredAt),
                returnDueAt != null ? Timestamp.from(returnDueAt) : null,
                order.getId()
        );
    }

    @Override
    public void markCompleted(long orderId, Instant completedAt) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));
        order.markCompleted(completedAt);
        orderRepository.save(order);
    }
}
