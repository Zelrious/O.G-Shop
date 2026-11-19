package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.api.ReviewDtos.*;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.DisputeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class OrderReviewService {

    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final JdbcTemplate jdbc;

    public OrderReviewService(
            DisputeRepository disputeRepository,
            OrderRepository orderRepository,
            JdbcTemplate jdbc
    ) {
        this.disputeRepository = disputeRepository;
        this.orderRepository = orderRepository;
        this.jdbc = jdbc;
    }

    public ReviewResponse submitReview(long userId, CreateReviewRequest request) {
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new IllegalArgumentException("Điểm đánh giá phải từ 1 đến 5 sao.");
        }

        OrderEntity order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + request.orderId()));

        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);

        if (!isBuyer && !isSeller) {
            throw new AccessDeniedException("Bạn không phải thành viên của đơn hàng #" + request.orderId());
        }

        if (!OrderEntity.STATUS_COMPLETED.equals(order.getStatus()) && !OrderEntity.STATUS_DELIVERED.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể đánh giá đối tác khi đơn hàng đã giao hoặc hoàn tất. Trạng thái hiện tại: " + order.getStatus());
        }

        long revieweeId = isBuyer ? order.getSellerId() : order.getBuyerId();
        String comment = request.comment() != null ? request.comment().trim() : "";

        disputeRepository.saveReview(order.getId(), userId, revieweeId, request.rating(), comment);

        // Thưởng xu theo BR-UC61-05 và TASK-0071:
        // Người gửi nhận 500 xu:
        rewardCoins(userId, order.getId(), 500, "Thưởng gửi đánh giá đơn #" + order.getId(), "REVIEW_SUBMIT:" + order.getId() + ":" + userId);

        // Người nhận được 4 sao: 400 xu; 5 sao: 500 xu:
        if (request.rating() == 4) {
            rewardCoins(revieweeId, order.getId(), 400, "Thưởng nhận 4 sao đơn #" + order.getId(), "REVIEW_RECEIVE_4:" + order.getId() + ":" + revieweeId);
        } else if (request.rating() == 5) {
            rewardCoins(revieweeId, order.getId(), 500, "Thưởng nhận 5 sao đơn #" + order.getId(), "REVIEW_RECEIVE_5:" + order.getId() + ":" + revieweeId);
        }

        List<ReviewResponse> reviews = disputeRepository.findReviewsForOrder(order.getId());
        return reviews.stream()
                .filter(r -> r.reviewerId().equals(userId))
                .findFirst()
                .orElse(new ReviewResponse(1L, order.getId(), userId, "Bạn", revieweeId, "Đối tác", request.rating(), comment, java.time.Instant.now(), java.time.Instant.now()));
    }

    @Transactional(readOnly = true)
    public OrderReviewsResponse getOrderReviews(long orderId) {
        List<ReviewResponse> list = disputeRepository.findReviewsForOrder(orderId);
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        ReviewResponse buyerReview = null;
        ReviewResponse sellerReview = null;

        if (order != null) {
            for (ReviewResponse r : list) {
                if (r.reviewerId().equals(order.getBuyerId())) {
                    buyerReview = r;
                } else if (r.reviewerId().equals(order.getSellerId())) {
                    sellerReview = r;
                }
            }
        }
        return new OrderReviewsResponse(orderId, buyerReview, sellerReview);
    }

    @Transactional(readOnly = true)
    public UserReviewsSummaryResponse getUserReviews(long userId, int limit) {
        int max = limit > 0 ? Math.min(50, limit) : 10;
        List<ReviewResponse> list = disputeRepository.findReviewsForUser(userId, max);
        double avg = list.stream().mapToInt(ReviewResponse::rating).average().orElse(5.0);
        return new UserReviewsSummaryResponse(userId, Math.round(avg * 10.0) / 10.0, (long) list.size(), list);
    }

    private void rewardCoins(long userId, long orderId, int coins, String reason, String commandKey) {
        if (jdbc == null) {
            return;
        }
        Long count = jdbc.queryForObject("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?", Long.class, commandKey);
        if (count != null && count > 0) {
            return; // Idempotent
        }

        Long accCount = jdbc.queryForObject("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, userId);
        if (accCount == null || accCount == 0) {
            jdbc.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)", userId);
        }

        jdbc.update("""
            INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, order_id, reason, command_key)
            VALUES(?, ?, 'CREDIT_REVIEW', ?, ?, ?, ?)
            """, UUID.randomUUID(), userId, coins, orderId, reason, commandKey);
    }
}
