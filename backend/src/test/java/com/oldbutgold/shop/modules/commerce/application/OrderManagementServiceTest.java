package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentResponse;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentService;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentStateService;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentStateService.DeliveryTransitionResult;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderManagementServiceTest {
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private OrderVoucherRepository orderVoucherRepository;
    private CatalogCommerceFacade catalogCommerceFacade;
    private PaymentRepository paymentRepository;
    private IdentityCommerceFacade identityCommerceFacade;
    private ShipmentRepository shipmentRepository;
    private FulfillmentService fulfillmentService;
    private FulfillmentStateService fulfillmentStateService;
    private FulfillmentSimulationGuard simulationGuard;
    private TransactionTemplate transactionTemplate;
    private Clock clock;
    private JdbcTemplate jdbcTemplate;
    private EntityManager entityManager;

    private OrderManagementService service;
    private Instant now;
    private OrderEntity order;
    private OrderItemEntity orderItem;

    @BeforeEach
    void setUp() throws Exception {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        orderVoucherRepository = mock(OrderVoucherRepository.class);
        catalogCommerceFacade = mock(CatalogCommerceFacade.class);
        paymentRepository = mock(PaymentRepository.class);
        identityCommerceFacade = mock(IdentityCommerceFacade.class);
        shipmentRepository = mock(ShipmentRepository.class);
        fulfillmentService = mock(FulfillmentService.class);
        fulfillmentStateService = mock(FulfillmentStateService.class);
        simulationGuard = mock(FulfillmentSimulationGuard.class);
        transactionTemplate = mock(TransactionTemplate.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        entityManager = mock(EntityManager.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        now = Instant.parse("2026-09-21T10:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));

        service = new OrderManagementService(
                orderRepository,
                orderItemRepository,
                orderVoucherRepository,
                catalogCommerceFacade,
                paymentRepository,
                identityCommerceFacade,
                shipmentRepository,
                fulfillmentService,
                fulfillmentStateService,
                simulationGuard,
                transactionTemplate,
                clock,
                jdbcTemplate,
                entityManager
        );

        order = new OrderEntity(
                UUID.randomUUID(),
                100L, // buyerId
                200L, // sellerId
                1L,
                "Nguyễn Văn A",
                "0901234567",
                "Hồ Chí Minh",
                "Quận 1",
                "Phường Bến Nghé",
                "123 Lê Lợi",
                new BigDecimal("500000.00"),
                new BigDecimal("30000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "PLATFORM",
                new BigDecimal("530000.00"),
                now.plus(Duration.ofHours(1)),
                now
        );
        setId(order, 1L);

        orderItem = new OrderItemEntity(
                1L, 10L, "Máy ảnh cơ Canon QL17 GIII", (short) 1,
                new BigDecimal("500000.00"), new BigDecimal("500000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000.00"),
                new BigDecimal("500000.00"), 1L, null, "LIST_PRICE"
        );
    }

    @Test
    void cancelBuyerOrder_pendingPayment_releasesProduct() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(orderItem));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());

        OrderManagementDtos.OrderActionResponse response = service.cancelBuyerOrder(100L, 1L, "Tôi đổi ý không mua nữa");

        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Tôi đổi ý không mua nữa");

        verify(catalogCommerceFacade).releaseProductReservation(10L, 1L, now);
        verify(orderRepository).save(order);
    }

    @Test
    void confirmSellerOrder_paidHeld_marksSold() {
        order.markPaidHeld(now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(orderItem));

        OrderManagementDtos.OrderActionResponse response = service.confirmSellerOrder(200L, 1L);

        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_SELLER_CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SELLER_CONFIRMED);

        verify(catalogCommerceFacade).markProductSold(10L, 1L, now);
        verify(orderRepository).save(order);
    }

    @Test
    void confirmSellerOrder_notPaidHeld_throwsIllegalStateException() {
        // Order is still PAYMENT_PENDING
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirmSellerOrder(200L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAID_HELD");
    }

    @Test
    void expireOverdueOrders_cancelsOrdersAndReleasesProduct() {
        when(orderRepository.findOverduePendingOrders(now)).thenReturn(List.of(order));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(orderItem));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());

        int expiredCount = service.expireOverdueOrders();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_CANCELLED);
        assertThat(order.getCancellationReason()).contains("Hết hạn");

        verify(catalogCommerceFacade).releaseProductReservation(10L, 1L, now);
        verify(orderRepository).save(order);
    }

    @Test
    void getBuyerOrders_withPaginationAndClamping_callsRepositoryWithStableSort() {
        org.springframework.data.domain.Page<OrderEntity> emptyPage =
                new org.springframework.data.domain.PageImpl<>(List.of(order));
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);

        when(orderRepository.findByBuyerIdAndStatusOrderByCreatedAtDescIdDesc(eq(100L), eq("PAID_HELD"), pageableCaptor.capture()))
                .thenReturn(emptyPage);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(orderItem));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        // Negative page (-1) clamped to 0, excessive size (100) clamped to 50
        org.springframework.data.domain.Page<OrderManagementDtos.OrderSummaryResponse> result =
                service.getBuyerOrders(100L, "PAID_HELD", -1, 100);

        assertThat(result.getContent()).hasSize(1);
        org.springframework.data.domain.Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(0);
        assertThat(captured.getPageSize()).isEqualTo(50);
    }

    @Test
    void getBuyerOrders_allStatus_callsFindByBuyerIdOrderByCreatedAtDescIdDesc() {
        org.springframework.data.domain.Page<OrderEntity> emptyPage =
                new org.springframework.data.domain.PageImpl<>(List.of(order));
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);

        when(orderRepository.findByBuyerIdOrderByCreatedAtDescIdDesc(eq(100L), pageableCaptor.capture()))
                .thenReturn(emptyPage);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(orderItem));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        org.springframework.data.domain.Page<OrderManagementDtos.OrderSummaryResponse> result =
                service.getBuyerOrders(100L, "ALL", 1, 10);

        assertThat(result.getContent()).hasSize(1);
        org.springframework.data.domain.Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(1);
        assertThat(captured.getPageSize()).isEqualTo(10);
    }

    @Test
    void getSellerOrders_withPagination_callsRepositoryWithStableSort() {
        org.springframework.data.domain.Page<OrderEntity> emptyPage =
                new org.springframework.data.domain.PageImpl<>(List.of(order));
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);

        when(orderRepository.findBySellerIdAndStatusOrderByCreatedAtDescIdDesc(eq(200L), eq("SELLER_CONFIRMED"), pageableCaptor.capture()))
                .thenReturn(emptyPage);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(orderItem));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        org.springframework.data.domain.Page<OrderManagementDtos.OrderSummaryResponse> result =
                service.getSellerOrders(200L, "SELLER_CONFIRMED", 0, 10);

        assertThat(result.getContent()).hasSize(1);
        org.springframework.data.domain.Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(0);
        assertThat(captured.getPageSize()).isEqualTo(10);
    }

    // ==========================================
    // Tests for shipSellerOrder (A3, A5)
    // ==========================================

    @Test
    void shipSellerOrder_success_transitionsToShipped() {
        order.markPaidHeld(now);
        order.confirmBySeller(now);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        ShipmentResponse mockShipmentResponse = new ShipmentResponse(
                1L, "GHN123456789", "CL1L", "GHN", "PENDING",
                new BigDecimal("30000.00"), now.plus(Duration.ofDays(3)), "ready_to_pick", now
        );
        when(fulfillmentService.createShipment(200L, 1L, null)).thenReturn(mockShipmentResponse);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        OrderManagementDtos.OrderShipmentActionResponse response = service.shipSellerOrder(200L, 1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_SHIPPED);
        assertThat(response.trackingNumber()).isEqualTo("GHN123456789");
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SHIPPED);
        verify(orderRepository).save(order);
    }

    @Test
    void shipSellerOrder_unauthorizedSeller_throwsNoSuchElementException() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.shipSellerOrder(999L, 1L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Không tìm thấy đơn hàng #1");

        verifyNoInteractions(fulfillmentService);
    }

    @Test
    void shipSellerOrder_alreadyShipped_returnsIdempotentWithoutCallingFulfillment() throws Exception {
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);

        ShipmentEntity existingShipment = new ShipmentEntity(1L, "CL1L", now);
        existingShipment.setTrackingNumber("GHN_EXISTING_TRACKING");

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId(1L)).thenReturn(Optional.of(existingShipment));

        OrderManagementDtos.OrderShipmentActionResponse response = service.shipSellerOrder(200L, 1L);

        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_SHIPPED);
        assertThat(response.trackingNumber()).isEqualTo("GHN_EXISTING_TRACKING");
        assertThat(response.message()).contains("đã được tạo vận đơn trước đó");
        verifyNoInteractions(fulfillmentService);
    }

    @Test
    void shipSellerOrder_ghnThrowsException_orderRemainsConfirmed() {
        order.markPaidHeld(now);
        order.confirmBySeller(now);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(fulfillmentService.createShipment(200L, 1L, null)).thenThrow(new RuntimeException("GHN Gateway Timeout"));

        assertThatThrownBy(() -> service.shipSellerOrder(200L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("GHN Gateway Timeout");

        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SELLER_CONFIRMED);
        verify(orderRepository, never()).save(order);
    }

    @Test
    void shipSellerOrder_phase3Recovery_recoversWithoutDuplicateGhnCall() {
        order.markPaidHeld(now);
        order.confirmBySeller(now);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        ShipmentResponse existingResponse = new ShipmentResponse(
                1L, "GHN_RECOVERED", "CL1L", "GHN", "PENDING",
                new BigDecimal("30000.00"), now.plus(Duration.ofDays(3)), "ready_to_pick", now
        );
        when(fulfillmentService.createShipment(200L, 1L, null)).thenReturn(existingResponse);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        OrderManagementDtos.OrderShipmentActionResponse response = service.shipSellerOrder(200L, 1L);

        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_SHIPPED);
        assertThat(response.trackingNumber()).isEqualTo("GHN_RECOVERED");
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SHIPPED);
        verify(orderRepository).save(order);
    }

    // ==========================================
    // Tests for deliverSellerOrder (A1, A2, A5)
    // ==========================================

    @Test
    void deliverSellerOrder_simulationBlocked_throwsAccessDeniedBeforeDbQuery() {
        doThrow(new AccessDeniedException("Môi trường PROD: Mô phỏng giao hàng đã bị vô hiệu hóa"))
                .when(simulationGuard).ensureSimulationAllowed();

        assertThatThrownBy(() -> service.deliverSellerOrder(200L, 1L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("vô hiệu hóa");

        verifyNoInteractions(orderRepository);
        verifyNoInteractions(fulfillmentStateService);
    }

    @Test
    void deliverSellerOrder_unauthorizedSeller_throwsNoSuchElementException() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.deliverSellerOrder(999L, 1L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Không tìm thấy đơn hàng #1");

        verifyNoInteractions(fulfillmentStateService);
    }

    @Test
    void deliverSellerOrder_success_callsMarkOrderDelivered() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(fulfillmentStateService.markOrderDelivered(eq(1L), any(), eq("SELLER_DEMO_DELIVER")))
                .thenReturn(DeliveryTransitionResult.ofSuccess());

        OrderManagementDtos.OrderActionResponse response = service.deliverSellerOrder(200L, 1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(response.message()).isEqualTo("SUCCESS");
    }

    // ==========================================
    // Tests for completeBuyerOrder (A4, A5)
    // ==========================================

    @Test
    void completeBuyerOrder_unauthorizedBuyer_throwsNoSuchElementException() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.completeBuyerOrder(999L, 1L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Không tìm thấy đơn hàng #1");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void completeBuyerOrder_alreadyCompletedAndReleased_returnsIdempotent() {
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        order.markCompleted(now);

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("530000.00"), PaymentEntity.METHOD_VNPAY, now);
        payment.markHeld("TX_VNPAY", now);
        payment.markReleased(now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        OrderManagementDtos.OrderActionResponse response = service.completeBuyerOrder(100L, 1L);

        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(response.message()).contains("đã được xác nhận hoàn thành trước đó");
        verify(orderRepository, never()).save(order);
        verify(paymentRepository, never()).save(payment);
    }

    @Test
    void completeBuyerOrder_notDelivered_throwsIllegalStateException() {
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("530000.00"), PaymentEntity.METHOD_VNPAY, now);
        payment.markHeld("TX_VNPAY", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.completeBuyerOrder(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void completeBuyerOrder_adminReviewRequired_throwsIllegalStateException() {
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);

        ShipmentEntity shipment = new ShipmentEntity(1L, "CL1L", now);
        shipment.recordIncident("incident", "Nghi ngờ thất lạc kiện hàng", now);

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("530000.00"), PaymentEntity.METHOD_VNPAY, now);
        payment.markHeld("TX_VNPAY", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(shipment));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.completeBuyerOrder(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin kiểm duyệt");
    }

    @Test
    void completeBuyerOrder_paymentNotHeld_throwsIllegalStateException() {
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("530000.00"), PaymentEntity.METHOD_VNPAY, now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.completeBuyerOrder(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HELD");
    }

    @Test
    void completeBuyerOrder_success_atomicallyUpdatesOrderAndReleasesPaymentInStrictLockOrder() {
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);

        ShipmentEntity shipment = new ShipmentEntity(1L, "CL1L", now);
        shipment.setTrackingNumber("GHN123");

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("530000.00"), PaymentEntity.METHOD_VNPAY, now);
        payment.markHeld("TX_VNPAY_OK", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(shipment));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        OrderManagementDtos.OrderActionResponse response = service.completeBuyerOrder(100L, 1L);

        // Verification of state changes
        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(order.getCompletedAt()).isEqualTo(now);
        assertThat(payment.getStatus()).isEqualTo(PaymentEntity.STATUS_RELEASED);
        assertThat(payment.getReleasedAt()).isEqualTo(now);

        verify(orderRepository).save(order);
        verify(paymentRepository).save(payment);

        // Verification of strict Lock Order Invariant: Order -> Shipment -> Payment
        InOrder inOrder = inOrder(orderRepository, shipmentRepository, paymentRepository);
        inOrder.verify(orderRepository).findByIdForUpdate(1L);
        inOrder.verify(shipmentRepository).findByOrderIdForUpdate(1L);
        inOrder.verify(paymentRepository).findByOrderIdForUpdate(1L);
    }

    @Test
    void confirmReceived_success_setsReceivedAtAndKeepsDeliveredWithoutDisbursement() {
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        OrderManagementDtos.OrderActionResponse response = service.confirmReceived(100L, 1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(order.getReceivedAt()).isEqualTo(now);
        assertThat(order.getCompletedAt()).isNull();

        verify(orderRepository).save(order);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void confirmReceived_idempotent_whenAlreadyReceived_doesNotOverwrite() {
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);

        Instant earlier = now.minus(Duration.ofHours(2));
        order.setReceivedAt(earlier);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        OrderManagementDtos.OrderActionResponse response = service.confirmReceived(100L, 1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(order.getReceivedAt()).isEqualTo(earlier);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirmReceived_orderNotDelivered_throwsIllegalStateException() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirmReceived(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void confirmReceived_unauthorizedBuyer_throwsNoSuchElementException() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirmReceived(999L, 1L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void completeBuyerOrder_uc83Order_throwsIllegalArgumentExceptionWithClearGuidance() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.completeBuyerOrder(100L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UC83")
                .hasMessageContaining("confirm-received")
                .hasMessageContaining("early-complete");
    }

    @Test
    void earlyCompleteOrder_missingConsent_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.earlyCompleteOrder(100L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warningConsent");

        assertThatThrownBy(() -> service.earlyCompleteOrder(100L, 1L, new OrderManagementDtos.EarlyCompleteRequest(false, "CMD1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warningConsent");
    }

    @Test
    void earlyCompleteOrder_notUc83_throwsIllegalStateException() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        OrderManagementDtos.EarlyCompleteRequest request = new OrderManagementDtos.EarlyCompleteRequest(true, "CMD1");
        assertThatThrownBy(() -> service.earlyCompleteOrder(100L, 1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UC83");
    }

    @Test
    void earlyCompleteOrder_notDelivered_throwsIllegalStateException() throws Exception {
        setField(order, "workflowModel", "UC83");
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        OrderManagementDtos.EarlyCompleteRequest request = new OrderManagementDtos.EarlyCompleteRequest(true, "CMD1");
        assertThatThrownBy(() -> service.earlyCompleteOrder(100L, 1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void earlyCompleteOrder_missingValidDeliveredAt_throwsIllegalStateException() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        OrderManagementDtos.EarlyCompleteRequest request = new OrderManagementDtos.EarlyCompleteRequest(true, "CMD1");
        assertThatThrownBy(() -> service.earlyCompleteOrder(100L, 1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validDeliveredAt");
    }

    @Test
    void earlyCompleteOrder_hasActiveCase_throwsIllegalStateException() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(true);

        OrderManagementDtos.EarlyCompleteRequest request = new OrderManagementDtos.EarlyCompleteRequest(true, "CMD1");
        assertThatThrownBy(() -> service.earlyCompleteOrder(100L, 1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("khiếu nại");
    }

    @Test
    void earlyCompleteOrder_success_callsProcedureFlushRefreshAndKeepsDeliveredWithoutDisbursement() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(eq("SELECT og_early_complete_order(?, ?, ?, ?)"), eq(Boolean.class), eq(1L), eq(100L), eq("v1.0"), eq("CMD_123")))
                .thenReturn(true);

        OrderManagementDtos.EarlyCompleteRequest request = new OrderManagementDtos.EarlyCompleteRequest(true, "CMD_123");
        OrderManagementDtos.OrderActionResponse response = service.earlyCompleteOrder(100L, 1L, request);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(order.getCompletedAt()).isNull();

        verify(entityManager).flush();
        verify(entityManager).refresh(order);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void disburseSellerFunds_orderNotFound_throwsNoSuchElementException() {
        when(orderRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disburseSellerFunds(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Không tìm thấy đơn hàng #999");
    }

    @Test
    void disburseSellerFunds_notDelivered_throwsIllegalStateException() throws Exception {
        setField(order, "workflowModel", "UC83");
        // status is PAYMENT_PENDING
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.disburseSellerFunds(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chỉ có thể giải ngân cho đơn hàng ở trạng thái DELIVERED");
    }

    @Test
    void disburseSellerFunds_missingValidDeliveredAt_throwsIllegalStateException() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        // validDeliveredAt is null

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.disburseSellerFunds(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validDeliveredAt");
    }

    @Test
    void disburseSellerFunds_neither3DaysNorEarlyCompleted_throwsIllegalStateException() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(1))); // only 1 day ago

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.disburseSellerFunds(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chưa đủ 3 ngày");
    }

    @Test
    void disburseSellerFunds_hasBlockingCase_throwsIllegalStateException() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.disburseSellerFunds(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("khiếu nại");
    }

    @Test
    void disburseSellerFunds_hasActiveHold_throwsIllegalStateException() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), eq(1L)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.disburseSellerFunds(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tạm giữ");
    }

    @Test
    void disburseSellerFunds_legacyEligible_marksPaymentReleasedAndOrderCompleted() throws Exception {
        setField(order, "workflowModel", "LEGACY_V14");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("120000.00"), PaymentEntity.METHOD_VNPAY, now);
        payment.markHeld("TX_123", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        var response = service.disburseSellerFunds(1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(response.totalDisbursedAmount()).isEqualByComparingTo(new BigDecimal("120000.00"));
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(order.getCompletedAt()).isEqualTo(now);
        assertThat(payment.getStatus()).isEqualTo(PaymentEntity.STATUS_RELEASED);
        assertThat(payment.getReleasedAt()).isEqualTo(now);

        verify(orderRepository).save(order);
        verify(paymentRepository).save(payment);
    }

    @Test
    void disburseSellerFunds_legacyAlreadyCompleted_returnsIdempotent() throws Exception {
        setField(order, "workflowModel", "LEGACY_V14");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        order.markCompleted(now);

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("120000.00"), PaymentEntity.METHOD_VNPAY, now);
        payment.markHeld("TX_123", now);
        payment.markReleased(now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

        var response = service.disburseSellerFunds(1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(response.totalDisbursedAmount()).isEqualByComparingTo(new BigDecimal("120000.00"));
        assertThat(response.message()).contains("trước đó");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void disburseSellerFunds_hasFinalFullGoodsRefund_throwsIllegalStateExceptionAndNoDisbursement() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("money_holds"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("case_decisions"), eq(Boolean.class), eq(1L)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.disburseSellerFunds(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FULL_GOODS_REFUND/EXEMPT_GOODS_REFUND");

        verify(paymentRepository, never()).findByOrderIdForUpdate(any());
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(jdbcTemplate, never()).update(contains("INSERT INTO settlement_operations"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void disburseSellerFunds_hasFinalExemptGoodsRefund_throwsIllegalStateExceptionAndNoDisbursement() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("money_holds"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("case_decisions"), eq(Boolean.class), eq(1L)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.disburseSellerFunds(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FULL_GOODS_REFUND/EXEMPT_GOODS_REFUND");

        verify(paymentRepository, never()).findByOrderIdForUpdate(any());
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(jdbcTemplate, never()).update(contains("INSERT INTO settlement_operations"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void disburseSellerFunds_hasFinalRejectReturn_allowsDisbursement() throws Exception {
        setField(order, "workflowModel", "LEGACY_V14");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("120000.00"), PaymentEntity.METHOD_VNPAY, now);
        payment.markHeld("TX_123", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("money_holds"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("case_decisions"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        var response = service.disburseSellerFunds(1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentEntity.STATUS_RELEASED);
        verify(orderRepository).save(order);
        verify(paymentRepository).save(payment);
    }

    @Test
    void disburseSellerFunds_sellerDeclinedNotFinal_notBlockedByGuard() throws Exception {
        setField(order, "workflowModel", "LEGACY_V14");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("120000.00"), PaymentEntity.METHOD_VNPAY, now);
        payment.markHeld("TX_123", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("money_holds"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("case_decisions"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        var response = service.disburseSellerFunds(1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void disburseSellerFunds_approveReturn_notBlockedByGuard() throws Exception {
        setField(order, "workflowModel", "LEGACY_V14");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("120000.00"), PaymentEntity.METHOD_VNPAY, now);
        payment.markHeld("TX_123", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("money_holds"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("case_decisions"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        var response = service.disburseSellerFunds(1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void disburseSellerFunds_noCase_bothLegacyAndUc83_behaviorUnchanged() throws Exception {
        setField(order, "workflowModel", "LEGACY_V14");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        PaymentEntity payment = new PaymentEntity(1L, new BigDecimal("120000.00"), PaymentEntity.METHOD_VNPAY, now);
        payment.markHeld("TX_123", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("money_holds"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("case_decisions"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        var response = service.disburseSellerFunds(1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
    }

    @Test
    void disburseSellerFunds_lockBeforeGuardQuery_verifiedByInOrder() throws Exception {
        setField(order, "workflowModel", "LEGACY_V14");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("money_holds"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("case_decisions"), eq(Boolean.class), eq(1L)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.disburseSellerFunds(1L))
                .isInstanceOf(IllegalStateException.class);

        InOrder inOrder = inOrder(orderRepository, entityManager, jdbcTemplate);
        inOrder.verify(orderRepository).findByIdForUpdate(1L);
        inOrder.verify(entityManager).refresh(order);
        inOrder.verify(jdbcTemplate).queryForObject(contains("case_decisions"), eq(Boolean.class), eq(1L));
    }

    @Test
    void disburseSellerFunds_queryThrowsDataAccessException_propagatesAndAborts() throws Exception {
        setField(order, "workflowModel", "UC83");
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        order.markShipped(now);
        order.markDelivered(now);
        setField(order, "validDeliveredAt", now.minus(Duration.ofDays(4)));

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(jdbcTemplate.queryForObject(eq("SELECT og_order_has_blocking_case(?)"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("money_holds"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("case_decisions"), eq(Boolean.class), eq(1L)))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("DB query failure"));

        assertThatThrownBy(() -> service.disburseSellerFunds(1L))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("DB query failure")
                .isNotInstanceOf(IllegalStateException.class);

        verify(paymentRepository, never()).findByOrderIdForUpdate(any());
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(jdbcTemplate, never()).update(contains("INSERT INTO settlement_operations"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void constructor_nullJdbcTemplate_throwsNullPointerException() {
        assertThatThrownBy(() -> new OrderManagementService(
                orderRepository,
                orderItemRepository,
                orderVoucherRepository,
                catalogCommerceFacade,
                paymentRepository,
                identityCommerceFacade,
                shipmentRepository,
                fulfillmentService,
                fulfillmentStateService,
                simulationGuard,
                transactionTemplate,
                clock,
                null,
                entityManager
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jdbcTemplate must not be null");
    }

    // =========================================================================
    // Task 3.3: Overdue Delivery Auto-Disbursement Worker Tests (UC71)
    // =========================================================================

    @Test
    @DisplayName("Task 3.3: reconcileOverdueDeliveries returns empty result when no overdue orders found")
    void reconcileOverdueDeliveries_noOverdueOrders_returnsEmpty() {
        when(orderRepository.findOverdueDeliveredOrderIds(any())).thenReturn(List.of());

        OrderManagementDtos.OverdueDeliveryBatchResult result = service.reconcileOverdueDeliveries();

        assertThat(result.totalScanned()).isZero();
        assertThat(result.processedOrderIds()).isEmpty();
        assertThat(result.skippedOrderIds()).isEmpty();
        assertThat(result.failedOrderIds()).isEmpty();
        verify(orderRepository).findOverdueDeliveredOrderIds(now.minus(Duration.ofDays(3)));
    }

    @Test
    @DisplayName("Task 3.3: reconcileOverdueDeliveries processes each eligible order through proxy")
    void reconcileOverdueDeliveries_multipleOrders_processesEach() {
        when(orderRepository.findOverdueDeliveredOrderIds(any())).thenReturn(List.of(101L, 102L));

        OrderManagementService mockProxy = mock(OrderManagementService.class);
        service.setSelf(mockProxy);

        OrderManagementDtos.DisbursementResponse resp101 = new OrderManagementDtos.DisbursementResponse(
                101L, "COMPLETED", List.of(), new BigDecimal("100000"), "Giải ngân thành công"
        );
        OrderManagementDtos.DisbursementResponse resp102 = new OrderManagementDtos.DisbursementResponse(
                102L, "COMPLETED", List.of(), new BigDecimal("200000"), "Giải ngân thành công"
        );
        when(mockProxy.disburseSellerFunds(101L)).thenReturn(resp101);
        when(mockProxy.disburseSellerFunds(102L)).thenReturn(resp102);

        OrderManagementDtos.OverdueDeliveryBatchResult result = service.reconcileOverdueDeliveries();

        assertThat(result.totalScanned()).isEqualTo(2);
        assertThat(result.processedOrderIds()).containsExactly(101L, 102L);
        assertThat(result.skippedOrderIds()).isEmpty();
        assertThat(result.failedOrderIds()).isEmpty();
        verify(mockProxy).disburseSellerFunds(101L);
        verify(mockProxy).disburseSellerFunds(102L);
    }

    @Test
    @DisplayName("Task 3.3: One failing order does not abort subsequent orders (Batch Error Isolation)")
    void reconcileOverdueDeliveries_errorIsolation_continuesProcessing() {
        when(orderRepository.findOverdueDeliveredOrderIds(any())).thenReturn(List.of(101L, 102L, 103L));

        OrderManagementService mockProxy = mock(OrderManagementService.class);
        service.setSelf(mockProxy);

        OrderManagementDtos.DisbursementResponse resp101 = new OrderManagementDtos.DisbursementResponse(
                101L, "COMPLETED", List.of(), new BigDecimal("100000"), "Giải ngân thành công"
        );
        OrderManagementDtos.DisbursementResponse resp103 = new OrderManagementDtos.DisbursementResponse(
                103L, "COMPLETED", List.of(), new BigDecimal("300000"), "Giải ngân thành công"
        );
        when(mockProxy.disburseSellerFunds(101L)).thenReturn(resp101);
        when(mockProxy.disburseSellerFunds(102L)).thenThrow(new RuntimeException("Transient DB lock timeout"));
        when(mockProxy.disburseSellerFunds(103L)).thenReturn(resp103);

        OrderManagementDtos.OverdueDeliveryBatchResult result = service.reconcileOverdueDeliveries();

        assertThat(result.totalScanned()).isEqualTo(3);
        assertThat(result.processedOrderIds()).containsExactly(101L, 103L);
        assertThat(result.failedOrderIds()).containsExactly(102L);
        assertThat(result.skippedOrderIds()).isEmpty();
        verify(mockProxy).disburseSellerFunds(101L);
        verify(mockProxy).disburseSellerFunds(102L);
        verify(mockProxy).disburseSellerFunds(103L);
    }

    @Test
    @DisplayName("Task 3.3: Business guard rejection (IllegalStateException) is categorized as skipped")
    void reconcileOverdueDeliveries_businessGuardRejection_skipped() {
        when(orderRepository.findOverdueDeliveredOrderIds(any())).thenReturn(List.of(101L, 102L));

        OrderManagementService mockProxy = mock(OrderManagementService.class);
        service.setSelf(mockProxy);

        // Order 101 has blocking case (Guard 3)
        when(mockProxy.disburseSellerFunds(101L)).thenThrow(
                new IllegalStateException("Đơn hàng đang có hồ sơ khiếu nại/trả hàng chưa đóng, không thể giải ngân")
        );
        // Order 102 has refund decision (Guard 5)
        when(mockProxy.disburseSellerFunds(102L)).thenThrow(
                new IllegalStateException("Đơn hàng đã có quyết định hoàn tiền hoặc miễn hoàn tiền hàng")
        );

        OrderManagementDtos.OverdueDeliveryBatchResult result = service.reconcileOverdueDeliveries();

        assertThat(result.totalScanned()).isEqualTo(2);
        assertThat(result.processedOrderIds()).isEmpty();
        assertThat(result.skippedOrderIds()).containsExactly(101L, 102L);
        assertThat(result.failedOrderIds()).isEmpty();
    }

    @Test
    @DisplayName("Task 3.3: Idempotent replay response is categorized as skipped")
    void reconcileOverdueDeliveries_idempotentReplay_skipped() {
        when(orderRepository.findOverdueDeliveredOrderIds(any())).thenReturn(List.of(101L));

        OrderManagementService mockProxy = mock(OrderManagementService.class);
        service.setSelf(mockProxy);

        OrderManagementDtos.DisbursementResponse idempotentResp = new OrderManagementDtos.DisbursementResponse(
                101L, "COMPLETED", List.of(), new BigDecimal("100000"), "Đơn hàng đã được giải ngân và hoàn tất trước đó."
        );
        when(mockProxy.disburseSellerFunds(101L)).thenReturn(idempotentResp);

        OrderManagementDtos.OverdueDeliveryBatchResult result = service.reconcileOverdueDeliveries();

        assertThat(result.totalScanned()).isEqualTo(1);
        assertThat(result.processedOrderIds()).isEmpty();
        assertThat(result.skippedOrderIds()).containsExactly(101L);
        assertThat(result.failedOrderIds()).isEmpty();
    }

    private static void setId(Object target, Long id) throws Exception {
        Field idField = target.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
