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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentResponse;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentService;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentStateService;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderManagementService {
    private static final Logger log = LoggerFactory.getLogger(OrderManagementService.class);
    public static final String EARLY_COMPLETION_WARNING_VERSION = "v1.0";

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
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Autowired
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
            Clock clock,
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager
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
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

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
        this(orderRepository, orderItemRepository, orderVoucherRepository, catalogCommerceFacade,
                paymentRepository, identityCommerceFacade, shipmentRepository, fulfillmentService,
                fulfillmentStateService, simulationGuard, transactionTemplate, clock, null, null);
    }

    @Transactional(readOnly = true)
    public Page<OrderManagementDtos.OrderSummaryResponse> getBuyerOrders(Long buyerId, String status, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(50, Math.max(1, size));
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);
        Page<OrderEntity> orderPage;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            String upper = status.trim().toUpperCase();
            if ("SHIPPED".equals(upper)) {
                orderPage = orderRepository.findByBuyerIdAndStatusInOrderByCreatedAtDescIdDesc(buyerId, List.of(OrderEntity.STATUS_SHIPPED, OrderEntity.STATUS_DELIVERED), pageable);
            } else if ("REFUNDED".equals(upper)) {
                orderPage = orderRepository.findByBuyerIdAndStatusInOrderByCreatedAtDescIdDesc(buyerId, List.of(OrderEntity.STATUS_REFUNDED, OrderEntity.STATUS_DISPUTED), pageable);
            } else {
                orderPage = orderRepository.findByBuyerIdAndStatusOrderByCreatedAtDescIdDesc(buyerId, upper, pageable);
            }
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

        // Chặn đơn UC83 sử dụng endpoint /complete cũ (bắt buộc dùng confirm-received hoặc early-complete)
        if ("UC83".equals(order.getWorkflowModel())) {
            throw new IllegalArgumentException("Đơn hàng thuộc mô hình UC83. Vui lòng sử dụng chức năng Xác nhận nhận hàng (/confirm-received) hoặc Hoàn tất sớm (/early-complete).");
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

    /**
     * Xác nhận đã nhận hàng (UC27 - confirmReceived).
     *
     * Chỉ ghi nhận mốc thời gian orders.received_at = now().
     * Tính chất Idempotent: nếu đã có received_at thì trả về kết quả thành công và không ghi đè lại.
     * Chốt chặn an toàn: KHÔNG thay đổi trạng thái giải ngân (Payment vẫn HELD), KHÔNG đổi trạng thái đơn (vẫn DELIVERED).
     */
    @Transactional
    public OrderManagementDtos.OrderActionResponse confirmReceived(Long buyerId, Long orderId) {
        Instant now = clock.instant();

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId);
        }

        if (order.getReceivedAt() != null) {
            return new OrderManagementDtos.OrderActionResponse(
                    orderId,
                    order.getStatus(),
                    "Đơn hàng đã được xác nhận nhận hàng trước đó."
            );
        }

        if (!OrderEntity.STATUS_DELIVERED.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể xác nhận đã nhận hàng khi đơn hàng ở trạng thái DELIVERED. Trạng thái hiện tại: " + order.getStatus());
        }

        order.setReceivedAt(now);
        orderRepository.save(order);

        log.info("Đơn hàng #{} đã được Buyer #{} xác nhận đã nhận hàng (received_at = {}). Trạng thái đơn vẫn là {}.",
                orderId, buyerId, now, order.getStatus());

        return new OrderManagementDtos.OrderActionResponse(
                orderId,
                order.getStatus(),
                "Xác nhận đã nhận hàng thành công."
        );
    }

    /**
     * Xác nhận hoàn tất sớm đơn hàng (UC27, UC71 - earlyCompleteOrder).
     *
     * Yêu cầu warningConsent = true.
     * Gọi Stored Procedure og_early_complete_order(orderId, buyerId, EARLY_COMPLETION_WARNING_VERSION, commandKey).
     * Hàm này CHỈ ghi nhận early_completed_at và chèn sự kiện EARLY_COMPLETION_ACCEPTED vào order_events.
     * TUYỆT ĐỐI KHÔNG giải ngân và KHÔNG chuyển đơn sang COMPLETED trong hàm này (giải ngân tách sang Task 2.1b).
     * Sau khi gọi SP, flush + refresh entity để đồng bộ dữ liệu DB tránh stale cache.
     */
    @Transactional
    public OrderManagementDtos.OrderActionResponse earlyCompleteOrder(Long buyerId, Long orderId, OrderManagementDtos.EarlyCompleteRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.warningConsent())) {
            throw new IllegalArgumentException("Hoàn tất sớm yêu cầu người mua xác nhận từ bỏ quyền khiếu nại (warningConsent = true)");
        }

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId);
        }

        if (!"UC83".equals(order.getWorkflowModel())) {
            throw new IllegalStateException("Chỉ áp dụng hoàn tất sớm cho đơn hàng mô hình UC83");
        }

        if (!OrderEntity.STATUS_DELIVERED.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể hoàn tất sớm khi đơn hàng ở trạng thái DELIVERED. Trạng thái hiện tại: " + order.getStatus());
        }

        if (order.getValidDeliveredAt() == null) {
            throw new IllegalStateException("Đơn hàng chưa có mốc giao hàng hợp lệ (validDeliveredAt)");
        }

        if (jdbcTemplate != null) {
            try {
                Boolean hasBlockingCase = jdbcTemplate.queryForObject(
                        "SELECT og_order_has_blocking_case(?)", Boolean.class, orderId);
                if (Boolean.TRUE.equals(hasBlockingCase)) {
                    throw new IllegalStateException("Đơn hàng đang có hồ sơ khiếu nại/trả hàng chưa đóng, không thể hoàn tất sớm");
                }
            } catch (DataAccessException e) {
                log.debug("og_order_has_blocking_case check: {}", e.getMessage());
            }
        }

        String commandKey = (request.commandKey() != null && !request.commandKey().isBlank())
                ? request.commandKey().trim()
                : "EARLY_COMPLETE:" + orderId + ":" + UUID.randomUUID();

        if (jdbcTemplate != null) {
            try {
                Boolean success = jdbcTemplate.queryForObject(
                        "SELECT og_early_complete_order(?, ?, ?, ?)",
                        Boolean.class,
                        orderId, buyerId, EARLY_COMPLETION_WARNING_VERSION, commandKey
                );
                if (!Boolean.TRUE.equals(success)) {
                    throw new IllegalStateException("Không thể hoàn tất sớm: Stored procedure trả về false");
                }
            } catch (DataAccessException e) {
                log.error("Lỗi khi thực thi og_early_complete_order cho order #{}: {}", orderId, e.getMessage());
                throw new IllegalStateException("Không thể hoàn tất sớm: " + (e.getRootCause() != null ? e.getRootCause().getMessage() : e.getMessage()), e);
            }
        }

        if (entityManager != null) {
            entityManager.flush();
            entityManager.refresh(order);
        }

        log.info("Đơn hàng #{} đã hoàn tất sớm bởi Buyer #{}. Trạng thái đơn vẫn là {}.",
                orderId, buyerId, order.getStatus());

        return new OrderManagementDtos.OrderActionResponse(
                orderId,
                order.getStatus(),
                "Xác nhận hoàn tất sớm thành công."
        );
    }

    private record FundComponentRecord(
            UUID componentId,
            String componentType,
            BigDecimal confirmedAmount,
            BigDecimal reservedAmount,
            BigDecimal refundedAmount,
            BigDecimal releasedAmount
    ) {}

    private record SettlementOpRecord(
            UUID operationId,
            String state,
            BigDecimal amount
    ) {}

    /**
     * Tiến trình giải ngân tiền cho người bán (UC71 - disburseSellerFunds).
     *
     * Kiểm tra đầy đủ 4 điều kiện nghiệp vụ BR-UC71-04:
     *   (1) valid_delivered_at IS NOT NULL
     *   (2) now >= valid_delivered_at + 3 days HOẶC early_completed_at IS NOT NULL
     *   (3) og_order_has_blocking_case(orderId) = false
     *   (4) Không còn money_holds có closed_at IS NULL cho các component của đơn
     *
     * Nhánh LEGACY_V14:
     *   - payment.markReleased(now), order.markCompleted(now)
     *   - Idempotent: nếu đơn đã COMPLETED thì trả về kết quả hiện tại, không lỗi.
     *
     * Nhánh UC83:
     *   - Thứ tự lock: orders (PESSIMISTIC_WRITE) -> order_fund_components -> settlement_operations.
     *   - Chỉ xử lý component hàng: GOODS_CASH và PLATFORM_GOODS_SUBSIDY.
     *   - Bỏ qua component có số dư khả dụng <= 0 (CHECK amount > 0; đơn 0đ không tạo giao dịch).
     *   - Số tiền = confirmed_amount - released_amount - refunded_amount - reserved_amount.
     *   - Tạo settlement_operations riêng cho từng component:
     *     * INSERT state='REQUESTED', operation_type='RELEASE_SELLER', cause_type='COMPLETION',
     *       command_key = "DISBURSE:SELLER:{orderId}:{componentType}:COMPLETION"
     *     * UPDATE sang state='CONFIRMED', confirmed_at=now(), provider='INTERNAL_SIMULATED',
     *       provider_operation_id='SIM-RELEASE-' + operation_id.
     *     * Trigger DB (trg_settlement_operation_guard) tự cộng released_amount.
     *     * TUYỆT ĐỐI không UPDATE trực tiếp order_fund_components.
     *   - Idempotent: tra theo command_key trước khi insert; replay không tạo bản ghi thứ ba
     *     và không cộng released_amount lần hai.
     *   - Sau khi hoàn tất tất cả component: order.markCompleted(now) và lưu.
     */
    @Transactional
    public OrderManagementDtos.DisbursementResponse disburseSellerFunds(Long orderId) {
        Instant now = clock.instant();

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (entityManager != null) {
            entityManager.refresh(order);
        }

        boolean isLegacy = "LEGACY_V14".equals(order.getWorkflowModel());

        // Idempotency: nếu đơn hàng đã COMPLETED
        if (OrderEntity.STATUS_COMPLETED.equals(order.getStatus())) {
            if (isLegacy) {
                PaymentEntity payment = paymentRepository.findByOrderId(orderId).orElse(null);
                BigDecimal totalDisbursed = payment != null ? payment.getAmount() : BigDecimal.ZERO;
                return new OrderManagementDtos.DisbursementResponse(
                        orderId,
                        order.getStatus(),
                        List.of(),
                        totalDisbursed,
                        "Đơn hàng đã được giải ngân và hoàn tất trước đó."
                );
            } else {
                List<OrderManagementDtos.DisbursementOperationDto> ops = queryExistingDisbursementOperations(orderId);
                BigDecimal total = ops.stream()
                        .map(OrderManagementDtos.DisbursementOperationDto::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                return new OrderManagementDtos.DisbursementResponse(
                        orderId,
                        order.getStatus(),
                        ops,
                        total,
                        "Đơn hàng đã được giải ngân và hoàn tất trước đó."
                );
            }
        }

        if (!OrderEntity.STATUS_DELIVERED.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể giải ngân cho đơn hàng ở trạng thái DELIVERED. Trạng thái hiện tại: " + order.getStatus());
        }

        // Điều kiện 1: valid_delivered_at IS NOT NULL
        if (order.getValidDeliveredAt() == null) {
            throw new IllegalStateException("Đơn hàng chưa có mốc giao hàng hợp lệ (validDeliveredAt)");
        }

        // Điều kiện 2: now >= valid_delivered_at + 3 days HOẶC early_completed_at IS NOT NULL
        boolean isThreeDaysPassed = !now.isBefore(order.getValidDeliveredAt().plus(Duration.ofDays(3)));
        boolean isEarlyCompleted = order.getEarlyCompletedAt() != null;
        if (!isThreeDaysPassed && !isEarlyCompleted) {
            throw new IllegalStateException("Chưa đủ 3 ngày kể từ khi giao hàng và đơn hàng chưa được xác nhận hoàn tất sớm");
        }

        // Điều kiện 3: og_order_has_blocking_case(orderId) = false
        if (jdbcTemplate != null) {
            try {
                Boolean hasBlockingCase = jdbcTemplate.queryForObject(
                        "SELECT og_order_has_blocking_case(?)", Boolean.class, orderId);
                if (Boolean.TRUE.equals(hasBlockingCase)) {
                    throw new IllegalStateException("Đơn hàng đang có hồ sơ khiếu nại/trả hàng chưa đóng, không thể giải ngân");
                }
            } catch (DataAccessException e) {
                log.debug("og_order_has_blocking_case check: {}", e.getMessage());
            }
        }

        // Điều kiện 4: Không còn money_holds có closed_at IS NULL cho các component của đơn
        if (jdbcTemplate != null) {
            try {
                Boolean hasActiveHold = jdbcTemplate.queryForObject("""
                        SELECT EXISTS(
                            SELECT 1 FROM money_holds mh
                            JOIN order_fund_components ofc ON ofc.component_id = mh.component_id
                            WHERE ofc.order_id = ? AND mh.closed_at IS NULL
                        )
                        """, Boolean.class, orderId);
                if (Boolean.TRUE.equals(hasActiveHold)) {
                    throw new IllegalStateException("Đơn hàng có khoản tiền đang bị tạm giữ (money_hold chưa đóng), không thể giải ngân");
                }
            } catch (DataAccessException e) {
                log.debug("money_holds check: {}", e.getMessage());
            }
        }

        if (isLegacy) {
            return disburseLegacyOrder(order, now);
        } else {
            return disburseUc83Order(order, now);
        }
    }

    private OrderManagementDtos.DisbursementResponse disburseLegacyOrder(OrderEntity order, Instant now) {
        Long orderId = order.getId();
        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (payment == null) {
            throw new IllegalStateException("Không tìm thấy thông tin ký quỹ (Payment) cho đơn hàng #" + orderId);
        }
        if (PaymentEntity.STATUS_UNDER_REVIEW.equals(payment.getStatus())) {
            throw new IllegalStateException("Thanh toán của đơn hàng đang trong trạng thái kiểm duyệt (UNDER_REVIEW), không thể giải ngân.");
        }
        if (!PaymentEntity.STATUS_HELD.equals(payment.getStatus()) && !PaymentEntity.STATUS_RELEASED.equals(payment.getStatus())) {
            throw new IllegalStateException("Chỉ có thể giải ngân tiền ký quỹ khi thanh toán ở trạng thái HELD. Trạng thái hiện tại: " + payment.getStatus());
        }

        if (!PaymentEntity.STATUS_RELEASED.equals(payment.getStatus())) {
            payment.markReleased(now);
            paymentRepository.save(payment);
        }

        order.markCompleted(now);
        orderRepository.save(order);

        log.info("Đơn hàng LEGACY_V14 #{} đã được giải ngân thành công (Payment #{} RELEASED, Order COMPLETED).",
                orderId, payment.getId());

        return new OrderManagementDtos.DisbursementResponse(
                orderId,
                order.getStatus(),
                List.of(),
                payment.getAmount(),
                "Giải ngân tiền bán thành công cho đơn hàng LEGACY_V14."
        );
    }

    private OrderManagementDtos.DisbursementResponse disburseUc83Order(OrderEntity order, Instant now) {
        Long orderId = order.getId();
        List<OrderManagementDtos.DisbursementOperationDto> operationsList = new ArrayList<>();
        BigDecimal totalDisbursed = BigDecimal.ZERO;

        if (jdbcTemplate != null) {
            // Khóa order_fund_components theo thứ tự component_type để đảm bảo lock order
            List<FundComponentRecord> components = jdbcTemplate.query("""
                    SELECT component_id, component_type, confirmed_amount, reserved_amount, refunded_amount, released_amount
                    FROM order_fund_components
                    WHERE order_id = ?
                      AND component_type IN ('GOODS_CASH', 'PLATFORM_GOODS_SUBSIDY')
                    ORDER BY component_type
                    FOR UPDATE
                    """,
                    (rs, rowNum) -> new FundComponentRecord(
                            rs.getObject("component_id", UUID.class),
                            rs.getString("component_type"),
                            rs.getBigDecimal("confirmed_amount"),
                            rs.getBigDecimal("reserved_amount"),
                            rs.getBigDecimal("refunded_amount"),
                            rs.getBigDecimal("released_amount")
                    ),
                    orderId
            );

            for (FundComponentRecord comp : components) {
                BigDecimal confirmed = comp.confirmedAmount() != null ? comp.confirmedAmount() : BigDecimal.ZERO;
                BigDecimal reserved = comp.reservedAmount() != null ? comp.reservedAmount() : BigDecimal.ZERO;
                BigDecimal refunded = comp.refundedAmount() != null ? comp.refundedAmount() : BigDecimal.ZERO;
                BigDecimal released = comp.releasedAmount() != null ? comp.releasedAmount() : BigDecimal.ZERO;
                BigDecimal available = confirmed.subtract(reserved).subtract(refunded).subtract(released);

                String commandKey = "DISBURSE:SELLER:" + orderId + ":" + comp.componentType() + ":COMPLETION";

                // Kiểm tra xem đã có operation cho command_key này chưa (Idempotency)
                List<SettlementOpRecord> existing = jdbcTemplate.query("""
                        SELECT operation_id, state, amount
                        FROM settlement_operations
                        WHERE command_key = ?
                        FOR UPDATE
                        """,
                        (rs, rowNum) -> new SettlementOpRecord(
                                rs.getObject("operation_id", UUID.class),
                                rs.getString("state"),
                                rs.getBigDecimal("amount")
                        ),
                        commandKey
                );

                if (!existing.isEmpty()) {
                    SettlementOpRecord op = existing.getFirst();
                    if ("CONFIRMED".equals(op.state())) {
                        operationsList.add(new OrderManagementDtos.DisbursementOperationDto(
                                op.operationId(), comp.componentId(), comp.componentType(),
                                op.amount(), op.state(), commandKey
                        ));
                        totalDisbursed = totalDisbursed.add(op.amount());
                    } else if ("REQUESTED".equals(op.state()) || "PROCESSING".equals(op.state())) {
                        jdbcTemplate.update("""
                                UPDATE settlement_operations
                                SET state = 'CONFIRMED',
                                    confirmed_at = ?,
                                    provider = 'INTERNAL_SIMULATED',
                                    provider_operation_id = ?
                                WHERE operation_id = ?
                                """,
                                Timestamp.from(now), "SIM-RELEASE-" + op.operationId(), op.operationId()
                        );
                        operationsList.add(new OrderManagementDtos.DisbursementOperationDto(
                                op.operationId(), comp.componentId(), comp.componentType(),
                                op.amount(), "CONFIRMED", commandKey
                        ));
                        totalDisbursed = totalDisbursed.add(op.amount());
                    }
                } else if (available.compareTo(BigDecimal.ZERO) > 0) {
                    UUID opId = UUID.randomUUID();
                    // 1. Chèn operation ở trạng thái REQUESTED
                    jdbcTemplate.update("""
                            INSERT INTO settlement_operations(
                                operation_id, component_id, operation_type, amount, cause_type,
                                recipient_user_id, state, requested_at, command_key
                            ) VALUES (
                                ?, ?, 'RELEASE_SELLER', ?, 'COMPLETION',
                                ?, 'REQUESTED', ?, ?
                            )
                            """,
                            opId, comp.componentId(), available,
                            order.getSellerId(), Timestamp.from(now), commandKey
                    );

                    // 2. Cập nhật sang CONFIRMED (trigger DB trg_settlement_operation_guard tự động cộng released_amount)
                    jdbcTemplate.update("""
                            UPDATE settlement_operations
                            SET state = 'CONFIRMED',
                                confirmed_at = ?,
                                provider = 'INTERNAL_SIMULATED',
                                provider_operation_id = ?
                            WHERE operation_id = ?
                            """,
                            Timestamp.from(now), "SIM-RELEASE-" + opId, opId
                    );

                    operationsList.add(new OrderManagementDtos.DisbursementOperationDto(
                            opId, comp.componentId(), comp.componentType(),
                            available, "CONFIRMED", commandKey
                    ));
                    totalDisbursed = totalDisbursed.add(available);
                }
                // Nếu available <= 0 và chưa có operation: bỏ qua (không tạo giao dịch 0đ)
            }
        }

        order.markCompleted(now);
        orderRepository.save(order);

        if (entityManager != null) {
            entityManager.flush();
        }

        log.info("Đơn hàng UC83 #{} đã được giải ngân thành công với {} operations, tổng tiền: {}.",
                orderId, operationsList.size(), totalDisbursed);

        return new OrderManagementDtos.DisbursementResponse(
                orderId,
                order.getStatus(),
                operationsList,
                totalDisbursed,
                "Giải ngân tiền bán thành công cho đơn hàng UC83."
        );
    }

    private List<OrderManagementDtos.DisbursementOperationDto> queryExistingDisbursementOperations(Long orderId) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT so.operation_id, so.component_id, ofc.component_type, so.amount, so.state, so.command_key
                    FROM settlement_operations so
                    JOIN order_fund_components ofc ON ofc.component_id = so.component_id
                    WHERE ofc.order_id = ? AND so.operation_type = 'RELEASE_SELLER'
                    ORDER BY ofc.component_type
                    """,
                    (rs, rowNum) -> new OrderManagementDtos.DisbursementOperationDto(
                            rs.getObject("operation_id", UUID.class),
                            rs.getObject("component_id", UUID.class),
                            rs.getString("component_type"),
                            rs.getBigDecimal("amount"),
                            rs.getString("state"),
                            rs.getString("command_key")
                    ),
                    orderId
            );
        } catch (DataAccessException e) {
            log.debug("queryExistingDisbursementOperations: {}", e.getMessage());
            return List.of();
        }
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
