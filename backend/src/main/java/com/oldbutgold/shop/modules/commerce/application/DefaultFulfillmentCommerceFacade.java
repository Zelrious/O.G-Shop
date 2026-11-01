package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DefaultFulfillmentCommerceFacade implements FulfillmentCommerceFacade {

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;

    public DefaultFulfillmentCommerceFacade(OrderRepository orderRepository, AddressRepository addressRepository) {
        this.orderRepository = orderRepository;
        this.addressRepository = addressRepository;
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
                o.getShippingGhnWardCode()
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
        orderRepository.save(order);
    }

    @Override
    public void markDeliveredDirectFromCarrier(long orderId, Instant deliveredAt) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));
        order.markDeliveredDirectFromCarrier(deliveredAt);
        orderRepository.save(order);
    }

    @Override
    public void markCompleted(long orderId, Instant completedAt) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));
        order.markCompleted(completedAt);
        orderRepository.save(order);
    }
}
