package com.oldbutgold.shop.modules.platform.infrastructure.persistence;

import com.oldbutgold.shop.modules.platform.api.PublicSellerDtos.PublicSellerProductSummary;
import com.oldbutgold.shop.modules.platform.api.PublicSellerDtos.PublicSellerReviewItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class PublicSellerProfileRepository {

    private final JdbcTemplate jdbc;

    public PublicSellerProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> findSellerSummaryRows(long sellerId) {
        return jdbc.queryForList("""
                SELECT u.user_id,
                       u.full_name,
                       u.avatar_url,
                       u.status,
                       u.governance_state,
                       u.governance_until,
                       u.deleted_at,
                       u.created_at,
                       (
                           EXISTS (
                               SELECT 1
                               FROM og_compat.user_roles ur
                               INNER JOIN og_compat.roles r ON r.role_id = ur.role_id
                               WHERE ur.user_id = u.user_id
                                 AND UPPER(r.role_name) = 'SELLER'
                           ) OR EXISTS (
                               SELECT 1
                               FROM public.seller_profiles sp
                               WHERE sp.user_id = u.user_id
                                 AND sp.record_type = 'seller_profiles'
                           )
                       ) AS has_seller_role,
                       EXISTS (
                           SELECT 1
                           FROM public.ekyc_profiles ep
                           WHERE ep.user_id = u.user_id
                             AND ep.state = 'VERIFIED'
                       ) AS ekyc_verified,
                       (
                           SELECT sp.store_name
                           FROM public.seller_profiles sp
                           WHERE sp.user_id = u.user_id
                             AND sp.record_type = 'seller_profiles'
                           ORDER BY sp.seller_profile_id DESC
                           LIMIT 1
                       ) AS store_name,
                       COALESCE((
                           SELECT sp.allow_public_avatar
                           FROM public.seller_profiles sp
                           WHERE sp.user_id = u.user_id
                             AND sp.record_type = 'seller_profiles'
                           ORDER BY sp.seller_profile_id DESC
                           LIMIT 1
                       ), FALSE) AS allow_public_avatar
                FROM public.users u
                WHERE u.user_id = ?
                """, sellerId);
    }

    public List<Map<String, Object>> findRatingDistributionRows(long sellerId) {
        return jdbc.queryForList("""
                SELECT r.rating, COUNT(*) AS cnt
                FROM public.reviews r
                INNER JOIN public.orders o ON o.order_id = r.order_id
                WHERE r.record_type = 'reviews'
                  AND r.reviewee_id = ?
                  AND r.deleted_at IS NULL
                  AND r.rating BETWEEN 1 AND 5
                  AND o.seller_id = ?
                  AND r.reviewer_id = o.buyer_id
                GROUP BY r.rating
                """, sellerId, sellerId);
    }

    public long countActiveProducts(long sellerId) {
        Long cnt = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM public.products p
                WHERE p.seller_id = ?
                  AND p.status = 'ACTIVE'
                  AND p.deleted_at IS NULL
                  AND COALESCE(p.quantity_available, p.quantity_total - p.quantity_held - p.quantity_sold - p.quantity_unavailable) > 0
                """, Long.class, sellerId);
        return cnt != null ? cnt : 0L;
    }

    public long countCompletedOrders(long sellerId) {
        Long cnt = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM public.orders o
                WHERE o.seller_id = ?
                  AND o.status = 'COMPLETED'
                """, Long.class, sellerId);
        return cnt != null ? cnt : 0L;
    }

    public List<PublicSellerProductSummary> findActiveProductsPage(
            long sellerId,
            String orderByClause,
            int limit,
            long offset
    ) {
        String sql = """
                SELECT p.product_id,
                       p.title,
                       p.listed_price,
                       p.currency,
                       p.condition,
                       p.location,
                       COALESCE(c.category_name, 'Khác') AS category_name,
                       COALESCE(p.quantity_available, p.quantity_total - p.quantity_held - p.quantity_sold - p.quantity_unavailable) AS qty_avail,
                       p.requires_buyer_ekyc,
                       p.created_at,
                       (
                           SELECT pm.media_url
                           FROM public.product_media pm
                           WHERE pm.product_id = p.product_id
                             AND pm.media_type = 'IMAGE'
                           ORDER BY pm.display_order ASC, pm.media_id ASC
                           LIMIT 1
                       ) AS thumbnail_url
                FROM public.products p
                LEFT JOIN public.categories c ON c.category_id = p.category_id
                WHERE p.seller_id = ?
                  AND p.status = 'ACTIVE'
                  AND p.deleted_at IS NULL
                  AND COALESCE(p.quantity_available, p.quantity_total - p.quantity_held - p.quantity_sold - p.quantity_unavailable) > 0
                ORDER BY\s""" + orderByClause + """

                LIMIT ? OFFSET ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> new PublicSellerProductSummary(
                rs.getLong("product_id"),
                rs.getString("title"),
                rs.getBigDecimal("listed_price"),
                rs.getString("currency") != null ? rs.getString("currency").trim() : "VND",
                rs.getString("condition"),
                rs.getString("location"),
                rs.getString("thumbnail_url"),
                rs.getString("category_name"),
                rs.getInt("qty_avail"),
                rs.getBoolean("requires_buyer_ekyc"),
                toInstant(rs.getTimestamp("created_at"))
        ), sellerId, limit, offset);
    }

    public long countPublicReviews(long sellerId, Integer ratingFilter) {
        boolean hasRatingFilter = ratingFilter != null && ratingFilter >= 1 && ratingFilter <= 5;
        String base = """
                SELECT COUNT(*)
                FROM public.reviews r
                INNER JOIN public.orders o ON o.order_id = r.order_id
                WHERE r.record_type = 'reviews'
                  AND r.reviewee_id = ?
                  AND r.deleted_at IS NULL
                  AND r.rating BETWEEN 1 AND 5
                  AND o.seller_id = ?
                  AND r.reviewer_id = o.buyer_id
                """ + (hasRatingFilter ? " AND r.rating = ? " : "");
        Long cnt = hasRatingFilter
                ? jdbc.queryForObject(base, Long.class, sellerId, sellerId, ratingFilter)
                : jdbc.queryForObject(base, Long.class, sellerId, sellerId);
        return cnt != null ? cnt : 0L;
    }

    public List<PublicSellerReviewItem> findPublicReviewsPage(
            long sellerId,
            Integer ratingFilter,
            int limit,
            long offset,
            RowMapper<PublicSellerReviewItem> mapper
    ) {
        boolean hasRatingFilter = ratingFilter != null && ratingFilter >= 1 && ratingFilter <= 5;
        String sql = """
                SELECT r.review_id,
                       r.rating,
                       r.comment,
                       r.created_at,
                       oi.product_id,
                       COALESCE(oi.product_title, p.title) AS product_title,
                       (
                           SELECT pm.media_url
                           FROM public.product_media pm
                           WHERE pm.product_id = oi.product_id
                             AND pm.media_type = 'IMAGE'
                           ORDER BY pm.display_order ASC, pm.media_id ASC
                           LIMIT 1
                       ) AS product_thumbnail_url
                FROM public.reviews r
                INNER JOIN public.orders o ON o.order_id = r.order_id
                LEFT JOIN LATERAL (
                    SELECT oi2.product_id, oi2.product_title
                    FROM public.order_items oi2
                    WHERE oi2.order_id = o.order_id
                    ORDER BY oi2.order_item_id ASC
                    LIMIT 1
                ) oi ON TRUE
                LEFT JOIN public.products p ON p.product_id = oi.product_id
                WHERE r.record_type = 'reviews'
                  AND r.reviewee_id = ?
                  AND r.deleted_at IS NULL
                  AND r.rating BETWEEN 1 AND 5
                  AND o.seller_id = ?
                  AND r.reviewer_id = o.buyer_id
                """ + (hasRatingFilter ? " AND r.rating = ? " : "") + """
                ORDER BY r.created_at DESC, r.review_id DESC
                LIMIT ? OFFSET ?
                """;
        return hasRatingFilter
                ? jdbc.query(sql, mapper, sellerId, sellerId, ratingFilter, limit, offset)
                : jdbc.query(sql, mapper, sellerId, sellerId, limit, offset);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}

