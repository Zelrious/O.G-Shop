package com.oldbutgold.shop.modules.fulfillment.application;

import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnClient;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnProperties;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.OrderDetailResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class GhnReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(GhnReconciliationScheduler.class);
    private static final Duration UNTRACKED_COOLDOWN = Duration.ofSeconds(30);

    private final ShipmentRepository shipmentRepository;
    private final GhnClient ghnClient;
    private final GhnProperties ghnProperties;
    private final FulfillmentStateService stateService;
    private final Clock clock;

    public GhnReconciliationScheduler(
            ShipmentRepository shipmentRepository,
            GhnClient ghnClient,
            GhnProperties ghnProperties,
            FulfillmentStateService stateService,
            Clock clock
    ) {
        this.shipmentRepository = shipmentRepository;
        this.ghnClient = ghnClient;
        this.ghnProperties = ghnProperties;
        this.stateService = stateService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.ghn.reconciliation.cron:0 */30 * * * *}")
    public void runReconciliation() {
        if (!isReconciliationEnabled()) {
            return;
        }
        reconcileActiveShipments();
    }

    @Scheduled(fixedDelayString = "${app.ghn.reconciliation.untracked-delay-ms:60000}", initialDelay = 15000)
    public void runUntrackedPendingReconciliation() {
        if (!isReconciliationEnabled()) {
            return;
        }
        reconcileUntrackedPendingShipments();
    }

    public ReconciliationReport reconcileUntrackedPendingShipments() {
        int batchSize = (ghnProperties.reconciliation() != null && ghnProperties.reconciliation().batchSize() > 0)
                ? Math.max(10, ghnProperties.reconciliation().batchSize() / 2)
                : 20;

        int checked = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;

        Instant now = clock.instant();
        Instant cooldownThreshold = now.minus(UNTRACKED_COOLDOWN);
        List<ShipmentEntity> untrackedCandidates = shipmentRepository.findUntrackedPendingShipmentsForReconciliation(
                now,
                cooldownThreshold,
                PageRequest.of(0, batchSize)
        );

        for (ShipmentEntity shipment : untrackedCandidates) {
            String clientCode = shipment.getClientOrderCode();
            if (clientCode == null || clientCode.isBlank()) {
                skipped++;
                continue;
            }

            // Giành claim lease ngắn hạn (30s) chống race condition với on-demand tracking hoặc scheduler khác
            String leaseToken = "sched-" + UUID.randomUUID();
            int acquired = shipmentRepository.acquireClaim(shipment.getOrderId(), leaseToken, now.plusSeconds(30), now);
            if (acquired == 0) {
                // Đang có tiến trình khác giữ claim
                skipped++;
                continue;
            }

            checked++;
            try {
                OrderDetailResponseDto detail = ghnClient.getOrderDetailByClientCode(clientCode);
                if (detail != null && detail.orderCode() != null && !detail.orderCode().isBlank()) {
                    log.info("Reconciliation tìm thấy đơn tạo thành công trên GHN cho clientOrderCode={}: orderCode={}",
                            clientCode, detail.orderCode());
                    String rawStatus = (detail.status() != null && !detail.status().isBlank())
                            ? detail.status().trim().toLowerCase(Locale.ROOT)
                            : "ready_to_pick";

                    int updatedRows = shipmentRepository.recordReconciledTracking(
                            shipment.getOrderId(),
                            leaseToken,
                            detail.orderCode(),
                            rawStatus,
                            now
                    );

                    if (updatedRows > 0) {
                        ShipmentEntity freshShipment = shipmentRepository.findByOrderId(shipment.getOrderId())
                                .orElse(shipment);
                        applyReconciliationStatus(freshShipment, detail.status(), now);
                        updated++;
                    } else {
                        log.warn("Scheduler worker với leaseToken={} không còn sở hữu claim cho orderId={} (fenced write prevented overwriting newer claim).",
                                leaseToken, shipment.getOrderId());
                        skipped++;
                    }
                } else {
                    // GHN chưa có đơn (chưa tạo hoặc có độ trễ indexing): áp dụng backoff có giới hạn qua query nguyên tử
                    applyUntrackedBackoff(shipment, leaseToken, now);
                    skipped++;
                }
            } catch (Exception ex) {
                failed++;
                log.warn("Reconciliation lỗi theo clientOrderCode={} cho orderId={}: {}", clientCode, shipment.getOrderId(), ex.getMessage());
                // Lỗi tạm thời khi kết nối GHN: ghi nhận lần thử và backoff 60s qua atomic token-safe query
                int attempts = shipment.getReconciliationAttempts() + 1;
                shipmentRepository.releaseClaimWithBackoff(
                        shipment.getOrderId(),
                        leaseToken,
                        now.plusSeconds(60),
                        attempts,
                        "Lỗi tạm thời khi đối soát GHN: " + ex.getMessage(),
                        now
                );
                shipment.incrementReconciliationAttempts();
                shipment.setNextReconciliationAt(now.plusSeconds(60));
            } finally {
                // Luôn giải phóng claim lease sau khi kết thúc outbound call (idempotent nếu đã giải phóng)
                shipmentRepository.releaseClaim(shipment.getOrderId(), leaseToken, clock.instant());
            }
        }

        return new ReconciliationReport(checked, updated, skipped, failed);
    }

    private void applyUntrackedBackoff(ShipmentEntity shipment, String leaseToken, Instant now) {
        int attempts = shipment.getReconciliationAttempts() + 1;
        Instant created = shipment.getCreatedAt() != null ? shipment.getCreatedAt() : now;
        Duration age = Duration.between(created, now);

        if (age.toMinutes() >= 30 || attempts >= 10) {
            log.warn("Shipment orderId={}, clientOrderCode={} đã đối soát {} lần trong hơn 30 phút nhưng GHN chưa ghi nhận. Chuyển Admin review.",
                    shipment.getOrderId(), shipment.getClientOrderCode(), attempts);
            String incidentNotes = "Đã đối soát " + attempts + " lần trong hơn 30 phút nhưng GHN chưa ghi nhận đơn. Cần kiểm tra với GHN hoặc tạo lại sau khi xác minh.";
            shipmentRepository.recordIncidentAndReleaseClaim(
                    shipment.getOrderId(),
                    leaseToken,
                    "untracked_timeout_exhausted",
                    incidentNotes,
                    attempts,
                    now
            );
            shipment.recordIncident("untracked_timeout_exhausted", incidentNotes, now);
            shipment.setReconciliationAttempts(attempts);
            return;
        }

        long backoffSeconds;
        if (age.getSeconds() < 60) {
            backoffSeconds = 30;
        } else if (age.getSeconds() < 300) {
            backoffSeconds = 60;
        } else if (age.getSeconds() < 900) {
            backoffSeconds = 120;
        } else {
            backoffSeconds = 300;
        }

        Instant nextRecon = now.plusSeconds(backoffSeconds);
        String notes = "Đang chờ GHN xác nhận vận đơn (lần đối soát thứ " + attempts + " lúc " + now + ")";
        shipmentRepository.releaseClaimWithBackoff(
                shipment.getOrderId(),
                leaseToken,
                nextRecon,
                attempts,
                notes,
                now
        );
        shipment.setNextReconciliationAt(nextRecon);
        shipment.setReconciliationAttempts(attempts);
        shipment.setIncidentNotes(notes);
    }

    public ReconciliationReport reconcileActiveShipments() {
        int batchSize = (ghnProperties.reconciliation() != null && ghnProperties.reconciliation().batchSize() > 0)
                ? ghnProperties.reconciliation().batchSize()
                : 50;

        // 1. Nhánh đối soát cho các đơn PENDING chưa có tracking_number
        ReconciliationReport untrackedReport = reconcileUntrackedPendingShipments();
        int checked = untrackedReport.checked();
        int updated = untrackedReport.updated();
        int skipped = untrackedReport.skipped();
        int failed = untrackedReport.failed();

        Instant now = clock.instant();

        // 2. Nhánh đối soát cho các đơn đang hoạt động đã có tracking_number (loại trừ các đơn có admin_review_required)
        List<ShipmentEntity> trackedCandidates = shipmentRepository.findActiveShipmentsForReconciliation(PageRequest.of(0, batchSize));

        for (ShipmentEntity shipment : trackedCandidates) {
            String trackingNumber = shipment.getTrackingNumber();
            if (trackingNumber == null || trackingNumber.isBlank()) {
                skipped++;
                continue;
            }

            checked++;
            try {
                OrderDetailResponseDto detail = ghnClient.getOrderDetail(trackingNumber);
                if (detail == null || detail.status() == null || detail.status().isBlank()) {
                    log.warn("Reconciliation: GHN không trả về thông tin trạng thái cho trackingNumber={}", trackingNumber);
                    skipped++;
                    continue;
                }

                boolean changed = applyReconciliationStatus(shipment, detail.status(), now);
                if (changed) {
                    updated++;
                } else {
                    skipped++;
                }
            } catch (Exception ex) {
                failed++;
                log.warn("Reconciliation lỗi cho trackingNumber={}: {}", trackingNumber, ex.getMessage());
            }
        }

        log.info("GHN Reconciliation hoàn tất: checked={}, updated={}, skipped={}, failed={}",
                checked, updated, skipped, failed);

        return new ReconciliationReport(checked, updated, skipped, failed);
    }

    private boolean applyReconciliationStatus(ShipmentEntity shipment, String rawStatus, Instant now) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return false;
        }

        String normalizedStatus = rawStatus.trim().toLowerCase(Locale.ROOT);
        String trackingNumber = shipment.getTrackingNumber();
        String currentStatus = shipment.getStatus();

        if (shipment.isTerminal()) {
            log.info("Reconciliation bỏ qua trackingNumber={} vì đã ở trạng thái kết thúc ({})", trackingNumber, currentStatus);
            return false;
        }

        switch (normalizedStatus) {
            case "ready_to_pick", "picking", "money_collect_picking" -> {
                if (ShipmentEntity.STATUS_PENDING.equals(currentStatus) && !normalizedStatus.equals(shipment.getRawGhnStatus())) {
                    int updated = shipmentRepository.updateRawGhnStatusIfPending(shipment.getOrderId(), normalizedStatus, now);
                    if (updated > 0) {
                        shipment.setRawGhnStatus(normalizedStatus);
                        return true;
                    }
                }
                return false;
            }
            case "picked" -> {
                if (ShipmentEntity.STATUS_PENDING.equals(currentStatus)) {
                    stateService.applyCarrierPickedUp(trackingNumber, now, normalizedStatus);
                    return true;
                }
                return false;
            }
            case "storing", "sorting", "transporting", "delivering", "money_collect_delivering", "delivery_fail" -> {
                if (ShipmentEntity.STATUS_PENDING.equals(currentStatus) || ShipmentEntity.STATUS_PICKED_UP.equals(currentStatus)) {
                    stateService.applyCarrierInTransit(trackingNumber, now, normalizedStatus);
                    return true;
                }
                return false;
            }
            case "delivered" -> {
                if (!ShipmentEntity.STATUS_DELIVERED.equals(currentStatus)) {
                    stateService.applyCarrierDelivered(trackingNumber, now, "delivered");
                    return true;
                }
                return false;
            }
            case "waiting_to_return", "return", "returning", "return_transporting", "return_sorting" -> {
                if (!normalizedStatus.equals(shipment.getRawGhnStatus())) {
                    int updated = shipmentRepository.updateRawGhnStatusIfNotTerminal(shipment.getOrderId(), normalizedStatus, now);
                    if (updated > 0) {
                        shipment.setRawGhnStatus(normalizedStatus);
                        return true;
                    }
                }
                return false;
            }
            case "returned" -> {
                if (!ShipmentEntity.STATUS_RETURNED.equals(currentStatus)) {
                    stateService.applyCarrierReturned(trackingNumber, now, "returned");
                    return true;
                }
                return false;
            }
            case "cancel" -> {
                if (!ShipmentEntity.STATUS_FAILED.equals(currentStatus)) {
                    stateService.applyCarrierFailed(trackingNumber, "cancel");
                    return true;
                }
                return false;
            }
            case "return_fail", "damage", "lost", "scrap", "exception" -> {
                // Sự cố nghiêm trọng: gắn cờ adminReviewRequired = true để dừng polling định kỳ và chuyển cho Admin
                if (!shipment.isAdminReviewRequired() || !normalizedStatus.equals(shipment.getRawGhnStatus())) {
                    int updated = shipmentRepository.recordIncidentIfNotTerminal(
                            shipment.getOrderId(),
                            normalizedStatus,
                            "Reconciliation phát hiện sự cố: " + normalizedStatus,
                            now
                    );
                    if (updated > 0) {
                        shipment.recordIncident(normalizedStatus, "Reconciliation phát hiện sự cố: " + normalizedStatus, now);
                        return true;
                    }
                }
                return false;
            }
            default -> {
                log.warn("Reconciliation: nhận status không xác định '{}' cho trackingNumber={}", rawStatus, trackingNumber);
                return false;
            }
        }
    }

    private boolean isReconciliationEnabled() {
        return ghnProperties.reconciliation() == null || ghnProperties.reconciliation().enabled();
    }

    public record ReconciliationReport(int checked, int updated, int skipped, int failed) {}
}
