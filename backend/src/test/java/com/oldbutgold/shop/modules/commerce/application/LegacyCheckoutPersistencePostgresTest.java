package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.application.CheckoutDtos.BuyNowRequest;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 1.2 Verification Test on Real PostgreSQL:
 * <p>
 * Verifies that the existing legacy checkout flow (CheckoutService.buyNow()) remains 100% operational
 * after adding 9 read-only fields to OrderEntity.
 * <p>
 * Key checks:
 * 1. CheckoutService.buyNow() successfully creates a legacy order.
 * 2. entityManager.flush() + entityManager.clear() forces cache eviction so that findById() re-queries DB.
 * 3. The reloaded OrderEntity populates workflow_model with DB default 'LEGACY_V14' and all other 8 read-only fields as null.
 * 4. Direct SQL SELECT via JdbcTemplate on og_compat.orders confirms DB-level persistence matches entity values.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("LegacyCheckoutPersistencePostgresTest - Verify legacy checkout flow and OrderEntity read-only mapping")
class LegacyCheckoutPersistencePostgresTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("OGSHOP_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO og_compat,public");
        registry.add("spring.flyway.enabled", PostgresTestSafetyGuard::isFlywayOptInEnabled);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long queryForId(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        assertThat(result).isNotNull();
        return result;
    }

    private long createUser(String role) {
        long id = queryForId(
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, '{noop}legacy-qa-pass', 'Legacy QA') RETURNING user_id",
                UUID.randomUUID() + "@example.test"
        );
        jdbcTemplate.update("INSERT INTO og_compat.user_roles(user_id, role_id) SELECT ?, role_id FROM og_compat.roles WHERE role_name = ?", id, role);
        return id;
    }

    private long createProduct(long sellerId) {
        long p = queryForId(
                "INSERT INTO og_compat.products(seller_id, category_id, title, description, listed_price, condition, status, workflow_model) " +
                "VALUES (?, (SELECT min(category_id) FROM og_compat.categories), 'Legacy Camera', 'Legacy QA fixture', 100000, 'GOOD', 'ACTIVE', 'LEGACY_V14') RETURNING product_id",
                sellerId
        );
        jdbcTemplate.update("INSERT INTO og_compat.product_categories(product_id, category_id) SELECT ?, min(category_id) FROM og_compat.categories", p);
        return p;
    }

    private long createAddress(long userId, String recipient, String phone) {
        return queryForId(
                "INSERT INTO og_compat.addresses(user_id, recipient_name, phone_number, province, district, ward, detail_address, is_default, ghn_province_id, ghn_district_id, ghn_ward_code) " +
                "VALUES (?, ?, ?, 'TP Ho Chi Minh', 'Quan 1', 'Phuong Ben Nghe', '123 Nguyen Hue', true, 201, 1442, '20101') RETURNING address_id",
                userId, recipient, phone
        );
    }

    @Test
    @DisplayName("CheckoutService.buyNow() creates valid legacy order; reloaded OrderEntity preserves default workflow_model and null read-only fields")
    void legacyCheckoutPreservesDefaultsAndReadOnlyFields() {
        // 1. Fixture setup: Buyer, Seller, Product and valid addresses with GHN codes
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        long productId = createProduct(sellerId);
        long buyerAddressId = createAddress(buyerId, "Buyer QA", "0900000001");
        createAddress(sellerId, "Seller QA", "0900000002");

        // 2. Buy now via existing CheckoutService
        var request = new BuyNowRequest(productId, buyerAddressId, null, null, "LEGACY-" + UUID.randomUUID());
        var response = checkoutService.buyNow(buyerId, request);
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isNotNull();

        // 3. MANDATORY FLUSH + CLEAR:
        // Evicts L1 cache so Hibernate is forced to execute a fresh SELECT query against database.
        // Without this, findById() would return the in-memory OrderEntity where read-only fields are still unpopulated.
        entityManager.flush();
        entityManager.clear();

        // 4. Reload OrderEntity from database via OrderRepository
        OrderEntity order = orderRepository.findById(response.orderId()).orElseThrow(
                () -> new AssertionError("Order not found after checkout: " + response.orderId())
        );

        // 5. Assert that workflow_model is populated from DB default ('LEGACY_V14') and other 8 read-only fields are null
        assertThat(order.getWorkflowModel()).isEqualTo("LEGACY_V14");
        assertThat(order.getGroupId()).isNull();
        assertThat(order.getReturnAllowed()).isNull();
        assertThat(order.getDeliveryMethod()).isNull();
        assertThat(order.getHandoverDay()).isNull();
        assertThat(order.getValidDeliveredAt()).isNull();
        assertThat(order.getReturnDueAt()).isNull();
        assertThat(order.getEarlyCompletedAt()).isNull();
        assertThat(order.getEarlyCompletionWarningVersion()).isNull();

        // 6. Assert directly via raw SQL against view og_compat.orders
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT workflow_model, group_id, return_allowed, delivery_method, handover_day, " +
                "valid_delivered_at, return_due_at, early_completed_at, early_completion_warning_version " +
                "FROM og_compat.orders WHERE order_id = ?",
                response.orderId()
        );
        assertThat(row.get("workflow_model")).isEqualTo("LEGACY_V14");
        assertThat(row.get("group_id")).isNull();
        assertThat(row.get("return_allowed")).isNull();
        assertThat(row.get("delivery_method")).isNull();
        assertThat(row.get("handover_day")).isNull();
        assertThat(row.get("valid_delivered_at")).isNull();
        assertThat(row.get("return_due_at")).isNull();
        assertThat(row.get("early_completed_at")).isNull();
        assertThat(row.get("early_completion_warning_version")).isNull();
    }

    @Test
    @DisplayName("Legacy Checkout with Coins records DEBIT_CHECKOUT in reward_ledger with valid dynamic policy_id")
    void legacyCheckoutWithCoinsSucceedsAndPersistsDebitRecord() {
        // 1. Fixture setup: Admin, Buyer with Coin Balance, Seller, Product, Addresses
        long adminId = createUser("ADMIN");
        Long canonicalPolicyId = jdbcTemplate.queryForObject(
                "SELECT public.og_provision_canonical_reward_policy(?)",
                Long.class,
                adminId
        );
        assertThat(canonicalPolicyId).isNotNull();

        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        long productId = createProduct(sellerId);
        long buyerAddressId = createAddress(buyerId, "Buyer QA", "0900000001");
        createAddress(sellerId, "Seller QA", "0900000002");

        // Fund buyer reward account with 50,000 coins via Admin CORRECTION
        jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)", buyerId);
        jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key, created_at) " +
                "VALUES (?, ?, ?, 'CORRECTION', 50000, ?, 'Initial QA grant', ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerId, canonicalPolicyId, adminId, "GRANT-" + UUID.randomUUID()
        );

        // 2. Buy now via CheckoutService with 5,000 coins redeemed
        long coinsToRedeem = 5000L;
        System.setProperty("app.reward.canonical-policy-code", "REWARD_CHECKOUT_STANDARD");
        // DEC-10.7A: cap must be configured; using 50% for this test scenario
        checkoutService.setMaxRedemptionPercentage("50");
        CheckoutDtos.OrderCreatedResponse response;
        try (var poApprovalScope = SimulatedPoApprovalTestFixture.openScope(checkoutService)) {
            var request = new BuyNowRequest(productId, buyerAddressId, null, null, null, coinsToRedeem, "LEGACY-COIN-" + UUID.randomUUID());
            response = checkoutService.buyNow(buyerId, request);
        } finally {
            System.clearProperty("app.reward.canonical-policy-code");
            checkoutService.setMaxRedemptionPercentage(null);
        }

        assertThat(response).isNotNull();
        assertThat(response.orderId()).isNotNull();
        assertThat(response.pointsDiscountAmount()).isEqualByComparingTo(new java.math.BigDecimal(coinsToRedeem));

        // 3. Flush & clear entity manager
        entityManager.flush();
        entityManager.clear();

        // 4. Assert OrderEntity reloaded has points discount
        OrderEntity order = orderRepository.findById(response.orderId()).orElseThrow();
        assertThat(order.getPointsDiscountAmount()).isEqualByComparingTo(new java.math.BigDecimal(coinsToRedeem));
        assertThat(order.getWorkflowModel()).isEqualTo("LEGACY_V14");

        // 5. Assert raw SQL in og_compat.reward_ledger for DEBIT_CHECKOUT entry
        Map<String, Object> ledgerEntry = jdbcTemplate.queryForMap(
                "SELECT entry_type, points_delta, order_id, group_id, policy_id, command_key " +
                "FROM og_compat.reward_ledger WHERE order_id = ? AND entry_type = 'DEBIT_CHECKOUT'",
                response.orderId()
        );
        assertThat(ledgerEntry.get("entry_type")).isEqualTo("DEBIT_CHECKOUT");
        assertThat(((Number) ledgerEntry.get("points_delta")).longValue()).isEqualTo(-coinsToRedeem);
        assertThat(ledgerEntry.get("order_id")).isEqualTo(response.orderId());
        assertThat(ledgerEntry.get("group_id")).isNull();
        assertThat(ledgerEntry.get("policy_id")).isEqualTo(canonicalPolicyId);
        assertThat(ledgerEntry.get("command_key")).isEqualTo("CHECKOUT:DEBIT:" + response.orderId());
    }

    @Test
    @DisplayName("Legacy Checkout with Coins fails-closed when canonical reward policy is not found")
    void legacyCheckoutWithCoinsFailsClosedWhenCanonicalPolicyNotFound() {
        long adminId = createUser("ADMIN");
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        long productId = createProduct(sellerId);
        long buyerAddressId = createAddress(buyerId, "Buyer QA", "0900000001");
        createAddress(sellerId, "Seller QA", "0900000002");

        Long policyId = jdbcTemplate.queryForObject(
                "SELECT public.og_provision_canonical_reward_policy(?)",
                Long.class,
                adminId
        );
        jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)", buyerId);
        jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key, created_at) " +
                "VALUES (?, ?, ?, 'CORRECTION', 50000, ?, 'Initial QA grant', ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerId, policyId, adminId, "GRANT-" + UUID.randomUUID()
        );

        System.setProperty("app.reward.canonical-policy-code", "NON_EXISTENT_POLICY_CODE");
        checkoutService.setMaxRedemptionPercentage("50");
        try (var poApprovalScope = SimulatedPoApprovalTestFixture.openScope(checkoutService)) {
            var request = new BuyNowRequest(productId, buyerAddressId, null, null, null, 5000L, "FAIL-COIN-" + UUID.randomUUID());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> checkoutService.buyNow(buyerId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Canonical reward policy not found for policy_code: NON_EXISTENT_POLICY_CODE");
        } finally {
            System.clearProperty("app.reward.canonical-policy-code");
            checkoutService.setMaxRedemptionPercentage(null);
        }
    }

    @Test
    @DisplayName("Legacy Checkout with Coins fails-closed when canonical reward policy code is not configured")
    void legacyCheckoutWithCoinsFailsClosedWhenPolicyCodeNotConfigured() {
        long adminId = createUser("ADMIN");
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        long productId = createProduct(sellerId);
        long buyerAddressId = createAddress(buyerId, "Buyer QA", "0900000001");
        createAddress(sellerId, "Seller QA", "0900000002");

        Long policyId = jdbcTemplate.queryForObject(
                "SELECT public.og_provision_canonical_reward_policy(?)",
                Long.class,
                adminId
        );
        jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)", buyerId);
        jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key, created_at) " +
                "VALUES (?, ?, ?, 'CORRECTION', 50000, ?, 'Initial QA grant', ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerId, policyId, adminId, "GRANT-" + UUID.randomUUID()
        );

        checkoutService.setMaxRedemptionPercentage("50");
        System.clearProperty("app.reward.canonical-policy-code");
        checkoutService.setCanonicalRewardPolicyCode(null);
        try (var poApprovalScope = SimulatedPoApprovalTestFixture.openScope(checkoutService)) {
            var request = new BuyNowRequest(productId, buyerAddressId, null, null, null, 5000L, "FAIL-NO-CONFIG-" + UUID.randomUUID());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> checkoutService.buyNow(buyerId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Missing explicit configuration for canonical reward policy code");
        } finally {
            checkoutService.setMaxRedemptionPercentage(null);
        }
    }

    @Test
    @DisplayName("DEC-10.7A: Legacy Checkout with Coins fails-closed on PostgreSQL when max redemption percentage is not configured")
    void legacyCheckoutWithCoinsFailsClosedWhenCapNotConfigured() {
        long adminId = createUser("ADMIN");
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        long productId = createProduct(sellerId);
        long buyerAddressId = createAddress(buyerId, "Buyer QA", "0900000001");
        createAddress(sellerId, "Seller QA", "0900000002");

        Long policyId = jdbcTemplate.queryForObject(
                "SELECT public.og_provision_canonical_reward_policy(?)",
                Long.class,
                adminId
        );
        jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)", buyerId);
        jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key, created_at) " +
                "VALUES (?, ?, ?, 'CORRECTION', 50000, ?, 'Initial QA grant', ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerId, policyId, adminId, "GRANT-" + UUID.randomUUID()
        );

        System.setProperty("app.reward.canonical-policy-code", "REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage(null);
        try {
            var request = new BuyNowRequest(productId, buyerAddressId, null, null, null, 5000L, "FAIL-NO-CAP-" + UUID.randomUUID());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> checkoutService.buyNow(buyerId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REWARD_MAX_REDEMPTION_PERCENTAGE")
                    .hasMessageContaining("DEC-10.7A");
        } finally {
            System.clearProperty("app.reward.canonical-policy-code");
        }
    }
}
