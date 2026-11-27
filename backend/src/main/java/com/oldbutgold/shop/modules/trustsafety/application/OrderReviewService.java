package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.api.ReviewDtos.*;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.DisputeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.AccountTrustRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class OrderReviewService {

    private static final Logger log = LoggerFactory.getLogger(OrderReviewService.class);
    private static final String ENTRY_TYPE_CREDIT_REVIEW = "CREDIT_REVIEW";

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
        if (request == null || request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new IllegalArgumentException("Điểm đánh giá phải từ 1 đến 5 sao.");
        }
        if (request.orderId() == null) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống.");
        }

        String comment = request.comment() != null ? request.comment().trim() : "";
        ReviewPrivacyGuard.validatePublicComment(comment);

        // Lock order row in real DB before evaluating or mutating review/reward state
        lockOrderIfBackedByDb(request.orderId());
        OrderEntity order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + request.orderId()));

        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new AccessDeniedException("Bạn không phải thành viên của đơn hàng #" + request.orderId());
        }

        if (!isOrderValidDelivered(order)) {
            throw new IllegalStateException("Chỉ có thể đánh giá đối tác khi đơn hàng đã giao hoặc hoàn tất. Trạng thái hiện tại: " + order.getStatus());
        }

        long revieweeId = isBuyer ? order.getSellerId() : order.getBuyerId();
        int newStar = request.rating();

        ExistingReviewRow existing = loadExistingActiveReviewForUpdate(order.getId(), userId);
        boolean isEdit = existing != null;

        // Idempotent replay: same star and same comment -> no DB write, no ledger entry, no notification
        if (isEdit && existing.rating == newStar && Objects.equals(existing.comment == null ? "" : existing.comment, comment)) {
            return disputeRepository.findReviewsForOrder(order.getId()).stream()
                    .filter(r -> r.reviewerId().equals(userId))
                    .findFirst()
                    .orElse(new ReviewResponse(
                            existing.reviewId, order.getId(), userId, "Bạn", revieweeId, "Đối tác",
                            newStar, comment, existing.createdAt, existing.updatedAt
                    ));
        }

        int scoreBefore = reputationScore(revieweeId);
        if (existing == null) {
            disputeRepository.saveReview(order.getId(), userId, revieweeId, newStar, comment);
            ExistingReviewRow created = loadExistingActiveReviewForUpdate(order.getId(), userId);
            long reviewId = created != null ? created.reviewId : 0L;

            // Submitter reward (+500 xu once per (order_id, reviewer_id))
            ensureRewardAccount(userId);
            ensureRewardAccount(revieweeId);
            long submitReward = awardSubmitterOnce(userId, order.getId());

            // Recipient initial reward (4* = 400, 5* = 500, 1..3* = 0)
            long target = targetCoinsForStar(newStar);
            long recipientDelta = 0L;
            if (target > 0) {
                String recKey = reviewId > 0
                        ? "REVIEW_RECIPIENT:" + reviewId + ":REV:0"
                        : "REVIEW_RECEIVE_" + newStar + ":" + order.getId() + ":" + revieweeId;
                recipientDelta = applyRecipientRewardDelta(
                        revieweeId, order.getId(), userId, target,
                        String.format("Thưởng nhận %d sao đơn #%d [U=%d,W=0]", newStar, order.getId(), target),
                        recKey
                );
                if (reviewId > 0) {
                    persistReviewCoinState(reviewId, target, 0L);
                }
            }

            sendReviewNotification(
                    revieweeId, order.getId(), reviewId, userId,
                    null, newStar, reputationScore(revieweeId) - scoreBefore, recipientDelta, comment, false
            );
            log.debug("Created review orderId={}, reviewer={}, reviewee={}, star={}, submitCoins={}, recipientCoins={}",
                    order.getId(), userId, revieweeId, newStar, submitReward, recipientDelta);
        } else {
            // Edit flow under order lock + recipient wallet lock
            int oldStar = existing.rating;
            ensureRewardAccount(revieweeId);
            long walletBalance = lockAndReadWalletBalance(revieweeId);

            long currentUnwaived = existing.unwaivedCoins;
            long currentWaived = existing.waivedCoins;
            if (currentUnwaived == 0L && currentWaived == 0L) {
                // Backward-compatible bootstrap if row predates V61 columns
                currentUnwaived = Math.max(0L, sumExistingRecipientCoins(order.getId(), revieweeId, existing.reviewId));
            }

            long targetNew = targetCoinsForStar(newStar);
            long delta = 0L;
            long nextUnwaived = currentUnwaived;
            long nextWaived = currentWaived;

            if (newStar > oldStar) {
                long credit = Math.max(0L, targetNew - (currentUnwaived + currentWaived));
                delta = credit;
                nextUnwaived = currentUnwaived + credit;
            } else if (newStar < oldStar) {
                long askBack = Math.min(currentUnwaived, Math.max(0L, (currentUnwaived + currentWaived) - targetNew));
                long deducted = Math.min(Math.max(0L, walletBalance), askBack);
                long newlyWaived = askBack - deducted;
                delta = -deducted;
                nextUnwaived = currentUnwaived - askBack;
                nextWaived = currentWaived + newlyWaived;
            }

            disputeRepository.saveReview(order.getId(), userId, revieweeId, newStar, comment);
            persistReviewCoinState(existing.reviewId, nextUnwaived, nextWaived);

            long actualDelta = 0L;
            if (delta != 0L) {
                long revSeq = nextRecipientRevisionSeq(order.getId(), existing.reviewId);
                String reason = String.format("Điều chỉnh đánh giá %d sao -> %d sao đơn #%d [U=%d,W=%d]",
                        oldStar, newStar, order.getId(), nextUnwaived, nextWaived);
                String cmdKey = "REVIEW_RECIPIENT:" + existing.reviewId + ":REV:" + revSeq;
                actualDelta = applyRecipientRewardDelta(revieweeId, order.getId(), userId, delta, reason, cmdKey);
            }

            int repDelta = reputationScore(revieweeId) - scoreBefore;
            sendReviewNotification(
                    revieweeId, order.getId(), existing.reviewId, userId,
                    oldStar, newStar, repDelta, actualDelta, comment, true
            );
        }

        List<ReviewResponse> reviews = disputeRepository.findReviewsForOrder(order.getId());
        return reviews.stream()
                .filter(r -> r.reviewerId().equals(userId))
                .findFirst()
                .orElse(new ReviewResponse(
                        existing != null ? existing.reviewId : 1L,
                        order.getId(), userId, "Bạn", revieweeId, "Đối tác",
                        newStar, comment, Instant.now(), Instant.now()
                ));
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

    private boolean isOrderValidDelivered(OrderEntity order) {
        if ("UC83".equals(order.getWorkflowModel())) {
            return order.getValidDeliveredAt() != null;
        }
        if (order.getValidDeliveredAt() != null
                || OrderEntity.STATUS_DELIVERED.equals(order.getStatus())
                || OrderEntity.STATUS_COMPLETED.equals(order.getStatus())) {
            return true;
        }
        return hasDeliveredOutboundShipment(order.getId());
    }

    private boolean hasDeliveredOutboundShipment(Long orderId) {
        if (jdbc == null || orderId == null) {
            return false;
        }
        try {
            Boolean delivered = jdbc.queryForObject("""
                    SELECT EXISTS(
                        SELECT 1 FROM og_compat.shipments s
                        WHERE s.order_id = ? AND s.leg = 'OUTBOUND'
                          AND (s.status = 'DELIVERED' OR s.delivered_at IS NOT NULL)
                    )
                    """, Boolean.class, orderId);
            return Boolean.TRUE.equals(delivered);
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private void lockOrderIfBackedByDb(Long orderId) {
        if (jdbc == null || orderId == null) {
            return;
        }
        try {
            jdbc.query("SELECT order_id FROM og_compat.orders WHERE order_id = ? FOR UPDATE", rs -> null, orderId);
        } catch (DataAccessException ignored) {
            // H2 / unit mock safety
        }
    }

    private ExistingReviewRow loadExistingActiveReviewForUpdate(long orderId, long reviewerId) {
        if (jdbc == null) {
            return null;
        }
        List<ExistingReviewRow> rows = jdbc.query("""
                SELECT review_id, rating, comment, created_at, updated_at,
                       COALESCE(recipient_unwaived_coins, 0) AS u_coins,
                       COALESCE(recipient_waived_coins, 0) AS w_coins
                FROM public.reviews
                WHERE record_type = 'reviews' AND order_id = ? AND reviewer_id = ? AND deleted_at IS NULL
                FOR UPDATE
                """,
                (rs, idx) -> new ExistingReviewRow(
                        rs.getLong("review_id"),
                        rs.getInt("rating"),
                        rs.getString("comment"),
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now(),
                        rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : Instant.now(),
                        rs.getLong("u_coins"),
                        rs.getLong("w_coins")
                ),
                orderId, reviewerId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void ensureRewardAccount(long userId) {
        if (jdbc == null) {
            return;
        }
        Long accCount = jdbc.queryForObject("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, userId);
        if (accCount == null || accCount == 0) {
            jdbc.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)", userId);
        }
    }

    private long lockAndReadWalletBalance(long userId) {
        if (jdbc == null) {
            return 0L;
        }
        Long bal = jdbc.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ? FOR UPDATE",
                Long.class, userId
        );
        return bal != null ? bal : 0L;
    }

    private long sumExistingRecipientCoins(long orderId, long revieweeId, long reviewId) {
        if (jdbc == null) {
            return 0L;
        }
        Long sum = jdbc.queryForObject("""
                SELECT COALESCE(SUM(points_delta), 0)
                FROM og_compat.reward_ledger
                WHERE order_id = ? AND user_id = ? AND entry_type = 'CREDIT_REVIEW'
                  AND (command_key LIKE ? OR command_key LIKE ?)
                """, Long.class,
                orderId, revieweeId,
                "REVIEW_RECIPIENT:" + reviewId + ":%",
                "REVIEW_RECEIVE_%:" + orderId + ":" + revieweeId);
        return sum != null ? sum : 0L;
    }

    private long nextRecipientRevisionSeq(long orderId, long reviewId) {
        if (jdbc == null) {
            return 1L;
        }
        Long cnt = jdbc.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE order_id = ? AND command_key LIKE ?",
                Long.class, orderId, "REVIEW_RECIPIENT:" + reviewId + ":REV:%"
        );
        return (cnt != null ? cnt : 0L) + 1L;
    }

    private void persistReviewCoinState(long reviewId, long unwaivedCoins, long waivedCoins) {
        if (jdbc == null || reviewId <= 0) {
            return;
        }
        jdbc.update("""
                UPDATE public.reviews
                SET recipient_unwaived_coins = ?, recipient_waived_coins = ?
                WHERE review_id = ? AND record_type = 'reviews'
                """, Math.max(0L, unwaivedCoins), Math.max(0L, waivedCoins), reviewId);
    }

    private long awardSubmitterOnce(long reviewerId, long orderId) {
        String commandKey = "REVIEW_SUBMIT:" + orderId + ":" + reviewerId;
        if (jdbc == null) {
            return 0L;
        }
        Long existing = jdbc.queryForObject("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?", Long.class, commandKey);
        if (existing != null && existing > 0) {
            return 0L;
        }
        jdbc.update("""
                INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, order_id, actor_id, reason, command_key)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), reviewerId, ENTRY_TYPE_CREDIT_REVIEW, 500L, orderId, reviewerId,
                "Thưởng gửi đánh giá đơn #" + orderId, commandKey);
        return 500L;
    }

    private long applyRecipientRewardDelta(long recipientId, long orderId, long reviewerId, long delta, String reason, String commandKey) {
        if (jdbc == null || delta == 0L) {
            return 0L;
        }
        Long existing = jdbc.queryForObject("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?", Long.class, commandKey);
        if (existing != null && existing > 0) {
            return 0L;
        }
        jdbc.update("""
                INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, order_id, actor_id, reason, command_key)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), recipientId, ENTRY_TYPE_CREDIT_REVIEW, delta, orderId, reviewerId, reason, commandKey);
        return delta;
    }

    private void sendReviewNotification(
            long recipientId,
            long orderId,
            long reviewId,
            long reviewerId,
            Integer oldStar,
            int newStar,
            int repDelta,
            long coinDelta,
            String comment,
            boolean isEdit
    ) {
        if (jdbc == null) {
            return;
        }
        String snippet = ReviewPrivacyGuard.sanitizeSnippetForNotification(comment);
        String title = isEdit
                ? String.format("Đối tác đã cập nhật đánh giá đơn #%d (%d sao -> %d sao)", orderId, oldStar, newStar)
                : String.format("Bạn nhận được đánh giá %d sao cho đơn #%d", newStar, orderId);
        StringBuilder content = new StringBuilder();
        content.append(String.format("Đơn #%d vừa được đánh giá %d sao.", orderId, newStar));
        if (repDelta != 0) {
            content.append(String.format(" Uy tín: %s%d điểm.", repDelta > 0 ? "+" : "", repDelta));
        }
        if (coinDelta != 0) {
            content.append(String.format(" Xu thưởng: %s%d xu.", coinDelta > 0 ? "+" : "", coinDelta));
        }
        if (!snippet.isEmpty()) {
            content.append(" Nhận xét: \"").append(snippet).append("\"");
        }
        String targetDataJson = String.format(
                "{\"destination\":\"ORDER_DETAIL\",\"orderId\":%d,\"reviewId\":%d,\"reviewerId\":%d,\"rating\":%d,\"reputationDelta\":%d,\"coinDelta\":%d}",
                orderId, reviewId, reviewerId, newStar, repDelta, coinDelta
        );
        String sql = """
                INSERT INTO og_compat.notifications(
                    user_id, type, title, content, reference_type, reference_id, is_read, created_at, target_data
                ) VALUES (?, ?, ?, ?, 'ORDER', ?, false, ?, ?::jsonb)
                """;
        String notifType = isEdit ? "ORDER_REVIEW_UPDATED" : "ORDER_REVIEW_RECEIVED";
        Timestamp createdAt = Timestamp.from(Instant.now());
        jdbc.execute((ConnectionCallback<Void>) conn -> {
            Savepoint sp = null;
            boolean useSavepoint = !conn.getAutoCommit() && conn.getMetaData().supportsSavepoints();
            if (useSavepoint) {
                sp = conn.setSavepoint("sp_rev_notif");
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, recipientId);
                ps.setString(2, notifType);
                ps.setString(3, title);
                ps.setString(4, content.toString());
                ps.setLong(5, orderId);
                ps.setTimestamp(6, createdAt);
                ps.setString(7, targetDataJson);
                ps.executeUpdate();
                if (sp != null) {
                    conn.releaseSavepoint(sp);
                }
            } catch (SQLException ex) {
                if (sp != null) {
                    conn.rollback(sp);
                    conn.releaseSavepoint(sp);
                }
                // BR-UC46 Exception: failure to deliver notification must NOT block saving the review
                log.warn("Could not insert UC46 review notification for orderId={}, recipientId={}: {}", orderId, recipientId, ex.getMessage());
            }
            return null;
        });
    }

    private static long targetCoinsForStar(int rating) {
        return switch (rating) {
            case 5 -> 500L;
            case 4 -> 400L;
            default -> 0L;
        };
    }

    private int reputationScore(long recipient) {
        if (jdbc == null) return 100;
        return new AccountTrustRepository(jdbc).reputation(recipient).score();
    }

    private record ExistingReviewRow(
            long reviewId,
            int rating,
            String comment,
            Instant createdAt,
            Instant updatedAt,
            long unwaivedCoins,
            long waivedCoins
    ) {}
}
