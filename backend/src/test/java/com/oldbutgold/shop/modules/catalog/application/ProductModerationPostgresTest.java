package com.oldbutgold.shop.modules.catalog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductModerationDecisionEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductModerationDecisionRepository;
import com.oldbutgold.shop.modules.platform.application.PlatformAuditFacade;
import com.oldbutgold.shop.modules.platform.infrastructure.persistence.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("isDatabaseConfigured")
class ProductModerationPostgresTest {

    static boolean isDatabaseConfigured() {
        return (System.getenv("OGSHOP_MODERATION_TEST_DB_URL") != null && !System.getenv("OGSHOP_MODERATION_TEST_DB_URL").isBlank())
                || (System.getenv("OGSHOP_TEST_DB_URL") != null && !System.getenv("OGSHOP_TEST_DB_URL").isBlank());
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> {
            String url = System.getenv("OGSHOP_MODERATION_TEST_DB_URL");
            return (url != null && !url.isBlank()) ? url : System.getenv("OGSHOP_TEST_DB_URL");
        });
        properties.add("spring.datasource.username", () -> {
            String u = System.getenv("OGSHOP_MODERATION_TEST_DB_USER");
            if (u == null || u.isBlank()) u = System.getenv("OGSHOP_TEST_DB_USER");
            return (u != null && !u.isBlank()) ? u : "og_shop";
        });
        properties.add("spring.datasource.password", () -> {
            String p = System.getenv("OGSHOP_MODERATION_TEST_DB_PASSWORD");
            if (p == null || p.isBlank()) p = System.getenv("OGSHOP_TEST_DB_PASSWORD");
            return (p != null && !p.isBlank()) ? p : "root";
        });
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private SellerProductService sellerProductService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private ProductModerationDecisionRepository productModerationDecisionRepository;

    @MockitoSpyBean
    private AuditLogRepository auditLogRepository;

    private long sellerId;
    private long reviewerId;
    private long categoryId;

    @BeforeEach
    void setUp() {
        Mockito.reset(productModerationDecisionRepository, auditLogRepository);

        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        sellerId = jdbc.queryForObject(
                "INSERT INTO users(email, password_hash, full_name) VALUES (?, ?, ?) RETURNING user_id",
                Long.class, "seller-" + randomSuffix + "@example.com", "hash", "Seller " + randomSuffix
        );
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, role_id FROM roles WHERE role_name='SELLER'", sellerId);

        reviewerId = jdbc.queryForObject(
                "INSERT INTO users(email, password_hash, full_name) VALUES (?, ?, ?) RETURNING user_id",
                Long.class, "reviewer-" + randomSuffix + "@example.com", "hash", "Reviewer " + randomSuffix
        );
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, role_id FROM roles WHERE role_name='KTV'", reviewerId);

        categoryId = jdbc.queryForObject("SELECT category_id FROM categories LIMIT 1", Long.class);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(productModerationDecisionRepository, auditLogRepository);
    }

    private long createDraftProduct() {
        var createReq = new CatalogDtos.CreateOrUpdateProductRequest(
                categoryId, "iPhone 15 Pro Max", "Mô tả sản phẩm test",
                BigDecimal.valueOf(25000000), "LIKE_NEW", "1 tháng", null, null, null, "Hà Nội", false, null, List.of(categoryId)
        );
        var created = sellerProductService.createProduct(sellerId, createReq);
        long productId = created.productId();
        jdbc.update("INSERT INTO product_media(product_id, media_type, media_url, display_order) VALUES (?, 'IMAGE', 'https://example.com/img1.jpg', 1)", productId);
        jdbc.update("INSERT INTO product_media(product_id, media_type, media_url, display_order) VALUES (?, 'VIDEO', 'https://example.com/vid1.mp4', 2)", productId);
        return productId;
    }

    private long createPendingProduct() {
        long productId = createDraftProduct();
        sellerProductService.submitProduct(sellerId, productId);
        return productId;
    }

    @Test
    void flywayV10_schemaAndConstraints_enforcedByPostgreSQL() {
        long productId = createPendingProduct();
        String key = "ck-" + UUID.randomUUID();

        // 1. Negative product_version must fail check constraint
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
            VALUES (?, ?, 'APPROVED', NULL, -1, ?)
        """, productId, reviewerId, "ck-" + UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);

        // 2. Valid insert (APPROVED with NULL reason)
        jdbc.update("""
            INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
            VALUES (?, ?, 'APPROVED', NULL, 1, ?)
        """, productId, reviewerId, key);

        // 3. Duplicate command_key triggers unique constraint violation
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
            VALUES (?, ?, 'APPROVED', NULL, 1, ?)
        """, productId, reviewerId, key)).isInstanceOf(DataIntegrityViolationException.class);

        // 4. Invalid decision value triggers check constraint
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
            VALUES (?, ?, 'INVALID_DECISION', NULL, 1, ?)
        """, productId, reviewerId, "ck-" + UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);

        // 5. APPROVED with reason must fail check constraint
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
            VALUES (?, ?, 'APPROVED', 'Should not have reason', 1, ?)
        """, productId, reviewerId, "ck-" + UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);

        // 6. REJECTED with NULL or empty reason triggers check constraint
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
            VALUES (?, ?, 'REJECTED', NULL, 1, ?)
        """, productId, reviewerId, "ck-" + UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
            VALUES (?, ?, 'REJECTED', '   ', 1, ?)
        """, productId, reviewerId, "ck-" + UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);

        // 7. Invalid foreign keys trigger FK constraint
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
            VALUES (99999999, ?, 'APPROVED', NULL, 1, ?)
        """, reviewerId, "ck-" + UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
            VALUES (?, 99999999, 'APPROVED', NULL, 1, ?)
        """, productId, "ck-" + UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);

        // 8. UPDATE on product_moderation_decisions is prohibited by append-only trigger
        Long decisionId = jdbc.queryForObject("SELECT decision_id FROM product_moderation_decisions WHERE command_key=?", Long.class, key);
        assertThatThrownBy(() -> jdbc.update("UPDATE product_moderation_decisions SET reason='tampered' WHERE decision_id=?", decisionId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        // 9. DELETE on product_moderation_decisions is prohibited by append-only trigger
        assertThatThrownBy(() -> jdbc.update("DELETE FROM product_moderation_decisions WHERE decision_id=?", decisionId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        // 10. products.content_revision < 1 must fail check constraint
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET content_revision=0 WHERE product_id=?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void publishProduct_draftPendingRejected_cannotPublishDirectly() {
        long draftId = createDraftProduct();

        // 1. DRAFT cannot publish
        assertThatThrownBy(() -> sellerProductService.publishProduct(sellerId, draftId))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("HIDDEN");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, draftId)).isEqualTo("DRAFT");

        // 2. PENDING cannot publish
        sellerProductService.submitProduct(sellerId, draftId);
        assertThatThrownBy(() -> sellerProductService.publishProduct(sellerId, draftId))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("HIDDEN");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, draftId)).isEqualTo("PENDING");

        // 3. REJECTED cannot publish
        long rev = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, draftId);
        sellerProductService.rejectProduct(reviewerId, draftId, "Ảnh mờ", rev, "ck-rej-pub-" + draftId);
        assertThatThrownBy(() -> sellerProductService.publishProduct(sellerId, draftId))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("HIDDEN");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, draftId)).isEqualTo("REJECTED");
    }

    @Test
    void publishProduct_hiddenLegacyWithoutDecision_blockedFromPublishing() {
        long productId = createDraftProduct();
        // Manually set status to HIDDEN without any moderation decision (legacy data)
        jdbc.update("UPDATE products SET status='HIDDEN' WHERE product_id=?", productId);

        assertThatThrownBy(() -> sellerProductService.publishProduct(sellerId, productId))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("chưa có quyết định kiểm duyệt");

        String status = jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId);
        assertThat(status).isEqualTo("HIDDEN");
    }

    @Test
    void publishProduct_hiddenWithEditedContentOrMedia_blockedFromPublishing() {
        long productId = createPendingProduct();
        long rev1 = jdbc.queryForObject("SELECT content_revision FROM products WHERE product_id=?", Long.class, productId);

        // 1. Approve product -> ACTIVE
        sellerProductService.approveProduct(reviewerId, productId, jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId), "ck-app-pub-" + productId);
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("ACTIVE");

        // 2. Seller hides product -> HIDDEN
        sellerProductService.hideProduct(sellerId, productId);
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("HIDDEN");

        // 3. Without editing, publishing works
        sellerProductService.publishProduct(sellerId, productId);
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("ACTIVE");

        // 4. Hide again and edit details -> contentRevision increments
        sellerProductService.hideProduct(sellerId, productId);
        long version = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);
        var updateReq = new CatalogDtos.CreateOrUpdateProductRequest(
                categoryId, "iPhone 15 Pro Max Sửa Giá", "Mô tả mới",
                BigDecimal.valueOf(23000000), "GOOD", "2 tháng", null, null, null, "Đà Nẵng", false, version, List.of(categoryId)
        );
        sellerProductService.updateProduct(sellerId, productId, updateReq);

        long rev2 = jdbc.queryForObject("SELECT content_revision FROM products WHERE product_id=?", Long.class, productId);
        assertThat(rev2).isGreaterThan(rev1);

        // 5. Publish must be blocked because content changed since last approved decision!
        assertThatThrownBy(() -> sellerProductService.publishProduct(sellerId, productId))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("đã thay đổi");

        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("HIDDEN");
    }

    @Test
    void moderation_fullApprovalFlow_persistsDecisionAndAuditAtomically() throws Exception {
        long productId = createPendingProduct();
        long rev = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);

        var auth = jwt().jwt(token -> token.subject(String.valueOf(reviewerId)))
                .authorities(new SimpleGrantedAuthority("ROLE_KTV"));

        String cmdKey = "cmd-approve-" + productId;
        var body = Map.of("expectedVersion", rev, "commandKey", cmdKey);

        mockMvc.perform(post("/api/v1/moderation/products/" + productId + "/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify product status in Postgres is ACTIVE
        String status = jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId);
        assertThat(status).isEqualTo("ACTIVE");

        // Verify decision persisted in Postgres
        var decisionCount = jdbc.queryForObject(
                "SELECT count(*) FROM product_moderation_decisions WHERE product_id=? AND decision='APPROVED' AND reviewer_id=? AND command_key=?",
                Integer.class, productId, reviewerId, cmdKey
        );
        assertThat(decisionCount).isEqualTo(1);

        // Verify audit log persisted in Postgres
        var auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_type='PRODUCT' AND entity_id=? AND action='APPROVE_PRODUCT' AND user_id=?",
                Integer.class, productId, reviewerId
        );
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void moderation_rejectionFlow_persistsReasonAndSellerCanQueryIt() throws Exception {
        long productId = createPendingProduct();
        long rev = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);

        var auth = jwt().jwt(token -> token.subject(String.valueOf(reviewerId)))
                .authorities(new SimpleGrantedAuthority("ROLE_KTV"));

        String reason = "Video quay cận cảnh bị mờ nhòe, không nhìn rõ vết xước màn hình.";
        var body = Map.of("reason", reason, "expectedVersion", rev, "commandKey", "cmd-reject-" + productId);

        mockMvc.perform(post("/api/v1/moderation/products/" + productId + "/reject")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Seller queries own product details
        var sellerProduct = sellerProductService.getSellerProduct(sellerId, productId);
        assertThat(sellerProduct.status()).isEqualTo("REJECTED");
        assertThat(sellerProduct.rejectionReason()).isEqualTo(reason);
        assertThat(sellerProduct.reviewedAt()).isNotNull();

        // Seller summary list also has rejection reason
        var sellerProducts = sellerProductService.getSellerProducts(sellerId, 0, 10);
        var item = sellerProducts.items().stream().filter(p -> p.productId() == productId).findFirst().orElseThrow();
        assertThat(item.rejectionReason()).isEqualTo(reason);
    }

    @Test
    void moderation_validation_missingNullOrInvalidInputs_returns400AndNoDataWritten() throws Exception {
        long productId = createPendingProduct();
        long rev = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);

        var auth = jwt().jwt(token -> token.subject(String.valueOf(reviewerId)))
                .authorities(new SimpleGrantedAuthority("ROLE_KTV"));

        // 1. Missing body -> 400
        mockMvc.perform(post("/api/v1/moderation/products/" + productId + "/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // 2. Negative expectedVersion -> 400
        mockMvc.perform(post("/api/v1/moderation/products/" + productId + "/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expectedVersion", -1, "commandKey", "valid-key"))))
                .andExpect(status().isBadRequest());

        // 3. Blank commandKey -> 400
        mockMvc.perform(post("/api/v1/moderation/products/" + productId + "/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expectedVersion", rev, "commandKey", "   "))))
                .andExpect(status().isBadRequest());

        // 4. Oversize commandKey -> 400
        mockMvc.perform(post("/api/v1/moderation/products/" + productId + "/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expectedVersion", rev, "commandKey", "k".repeat(101)))))
                .andExpect(status().isBadRequest());

        // 5. Reject with missing reason -> 400
        mockMvc.perform(post("/api/v1/moderation/products/" + productId + "/reject")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expectedVersion", rev, "commandKey", "valid-key"))))
                .andExpect(status().isBadRequest());

        // 6. Reject with blank reason -> 400
        mockMvc.perform(post("/api/v1/moderation/products/" + productId + "/reject")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "   ", "expectedVersion", rev, "commandKey", "valid-key"))))
                .andExpect(status().isBadRequest());

        // 7. Reject with oversize reason -> 400
        mockMvc.perform(post("/api/v1/moderation/products/" + productId + "/reject")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "a".repeat(501), "expectedVersion", rev, "commandKey", "valid-key"))))
                .andExpect(status().isBadRequest());

        // 8. Service caller validation
        assertThatThrownBy(() -> sellerProductService.approveProduct(0L, productId, rev, "key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sellerProductService.approveProduct(reviewerId, productId, null, "key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sellerProductService.approveProduct(reviewerId, productId, rev, null))
                .isInstanceOf(IllegalArgumentException.class);

        // Verify status remains PENDING and no decision rows created
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_moderation_decisions WHERE product_id=?", Integer.class, productId)).isEqualTo(0);
    }

    @Test
    void moderation_sameKeyReplay_andConflictScenarios() {
        long productId = createPendingProduct();
        long rev = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);
        String key = "shared-cmd-key-" + UUID.randomUUID();

        // 1. First call: approves with key
        var res1 = sellerProductService.approveProduct(reviewerId, productId, rev, key);
        assertThat(res1.status()).isEqualTo("ACTIVE");

        // 2. Second call with exact same parameters: idempotent replay (returns original status ACTIVE)
        var res2 = sellerProductService.approveProduct(reviewerId, productId, rev, key);
        assertThat(res2.status()).isEqualTo("ACTIVE");

        // 3. Same key with different reviewerId -> CommandKeyConflictException
        long otherReviewerId = jdbc.queryForObject(
                "INSERT INTO users(email, password_hash, full_name) VALUES (?, ?, ?) RETURNING user_id",
                Long.class, "other-rev-" + UUID.randomUUID() + "@example.com", "hash", "Other Reviewer"
        );
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,role_id FROM roles WHERE role_name='KTV'", otherReviewerId);
        assertThatThrownBy(() -> sellerProductService.approveProduct(otherReviewerId, productId, rev, key))
                .isInstanceOf(CommandKeyConflictException.class);

        // 4. Same key with different expectedVersion -> CommandKeyConflictException
        assertThatThrownBy(() -> sellerProductService.approveProduct(reviewerId, productId, 9999L, key))
                .isInstanceOf(CommandKeyConflictException.class);

        // 5. Same key with different action (REJECT instead of APPROVE) -> CommandKeyConflictException
        assertThatThrownBy(() -> sellerProductService.rejectProduct(reviewerId, productId, "Lý do khác", rev, key))
                .isInstanceOf(CommandKeyConflictException.class);

        // Verify exactly one decision row in Postgres
        var count = jdbc.queryForObject("SELECT count(*) FROM product_moderation_decisions WHERE command_key=?", Integer.class, key);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void moderation_sameKeyReplay_rejectReturnsOriginalRejectedStatusAfterResubmit() {
        long productId = createPendingProduct();
        long rev = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);
        String rejKey = "cmd-rej-replay-" + productId;
        String reason = "Cần bổ sung thêm phụ kiện đi kèm";

        // 1. Reject product
        var rejRes = sellerProductService.rejectProduct(reviewerId, productId, reason, rev, rejKey);
        assertThat(rejRes.status()).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("REJECTED");

        // 2. Seller resubmits product -> status is PENDING again
        sellerProductService.submitProduct(sellerId, productId);
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("PENDING");

        // 3. Retry the old reject request with same key and payload
        // MUST return original recorded result (REJECTED) without resetting current PENDING status or creating a new decision!
        var replayRes = sellerProductService.rejectProduct(reviewerId, productId, reason, rev, rejKey);
        assertThat(replayRes.status()).isEqualTo("REJECTED");

        // Current status in DB remains PENDING!
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("PENDING");

        // Exactly one decision row in Postgres
        var count = jdbc.queryForObject("SELECT count(*) FROM product_moderation_decisions WHERE command_key=?", Integer.class, rejKey);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void moderation_contentRevisionTracking_andSoldReservedImmutability() {
        long productId = createDraftProduct();
        long rev1 = jdbc.queryForObject("SELECT content_revision FROM products WHERE product_id=?", Long.class, productId);

        // 1. Seller updates details in DRAFT -> contentRevision increments
        long version = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);
        var updateReq = new CatalogDtos.CreateOrUpdateProductRequest(
                categoryId, "iPhone 15 Pro Max Sửa", "Mô tả mới",
                BigDecimal.valueOf(24000000), "GOOD", "2 tháng", null, null, null, "Hà Nội", false, version, List.of(categoryId)
        );
        sellerProductService.updateProduct(sellerId, productId, updateReq);
        long rev2 = jdbc.queryForObject("SELECT content_revision FROM products WHERE product_id=?", Long.class, productId);
        assertThat(rev2).isGreaterThan(rev1);

        // Submit to PENDING
        sellerProductService.submitProduct(sellerId, productId);

        // 2. Stale expectedVersion rev1 fails with ProductVersionConflictException
        assertThatThrownBy(() -> sellerProductService.approveProduct(reviewerId, productId, rev1, "stale-key-" + UUID.randomUUID()))
                .isInstanceOf(ProductVersionConflictException.class)
                .hasMessageContaining("Phiên bản tin đăng đã thay đổi");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("PENDING");

        // 3. Product in SOLD or RESERVED status rejects moderation
        jdbc.update("UPDATE products SET status='SOLD' WHERE product_id=?", productId);
        assertThatThrownBy(() -> sellerProductService.approveProduct(reviewerId, productId, rev2, "key-sold-1"))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("SOLD");

        UUID checkoutGroup = UUID.randomUUID();
        long orderId = jdbc.queryForObject("""
            INSERT INTO orders (checkout_group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                                shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                                subtotal, shipping_fee, total_amount, buyer_system_fee, seller_system_fee, seller_proceeds, payment_due_at)
            VALUES (?, ?, ?, 'Nguyen Van A', '0912345678', 'HN', 'BD', 'Phuong', 'So 1',
                    24000000, 0, 24000000, 0, 0, 24000000, NOW() + INTERVAL '1 day')
            RETURNING order_id
        """, Long.class, checkoutGroup, reviewerId, sellerId);
        jdbc.update("""
            INSERT INTO order_items(order_id, product_id, product_title, quantity, listed_price, agreed_price,
                                    buyer_system_fee, seller_system_fee, buyer_line_total, seller_line_proceeds, fee_policy_id, pricing_source)
            VALUES (?, ?, 'iPhone', 1, 24000000, 24000000, 0, 0, 24000000, 24000000, 1, 'LIST_PRICE')
        """, orderId, productId);
        jdbc.update("UPDATE products SET status='RESERVED', reserved_until=NOW() + INTERVAL '1 hour', reserved_order_id=? WHERE product_id=?", orderId, productId);

        assertThatThrownBy(() -> sellerProductService.rejectProduct(reviewerId, productId, "Lý do", rev2, "key-res-1"))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId)).isEqualTo("RESERVED");
    }

    @Test
    void delayedCommandCannotApproveResubmittedStateWithUnchangedContent() {
        long id = createPendingProduct();
        long oldVersion = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, id);
        long content = jdbc.queryForObject("SELECT content_revision FROM products WHERE product_id=?", Long.class, id);
        sellerProductService.rejectProduct(reviewerId, id, "Review again", oldVersion, "reject-cycle-" + id);
        sellerProductService.submitProduct(sellerId, id);
        assertThat(jdbc.queryForObject("SELECT content_revision FROM products WHERE product_id=?", Long.class, id)).isEqualTo(content);
        assertThatThrownBy(() -> sellerProductService.approveProduct(reviewerId, id, oldVersion, "delayed-old-" + id))
                .isInstanceOf(ProductVersionConflictException.class);
        long currentVersion = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, id);
        sellerProductService.approveProduct(reviewerId, id, currentVersion, "current-cycle-" + id);
        assertThat(jdbc.queryForObject("SELECT decision_content_revision FROM product_moderation_decisions WHERE command_key=?", Long.class, "current-cycle-" + id)).isEqualTo(content);
    }

    @Test
    void legacyDecisionWithCoincidentallyMatchingNumberDoesNotAuthorizePublishing() {
        long id = createPendingProduct();
        jdbc.update("UPDATE products SET status='HIDDEN',content_revision=1 WHERE product_id=?", id);
        jdbc.update("INSERT INTO product_moderation_decisions(product_id,reviewer_id,decision,product_version,command_key) VALUES(?,?,'APPROVED',1,?)", id, reviewerId, "legacy-unsafe-" + id);
        assertThatThrownBy(() -> sellerProductService.publishProduct(sellerId, id)).isInstanceOf(ProductStateConflictException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, id)).isEqualTo("HIDDEN");
        sellerProductService.submitProduct(sellerId, id);
        long version = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, id);
        sellerProductService.approveProduct(reviewerId, id, version, "renewed-proof-" + id);
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, id)).isEqualTo("ACTIVE");
    }

    @Test
    void concurrency_twoCompetingDecisions_onlyOneWins() throws Exception {
        long productId = createPendingProduct();
        long rev = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        var unexpected = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        AtomicInteger conflictCount = new AtomicInteger(0);

        String key1 = "concurrency-key-1-" + UUID.randomUUID();
        String key2 = "concurrency-key-2-" + UUID.randomUUID();

        executor.submit(() -> {
            try {
                startLatch.await();
                sellerProductService.approveProduct(reviewerId, productId, rev, key1);
                successCount.incrementAndGet();
            } catch (Exception e) {
                if (isExpectedModerationConflict(e)) conflictCount.incrementAndGet(); else unexpected.add(e);
            } finally {
                endLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                sellerProductService.rejectProduct(reviewerId, productId, "Lý do cạnh tranh", rev, key2);
                successCount.incrementAndGet();
            } catch (Exception e) {
                if (isExpectedModerationConflict(e)) conflictCount.incrementAndGet(); else unexpected.add(e);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean finished = endLatch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(unexpected).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        var decisionCount = jdbc.queryForObject(
                "SELECT count(*) FROM product_moderation_decisions WHERE product_id=?",
                Integer.class, productId
        );
        assertThat(decisionCount).isEqualTo(1);
    }

    @Test
    void concurrency_sameKeyConcurrentRequests_exactOneDecisionPersisted() throws Exception {
        long productId = createPendingProduct();
        long rev = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        var unexpected = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        String sharedKey = "concurrent-same-key-" + UUID.randomUUID();

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sellerProductService.approveProduct(reviewerId, productId, rev, sharedKey);
                    successCount.incrementAndGet();
                } catch (Exception error) {
                    if (!isExpectedModerationConflict(error)) unexpected.add(error);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(unexpected).isEmpty();
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        var decisionCount = jdbc.queryForObject(
                "SELECT count(*) FROM product_moderation_decisions WHERE command_key=?",
                Integer.class, sharedKey
        );
        assertThat(decisionCount).isEqualTo(1);
    }

    @Test
    void moderation_atomicRollback_whenDecisionPersistenceFails() {
        long productId = createPendingProduct();
        long rev = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);
        String key = "fail-dec-key-" + UUID.randomUUID();

        // Inject persistence error during decision save
        doThrow(new DataIntegrityViolationException("Simulated decision persistence failure"))
                .when(productModerationDecisionRepository).save(any(ProductModerationDecisionEntity.class));

        assertThatThrownBy(() -> sellerProductService.approveProduct(reviewerId, productId, rev, key))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Verify product in PostgreSQL is still PENDING
        String currentStatus = jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId);
        assertThat(currentStatus).isEqualTo("PENDING");

        // Verify no moderation decisions row was committed
        Integer decisionCount = jdbc.queryForObject(
                "SELECT count(*) FROM product_moderation_decisions WHERE command_key=?",
                Integer.class, key
        );
        assertThat(decisionCount).isEqualTo(0);

        // Verify no audit log committed
        Integer auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_type='PRODUCT' AND entity_id=?",
                Integer.class, productId
        );
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    void moderation_atomicRollback_whenPlatformAuditFails() {
        long productId = createPendingProduct();
        long rev = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);
        String key = "fail-audit-key-" + UUID.randomUUID();

        // Inject persistence error during platform audit persistence
        doThrow(new DataIntegrityViolationException("Simulated audit persistence failure"))
                .when(auditLogRepository).save(any());

        assertThatThrownBy(() -> sellerProductService.approveProduct(reviewerId, productId, rev, key))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Verify product in PostgreSQL is still PENDING
        String currentStatus = jdbc.queryForObject("SELECT status FROM products WHERE product_id=?", String.class, productId);
        assertThat(currentStatus).isEqualTo("PENDING");

        // Verify no moderation decisions row was committed
        Integer decisionCount = jdbc.queryForObject(
                "SELECT count(*) FROM product_moderation_decisions WHERE command_key=?",
                Integer.class, key
        );
        assertThat(decisionCount).isEqualTo(0);

        // Verify no audit log committed
        Integer auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_type='PRODUCT' AND entity_id=?",
                Integer.class, productId
        );
        assertThat(auditCount).isEqualTo(0);
    }
    private static boolean isExpectedModerationConflict(Exception error) {
        return error instanceof ProductStateConflictException || error instanceof ProductVersionConflictException
                || error instanceof org.springframework.dao.OptimisticLockingFailureException
                || (error instanceof org.springframework.dao.DataIntegrityViolationException
                    && error.getMessage() != null && error.getMessage().contains("uq_moderation_decisions_command_key"));
    }}
