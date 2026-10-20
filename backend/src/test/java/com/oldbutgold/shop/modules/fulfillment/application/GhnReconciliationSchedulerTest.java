package com.oldbutgold.shop.modules.fulfillment.application;

import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnApiException;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnClient;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnProperties;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnProperties.ReconciliationProperties;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.OrderDetailResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GhnReconciliationSchedulerTest {

    private ShipmentRepository shipmentRepository;
    private GhnClient ghnClient;
    private GhnProperties ghnProperties;
    private FulfillmentStateService stateService;
    private Clock clock;
    private GhnReconciliationScheduler scheduler;

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    @BeforeEach
    void setUp() {
        shipmentRepository = mock(ShipmentRepository.class);
        ghnClient = mock(GhnClient.class);
        ghnProperties = new GhnProperties(
                "https://dev-online-gateway.ghn.vn/shiip/public-api/",
                "test-token",
                229762,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                "test-secret",
                new ReconciliationProperties(true, "0 */30 * * * *", 50)
        );
        stateService = mock(FulfillmentStateService.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);

        scheduler = new GhnReconciliationScheduler(
                shipmentRepository,
                ghnClient,
                ghnProperties,
                stateService,
                clock
        );

        when(shipmentRepository.acquireClaim(any(), any(), any(), any())).thenReturn(1);
        when(shipmentRepository.recordReconciledTracking(any(), any(), any(), any(), any())).thenReturn(1);
        when(shipmentRepository.updateRawGhnStatusIfPending(any(), any(), any())).thenReturn(1);
        when(shipmentRepository.updateRawGhnStatusIfNotTerminal(any(), any(), any())).thenReturn(1);
        when(shipmentRepository.recordIncidentIfNotTerminal(any(), any(), any(), any())).thenReturn(1);
    }

    private OrderDetailResponseDto createDetailResponse(String orderCode, String status) {
        return new OrderDetailResponseDto(
                orderCode,
                status,
                "CHOTHUHANG",
                "Shop",
                "0912345678",
                "From Address",
                "20101",
                1442,
                "Buyer",
                "0987654321",
                "To Address",
                "20311",
                1444
        );
    }

    @Test
    @DisplayName("E1. Đối soát quét các shipment đang active và cập nhật trạng thái tương ứng")
    void testReconciliationUpdatesActiveShipment() {
        ShipmentEntity shipment = new ShipmentEntity(101L, "OG-DEV-101", NOW);
        shipment.recordGhnCreated("GHN-RECON-1", BigDecimal.valueOf(20900), NOW.plus(Duration.ofDays(2)), NOW);

        when(shipmentRepository.findActiveShipmentsForReconciliation(any(Pageable.class)))
                .thenReturn(List.of(shipment));

        when(ghnClient.getOrderDetail("GHN-RECON-1"))
                .thenReturn(createDetailResponse("GHN-RECON-1", "delivered"));
        when(stateService.markOrderDelivered(eq(101L), any(), eq("GHN_RECONCILIATION")))
                .thenReturn(FulfillmentStateService.DeliveryTransitionResult.ofSuccess());

        var report = scheduler.reconcileActiveShipments();

        assertThat(report.checked()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(0);

        verify(stateService).markOrderDelivered(eq(101L), any(), eq("GHN_RECONCILIATION"));
    }

    @Test
    @DisplayName("E2. Một shipment lỗi không làm dừng các shipment tiếp theo trong batch")
    void testReconciliationErrorOnOneDoesNotAbortBatch() {
        ShipmentEntity shipment1 = new ShipmentEntity(101L, "OG-DEV-101", NOW);
        shipment1.recordGhnCreated("GHN-FAIL-1", BigDecimal.valueOf(20900), NOW.plus(Duration.ofDays(2)), NOW);

        ShipmentEntity shipment2 = new ShipmentEntity(102L, "OG-DEV-102", NOW);
        shipment2.recordGhnCreated("GHN-SUCCESS-2", BigDecimal.valueOf(20900), NOW.plus(Duration.ofDays(2)), NOW);

        when(shipmentRepository.findActiveShipmentsForReconciliation(any(Pageable.class)))
                .thenReturn(List.of(shipment1, shipment2));

        // Shipment 1 lỗi mạng / 500 từ GHN
        when(ghnClient.getOrderDetail("GHN-FAIL-1"))
                .thenThrow(new GhnApiException("GHN timeout"));

        // Shipment 2 thành công
        when(ghnClient.getOrderDetail("GHN-SUCCESS-2"))
                .thenReturn(createDetailResponse("GHN-SUCCESS-2", "transporting"));

        var report = scheduler.reconcileActiveShipments();

        assertThat(report.checked()).isEqualTo(2);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(1);

        verify(stateService).applyCarrierInTransit(eq("GHN-SUCCESS-2"), any(), eq("transporting"));
    }

    @Test
    @DisplayName("E3. Trạng thái GHN chưa biết không làm thay đổi trạng thái shipment")
    void testReconciliationUnknownStatusDoesNotMutateState() {
        ShipmentEntity shipment = new ShipmentEntity(103L, "OG-DEV-103", NOW);
        shipment.recordGhnCreated("GHN-UNKNOWN-3", BigDecimal.valueOf(20900), NOW.plus(Duration.ofDays(2)), NOW);

        when(shipmentRepository.findActiveShipmentsForReconciliation(any(Pageable.class)))
                .thenReturn(List.of(shipment));

        when(ghnClient.getOrderDetail("GHN-UNKNOWN-3"))
                .thenReturn(createDetailResponse("GHN-UNKNOWN-3", "some_brand_new_ghn_status"));

        var report = scheduler.reconcileActiveShipments();

        assertThat(report.checked()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(0);

        verify(stateService, never()).applyCarrierDelivered(any(), any(), any());
        verify(stateService, never()).applyCarrierPickedUp(any(), any(), any());
        verify(stateService, never()).applyCarrierInTransit(any(), any(), any());
    }

    @Test
    @DisplayName("E4. Khi cấu hình reconciliation.enabled=false, job không chạy")
    void testReconciliationDisabledDoesNotRun() {
        GhnProperties disabledProps = new GhnProperties(
                "https://dev-online-gateway.ghn.vn/shiip/public-api/",
                "test-token",
                229762,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                "test-secret",
                new ReconciliationProperties(false, "0 */30 * * * *", 50)
        );

        GhnReconciliationScheduler disabledScheduler = new GhnReconciliationScheduler(
                shipmentRepository,
                ghnClient,
                disabledProps,
                stateService,
                clock
        );

        disabledScheduler.runReconciliation();

        verify(shipmentRepository, never()).findActiveShipmentsForReconciliation(any());
        verify(ghnClient, never()).getOrderDetail(any());
    }

    @Test
    @DisplayName("E5. Đơn PENDING chưa có tracking_number được đối soát qua client_order_code")
    void testUntrackedPendingShipmentReconciledViaClientOrderCode() {
        ShipmentEntity untracked = new ShipmentEntity(105L, "OG-DEV-105-UNTRACKED", NOW.minusSeconds(60));

        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(untracked));
        when(shipmentRepository.findActiveShipmentsForReconciliation(any()))
                .thenReturn(List.of());

        when(ghnClient.getOrderDetailByClientCode("OG-DEV-105-UNTRACKED"))
                .thenReturn(createDetailResponse("GHN-FOUND-105", "transporting"));
        when(shipmentRepository.findByOrderId(105L)).thenReturn(Optional.of(untracked));
        when(shipmentRepository.recordReconciledTracking(eq(105L), any(), eq("GHN-FOUND-105"), any(), any()))
                .thenAnswer(inv -> {
                    untracked.setTrackingNumber("GHN-FOUND-105");
                    return 1;
                });

        var report = scheduler.reconcileActiveShipments();

        assertThat(report.checked()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(untracked.getTrackingNumber()).isEqualTo("GHN-FOUND-105");

        verify(shipmentRepository).recordReconciledTracking(eq(105L), any(), eq("GHN-FOUND-105"), eq("transporting"), any());
        verify(stateService).applyCarrierInTransit(eq("GHN-FOUND-105"), any(), eq("transporting"));
    }

    @Test
    @DisplayName("E6. Đơn PENDING tra cứu qua client_order_code không thấy trên GHN: giữ PENDING, không sinh mã mới")
    void testUntrackedPendingShipmentNotFoundKeepsPending() {
        ShipmentEntity untracked = new ShipmentEntity(106L, "OG-DEV-106-NOTFOUND", NOW.minusSeconds(60));

        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(untracked));
        when(shipmentRepository.findActiveShipmentsForReconciliation(any()))
                .thenReturn(List.of());

        // GHN trả về null (chưa có đơn)
        when(ghnClient.getOrderDetailByClientCode("OG-DEV-106-NOTFOUND")).thenReturn(null);

        var report = scheduler.reconcileActiveShipments();

        assertThat(report.checked()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(0);
        assertThat(untracked.getTrackingNumber()).isNull();
        assertThat(untracked.getClientOrderCode()).isEqualTo("OG-DEV-106-NOTFOUND"); // Không sinh mã mới
    }

    @Test
    @DisplayName("E7. Đơn PENDING tra cứu chưa thấy: áp dụng backoff thời gian nextReconciliationAt tăng dần")
    void testUntrackedPendingShipmentNotFoundAppliesBackoff() {
        ShipmentEntity untracked = new ShipmentEntity(107L, "OG-DEV-107-BACKOFF", NOW.minusSeconds(40));

        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(untracked));
        when(shipmentRepository.findActiveShipmentsForReconciliation(any()))
                .thenReturn(List.of());

        when(ghnClient.getOrderDetailByClientCode("OG-DEV-107-BACKOFF")).thenReturn(null);

        var report = scheduler.reconcileUntrackedPendingShipments();

        assertThat(report.checked()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        // Kiểm tra nextReconciliationAt đã được đặt trong tương lai (30s) và attempts = 1
        assertThat(untracked.getNextReconciliationAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(untracked.getReconciliationAttempts()).isEqualTo(1);
        assertThat(untracked.getRawGhnStatus()).isNull();
        verify(shipmentRepository).releaseClaimWithBackoff(eq(107L), any(), eq(NOW.plusSeconds(30)), eq(1), any(), eq(NOW));
        verify(shipmentRepository, never()).save(untracked);
        verify(shipmentRepository).releaseClaim(eq(107L), any(), any());
    }

    @Test
    @DisplayName("E8. Đơn PENDING đối soát quá 30 phút vẫn chưa thấy: chuyển sang admin_review_required để dừng polling")
    void testUntrackedPendingShipmentExhaustedMarksAdminReviewRequired() {
        // Đơn đã tạo từ 35 phút trước
        ShipmentEntity untracked = new ShipmentEntity(108L, "OG-DEV-108-EXHAUSTED", NOW.minus(Duration.ofMinutes(35)));

        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(untracked));
        when(shipmentRepository.findActiveShipmentsForReconciliation(any()))
                .thenReturn(List.of());

        when(ghnClient.getOrderDetailByClientCode("OG-DEV-108-EXHAUSTED")).thenReturn(null);

        var report = scheduler.reconcileUntrackedPendingShipments();

        assertThat(report.checked()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(untracked.isAdminReviewRequired()).isTrue();
        assertThat(untracked.getIncidentNotes()).contains("hơn 30 phút");
        assertThat(untracked.getRawGhnStatus()).isEqualTo("untracked_timeout_exhausted");
        verify(shipmentRepository).recordIncidentAndReleaseClaim(eq(108L), any(), eq("untracked_timeout_exhausted"), any(), eq(1), eq(NOW));
        verify(shipmentRepository, never()).save(untracked);
        verify(shipmentRepository).releaseClaim(eq(108L), any(), any());
    }

    @Test
    @DisplayName("E9. Lỗi tạm thời (5xx/timeout) khi tra cứu untracked: ghi nhận lỗi, đặt backoff 60s, không dừng batch")
    void testUntrackedPendingTransientErrorDoesNotAbortBatchAndSetsBackoff() {
        ShipmentEntity untracked1 = new ShipmentEntity(109L, "OG-DEV-109-ERR", NOW.minusSeconds(50));
        ShipmentEntity untracked2 = new ShipmentEntity(110L, "OG-DEV-110-OK", NOW.minusSeconds(50));

        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(untracked1, untracked2));
        when(shipmentRepository.findActiveShipmentsForReconciliation(any()))
                .thenReturn(List.of());

        // Đơn 1 gặp lỗi mạng
        when(ghnClient.getOrderDetailByClientCode("OG-DEV-109-ERR"))
                .thenThrow(new GhnApiException("GHN 503 Service Unavailable"));

        // Đơn 2 thành công
        when(ghnClient.getOrderDetailByClientCode("OG-DEV-110-OK"))
                .thenReturn(createDetailResponse("GHN-FOUND-110", "ready_to_pick"));
        when(shipmentRepository.findByOrderId(110L)).thenReturn(Optional.of(untracked2));
        when(shipmentRepository.recordReconciledTracking(eq(110L), any(), eq("GHN-FOUND-110"), any(), any()))
                .thenAnswer(inv -> {
                    untracked2.setTrackingNumber("GHN-FOUND-110");
                    return 1;
                });

        var report = scheduler.reconcileUntrackedPendingShipments();

        assertThat(report.checked()).isEqualTo(2);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(1);

        // Đơn 1 được đặt backoff 60s qua nextReconciliationAt và tăng attempts
        assertThat(untracked1.getNextReconciliationAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(untracked1.getReconciliationAttempts()).isEqualTo(1);
        // Đơn 2 cập nhật tracking number thành công qua atomic query
        assertThat(untracked2.getTrackingNumber()).isEqualTo("GHN-FOUND-110");
        verify(shipmentRepository).recordReconciledTracking(eq(110L), any(), eq("GHN-FOUND-110"), eq("ready_to_pick"), any());
        verify(shipmentRepository).releaseClaim(eq(109L), any(), any());
        verify(shipmentRepository).releaseClaim(eq(110L), any(), any());
    }

    @Test
    @DisplayName("E10. Khi không giành được claim lease (đang có on-demand hoặc process khác xử lý), scheduler bỏ qua đơn đó")
    void testUntrackedPendingClaimNotAcquiredSkipsShipment() {
        ShipmentEntity untracked = new ShipmentEntity(111L, "OG-DEV-111-LOCKED", NOW.minusSeconds(50));

        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(untracked));
        // Không giành được claim
        when(shipmentRepository.acquireClaim(eq(111L), any(), any(), any())).thenReturn(0);

        var report = scheduler.reconcileUntrackedPendingShipments();

        assertThat(report.checked()).isEqualTo(0);
        assertThat(report.skipped()).isEqualTo(1);
        verify(ghnClient, never()).getOrderDetailByClientCode(any());
        verify(shipmentRepository, never()).releaseClaim(eq(111L), any(), any());
    }

    @Test
    @DisplayName("E11. Token-safe claim release: Scheduler worker giải phóng claim chỉ với chính token đã giành được, không xóa claim của worker mới")
    void testTokenSafeClaimReleaseUsesMatchingToken() {
        ShipmentEntity untracked = new ShipmentEntity(112L, "OG-DEV-112-TOKENSAFE", NOW.minusSeconds(50));

        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(untracked));

        org.mockito.ArgumentCaptor<String> tokenCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(shipmentRepository.acquireClaim(eq(112L), tokenCaptor.capture(), any(), any())).thenReturn(1);

        when(ghnClient.getOrderDetailByClientCode("OG-DEV-112-TOKENSAFE")).thenReturn(null);

        scheduler.reconcileUntrackedPendingShipments();

        String acquiredToken = tokenCaptor.getValue();
        assertThat(acquiredToken).isNotBlank().startsWith("sched-");

        // releaseClaimWithBackoff phải dùng đúng token đã giành
        verify(shipmentRepository).releaseClaimWithBackoff(eq(112L), eq(acquiredToken), any(), anyInt(), any(), any());
        // releaseClaim trong finally cũng dùng đúng token đó
        verify(shipmentRepository).releaseClaim(eq(112L), eq(acquiredToken), any());
        // Tuyệt đối không dùng save() thông thường ghi đè entity stale
        verify(shipmentRepository, never()).save(untracked);
    }

    @Test
    @DisplayName("E12. Fenced write: Worker A mất lease, Worker B lấy claim mới, A nhận kết quả muộn bị chặn ghi đè dữ liệu của B")
    void testLateWorkerAIsFencedFromOverwritingWorkerB() {
        long orderId = 113L;
        String clientCode = "OG-DEV-113-FENCED";

        ShipmentEntity shipmentHeldByWorkerB = new ShipmentEntity(orderId, clientCode, NOW.minusSeconds(60));

        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(shipmentHeldByWorkerB));

        // Worker A ban đầu giành claim
        org.mockito.ArgumentCaptor<String> tokenCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(shipmentRepository.acquireClaim(eq(orderId), tokenCaptor.capture(), any(), any())).thenReturn(1);

        // GHN phản hồi muộn cho Worker A (sau thời gian lease hết hạn)
        when(ghnClient.getOrderDetailByClientCode(clientCode))
                .thenReturn(createDetailResponse("GHN-LATE-113", "transporting"));

        // Worker B đã giành claim mới (tokenB) trong DB.
        // Khi Worker A cố ghi bằng recordReconciledTracking(orderId, tokenA, ...), DB trả về 0 rows updated
        when(shipmentRepository.recordReconciledTracking(eq(orderId), any(), any(), any(), any()))
                .thenReturn(0);

        var report = scheduler.reconcileUntrackedPendingShipments();

        String workerAToken = tokenCaptor.getValue();
        assertThat(workerAToken).isNotBlank().startsWith("sched-");

        // 1. Worker A gọi recordReconciledTracking nhưng cập nhật 0 rows
        verify(shipmentRepository).recordReconciledTracking(
                eq(orderId),
                eq(workerAToken),
                eq("GHN-LATE-113"),
                eq("transporting"),
                eq(NOW)
        );

        // 2. Tuyệt đối KHÔNG thay đổi trạng thái nghiệp vụ qua stateService
        verify(stateService, never()).applyCarrierInTransit(eq("GHN-LATE-113"), any(), any());
        verify(stateService, never()).applyCarrierPickedUp(any(), any(), any());
        verify(stateService, never()).applyCarrierDelivered(any(), any(), any());

        // 3. Tuyệt đối KHÔNG gọi save() ghi đè entity
        verify(shipmentRepository, never()).save(shipmentHeldByWorkerB);

        // 4. Report ghi nhận bỏ qua (skipped)
        assertThat(report.checked()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(0);
        assertThat(report.skipped()).isEqualTo(1);

        // 5. Trong finally, Worker A chỉ gọi releaseClaim với đúng token của mình (match 0 rows, không xóa token của B)
        verify(shipmentRepository).releaseClaim(eq(orderId), eq(workerAToken), any());
    }

    @Test
    @DisplayName("E13 Reconciliation đơn PENDING: Webhook đã cập nhật DELIVERED trước khi scheduler chuyển trạng thái thì trạng thái cũ 'picked' bị chặn")
    void testMonotonicReconciliationPreventsOldPickedUpFromOverwritingWebhookDelivered() {
        long orderId = 205L;
        String clientCode = "OG-DEV-205-MONO";
        String trackingCode = "GHN-MONO-205";

        ShipmentEntity initialPending = new ShipmentEntity(orderId, clientCode, NOW.minusSeconds(120));
        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(initialPending));

        when(shipmentRepository.acquireClaim(eq(orderId), any(), any(), any())).thenReturn(1);

        // GHN trả về kết quả cũ: đơn mới chỉ "picked"
        when(ghnClient.getOrderDetailByClientCode(clientCode))
                .thenReturn(createDetailResponse(trackingCode, "picked"));

        when(shipmentRepository.recordReconciledTracking(eq(orderId), any(), eq(trackingCode), any(), any()))
                .thenReturn(1);

        // Giữa lúc recordReconciledTracking và applyReconciliationStatus, Webhook đã chuyển shipment sang DELIVERED
        ShipmentEntity webhookDeliveredShipment = new ShipmentEntity(orderId, clientCode, NOW.minusSeconds(120));
        webhookDeliveredShipment.setTrackingNumber(trackingCode);
        webhookDeliveredShipment.markDelivered(NOW.minusSeconds(10), NOW.minusSeconds(50), NOW.minusSeconds(10));
        assertThat(webhookDeliveredShipment.isTerminal()).isTrue();
        assertThat(webhookDeliveredShipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);

        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(webhookDeliveredShipment));

        var report = scheduler.reconcileUntrackedPendingShipments();

        // Kiểm chứng:
        // 1. Tuyệt đối KHÔNG gọi stateService.applyCarrierPickedUp vì shipment đã DELIVERED
        verify(stateService, never()).applyCarrierPickedUp(any(), any(), any());
        verify(stateService, never()).applyCarrierInTransit(any(), any(), any());

        // 2. Tuyệt đối KHÔNG gọi save() ghi đè shipment đã DELIVERED
        verify(shipmentRepository, never()).save(webhookDeliveredShipment);

        // 3. Trạng thái DELIVERED được bảo toàn
        assertThat(webhookDeliveredShipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
    }

    @Test
    @DisplayName("E14 Reconciliation đơn PENDING: Webhook đã cập nhật IN_TRANSIT thì kết quả cũ 'picking' không được ghi đè lùi rawGhnStatus hay gọi save")
    void testMonotonicReconciliationPreventsOldPickingFromOverwritingWebhookInTransit() {
        long orderId = 206L;
        String clientCode = "OG-DEV-206-PICK";
        String trackingCode = "GHN-PICK-206";

        ShipmentEntity initialPending = new ShipmentEntity(orderId, clientCode, NOW.minusSeconds(90));
        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(initialPending));

        when(shipmentRepository.acquireClaim(eq(orderId), any(), any(), any())).thenReturn(1);

        // GHN trả về trạng thái cũ: "picking"
        when(ghnClient.getOrderDetailByClientCode(clientCode))
                .thenReturn(createDetailResponse(trackingCode, "picking"));

        when(shipmentRepository.recordReconciledTracking(eq(orderId), any(), eq(trackingCode), any(), any()))
                .thenReturn(1);

        // Webhook đã cập nhật sang IN_TRANSIT với raw status "delivering"
        ShipmentEntity webhookInTransitShipment = new ShipmentEntity(orderId, clientCode, NOW.minusSeconds(90));
        webhookInTransitShipment.setTrackingNumber(trackingCode);
        webhookInTransitShipment.markInTransit(NOW.minusSeconds(30), "delivering", NOW.minusSeconds(30));
        assertThat(webhookInTransitShipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_IN_TRANSIT);
        assertThat(webhookInTransitShipment.getRawGhnStatus()).isEqualTo("delivering");

        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(webhookInTransitShipment));

        scheduler.reconcileUntrackedPendingShipments();

        // 1. applyReconciliationStatus không được gọi save() để ghi đè "picking" lên đơn đã IN_TRANSIT
        verify(shipmentRepository, never()).save(webhookInTransitShipment);
        verify(shipmentRepository, never()).updateRawGhnStatusIfPending(eq(orderId), any(), any());

        // 2. Raw status "delivering" và status "IN_TRANSIT" được bảo toàn
        assertThat(webhookInTransitShipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_IN_TRANSIT);
        assertThat(webhookInTransitShipment.getRawGhnStatus()).isEqualTo("delivering");
    }

    @Test
    @DisplayName("E15. TOCTOU Defense: Scheduler thấy PENDING trong bộ nhớ, nhưng Webhook đã chuyển status trước khi UPDATE chạy -> updateRawGhnStatusIfPending trả về 0, không gọi save()")
    void testToctouDefensePreventsStalePendingFromOverwritingConcurrentWebhook() {
        long orderId = 207L;
        String clientCode = "OG-DEV-207-TOCTOU";
        String trackingCode = "GHN-TOCTOU-207";

        ShipmentEntity initialPending = new ShipmentEntity(orderId, clientCode, NOW.minusSeconds(90));
        when(shipmentRepository.findUntrackedPendingShipmentsForReconciliation(any(), any(), any()))
                .thenReturn(List.of(initialPending));

        when(shipmentRepository.acquireClaim(eq(orderId), any(), any(), any())).thenReturn(1);

        // GHN trả về kết quả là "picking"
        when(ghnClient.getOrderDetailByClientCode(clientCode))
                .thenReturn(createDetailResponse(trackingCode, "picking"));

        when(shipmentRepository.recordReconciledTracking(eq(orderId), any(), eq(trackingCode), any(), any()))
                .thenReturn(1);

        // Scheduler nạp freshShipment lúc vẫn PENDING
        ShipmentEntity freshPending = new ShipmentEntity(orderId, clientCode, NOW.minusSeconds(90));
        freshPending.setTrackingNumber(trackingCode);
        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(freshPending));

        // TOCTOU Race Condition: Ngay trước khi updateRawGhnStatusIfPending chạy, Webhook đã chuyển status sang IN_TRANSIT trong DB!
        // Do đó, câu lệnh UPDATE ... WHERE status = 'PENDING' trong DB trả về 0 rows!
        when(shipmentRepository.updateRawGhnStatusIfPending(eq(orderId), eq("picking"), any()))
                .thenReturn(0);

        var report = scheduler.reconcileUntrackedPendingShipments();

        // 1. Scheduler gọi câu query nguyên tử có điều kiện bảo vệ WHERE status = 'PENDING'
        verify(shipmentRepository).updateRawGhnStatusIfPending(eq(orderId), eq("picking"), eq(NOW));

        // 2. Tuyệt đối KHÔNG gọi save() làm ghi đè lost update lên database
        verify(shipmentRepository, never()).save(any());

        // 3. rawGhnStatus của freshPending không bị gán nếu DB trả về 0 rows
        assertThat(freshPending.getRawGhnStatus()).isNull();
    }
}
