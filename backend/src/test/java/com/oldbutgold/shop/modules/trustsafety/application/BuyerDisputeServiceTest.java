package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.application.CommerceDisputeFacade;
import com.oldbutgold.shop.modules.commerce.application.FulfillmentSimulationGuard;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.*;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BuyerDisputeServiceTest {

    private DisputeRepository disputeRepository;
    private OrderRepository orderRepository;
    private CommerceDisputeFacade commerceDisputeFacade;
    private BuyerDisputeService service;

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        disputeRepository = mock(DisputeRepository.class);
        orderRepository = mock(OrderRepository.class);
        commerceDisputeFacade = mock(CommerceDisputeFacade.class);
        service = new BuyerDisputeService(disputeRepository, orderRepository, commerceDisputeFacade);
    }

    private OrderEntity createSampleOrder(long orderId, long buyerId, long sellerId, String status) {
        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), buyerId, sellerId, 1L,
                "Nguyễn Văn A", "0901234567",
                "Hồ Chí Minh", "Quận 1", "Bến Nghé", "12 Lê Duẩn",
                202, 1444, "20311",
                new BigDecimal("200000"), new BigDecimal("25000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "PLATFORM", new BigDecimal("225000"),
                NOW.plusSeconds(3600), NOW
        );
        try {
            var idField = OrderEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderId);

            var statusField = OrderEntity.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(order, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return order;
    }

    private DisputeDetailDto createSampleDispute(long disputeId, long orderId, String status) {
        return new DisputeDetailDto(
                disputeId,
                orderId,
                "ORDER-" + orderId,
                201L,
                "Buyer Name",
                "buyer@example.com",
                301L,
                "Seller Shop",
                "seller@example.com",
                "Đồng hồ Vintage",
                new BigDecimal("200000"),
                "Hàng vỡ",
                "Mặt kính vỡ vụn",
                List.of("https://example.com/unboxing.mp4"),
                "https://example.com/unboxing.mp4",
                null,
                List.of(),
                NOW.plusSeconds(86400),
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW
        );
    }

    @Test
    @DisplayName("createDispute: Người mua tạo khiếu nại thành công cho đơn đã giao")
    void testCreateDisputeSuccess() {
        long orderId = 100L;
        long buyerId = 201L;
        long sellerId = 301L;

        OrderEntity order = createSampleOrder(orderId, buyerId, sellerId, OrderEntity.STATUS_DELIVERED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(disputeRepository.findDisputeByOrderId(orderId)).thenReturn(Optional.empty());
        when(disputeRepository.createDispute(eq(orderId), eq(buyerId), eq(sellerId), any(), any(), any(), any()))
                .thenReturn(1L);

        DisputeDetailDto created = createSampleDispute(1L, orderId, "WAITING_SELLER_RESPONSE");
        when(disputeRepository.findDisputeById(1L)).thenReturn(Optional.of(created));

        CreateDisputeRequest request = new CreateDisputeRequest(
                orderId,
                "Hàng vỡ",
                "Mặt kính vỡ vụn",
                List.of("https://example.com/unboxing.mp4"),
                "https://example.com/unboxing.mp4"
        );

        DisputeDetailDto result = service.createDispute(buyerId, request);

        assertThat(result.disputeId()).isEqualTo(1L);
        verify(commerceDisputeFacade).markOrderDisputed(orderId);
    }

    @Test
    @DisplayName("createDispute: Báo lỗi nếu người yêu cầu không phải người mua của đơn")
    void testCreateDisputeUnauthorized() {
        long orderId = 100L;
        OrderEntity order = createSampleOrder(orderId, 201L, 301L, OrderEntity.STATUS_DELIVERED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        CreateDisputeRequest request = new CreateDisputeRequest(
                orderId, "Lỗi", "Mô tả", List.of(), null
        );

        assertThatThrownBy(() -> service.createDispute(999L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền yêu cầu trả hàng / khiếu nại đơn này");
    }

    @Test
    @DisplayName("submitSellerResponse: Người bán phản hồi thành công")
    void testSubmitSellerResponseSuccess() {
        long orderId = 100L;
        long sellerId = 301L;

        OrderEntity order = createSampleOrder(orderId, 201L, sellerId, OrderEntity.STATUS_DISPUTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        DisputeDetailDto existing = createSampleDispute(1L, orderId, "WAITING_SELLER_RESPONSE");
        when(disputeRepository.findDisputeByOrderId(orderId)).thenReturn(Optional.of(existing));

        DisputeDetailDto updated = createSampleDispute(1L, orderId, "WAITING_KTV_REVIEW");
        when(disputeRepository.findDisputeById(1L)).thenReturn(Optional.of(updated));

        SellerDisputeResponseRequest request = new SellerDisputeResponseRequest(
                "Tôi đã bọc xốp 3 lớp trước khi gửi",
                List.of("https://example.com/pack1.jpg")
        );

        DisputeDetailDto result = service.submitSellerResponse(sellerId, orderId, request);

        assertThat(result.status()).isEqualTo("WAITING_KTV_REVIEW");
        verify(disputeRepository).saveSellerResponse(1L, "Tôi đã bọc xốp 3 lớp trước khi gửi", List.of("https://example.com/pack1.jpg"));
    }

    private DisputeDetailDto createReturnDispute(
            long disputeId,
            long orderId,
            String status,
            String returnTrackingCode,
            String returnShipmentStatus,
            Instant handedOverAt,
            Instant deliveredAt
    ) {
        return new DisputeDetailDto(
                disputeId,
                orderId,
                "ORDER-" + orderId,
                201L,
                "Buyer Name",
                "buyer@example.com",
                301L,
                "Seller Shop",
                "seller@example.com",
                "Đồng hồ Vintage",
                new BigDecimal("200000"),
                "Hàng vỡ",
                "Mặt kính vỡ vụn",
                List.of("https://example.com/unboxing.mp4"),
                "https://example.com/unboxing.mp4",
                null,
                List.of(),
                NOW.plusSeconds(86400),
                status,
                "RETURN_REFUND",
                "KTV chấp thuận trả hàng",
                new BigDecimal("225000"),
                0,
                1L,
                NOW,
                NOW,
                NOW,
                returnTrackingCode,
                "GHN",
                returnShipmentStatus,
                handedOverAt,
                deliveredAt,
                "Kho Người bán",
                "0909888777",
                "123 Lê Lợi, Q1, TP.HCM"
        );
    }

    @Test
    @DisplayName("submitReturnShipment: Khởi tạo vận đơn hoàn trả ở trạng thái RETURN_CREATED (không nhảy sang RETURN_SHIPPING)")
    void testSubmitReturnShipmentInitializesReturnCreated() {
        long orderId = 100L;
        long buyerId = 201L;
        long sellerId = 301L;

        OrderEntity order = createSampleOrder(orderId, buyerId, sellerId, OrderEntity.STATUS_DISPUTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        DisputeDetailDto approved = createReturnDispute(1L, orderId, "RETURN_APPROVED", null, null, null, null);
        when(disputeRepository.findDisputeByOrderId(orderId)).thenReturn(Optional.of(approved));

        DisputeDetailDto created = createReturnDispute(1L, orderId, "RETURN_APPROVED", "GHN-RET-100-ABC123", "RETURN_CREATED", null, null);
        when(disputeRepository.findDisputeById(1L)).thenReturn(Optional.of(created));

        DisputeDetailDto result = service.submitReturnShipment(buyerId, orderId, new ReturnShipmentRequest("GHN", "GHN-RET-100-ABC123"));

        assertThat(result.returnShipmentStatus()).isEqualTo("RETURN_CREATED");
        assertThat(result.returnTrackingCode()).isEqualTo("GHN-RET-100-ABC123");
        verify(disputeRepository).saveReturnShipment(1L, "GHN", "GHN-RET-100-ABC123", "RETURN_CREATED");
        verify(disputeRepository).recordNotification(eq(buyerId), eq("DISPUTE_BUYER"), anyString(), contains("GHN-RET-100-ABC123"));
        verify(commerceDisputeFacade, never()).resolveDisputeRefund(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("submitReturnShipment: Idempotent nếu đã có mã vận đơn hoàn trả")
    void testSubmitReturnShipmentIdempotentWhenAlreadyCreated() {
        long orderId = 100L;
        OrderEntity order = createSampleOrder(orderId, 201L, 301L, OrderEntity.STATUS_DISPUTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        DisputeDetailDto existing = createReturnDispute(1L, orderId, "RETURN_APPROVED", "GHN-RET-100-EXIST", "RETURN_CREATED", null, null);
        when(disputeRepository.findDisputeByOrderId(orderId)).thenReturn(Optional.of(existing));

        DisputeDetailDto result = service.submitReturnShipment(201L, orderId, new ReturnShipmentRequest("GHN", null));

        assertThat(result.returnTrackingCode()).isEqualTo("GHN-RET-100-EXIST");
        verify(disputeRepository, never()).saveReturnShipment(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("confirmReturnHandover: Người mua xác nhận bàn giao -> chuyển RETURN_SHIPPING và gửi thông báo cho Người bán")
    void testConfirmReturnHandoverSuccess() {
        long orderId = 100L;
        long buyerId = 201L;
        long sellerId = 301L;

        OrderEntity order = createSampleOrder(orderId, buyerId, sellerId, OrderEntity.STATUS_DISPUTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        DisputeDetailDto created = createReturnDispute(1L, orderId, "RETURN_APPROVED", "GHN-RET-100-XYZ", "RETURN_CREATED", null, null);
        when(disputeRepository.findDisputeByOrderId(orderId)).thenReturn(Optional.of(created));
        when(disputeRepository.markReturnHandedOver(eq(1L), eq(buyerId), any(Instant.class))).thenReturn(1);

        DisputeDetailDto shipping = createReturnDispute(1L, orderId, "RETURN_APPROVED", "GHN-RET-100-XYZ", "RETURN_SHIPPING", NOW, null);
        when(disputeRepository.findDisputeById(1L)).thenReturn(Optional.of(shipping));

        DisputeDetailDto result = service.confirmReturnHandover(buyerId, orderId);

        assertThat(result.returnShipmentStatus()).isEqualTo("RETURN_SHIPPING");
        verify(disputeRepository).markReturnHandedOver(eq(1L), eq(buyerId), any(Instant.class));
        verify(disputeRepository).recordNotification(
                eq(sellerId),
                eq("DISPUTE_SELLER"),
                anyString(),
                eq("Người mua đã bàn giao kiện hàng hoàn trả (Mã: GHN-RET-100-XYZ) cho GHN. Đơn hàng đang trên đường giao về kho của bạn!")
        );
        verify(commerceDisputeFacade, never()).resolveDisputeRefund(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("confirmReturnHandover: Chặn trùng lặp hoặc gọi sai trạng thái")
    void testConfirmReturnHandoverRejectsDuplicateAndWrongActor() {
        long orderId = 100L;
        OrderEntity order = createSampleOrder(orderId, 201L, 301L, OrderEntity.STATUS_DISPUTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // Seller cannot confirm buyer handover
        assertThatThrownBy(() -> service.confirmReturnHandover(301L, orderId))
                .isInstanceOf(AccessDeniedException.class);

        // Already RETURN_SHIPPING -> reject
        DisputeDetailDto alreadyShipping = createReturnDispute(1L, orderId, "RETURN_APPROVED", "GHN-RET-100-XYZ", "RETURN_SHIPPING", NOW, null);
        when(disputeRepository.findDisputeByOrderId(orderId)).thenReturn(Optional.of(alreadyShipping));
        assertThatThrownBy(() -> service.confirmReturnHandover(201L, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETURN_CREATED");

        // Concurrent duplicate handover (atomic update returns 0)
        DisputeDetailDto created = createReturnDispute(1L, orderId, "RETURN_APPROVED", "GHN-RET-100-XYZ", "RETURN_CREATED", null, null);
        when(disputeRepository.findDisputeByOrderId(orderId)).thenReturn(Optional.of(created));
        when(disputeRepository.markReturnHandedOver(eq(1L), eq(201L), any(Instant.class))).thenReturn(0);
        assertThatThrownBy(() -> service.confirmReturnHandover(201L, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã được xác nhận bàn giao");
    }

    @Test
    @DisplayName("confirmReturnDelivered: Chặn Buyer/Seller tự mô phỏng và fail-closed khi thiếu guard; Admin hợp lệ chuyển RETURN_SHIPPING -> RETURN_DELIVERED và KHÔNG tự hoàn tiền")
    void testConfirmReturnDeliveredDoesNotAutoRefund() {
        long orderId = 100L;
        OrderEntity order = createSampleOrder(orderId, 201L, 301L, OrderEntity.STATUS_DISPUTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        DisputeDetailDto shipping = createReturnDispute(1L, orderId, "RETURN_APPROVED", "GHN-RET-100-XYZ", "RETURN_SHIPPING", NOW, null);
        when(disputeRepository.findDisputeByOrderId(orderId)).thenReturn(Optional.of(shipping));
        when(disputeRepository.markReturnDelivered(eq(1L), any(Instant.class))).thenReturn(1);

        DisputeDetailDto delivered = createReturnDispute(1L, orderId, "RETURN_APPROVED", "GHN-RET-100-XYZ", "RETURN_DELIVERED", NOW, NOW);
        when(disputeRepository.findDisputeById(1L)).thenReturn(Optional.of(delivered));

        // 1. Fail-closed when simulationGuard is absent
        assertThatThrownBy(() -> service.confirmReturnDelivered(999L, orderId, true))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        // 2. Fail-closed when flag is disabled or PROD
        service.setSimulationGuard(new FulfillmentSimulationGuard("PROD", true));
        assertThatThrownBy(() -> service.confirmReturnDelivered(999L, orderId, true))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        // 3. Buyer & Seller cannot self-simulate even when flag is enabled
        service.setSimulationGuard(new FulfillmentSimulationGuard("DEV", true));
        assertThatThrownBy(() -> service.confirmReturnDelivered(201L, orderId, false))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.confirmReturnDelivered(301L, orderId, false))
                .isInstanceOf(AccessDeniedException.class);

        // 4. Admin succeeds when DEV + simulationEnabled = true
        DisputeDetailDto result = service.confirmReturnDelivered(999L, orderId, true);

        assertThat(result.returnShipmentStatus()).isEqualTo("RETURN_DELIVERED");
        assertThat(result.status()).isEqualTo("RETURN_APPROVED");
        verify(disputeRepository).markReturnDelivered(eq(1L), any(Instant.class));
        // Critical invariant: RETURN_DELIVERED MUST NOT automatically trigger refund settlement
        verify(commerceDisputeFacade, never()).resolveDisputeRefund(anyLong(), any(), anyString());
        verify(disputeRepository, never()).markReturnDeliveredAndRefunded(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("confirmReturnReceivedAndRefund: Chỉ Người bán được xác nhận và bắt buộc vận đơn ở trạng thái RETURN_DELIVERED")
    void testConfirmReturnReceivedAndRefundRequiresSellerAndReturnDelivered() {
        long orderId = 100L;
        OrderEntity order = createSampleOrder(orderId, 201L, 301L, OrderEntity.STATUS_DISPUTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // Buyer cannot trigger refund settlement
        assertThatThrownBy(() -> service.confirmReturnReceivedAndRefund(201L, orderId))
                .isInstanceOf(AccessDeniedException.class);

        // Seller cannot trigger refund while still RETURN_SHIPPING
        DisputeDetailDto shipping = createReturnDispute(1L, orderId, "RETURN_APPROVED", "GHN-RET-100-XYZ", "RETURN_SHIPPING", NOW, null);
        when(disputeRepository.findDisputeByOrderId(orderId)).thenReturn(Optional.of(shipping));
        assertThatThrownBy(() -> service.confirmReturnReceivedAndRefund(301L, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETURN_DELIVERED");

        // Seller succeeds once RETURN_DELIVERED (legacy row stored 225000 incl. shipping -> capped to refundable goods 200000)
        DisputeDetailDto delivered = createReturnDispute(1L, orderId, "RETURN_APPROVED", "GHN-RET-100-XYZ", "RETURN_DELIVERED", NOW, NOW);
        when(disputeRepository.findDisputeByOrderId(orderId)).thenReturn(Optional.of(delivered));
        DisputeDetailDto refunded = createReturnDispute(1L, orderId, "RESOLVED_REFUND", "GHN-RET-100-XYZ", "RETURN_DELIVERED", NOW, NOW);
        when(disputeRepository.findDisputeById(1L)).thenReturn(Optional.of(refunded));

        DisputeDetailDto result = service.confirmReturnReceivedAndRefund(301L, orderId);

        assertThat(result.status()).isEqualTo("RESOLVED_REFUND");
        verify(commerceDisputeFacade).resolveDisputeRefund(eq(orderId), eq(new BigDecimal("200000")), contains("GHN-RET-100-XYZ"));
        verify(disputeRepository).markReturnDeliveredAndRefunded(1L, 301L, new BigDecimal("200000"));
    }

    @Test
    @DisplayName("confirmReturnReceivedAndRefund: Khấu trừ voucher + xu trên tiền hàng, bỏ phí ship và cho phép hoàn 0 đồng hợp lệ")
    void testRefundCalculationExcludesShippingAppliesDiscountsAndAllowsZero() throws Exception {
        // Case A: subtotal 200k, shipping 30k, voucher 50k, points 30k -> refundable goods = 120k (legacy stored 230k capped to 120k)
        long orderA = 101L;
        OrderEntity discounted = new OrderEntity(
                UUID.randomUUID(), 201L, 301L, 1L,
                "Nguyễn Văn A", "0901234567",
                "Hồ Chí Minh", "Quận 1", "Bến Nghé", "12 Lê Duẩn",
                202, 1444, "20311",
                new BigDecimal("200000"), new BigDecimal("30000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("120000"),
                new BigDecimal("50000"), BigDecimal.ZERO, "PLATFORM", new BigDecimal("150000"),
                NOW.plusSeconds(3600), NOW
        );
        var idField = OrderEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(discounted, orderA);
        var ptsField = OrderEntity.class.getDeclaredField("pointsDiscountAmount");
        ptsField.setAccessible(true);
        ptsField.set(discounted, new BigDecimal("30000"));

        when(orderRepository.findById(orderA)).thenReturn(Optional.of(discounted));
        DisputeDetailDto deliveredA = new DisputeDetailDto(
                10L, orderA, "ORDER-" + orderA, 201L, "B", "b@x.com", 301L, "S", "s@x.com",
                "Item", new BigDecimal("120000"), "R", "D", List.of(), null, null, List.of(),
                NOW, "RETURN_APPROVED", "RETURN_REFUND", "OK", new BigDecimal("230000"), 0,
                1L, NOW, NOW, NOW, "GHN-A", "GHN", "RETURN_DELIVERED", NOW, NOW, "S", "090", "Addr"
        );
        when(disputeRepository.findDisputeByOrderId(orderA)).thenReturn(Optional.of(deliveredA));
        when(disputeRepository.findDisputeById(10L)).thenReturn(Optional.of(deliveredA));

        service.confirmReturnReceivedAndRefund(301L, orderA);
        verify(commerceDisputeFacade).resolveDisputeRefund(eq(orderA), eq(new BigDecimal("120000")), contains("GHN-A"));
        verify(disputeRepository).markReturnDeliveredAndRefunded(10L, 301L, new BigDecimal("120000"));

        // Case B: subtotal 100k, shipping 25k, voucher 60k, points 40k -> valid 0 VND refund (must NOT fall back to 25k shipping!)
        long orderB = 102L;
        OrderEntity zeroGoods = new OrderEntity(
                UUID.randomUUID(), 201L, 301L, 1L,
                "Nguyễn Văn A", "0901234567",
                "Hồ Chí Minh", "Quận 1", "Bến Nghé", "12 Lê Duẩn",
                202, 1444, "20311",
                new BigDecimal("100000"), new BigDecimal("25000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("60000"), BigDecimal.ZERO, "PLATFORM", new BigDecimal("25000"),
                NOW.plusSeconds(3600), NOW
        );
        idField.set(zeroGoods, orderB);
        ptsField.set(zeroGoods, new BigDecimal("40000"));

        when(orderRepository.findById(orderB)).thenReturn(Optional.of(zeroGoods));
        DisputeDetailDto deliveredB = createReturnDispute(11L, orderB, "RETURN_APPROVED", "GHN-B", "RETURN_DELIVERED", NOW, NOW);
        // Simulate refundAmount == 0 stored by KTV decision
        DisputeDetailDto zeroStored = new DisputeDetailDto(
                deliveredB.disputeId(), deliveredB.orderId(), deliveredB.orderCode(),
                deliveredB.buyerId(), deliveredB.buyerName(), deliveredB.buyerEmail(),
                deliveredB.sellerId(), deliveredB.sellerName(), deliveredB.sellerEmail(),
                deliveredB.productTitle(), BigDecimal.ZERO, deliveredB.reason(), deliveredB.description(),
                deliveredB.buyerEvidenceUrls(), deliveredB.videoUnboxingUrl(), deliveredB.sellerResponse(),
                deliveredB.sellerEvidenceUrls(), deliveredB.sellerResponseDueAt(), deliveredB.status(),
                deliveredB.verdictAction(), deliveredB.verdictNotes(), BigDecimal.ZERO, deliveredB.penaltyPoints(),
                deliveredB.resolvedBy(), deliveredB.resolvedAt(), deliveredB.createdAt(), deliveredB.updatedAt(),
                deliveredB.returnTrackingCode(), deliveredB.returnCarrier(), deliveredB.returnShipmentStatus(),
                deliveredB.returnHandedOverAt(), deliveredB.returnDeliveredAt(),
                deliveredB.returnRecipientName(), deliveredB.returnRecipientPhone(), deliveredB.returnRecipientAddress()
        );
        when(disputeRepository.findDisputeByOrderId(orderB)).thenReturn(Optional.of(zeroStored));
        when(disputeRepository.findDisputeById(11L)).thenReturn(Optional.of(zeroStored));

        service.confirmReturnReceivedAndRefund(301L, orderB);
        verify(commerceDisputeFacade).resolveDisputeRefund(eq(orderB), eq(BigDecimal.ZERO), contains("GHN-B"));
        verify(disputeRepository).markReturnDeliveredAndRefunded(11L, 301L, BigDecimal.ZERO);
    }
}
