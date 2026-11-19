package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.application.CommerceDisputeFacade;
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
}
