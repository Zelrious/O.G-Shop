package com.oldbutgold.shop.modules.fulfillment.application;

import com.oldbutgold.shop.modules.commerce.application.FulfillmentCommerceFacade;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import jakarta.persistence.EntityManager;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
class FulfillmentStateIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("OGSHOP_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private FulfillmentStateService fulfillmentStateService;

    @Autowired
    private FulfillmentCommerceFacade commerceFacade;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private final List<Long> createdOrderIds = new CopyOnWriteArrayList<>();
    private final List<Long> createdUserIds = new CopyOnWriteArrayList<>();

    private Long buyerId;
    private Long sellerId;

    @BeforeEach
    void setUp() {
        buyerId = createTestUser("buyer");
        sellerId = createTestUser("seller");
    }

    @AfterEach
    void tearDown() {
        try {
            if (!createdOrderIds.isEmpty()) {
                String orderInPlaceholders = String.join(",", Collections.nCopies(createdOrderIds.size(), "?"));
                jdbcTemplate.update("DELETE FROM shipments WHERE order_id IN (" + orderInPlaceholders + ")", createdOrderIds.toArray());
                jdbcTemplate.update("DELETE FROM orders WHERE order_id IN (" + orderInPlaceholders + ")", createdOrderIds.toArray());
            }
            if (!createdUserIds.isEmpty()) {
                String userInPlaceholders = String.join(",", Collections.nCopies(createdUserIds.size(), "?"));
                jdbcTemplate.update("DELETE FROM users WHERE user_id IN (" + userInPlaceholders + ")", createdUserIds.toArray());
            }
        } finally {
            createdOrderIds.clear();
            createdUserIds.clear();
        }
    }

    private Long createTestUser(String prefix) {
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO users(email, password_hash, full_name) VALUES (?, 'fixture_pass', ?) RETURNING user_id",
                Long.class,
                prefix + "_" + UUID.randomUUID() + "@example.test",
                "User " + prefix
        );
        createdUserIds.add(userId);
        return userId;
    }

    private Long createTestOrder(String status) {
        Long orderId = jdbcTemplate.queryForObject("""
                INSERT INTO orders(
                    checkout_group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, shipping_fee, total_amount, buyer_system_fee, seller_system_fee, seller_proceeds,
                    status, payment_due_at
                ) VALUES (
                    ?, ?, ?, 'Recipient', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    100000, 0, 100000, 0, 0, 100000,
                    ?, NOW() + INTERVAL '1 hour'
                ) RETURNING order_id
                """,
                Long.class,
                UUID.randomUUID(), buyerId, sellerId, status
        );
        createdOrderIds.add(orderId);
        return orderId;
    }

    private ShipmentEntity createTestShipment(Long orderId, String trackingNumber, String clientOrderCode, String status) {
        Instant now = Instant.now();
        ShipmentEntity shipment = new ShipmentEntity(orderId, clientOrderCode, now);
        shipment.setTrackingNumber(trackingNumber);
        if ("PICKED_UP".equals(status)) {
            shipment.markPickedUp(now, "picked", now);
        } else if ("IN_TRANSIT".equals(status)) {
            shipment.markInTransit(now, "transporting", now);
        } else if ("RETURNED".equals(status)) {
            shipment.markReturned(now, now);
        } else if ("FAILED".equals(status)) {
            shipment.markFailed("damage", now);
        }
        return shipmentRepository.saveAndFlush(shipment);
    }

    @Test
    @DisplayName("1. Atomic success: Order và Shipment cùng cập nhật sang DELIVERED trong một transaction")
    void testAtomicSuccessUpdatesBothEntities() {
        Long orderId = createTestOrder("SELLER_CONFIRMED");
        String trackingNumber = "GHN_TRACK_" + UUID.randomUUID().toString().substring(0, 8);
        String clientCode = "OG_CLIENT_" + UUID.randomUUID().toString().substring(0, 8);
        createTestShipment(orderId, trackingNumber, clientCode, "PICKED_UP");

        Instant deliveryTime = Instant.now();
        fulfillmentStateService.applyCarrierDelivered(trackingNumber, deliveryTime, "delivered");

        // Đọc lại từ DB để kiểm tra tính nhất quán
        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId);
        String shipmentStatus = jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE order_id = ?", String.class, orderId);
        Timestamp shipmentDelivered = jdbcTemplate.queryForObject("SELECT delivered_at FROM shipments WHERE order_id = ?", Timestamp.class, orderId);

        assertThat(orderStatus).isEqualTo("DELIVERED");
        assertThat(shipmentStatus).isEqualTo("DELIVERED");
        assertThat(shipmentDelivered).isNotNull();
    }

    @Test
    @DisplayName("2. Full rollback: Lỗi phát sinh trong transaction khiến cả Order và Shipment cùng rollback về trạng thái ban đầu")
    void testFullRollbackWhenExceptionOccursBeforeCommit() {
        Long orderId = createTestOrder("SELLER_CONFIRMED");
        String trackingNumber = "GHN_TRACK_" + UUID.randomUUID().toString().substring(0, 8);
        String clientCode = "OG_CLIENT_" + UUID.randomUUID().toString().substring(0, 8);
        createTestShipment(orderId, trackingNumber, clientCode, "PICKED_UP");

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.execute(status -> {
            // Thực hiện chuyển đổi trạng thái (cả hai entity đều được cập nhật trong persistence context)
            fulfillmentStateService.applyCarrierDelivered(trackingNumber, Instant.now(), "delivered");
            entityManager.flush();

            // Cố tình ném exception trước khi commit transaction bên ngoài
            throw new RuntimeException("Simulated failure right before commit");
        })).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated failure right before commit");

        // Đọc lại độc lập bằng JDBC ngoài transaction để chứng minh rollback toàn phần
        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId);
        String shipmentStatus = jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE order_id = ?", String.class, orderId);
        Timestamp shipmentDelivered = jdbcTemplate.queryForObject("SELECT delivered_at FROM shipments WHERE order_id = ?", Timestamp.class, orderId);

        assertThat(orderStatus).isEqualTo("SELLER_CONFIRMED");
        assertThat(shipmentStatus).isEqualTo("PICKED_UP");
        assertThat(shipmentDelivered).isNull();
    }

    @Test
    @DisplayName("3. Concurrent access: Hai thao tác đồng thời tuân thủ thứ tự khóa Order -> Shipment và kết thúc hợp lệ")
    void testConcurrentAccessFollowsLockOrdering() throws Exception {
        Long orderId = createTestOrder("SELLER_CONFIRMED");
        String trackingNumber = "GHN_TRACK_" + UUID.randomUUID().toString().substring(0, 8);
        String clientCode = "OG_CLIENT_" + UUID.randomUUID().toString().substring(0, 8);
        createTestShipment(orderId, trackingNumber, clientCode, "PENDING");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<Void> taskPickedUp = () -> {
            latch.await();
            fulfillmentStateService.applyCarrierPickedUp(trackingNumber, Instant.now(), "picked");
            return null;
        };

        Callable<Void> taskDelivered = () -> {
            latch.await();
            fulfillmentStateService.applyCarrierDelivered(trackingNumber, Instant.now(), "delivered");
            return null;
        };

        Future<Void> f1 = executor.submit(taskPickedUp);
        Future<Void> f2 = executor.submit(taskDelivered);

        latch.countDown(); // Kích hoạt 2 luồng đồng thời

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Trạng thái cuối cùng phải là DELIVERED cho cả hai
        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId);
        String shipmentStatus = jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE order_id = ?", String.class, orderId);

        assertThat(orderStatus).isEqualTo("DELIVERED");
        assertThat(shipmentStatus).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("4. Tracking mismatch: Từ chối cập nhật khi mã vận đơn không khớp và không thay đổi dữ liệu")
    void testTrackingMismatchRejectsUpdate() {
        Long orderId = createTestOrder("SELLER_CONFIRMED");
        String correctTracking = "GHN_CORRECT_" + UUID.randomUUID().toString().substring(0, 8);
        String wrongTracking = "GHN_WRONG_" + UUID.randomUUID().toString().substring(0, 8);
        String clientCode = "OG_CLIENT_" + UUID.randomUUID().toString().substring(0, 8);
        createTestShipment(orderId, correctTracking, clientCode, "PICKED_UP");

        assertThatThrownBy(() -> fulfillmentStateService.applyCarrierDelivered(wrongTracking, Instant.now(), "delivered"))
                .isInstanceOf(Exception.class);

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId);
        String shipmentStatus = jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE order_id = ?", String.class, orderId);

        assertThat(orderStatus).isEqualTo("SELLER_CONFIRMED");
        assertThat(shipmentStatus).isEqualTo("PICKED_UP");
    }

    @Test
    @DisplayName("5. Terminal conflict: Không chuyển Order sang DELIVERED khi Shipment đã RETURNED hoặc FAILED")
    void testTerminalConflictPreventsDeliveredTransition() {
        Long orderIdReturned = createTestOrder("SELLER_CONFIRMED");
        String trackingReturned = "GHN_RET_" + UUID.randomUUID().toString().substring(0, 8);
        String clientReturned = "OG_CLIENT_RET_" + UUID.randomUUID().toString().substring(0, 8);
        createTestShipment(orderIdReturned, trackingReturned, clientReturned, "RETURNED");

        assertThatThrownBy(() -> fulfillmentStateService.applyCarrierDelivered(trackingReturned, Instant.now(), "delivered"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trạng thái xung đột");

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderIdReturned);
        String shipmentStatus = jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE order_id = ?", String.class, orderIdReturned);

        assertThat(orderStatus).isEqualTo("SELLER_CONFIRMED");
        assertThat(shipmentStatus).isEqualTo("RETURNED");
    }

    @Test
    @DisplayName("6. Concurrent Webhook & Reconciliation: Webhook processor và Reconciliation scheduler cùng cập nhật đồng thời cùng 1 shipment trên PostgreSQL thật")
    void testConcurrentWebhookAndReconciliation() throws Exception {
        Long orderId = createTestOrder("SELLER_CONFIRMED");
        String trackingNumber = "GHN_CONC_" + UUID.randomUUID().toString().substring(0, 8);
        String clientCode = "OG_CONC_" + UUID.randomUUID().toString().substring(0, 8);
        createTestShipment(orderId, trackingNumber, clientCode, "PENDING");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        // Luồng 1 (mô phỏng Webhook processor nhận transporting):
        Callable<Void> webhookTask = () -> {
            latch.await();
            fulfillmentStateService.applyCarrierInTransit(trackingNumber, Instant.now(), "transporting");
            return null;
        };

        // Luồng 2 (mô phỏng Reconciliation scheduler nhận delivered):
        Callable<Void> reconTask = () -> {
            latch.await();
            fulfillmentStateService.applyCarrierDelivered(trackingNumber, Instant.now(), "delivered");
            return null;
        };

        Future<Void> f1 = executor.submit(webhookTask);
        Future<Void> f2 = executor.submit(reconTask);

        latch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId);
        String shipmentStatus = jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE order_id = ?", String.class, orderId);

        // Trạng thái cuối cùng hội tụ về DELIVERED
        assertThat(orderStatus).isEqualTo("DELIVERED");
        assertThat(shipmentStatus).isEqualTo("DELIVERED");
    }
}
