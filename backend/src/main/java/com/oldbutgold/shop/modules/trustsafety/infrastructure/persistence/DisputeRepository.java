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
    private com.oldbutgold.shop.modules.trustsafety.application.EvidenceStorageService evidenceStorageService;

    public DisputeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setEvidenceStorageService(com.oldbutgold.shop.modules.trustsafety.application.EvidenceStorageService evidenceStorageService) {
        this.evidenceStorageService = evidenceStorageService;
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
                   GREATEST(0, COALESCE(o.subtotal, 0) - COALESCE(o.voucher_discount_amount, 0) - COALESCE(o.points_discount_amount, 0)) AS refundable_goods_amount,
                   d.reason, d.description, d.status, d.verdict_action, d.created_at, d.updated_at,
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
            BigDecimal disputeAmount = rs.getBigDecimal("refundable_goods_amount");
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
                   s.email AS seller_email,
                   GREATEST(0, COALESCE(o.subtotal, 0) - COALESCE(o.voucher_discount_amount, 0) - COALESCE(o.points_discount_amount, 0)) AS refundable_goods_amount,
                   d.reason, d.description, d.buyer_evidence_urls,
                   d.video_unboxing_url, d.seller_response, d.seller_evidence_urls, d.seller_response_due_at,
                   d.status, d.verdict_action, d.verdict_notes, d.refund_amount, d.penalty_points,
                   d.resolved_by, d.resolved_at, d.created_at, d.updated_at,
                   d.return_tracking_code, d.return_carrier, d.return_shipment_status,
                   d.return_handed_over_at, d.return_delivered_at,
                   sa.recipient_name AS return_recipient_name,
                   sa.phone_number AS return_recipient_phone,
                   CASE WHEN sa.address_id IS NOT NULL
                        THEN CONCAT_WS(', ', sa.detail_address, sa.ward, sa.district, sa.province)
                        ELSE NULL END AS return_recipient_address,
                   COALESCE((SELECT oi.product_title FROM public.order_items oi WHERE oi.order_id = d.order_id ORDER BY oi.order_item_id ASC LIMIT 1), 'Đơn hàng #' || d.order_id) AS product_title
            FROM public.dispute_cases d
            LEFT JOIN public.orders o ON d.order_id = o.order_id
            LEFT JOIN public.users b ON d.buyer_id = b.user_id
            LEFT JOIN public.users s ON d.seller_id = s.user_id
            LEFT JOIN og_compat.addresses sa ON o.source_address_id = sa.address_id
            WHERE d.dispute_id = ?
            """;
        List<DisputeDetailDto> list = jdbc.query(sql, this::mapDisputeDetail, disputeId);
        return list.stream().findFirst();
    }

    public Optional<DisputeDetailDto> findDisputeByOrderId(long orderId) {
        String sql = """
            SELECT d.dispute_id, d.order_id, d.buyer_id, COALESCE(b.full_name, 'Người mua #' || d.buyer_id) AS buyer_name,
                   b.email AS buyer_email, d.seller_id, COALESCE(s.full_name, 'Người bán #' || d.seller_id) AS seller_name,
                   s.email AS seller_email,
                   GREATEST(0, COALESCE(o.subtotal, 0) - COALESCE(o.voucher_discount_amount, 0) - COALESCE(o.points_discount_amount, 0)) AS refundable_goods_amount,
                   d.reason, d.description, d.buyer_evidence_urls,
                   d.video_unboxing_url, d.seller_response, d.seller_evidence_urls, d.seller_response_due_at,
                   d.status, d.verdict_action, d.verdict_notes, d.refund_amount, d.penalty_points,
                   d.resolved_by, d.resolved_at, d.created_at, d.updated_at,
                   d.return_tracking_code, d.return_carrier, d.return_shipment_status,
                   d.return_handed_over_at, d.return_delivered_at,
                   sa.recipient_name AS return_recipient_name,
                   sa.phone_number AS return_recipient_phone,
                   CASE WHEN sa.address_id IS NOT NULL
                        THEN CONCAT_WS(', ', sa.detail_address, sa.ward, sa.district, sa.province)
                        ELSE NULL END AS return_recipient_address,
                   COALESCE((SELECT oi.product_title FROM public.order_items oi WHERE oi.order_id = d.order_id ORDER BY oi.order_item_id ASC LIMIT 1), 'Đơn hàng #' || d.order_id) AS product_title
            FROM public.dispute_cases d
            LEFT JOIN public.orders o ON d.order_id = o.order_id
            LEFT JOIN public.users b ON d.buyer_id = b.user_id
            LEFT JOIN public.users s ON d.seller_id = s.user_id
            LEFT JOIN og_compat.addresses sa ON o.source_address_id = sa.address_id
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

    public void saveReturnShipment(long disputeId, String trackingCode, String carrier, String shipmentStatus) {
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE public.dispute_cases
                SET return_tracking_code = ?, return_carrier = ?, return_shipment_status = ?, updated_at = ?
                WHERE dispute_id = ?
                """,
                trackingCode, carrier, shipmentStatus, Timestamp.from(now), disputeId);
    }

    public int markReturnHandedOver(long disputeId, long buyerId, Instant handedOverAt) {
        Instant effective = handedOverAt != null ? handedOverAt : Instant.now();
        return jdbc.update("""
                UPDATE public.dispute_cases
                SET return_shipment_status = 'RETURN_SHIPPING',
                    return_handed_over_at = ?,
                    return_handed_over_by = ?,
                    updated_at = ?
                WHERE dispute_id = ?
                  AND return_shipment_status = 'RETURN_CREATED'
                """,
                Timestamp.from(effective), buyerId, Timestamp.from(effective), disputeId);
    }

    public int markReturnDelivered(long disputeId, Instant deliveredAt) {
        Instant effective = deliveredAt != null ? deliveredAt : Instant.now();
        return jdbc.update("""
                UPDATE public.dispute_cases
                SET return_shipment_status = 'RETURN_DELIVERED',
                    return_delivered_at = ?,
                    updated_at = ?
                WHERE dispute_id = ?
                  AND return_shipment_status = 'RETURN_SHIPPING'
                """,
                Timestamp.from(effective), Timestamp.from(effective), disputeId);
    }

    public void markReturnDeliveredAndRefunded(long disputeId, long actorId, BigDecimal refundAmount) {
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE public.dispute_cases
                SET status = 'RESOLVED_REFUND',
                    verdict_action = 'RESOLVED_REFUND',
                    return_shipment_status = 'RETURN_DELIVERED',
                    return_delivered_at = COALESCE(return_delivered_at, ?),
                    refund_amount = COALESCE(?, refund_amount),
                    resolved_at = COALESCE(resolved_at, ?),
                    updated_at = ?
                WHERE dispute_id = ?
                """,
                Timestamp.from(now), refundAmount, Timestamp.from(now), Timestamp.from(now), disputeId);
    }

    public void recordNotification(long userId, String type, String title, String content) {
        Instant now = Instant.now();
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) conn -> {
            java.sql.Savepoint sp = null;
            try {
                sp = conn.setSavepoint("sp_dispute_notif");
            } catch (SQLException ignored) {}
            try (var ps = conn.prepareStatement("""
                    INSERT INTO og_compat.notifications (
                      user_id, type, title, content, is_read, created_at, target_data
                    ) VALUES (?, ?, ?, ?, false, ?, '{}'::jsonb)
                    """)) {
                ps.setLong(1, userId);
                ps.setString(2, type);
                ps.setString(3, title);
                ps.setString(4, content);
                ps.setTimestamp(5, Timestamp.from(now));
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
        BigDecimal disputeAmount = rs.getBigDecimal("refundable_goods_amount");
        if (disputeAmount == null) disputeAmount = BigDecimal.ZERO;
        String reason = rs.getString("reason");
        String description = rs.getString("description");
        List<String> buyerEvidenceUrls = deserializeList(rs.getString("buyer_evidence_urls"));
        String videoUnboxingUrl = rs.getString("video_unboxing_url");
        String sellerResponse = rs.getString("seller_response");
        List<String> sellerEvidenceUrls = deserializeList(rs.getString("seller_evidence_urls"));
        if (evidenceStorageService != null) {
            buyerEvidenceUrls = evidenceStorageService.toPrivateEvidenceUrls(buyerEvidenceUrls);
            videoUnboxingUrl = evidenceStorageService.toPrivateEvidenceUrl(videoUnboxingUrl);
            sellerEvidenceUrls = evidenceStorageService.toPrivateEvidenceUrls(sellerEvidenceUrls);
        }
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
        String returnTrackingCode = null;
        String returnCarrier = null;
        String returnShipmentStatus = null;
        Instant returnHandedOverAt = null;
        Instant returnDeliveredAt = null;
        String returnRecipientName = null;
        String returnRecipientPhone = null;
        String returnRecipientAddress = null;
        try {
            returnTrackingCode = rs.getString("return_tracking_code");
            returnCarrier = rs.getString("return_carrier");
            returnShipmentStatus = rs.getString("return_shipment_status");
            returnHandedOverAt = getInstant(rs, "return_handed_over_at");
            returnDeliveredAt = getInstant(rs, "return_delivered_at");
            returnRecipientName = rs.getString("return_recipient_name");
            returnRecipientPhone = rs.getString("return_recipient_phone");
            returnRecipientAddress = rs.getString("return_recipient_address");
        } catch (SQLException ignored) {}

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
                updatedAt,
                returnTrackingCode,
                returnCarrier,
                returnShipmentStatus,
                returnHandedOverAt,
                returnDeliveredAt,
                returnRecipientName,
                returnRecipientPhone,
                returnRecipientAddress
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
