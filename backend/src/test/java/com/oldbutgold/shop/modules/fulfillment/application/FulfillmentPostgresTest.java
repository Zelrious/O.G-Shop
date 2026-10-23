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

import com.oldbutgold.shop.modules.commerce.application.OrderManagementDtos;
import com.oldbutgold.shop.modules.commerce.application.OrderManagementService;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @Autowired
    private OrderManagementService orderManagementService;

    @Autowired
    private FulfillmentStateService fulfillmentStateService;

    @MockitoSpyBean
    private PaymentRepository paymentRepository;

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
                jdbcTemplate.update("DELETE FROM og_compat.payments WHERE order_id IN (" + inClause + ")", createdOrderIds.toArray());
                jdbcTemplate.update("DELETE FROM orders WHERE order_id IN (" + inClause + ")", createdOrderIds.toArray());
            }
            jdbcTemplate.update("DELETE FROM ghn_webhook_events WHERE order_code LIKE 'OG-PG-%'");
        } finally {
            createdOrderIds.clear();
            createdUserIds.clear();
            reset(paymentRepository);
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

    private Long createOrderWithStatus(String status) {
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
                    ?, NOW() + INTERVAL '1 hour', NOW(), NOW()
                ) RETURNING order_id
                """,
                Long.class,
                UUID.randomUUID(), buyerId, sellerId, status
        );
        createdOrderIds.add(orderId);
        return orderId;
    }

    private PaymentEntity createTestPayment(Long orderId, String status) {
        PaymentEntity payment = new PaymentEntity(orderId, BigDecimal.valueOf(100000), PaymentEntity.METHOD_VNPAY, Instant.now());
        if (PaymentEntity.STATUS_HELD.equals(status)) {
            payment.markHeld("VNPAY-TEST-" + orderId + "-" + UUID.randomUUID(), Instant.now());
        } else if (PaymentEntity.STATUS_UNDER_REVIEW.equals(status)) {
            payment.markUnderReview("VNPAY-TEST-" + orderId + "-" + UUID.randomUUID(), Instant.now());
        } else if (PaymentEntity.STATUS_RELEASED.equals(status)) {
            payment.markHeld("VNPAY-TEST-" + orderId + "-" + UUID.randomUUID(), Instant.now());
            payment.markReleased(Instant.now());
        }
        return paymentRepository.saveAndFlush(payment);
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
        verify(ghnClient, times(1)).createShippingOrder(any());
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

    @Test
    @DisplayName("11. PostgreSQL: 2 request complete đồng thời -> payment RELEASED đúng 1 lần")
    void testConcurrentCompleteBuyerOrder_PaymentReleasedExactlyOnce() throws Exception {
        Long orderId = createOrderWithStatus("DELIVERED");
        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, tracking_number, client_order_code, status, shipped_at, delivered_at, admin_review_required, created_at, updated_at)
                VALUES (?, 'GHN', 'GHN-COMP-001', 'CLI-COMP-001', 'DELIVERED', NOW() - INTERVAL '1 hour', NOW(), false, NOW(), NOW())
                """, orderId);
        PaymentEntity initialPayment = createTestPayment(orderId, PaymentEntity.STATUS_HELD);
        long initialVersion = initialPayment.getVersion();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<OrderManagementDtos.OrderActionResponse>> futures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return orderManagementService.completeBuyerOrder(buyerId, orderId);
            }));
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        for (Future<OrderManagementDtos.OrderActionResponse> future : futures) {
            OrderManagementDtos.OrderActionResponse res = future.get();
            assertThat(res.status()).isEqualTo("COMPLETED");
        }

        // Kiểm tra trong PostgreSQL:
        // 1. Order status là COMPLETED
        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE order_id = ?",
                String.class, orderId
        );
        assertThat(orderStatus).isEqualTo("COMPLETED");

        // 2. Payment status là RELEASED, released_at được ghi nhận và version chỉ tăng đúng 1 lần
        var paymentRow = jdbcTemplate.queryForMap(
                "SELECT status, released_at, held_at, version FROM og_compat.payments WHERE order_id = ?",
                orderId
        );
        assertThat(paymentRow.get("status")).isEqualTo("RELEASED");
        Timestamp releasedAt = (Timestamp) paymentRow.get("released_at");
        assertThat(releasedAt).isNotNull();
        long finalVersion = ((Number) paymentRow.get("version")).longValue();
        assertThat(finalVersion).isEqualTo(initialVersion + 1L);

        // 3. Gọi lại complete lần thứ 3: kiểm tra released_at không bị ghi đè và version giữ nguyên
        var retryResp = orderManagementService.completeBuyerOrder(buyerId, orderId);
        assertThat(retryResp.status()).isEqualTo("COMPLETED");

        var retryPaymentRow = jdbcTemplate.queryForMap(
                "SELECT released_at, version FROM og_compat.payments WHERE order_id = ?",
                orderId
        );
        assertThat((Timestamp) retryPaymentRow.get("released_at")).isEqualTo(releasedAt);
        assertThat(((Number) retryPaymentRow.get("version")).longValue()).isEqualTo(finalVersion);
    }

    @Test
    @DisplayName("12. PostgreSQL: complete bị chặn khi shipment có adminReviewRequired hoặc payment là UNDER_REVIEW")
    void testCompleteBuyerOrder_BlockedByAdminReviewOrUnderReview() {
        // Trường hợp 1: shipment.adminReviewRequired = true
        Long orderId1 = createOrderWithStatus("DELIVERED");
        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, tracking_number, client_order_code, status, shipped_at, delivered_at, admin_review_required, created_at, updated_at)
                VALUES (?, 'GHN', 'GHN-REV-001', 'CLI-REV-001', 'DELIVERED', NOW() - INTERVAL '1 hour', NOW(), true, NOW(), NOW())
                """, orderId1);
        createTestPayment(orderId1, PaymentEntity.STATUS_HELD);

        assertThatThrownBy(() -> orderManagementService.completeBuyerOrder(buyerId, orderId1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin kiểm duyệt");

        String status1 = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId1);
        String payStatus1 = jdbcTemplate.queryForObject("SELECT status FROM og_compat.payments WHERE order_id = ?", String.class, orderId1);
        assertThat(status1).isEqualTo("DELIVERED");
        assertThat(payStatus1).isEqualTo("HELD");

        // Trường hợp 2: payment là UNDER_REVIEW
        Long orderId2 = createOrderWithStatus("DELIVERED");
        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, tracking_number, client_order_code, status, shipped_at, delivered_at, admin_review_required, created_at, updated_at)
                VALUES (?, 'GHN', 'GHN-REV-002', 'CLI-REV-002', 'DELIVERED', NOW() - INTERVAL '1 hour', NOW(), false, NOW(), NOW())
                """, orderId2);
        createTestPayment(orderId2, PaymentEntity.STATUS_UNDER_REVIEW);

        assertThatThrownBy(() -> orderManagementService.completeBuyerOrder(buyerId, orderId2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNDER_REVIEW");

        String status2 = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId2);
        String payStatus2 = jdbcTemplate.queryForObject("SELECT status FROM og_compat.payments WHERE order_id = ?", String.class, orderId2);
        assertThat(status2).isEqualTo("DELIVERED");
        assertThat(payStatus2).isEqualTo("UNDER_REVIEW");
    }

    @Test
    @DisplayName("13. PostgreSQL: deliver (demo) vs webhook delivered đồng thời không gây deadlock và Order sang DELIVERED")
    void testConcurrentDemoDeliverAndWebhookDelivered() throws Exception {
        Long orderId = createOrderWithStatus("SHIPPED");
        String trackingCode = "OG-PG-DELIVER-RACE-" + orderId;
        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, tracking_number, client_order_code, status, shipped_at, admin_review_required, created_at, updated_at)
                VALUES (?, 'GHN', ?, 'CLI-RACE-001', 'IN_TRANSIT', NOW() - INTERVAL '1 hour', false, NOW(), NOW())
                """, orderId, trackingCode);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<String>> futures = new CopyOnWriteArrayList<>();

        // Thread 1: seller demo deliver
        futures.add(executor.submit(() -> {
            barrier.await();
            orderManagementService.deliverSellerOrder(sellerId, orderId);
            return "DEMO_DONE";
        }));

        // Thread 2: carrier webhook delivered
        futures.add(executor.submit(() -> {
            barrier.await();
            fulfillmentStateService.applyCarrierDelivered(trackingCode, Instant.now(), "delivered");
            return "CARRIER_DONE";
        }));

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // Xác nhận không thread nào ném exception
        for (Future<String> future : futures) {
            String result = future.get();
            assertThat(result).isIn("DEMO_DONE", "CARRIER_DONE");
        }

        // Bắt buộc: Cả Order và Shipment đều ở trạng thái DELIVERED
        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId);
        String shipmentStatus = jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE order_id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("DELIVERED");
        assertThat(shipmentStatus).isEqualTo("DELIVERED");

        // Xác nhận delivered_at giữ nguyên mốc của luồng thắng và không bị ghi đè bởi request sau
        Timestamp winningDeliveredAt = jdbcTemplate.queryForObject("SELECT delivered_at FROM shipments WHERE order_id = ?", Timestamp.class, orderId);
        assertThat(winningDeliveredAt).isNotNull();

        // Thử gọi lại cả carrier webhook và demo deliver sau đó: delivered_at phải giữ nguyên tuyệt đối
        fulfillmentStateService.applyCarrierDelivered(trackingCode, Instant.now().plusSeconds(3600), "delivered");
        orderManagementService.deliverSellerOrder(sellerId, orderId);

        Timestamp unchangedDeliveredAt = jdbcTemplate.queryForObject("SELECT delivered_at FROM shipments WHERE order_id = ?", Timestamp.class, orderId);
        assertThat(unchangedDeliveredAt).isEqualTo(winningDeliveredAt);
    }

    @Test
    @DisplayName("14. PostgreSQL: Ép lỗi khi cập nhật Payment trong complete -> Order rollback nguyên tử (không COMPLETED)")
    void testCompleteBuyerOrder_PaymentFailureRollsBackOrder() {
        Long orderId = createOrderWithStatus("DELIVERED");
        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, tracking_number, client_order_code, status, shipped_at, delivered_at, admin_review_required, created_at, updated_at)
                VALUES (?, 'GHN', 'GHN-ROLLBACK-001', 'CLI-ROLLBACK-001', 'DELIVERED', NOW() - INTERVAL '1 hour', NOW(), false, NOW(), NOW())
                """, orderId);
        createTestPayment(orderId, PaymentEntity.STATUS_HELD);

        // Ép lỗi khi save payment có status = RELEASED
        org.mockito.Mockito.doAnswer(invocation -> {
            PaymentEntity entity = invocation.getArgument(0);
            if (PaymentEntity.STATUS_RELEASED.equals(entity.getStatus())) {
                throw new org.springframework.dao.DataIntegrityViolationException("Simulated payment DB failure");
            }
            return invocation.callRealMethod();
        }).when(paymentRepository).save(any(PaymentEntity.class));

        assertThatThrownBy(() -> orderManagementService.completeBuyerOrder(buyerId, orderId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("Simulated payment DB failure");

        // Sau khi ném lỗi, reset spy để có thể kiểm tra trạng thái trong DB an toàn
        org.mockito.Mockito.reset(paymentRepository);

        // Kiểm tra trong PostgreSQL thật: Cả transaction phải bị rollback hoàn toàn!
        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId);
        String paymentStatus = jdbcTemplate.queryForObject("SELECT status FROM og_compat.payments WHERE order_id = ?", String.class, orderId);
        Timestamp completedAt = jdbcTemplate.queryForObject("SELECT completed_at FROM orders WHERE order_id = ?", Timestamp.class, orderId);

        assertThat(orderStatus).isEqualTo("DELIVERED"); // Bắt buộc KHÔNG được là COMPLETED!
        assertThat(paymentStatus).isEqualTo("HELD");     // Bắt buộc KHÔNG được là RELEASED!
        assertThat(completedAt).isNull();
    }

    @Test
    @DisplayName("15. PostgreSQL: GhnWebhookProcessor nhận switch_status delivered -> Order chuyển DELIVERED")
    void testWebhookProcessorDeliveredUpdatesOrderToDelivered() throws Exception {
        Long orderId = createOrderWithStatus("SHIPPED");
        String trackingCode = "OG-PG-DELIVERED-TEST-" + orderId;
        String clientCode = "CLI-DELIVERED-" + orderId;

        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, tracking_number, client_order_code, status, shipped_at, admin_review_required, created_at, updated_at)
                VALUES (?, 'GHN', ?, ?, 'IN_TRANSIT', NOW() - INTERVAL '1 hour', false, NOW(), NOW())
                """, orderId, trackingCode, clientCode);

        com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookPayloadDto payload =
                new com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookPayloadDto(
                        trackingCode, clientCode, "switch_status", "delivered",
                        "2026-10-20T10:00:00Z", "Giao hàng thành công", 20900L, 0L,
                        1000, 20, 15, 10, 1000, "Kho Tan Binh", null, null,
                        "Shipper Nguyen", "0901234567"
                );

        var ack = webhookService.ingestWebhook("test-secret", payload);
        assertThat(ack.status()).isEqualTo("OK");

        // Đảm bảo event hoàn tất xử lý (đồng bộ hoặc recovery)
        webhookProcessor.processPendingEvents();

        // Xác minh OrderEntity trong PostgreSQL thật đã được markOrderDelivered chuyển sang DELIVERED
        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("DELIVERED");

        // Xác minh ShipmentEntity cũng là DELIVERED
        String shipmentStatus = jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE order_id = ?", String.class, orderId);
        assertThat(shipmentStatus).isEqualTo("DELIVERED");

        // Xác minh Webhook event được đánh dấu PROCESSED
        String eventStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM ghn_webhook_events WHERE order_code = ?",
                String.class, trackingCode
        );
        assertThat(eventStatus).isEqualTo("PROCESSED");
    }

    @Test
    @DisplayName("16. PostgreSQL: Webhook delivered khi Order ở SELLER_CONFIRMED (nhánh catch-up) không vi phạm ck_shipments_times")
    void testWebhookProcessorDelivered_WhenOrderSellerConfirmed_CatchesUpWithoutCkShipmentsTimesViolation() throws Exception {
        Long orderId = createConfirmedOrder(); // Order status: SELLER_CONFIRMED
        String trackingCode = "OG-PG-CATCHUP-" + orderId;
        String clientCode = "CLI-CATCHUP-" + orderId;

        // Vận đơn ban đầu ở PENDING: shipped_at = null, delivered_at = null (tuân thủ ck_shipments_times)
        jdbcTemplate.update("""
                INSERT INTO shipments(order_id, carrier, tracking_number, client_order_code, status, shipped_at, delivered_at, admin_review_required, created_at, updated_at)
                VALUES (?, 'GHN', ?, ?, 'PENDING', NULL, NULL, false, NOW(), NOW())
                """, orderId, trackingCode, clientCode);

        com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookPayloadDto payload =
                new com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookPayloadDto(
                        trackingCode, clientCode, "switch_status", "delivered",
                        "2026-10-20T14:00:00Z", "Giao hàng thành công (bỏ qua picked)", 20900L, 0L,
                        1000, 20, 15, 10, 1000, "Kho Tan Binh", null, null,
                        "Shipper CatchUp", "0909998888"
                );

        var ack = webhookService.ingestWebhook("test-secret", payload);
        assertThat(ack.status()).isEqualTo("OK");

        webhookProcessor.processPendingEvents();

        // 1. Xác minh OrderEntity đã tự động bắt kịp (catch-up) từ SELLER_CONFIRMED sang DELIVERED
        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("DELIVERED");

        // 2. Xác minh ShipmentEntity chuyển sang DELIVERED
        var shipmentRow = jdbcTemplate.queryForMap(
                "SELECT status, shipped_at, delivered_at FROM shipments WHERE order_id = ?",
                orderId
        );
        assertThat(shipmentRow.get("status")).isEqualTo("DELIVERED");

        Timestamp shippedAt = (Timestamp) shipmentRow.get("shipped_at");
        Timestamp deliveredAt = (Timestamp) shipmentRow.get("delivered_at");

        // 3. Bắt buộc: shipped_at và delivered_at đều không null, và delivered_at >= shipped_at
        // để thỏa mãn ràng buộc PostgreSQL ck_shipments_times
        assertThat(shippedAt).isNotNull();
        assertThat(deliveredAt).isNotNull();
        assertThat(deliveredAt.getTime()).isGreaterThanOrEqualTo(shippedAt.getTime());

        // 4. Xác minh sự kiện webhook chuyển sang PROCESSED thành công
        String eventStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM ghn_webhook_events WHERE order_code = ?",
                String.class, trackingCode
        );
        assertThat(eventStatus).isEqualTo("PROCESSED");
    }
}
