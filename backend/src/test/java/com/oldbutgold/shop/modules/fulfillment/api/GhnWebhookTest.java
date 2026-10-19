package com.oldbutgold.shop.modules.fulfillment.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentStateService;
import com.oldbutgold.shop.modules.fulfillment.application.GhnWebhookProcessor;
import com.oldbutgold.shop.modules.fulfillment.application.GhnWebhookService;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnProperties;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookPayloadDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.GhnWebhookEventEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.GhnWebhookEventRepository;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GhnWebhookTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GhnWebhookEventRepository eventRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @MockitoBean
    private FulfillmentStateService stateService;

    private static final String TEST_SECRET = "test-webhook-secret-key-12345";
    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    private WebhookPayloadDto createValidPayload(String orderCode, String status, String time) {
        return new WebhookPayloadDto(
                orderCode,
                "OG-DEV-500-ABCDEF",
                "switch_status",
                status,
                time,
                "Trạng thái cập nhật từ GHN",
                20900L,
                0L,
                1000,
                20,
                15,
                10,
                1000,
                "Kho Tan Binh",
                null,
                null,
                "Nguyen Shipper",
                "0909999999"
        );
    }

    // ========================================================================
    // C1. Endpoint & Authentication
    // ========================================================================

    @Test
    @DisplayName("C1.1 Thiếu header secret bị từ chối 403 Forbidden")
    void testMissingSecretHeaderReturns403() throws Exception {
        WebhookPayloadDto payload = createValidPayload("GHN-TRACK-001", "ready_to_pick", "2026-10-18T10:00:00Z");

        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("C1.2 Header secret sai bị từ chối 403 Forbidden")
    void testInvalidSecretHeaderReturns403() throws Exception {
        WebhookPayloadDto payload = createValidPayload("GHN-TRACK-001", "ready_to_pick", "2026-10-18T10:00:00Z");

        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", "wrong-secret-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("C1.3 Header secret hợp lệ được tiếp nhận và trả 200 OK")
    void testValidSecretHeaderReturns200() throws Exception {
        WebhookPayloadDto payload = createValidPayload("GHN-TRACK-001", "ready_to_pick", "2026-10-18T10:00:00Z");

        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    // ========================================================================
    // C2. Durable Event Persistence & PII Redaction
    // ========================================================================

    @Test
    @DisplayName("C2.1 Sự kiện được lưu bền vững vào ghn_webhook_events và PII được redact")
    void testEventPersistedDurablyAndPiiRedacted() throws Exception {
        WebhookPayloadDto payload = createValidPayload("GHN-TRACK-PII", "ready_to_pick", "2026-10-18T10:00:00Z");

        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        var events = eventRepository.findByOrderCode("GHN-TRACK-PII");
        assertThat(events).hasSize(1);
        GhnWebhookEventEntity entity = events.get(0);
        assertThat(entity.getOrderCode()).isEqualTo("GHN-TRACK-PII");
        assertThat(entity.getType()).isEqualTo("switch_status");

        // Xác nhận PII bưu tá được lọc bỏ
        assertThat(entity.getRawPayload()).contains("[REDACTED]");
        assertThat(entity.getRawPayload()).doesNotContain("Nguyen Shipper");
        assertThat(entity.getRawPayload()).doesNotContain("0909999999");
    }

    @Test
    @DisplayName("C2.2 Sự kiện duplicate trả về 200 OK ngay và không lưu duplicate")
    void testDuplicateEventReturns200WithoutDoubleSave() throws Exception {
        WebhookPayloadDto payload = createValidPayload("GHN-TRACK-DUP", "ready_to_pick", "2026-10-18T10:00:00Z");

        // Lần 1
        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Lần 2 (trùng lặp hoàn toàn)
        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Chỉ có 1 bản ghi duy nhất trong DB
        var events = eventRepository.findByOrderCode("GHN-TRACK-DUP");
        assertThat(events).hasSize(1);
    }

    // ========================================================================
    // D. State Machine & Incidents Processing Tests
    // ========================================================================

    @Test
    @DisplayName("D1. Type không phải create/switch_status bị đánh dấu IGNORED")
    void testNonStatusTypeIsIgnored() throws Exception {
        WebhookPayloadDto payload = new WebhookPayloadDto(
                "GHN-TRACK-WEIGHT", "OG-DEV-500", "update_weight", "storing",
                "2026-10-18T10:00:00Z", "Cập nhật trọng lượng", 20900L, 0L,
                1500, 20, 15, 10, 1500, "Kho", null, null, null, null
        );

        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Đợi xử lý async
        Thread.sleep(200);

        var events = eventRepository.findByOrderCode("GHN-TRACK-WEIGHT");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatus()).isEqualTo(GhnWebhookEventEntity.STATUS_IGNORED);

        verify(stateService, never()).applyCarrierPickedUp(any(), any(), any());
        verify(stateService, never()).applyCarrierDelivered(any(), any(), any());
    }

    @Test
    @DisplayName("D2. Webhook switch_status 'picked' kích hoạt applyCarrierPickedUp")
    void testPickedTriggersApplyCarrierPickedUp() throws Exception {
        // Tạo shipment giả lập trong DB
        ShipmentEntity shipment = new ShipmentEntity(901L, "OG-DEV-901-PICK", NOW);
        shipment.setTrackingNumber("GHN-ORDER-PICKED");
        shipmentRepository.save(shipment);

        WebhookPayloadDto payload = createValidPayload("GHN-ORDER-PICKED", "picked", "2026-10-18T10:00:00Z");

        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Thread.sleep(200);

        verify(stateService).applyCarrierPickedUp(eq("GHN-ORDER-PICKED"), any(), eq("picked"));

        // Cleanup
        shipmentRepository.delete(shipment);
    }

    @Test
    @DisplayName("D3. Webhook switch_status 'delivered' kích hoạt applyCarrierDelivered")
    void testDeliveredTriggersApplyCarrierDelivered() throws Exception {
        ShipmentEntity shipment = new ShipmentEntity(902L, "OG-DEV-902-DELV", NOW);
        shipment.setTrackingNumber("GHN-ORDER-DELIVERED");
        shipmentRepository.save(shipment);

        WebhookPayloadDto payload = createValidPayload("GHN-ORDER-DELIVERED", "delivered", "2026-10-18T10:00:00Z");

        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Thread.sleep(200);

        verify(stateService).applyCarrierDelivered(eq("GHN-ORDER-DELIVERED"), any(), eq("delivered"));

        // Cleanup
        shipmentRepository.delete(shipment);
    }

    @Test
    @DisplayName("D4. Webhook incident (damage/lost) ghi nhận incident và không tự ý chuyển DELIVERED/FAILED")
    void testIncidentRecordsAuditWithoutStatusChange() throws Exception {
        ShipmentEntity shipment = new ShipmentEntity(903L, "OG-DEV-903-INCIDENT", NOW);
        shipment.setTrackingNumber("GHN-ORDER-DAMAGE");
        shipmentRepository.save(shipment);

        WebhookPayloadDto payload = createValidPayload("GHN-ORDER-DAMAGE", "damage", "2026-10-18T10:00:00Z");

        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Thread.sleep(200);

        ShipmentEntity reloaded = shipmentRepository.findByOrderId(903L).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ShipmentEntity.STATUS_PENDING); // Status không bị đổi tùy tiện
        assertThat(reloaded.isAdminReviewRequired()).isTrue();
        assertThat(reloaded.getIncidentNotes()).contains("damage");

        verify(stateService, never()).applyCarrierDelivered(any(), any(), any());
        verify(stateService, never()).applyCarrierFailed(any(), any());

        // Cleanup
        shipmentRepository.delete(reloaded);
    }

    @Test
    @DisplayName("C1.4 Secret chưa cấu hình trong hệ thống thì fail-closed và trả 503 Service Unavailable để GHN retry")
    void testUnconfiguredSecretFailsClosed() {
        GhnProperties emptyProps = new GhnProperties(
                "https://dev-online-gateway.ghn.vn/shiip/public-api/",
                "test-token",
                229762,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                "", // Blank secret
                new GhnProperties.ReconciliationProperties(false, "0 */30 * * * *", 50)
        );
        GhnWebhookService serviceWithNoSecret = new GhnWebhookService(
                emptyProps,
                eventRepository,
                mock(GhnWebhookProcessor.class),
                mock(org.springframework.transaction.support.TransactionTemplate.class),
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        WebhookPayloadDto payload = createValidPayload("GHN-TRACK-NO-SECRET", "ready_to_pick", "2026-10-18T10:00:00Z");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                serviceWithNoSecret.ingestWebhook("any-secret", payload)
        ).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
         .hasMessageContaining("503");
    }

    @Test
    @DisplayName("C2.3 DB failure khi persist event không trả về ACK thành công mà ném exception")
    void testDatabaseFailureDoesNotReturnAck() {
        org.springframework.transaction.support.TransactionTemplate failingTxTemplate =
                mock(org.springframework.transaction.support.TransactionTemplate.class);
        when(failingTxTemplate.execute(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB down"));

        GhnProperties props = new GhnProperties(
                "https://dev-online-gateway.ghn.vn/shiip/public-api/",
                "test-token",
                229762,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                TEST_SECRET,
                new GhnProperties.ReconciliationProperties(false, "0 */30 * * * *", 50)
        );

        GhnWebhookService failingService = new GhnWebhookService(
                props,
                eventRepository,
                mock(GhnWebhookProcessor.class),
                failingTxTemplate,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        WebhookPayloadDto payload = createValidPayload("GHN-TRACK-DB-ERR", "ready_to_pick", "2026-10-18T10:00:00Z");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                failingService.ingestWebhook(TEST_SECRET, payload)
        ).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }

    @Test
    @DisplayName("C2.4 Tranh chấp đồng thời 2 webhook duplicate trả lời an toàn và không lưu duplicate")
    void testConcurrentDuplicateWebhooks() throws Exception {
        WebhookPayloadDto payload = createValidPayload("GHN-TRACK-CONCURRENT", "ready_to_pick", "2026-10-18T10:00:00Z");

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(2);

        java.util.List<Integer> statusCodes = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var result = mockMvc.perform(post("/api/v1/fulfillment/webhook")
                                    .header("X-Webhook-Secret", TEST_SECRET)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(payload)))
                            .andReturn();
                    statusCodes.add(result.getResponse().getStatus());
                } catch (Exception e) {
                    // ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(statusCodes).containsOnly(200);

        var events = eventRepository.findByOrderCode("GHN-TRACK-CONCURRENT");
        assertThat(events).hasSize(1);
    }

    // ========================================================================
    // Task 3: Idempotency Key Fingerprint Tests
    // ========================================================================

    @Autowired
    private GhnWebhookService webhookService;

    @Autowired
    private GhnWebhookProcessor webhookProcessor;

    @Test
    @DisplayName("Task 3.1 Cùng OrderCode + Type nhưng khác Time tạo thành 2 idempotency key khác nhau")
    void testDifferentValidTimesCreateDifferentKeys() {
        WebhookPayloadDto p1 = createValidPayload("GHN-TRACK-TIME", "storing", "2026-10-18T10:00:00Z");
        WebhookPayloadDto p2 = createValidPayload("GHN-TRACK-TIME", "storing", "2026-10-18T11:00:00Z");

        String k1 = webhookService.buildIdempotencyKey(p1, "payload1");
        String k2 = webhookService.buildIdempotencyKey(p2, "payload2");

        assertThat(k1).isNotEqualTo(k2);
        assertThat(k1).contains("2026-10-18T10:00:00Z");
        assertThat(k2).contains("2026-10-18T11:00:00Z");
    }

    @Test
    @DisplayName("Task 3.2 Cùng OrderCode + Type, thiếu Time, khác nội dung tạo fingerprint khác nhau")
    void testMissingTimeWithDifferentPayloadsProducesDifferentKeys() {
        WebhookPayloadDto p1 = createValidPayload("GHN-TRACK-NO-TIME", "storing", null);
        WebhookPayloadDto p2 = createValidPayload("GHN-TRACK-NO-TIME", "delivering", null);

        String k1 = webhookService.buildIdempotencyKey(p1, "{\"status\":\"storing\"}");
        String k2 = webhookService.buildIdempotencyKey(p2, "{\"status\":\"delivering\"}");

        assertThat(k1).isNotEqualTo(k2);
        assertThat(k1).contains("GHN-TRACK-NO-TIME:switch_status:FP:");
        assertThat(k2).contains("GHN-TRACK-NO-TIME:switch_status:FP:");
    }

    @Test
    @DisplayName("Task 3.3 Cùng OrderCode + Type, thiếu Time, trùng nội dung tạo cùng fingerprint và deduplicate")
    void testMissingTimeWithIdenticalPayloadDeduplicates() throws Exception {
        WebhookPayloadDto p1 = createValidPayload("GHN-TRACK-NO-TIME-DUP", "storing", null);

        // Gửi lần 1
        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p1)))
                .andExpect(status().isOk());

        // Gửi lần 2
        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p1)))
                .andExpect(status().isOk());

        var events = eventRepository.findByOrderCode("GHN-TRACK-NO-TIME-DUP");
        assertThat(events).hasSize(1);
    }

    // ========================================================================
    // Task 2: Status Mapping (storing without picked advances to in-transit)
    // ========================================================================

    @Test
    @DisplayName("Task 2.1 Bỏ lỡ 'picked', nhận trực tiếp 'storing' kích hoạt applyCarrierInTransit")
    void testMissedPickedReceivingStoringAdvancesInTransit() throws Exception {
        ShipmentEntity shipment = new ShipmentEntity(910L, "OG-DEV-910-STORING", NOW);
        shipment.setTrackingNumber("GHN-ORDER-STORING");
        shipmentRepository.save(shipment);

        WebhookPayloadDto payload = createValidPayload("GHN-ORDER-STORING", "storing", "2026-10-18T10:00:00Z");

        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .header("X-Webhook-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Thread.sleep(200);

        verify(stateService).applyCarrierInTransit(eq("GHN-ORDER-STORING"), any(), eq("storing"));

        shipmentRepository.delete(shipment);
    }

    // ========================================================================
    // Task 1: Durable Processing & Recovery Tests
    // ========================================================================

    @Test
    @DisplayName("Task 1.1 Recovery worker tìm lại event PENDING chưa dispatch (giả lập crash) và xử lý thành công")
    void testRecoveryWorkerProcessesPendingEvents() {
        ShipmentEntity shipment = new ShipmentEntity(911L, "OG-DEV-911-RECOVER", NOW);
        shipment.setTrackingNumber("GHN-RECOVER-PENDING");
        shipmentRepository.save(shipment);

        WebhookPayloadDto payload = createValidPayload("GHN-RECOVER-PENDING", "delivered", "2026-10-18T10:00:00Z");
        GhnWebhookEventEntity entity = new GhnWebhookEventEntity(
                "GHN-RECOVER-PENDING", "OG-DEV-911-RECOVER", "switch_status", NOW,
                "GHN-RECOVER-PENDING:switch_status:2026-10-18T10:00:00Z",
                objectMapper.valueToTree(payload).toString(), NOW
        );
        eventRepository.save(entity);

        // Gọi recovery worker
        int recovered = webhookProcessor.recoverAndProcessPendingEvents();
        assertThat(recovered).isGreaterThanOrEqualTo(1);

        GhnWebhookEventEntity updated = eventRepository.findById(entity.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GhnWebhookEventEntity.STATUS_PROCESSED);

        verify(stateService).applyCarrierDelivered(eq("GHN-RECOVER-PENDING"), any(), eq("delivered"));

        shipmentRepository.delete(shipment);
    }

    @Test
    @DisplayName("Task 1.2 Event bị kẹt ở PROCESSING vượt ngưỡng stale threshold được recovery worker reclaim")
    void testStaleProcessingEventReclaimedAndProcessed() {
        ShipmentEntity shipment = new ShipmentEntity(912L, "OG-DEV-912-STALE", NOW);
        shipment.setTrackingNumber("GHN-RECOVER-STALE");
        shipmentRepository.save(shipment);

        WebhookPayloadDto payload = createValidPayload("GHN-RECOVER-STALE", "delivered", "2026-10-18T10:00:00Z");
        GhnWebhookEventEntity entity = new GhnWebhookEventEntity(
                "GHN-RECOVER-STALE", "OG-DEV-912-STALE", "switch_status", NOW,
                "GHN-RECOVER-STALE:switch_status:2026-10-18T10:00:00Z",
                objectMapper.valueToTree(payload).toString(), NOW
        );
        entity.markProcessing(Instant.now().minus(Duration.ofMinutes(10))); // Bắt đầu xử lý từ 10 phút trước (stale)
        eventRepository.save(entity);

        int recovered = webhookProcessor.recoverAndProcessPendingEvents();
        assertThat(recovered).isGreaterThanOrEqualTo(1);

        GhnWebhookEventEntity updated = eventRepository.findById(entity.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GhnWebhookEventEntity.STATUS_PROCESSED);

        verify(stateService).applyCarrierDelivered(eq("GHN-RECOVER-STALE"), any(), eq("delivered"));

        shipmentRepository.delete(shipment);
    }

    @Test
    @DisplayName("Task 1.3 Hai worker cùng gọi processEvent: atomic claim đảm bảo chỉ đúng 1 worker thực thi")
    void testConcurrentWorkersOnlyOneProcessesEvent() throws Exception {
        ShipmentEntity shipment = new ShipmentEntity(913L, "OG-DEV-913-RACE", NOW);
        shipment.setTrackingNumber("GHN-RACE-WORKER");
        shipmentRepository.save(shipment);

        WebhookPayloadDto payload = createValidPayload("GHN-RACE-WORKER", "delivered", "2026-10-18T10:00:00Z");
        GhnWebhookEventEntity entity = new GhnWebhookEventEntity(
                "GHN-RACE-WORKER", "OG-DEV-913-RACE", "switch_status", NOW,
                "GHN-RACE-WORKER:switch_status:2026-10-18T10:00:00Z",
                objectMapper.valueToTree(payload).toString(), NOW
        );
        eventRepository.save(entity);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    webhookProcessor.processEvent(entity.getId());
                } catch (Exception e) {
                    // ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        GhnWebhookEventEntity updated = eventRepository.findById(entity.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GhnWebhookEventEntity.STATUS_PROCESSED);
        // Xác minh chỉ gọi applyCarrierDelivered đúng 1 lần duy nhất
        verify(stateService, times(1)).applyCarrierDelivered(eq("GHN-RACE-WORKER"), any(), eq("delivered"));

        shipmentRepository.delete(shipment);
    }
}
