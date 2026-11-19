package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.api.ReviewDtos.*;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderReviewServiceTest {

    private DisputeRepository disputeRepository;
    private OrderRepository orderRepository;
    private JdbcTemplate jdbc;
    private OrderReviewService service;

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        disputeRepository = mock(DisputeRepository.class);
        orderRepository = mock(OrderRepository.class);
        jdbc = mock(JdbcTemplate.class);
        service = new OrderReviewService(disputeRepository, orderRepository, jdbc);
    }

    private OrderEntity createDeliveredOrder(long orderId, long buyerId, long sellerId) {
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
            statusField.set(order, OrderEntity.STATUS_DELIVERED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return order;
    }

    @Test
    @DisplayName("submitReview: Người mua đánh giá 5 sao cho đơn hàng đã giao thành công")
    void testSubmitReviewBuyer5Stars() {
        long orderId = 100L;
        long buyerId = 200L;
        long sellerId = 300L;

        OrderEntity order = createDeliveredOrder(orderId, buyerId, sellerId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        ReviewResponse savedReview = new ReviewResponse(
                1L, orderId, buyerId, "buyer", sellerId, "seller", 5, "Sản phẩm tuyệt vời", NOW, NOW
        );
        when(disputeRepository.findReviewsForOrder(orderId)).thenReturn(List.of(savedReview));

        CreateReviewRequest request = new CreateReviewRequest(orderId, 5, "Sản phẩm tuyệt vời");
        ReviewResponse result = service.submitReview(buyerId, request);

        assertThat(result.rating()).isEqualTo(5);
        assertThat(result.comment()).isEqualTo("Sản phẩm tuyệt vời");

        verify(disputeRepository).saveReview(orderId, buyerId, sellerId, 5, "Sản phẩm tuyệt vời");
    }

    @Test
    @DisplayName("submitReview: Báo lỗi nếu rating ngoài khoảng 1-5")
    void testSubmitReviewInvalidRating() {
        CreateReviewRequest request = new CreateReviewRequest(100L, 6, "Quá 5 sao");

        assertThatThrownBy(() -> service.submitReview(200L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Điểm đánh giá phải từ 1 đến 5 sao");
    }

    @Test
    @DisplayName("submitReview: Báo lỗi nếu người dùng không thuộc đơn hàng")
    void testSubmitReviewUnauthorizedUser() {
        long orderId = 100L;
        OrderEntity order = createDeliveredOrder(orderId, 200L, 300L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        CreateReviewRequest request = new CreateReviewRequest(orderId, 5, "Hàng tốt");

        assertThatThrownBy(() -> service.submitReview(999L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không phải thành viên của đơn hàng");
    }

    @Test
    @DisplayName("submitReview: Báo lỗi nếu đơn hàng chưa giao nhận")
    void testSubmitReviewPendingOrder() throws Exception {
        long orderId = 100L;
        OrderEntity order = createDeliveredOrder(orderId, 200L, 300L);
        var statusField = OrderEntity.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(order, OrderEntity.STATUS_SELLER_CONFIRMED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        CreateReviewRequest request = new CreateReviewRequest(orderId, 5, "Chưa nhận đã đánh giá");

        assertThatThrownBy(() -> service.submitReview(200L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chỉ có thể đánh giá đối tác khi đơn hàng đã giao hoặc hoàn tất");
    }

    @Test
    @DisplayName("getUserReviews: Tính trung bình rating chính xác")
    void testGetUserReviewsAverage() {
        long userId = 300L;
        List<ReviewResponse> reviews = List.of(
                new ReviewResponse(1L, 10L, 201L, "u1", userId, "target", 5, "Tốt", NOW, NOW),
                new ReviewResponse(2L, 11L, 202L, "u2", userId, "target", 4, "Khá", NOW, NOW),
                new ReviewResponse(3L, 12L, 203L, "u3", userId, "target", 3, "Bình thường", NOW, NOW)
        );
        when(disputeRepository.findReviewsForUser(userId, 10)).thenReturn(reviews);

        UserReviewsSummaryResponse summary = service.getUserReviews(userId, 10);

        assertThat(summary.totalReviews()).isEqualTo(3L);
        assertThat(summary.averageRating()).isEqualTo(4.0);
    }
}
