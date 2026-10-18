package com.oldbutgold.shop.modules.fulfillment.application;

import com.oldbutgold.shop.modules.commerce.application.FulfillmentCommerceFacade;
import com.oldbutgold.shop.modules.commerce.application.FulfillmentCommerceFacade.OrderFulfillmentSnapshot;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.IncidentResolutionAction;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ResolveIncidentResponse;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentIncidentResponse;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class FulfillmentStateService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentStateService.class);

    private final FulfillmentCommerceFacade commerceFacade;
    private final ShipmentRepository shipmentRepository;
    private final Clock clock;

    public FulfillmentStateService(
            FulfillmentCommerceFacade commerceFacade,
            ShipmentRepository shipmentRepository,
            Clock clock
    ) {
        this.commerceFacade = commerceFacade;
        this.shipmentRepository = shipmentRepository;
        this.clock = clock;
    }

    /**
     * Điều phối cập nhật trạng thái giao hàng thành công từ đơn vị vận chuyển (Carrier DELIVERED).
     *
     * Các bước chuẩn tắc:
     * 1. Mở transaction với @Transactional.
     * 2. Đọc sơ bộ Shipment không giữ khóa để xác định orderId.
     * 3. Khóa Order TRƯỚC bằng commerceFacade.lockOrderForFulfillment(orderId).
     * 4. Khóa Shipment SAU bằng shipmentRepository.findByOrderIdForUpdate(orderId).
     * 5. Tái kiểm tra liên kết Order-Shipment, tracking number và trạng thái terminal xung đột.
     * 6. Cập nhật ShipmentEntity (markDelivered).
     * 7. Cập nhật OrderEntity qua Facade (markDeliveredDirectFromCarrier).
     * Cả hai thực thể được lưu nguyên tử trong cùng transaction. Nếu phát sinh lỗi, rollback toàn bộ.
     */
    @Transactional
    public void applyCarrierDelivered(String trackingNumber, Instant eventTime, String rawStatus) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number không được để trống");
        }
        Instant now = clock.instant();
        Instant effectiveTime = eventTime != null ? eventTime : now;

        // 1. Đọc sơ bộ không giữ khóa
        Long orderId = shipmentRepository.findOrderIdByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn với mã: " + trackingNumber));

        // 2. Khóa Order TRƯỚC
        OrderFulfillmentSnapshot orderSnap = commerceFacade.lockOrderForFulfillment(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        // 3. Khóa Shipment SAU theo orderId
        ShipmentEntity shipment = shipmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn bị khóa cho đơn hàng #" + orderId));

        // 4. Tái kiểm tra tính toàn vẹn dưới khóa bi quan
        if (!trackingNumber.equals(shipment.getTrackingNumber())) {
            throw new IllegalStateException("Mã vận đơn không khớp dưới khóa bi quan (yêu cầu: "
                    + trackingNumber + ", hiện tại: " + shipment.getTrackingNumber() + ")");
        }
        if (!orderId.equals(shipment.getOrderId())) {
            throw new IllegalStateException("OrderId không khớp giữa Order và Shipment");
        }

        // Kiểm tra xung đột terminal
        if (shipment.hasTerminalConflictWithDelivered()) {
            throw new IllegalStateException("Vận đơn đang ở trạng thái xung đột (" + shipment.getStatus() + "), không thể chuyển sang DELIVERED");
        }
        if (orderSnap.isTerminalConflict()) {
            throw new IllegalStateException("Đơn hàng đang ở trạng thái xung đột (" + orderSnap.status() + "), không thể chuyển sang DELIVERED");
        }

        // 5. Cập nhật Shipment
        shipment.markDelivered(effectiveTime, effectiveTime, now);
        shipmentRepository.save(shipment);

        // 6. Cập nhật Order trong cùng transaction
        commerceFacade.markDeliveredDirectFromCarrier(orderId, effectiveTime);

        log.info("Đã đồng bộ giao hàng thành công (DELIVERED) cho orderId={} và trackingNumber={}", orderId, trackingNumber);
    }

    /**
     * Điều phối cập nhật bưu tá lấy hàng thành công (Carrier PICKED_UP / IN_TRANSIT).
     */
    @Transactional
    public void applyCarrierPickedUp(String trackingNumber, Instant eventTime, String rawStatus) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number không được để trống");
        }
        Instant now = clock.instant();
        Instant effectiveTime = eventTime != null ? eventTime : now;

        // 1. Đọc sơ bộ không giữ khóa
        Long orderId = shipmentRepository.findOrderIdByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn với mã: " + trackingNumber));

        // 2. Khóa Order TRƯỚC
        OrderFulfillmentSnapshot orderSnap = commerceFacade.lockOrderForFulfillment(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        // 3. Khóa Shipment SAU theo orderId
        ShipmentEntity shipment = shipmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn bị khóa cho đơn hàng #" + orderId));

        // 4. Tái kiểm tra tính toàn vẹn
        if (!trackingNumber.equals(shipment.getTrackingNumber())) {
            throw new IllegalStateException("Mã vận đơn không khớp dưới khóa bi quan (yêu cầu: "
                    + trackingNumber + ", hiện tại: " + shipment.getTrackingNumber() + ")");
        }

        if (shipment.isTerminal() || orderSnap.isTerminalConflict() || orderSnap.isAlreadyDeliveredOrCompleted()) {
            log.warn("Bỏ qua sự kiện picked_up vì đơn hàng hoặc vận đơn đã ở trạng thái kết thúc (shipment={}, order={})",
                    shipment.getStatus(), orderSnap.status());
            return;
        }

        // 5. Cập nhật Shipment (áp dụng guard chống lùi trạng thái nếu đã IN_TRANSIT)
        shipment.markPickedUp(effectiveTime, rawStatus, now);
        shipmentRepository.save(shipment);

        // 6. Cập nhật Order sang SHIPPED (idempotent nếu order đã SHIPPED/DELIVERED/COMPLETED)
        commerceFacade.markShipped(orderId, effectiveTime);

        log.info("Đã đồng bộ lấy hàng (SHIPPED) cho orderId={} và trackingNumber={}", orderId, trackingNumber);
    }

    /**
     * Điều phối cập nhật đang giao hàng hoặc luân chuyển (Carrier IN_TRANSIT).
     */
    @Transactional
    public void applyCarrierInTransit(String trackingNumber, Instant eventTime, String rawStatus) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number không được để trống");
        }
        Instant now = clock.instant();
        Instant effectiveTime = eventTime != null ? eventTime : now;

        Long orderId = shipmentRepository.findOrderIdByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn với mã: " + trackingNumber));

        OrderFulfillmentSnapshot orderSnap = commerceFacade.lockOrderForFulfillment(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        ShipmentEntity shipment = shipmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn bị khóa cho đơn hàng #" + orderId));

        if (!trackingNumber.equals(shipment.getTrackingNumber())) {
            throw new IllegalStateException("Mã vận đơn không khớp dưới khóa bi quan (yêu cầu: "
                    + trackingNumber + ", hiện tại: " + shipment.getTrackingNumber() + ")");
        }

        if (shipment.isTerminal() || orderSnap.isTerminalConflict() || orderSnap.isAlreadyDeliveredOrCompleted()) {
            log.warn("Bỏ qua sự kiện in_transit vì đơn hàng hoặc vận đơn đã ở trạng thái kết thúc (shipment={}, order={})",
                    shipment.getStatus(), orderSnap.status());
            return;
        }

        shipment.markInTransit(effectiveTime, rawStatus, now);
        shipmentRepository.save(shipment);

        // Đảm bảo Order được cập nhật SHIPPED nếu trước đó chưa cập nhật
        commerceFacade.markShipped(orderId, effectiveTime);

        log.info("Đã đồng bộ đang giao hàng (IN_TRANSIT) cho orderId={} và trackingNumber={}", orderId, trackingNumber);
    }

    /**
     * Điều phối cập nhật hàng hoàn trả về cho Seller (Carrier RETURNED).
     */
    @Transactional
    public void applyCarrierReturned(String trackingNumber, Instant eventTime, String rawStatus) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number không được để trống");
        }
        Instant now = clock.instant();
        Instant effectiveTime = eventTime != null ? eventTime : now;

        Long orderId = shipmentRepository.findOrderIdByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn với mã: " + trackingNumber));

        OrderFulfillmentSnapshot orderSnap = commerceFacade.lockOrderForFulfillment(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        ShipmentEntity shipment = shipmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn bị khóa cho đơn hàng #" + orderId));

        if (!trackingNumber.equals(shipment.getTrackingNumber())) {
            throw new IllegalStateException("Mã vận đơn không khớp dưới khóa bi quan (yêu cầu: "
                    + trackingNumber + ", hiện tại: " + shipment.getTrackingNumber() + ")");
        }

        if (shipment.isTerminal()) {
            log.warn("Bỏ qua sự kiện returned vì shipment đã ở trạng thái kết thúc ({})", shipment.getStatus());
            return;
        }

        if (shipment.getShippedAt() != null) {
            shipment.markReturned(effectiveTime, now);
        } else {
            shipment.markFailed(rawStatus, now);
        }
        shipmentRepository.save(shipment);

        log.info("Đã đồng bộ trả hàng (RETURNED) cho orderId={} và trackingNumber={}", orderId, trackingNumber);
    }

    /**
     * Điều phối cập nhật hủy đơn hoặc lỗi không thể khắc phục (Carrier FAILED).
     */
    @Transactional
    public void applyCarrierFailed(String trackingNumber, String rawStatus) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number không được để trống");
        }
        Instant now = clock.instant();

        Long orderId = shipmentRepository.findOrderIdByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn với mã: " + trackingNumber));

        OrderFulfillmentSnapshot orderSnap = commerceFacade.lockOrderForFulfillment(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        ShipmentEntity shipment = shipmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn bị khóa cho đơn hàng #" + orderId));

        if (!trackingNumber.equals(shipment.getTrackingNumber())) {
            throw new IllegalStateException("Mã vận đơn không khớp dưới khóa bi quan (yêu cầu: "
                    + trackingNumber + ", hiện tại: " + shipment.getTrackingNumber() + ")");
        }

        if (shipment.isTerminal()) {
            log.warn("Bỏ qua sự kiện cancel/failed vì shipment đã ở trạng thái kết thúc ({})", shipment.getStatus());
            return;
        }

        shipment.markFailed(rawStatus, now);
        shipmentRepository.save(shipment);

        log.info("Đã đồng bộ hủy/thất bại (FAILED) cho orderId={} và trackingNumber={}", orderId, trackingNumber);
    }

    /**
     * Lấy danh sách toàn bộ các vận đơn đang gặp sự cố cần Admin can thiệp thủ công (admin_review_required = true).
     */
    @Transactional(readOnly = true)
    public List<ShipmentIncidentResponse> getIncidentQueue() {
        return shipmentRepository.findByAdminReviewRequiredTrueOrderByUpdatedAtDesc().stream()
                .map(this::toIncidentResponse)
                .toList();
    }

    private ShipmentIncidentResponse toIncidentResponse(ShipmentEntity s) {
        return new ShipmentIncidentResponse(
                s.getId(),
                s.getOrderId(),
                s.getTrackingNumber(),
                s.getClientOrderCode(),
                s.getCarrier(),
                s.getStatus(),
                s.getRawGhnStatus(),
                s.getIncidentNotes(),
                s.isAdminReviewRequired(),
                s.getReconciliationAttempts(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }

    /**
     * Admin xử lý thủ công sự cố vận đơn theo các hành động nghiệp vụ an toàn (ADMIN-01):
     * 1. DISMISS_AND_RESUME: Tắt cờ admin_review_required, ghi chú quyết định, reset reconciliationAttempts = 0 để tiếp tục đối soát/nhận webhook.
     * 2. MARK_DELIVERED: Kiểm tra xung đột terminal, chuyển shipment sang DELIVERED, đồng bộ Order sang DELIVERED, tắt cờ admin_review_required.
     * 3. MARK_RETURNED: Chuyển shipment sang RETURNED (nếu đã shipped) hoặc FAILED, tắt cờ admin_review_required, ghi chú quyết định.
     * 4. MARK_FAILED: Chuyển shipment sang FAILED (nếu chưa terminal), tắt cờ admin_review_required, ghi chú quyết định.
     *
     * Mọi hành động đều tuân thủ thứ tự khóa bi quan bất biến: Order TRƯỚC, Shipment SAU.
     */
    @Transactional
    public ResolveIncidentResponse resolveIncident(
            Long orderId,
            IncidentResolutionAction action,
            String adminNotes,
            String adminUsername
    ) {
        if (orderId == null) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống");
        }
        if (action == null) {
            throw new IllegalArgumentException("Hành động giải quyết không được để trống");
        }

        Instant now = clock.instant();

        // 1. Khóa Order TRƯỚC theo chuẩn tắc
        OrderFulfillmentSnapshot orderSnap = commerceFacade.lockOrderForFulfillment(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        // 2. Khóa Shipment SAU theo orderId
        ShipmentEntity shipment = shipmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn cho đơn hàng #" + orderId));

        String user = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername : "ADMIN";
        String effectiveNote = formatAdminIncidentNote(shipment.getIncidentNotes(), action.name(), adminNotes, user, now);
        String resultMessage;

        switch (action) {
            case DISMISS_AND_RESUME -> {
                shipment.setAdminReviewRequired(false);
                shipment.setIncidentNotes(effectiveNote);
                shipment.resetReconciliationAttempts();
                shipment.setNextReconciliationAt(now);
                shipment.releaseClaim();
                shipmentRepository.save(shipment);
                resultMessage = "Đã gỡ cờ sự cố và khôi phục đối soát tự động cho đơn hàng #" + orderId;
                log.info("Admin [{}] đã gỡ cờ sự cố (DISMISS_AND_RESUME) cho orderId={}, trackingNumber={}", user, orderId, shipment.getTrackingNumber());
            }

            case MARK_DELIVERED -> {
                if (shipment.hasTerminalConflictWithDelivered()) {
                    throw new IllegalStateException("Vận đơn đang ở trạng thái xung đột (" + shipment.getStatus() + "), không thể chuyển sang DELIVERED");
                }
                if (orderSnap.isTerminalConflict()) {
                    throw new IllegalStateException("Đơn hàng đang ở trạng thái xung đột (" + orderSnap.status() + "), không thể chuyển sang DELIVERED");
                }

                shipment.markDelivered(now, now, now);
                shipment.setAdminReviewRequired(false);
                shipment.setIncidentNotes(effectiveNote);
                shipment.releaseClaim();
                shipment.setNextReconciliationAt(null);
                shipmentRepository.save(shipment);

                commerceFacade.markDeliveredDirectFromCarrier(orderId, now);
                resultMessage = "Đã xác nhận giao hàng thành công (DELIVERED) cho đơn hàng #" + orderId;
                log.info("Admin [{}] đã xác nhận giao hàng thành công (MARK_DELIVERED) cho orderId={}, trackingNumber={}", user, orderId, shipment.getTrackingNumber());
            }

            case MARK_RETURNED -> {
                if (ShipmentEntity.STATUS_DELIVERED.equals(shipment.getStatus())) {
                    throw new IllegalStateException("Vận đơn đã ở trạng thái DELIVERED, không thể đánh dấu RETURNED");
                }

                if (!shipment.isTerminal()) {
                    if (shipment.getShippedAt() != null) {
                        shipment.markReturned(now, now);
                    } else {
                        shipment.markFailed("admin_returned_before_pickup", now);
                    }
                }
                shipment.setAdminReviewRequired(false);
                shipment.setIncidentNotes(effectiveNote);
                shipment.releaseClaim();
                shipment.setNextReconciliationAt(null);
                shipmentRepository.save(shipment);
                resultMessage = "Đã xác nhận hoàn trả hàng (RETURNED) cho đơn hàng #" + orderId;
                log.info("Admin [{}] đã xác nhận hoàn hàng (MARK_RETURNED) cho orderId={}, trackingNumber={}", user, orderId, shipment.getTrackingNumber());
            }

            case MARK_FAILED -> {
                if (ShipmentEntity.STATUS_DELIVERED.equals(shipment.getStatus()) || ShipmentEntity.STATUS_RETURNED.equals(shipment.getStatus())) {
                    throw new IllegalStateException("Vận đơn đã ở trạng thái kết thúc " + shipment.getStatus() + ", không thể chuyển sang FAILED");
                }

                if (!shipment.isTerminal()) {
                    shipment.markFailed("admin_manual_failed", now);
                }
                shipment.setAdminReviewRequired(false);
                shipment.setIncidentNotes(effectiveNote);
                shipment.releaseClaim();
                shipment.setNextReconciliationAt(null);
                shipmentRepository.save(shipment);
                resultMessage = "Đã đánh dấu thất bại/hủy (FAILED) cho đơn hàng #" + orderId;
                log.info("Admin [{}] đã đánh dấu thất bại (MARK_FAILED) cho orderId={}, trackingNumber={}", user, orderId, shipment.getTrackingNumber());
            }

            default -> throw new IllegalArgumentException("Hành động không hợp lệ: " + action);
        }

        return new ResolveIncidentResponse(
                shipment.getOrderId(),
                shipment.getTrackingNumber(),
                shipment.getStatus(),
                shipment.getRawGhnStatus(),
                shipment.isAdminReviewRequired(),
                shipment.getIncidentNotes(),
                resultMessage
        );
    }

    private String formatAdminIncidentNote(String existingNotes, String action, String adminNotes, String adminUsername, Instant now) {
        String adminText = (adminNotes != null && !adminNotes.isBlank()) ? adminNotes.trim() : "Xử lý thủ công bởi Admin";
        String entry = String.format("[%s by %s at %s]: %s", action, adminUsername, now, adminText);
        if (existingNotes != null && !existingNotes.isBlank()) {
            return entry + " | " + existingNotes;
        }
        return entry;
    }
}
