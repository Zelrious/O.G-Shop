package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderVoucherEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderVoucherRepository;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class OrderManagementService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderVoucherRepository orderVoucherRepository;
    private final CatalogCommerceFacade catalogCommerceFacade;
    private final PaymentRepository paymentRepository;
    private final IdentityCommerceFacade identityCommerceFacade;
    private final Clock clock;

    public OrderManagementService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderVoucherRepository orderVoucherRepository,
            CatalogCommerceFacade catalogCommerceFacade,
            PaymentRepository paymentRepository,
            IdentityCommerceFacade identityCommerceFacade,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderVoucherRepository = orderVoucherRepository;
        this.catalogCommerceFacade = catalogCommerceFacade;
        this.paymentRepository = paymentRepository;
        this.identityCommerceFacade = identityCommerceFacade;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<OrderManagementDtos.OrderSummaryResponse> getBuyerOrders(Long buyerId, String status, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(50, Math.max(1, size));
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);
        Page<OrderEntity> orderPage;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            orderPage = orderRepository.findByBuyerIdAndStatusOrderByCreatedAtDescIdDesc(buyerId, status.trim().toUpperCase(), pageable);
        } else {
            orderPage = orderRepository.findByBuyerIdOrderByCreatedAtDescIdDesc(buyerId, pageable);
        }

        return orderPage.map(order -> mapToSummary(order, true));
    }

    @Transactional(readOnly = true)
    public OrderManagementDtos.OrderDetailResponse getBuyerOrderDetail(Long buyerId, Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập đơn hàng này.");
        }

        return mapToDetail(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderManagementDtos.OrderSummaryResponse> getSellerOrders(Long sellerId, String status, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(50, Math.max(1, size));
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);
        Page<OrderEntity> orderPage;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            orderPage = orderRepository.findBySellerIdAndStatusOrderByCreatedAtDescIdDesc(sellerId, status.trim().toUpperCase(), pageable);
        } else {
            orderPage = orderRepository.findBySellerIdOrderByCreatedAtDescIdDesc(sellerId, pageable);
        }

        return orderPage.map(order -> mapToSummary(order, false));
    }

    @Transactional(readOnly = true)
    public OrderManagementDtos.OrderDetailResponse getSellerOrderDetail(Long sellerId, Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getSellerId().equals(sellerId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập đơn hàng này.");
        }

        return mapToDetail(order);
    }

    @Transactional
    public OrderManagementDtos.OrderActionResponse cancelBuyerOrder(Long buyerId, Long orderId, String reason) {
        Instant now = clock.instant();

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Bạn không có quyền hủy đơn hàng này.");
        }

        if (OrderEntity.STATUS_CANCELLED.equals(order.getStatus())) {
            return new OrderManagementDtos.OrderActionResponse(orderId, OrderEntity.STATUS_CANCELLED, "Đơn hàng đã được hủy trước đó.");
        }

        if (!OrderEntity.STATUS_PAYMENT_PENDING.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể hủy đơn hàng ở trạng thái chờ thanh toán (PAYMENT_PENDING). Trạng thái hiện tại: " + order.getStatus());
        }

        String cancelReason = reason != null && !reason.isBlank() ? reason.trim() : "Người mua chủ động hủy đơn";
        order.cancel(cancelReason, now);
        orderRepository.save(order);

        // Cancel associated payment record if exists
        Optional<PaymentEntity> paymentOpt = paymentRepository.findByOrderIdForUpdate(orderId);
        if (paymentOpt.isPresent()) {
            PaymentEntity payment = paymentOpt.get();
            payment.markFailed("Người mua hủy đơn hàng: " + cancelReason, now);
            paymentRepository.save(payment);
        }

        // Release product reservation back to ACTIVE via facade
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItemEntity item : items) {
            catalogCommerceFacade.releaseProductReservation(item.getProductId(), orderId, now);
        }

        return new OrderManagementDtos.OrderActionResponse(
                orderId,
                OrderEntity.STATUS_CANCELLED,
                "Đơn hàng đã được hủy thành công. Sản phẩm đã được giải phóng để bán lại."
        );
    }

    @Transactional
    public OrderManagementDtos.OrderActionResponse confirmSellerOrder(Long sellerId, Long orderId) {
        Instant now = clock.instant();

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getSellerId().equals(sellerId)) {
            throw new AccessDeniedException("Bạn không phải người bán của đơn hàng này.");
        }

        if (OrderEntity.STATUS_SELLER_CONFIRMED.equals(order.getStatus())) {
            return new OrderManagementDtos.OrderActionResponse(orderId, OrderEntity.STATUS_SELLER_CONFIRMED, "Đơn hàng đã được xác nhận trước đó.");
        }

        if (!OrderEntity.STATUS_PAID_HELD.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể xác nhận đơn hàng khi tiền đã được Ký quỹ giữ an toàn (PAID_HELD). Trạng thái hiện tại: " + order.getStatus());
        }

        order.confirmBySeller(now);
        orderRepository.save(order);

        // Product is permanently marked as SOLD via facade
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItemEntity item : items) {
            catalogCommerceFacade.markProductSold(item.getProductId(), orderId, now);
        }

        return new OrderManagementDtos.OrderActionResponse(
                orderId,
                OrderEntity.STATUS_SELLER_CONFIRMED,
                "Đã xác nhận đơn hàng thành công! Sản phẩm đã chuyển sang trạng thái ĐÃ BÁN."
        );
    }

    @Transactional
    public int expireOverdueOrders() {
        Instant now = clock.instant();
        List<OrderEntity> overdueOrders = orderRepository.findOverduePendingOrders(now);
        int count = 0;

        for (OrderEntity pending : overdueOrders) {
            Optional<OrderEntity> lockedOpt = orderRepository.findByIdForUpdate(pending.getId());
            if (lockedOpt.isPresent()) {
                OrderEntity order = lockedOpt.get();
                if (OrderEntity.STATUS_PAYMENT_PENDING.equals(order.getStatus())) {
                    order.cancel("Hết hạn thời gian thanh toán (1 giờ)", now);
                    orderRepository.save(order);

                    List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
                    for (OrderItemEntity item : items) {
                        catalogCommerceFacade.releaseProductReservation(item.getProductId(), order.getId(), now);
                    }

                    Optional<PaymentEntity> paymentOpt = paymentRepository.findByOrderIdForUpdate(order.getId());
                    if (paymentOpt.isPresent()) {
                        paymentOpt.get().markFailed("Hết hạn thời gian thanh toán (1 giờ)", now);
                        paymentRepository.save(paymentOpt.get());
                    }

                    count++;
                }
            }
        }
        return count;
    }

    private OrderManagementDtos.OrderSummaryResponse mapToSummary(OrderEntity order, boolean forBuyer) {
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
        OrderItemEntity firstItem = !items.isEmpty() ? items.get(0) : null;

        Long productId = firstItem != null ? firstItem.getProductId() : null;
        String productTitle = firstItem != null ? firstItem.getProductTitle() : "Đơn hàng #" + order.getId();
        BigDecimal unitPrice = firstItem != null ? firstItem.getAgreedPrice() : order.getSubtotal();
        int quantity = firstItem != null ? firstItem.getQuantity() : 1;

        String thumbnail = null;
        if (productId != null) {
            thumbnail = catalogCommerceFacade.getActiveProduct(productId)
                    .map(CatalogCommerceFacade.CommerceProductSummary::thumbnailUrl)
                    .orElse(null);
        }

        Long partnerId = forBuyer ? order.getSellerId() : order.getBuyerId();
        String partnerName = identityCommerceFacade.getUserSummary(partnerId)
                .map(IdentityCommerceFacade.UserSummary::fullName)
                .orElse(forBuyer ? "Người bán" : "Người mua");

        String paymentStatus = paymentRepository.findByOrderId(order.getId())
                .map(PaymentEntity::getStatus)
                .orElse("CHUA_KHOI_TAO");

        return new OrderManagementDtos.OrderSummaryResponse(
                order.getId(),
                order.getCheckoutGroupId(),
                productId,
                productTitle,
                thumbnail,
                unitPrice,
                quantity,
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus(),
                paymentStatus,
                partnerId,
                partnerName,
                order.getCreatedAt(),
                order.getPaymentDueAt(),
                order.getCompletedAt(),
                order.getCancelledAt()
        );
    }

    private OrderManagementDtos.OrderDetailResponse mapToDetail(OrderEntity order) {
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
        OrderItemEntity firstItem = !items.isEmpty() ? items.get(0) : null;

        Long productId = firstItem != null ? firstItem.getProductId() : null;
        String productTitle = firstItem != null ? firstItem.getProductTitle() : "Đơn hàng #" + order.getId();
        BigDecimal unitPrice = firstItem != null ? firstItem.getAgreedPrice() : order.getSubtotal();
        int quantity = firstItem != null ? firstItem.getQuantity() : 1;

        String thumbnail = null;
        if (productId != null) {
            thumbnail = catalogCommerceFacade.getActiveProduct(productId)
                    .map(CatalogCommerceFacade.CommerceProductSummary::thumbnailUrl)
                    .orElse(null);
        }

        String buyerName = identityCommerceFacade.getUserSummary(order.getBuyerId())
                .map(IdentityCommerceFacade.UserSummary::fullName)
                .orElse("Người mua #" + order.getBuyerId());

        String sellerName = identityCommerceFacade.getUserSummary(order.getSellerId())
                .map(IdentityCommerceFacade.UserSummary::fullName)
                .orElse("Người bán #" + order.getSellerId());

        String fullAddress = String.format("%s, %s, %s, %s",
                order.getShippingDetailAddress(),
                order.getShippingWard(),
                order.getShippingDistrict(),
                order.getShippingProvince());

        List<OrderVoucherEntity> vouchers = orderVoucherRepository.findByOrderId(order.getId());
        String appliedVoucherCode = !vouchers.isEmpty() ? vouchers.get(0).getVoucherCodeSnapshot() : null;

        PaymentEntity payment = paymentRepository.findByOrderId(order.getId()).orElse(null);

        return new OrderManagementDtos.OrderDetailResponse(
                order.getId(),
                order.getCheckoutGroupId(),
                order.getStatus(),
                order.getBuyerId(),
                buyerName,
                order.getSellerId(),
                sellerName,
                productId,
                productTitle,
                thumbnail,
                unitPrice,
                quantity,
                order.getShippingRecipientName(),
                order.getShippingPhoneNumber(),
                fullAddress,
                order.getSubtotal(),
                order.getShippingFee(),
                order.getBuyerSystemFee(),
                order.getSellerSystemFee(),
                order.getSellerProceeds(),
                order.getVoucherDiscountAmount(),
                order.getShippingDiscountAmount(),
                appliedVoucherCode,
                order.getTotalAmount(),
                order.getCurrency(),
                payment != null ? payment.getId() : null,
                payment != null ? payment.getPaymentMethod() : null,
                payment != null ? payment.getStatus() : "CHUA_KHOI_TAO",
                payment != null ? payment.getTransactionCode() : null,
                payment != null ? payment.getPaidAt() : null,
                payment != null ? payment.getHeldAt() : null,
                order.getPaymentDueAt(),
                order.getCreatedAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCancellationReason()
        );
    }
}
