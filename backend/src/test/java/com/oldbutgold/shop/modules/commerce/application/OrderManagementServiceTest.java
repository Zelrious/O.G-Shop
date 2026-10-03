package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderManagementServiceTest {
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private OrderVoucherRepository orderVoucherRepository;
    private CatalogCommerceFacade catalogCommerceFacade;
    private PaymentRepository paymentRepository;
    private IdentityCommerceFacade identityCommerceFacade;
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

        now = Instant.parse("2026-09-21T10:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));

        service = new OrderManagementService(
                orderRepository,
                orderItemRepository,
                orderVoucherRepository,
                catalogCommerceFacade,
                paymentRepository,
                identityCommerceFacade,
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

    private static void setId(Object target, Long id) throws Exception {
        Field idField = target.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }
}
