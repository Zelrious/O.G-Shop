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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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
                clock
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

    private static void setId(Object target, Long id) throws Exception {
        Field idField = target.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }
}
