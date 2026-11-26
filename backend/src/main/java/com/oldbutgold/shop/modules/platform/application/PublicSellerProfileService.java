package com.oldbutgold.shop.modules.platform.application;

import com.oldbutgold.shop.modules.platform.api.PublicSellerDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Read-only aggregator for Public Seller Profile (/api/v1/public/sellers/**).
 * Enforces strict account eligibility, opt-in avatar consent, buyer anonymization,
 * PII masking in free-text comments, deterministic pagination, and zero N+1 queries.
 */
@Service
@Transactional(readOnly = true)
public class PublicSellerProfileService {

    public static final int MAX_PRODUCTS_PAGE_SIZE = 24;
    public static final int MAX_REVIEWS_PAGE_SIZE = 20;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(?:\\+?84|0)[\\s.-]?\\d{2,3}[\\s.-]?\\d{3}[\\s.-]?\\d{3,4}(?!\\d)");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PublicSellerProfileService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<PublicSellerProfileResponse> getPublicProfile(long sellerId) {
        Optional<EligibleSellerRow> sellerOpt = findEligibleSeller(sellerId);
        if (sellerOpt.isEmpty()) {
            return Optional.empty();
        }
        EligibleSellerRow seller = sellerOpt.get();

        // Avatar is opt-in only; never leak URL when allowPublicAvatar is false
        String publicAvatarUrl = seller.allowPublicAvatar() && seller.avatarUrl() != null && !seller.avatarUrl().isBlank()
                ? seller.avatarUrl().trim()
                : null;

        PublicSellerStats stats = computeStats(sellerId);

        return Optional.of(new PublicSellerProfileResponse(
                seller.sellerId(),
                seller.displayName(),
                publicAvatarUrl,
                seller.ekycVerified(),
                seller.joinedAt(),
                stats
        ));
    }

    public Optional<PublicPageResponse<PublicSellerProductSummary>> getPublicProducts(
            long sellerId,
            int page,
            int size,
            String sort
    ) {
        if (findEligibleSeller(sellerId).isEmpty()) {
            return Optional.empty();
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PRODUCTS_PAGE_SIZE, Math.max(1, size));
        int offset = safePage * safeSize;

        Long totalObj = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM public.products p
                WHERE p.seller_id = ?
                  AND p.status = 'ACTIVE'
                  AND p.deleted_at IS NULL
                  AND COALESCE(p.quantity_available, p.quantity_total - p.quantity_held - p.quantity_sold - p.quantity_unavailable) > 0
                """, Long.class, sellerId);
        long total = totalObj != null ? totalObj : 0L;
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        if (total == 0 || offset >= total) {
            return Optional.of(new PublicPageResponse<>(List.of(), safePage, safeSize, total, totalPages));
        }

        String orderBy = switch (sort != null ? sort.trim().toLowerCase(Locale.ROOT) : "newest") {
            case "price_asc" -> "p.listed_price ASC, p.product_id DESC";
            case "price_desc" -> "p.listed_price DESC, p.product_id DESC";
            default -> "p.created_at DESC, p.product_id DESC";
        };

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
                ORDER BY\s""" + orderBy + """
                
                LIMIT ? OFFSET ?
                """;

        List<PublicSellerProductSummary> items = jdbc.query(sql, (rs, rowNum) -> new PublicSellerProductSummary(
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
        ), sellerId, safeSize, offset);

        return Optional.of(new PublicPageResponse<>(items, safePage, safeSize, total, totalPages));
    }

    public Optional<PublicPageResponse<PublicSellerReviewItem>> getPublicReviews(
            long sellerId,
            int page,
            int size,
            Integer ratingFilter
    ) {
        if (findEligibleSeller(sellerId).isEmpty()) {
            return Optional.empty();
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_REVIEWS_PAGE_SIZE, Math.max(1, size));
        int offset = safePage * safeSize;
        boolean hasRatingFilter = ratingFilter != null && ratingFilter >= 1 && ratingFilter <= 5;

        String fromJoin = """
                FROM public.reviews r
                INNER JOIN public.orders o ON o.order_id = r.order_id
                """;
        String whereClause = """
                WHERE r.reviewee_id = ?
                  AND r.deleted_at IS NULL
                  AND r.rating BETWEEN 1 AND 5
                  AND o.seller_id = ?
                  AND r.reviewer_id = o.buyer_id
                """ + (hasRatingFilter ? " AND r.rating = ? " : "");

        Long totalObj = hasRatingFilter
                ? jdbc.queryForObject("SELECT COUNT(*) " + fromJoin + whereClause, Long.class, sellerId, sellerId, ratingFilter)
                : jdbc.queryForObject("SELECT COUNT(*) " + fromJoin + whereClause, Long.class, sellerId, sellerId);
        long total = totalObj != null ? totalObj : 0L;
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        if (total == 0 || offset >= total) {
            return Optional.of(new PublicPageResponse<>(List.of(), safePage, safeSize, total, totalPages));
        }

        String selectSql = """
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
                """ + fromJoin + """
                LEFT JOIN LATERAL (
                    SELECT oi2.product_id, oi2.product_title
                    FROM public.order_items oi2
                    WHERE oi2.order_id = o.order_id
                    ORDER BY oi2.order_item_id ASC
                    LIMIT 1
                ) oi ON TRUE
                LEFT JOIN public.products p ON p.product_id = oi.product_id
                """ + whereClause + """
                ORDER BY r.created_at DESC, r.review_id DESC
                LIMIT ? OFFSET ?
                """;

        List<PublicSellerReviewItem> items = hasRatingFilter
                ? jdbc.query(selectSql, this::mapPublicReview, sellerId, sellerId, ratingFilter, safeSize, offset)
                : jdbc.query(selectSql, this::mapPublicReview, sellerId, sellerId, safeSize, offset);

        return Optional.of(new PublicPageResponse<>(items, safePage, safeSize, total, totalPages));
    }

    /**
     * Public helper for other read models (such as Product Detail UC13) to fetch verified real stats.
     */
    public PublicSellerStats computeStats(long sellerId) {
        Map<Integer, Long> dist = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            dist.put(star, 0L);
        }

        List<Map<String, Object>> ratingRows = jdbc.queryForList("""
                SELECT r.rating, COUNT(*) AS cnt
                FROM public.reviews r
                INNER JOIN public.orders o ON o.order_id = r.order_id
                WHERE r.reviewee_id = ?
                  AND r.deleted_at IS NULL
                  AND r.rating BETWEEN 1 AND 5
                  AND o.seller_id = ?
                  AND r.reviewer_id = o.buyer_id
                GROUP BY r.rating
                """, sellerId, sellerId);

        long totalReviews = 0L;
        long ratingSum = 0L;
        for (Map<String, Object> row : ratingRows) {
            int star = ((Number) row.get("rating")).intValue();
            long cnt = ((Number) row.get("cnt")).longValue();
            if (star >= 1 && star <= 5) {
                dist.put(star, cnt);
                totalReviews += cnt;
                ratingSum += star * cnt;
            }
        }

        Double averageRating = totalReviews == 0L
                ? null
                : BigDecimal.valueOf((double) ratingSum / totalReviews)
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();

        Long activeProductsObj = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM public.products p
                WHERE p.seller_id = ?
                  AND p.status = 'ACTIVE'
                  AND p.deleted_at IS NULL
                  AND COALESCE(p.quantity_available, p.quantity_total - p.quantity_held - p.quantity_sold - p.quantity_unavailable) > 0
                """, Long.class, sellerId);
        long activeProductsCount = activeProductsObj != null ? activeProductsObj : 0L;

        // PO-01 (Option A): 1 COMPLETED order = 1 completed transaction
        Long completedOrdersObj = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM public.orders o
                WHERE o.seller_id = ?
                  AND o.status = 'COMPLETED'
                """, Long.class, sellerId);
        long completedTransactionsCount = completedOrdersObj != null ? completedOrdersObj : 0L;

        return new PublicSellerStats(
                averageRating,
                totalReviews,
                activeProductsCount,
                completedTransactionsCount,
                dist
        );
    }

    /**
     * Checks account eligibility per PO-02 (Option A):
     * - Must exist, not soft-deleted (deleted_at IS NULL)
     * - Must have SELLER role (or an approved seller_profile / active product)
     * - status = 'ACTIVE'
     * - governance_state = 'ACTIVE' (or expired temporary LOCKED where governance_until <= now)
     */
    private Optional<EligibleSellerRow> findEligibleSeller(long sellerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
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

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);

        if (row.get("deleted_at") != null) {
            return Optional.empty();
        }

        boolean hasSellerRole = Boolean.TRUE.equals(row.get("has_seller_role"));
        if (!hasSellerRole) {
            return Optional.empty();
        }

        String status = row.get("status") != null ? row.get("status").toString().toUpperCase(Locale.ROOT) : "";
        if (!"ACTIVE".equals(status)) {
            return Optional.empty();
        }

        String govState = row.get("governance_state") != null
                ? row.get("governance_state").toString().toUpperCase(Locale.ROOT)
                : "ACTIVE";
        Instant govUntil = toInstant(row.get("governance_until"));
        Instant now = clock.instant();

        // PO-02 (Option A): DEACTIVATED or active LOCKED (permanent or not yet expired) => 404
        if ("DEACTIVATED".equals(govState)) {
            return Optional.empty();
        }
        if ("LOCKED".equals(govState) && (govUntil == null || govUntil.isAfter(now))) {
            return Optional.empty();
        }

        String storeName = row.get("store_name") != null ? row.get("store_name").toString().trim() : "";
        String fullName = row.get("full_name") != null ? row.get("full_name").toString().trim() : "";
        String displayName = !storeName.isEmpty() ? storeName : (!fullName.isEmpty() ? fullName : "Người bán #" + sellerId);

        boolean ekycVerified = Boolean.TRUE.equals(row.get("ekyc_verified"));
        boolean allowPublicAvatar = Boolean.TRUE.equals(row.get("allow_public_avatar"));
        String avatarUrl = row.get("avatar_url") != null ? row.get("avatar_url").toString() : null;
        Instant joinedAt = toInstant(row.get("created_at"));

        return Optional.of(new EligibleSellerRow(
                sellerId,
                displayName,
                avatarUrl,
                allowPublicAvatar,
                ekycVerified,
                joinedAt
        ));
    }

    private PublicSellerReviewItem mapPublicReview(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new PublicSellerReviewItem(
                rs.getLong("review_id"),
                "Người mua đã xác thực",
                rs.getInt("rating"),
                maskCommentPii(rs.getString("comment")),
                toInstant(rs.getTimestamp("created_at")),
                rs.getObject("product_id", Long.class),
                rs.getString("product_title"),
                rs.getString("product_thumbnail_url")
        );
    }

    /**
     * PO-04 (Option B): Defense-in-depth read-time masking for emails and phone numbers
     * in free-text review comments without altering persisted DB records.
     */
    public static String maskCommentPii(String rawComment) {
        if (rawComment == null || rawComment.isBlank()) {
            return "";
        }
        String masked = EMAIL_PATTERN.matcher(rawComment).replaceAll("[email đã ẩn]");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[SĐT đã ẩn]");
        return masked;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof Instant inst) {
            return inst;
        }
        return null;
    }

    private record EligibleSellerRow(
            long sellerId,
            String displayName,
            String avatarUrl,
            boolean allowPublicAvatar,
            boolean ekycVerified,
            Instant joinedAt
    ) {}
}

