package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/**
 * D-08-IDEMP-01 / Option 3: Minimal Dedicated Idempotency Table Integration Test.
 *
 * Implements the 6 required validation scenarios for real PostgreSQL database environments:
 * 1. Concurrent Same-Key Same-Payload
 * 2. Concurrent Same-Key Different-Payload
 * 3. Lock Wait & Commit
 * 4. Rollback & Recovery
 * 5. Client Retry Post-Commit (Replay 200 OK)
 * 6. Multi-Order Replay (1:N)
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("CheckoutIdempotencyPostgresTest - PostgreSQL Integration Verification of D-08-IDEMP-01 (Option 3)")
class CheckoutIdempotencyPostgresTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        String user = System.getenv("OGSHOP_TEST_DB_USER");
        String pass = System.getenv("OGSHOP_TEST_DB_PASSWORD");

        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> pass);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO og_compat,public");
        registry.add("spring.flyway.enabled", PostgresTestSafetyGuard::isFlywayOptInEnabled);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeAll
    static void checkDatabaseConnection() {
        PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
    }

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private CatalogCommerceFacade catalogCommerceFacade;

    private Long createBuyer(String email) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, '{noop}test-pass', 'Idemp Buyer') RETURNING user_id",
                Long.class,
                email
        );
        jdbcTemplate.update("INSERT INTO og_compat.user_roles(user_id, role_id) SELECT ?, role_id FROM og_compat.roles WHERE role_name = 'BUYER'", id);
        return id;
    }

    private Long createSeller(String email) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, '{noop}test-pass', 'Idemp Seller') RETURNING user_id",
                Long.class,
                email
        );
        jdbcTemplate.update("INSERT INTO og_compat.user_roles(user_id, role_id) SELECT ?, role_id FROM og_compat.roles WHERE role_name = 'SELLER'", id);
        return id;
    }

    private Long createAdmin(String email) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, '{noop}test-pass', 'Idemp Admin') RETURNING user_id",
                Long.class,
                email
        );
        jdbcTemplate.update("INSERT INTO og_compat.user_roles(user_id, role_id) SELECT ?, role_id FROM og_compat.roles WHERE role_name = 'ADMIN'", id);
        return id;
    }

    private Long createCategory() {
        List<Long> cats = jdbcTemplate.queryForList("SELECT category_id FROM og_compat.categories LIMIT 1", Long.class);
        if (!cats.isEmpty()) {
            return cats.get(0);
        }
        return jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.categories(name, slug) VALUES ('Test Cat', ?) RETURNING category_id",
                Long.class,
                "cat-" + UUID.randomUUID()
        );
    }

    private Long createProduct(Long sellerId, Long categoryId, BigDecimal price) {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        return tt.execute(status -> {
            Long pId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.products(seller_id, category_id, title, description, listed_price, condition, status, workflow_model) " +
                            "VALUES (?, ?, 'Product Test', 'Desc', ?, 'LIKE_NEW', 'ACTIVE', 'LEGACY_V14') RETURNING product_id",
                    Long.class,
                    sellerId, categoryId, price
            );
            jdbcTemplate.update("INSERT INTO og_compat.product_categories(product_id, category_id) VALUES (?, ?)", pId, categoryId);
            return pId;
        });
    }

    private Long createAddress(Long userId, boolean isDefault) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.addresses(user_id, recipient_name, phone_number, province, district, ward, detail_address, is_default, ghn_province_id, ghn_district_id, ghn_ward_code) " +
                        "VALUES (?, 'Nguyen Van A', '0901234567', 'TP Ho Chi Minh', 'Quan 1', 'Phuong Ben Nghe', '123 Le Loi', ?, 201, 1442, '20101') RETURNING address_id",
                Long.class,
                userId, isDefault
        );
    }

    private void seedRewardBalance(Long buyerId, Long actorId, long amount) {
        List<Long> policies = jdbcTemplate.queryForList("SELECT policy_id FROM og_compat.reward_policies LIMIT 1", Long.class);
        Long policyId;
        if (!policies.isEmpty()) {
            policyId = policies.get(0);
        } else {
            policyId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.reward_policies(policy_code, point_value, policy_snapshot, created_by) VALUES (?, 1, '{}', ?) RETURNING policy_id",
                    Long.class,
                    "SEED_POL_" + UUID.randomUUID(), actorId
            );
        }
        if (jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId) == 0) {
            jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)", buyerId);
        }
        jdbcTemplate.update(
                "INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key) " +
                        "VALUES (?, ?, ?, 'CORRECTION', ?, ?, 'Initial seed balance', ?)",
                UUID.randomUUID(), buyerId, policyId, amount, actorId, "SEED:" + UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("Scenario 1: Concurrent Same-Key Same-Payload claims once and replays without duplicate order/coin debit")
    void scenario1_concurrentSameKeySamePayload() throws Exception {
        Long buyerId = createBuyer("buyer1_" + UUID.randomUUID() + "@test.local");
        Long sellerId = createSeller("seller1_" + UUID.randomUUID() + "@test.local");
        Long adminId = createAdmin("admin1_" + UUID.randomUUID() + "@test.local");
        Long categoryId = createCategory();
        Long productId = createProduct(sellerId, categoryId, new BigDecimal("100000"));
        Long addressId = createAddress(buyerId, true);
        createAddress(sellerId, true);

        // Ensure reward balance
        seedRewardBalance(buyerId, adminId, 50000L);

        String cmdKey = "CMD-" + UUID.randomUUID();
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(productId, addressId, null, null, null, 0L, cmdKey);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        Callable<CheckoutDtos.BuyNowExecutionResult> task = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return checkoutService.executeBuyNow(buyerId, request);
        };

        Future<CheckoutDtos.BuyNowExecutionResult> f1 = executor.submit(task);
        Future<CheckoutDtos.BuyNowExecutionResult> f2 = executor.submit(task);

        CheckoutDtos.BuyNowExecutionResult res1 = f1.get(10, TimeUnit.SECONDS);
        CheckoutDtos.BuyNowExecutionResult res2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one created, exactly one replayed
        assertThat(res1.replayed() ^ res2.replayed()).isTrue();
        assertThat(res1.response().orderId()).isEqualTo(res2.response().orderId());

        // Verify only 1 order created in DB
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.orders WHERE buyer_id = ? AND checkout_group_id = CAST(? AS uuid)",
                Integer.class,
                buyerId, res1.response().checkoutGroupId()
        );
        assertThat(orderCount).isEqualTo(1);

        // Verify no unexpected duplicate debit
        Integer debitCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE user_id = ? AND entry_type = 'DEBIT_CHECKOUT' AND order_id = ?",
                Integer.class,
                buyerId, res1.response().orderId()
        );
        assertThat(debitCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario 2: Concurrent Same-Key Different-Payload throws 409 COMMAND_KEY_CONFLICT for subsequent attempt")
    void scenario2_concurrentSameKeyDifferentPayload() {
        Long buyerId = createBuyer("buyer2_" + UUID.randomUUID() + "@test.local");
        Long sellerId = createSeller("seller2_" + UUID.randomUUID() + "@test.local");
        Long categoryId = createCategory();
        Long productId = createProduct(sellerId, categoryId, new BigDecimal("100000"));
        Long addressId = createAddress(buyerId, true);
        createAddress(sellerId, true);

        String cmdKey = "CMD-" + UUID.randomUUID();
        CheckoutDtos.BuyNowRequest reqA = new CheckoutDtos.BuyNowRequest(productId, addressId, null, null, null, 0L, cmdKey);
        CheckoutDtos.BuyNowRequest reqB = new CheckoutDtos.BuyNowRequest(productId, addressId, null, null, null, 10000L, cmdKey);

        // First attempt succeeds
        CheckoutDtos.BuyNowExecutionResult resA = checkoutService.executeBuyNow(buyerId, reqA);
        assertThat(resA.replayed()).isFalse();

        // Second attempt with different payload throws 409
        assertThatThrownBy(() -> checkoutService.executeBuyNow(buyerId, reqB))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMMAND_KEY_CONFLICT");
    }

    @Test
    @DisplayName("Scenario 3: Lock Wait & Commit - Second transaction waits and retrieves replay after first commits")
    void scenario3_lockWaitAndCommit() throws Exception {
        Long buyerId = createBuyer("buyer3_" + UUID.randomUUID() + "@test.local");
        String cmdKey = "CMD-" + UUID.randomUUID();
        UUID groupId = UUID.nameUUIDFromBytes(("CHECKOUT:" + buyerId + ":" + cmdKey).getBytes(StandardCharsets.UTF_8));
        String requestHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        CountDownLatch tx1InsertedLatch = new CountDownLatch(1);
        CountDownLatch tx2StartedLatch = new CountDownLatch(1);
        CountDownLatch tx1CanCommitLatch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Tx1: Inserts into checkout_idempotency_entries and holds transaction
        Future<Boolean> tx1 = executor.submit(() -> {
            TransactionTemplate tt1 = new TransactionTemplate(transactionManager);
            tt1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return tt1.execute(status -> {
                jdbcTemplate.update(
                        "INSERT INTO public.checkout_idempotency_entries (buyer_id, command_key, request_hash, checkout_group_id, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                        buyerId, cmdKey, requestHash, groupId
                );
                tx1InsertedLatch.countDown();
                try {
                    tx1CanCommitLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return true;
            });
        });

        // Tx2: Tries ON CONFLICT DO NOTHING while Tx1 has row uncommitted
        Future<List<Long>> tx2 = executor.submit(() -> {
            tx1InsertedLatch.await(5, TimeUnit.SECONDS);
            TransactionTemplate tt2 = new TransactionTemplate(transactionManager);
            tt2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return tt2.execute(status -> {
                // In Postgres READ COMMITTED, ON CONFLICT DO NOTHING waits for Tx1 to commit or abort
                // Release Tx1 immediately after Tx2 is inside the query call
                executor.submit(() -> {
                    try {
                        Thread.sleep(100);
                        tx1CanCommitLatch.countDown();
                    } catch (InterruptedException ignored) {}
                });
                return jdbcTemplate.query(
                        "INSERT INTO public.checkout_idempotency_entries (buyer_id, command_key, request_hash, checkout_group_id, created_at) " +
                                "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT (buyer_id, command_key) DO NOTHING RETURNING buyer_id",
                        (rs, rowNum) -> rs.getLong(1),
                        buyerId, cmdKey, requestHash, groupId
                );
            });
        });

        Boolean tx1Result = tx1.get(10, TimeUnit.SECONDS);
        List<Long> tx2Claimed = tx2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(tx1Result).isTrue();
        assertThat(tx2Claimed).isEmpty(); // Tx2 lost race, claimed is empty, enabling replay path
    }

    @Test
    @DisplayName("Scenario 4: Rollback & Recovery - Aborted transaction rolls back idempotency entry, allowing subsequent retry")
    void scenario4_rollbackAndRecovery() {
        Long buyerId = createBuyer("buyer4_" + UUID.randomUUID() + "@test.local");
        String cmdKey = "CMD-" + UUID.randomUUID();
        UUID groupId = UUID.nameUUIDFromBytes(("CHECKOUT:" + buyerId + ":" + cmdKey).getBytes(StandardCharsets.UTF_8));
        String requestHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // Tx1: Fails and rolls back
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            tt.execute(status -> {
                jdbcTemplate.update(
                        "INSERT INTO public.checkout_idempotency_entries (buyer_id, command_key, request_hash, checkout_group_id, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                        buyerId, cmdKey, requestHash, groupId
                );
                throw new RuntimeException("Simulated mid-flight checkout failure (e.g. inventory contention)");
            });
        } catch (RuntimeException ignored) {}

        // Verify row was rolled back and is NOT present in DB
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.checkout_idempotency_entries WHERE buyer_id = ? AND command_key = ?",
                Integer.class,
                buyerId, cmdKey
        );
        assertThat(count).isEqualTo(0);

        // Subsequent retry can claim the key without conflict
        List<Long> claimed = jdbcTemplate.query(
                "INSERT INTO public.checkout_idempotency_entries (buyer_id, command_key, request_hash, checkout_group_id, created_at) " +
                        "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT (buyer_id, command_key) DO NOTHING RETURNING buyer_id",
                (rs, rowNum) -> rs.getLong(1),
                buyerId, cmdKey, requestHash, groupId
        );
        assertThat(claimed).containsExactly(buyerId);
    }

    @Test
    @DisplayName("Scenario 5: Client Retry Post-Commit - Replays existing order with replayed=true")
    void scenario5_clientRetryPostCommit() {
        Long buyerId = createBuyer("buyer5_" + UUID.randomUUID() + "@test.local");
        Long sellerId = createSeller("seller5_" + UUID.randomUUID() + "@test.local");
        Long categoryId = createCategory();
        Long productId = createProduct(sellerId, categoryId, new BigDecimal("120000"));
        Long addressId = createAddress(buyerId, true);
        createAddress(sellerId, true);

        String cmdKey = "CMD-" + UUID.randomUUID();
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(productId, addressId, null, null, null, 0L, cmdKey);

        // Initial submission (201 Created)
        CheckoutDtos.BuyNowExecutionResult initial = checkoutService.executeBuyNow(buyerId, request);
        assertThat(initial.replayed()).isFalse();

        // Idempotent retry (200 OK Replay)
        CheckoutDtos.BuyNowExecutionResult retry = checkoutService.executeBuyNow(buyerId, request);
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.response().orderId()).isEqualTo(initial.response().orderId());
        assertThat(retry.response().checkoutGroupId()).isEqualTo(initial.response().checkoutGroupId());
    }

    @Test
    @DisplayName("Scenario 6: Multi-Order Replay (1:N) - Replays all orders for the checkout group in ascending order and enforces tenant isolation")
    void scenario6_multiOrderReplay() {
        Long buyerId = createBuyer("buyer6_" + UUID.randomUUID() + "@test.local");
        Long otherBuyerId = createBuyer("other6_" + UUID.randomUUID() + "@test.local");
        Long sellerId = createSeller("seller6_" + UUID.randomUUID() + "@test.local");
        Long sellerAddressId = createAddress(sellerId, true);
        String cmdKey = "CMD-" + UUID.randomUUID();
        UUID groupId = UUID.nameUUIDFromBytes(("CHECKOUT:" + buyerId + ":" + cmdKey).getBytes(StandardCharsets.UTF_8));
        String requestHash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

        // Seed idempotency entry
        jdbcTemplate.update(
                "INSERT INTO public.checkout_idempotency_entries (buyer_id, command_key, request_hash, checkout_group_id, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                buyerId, cmdKey, requestHash, groupId
        );

        Instant now = Instant.now();
        Instant due = now.plus(Duration.ofHours(1));
        OrderEntity o1 = orderRepository.save(new OrderEntity(
                groupId, buyerId, sellerId, sellerAddressId,
                "Buyer 6", "0901112222", "TP Ho Chi Minh", "Quan 1", "Phuong Ben Nghe", "123 Test St",
                201, 1442, "20101",
                new BigDecimal("100000"), new BigDecimal("20000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                "PLATFORM", new BigDecimal("120000"), due, now
        ));
        OrderEntity o2 = orderRepository.save(new OrderEntity(
                groupId, buyerId, sellerId, sellerAddressId,
                "Buyer 6", "0901112222", "TP Ho Chi Minh", "Quan 1", "Phuong Ben Nghe", "456 Test St",
                201, 1442, "20101",
                new BigDecimal("200000"), new BigDecimal("30000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200000"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                "PLATFORM", new BigDecimal("230000"), due, now
        ));

        // Record pre-replay order count and coin debit count
        Integer preReplayOrderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.orders WHERE checkout_group_id = ?",
                Integer.class, groupId
        );
        Integer preReplayDebitCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE user_id = ? AND entry_type = 'DEBIT_CHECKOUT'",
                Integer.class, buyerId
        );

        // Verify replayOrdersByGroupId retrieves orders ordered by order_id ASC
        CheckoutDtos.OrderCreatedResponse replayed = checkoutService.replayOrdersByGroupId(buyerId, groupId);
        assertThat(replayed).isNotNull();
        assertThat(replayed.orderId()).isEqualTo(Math.min(o1.getId(), o2.getId()));
        assertThat(replayed.orders()).isNotNull().hasSize(2);
        assertThat(replayed.orders().stream().map(CheckoutDtos.OrderCreatedResponse::orderId))
                .containsExactly(Math.min(o1.getId(), o2.getId()), Math.max(o1.getId(), o2.getId()));

        // Verify no additional order or coin debit was created when replaying
        Integer postReplayOrderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.orders WHERE checkout_group_id = ?",
                Integer.class, groupId
        );
        Integer postReplayDebitCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE user_id = ? AND entry_type = 'DEBIT_CHECKOUT'",
                Integer.class, buyerId
        );
        assertThat(postReplayOrderCount).isEqualTo(preReplayOrderCount);
        assertThat(postReplayDebitCount).isEqualTo(preReplayDebitCount);

        // Tenant isolation: querying with other buyer's id fails at service boundary
        assertThatThrownBy(() -> checkoutService.replayOrdersByGroupId(otherBuyerId, groupId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không tìm thấy đơn hàng cho phiên checkout đã xác nhận");

        // Tenant isolation at database query level:
        // Both JPA repository and JDBC queries return empty for other buyer
        List<OrderEntity> otherBuyerOrdersJPA = orderRepository.findByCheckoutGroupIdAndBuyerIdOrderByIdAsc(groupId, otherBuyerId);
        assertThat(otherBuyerOrdersJPA).isEmpty();

        Integer otherBuyerOrdersJDBC = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.orders WHERE checkout_group_id = ? AND buyer_id = ?",
                Integer.class, groupId, otherBuyerId
        );
        assertThat(otherBuyerOrdersJDBC).isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario 7: Real Checkout Transaction Atomicity Rollback and Recovery - Mid-flight exception rolls back JPA, JDBC and Coin Debit")
    void scenario7_realCheckoutTransactionAtomicityRollbackAndRecovery() {
        Long buyerId = createBuyer("buyer7_" + UUID.randomUUID() + "@test.local");
        Long sellerId = createSeller("seller7_" + UUID.randomUUID() + "@test.local");
        Long adminId = createAdmin("admin7_" + UUID.randomUUID() + "@test.local");
        Long categoryId = createCategory();
        Long productId = createProduct(sellerId, categoryId, new BigDecimal("150000"));
        Long addressId = createAddress(buyerId, true);
        createAddress(sellerId, true);

        // Seed initial Coin balance for buyer: 50,000 coins
        seedRewardBalance(buyerId, adminId, 50000L);

        // Record pre-checkout state
        Long preBalanceAccount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class, buyerId
        );
        Long preBalanceLedger = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(points_delta), 0) FROM og_compat.reward_ledger WHERE user_id = ?",
                Long.class, buyerId
        );
        Integer preLedgerCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE user_id = ?",
                Integer.class, buyerId
        );
        assertThat(preBalanceAccount).isEqualTo(50000L);
        assertThat(preBalanceLedger).isEqualTo(50000L);

        jdbcTemplate.queryForObject(
                "SELECT public.og_provision_canonical_reward_policy(?)",
                Long.class,
                adminId
        );

        String cmdKey = "CMD-ATOMIC-" + UUID.randomUUID();
        long coinsToRedeem = 5000L;
        System.setProperty("app.reward.canonical-policy-code", "REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("50");
        checkoutService.setCapPoApproved(true);
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(productId, addressId, null, null, null, coinsToRedeem, cmdKey);

        try {
            // Spy: Throw mid-flight exception at reserveProduct (after idempotency entry, Order, OrderItem, and Coin Debit, before TX commit)
            doThrow(new RuntimeException("Simulated mid-flight crash before transaction commit"))
                    .when(catalogCommerceFacade).reserveProduct(anyLong(), anyLong(), any(), any());

            assertThatThrownBy(() -> checkoutService.executeBuyNow(buyerId, request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Simulated mid-flight crash before transaction commit");

        // Verify total atomic rollback on real PostgreSQL database:
        // 1. Idempotency entry was rolled back (0 rows)
        Integer idempCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.checkout_idempotency_entries WHERE buyer_id = ? AND command_key = ?",
                Integer.class, buyerId, cmdKey
        );
        assertThat(idempCount).isEqualTo(0);

        // 2. Order was rolled back (0 rows)
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.orders WHERE buyer_id = ?",
                Integer.class, buyerId
        );
        assertThat(orderCount).isEqualTo(0);

        // 3. Order items were rolled back (0 rows)
        Integer itemCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.order_items oi JOIN og_compat.orders o ON oi.order_id = o.order_id WHERE o.buyer_id = ?",
                Integer.class, buyerId
        );
        assertThat(itemCount).isEqualTo(0);

        // 4. DEBIT_CHECKOUT entry in reward_ledger was rolled back (0 rows)
        Integer debitCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE user_id = ? AND entry_type = 'DEBIT_CHECKOUT'",
                Integer.class, buyerId
        );
        assertThat(debitCount).isEqualTo(0);

        // 5. Total reward_ledger count is unchanged
        Integer rolledBackLedgerCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE user_id = ?",
                Integer.class, buyerId
        );
        assertThat(rolledBackLedgerCount).isEqualTo(preLedgerCount);

        // 6. Buyer Coin balance is exactly restored to pre-transaction value
        Long postRollbackBalanceAccount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class, buyerId
        );
        Long postRollbackBalanceLedger = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(points_delta), 0) FROM og_compat.reward_ledger WHERE user_id = ?",
                Long.class, buyerId
        );
        assertThat(postRollbackBalanceAccount).isEqualTo(preBalanceAccount);
        assertThat(postRollbackBalanceLedger).isEqualTo(preBalanceLedger);

        // Reset spy to allow normal execution
        doCallRealMethod().when(catalogCommerceFacade).reserveProduct(anyLong(), anyLong(), any(), any());

        // Subsequent retry with the exact same commandKey and payload succeeds
        CheckoutDtos.BuyNowExecutionResult retryResult = checkoutService.executeBuyNow(buyerId, request);
        assertThat(retryResult).isNotNull();
        assertThat(retryResult.replayed()).isFalse();
        assertThat(retryResult.response().orderId()).isNotNull();

        // Verify committed state on PostgreSQL after successful retry:
        // 1. Exactly one idempotency entry
        Integer committedIdemp = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.checkout_idempotency_entries WHERE buyer_id = ? AND command_key = ?",
                Integer.class, buyerId, cmdKey
        );
        assertThat(committedIdemp).isEqualTo(1);

        // 2. Exactly one Order
        Integer committedOrder = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.orders WHERE buyer_id = ?",
                Integer.class, buyerId
        );
        assertThat(committedOrder).isEqualTo(1);

        // 3. Exactly one Order Item
        Integer committedItems = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.order_items oi JOIN og_compat.orders o ON oi.order_id = o.order_id WHERE o.buyer_id = ?",
                Integer.class, buyerId
        );
        assertThat(committedItems).isEqualTo(1);

        // 4. Exactly one DEBIT_CHECKOUT entry for this order
        Integer committedDebits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE user_id = ? AND entry_type = 'DEBIT_CHECKOUT'",
                Integer.class, buyerId
        );
        assertThat(committedDebits).isEqualTo(1);

        Long debitedPoints = jdbcTemplate.queryForObject(
                "SELECT points_delta FROM og_compat.reward_ledger WHERE user_id = ? AND entry_type = 'DEBIT_CHECKOUT'",
                Long.class, buyerId
        );
        assertThat(debitedPoints).isEqualTo(-coinsToRedeem);

        // 5. Coin balance reflects exactly one redemption
        Long finalBalanceAccount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class, buyerId
        );
        Long finalBalanceLedger = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(points_delta), 0) FROM og_compat.reward_ledger WHERE user_id = ?",
                Long.class, buyerId
        );
        assertThat(finalBalanceAccount).isEqualTo(preBalanceAccount - coinsToRedeem);
        assertThat(finalBalanceLedger).isEqualTo(preBalanceLedger - coinsToRedeem);
        } finally {
            System.clearProperty("app.reward.canonical-policy-code");
            checkoutService.setMaxRedemptionPercentage(null);
            checkoutService.setCapPoApproved(false);
        }
    }
}

