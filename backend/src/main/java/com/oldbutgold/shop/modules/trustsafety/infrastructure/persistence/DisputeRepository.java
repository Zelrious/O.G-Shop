package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.*;
import com.oldbutgold.shop.modules.trustsafety.api.ReviewDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository
public class DisputeRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public DisputeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureTablesExist();
    }

    private void ensureTablesExist() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.dispute_cases (
                  dispute_id BIGSERIAL PRIMARY KEY,
                  order_id BIGINT NOT NULL,
                  buyer_id BIGINT NOT NULL,
                  seller_id BIGINT NOT NULL,
                  reason VARCHAR(64) NOT NULL,
                  description TEXT NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
                  buyer_evidence_urls TEXT NULL,
                  video_unboxing_url TEXT NULL,
                  seller_response TEXT NULL,
                  seller_evidence_urls TEXT NULL,
                  seller_response_due_at TIMESTAMP WITH TIME ZONE NULL,
                  verdict_action VARCHAR(64) NULL,
                  verdict_notes TEXT NULL,
                  refund_amount NUMERIC(19, 2) NULL,
                  penalty_points INT NOT NULL DEFAULT 0,
                  resolved_by BIGINT NULL,
                  resolved_at TIMESTAMP WITH TIME ZONE NULL,
                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        } catch (Exception ignored) {}

        try {
            jdbc.execute("CREATE SCHEMA IF NOT EXISTS og_compat");
        } catch (Exception ignored) {}

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS og_compat.reviews (
                  review_id BIGSERIAL PRIMARY KEY,
                  order_id BIGINT NOT NULL,
                  reviewer_id BIGINT NOT NULL,
                  reviewee_id BIGINT NOT NULL,
                  rating SMALLINT NOT NULL,
                  comment VARCHAR(2000) NULL,
                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  deleted_at TIMESTAMP WITH TIME ZONE NULL,
                  record_type VARCHAR(32) NOT NULL DEFAULT 'reviews'
                )
                """);
        } catch (Exception ignored) {}

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS og_compat.penalty_ledger (
                  entry_id UUID PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  report_id BIGINT NULL,
                  policy_id BIGINT NULL,
                  points_delta INT NOT NULL,
                  adjustment_of UUID NULL,
                  decided_by BIGINT NULL,
                  reason TEXT NULL,
                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  command_key VARCHAR(150) NULL,
                  record_type VARCHAR(32) NOT NULL DEFAULT 'penalty_ledger'
                )
                """);
        } catch (Exception ignored) {}
    }

    public DisputePageResponse searchDisputes(String status, String query, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            String cleanStatus = status.trim().toUpperCase();
            if ("WAITING_KTV_REVIEW".equals(cleanStatus)) {
                where.append(" AND d.status IN ('WAITING_KTV_REVIEW', 'OPEN', 'INVESTIGATING') ");
            } else if ("WAITING_SELLER_RESPONSE".equals(cleanStatus)) {
                where.append(" AND d.status IN ('WAITING_SELLER_RESPONSE', 'SELLER_REJECTED') ");
            } else {
                where.append(" AND d.status = ? ");
                params.add(cleanStatus);
            }
        }

        if (query != null && !query.isBlank()) {
            String q = "%" + query.trim().toLowerCase() + "%";
            String digits = query.replaceAll("[^0-9]", "");
            where.append(" AND (LOWER(d.description) LIKE ? " +
                    "OR LOWER(d.reason) LIKE ? " +
                    "OR CAST(d.order_id AS TEXT) LIKE ? " +
                    "OR ('og-ord-' || CAST(d.order_id AS TEXT)) LIKE ? " +
                    "OR CAST(d.dispute_id AS TEXT) LIKE ? " +
                    "OR LOWER(b.full_name) LIKE ? " +
                    "OR LOWER(s.full_name) LIKE ? " +
                    "OR EXISTS (SELECT 1 FROM public.order_items oi WHERE oi.order_id = d.order_id AND LOWER(oi.product_title) LIKE ?)) ");
            params.add(q);
            params.add(q);
            params.add(digits.isBlank() ? q : "%" + digits + "%");
            params.add(q);
            params.add(digits.isBlank() ? q : "%" + digits + "%");
            params.add(q);
            params.add(q);
            params.add(q);
        }

        String countSql = "SELECT COUNT(*) FROM public.dispute_cases d " +
                "LEFT JOIN public.users b ON d.buyer_id = b.user_id " +
                "LEFT JOIN public.users s ON d.seller_id = s.user_id " + where;
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        long totalRows = total != null ? total : 0;

        int offset = Math.max(0, page) * Math.max(1, size);
        params.add(size);
        params.add(offset);

        String selectSql = """
            SELECT d.dispute_id, d.order_id, d.buyer_id, COALESCE(b.full_name, 'Người mua #' || d.buyer_id) AS buyer_name,
                   d.seller_id, COALESCE(s.full_name, 'Người bán #' || d.seller_id) AS seller_name,
                   o.total_amount, d.reason, d.description, d.status, d.verdict_action, d.created_at, d.updated_at,
                   COALESCE((SELECT oi.product_title FROM public.order_items oi WHERE oi.order_id = d.order_id ORDER BY oi.order_item_id ASC LIMIT 1), 'Đơn hàng #' || d.order_id) AS product_title
            FROM public.dispute_cases d
            LEFT JOIN public.orders o ON d.order_id = o.order_id
            LEFT JOIN public.users b ON d.buyer_id = b.user_id
            LEFT JOIN public.users s ON d.seller_id = s.user_id
            """ + where + " ORDER BY d.created_at DESC LIMIT ? OFFSET ?";

        List<DisputeSummaryDto> items = jdbc.query(selectSql, (rs, rowNum) -> {
            long disputeId = rs.getLong("dispute_id");
            long orderId = rs.getLong("order_id");
            long buyerId = rs.getLong("buyer_id");
            String buyerName = rs.getString("buyer_name");
            long sellerId = rs.getLong("seller_id");
            String sellerName = rs.getString("seller_name");
            String productTitle = rs.getString("product_title");
            BigDecimal disputeAmount = rs.getBigDecimal("total_amount");
            if (disputeAmount == null) disputeAmount = BigDecimal.ZERO;
            String reason = rs.getString("reason");
            String description = rs.getString("description");
            String rowStatus = rs.getString("status");
            String verdictAction = rs.getString("verdict_action");
            Instant createdAt = getInstant(rs, "created_at");
            Instant updatedAt = getInstant(rs, "updated_at");

            return new DisputeSummaryDto(
                    disputeId,
                    orderId,
                    "OG-ORD-" + orderId,
                    buyerId,
                    buyerName,
                    sellerId,
                    sellerName,
                    productTitle != null && !productTitle.isBlank() ? productTitle : "Đơn hàng #" + orderId,
                    disputeAmount,
                    reason,
                    description,
                    rowStatus,
                    verdictAction,
                    createdAt,
                    updatedAt
            );
        }, params.toArray());

        return new DisputePageResponse(items, totalRows, page, size);
    }

    public Optional<DisputeDetailDto> findDisputeById(long disputeId) {
        String sql = """
            SELECT d.dispute_id, d.order_id, d.buyer_id, COALESCE(b.full_name, 'Người mua #' || d.buyer_id) AS buyer_name,
                   b.email AS buyer_email, d.seller_id, COALESCE(s.full_name, 'Người bán #' || d.seller_id) AS seller_name,
                   s.email AS seller_email, o.total_amount, d.reason, d.description, d.buyer_evidence_urls,
                   d.video_unboxing_url, d.seller_response, d.seller_evidence_urls, d.seller_response_due_at,
                   d.status, d.verdict_action, d.verdict_notes, d.refund_amount, d.penalty_points,
                   d.resolved_by, d.resolved_at, d.created_at, d.updated_at,
                   COALESCE((SELECT oi.product_title FROM public.order_items oi WHERE oi.order_id = d.order_id ORDER BY oi.order_item_id ASC LIMIT 1), 'Đơn hàng #' || d.order_id) AS product_title
            FROM public.dispute_cases d
            LEFT JOIN public.orders o ON d.order_id = o.order_id
            LEFT JOIN public.users b ON d.buyer_id = b.user_id
            LEFT JOIN public.users s ON d.seller_id = s.user_id
            WHERE d.dispute_id = ?
            """;
        List<DisputeDetailDto> list = jdbc.query(sql, this::mapDisputeDetail, disputeId);
        return list.stream().findFirst();
    }

    public Optional<DisputeDetailDto> findDisputeByOrderId(long orderId) {
        String sql = """
            SELECT d.dispute_id, d.order_id, d.buyer_id, COALESCE(b.full_name, 'Người mua #' || d.buyer_id) AS buyer_name,
                   b.email AS buyer_email, d.seller_id, COALESCE(s.full_name, 'Người bán #' || d.seller_id) AS seller_name,
                   s.email AS seller_email, o.total_amount, d.reason, d.description, d.buyer_evidence_urls,
                   d.video_unboxing_url, d.seller_response, d.seller_evidence_urls, d.seller_response_due_at,
                   d.status, d.verdict_action, d.verdict_notes, d.refund_amount, d.penalty_points,
                   d.resolved_by, d.resolved_at, d.created_at, d.updated_at,
                   COALESCE((SELECT oi.product_title FROM public.order_items oi WHERE oi.order_id = d.order_id ORDER BY oi.order_item_id ASC LIMIT 1), 'Đơn hàng #' || d.order_id) AS product_title
            FROM public.dispute_cases d
            LEFT JOIN public.orders o ON d.order_id = o.order_id
            LEFT JOIN public.users b ON d.buyer_id = b.user_id
            LEFT JOIN public.users s ON d.seller_id = s.user_id
            WHERE d.order_id = ?
            ORDER BY d.created_at DESC LIMIT 1
            """;
        List<DisputeDetailDto> list = jdbc.query(sql, this::mapDisputeDetail, orderId);
        return list.stream().findFirst();
    }

    public long createDispute(long orderId, long buyerId, long sellerId, String reason, String description,
                              List<String> buyerEvidenceUrls, String videoUnboxingUrl) {
        Instant now = Instant.now();
        Instant responseDueAt = now.plus(48, ChronoUnit.HOURS);
        String evidenceJson = serializeList(buyerEvidenceUrls);

        String sql = """
            INSERT INTO public.dispute_cases (
              order_id, buyer_id, seller_id, reason, description, status, buyer_evidence_urls,
              video_unboxing_url, seller_response_due_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?)
            """;
        jdbc.update(sql, orderId, buyerId, sellerId, reason, description, evidenceJson,
                videoUnboxingUrl, Timestamp.from(responseDueAt), Timestamp.from(now), Timestamp.from(now));

        Long id = jdbc.queryForObject("SELECT MAX(dispute_id) FROM public.dispute_cases WHERE order_id = ?", Long.class, orderId);
        return id != null ? id : 0L;
    }

    public void saveSellerResponse(long disputeId, String sellerResponse, List<String> sellerEvidenceUrls) {
        Instant now = Instant.now();
        String evidenceJson = serializeList(sellerEvidenceUrls);
        String sql = """
            UPDATE public.dispute_cases
            SET seller_response = ?, seller_evidence_urls = ?, status = 'INVESTIGATING', updated_at = ?
            WHERE dispute_id = ?
            """;
        jdbc.update(sql, sellerResponse, evidenceJson, Timestamp.from(now), disputeId);
    }

    public void applyDecision(long disputeId, long ktvId, String verdictAction, BigDecimal refundAmount,
                              String verdictNotes, int penaltyPoints) {
        Instant now = Instant.now();
        String sql = """
            UPDATE public.dispute_cases
            SET status = ?, verdict_action = ?, refund_amount = ?, verdict_notes = ?,
                penalty_points = ?, resolved_by = ?, resolved_at = ?, updated_at = ?
            WHERE dispute_id = ?
            """;
        jdbc.update(sql, verdictAction, verdictAction, refundAmount, verdictNotes,
                penaltyPoints, ktvId, Timestamp.from(now), Timestamp.from(now), disputeId);
    }

    public void recordPenalty(long userId, int penaltyPoints, String reason, long decidedBy) {
        if (penaltyPoints <= 0) return;
        UUID entryId = UUID.randomUUID();
        Instant now = Instant.now();
        String cmdKey = "DISPUTE_PENALTY:" + entryId;
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) conn -> {
            java.sql.Savepoint sp = null;
            try {
                sp = conn.setSavepoint("sp_dispute_penalty");
            } catch (SQLException ignored) {}
            try (var ps = conn.prepareStatement("""
                    INSERT INTO og_compat.penalty_ledger (
                      entry_id, user_id, points_delta, reason, decided_by, created_at, command_key
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setObject(1, entryId);
                ps.setLong(2, userId);
                ps.setInt(3, penaltyPoints);
                ps.setString(4, reason);
                ps.setLong(5, decidedBy);
                ps.setTimestamp(6, Timestamp.from(now));
                ps.setString(7, cmdKey);
                ps.executeUpdate();
                if (sp != null) {
                    try { conn.releaseSavepoint(sp); } catch (SQLException ignored) {}
                }
            } catch (SQLException e) {
                if (sp != null) {
                    try { conn.rollback(sp); } catch (SQLException ignored) {}
                    try { conn.releaseSavepoint(sp); } catch (SQLException ignored) {}
                }
            }
            return null;
        });
    }

    public void saveReview(long orderId, long reviewerId, long revieweeId, int rating, String comment) {
        Instant now = Instant.now();
        // Check if existing review
        Long count = null;
        try {
            count = jdbc.queryForObject("SELECT COUNT(*) FROM og_compat.reviews WHERE order_id = ? AND reviewer_id = ?", Long.class, orderId, reviewerId);
        } catch (Exception e) {
            try {
                count = jdbc.queryForObject("SELECT COUNT(*) FROM public.reviews WHERE order_id = ? AND reviewer_id = ?", Long.class, orderId, reviewerId);
            } catch (Exception ignored) {}
        }

        if (count != null && count > 0) {
            try {
                jdbc.update("UPDATE og_compat.reviews SET rating = ?, comment = ?, updated_at = ? WHERE order_id = ? AND reviewer_id = ?",
                        rating, comment, Timestamp.from(now), orderId, reviewerId);
            } catch (Exception e) {
                jdbc.update("UPDATE public.reviews SET rating = ?, comment = ?, updated_at = ? WHERE order_id = ? AND reviewer_id = ?",
                        rating, comment, Timestamp.from(now), orderId, reviewerId);
            }
        } else {
            try {
                jdbc.update("INSERT INTO og_compat.reviews (order_id, reviewer_id, reviewee_id, rating, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        orderId, reviewerId, revieweeId, rating, comment, Timestamp.from(now), Timestamp.from(now));
            } catch (Exception e) {
                jdbc.update("INSERT INTO public.reviews (order_id, reviewer_id, reviewee_id, rating, comment, created_at, updated_at, record_type) VALUES (?, ?, ?, ?, ?, ?, ?, 'reviews')",
                        orderId, reviewerId, revieweeId, rating, comment, Timestamp.from(now), Timestamp.from(now));
            }
        }
    }

    public List<ReviewResponse> findReviewsForOrder(long orderId) {
        String sql = """
            SELECT r.review_id, r.order_id, r.reviewer_id, COALESCE(u1.full_name, 'Thành viên #' || r.reviewer_id) AS reviewer_name,
                   r.reviewee_id, COALESCE(u2.full_name, 'Thành viên #' || r.reviewee_id) AS reviewee_name,
                   r.rating, r.comment, r.created_at, r.updated_at
            FROM og_compat.reviews r
            LEFT JOIN public.users u1 ON r.reviewer_id = u1.user_id
            LEFT JOIN public.users u2 ON r.reviewee_id = u2.user_id
            WHERE r.order_id = ? AND r.deleted_at IS NULL
            """;
        try {
            return jdbc.query(sql, this::mapReviewResponse, orderId);
        } catch (Exception e) {
            String fallbackSql = sql.replace("og_compat.reviews", "public.reviews");
            try {
                return jdbc.query(fallbackSql, this::mapReviewResponse, orderId);
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }
    }

    public List<ReviewResponse> findReviewsForUser(long userId, int limit) {
        String sql = """
            SELECT r.review_id, r.order_id, r.reviewer_id, COALESCE(u1.full_name, 'Thành viên #' || r.reviewer_id) AS reviewer_name,
                   r.reviewee_id, COALESCE(u2.full_name, 'Thành viên #' || r.reviewee_id) AS reviewee_name,
                   r.rating, r.comment, r.created_at, r.updated_at
            FROM og_compat.reviews r
            LEFT JOIN public.users u1 ON r.reviewer_id = u1.user_id
            LEFT JOIN public.users u2 ON r.reviewee_id = u2.user_id
            WHERE r.reviewee_id = ? AND r.deleted_at IS NULL
            ORDER BY r.created_at DESC LIMIT ?
            """;
        try {
            return jdbc.query(sql, this::mapReviewResponse, userId, limit);
        } catch (Exception e) {
            String fallbackSql = sql.replace("og_compat.reviews", "public.reviews");
            try {
                return jdbc.query(fallbackSql, this::mapReviewResponse, userId, limit);
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }
    }

    private DisputeDetailDto mapDisputeDetail(ResultSet rs, int rowNum) throws SQLException {
        long disputeId = rs.getLong("dispute_id");
        long orderId = rs.getLong("order_id");
        long buyerId = rs.getLong("buyer_id");
        String buyerName = rs.getString("buyer_name");
        String buyerEmail = rs.getString("buyer_email");
        long sellerId = rs.getLong("seller_id");
        String sellerName = rs.getString("seller_name");
        String sellerEmail = rs.getString("seller_email");
        BigDecimal disputeAmount = rs.getBigDecimal("total_amount");
        if (disputeAmount == null) disputeAmount = BigDecimal.ZERO;
        String reason = rs.getString("reason");
        String description = rs.getString("description");
        List<String> buyerEvidenceUrls = deserializeList(rs.getString("buyer_evidence_urls"));
        String videoUnboxingUrl = rs.getString("video_unboxing_url");
        String sellerResponse = rs.getString("seller_response");
        List<String> sellerEvidenceUrls = deserializeList(rs.getString("seller_evidence_urls"));
        Instant sellerResponseDueAt = getInstant(rs, "seller_response_due_at");
        String status = rs.getString("status");
        String verdictAction = rs.getString("verdict_action");
        String verdictNotes = rs.getString("verdict_notes");
        BigDecimal refundAmount = rs.getBigDecimal("refund_amount");
        int penaltyPoints = rs.getInt("penalty_points");
        Long resolvedBy = rs.getObject("resolved_by", Long.class);
        Instant resolvedAt = getInstant(rs, "resolved_at");
        Instant createdAt = getInstant(rs, "created_at");
        Instant updatedAt = getInstant(rs, "updated_at");

        String productTitle = rs.getString("product_title");
        if (productTitle == null || productTitle.isBlank()) {
            productTitle = "Đơn hàng #" + orderId;
        }

        return new DisputeDetailDto(
                disputeId,
                orderId,
                "OG-ORD-" + orderId,
                buyerId,
                buyerName,
                buyerEmail,
                sellerId,
                sellerName,
                sellerEmail,
                productTitle,
                disputeAmount,
                reason,
                description,
                buyerEvidenceUrls,
                videoUnboxingUrl,
                sellerResponse,
                sellerEvidenceUrls,
                sellerResponseDueAt,
                status,
                verdictAction,
                verdictNotes,
                refundAmount,
                penaltyPoints,
                resolvedBy,
                resolvedAt,
                createdAt,
                updatedAt
        );
    }

    private ReviewResponse mapReviewResponse(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewResponse(
                rs.getLong("review_id"),
                rs.getLong("order_id"),
                rs.getLong("reviewer_id"),
                rs.getString("reviewer_name"),
                rs.getLong("reviewee_id"),
                rs.getString("reviewee_name"),
                rs.getInt("rating"),
                rs.getString("comment"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at")
        );
    }

    private static Instant getInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }

    private String serializeList(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return json.writeValueAsString(list);
        } catch (Exception e) {
            return String.join(",", list);
        }
    }

    private List<String> deserializeList(String str) {
        if (str == null || str.isBlank()) return Collections.emptyList();
        try {
            return json.readValue(str, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }
}
