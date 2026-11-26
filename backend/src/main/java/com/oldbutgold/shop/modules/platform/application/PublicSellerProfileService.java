package com.oldbutgold.shop.modules.platform.application;

import com.oldbutgold.shop.modules.platform.api.PublicSellerDtos.*;
import com.oldbutgold.shop.modules.platform.infrastructure.persistence.PublicSellerProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
 * PII masking in free-text comments, overflow-safe pagination, and zero N+1 queries.
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

    private final PublicSellerProfileRepository repository;
    private final Clock clock;

    @Autowired
    public PublicSellerProfileService(PublicSellerProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PublicSellerProfileService(JdbcTemplate jdbc, Clock clock) {
        this(new PublicSellerProfileRepository(jdbc), clock);
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
        long offset = (long) safePage * safeSize;

        long total = repository.countActiveProducts(sellerId);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        if (total == 0 || offset >= total) {
            return Optional.of(new PublicPageResponse<>(List.of(), safePage, safeSize, total, totalPages));
        }

        String orderBy = switch (sort != null ? sort.trim().toLowerCase(Locale.ROOT) : "newest") {
            case "price_asc" -> "p.listed_price ASC, p.product_id DESC";
            case "price_desc" -> "p.listed_price DESC, p.product_id DESC";
            default -> "p.created_at DESC, p.product_id DESC";
        };

        List<PublicSellerProductSummary> items = repository.findActiveProductsPage(sellerId, orderBy, safeSize, offset);
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
        long offset = (long) safePage * safeSize;

        long total = repository.countPublicReviews(sellerId, ratingFilter);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        if (total == 0 || offset >= total) {
            return Optional.of(new PublicPageResponse<>(List.of(), safePage, safeSize, total, totalPages));
        }

        List<PublicSellerReviewItem> items = repository.findPublicReviewsPage(
                sellerId, ratingFilter, safeSize, offset, this::mapPublicReview);

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

        List<Map<String, Object>> ratingRows = repository.findRatingDistributionRows(sellerId);

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

        long activeProductsCount = repository.countActiveProducts(sellerId);
        // PO-01 (Option A): 1 COMPLETED order = 1 completed transaction
        long completedTransactionsCount = repository.countCompletedOrders(sellerId);

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
        List<Map<String, Object>> rows = repository.findSellerSummaryRows(sellerId);

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
