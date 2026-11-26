package com.oldbutgold.shop.modules.platform.application;

import com.oldbutgold.shop.modules.platform.api.PublicSellerDtos.*;
import com.oldbutgold.shop.modules.platform.api.PublicSellerProfileController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicSellerProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private JdbcTemplate jdbc;
    private PublicSellerProfileService service;
    private PublicSellerProfileController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new PublicSellerProfileService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        controller = new PublicSellerProfileController(service);
    }

    private Map<String, Object> activeSellerRow(long sellerId, boolean allowPublicAvatar, String avatarUrl) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", sellerId);
        row.put("full_name", "Nguyễn Văn Bán");
        row.put("avatar_url", avatarUrl);
        row.put("status", "ACTIVE");
        row.put("governance_state", "ACTIVE");
        row.put("governance_until", null);
        row.put("deleted_at", null);
        row.put("created_at", Timestamp.from(NOW.minusSeconds(86400 * 30)));
        row.put("has_seller_role", true);
        row.put("ekyc_verified", true);
        row.put("store_name", "Tiệm Đồ Cổ Sài Gòn");
        row.put("allow_public_avatar", allowPublicAvatar);
        return row;
    }

    @Test
    @DisplayName("allowPublicAvatar = false phải trả avatarUrl = null và 0 review phải trả averageRating = null")
    void testAvatarHiddenWhenConsentOffAndNullAverageWhenNoReviews() {
        when(jdbc.queryForList(contains("FROM public.users u"), eq(10L)))
                .thenReturn(List.of(activeSellerRow(10L, false, "https://cdn.example.com/secret-avatar.jpg")));
        when(jdbc.queryForList(contains("GROUP BY r.rating"), eq(10L), eq(10L)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(contains("FROM public.products p"), eq(Long.class), eq(10L)))
                .thenReturn(3L);
        when(jdbc.queryForObject(contains("FROM public.orders o"), eq(Long.class), eq(10L)))
                .thenReturn(5L);

        ResponseEntity<?> response = controller.getPublicProfile(10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");

        PublicSellerProfileResponse body = (PublicSellerProfileResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.displayName()).isEqualTo("Tiệm Đồ Cổ Sài Gòn");
        assertThat(body.avatarUrl()).isNull(); // Critical: never leak avatar URL when opt-in is OFF
        assertThat(body.ekycVerified()).isTrue();
        assertThat(body.stats().averageRating()).isNull(); // Never fabricate 5.0 when 0 reviews
        assertThat(body.stats().totalReviews()).isEqualTo(0L);
        assertThat(body.stats().activeProductsCount()).isEqualTo(3L);
        assertThat(body.stats().completedTransactionsCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("allowPublicAvatar = true trả avatarUrl và tính đúng averageRating + ratingDistribution")
    void testAvatarShownWhenConsentOnAndRealStatsComputed() {
        when(jdbc.queryForList(contains("FROM public.users u"), eq(10L)))
                .thenReturn(List.of(activeSellerRow(10L, true, "https://cdn.example.com/public-avatar.jpg")));
        when(jdbc.queryForList(contains("GROUP BY r.rating"), eq(10L), eq(10L)))
                .thenReturn(List.of(
                        Map.of("rating", 5, "cnt", 3L),
                        Map.of("rating", 4, "cnt", 1L)
                ));
        when(jdbc.queryForObject(contains("FROM public.products p"), eq(Long.class), eq(10L)))
                .thenReturn(7L);
        when(jdbc.queryForObject(contains("FROM public.orders o"), eq(Long.class), eq(10L)))
                .thenReturn(12L);

        Optional<PublicSellerProfileResponse> opt = service.getPublicProfile(10L);

        assertThat(opt).isPresent();
        PublicSellerProfileResponse profile = opt.get();
        assertThat(profile.avatarUrl()).isEqualTo("https://cdn.example.com/public-avatar.jpg");
        assertThat(profile.stats().totalReviews()).isEqualTo(4L);
        assertThat(profile.stats().averageRating()).isEqualTo(4.8); // (15 + 4) / 4 = 4.75 -> 4.8
        assertThat(profile.stats().ratingDistribution().get(5)).isEqualTo(3L);
        assertThat(profile.stats().ratingDistribution().get(4)).isEqualTo(1L);
        assertThat(profile.stats().ratingDistribution().get(1)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Tài khoản không phải SELLER, DEACTIVATED, LOCKED tạm thời hoặc deleted_at != null phải trả 404")
    void testIneligibleAccountsReturn404() {
        // 1. Buyer-only account
        Map<String, Object> buyerOnly = activeSellerRow(20L, true, null);
        buyerOnly.put("has_seller_role", false);
        when(jdbc.queryForList(contains("FROM public.users u"), eq(20L))).thenReturn(List.of(buyerOnly));
        assertThat(controller.getPublicProfile(20L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 2. DEACTIVATED seller
        Map<String, Object> deactivated = activeSellerRow(21L, true, null);
        deactivated.put("governance_state", "DEACTIVATED");
        when(jdbc.queryForList(contains("FROM public.users u"), eq(21L))).thenReturn(List.of(deactivated));
        assertThat(controller.getPublicProfile(21L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 3. Temporarily LOCKED seller (PO-02 Option A)
        Map<String, Object> lockedTemp = activeSellerRow(22L, true, null);
        lockedTemp.put("governance_state", "LOCKED");
        lockedTemp.put("governance_until", Timestamp.from(NOW.plusSeconds(3600)));
        when(jdbc.queryForList(contains("FROM public.users u"), eq(22L))).thenReturn(List.of(lockedTemp));
        assertThat(controller.getPublicProfile(22L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 4. Soft-deleted seller
        Map<String, Object> deleted = activeSellerRow(23L, true, null);
        deleted.put("deleted_at", Timestamp.from(NOW.minusSeconds(60)));
        when(jdbc.queryForList(contains("FROM public.users u"), eq(23L))).thenReturn(List.of(deleted));
        assertThat(controller.getPublicProfile(23L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("maskCommentPii: Che tự động SĐT và email trong nhận xét tự do (PO-04 Option B)")
    void testMaskCommentPii() {
        String raw = "Shop giao nhanh, liên hệ 0901234567 hoặc email test.buyer@example.com nhé!";
        String masked = PublicSellerProfileService.maskCommentPii(raw);

        assertThat(masked).doesNotContain("0901234567");
        assertThat(masked).doesNotContain("test.buyer@example.com");
        assertThat(masked).contains("[SĐT đã ẩn]");
        assertThat(masked).contains("[email đã ẩn]");
    }

    @Test
    @DisplayName("getPublicReviews trả kèm productId, productTitle, productThumbnailUrl của sản phẩm được đánh giá")
    void testGetPublicReviewsIncludesReviewedProduct() {
        when(jdbc.queryForList(contains("FROM public.users u"), eq(10L)))
                .thenReturn(List.of(activeSellerRow(10L, false, null)));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*)"), eq(Long.class), eq(10L), eq(10L)))
                .thenReturn(1L);
        when(jdbc.query(contains("oi.product_id"), any(org.springframework.jdbc.core.RowMapper.class), eq(10L), eq(10L), eq(10), eq(0)))
                .thenReturn(List.of(new PublicSellerReviewItem(
                        501L,
                        "Người mua đã xác thực",
                        5,
                        "Máy đẹp lắm",
                        NOW,
                        101L,
                        "Máy ảnh Film Olympus OM-1",
                        "/api/v1/media/101.jpg"
                )));

        Optional<PublicPageResponse<PublicSellerReviewItem>> res = service.getPublicReviews(10L, 0, 10, null);
        assertThat(res).isPresent();
        assertThat(res.get().content()).hasSize(1);
        PublicSellerReviewItem rev = res.get().content().get(0);
        assertThat(rev.productId()).isEqualTo(101L);
        assertThat(rev.productTitle()).isEqualTo("Máy ảnh Film Olympus OM-1");
        assertThat(rev.productThumbnailUrl()).isEqualTo("/api/v1/media/101.jpg");
    }
}

