package com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence;

import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
class ShipmentPostgresTest {

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
    private ShipmentRepository shipmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private Long createTestOrder() {
        Long orderId = jdbcTemplate.queryForObject("""
                INSERT INTO orders(
                    checkout_group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, shipping_fee, total_amount, buyer_system_fee, seller_system_fee, seller_proceeds, payment_due_at
                ) VALUES (
                    ?, ?, ?, 'Recipient', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    100000, 0, 100000, 0, 0, 100000, NOW() + INTERVAL '1 hour'
                ) RETURNING order_id
                """,
                Long.class,
                UUID.randomUUID(), buyerId, sellerId
        );
        createdOrderIds.add(orderId);
        return orderId;
    }

    @Test
    @DisplayName("Hibernate ddl-auto: validate succeeds and Repository can perform CRUD on shipments")
    void testShipmentRepositoryCrudAndHibernateValidation() {
        Long orderId = createTestOrder();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant delivery = now.plus(2, ChronoUnit.DAYS);

        String clientCode = "OG-DEV-" + orderId + "-TEST01";
        ShipmentEntity entity = new ShipmentEntity(orderId, clientCode, now);
        entity.recordGhnCreated("TRACK-GHN-001", new BigDecimal("25000.00"), delivery, now);

        ShipmentEntity saved = shipmentRepository.saveAndFlush(entity);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCarrier()).isEqualTo("GHN");
        assertThat(saved.getClientOrderCode()).isEqualTo(clientCode);
        assertThat(saved.getTrackingNumber()).isEqualTo("TRACK-GHN-001");
        assertThat(saved.getShippingFee()).isEqualByComparingTo(new BigDecimal("25000.00"));
        assertThat(saved.getRawGhnStatus()).isEqualTo("ready_to_pick");
        assertThat(saved.getCreatedAt()).isNotNull();

        var foundByOrder = shipmentRepository.findByOrderId(orderId);
        assertThat(foundByOrder).isPresent();
        assertThat(foundByOrder.get().getClientOrderCode()).isEqualTo(clientCode);

        var foundByCode = shipmentRepository.findByClientOrderCode(clientCode);
        assertThat(foundByCode).isPresent();
        assertThat(foundByCode.get().getOrderId()).isEqualTo(orderId);

        var foundByTracking = shipmentRepository.findByTrackingNumber("TRACK-GHN-001");
        assertThat(foundByTracking).isPresent();
        assertThat(foundByTracking.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("Partial unique index: cho phép nhiều client_order_code NULL nhưng chặn trùng khi NOT NULL")
    void testPartialUniqueIndexOnClientOrderCode() {
        Long order1 = createTestOrder();
        Long order2 = createTestOrder();

        // 1. Cả 2 bản ghi có client_order_code NULL đều được chấp nhận
        jdbcTemplate.update(
                "INSERT INTO shipments (order_id, carrier, status, created_at) VALUES (?, 'GHN', 'PENDING', NOW())",
                order1
        );
        jdbcTemplate.update(
                "INSERT INTO shipments (order_id, carrier, status, created_at) VALUES (?, 'GHN', 'PENDING', NOW())",
                order2
        );

        Long order3 = createTestOrder();
        Long order4 = createTestOrder();
        String uniqueCode = "OG-TEST-CODE-DUP";

        // 2. Bản ghi đầu tiên có client_order_code thành công
        jdbcTemplate.update(
                "INSERT INTO shipments (order_id, carrier, client_order_code, status, created_at) VALUES (?, 'GHN', ?, 'PENDING', NOW())",
                order3, uniqueCode
        );

        // 3. Bản ghi thứ 2 trùng client_order_code phải bị chặn bởi partial index uq_shipments_client_order_code
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO shipments (order_id, carrier, client_order_code, status, created_at) VALUES (?, 'GHN', ?, 'PENDING', NOW())",
                order4, uniqueCode
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_shipments_client_order_code");
    }

    @Test
    @DisplayName("Giá trị mặc định của carrier trong DB là 'GHN' và các check constraints của shipments")
    void testCarrierDefaultAndCheckConstraints() {
        Long orderId = createTestOrder();

        // Insert không truyền carrier -> DB tự gán default 'GHN'
        jdbcTemplate.update(
                "INSERT INTO shipments (order_id, status, created_at) VALUES (?, 'PENDING', NOW())",
                orderId
        );

        String carrier = jdbcTemplate.queryForObject(
                "SELECT carrier FROM shipments WHERE order_id = ?",
                String.class,
                orderId
        );
        assertThat(carrier).isEqualTo("GHN");

        // Vi phạm check constraint ck_shipments_status
        Long invalidStatusOrder = createTestOrder();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO shipments (order_id, status, created_at) VALUES (?, 'INVALID_STATUS', NOW())",
                invalidStatusOrder
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_shipments_status");
    }
}
