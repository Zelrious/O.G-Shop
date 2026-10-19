package com.oldbutgold.shop.modules.fulfillment.application;

import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.CreateShipmentRequest;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentResponse;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnApiException;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnClient;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.AvailableServiceDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.CreateOrderResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
class FulfillmentPostgresTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("OGSHOP_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.ghn.webhook-secret", () -> "test-secret");
    }

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Autowired
    private GhnWebhookService webhookService;

    @Autowired
    private com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.GhnWebhookEventRepository eventRepository;

    @Autowired
    private GhnWebhookProcessor webhookProcessor;

    @MockitoBean
    private GhnClient ghnClient;

    private final List<Long> createdOrderIds = new CopyOnWriteArrayList<>();
    private final List<Long> createdUserIds = new CopyOnWriteArrayList<>();

    private Long buyerId;
    private Long sellerId;

    @BeforeEach
    void setUp() {
        buyerId = createTestUser("buyer");
        sellerId = createTestUser("seller");

        when(ghnClient.getAvailableServices(anyInt(), anyInt())).thenReturn(List.of(
                new AvailableServiceDto(53320, "Chuẩn", 2)
        ));
    }

    @AfterEach
    void tearDown() {
        try {
            if (!createdOrderIds.isEmpty()) {
                String inClause = String.join(",", Collections.nCopies(createdOrderIds.size(), "?"));
                jdbcTemplate.update("DELETE FROM shipments WHERE order_id IN (" + inClause + ")", createdOrderIds.toArray());
                jdbcTemplate.update("DELETE FROM orders WHERE order_id IN (" + inClause + ")", createdOrderIds.toArray());
            }
            jdbcTemplate.update("DELETE FROM ghn_webhook_events WHERE order_code LIKE 'OG-PG-%'");
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

    private Long createConfirmedOrder() {
        Long orderId = jdbcTemplate.queryForObject("""
                INSERT INTO orders(
                    checkout_group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, shipping_fee, total_amount, buyer_system_fee, seller_system_fee, seller_proceeds,
                    status, payment_due_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, 'Recipient', '0912345678',
                    'TP. Ho Chi Minh', 'Quan 1', 'Phuong Pham Ngu Lao', '123 Le Loi',
                    100000, 0, 100000, 0, 0, 100000,
                    'SELLER_CONFIRMED', NOW() + INTERVAL '1 hour', NOW(), NOW()
                ) RETURNING order_id
                """,
                Long.class,
                UUID.randomUUID(), buyerId, sellerId
        );
        createdOrderIds.add(orderId);
        return orderId;
    }

    private CreateShipmentRequest createValidRequest() {
        return new CreateShipmentRequest(
                "Shop Seller", "0912345678", "456 Tran Hung Dao", "Phuong Pham Ngu Lao", "Quan 1", "TP. Ho Chi Minh",
                1442, 1444, "20311", 1000, 20, 15, 10, "Giao nhanh", null
        );
    }

    @Test
    @DisplayName("0. Fail-closed: createShipment bị chặn trên PostgreSQL và không tạo shipment rác")
    void testCreateShipmentFailClosedBlocksWithoutDirtyShipment() {
        Long orderId = createConfirmedOrder();

        assertThatThrownBy(() -> fulfillmentService.createShipment(sellerId, orderId, createValidRequest()))
                .isInstanceOf(FulfillmentFeatureLockedException.class)
                .hasMessageContaining("ADDR-01");

        // Xác nhận không tạo bất kỳ shipment nào trong DB
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipments WHERE order_id = ?",
                Integer.class,
                orderId
        );
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("1. Tạo vận đơn qua lease thành công lưu DB và giữ nguyên trạng thái Order là SELLER_CONFIRMED")
    void testCreateShipmentSuccessPersistsInDbAndKeepsOrderSellerConfirmed() {
        Long orderId = createConfirmedOrder();

        CreateOrderResponseDto ghnResponse = new CreateOrderResponseDto(
                "GHN-TEST-ORDER-001", "D1", "2026-10-20T17:00:00Z", 20900, "standard"
        );
        when(ghnClient.createShippingOrder(any())).thenReturn(ghnResponse);

        ShipmentResponse response = fulfillmentService.executeShipmentCreationWithLease(sellerId, orderId, createValidRequest());

        assertThat(response.trackingNumber()).isEqualTo("GHN-TEST-ORDER-001");
        assertThat(response.orderId()).isEqualTo(orderId);

        // 1. Kiểm tra trạng thái Shipment trong PostgreSQL
        ShipmentEntity inDb = shipmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(inDb.getTrackingNumber()).isEqualTo("GHN-TEST-ORDER-001");
        assertThat(inDb.getStatus()).isEqualTo(ShipmentEntity.STATUS_PENDING);
        assertThat(inDb.getRawGhnStatus()).isEqualTo("ready_to_pick");
        assertThat(inDb.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(20900));
        assertThat(inDb.getClientOrderCode()).isNotBlank().startsWith("OG-DEV-");
        assertThat(inDb.getClaimToken()).isNull(); // claim cleared on finalize

        // 2. Kiểm tra OrderEntity trong DB vẫn là SELLER_CONFIRMED (chưa bưu tá lấy, chưa thành SHIPPED)
        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE order_id = ?",
                String.class,
                orderId
        );
        assertThat(orderStatus).isEqualTo("SELLER_CONFIRMED");
    }

    @Test
    @DisplayName("2. Cạnh tranh đồng thời giữa các thread với claim/lease không tạo trùng shipment")
    void testConcurrentShipmentCreationPreventsDuplicates() throws Exception {
        Long orderId = createConfirmedOrder();

        CreateOrderResponseDto ghnResponse = new CreateOrderResponseDto(
                "GHN-CONCURRENT-001", "D1", "2026-10-20T17:00:00Z", 20900, "standard"
        );
        when(ghnClient.createShippingOrder(any())).thenReturn(ghnResponse);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<ShipmentResponse>> futures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return fulfillmentService.executeShipmentCreationWithLease(sellerId, orderId, createValidRequest());
            }));
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        int successCount = 0;
        int failureCount = 0;

        for (Future<ShipmentResponse> future : futures) {
            try {
                ShipmentResponse resp = future.get();
                if (resp != null && "GHN-CONCURRENT-001".equals(resp.trackingNumber())) {
                    successCount++;
                }
            } catch (ExecutionException ex) {
                failureCount++;
            }
        }

        assertThat(successCount).isGreaterThanOrEqualTo(1);

        // Bắt buộc: trong database chỉ tồn tại duy nhất 1 bản ghi shipment cho order này
        Integer shipmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipments WHERE order_id = ?",
                Integer.class,
                orderId
        );
        assertThat(shipmentCount).isEqualTo(1);
    }

    @Test
    @DisplayName("3. Khi GHN timeout: Shipment PENDING được giữ nguyên trong DB, không bị đổi thành FAILED")
    void testTimeoutLeavesShipmentPendingWithoutMarkingFailed() {
        Long orderId = createConfirmedOrder();

        // Giả lập GHN timeout
        when(ghnClient.createShippingOrder(any()))
                .thenThrow(new GhnApiException("Read timed out", new ResourceAccessException("Timeout")));
        when(ghnClient.getOrderDetailByClientCode(any())).thenReturn(null);

        assertThatThrownBy(() -> fulfillmentService.executeShipmentCreationWithLease(sellerId, orderId, createValidRequest()))
                .isInstanceOf(GhnApiException.class)
                .hasMessageContaining("chưa xác định do GHN timeout");

        // Kiểm tra trong database: bản ghi shipment PENDING vẫn tồn tại, status KHÔNG PHẢI FAILED
        ShipmentEntity inDb = shipmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo(ShipmentEntity.STATUS_PENDING);
        assertThat(inDb.getTrackingNumber()).isNull();
        assertThat(inDb.getClientOrderCode()).isNotBlank();
    }

    @Test
    @DisplayName("4. Hai transaction tranh chấp atomic acquireClaim trên PostgreSQL thật")
    void testPostgresAtomicAcquireClaim() {
        Long orderId = createConfirmedOrder();

        // Tạo sẵn shipment PENDING trong PostgreSQL với claim đã hết hạn (tạo cách đây 1 phút, expiry = 30s trước)
        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, status, client_order_code, claim_token, claim_expires_at, created_at, updated_at)
                VALUES (?, 'GHN', 'PENDING', 'OG-DEV-CLAIM-1', 'expired-token', NOW() - INTERVAL '30 seconds', NOW() - INTERVAL '60 seconds', NOW() - INTERVAL '60 seconds')
                """, orderId);

        Instant now = Instant.now();
        Instant newExpiry = now.plusSeconds(30);

        // Thread 1 tranh claim:
        int updated1 = transactionTemplate.execute(status ->
                shipmentRepository.acquireClaim(orderId, "winner-token", newExpiry, now));
        assertThat(updated1).isEqualTo(1);

        // Thread 2 tranh claim ngay sau đó khi lease mới còn hiệu lực:
        int updated2 = transactionTemplate.execute(status ->
                shipmentRepository.acquireClaim(orderId, "loser-token", newExpiry, now));
        assertThat(updated2).isEqualTo(0);

        // Xác minh trong DB token hiện tại là của winner
        String activeToken = jdbcTemplate.queryForObject(
                "SELECT claim_token FROM shipments WHERE order_id = ?",
                String.class,
                orderId
        );
        assertThat(activeToken).isEqualTo("winner-token");
    }

    @Test
    @DisplayName("5. PostgreSQL: Hai luồng concurrent gửi webhook duplicate đều được ACK 200 và chỉ có 1 bản ghi trong DB")
    void testPostgresWebhookConcurrentDuplicates() throws Exception {
        com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookPayloadDto payload =
                new com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookPayloadDto(
                        "OG-PG-ORDER-1", "OG-PG-CLIENT-1", "switch_status", "storing",
                        "2026-10-18T10:00:00Z", "Hàng về kho", 20900L, 0L,
                        1000, 20, 15, 10, 1000, "Kho Tan Binh", null, null,
                        "Shipper Nguyen", "0901234567"
                );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        List<com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookAckResponse> responses =
                new CopyOnWriteArrayList<>();

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var ack = webhookService.ingestWebhook("test-secret", payload);
                    responses.add(ack);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(responses).hasSize(2);
        assertThat(responses.stream().allMatch(r -> "OK".equals(r.status()) || "DUPLICATE_IGNORED".equals(r.status()))).isTrue();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ghn_webhook_events WHERE order_code = 'OG-PG-ORDER-1'",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("6. PostgreSQL: Hai worker tranh chấp claimEventForProcessing trên database thật")
    void testPostgresWebhookAtomicClaim() {
        jdbcTemplate.update("""
                INSERT INTO ghn_webhook_events(order_code, type, idempotency_key, raw_payload, status, retry_count, received_at)
                VALUES ('OG-PG-CLAIM-1', 'switch_status', 'OG-PG-CLAIM-1:key', '{"status":"storing"}', 'PENDING', 0, NOW())
                """);

        Long eventId = jdbcTemplate.queryForObject(
                "SELECT event_id FROM ghn_webhook_events WHERE order_code = 'OG-PG-CLAIM-1'",
                Long.class
        );

        Instant now = Instant.now();
        Instant staleThreshold = now.minusSeconds(120);

        int updated1 = transactionTemplate.execute(status ->
                eventRepository.claimEventForProcessing(eventId, now, staleThreshold, 5));
        assertThat(updated1).isEqualTo(1);

        int updated2 = transactionTemplate.execute(status ->
                eventRepository.claimEventForProcessing(eventId, now, staleThreshold, 5));
        assertThat(updated2).isEqualTo(0);

        String currentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM ghn_webhook_events WHERE event_id = ?",
                String.class,
                eventId
        );
        assertThat(currentStatus).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("7. PostgreSQL: Recovery worker quét và phục hồi event stale PROCESSING thành công")
    void testPostgresWebhookRecoveryWorker() {
        jdbcTemplate.update("""
                INSERT INTO ghn_webhook_events(order_code, client_order_code, type, idempotency_key, raw_payload, status, retry_count, processing_started_at, received_at)
                VALUES ('OG-PG-RECOVERY-1', 'OG-PG-CLIENT-REC', 'switch_status', 'OG-PG-RECOVERY-1:key',
                        '{"OrderCode":"OG-PG-RECOVERY-1","Type":"switch_status","Status":"delivered"}',
                        'PROCESSING', 0, NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '15 minutes')
                """);

        int processed = webhookProcessor.recoverAndProcessPendingEvents();
        assertThat(processed).isGreaterThanOrEqualTo(1);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM ghn_webhook_events WHERE order_code = 'OG-PG-RECOVERY-1'",
                String.class
        );
        assertThat(status).isNotEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("8. PostgreSQL: releaseClaim giải phóng claim_token và claim_expires_at nhưng giữ nguyên next_reconciliation_at")
    void testPostgresReleaseClaimPreservesNextReconciliationAt() {
        Long orderId = createConfirmedOrder();
        Instant now = Instant.now();
        Instant nextRecon = now.plusSeconds(300);

        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, client_order_code, status, claim_token, claim_expires_at, next_reconciliation_at, reconciliation_attempts, created_at, updated_at)
                VALUES (?, 'GHN', 'OG-PG-RELEASE-TEST', 'PENDING', 'my-token', ?, ?, 1, ?, ?)
                """,
                orderId, Timestamp.from(now.plusSeconds(30)), Timestamp.from(nextRecon), Timestamp.from(now), Timestamp.from(now)
        );

        // Gọi releaseClaim
        int released = shipmentRepository.releaseClaim(orderId, "my-token", now);
        assertThat(released).isEqualTo(1);

        // Kiểm tra trong PostgreSQL
        var row = jdbcTemplate.queryForMap(
                "SELECT claim_token, claim_expires_at, next_reconciliation_at, reconciliation_attempts FROM shipments WHERE order_id = ?",
                orderId
        );

        assertThat(row.get("claim_token")).isNull();
        assertThat(row.get("claim_expires_at")).isNull();
        assertThat(row.get("next_reconciliation_at")).isNotNull();
        assertThat(((Number) row.get("reconciliation_attempts")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("9. PostgreSQL: findUntrackedPendingShipmentsForReconciliation tôn trọng next_reconciliation_at và claim lease")
    void testPostgresQueryFiltersByNextReconciliationAt() {
        Long orderId1 = createConfirmedOrder(); // Chưa đến hạn (next_reconciliation_at trong tương lai)
        Long orderId2 = createConfirmedOrder(); // Đã đến hạn (next_reconciliation_at trong quá khứ hoặc null)
        Instant now = Instant.now();

        // Shipment 1: next_reconciliation_at = now + 120s (chưa đến hạn)
        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, client_order_code, status, next_reconciliation_at, created_at, updated_at)
                VALUES (?, 'GHN', 'OG-PG-RECON-FUTURE', 'PENDING', ?, ?, ?)
                """,
                orderId1, Timestamp.from(now.plusSeconds(120)), Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.minusSeconds(60))
        );

        // Shipment 2: next_reconciliation_at = now - 10s (đã đến hạn)
        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, client_order_code, status, next_reconciliation_at, created_at, updated_at)
                VALUES (?, 'GHN', 'OG-PG-RECON-DUE', 'PENDING', ?, ?, ?)
                """,
                orderId2, Timestamp.from(now.minusSeconds(10)), Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.minusSeconds(60))
        );

        Instant cooldownThreshold = now.minusSeconds(30);
        List<ShipmentEntity> candidates = shipmentRepository.findUntrackedPendingShipmentsForReconciliation(
                now,
                cooldownThreshold,
                PageRequest.of(0, 50)
        );

        List<Long> candidateOrderIds = candidates.stream().map(ShipmentEntity::getOrderId).toList();
        assertThat(candidateOrderIds).doesNotContain(orderId1);
        assertThat(candidateOrderIds).contains(orderId2);
    }

    @Test
    @DisplayName("10. PostgreSQL: Hai worker (on-demand vs scheduler) tranh chấp acquireClaim trên database thật")
    void testPostgresClaimContentionBetweenOnDemandAndScheduler() {
        Long orderId = createConfirmedOrder();
        Instant now = Instant.now();

        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, client_order_code, status, created_at, updated_at)
                VALUES (?, 'GHN', 'OG-PG-CONCURRENT-CLAIM', 'PENDING', ?, ?)
                """,
                orderId, Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.minusSeconds(60))
        );

        // Worker 1 (on-demand) giành claim lease 10s
        int worker1 = shipmentRepository.acquireClaim(orderId, "ondemand-token", now.plusSeconds(10), now);
        assertThat(worker1).isEqualTo(1);

        // Worker 2 (scheduler) cố giành claim khi lease còn hiệu lực -> thất bại (0 row)
        int worker2 = shipmentRepository.acquireClaim(orderId, "sched-token", now.plusSeconds(30), now);
        assertThat(worker2).isEqualTo(0);

        // Sau khi worker 1 release claim
        shipmentRepository.releaseClaim(orderId, "ondemand-token", now);

        // Worker 2 thử lại -> thành công (1 row)
        int worker2Retry = shipmentRepository.acquireClaim(orderId, "sched-token", now.plusSeconds(30), now);
        assertThat(worker2Retry).isEqualTo(1);
    }
}
