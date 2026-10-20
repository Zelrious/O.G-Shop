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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentResponse;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentService;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentStateService;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class OrderManagementService {
    private static final Logger log = LoggerFactory.getLogger(OrderManagementService.class);
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderVoucherRepository orderVoucherRepository;
    private final CatalogCommerceFacade catalogCommerceFacade;
    private final PaymentRepository paymentRepository;
    private final IdentityCommerceFacade identityCommerceFacade;
    private final ShipmentRepository shipmentRepository;
    private final FulfillmentService fulfillmentService;
    private final FulfillmentStateService fulfillmentStateService;
    private final FulfillmentSimulationGuard simulationGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public OrderManagementService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderVoucherRepository orderVoucherRepository,
            CatalogCommerceFacade catalogCommerceFacade,
            PaymentRepository paymentRepository,
            IdentityCommerceFacade identityCommerceFacade,
            ShipmentRepository shipmentRepository,
            FulfillmentService fulfillmentService,
            FulfillmentStateService fulfillmentStateService,
            FulfillmentSimulationGuard simulationGuard,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderVoucherRepository = orderVoucherRepository;
        this.catalogCommerceFacade = catalogCommerceFacade;
        this.paymentRepository = paymentRepository;
        this.identityCommerceFacade = identityCommerceFacade;
        this.shipmentRepository = shipmentRepository;
        this.fulfillmentService = fulfillmentService;
        this.fulfillmentStateService = fulfillmentStateService;
        this.simulationGuard = simulationGuard;
        this.transactionTemplate = transactionTemplate;
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
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId);
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
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getSellerId().equals(sellerId)) {
            throw new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId);
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

    /**
     * Quy trình 3 pha tạo vận đơn GHN cho đơn hàng của người bán (A3):
     * - Pha 1: Transaction ngắn kiểm tra quyền sở hữu và trạng thái SELLER_CONFIRMED.
     *          Nếu đơn đã SHIPPED và có tracking_number thì trả về kết quả idempotent.
     * - Pha 2: Gọi FulfillmentService.createShipment ra ngoài transaction để không giữ DB lock khi chờ mạng GHN.
     * - Pha 3: Transaction ngắn khóa OrderEntity, kiểm tra lại trạng thái và chốt sang SHIPPED.
     */
    public OrderManagementDtos.OrderShipmentActionResponse shipSellerOrder(Long sellerId, Long orderId) {
        // Pha 1: Validate trong transaction ngắn
        ValidatedShipmentOrder validated = transactionTemplate.execute(status -> {
            OrderEntity order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

            if (!order.getSellerId().equals(sellerId)) {
                throw new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId);
            }

            if (OrderEntity.STATUS_SHIPPED.equals(order.getStatus())
                    || OrderEntity.STATUS_DELIVERED.equals(order.getStatus())
                    || OrderEntity.STATUS_COMPLETED.equals(order.getStatus())) {
                ShipmentEntity existing = shipmentRepository.findByOrderId(orderId).orElse(null);
                if (existing != null && existing.getTrackingNumber() != null && !existing.getTrackingNumber().isBlank()) {
                    return new ValidatedShipmentOrder(true, existing.getTrackingNumber(), existing.getCarrier(), order.getStatus());
                }
            }

            if (!OrderEntity.STATUS_SELLER_CONFIRMED.equals(order.getStatus())) {
                throw new IllegalStateException("Chỉ có thể tạo vận đơn khi người bán đã xác nhận đơn hàng (SELLER_CONFIRMED). Trạng thái hiện tại: " + order.getStatus());
            }

            return new ValidatedShipmentOrder(false, null, null, order.getStatus());
        });

        if (validated != null && validated.isAlreadyShipped()) {
            return new OrderManagementDtos.OrderShipmentActionResponse(
                    orderId,
                    validated.status(),
                    validated.trackingNumber(),
                    validated.carrier(),
                    "Đơn hàng đã được tạo vận đơn trước đó."
            );
        }

        // Pha 2: Outbound HTTP call sang GHN hoàn toàn ngoài transaction
        FulfillmentDtos.ShipmentResponse shipmentResponse = fulfillmentService.createShipment(sellerId, orderId, null);

        // Pha 3: Chốt trạng thái Order trong transaction ngắn độc lập
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

            if (OrderEntity.STATUS_SELLER_CONFIRMED.equals(order.getStatus())) {
                order.markShipped(now);
                orderRepository.save(order);
                log.info("Đơn hàng #{} đã chuyển trạng thái sang SHIPPED với vận đơn {}", orderId, shipmentResponse.trackingNumber());
            } else {
                log.warn("Đơn hàng #{} ở trạng thái {} trong pha chốt vận đơn (dự kiến SELLER_CONFIRMED)", orderId, order.getStatus());
            }

            return new OrderManagementDtos.OrderShipmentActionResponse(
                    orderId,
                    order.getStatus(),
                    shipmentResponse.trackingNumber(),
                    shipmentResponse.carrier(),
                    "Tạo vận đơn GHN thành công và đã chuyển trạng thái đơn hàng sang SHIPPED."
            );
        });
    }

    /**
     * Endpoint mô phỏng giao hàng dành riêng cho môi trường DEV/TEST (A1 + A2).
     * Chốt chặn bảo mật (fail-closed): Kiểm tra môi trường simulation NGAY ĐẦU method,
     * TRƯỚC kiểm tra owner và TRƯỚC mọi truy vấn database.
     */
    public OrderManagementDtos.OrderActionResponse deliverSellerOrder(Long sellerId, Long orderId) {
        // 1. Chốt chặn môi trường fail-closed trước mọi truy vấn
        simulationGuard.ensureSimulationAllowed();

        // 2. Kiểm tra quyền sở hữu người bán (trả 404 NoSuchElementException để không lộ sự tồn tại đơn)
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getSellerId().equals(sellerId)) {
            throw new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId);
        }

        Instant now = clock.instant();
        FulfillmentStateService.DeliveryTransitionResult result = fulfillmentStateService.markOrderDelivered(orderId, now, "SELLER_DEMO_DELIVER");

        if (!result.successful() && !result.idempotent()) {
            throw new IllegalStateException(result.reason());
        }

        return new OrderManagementDtos.OrderActionResponse(
                orderId,
                OrderEntity.STATUS_DELIVERED,
                result.reason()
        );
    }

    /**
     * Xác nhận hoàn tất đơn hàng và giải ngân tiền ký quỹ (Escrow Release: HELD -> RELEASED) (A4 + A5).
     *
     * Quy ước thứ tự khóa tài nguyên (Lock Order Invariant):
     *   OrderEntity (1) -> ShipmentEntity (2) -> PaymentEntity (3)
     *
     * Thứ tự kiểm tra bảo mật & nghiệp vụ bắt buộc:
     *   (1) Quyền sở hữu của buyer (trả 404 NoSuchElementException nếu không sở hữu)
     *   (2) Idempotency: nếu Order đã COMPLETED và Payment đã RELEASED -> trả 200 idempotent
     *   (3) Trạng thái Order phải là DELIVERED
     *   (4) Các chốt chặn an toàn: không có yêu cầu admin review, không UNDER_REVIEW, Payment phải là HELD
     *   (5) Cập nhật nguyên tử Order và Payment trong cùng một transaction
     */
    @Transactional
    public OrderManagementDtos.OrderActionResponse completeBuyerOrder(Long buyerId, Long orderId) {
        Instant now = clock.instant();

        // Bước 1: Khóa OrderEntity trước tiên theo quy ước Order -> Shipment -> Payment
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        // Kiểm tra quyền sở hữu buyer (trả 404 để không lộ sự tồn tại đơn)
        if (!order.getBuyerId().equals(buyerId)) {
            throw new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId);
        }

        // Bước 2: Khóa ShipmentEntity thứ hai
        ShipmentEntity shipment = shipmentRepository.findByOrderIdForUpdate(orderId).orElse(null);

        // Bước 3: Khóa PaymentEntity thứ ba
        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(orderId).orElse(null);

        // Bước 2 kiểm tra: Idempotency - nếu Order đã COMPLETED và Payment đã RELEASED
        if (OrderEntity.STATUS_COMPLETED.equals(order.getStatus())
                && payment != null && PaymentEntity.STATUS_RELEASED.equals(payment.getStatus())) {
            return new OrderManagementDtos.OrderActionResponse(
                    orderId,
                    OrderEntity.STATUS_COMPLETED,
                    "Đơn hàng đã được xác nhận hoàn thành trước đó."
            );
        }

        // Bước 3 kiểm tra: Trạng thái Order phải là DELIVERED
        if (!OrderEntity.STATUS_DELIVERED.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể xác nhận hoàn thành khi đơn hàng đã ở trạng thái DELIVERED. Trạng thái hiện tại: " + order.getStatus());
        }

        // Bước 4 kiểm tra: Các guards
        if (shipment != null && shipment.isAdminReviewRequired()) {
            throw new IllegalStateException("Đơn hàng đang có cảnh báo bất thường cần Admin kiểm duyệt, không thể tự động giải ngân.");
        }

        if (payment == null) {
            throw new IllegalStateException("Không tìm thấy thông tin ký quỹ (Payment) cho đơn hàng #" + orderId);
        }

        if (PaymentEntity.STATUS_UNDER_REVIEW.equals(payment.getStatus())) {
            throw new IllegalStateException("Thanh toán của đơn hàng đang trong trạng thái kiểm duyệt (UNDER_REVIEW), không thể giải ngân.");
        }

        if (!PaymentEntity.STATUS_HELD.equals(payment.getStatus())) {
            throw new IllegalStateException("Chỉ có thể giải ngân tiền ký quỹ khi thanh toán đang ở trạng thái HELD. Trạng thái hiện tại: " + payment.getStatus());
        }

        // Bước 5: Cập nhật nguyên tử Order và Payment trong cùng một transaction
        order.markCompleted(now);
        orderRepository.save(order);

        payment.markReleased(now);
        paymentRepository.save(payment);

        log.info("Đơn hàng #{} đã được Buyer #{} xác nhận COMPLETED. Escrow Payment #{} giải ngân thành công (RELEASED).",
                orderId, buyerId, payment.getId());

        return new OrderManagementDtos.OrderActionResponse(
                orderId,
                OrderEntity.STATUS_COMPLETED,
                "Xác nhận nhận hàng thành công. Tiền ký quỹ đã được giải ngân cho người bán."
        );
    }

    private record ValidatedShipmentOrder(
            boolean isAlreadyShipped,
            String trackingNumber,
            String carrier,
            String status
    ) {}

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
        ShipmentEntity shipment = shipmentRepository.findByOrderId(order.getId()).orElse(null);

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
                order.getCancellationReason(),
                shipment != null ? shipment.getTrackingNumber() : null,
                shipment != null ? shipment.getCarrier() : null,
                shipment != null ? shipment.getShippedAt() : null,
                shipment != null ? shipment.getDeliveredAt() : null
        );
    }
}
