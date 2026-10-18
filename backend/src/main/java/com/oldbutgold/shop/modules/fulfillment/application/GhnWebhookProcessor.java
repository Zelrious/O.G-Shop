package com.oldbutgold.shop.modules.fulfillment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookPayloadDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.GhnWebhookEventEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.GhnWebhookEventRepository;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class GhnWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(GhnWebhookProcessor.class);
    private static final int MAX_RETRIES = 5;
    private static final Duration STALE_PROCESSING_THRESHOLD = Duration.ofMinutes(2);

    private final GhnWebhookEventRepository eventRepository;
    private final ShipmentRepository shipmentRepository;
    private final FulfillmentStateService stateService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GhnWebhookProcessor(
            GhnWebhookEventRepository eventRepository,
            ShipmentRepository shipmentRepository,
            FulfillmentStateService stateService,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.shipmentRepository = shipmentRepository;
        this.stateService = stateService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Xử lý sự kiện webhook bất đồng bộ khi vừa tiếp nhận.
     */
    @Async
    public void processAsync(Long eventId) {
        try {
            processEvent(eventId);
        } catch (Exception ex) {
            log.error("Lỗi khi xử lý async webhook eventId={}: {}", eventId, ex.getMessage(), ex);
        }
    }

    /**
     * Xử lý sự kiện webhook đồng bộ, nguyên tử và idempotent.
     * Dùng claim SQL nguyên tử để đảm bảo duy nhất 1 worker được phép xử lý event.
     */
    public void processEvent(Long eventId) {
        Instant now = clock.instant();
        Instant staleThreshold = now.minus(STALE_PROCESSING_THRESHOLD);

        // 1. Giành quyền claim nguyên tử trong database
        Integer claimed = transactionTemplate.execute(status ->
                eventRepository.claimEventForProcessing(eventId, now, staleThreshold, MAX_RETRIES)
        );

        if (claimed == null || claimed == 0) {
            log.info("Bỏ qua webhook eventId={}: không thể giành claim (đang được xử lý hoặc đã kết thúc).", eventId);
            return;
        }

        GhnWebhookEventEntity event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("Không tìm thấy webhook eventId={} sau khi claim thành công", eventId);
            return;
        }

        try {
            WebhookPayloadDto payload = objectMapper.readValue(event.getRawPayload(), WebhookPayloadDto.class);

            // 2. Chỉ Type = 'create' và 'switch_status' mới được phép thay đổi trạng thái shipment
            String type = payload.type() != null ? payload.type().trim().toLowerCase(Locale.ROOT) : "";
            if (!"create".equals(type) && !"switch_status".equals(type)) {
                log.info("Bỏ qua thay đổi trạng thái cho webhook eventId={} có Type={}", eventId, payload.type());
                transactionTemplate.execute(status -> {
                    event.markIgnored("Loại sự kiện '" + payload.type() + "' không làm thay đổi trạng thái vận đơn.", now);
                    return eventRepository.save(event);
                });
                return;
            }

            // 3. Tra cứu Shipment tương ứng
            ShipmentEntity shipment = findTargetShipment(payload);
            if (shipment == null) {
                log.warn("Không tìm thấy shipment khớp với orderCode={} hoặc clientOrderCode={}",
                        payload.orderCode(), payload.clientOrderCode());
                transactionTemplate.execute(status -> {
                    event.markUnmatched("Không tìm thấy shipment khớp trong hệ thống.", now);
                    return eventRepository.save(event);
                });
                return;
            }

            // 4. Ánh xạ và chuyển trạng thái an toàn
            applyStateTransition(shipment, payload, now);

            // 5. Cập nhật trạng thái sự kiện thành PROCESSED
            transactionTemplate.execute(status -> {
                event.markProcessed(now);
                return eventRepository.save(event);
            });
            log.info("Xử lý thành công webhook eventId={} cho trackingNumber={}", eventId, shipment.getTrackingNumber());

        } catch (Exception ex) {
            log.error("Lỗi khi xử lý webhook eventId={}: {}", eventId, ex.getMessage(), ex);
            try {
                transactionTemplate.execute(status -> {
                    event.markFailed(ex.getMessage(), MAX_RETRIES, now);
                    return eventRepository.save(event);
                });
            } catch (Exception dbEx) {
                log.error("Không thể lưu trạng thái lỗi cho eventId={}: {}", eventId, dbEx.getMessage());
            }
        }
    }

    /**
     * Scheduled worker tự động phục hồi các event PENDING, PROCESSING bị treo (crash/restart),
     * hoặc FAILED (còn lượt retry).
     */
    @Scheduled(fixedDelayString = "${app.ghn.webhook-recovery.delay-ms:30000}", initialDelay = 5000)
    public int recoverAndProcessPendingEvents() {
        Instant now = clock.instant();
        Instant staleThreshold = now.minus(STALE_PROCESSING_THRESHOLD);

        List<Long> recoverableIds = eventRepository.findRecoverableEventIds(
                staleThreshold,
                MAX_RETRIES,
                PageRequest.of(0, 50)
        );

        int processedCount = 0;
        for (Long id : recoverableIds) {
            try {
                processEvent(id);
                processedCount++;
            } catch (Exception e) {
                log.error("Lỗi khi phục hồi eventId={}: {}", id, e.getMessage());
            }
        }
        return processedCount;
    }

    /**
     * Tương thích ngược cho các lời gọi processPendingEvents.
     */
    public int processPendingEvents() {
        return recoverAndProcessPendingEvents();
    }

    private ShipmentEntity findTargetShipment(WebhookPayloadDto payload) {
        // (a) Tra cứu theo tracking_number = OrderCode
        if (payload.orderCode() != null && !payload.orderCode().isBlank()) {
            Optional<ShipmentEntity> byTracking = shipmentRepository.findByTrackingNumber(payload.orderCode().trim());
            if (byTracking.isPresent()) {
                return byTracking.get();
            }
        }

        // (b) Tra cứu theo client_order_code = ClientOrderCode (xử lý race condition khi webhook đến trước finalize)
        if (payload.clientOrderCode() != null && !payload.clientOrderCode().isBlank()) {
            Optional<ShipmentEntity> byClientCode = shipmentRepository.findByClientOrderCode(payload.clientOrderCode().trim());
            if (byClientCode.isPresent()) {
                ShipmentEntity s = byClientCode.get();
                if (s.getTrackingNumber() == null || s.getTrackingNumber().isBlank()) {
                    s.setTrackingNumber(payload.orderCode());
                    shipmentRepository.save(s);
                }
                return s;
            }
        }

        return null;
    }

    private void applyStateTransition(ShipmentEntity shipment, WebhookPayloadDto payload, Instant now) {
        String rawStatus = payload.status() != null ? payload.status().trim().toLowerCase(Locale.ROOT) : "";
        Instant eventTime = payload.parseTime();
        String trackingNumber = shipment.getTrackingNumber();

        switch (rawStatus) {
            case "ready_to_pick", "picking", "money_collect_picking" -> {
                // Đơn tạo xong hoặc tài xế đang đến lấy: Order vẫn SELLER_CONFIRMED, Shipment giữ PENDING
                shipment.setRawGhnStatus(rawStatus);
                shipmentRepository.save(shipment);
            }
            case "picked" -> {
                // Bưu tá đã lấy hàng từ người bán: chuyển sang SHIPPED (lấy hàng thành công)
                stateService.applyCarrierPickedUp(trackingNumber, eventTime, rawStatus);
            }
            case "storing", "sorting", "transporting", "delivering", "money_collect_delivering" -> {
                // Kiện hàng đang ở kho GHN hoặc đang luân chuyển/giao hàng:
                // Nếu webhook 'picked' trước đó bị mất/bỏ lỡ, applyCarrierInTransit vẫn đảm bảo Order được chuyển sang SHIPPED
                stateService.applyCarrierInTransit(trackingNumber, eventTime, rawStatus);
            }
            case "delivery_fail" -> {
                // Giao hàng không thành công tạm thời (bưu tá sẽ giao lại): giữ IN_TRANSIT
                shipment.setRawGhnStatus("delivery_fail");
                shipmentRepository.save(shipment);
            }
            case "delivered" -> {
                // Giao hàng thành công tới Người mua: chuyển DELIVERED, Order sang DELIVERED
                // Thời hạn 3 ngày đồng kiểm được tính động từ delivered_at + 3 ngày. Tuyệt đối không tự giải ngân escrow.
                stateService.applyCarrierDelivered(trackingNumber, eventTime, rawStatus);
            }
            case "waiting_to_return" -> {
                // Chờ chuyển hoàn về cho Người bán sau khi giao không thành công nhiều lần
                shipment.setRawGhnStatus("waiting_to_return");
                shipmentRepository.save(shipment);
            }
            case "return", "returning", "return_transporting", "return_sorting" -> {
                // Đang trong tiến trình chuyển hoàn về cho Người bán
                shipment.setRawGhnStatus(rawStatus);
                shipmentRepository.save(shipment);
            }
            case "return_fail" -> {
                // Trả hàng về cho Người bán thất bại: giữ trạng thái, ghi nhận sự cố để cảnh báo
                shipment.recordIncident("return_fail", "Giao hoàn trả cho Người bán thất bại.", now);
                shipmentRepository.save(shipment);
            }
            case "returned" -> {
                // Kiện hàng đã hoàn tất trả về Người bán. Không kích hoạt side-effect tài chính không được phép.
                stateService.applyCarrierReturned(trackingNumber, eventTime, rawStatus);
            }
            case "cancel" -> {
                // Vận đơn GHN bị hủy
                stateService.applyCarrierFailed(trackingNumber, rawStatus);
            }
            case "damage", "lost", "scrap", "exception" -> {
                // Sự cố bất thường (hư hỏng, thất lạc, tiêu hủy, sự cố):
                // Giữ nguyên trạng thái shipment, gắn cờ admin_review_required cho hàng đợi Admin (ADMIN-01)
                // Tuyệt đối không tự động hoàn tiền hoặc can thiệp escrow
                String desc = payload.description() != null ? payload.description() : "Sự cố vận chuyển GHN";
                shipment.recordIncident(rawStatus, "Sự cố GHN (" + rawStatus + "): " + desc, now);
                shipmentRepository.save(shipment);
                log.warn("Đã ghi nhận sự cố '{}' cho trackingNumber={}, orderId={}. Chờ Admin duyệt thủ công.",
                        rawStatus, trackingNumber, shipment.getOrderId());
            }
            default -> {
                log.warn("Nhận trạng thái GHN chưa biết: '{}' cho trackingNumber={}. Giữ nguyên trạng thái nghiệp vụ.",
                        payload.status(), trackingNumber);
                shipment.setRawGhnStatus(payload.status());
                shipmentRepository.save(shipment);
            }
        }
    }
}
