package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.application.CommerceDisputeFacade;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.*;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class KtvDisputeServiceTest {

    private DisputeRepository disputeRepository;
    private CommerceDisputeFacade commerceDisputeFacade;
    private KtvDisputeService service;

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        disputeRepository = mock(DisputeRepository.class);
        commerceDisputeFacade = mock(CommerceDisputeFacade.class);
        service = new KtvDisputeService(disputeRepository, commerceDisputeFacade);
    }

    private DisputeDetailDto createSampleDispute(long disputeId, String status) {
        return new DisputeDetailDto(
                disputeId,
                1001L,
                "ORDER-1001",
                201L,
                "Buyer Name",
                "buyer@example.com",
                301L,
                "Seller Shop",
                "seller@example.com",
                "Đồng hồ Vintage",
                new BigDecimal("500000"),
                "Hàng bị trầy xước nặng không đúng mô tả",
                "Tôi nhận hàng thấy mặt kính bị vỡ",
                List.of("https://example.com/unboxing.mp4"),
                "https://example.com/unboxing.mp4",
                "Hàng lúc tôi gửi đi nguyên vẹn",
                List.of("https://example.com/packaging1.jpg", "https://example.com/packaging2.jpg"),
                NOW.plusSeconds(86400),
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                NOW.minusSeconds(86400),
                NOW.minusSeconds(43200)
        );
    }

    @Test
    @DisplayName("submitDecision: RETURN_APPROVED chuyển đơn sang chờ trả hàng và ghi nhận phạt người bán nếu có điểm trừ")
    void testSubmitDecisionReturnApproved() {
        long disputeId = 1L;
        long ktvId = 99L;
        DisputeDetailDto dispute = createSampleDispute(disputeId, "WAITING_KTV_REVIEW");

        when(disputeRepository.findDisputeById(disputeId)).thenReturn(Optional.of(dispute));

        DisputeDecisionRequest request = new DisputeDecisionRequest(
                "RETURN_APPROVED",
                "Chấp thuận trả hàng theo video unboxing",
                7
        );

        DisputeDecisionResponse response = service.submitDecision(disputeId, ktvId, request);

        assertThat(response.disputeId()).isEqualTo(disputeId);
        assertThat(response.verdictAction()).isEqualTo("RETURN_APPROVED");
        assertThat(response.status()).isEqualTo("RETURN_APPROVED");

        verify(commerceDisputeFacade).resolveDisputeReturnApproved(1001L, "Chấp thuận trả hàng theo video unboxing");
        verify(disputeRepository).recordPenalty(eq(301L), eq(7), contains("KTV phán quyết chấp thuận trả hàng"), eq(ktvId));
        verify(disputeRepository).applyDecision(disputeId, ktvId, "RETURN_APPROVED", new BigDecimal("500000"), "Chấp thuận trả hàng theo video unboxing", 7);
    }

    @Test
    @DisplayName("submitDecision: RESOLVED_REFUND thực hiện hoàn tiền 100% cho người mua và phạt người bán")
    void testSubmitDecisionResolvedRefund() {
        long disputeId = 2L;
        long ktvId = 99L;
        DisputeDetailDto dispute = createSampleDispute(disputeId, "SELLER_REJECTED");

        when(disputeRepository.findDisputeById(disputeId)).thenReturn(Optional.of(dispute));

        DisputeDecisionRequest request = new DisputeDecisionRequest(
                "RESOLVED_REFUND",
                "Hàng giả/nhái nghiêm trọng, hoàn tiền ngay lập tức",
                15
        );

        DisputeDecisionResponse response = service.submitDecision(disputeId, ktvId, request);

        assertThat(response.verdictAction()).isEqualTo("RESOLVED_REFUND");
        verify(commerceDisputeFacade).resolveDisputeRefund(1001L, new BigDecimal("500000"), "Hàng giả/nhái nghiêm trọng, hoàn tiền ngay lập tức");
        verify(disputeRepository).recordPenalty(eq(301L), eq(15), contains("KTV phán quyết hoàn tiền do lỗi người bán"), eq(ktvId));
        verify(disputeRepository).applyDecision(disputeId, ktvId, "RESOLVED_REFUND", new BigDecimal("500000"), "Hàng giả/nhái nghiêm trọng, hoàn tiền ngay lập tức", 15);
    }

    @Test
    @DisplayName("submitDecision: RESOLVED_RELEASE bác khiếu nại, giải ngân cho người bán và phạt người mua nếu khiếu nại ảo")
    void testSubmitDecisionResolvedRelease() {
        long disputeId = 3L;
        long ktvId = 99L;
        DisputeDetailDto dispute = createSampleDispute(disputeId, "WAITING_KTV_REVIEW");

        when(disputeRepository.findDisputeById(disputeId)).thenReturn(Optional.of(dispute));

        DisputeDecisionRequest request = new DisputeDecisionRequest(
                "RESOLVED_RELEASE",
                "Bác khiếu nại do người mua tráo hàng, video của seller chứng minh đóng gói nguyên seal",
                5
        );

        DisputeDecisionResponse response = service.submitDecision(disputeId, ktvId, request);

        assertThat(response.verdictAction()).isEqualTo("RESOLVED_RELEASE");
        verify(commerceDisputeFacade).resolveDisputeRelease(1001L, "Bác khiếu nại do người mua tráo hàng, video của seller chứng minh đóng gói nguyên seal");
        verify(disputeRepository).recordPenalty(eq(201L), eq(5), contains("KTV phán quyết bác khiếu nại do người mua khiếu nại sai/bom hàng"), eq(ktvId));
        verify(disputeRepository).applyDecision(disputeId, ktvId, "RESOLVED_RELEASE", BigDecimal.ZERO, "Bác khiếu nại do người mua tráo hàng, video của seller chứng minh đóng gói nguyên seal", 5);
    }

    @Test
    @DisplayName("submitDecision: Quăng ngoại lệ nếu phán quyết không hợp lệ")
    void testSubmitDecisionInvalidVerdictThrows() {
        long disputeId = 4L;
        DisputeDecisionRequest request = new DisputeDecisionRequest("UNKNOWN_VERDICT", "test", 0);

        assertThatThrownBy(() -> service.submitDecision(disputeId, 99L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quyết định phán quyết không hợp lệ");
    }

    @Test
    @DisplayName("submitDecision: Quăng ngoại lệ nếu tranh chấp đã kết thúc")
    void testSubmitDecisionAlreadyResolvedThrows() {
        long disputeId = 5L;
        DisputeDetailDto dispute = createSampleDispute(disputeId, "RESOLVED_REFUND");
        when(disputeRepository.findDisputeById(disputeId)).thenReturn(Optional.of(dispute));

        DisputeDecisionRequest request = new DisputeDecisionRequest("RESOLVED_RELEASE", "notes", 0);

        assertThatThrownBy(() -> service.submitDecision(disputeId, 99L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã có phán quyết kết thúc trước đó");
    }

    @Test
    @DisplayName("submitDecision: Đường đi chính tính tiền hoàn từ Order (bỏ phí ship, trừ voucher & xu, cho phép 0 đồng)")
    void testPrimaryDecisionUsesAuthoritativeOrderRefundableGoodsRule() throws Exception {
        OrderRepository orderRepo = mock(OrderRepository.class);
        service.setOrderRepository(orderRepo);

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), 201L, 301L, 1L,
                "Nguyễn Văn A", "0901234567",
                "Hồ Chí Minh", "Quận 1", "Bến Nghé", "12 Lê Duẩn",
                202, 1444, "20311",
                new BigDecimal("500000"), new BigDecimal("35000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("400000"),
                new BigDecimal("100000"), BigDecimal.ZERO, "PLATFORM", new BigDecimal("410000"),
                NOW.plusSeconds(3600), NOW
        );
        var ptsField = OrderEntity.class.getDeclaredField("pointsDiscountAmount");
        ptsField.setAccessible(true);
        ptsField.set(order, new BigDecimal("25000"));
        when(orderRepo.findById(1001L)).thenReturn(Optional.of(order));

        DisputeDetailDto dispute = createSampleDispute(10L, "WAITING_KTV_REVIEW");
        when(disputeRepository.findDisputeById(10L)).thenReturn(Optional.of(dispute));

        service.submitDecision(10L, 99L, new DisputeDecisionRequest("RESOLVED_REFUND", "Đồng ý hoàn tiền hàng", 5));

        // 500000 - 100000 voucher - 25000 points = 375000 (shipping 35000 excluded)
        verify(commerceDisputeFacade).resolveDisputeRefund(1001L, new BigDecimal("375000"), "Đồng ý hoàn tiền hàng");
        verify(disputeRepository).applyDecision(10L, 99L, "RESOLVED_REFUND", new BigDecimal("375000"), "Đồng ý hoàn tiền hàng", 5);

        // Zero-VND valid refund when voucher + points >= subtotal
        ptsField.set(order, new BigDecimal("450000"));
        DisputeDetailDto dispute2 = createSampleDispute(11L, "WAITING_KTV_REVIEW");
        when(disputeRepository.findDisputeById(11L)).thenReturn(Optional.of(dispute2));
        service.submitDecision(11L, 99L, new DisputeDecisionRequest("RETURN_APPROVED", "Duyệt trả 0đ", 0));
        verify(disputeRepository).applyDecision(11L, 99L, "RETURN_APPROVED", BigDecimal.ZERO, "Duyệt trả 0đ", 0);
    }
}
