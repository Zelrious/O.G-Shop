package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.CheckoutGroupEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.CheckoutGroupRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("V51ValidatorChainPostgresTest - Clean Chained Validator Layer & Canonical Reward Policy on PostgreSQL")
class V51ValidatorChainPostgresTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CheckoutGroupRepository checkoutGroupRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Long adminUserId;
    private Long buyerUserId;
    private Long sellerUserId;
    private Long canonicalPolicyId;

    private String key() {
        return UUID.randomUUID().toString();
    }

    private Long createUser(String role) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name, phone_number, status, identity_workflow) " +
                "VALUES (?, 'FAKE', 'QA User', '0900000000', 'ACTIVE', 'UC83') RETURNING user_id",
                Long.class,
                "test-" + key() + "@example.test"
        );
        jdbcTemplate.update("INSERT INTO og_compat.user_roles(user_id, role_id) SELECT ?, role_id FROM og_compat.roles WHERE role_name = ?", id, role);
        return id;
    }

    private Long createAddress(Long userId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.addresses(user_id, recipient_name, phone_number, province, district, ward, detail_address, is_default, ghn_province_id, ghn_district_id, ghn_ward_code) " +
                "VALUES (?, 'QA Recipient', '0901234567', 'TP Ho Chi Minh', 'Quan 1', 'Ben Nghe', '123 Le Loi', false, 201, 1442, '20101') RETURNING address_id",
                Long.class,
                userId
        );
    }

    private void ensureRewardAccountWithBalance(Long userId, long balance) {
        jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)", userId);
        if (balance > 0) {
            jdbcTemplate.update(
                    "INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key, created_at) " +
                    "VALUES (?, ?, ?, 'CORRECTION', ?, ?, 'Test balance grant', ?, CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), userId, canonicalPolicyId, balance, adminUserId, "GRANT-" + key()
            );
        }
    }

    private CheckoutGroupEntity createTestGroup(UUID groupId, Long buyerId, Long policyId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        CheckoutGroupEntity group = new CheckoutGroupEntity(
                groupId != null ? groupId : UUID.randomUUID(),
                buyerId,
                "CMD-" + key(),
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // 64-char sha256
                "{\"recipientName\": \"QA Recipient\"}",
                "CARRIER",
                "GHN",
                "VNPAY",
                new BigDecimal("200000.00"),
                "READY",
                now,
                now.plus(Duration.ofHours(1)), // checkout_groups_check requires payment_due_at = created_at + 1 hour
                policyId
        );
        return checkoutGroupRepository.save(group);
    }

    private OrderEntity createTestOrder(Long buyerId, Long sellerId, Long addressId) {
        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), buyerId, sellerId, addressId,
                "QA Recipient", "0901234567", "TP Ho Chi Minh", "Quan 1", "Ben Nghe", "123 Le Loi",
                new BigDecimal("100000.00"), new BigDecimal("20000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "PLATFORM", new BigDecimal("120000.00"),
                Instant.now().plus(Duration.ofHours(2)), Instant.now()
        );
        order.setWorkflowModel("LEGACY_V14");
        return orderRepository.save(order);
    }

    @BeforeEach
    void setUp() {
        adminUserId = createUser("ADMIN");
        buyerUserId = createUser("BUYER");
        sellerUserId = createUser("SELLER");

        canonicalPolicyId = jdbcTemplate.queryForObject(
                "SELECT public.og_provision_canonical_reward_policy(?)",
                Long.class,
                adminUserId
        );
    }

    @Test
    @DisplayName("DEC-10.6B: og_provision_canonical_reward_policy provisions canonically for admin, is idempotent, and rejects non-admin")
    void testCanonicalRewardPolicyProvisioning() {
        assertThat(canonicalPolicyId).isNotNull().isPositive();

        // Idempotency: calling again with same or another admin returns the same canonical policy_id
        Long secondCallId = jdbcTemplate.queryForObject(
                "SELECT public.og_provision_canonical_reward_policy(?)",
                Long.class,
                adminUserId
        );
        assertThat(secondCallId).isEqualTo(canonicalPolicyId);

        // Verification of policy values in business_policies / og_compat.reward_policies
        String code = jdbcTemplate.queryForObject(
                "SELECT policy_code FROM og_compat.reward_policies WHERE policy_id = ?",
                String.class,
                canonicalPolicyId
        );
        assertThat(code).isEqualTo("REWARD_CHECKOUT_STANDARD");

        BigDecimal pointValue = jdbcTemplate.queryForObject(
                "SELECT point_value FROM og_compat.reward_policies WHERE policy_id = ?",
                BigDecimal.class,
                canonicalPolicyId
        );
        assertThat(pointValue).isEqualByComparingTo("1.00");

        // Non-admin user provisioning must be rejected
        assertThatThrownBy(() -> jdbcTemplate.queryForObject(
                "SELECT public.og_provision_canonical_reward_policy(?)",
                Long.class,
                buyerUserId
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V47 Validator Chain: UC83 Group DEBIT_CHECKOUT succeeds when group_id is set and order_id is null")
    void testUc83GroupDebitCheckoutSuccess() {
        ensureRewardAccountWithBalance(buyerUserId, 50000L);
        UUID groupId = UUID.randomUUID();
        createTestGroup(groupId, buyerUserId, canonicalPolicyId);

        // Debit checkout at Group level (T2)
        jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, group_id, order_id, policy_id, command_key, created_at) " +
                "VALUES (?, ?, 'DEBIT_CHECKOUT', -20000, ?, NULL, ?, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerUserId, groupId, canonicalPolicyId, "DEBIT-" + key()
        );

        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE group_id = ? AND entry_type = 'DEBIT_CHECKOUT'",
                Long.class,
                groupId
        );
        assertThat(count).isEqualTo(1L);

        Long balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class,
                buyerUserId
        );
        assertThat(balance).isEqualTo(30000L);
    }

    @Test
    @DisplayName("V47 Validator Chain: Legacy V14 Order DEBIT_CHECKOUT succeeds when group_id is null and order_id is set")
    void testLegacyOrderDebitCheckoutSuccess() {
        ensureRewardAccountWithBalance(buyerUserId, 50000L);
        Long addressId = createAddress(buyerUserId);
        OrderEntity order = createTestOrder(buyerUserId, sellerUserId, addressId);

        // Debit checkout at Order level (Legacy flow)
        jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, group_id, order_id, policy_id, command_key, created_at) " +
                "VALUES (?, ?, 'DEBIT_CHECKOUT', -15000, NULL, ?, ?, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerUserId, order.getId(), canonicalPolicyId, "DEBIT-" + key()
        );

        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE order_id = ? AND entry_type = 'DEBIT_CHECKOUT'",
                Long.class,
                order.getId()
        );
        assertThat(count).isEqualTo(1L);

        Long balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class,
                buyerUserId
        );
        assertThat(balance).isEqualTo(35000L);
    }

    @Test
    @DisplayName("V47 Validator Chain: DEBIT_CHECKOUT rejects dual ownership (both group_id AND order_id)")
    void testDebitCheckoutRejectsDualOwnership() {
        ensureRewardAccountWithBalance(buyerUserId, 50000L);
        UUID groupId = UUID.randomUUID();
        createTestGroup(groupId, buyerUserId, canonicalPolicyId);
        Long addressId = createAddress(buyerUserId);
        OrderEntity order = createTestOrder(buyerUserId, sellerUserId, addressId);

        // Dual ownership (both group_id AND order_id) -> must be rejected (23514)
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, group_id, order_id, policy_id, command_key, created_at) " +
                "VALUES (?, ?, 'DEBIT_CHECKOUT', -10000, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerUserId, groupId, order.getId(), canonicalPolicyId, "DEBIT-" + key()
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V47 Validator Chain: DEBIT_CHECKOUT rejects zero ownership (neither group_id NOR order_id)")
    void testDebitCheckoutRejectsZeroOwnership() {
        ensureRewardAccountWithBalance(buyerUserId, 50000L);

        // Zero ownership (neither group_id NOR order_id) -> must be rejected (23514)
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, group_id, order_id, policy_id, command_key, created_at) " +
                "VALUES (?, ?, 'DEBIT_CHECKOUT', -10000, NULL, NULL, ?, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerUserId, canonicalPolicyId, "DEBIT-" + key()
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V47 Validator Chain: DEBIT_CHECKOUT requires non-null policy_id and non-blank command_key")
    void testDebitCheckoutRequiresPolicyIdAndCommandKey() {
        ensureRewardAccountWithBalance(buyerUserId, 50000L);
        UUID groupId = UUID.randomUUID();
        createTestGroup(groupId, buyerUserId, canonicalPolicyId);

        // Missing policy_id -> must be rejected (23502)
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, group_id, order_id, policy_id, command_key, created_at) " +
                "VALUES (?, ?, 'DEBIT_CHECKOUT', -10000, ?, NULL, NULL, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerUserId, groupId, "DEBIT-" + key()
        )).isInstanceOf(DataAccessException.class);

        // Blank command_key -> must be rejected
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, group_id, order_id, policy_id, command_key, created_at) " +
                "VALUES (?, ?, 'DEBIT_CHECKOUT', -10000, ?, NULL, ?, '   ', CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerUserId, groupId, canonicalPolicyId
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V47 Validator Chain: Preserves V44 CREDIT_REFUND validation (requires expires_at)")
    void testPreservedV44CreditRefundRequiresExpiresAt() {
        ensureRewardAccountWithBalance(buyerUserId, 50000L);
        Long addressId = createAddress(buyerUserId);
        OrderEntity order = createTestOrder(buyerUserId, sellerUserId, addressId);

        // CREDIT_REFUND without expires_at must fail with V44 check constraint (23514)
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, order_id, command_key, expires_at, created_at) " +
                "VALUES (?, ?, 'CREDIT_REFUND', 10000, ?, ?, NULL, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerUserId, order.getId(), "REFUND-" + key()
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V47 Validator Chain: CREDIT_REFUND succeeds when expires_at is present")
    void testPreservedV44CreditRefundSuccessWithExpiresAt() {
        ensureRewardAccountWithBalance(buyerUserId, 50000L);
        Long addressId = createAddress(buyerUserId);
        OrderEntity order = createTestOrder(buyerUserId, sellerUserId, addressId);

        // CREDIT_REFUND with expires_at must succeed
        jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, order_id, command_key, expires_at, created_at) " +
                "VALUES (?, ?, 'CREDIT_REFUND', 10000, ?, ?, CURRENT_TIMESTAMP + INTERVAL '30 days', CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerUserId, order.getId(), "REFUND-" + key()
        );

        Long balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class,
                buyerUserId
        );
        assertThat(balance).isEqualTo(60000L);
    }

    @Test
    @DisplayName("V47 Validator Chain: Balance guard prevents negative balance")
    void testBalanceGuardPreventsNegativeBalance() {
        ensureRewardAccountWithBalance(buyerUserId, 10000L);
        UUID groupId = UUID.randomUUID();
        createTestGroup(groupId, buyerUserId, canonicalPolicyId);

        // Attempt to debit 20,000 when balance is only 10,000 -> must fail with balance guard (23514)
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, group_id, order_id, policy_id, command_key, created_at) " +
                "VALUES (?, ?, 'DEBIT_CHECKOUT', -20000, ?, NULL, ?, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), buyerUserId, groupId, canonicalPolicyId, "DEBIT-" + key()
        )).isInstanceOf(DataAccessException.class);
    }

    private void runSeedScript() {
        try {
            String seedSql = java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/resources/db/seed/R__provision_canonical_reward_policy.sql")
            );
            jdbcTemplate.execute(seedSql);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read seed file", e);
        }
    }

    @Test
    @DisplayName("P0-1 Test 1: Seed script succeeds with validly configured Admin ID")
    void testSeedSucceedsWithValidlyConfiguredAdminId() {
        jdbcTemplate.execute("SET app.reward_policy_admin_id = '" + adminUserId + "'");
        runSeedScript();

        Long policyId = jdbcTemplate.queryForObject(
                "SELECT policy_id FROM og_compat.reward_policies WHERE policy_code = 'REWARD_CHECKOUT_STANDARD' ORDER BY policy_id ASC LIMIT 1",
                Long.class
        );
        assertThat(policyId).isNotNull().isEqualTo(canonicalPolicyId);
    }

    @Test
    @DisplayName("P0-1 Test 2: Seed script fails-closed when Admin ID configuration is missing")
    void testSeedFailsWhenMissingAdminId() {
        jdbcTemplate.execute("RESET app.reward_policy_admin_id");
        assertThatThrownBy(this::runSeedScript)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Missing explicit deployment configuration for Admin ID");
    }

    @Test
    @DisplayName("P0-1 Test 3a: Seed script fails-closed when Admin does not exist")
    void testSeedFailsWhenAdminDoesNotExist() {
        jdbcTemplate.execute("SET app.reward_policy_admin_id = '99999999'");
        assertThatThrownBy(this::runSeedScript)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("is not an active, non-deleted ADMIN");
    }

    @Test
    @DisplayName("P0-1 Test 3b: Seed script fails-closed when user lacks ROLE_ADMIN")
    void testSeedFailsWhenUserLacksAdminRole() {
        jdbcTemplate.execute("SET app.reward_policy_admin_id = '" + buyerUserId + "'");
        assertThatThrownBy(this::runSeedScript)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("is not an active, non-deleted ADMIN");
    }

    @Test
    @DisplayName("P0-1 Test 3c: Seed script fails-closed when Admin status is not ACTIVE")
    void testSeedFailsWhenAdminIsInactive() {
        Long inactiveAdminId = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name, phone_number, status, identity_workflow) " +
                "VALUES (?, 'FAKE', 'Inactive Admin', '0900000001', 'SUSPENDED', 'UC83') RETURNING user_id",
                Long.class,
                "inactive-admin-" + key() + "@example.test"
        );
        jdbcTemplate.update("INSERT INTO og_compat.user_roles(user_id, role_id) SELECT ?, role_id FROM og_compat.roles WHERE role_name = 'ADMIN'", inactiveAdminId);

        jdbcTemplate.execute("SET app.reward_policy_admin_id = '" + inactiveAdminId + "'");
        assertThatThrownBy(this::runSeedScript)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("is not an active, non-deleted ADMIN");
    }
}
