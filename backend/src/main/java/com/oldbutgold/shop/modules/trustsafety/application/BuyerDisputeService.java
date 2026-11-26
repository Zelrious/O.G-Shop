package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.application.CommerceDisputeFacade;
import com.oldbutgold.shop.modules.commerce.application.FulfillmentSimulationGuard;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.*;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.DisputeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

@Service
@Transactional
public class BuyerDisputeService {

    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final CommerceDisputeFacade commerceDisputeFacade;
    private EvidenceStorageService evidenceStorageService;
    private FulfillmentSimulationGuard simulationGuard;

    public BuyerDisputeService(
            DisputeRepository disputeRepository,
            OrderRepository orderRepository,
            CommerceDisputeFacade commerceDisputeFacade
    ) {
        this.disputeRepository = disputeRepository;
        this.orderRepository = orderRepository;
        this.commerceDisputeFacade = commerceDisputeFacade;
    }

    @Autowired(required = false)
    public void setEvidenceStorageService(EvidenceStorageService evidenceStorageService) {
        this.evidenceStorageService = evidenceStorageService;
    }

    @Autowired(required = false)
    public void setSimulationGuard(FulfillmentSimulationGuard simulationGuard) {
        this.simulationGuard = simulationGuard;
    }

    public DisputeDetailDto createDispute(long buyerId, CreateDisputeRequest request) {
        OrderEntity order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + request.orderId()));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Bạn không có quyền yêu cầu trả hàng / khiếu nại đơn này.");
        }

        if (!OrderEntity.STATUS_DELIVERED.equals(order.getStatus()) && !OrderEntity.STATUS_SHIPPED.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể yêu cầu trả hàng khi đơn hàng đang giao hoặc đã giao. Trạng thái hiện tại: " + order.getStatus());
        }

        var existing = disputeRepository.findDisputeByOrderId(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        java.util.List<String> resolvedEvidence = resolveAttachmentUrls(buyerId, request.buyerEvidenceUrls());
        String resolvedVideo = verifyAttachmentUrl(buyerId, request.videoUnboxingUrl());

        long disputeId = disputeRepository.createDispute(
                order.getId(),
                buyerId,
                order.getSellerId(),
                request.reason(),
                request.description(),
                resolvedEvidence,
                resolvedVideo
        );

        commerceDisputeFacade.markOrderDisputed(order.getId());

        return disputeRepository.findDisputeById(disputeId)
                .orElseThrow(() -> new IllegalStateException("Không thể tải hồ sơ tranh chấp vừa tạo."));
    }

    public DisputeDetailDto submitSellerResponse(long sellerId, long orderId, SellerDisputeResponseRequest request) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getSellerId().equals(sellerId)) {
            throw new AccessDeniedException("Bạn không phải người bán của đơn hàng này.");
        }

        DisputeDetailDto dispute = disputeRepository.findDisputeByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ khiếu nại cho đơn #" + orderId));

        java.util.List<String> resolvedSellerEvidence = resolveAttachmentUrls(sellerId, request.sellerEvidenceUrls());
        disputeRepository.saveSellerResponse(dispute.disputeId(), request.sellerResponse(), resolvedSellerEvidence);

        return disputeRepository.findDisputeById(dispute.disputeId())
                .orElseThrow(() -> new IllegalStateException("Không thể tải hồ sơ tranh chấp cập nhật."));
    }

    private java.util.List<String> resolveAttachmentUrls(long actorId, java.util.List<String> urls) {
        if (urls == null || urls.isEmpty() || evidenceStorageService == null) {
            return urls;
        }
        return urls.stream().map(u -> evidenceStorageService.verifyAttachmentOwnershipAndResolve(actorId, u)).toList();
    }

    private String verifyAttachmentUrl(long actorId, String url) {
        if (url == null || url.isBlank() || evidenceStorageService == null) {
            return url;
        }
        return evidenceStorageService.verifyAttachmentOwnershipAndResolve(actorId, url);
    }

    @Transactional(readOnly = true)
    public DisputeDetailDto getDisputeForOrder(long userId, long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền xem tranh chấp của đơn hàng này.");
        }

        return disputeRepository.findDisputeByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Đơn hàng #" + orderId + " chưa có hồ sơ tranh chấp."));
    }

    public DisputeDetailDto submitReturnShipment(long buyerId, long orderId, ReturnShipmentRequest request) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Chỉ người mua của đơn hàng mới có thể tạo vận đơn hoàn trả.");
        }

        DisputeDetailDto dispute = disputeRepository.findDisputeByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Đơn hàng #" + orderId + " chưa có hồ sơ tranh chấp."));

        if (!"RETURN_APPROVED".equals(dispute.status())) {
            throw new IllegalStateException("Chỉ có thể tạo vận đơn hoàn trả khi KTV đã chấp thuận yêu cầu trả hàng (RETURN_APPROVED). Trạng thái hiện tại: " + dispute.status());
        }

        // Idempotent replay: never reset an already created/shipping/delivered return shipment
        if (dispute.returnTrackingCode() != null && !dispute.returnTrackingCode().isBlank()) {
            return dispute;
        }

        String carrier = (request != null && request.carrier() != null && !request.carrier().isBlank())
                ? request.carrier().trim().toUpperCase()
                : "GHN";
        String trackingCode = (request != null && request.trackingCode() != null && !request.trackingCode().isBlank())
                ? request.trackingCode().trim().toUpperCase()
                : "GHN-RET-" + orderId + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // Stage 1: RETURN_CREATED (awaiting physical handover to GHN)
        disputeRepository.saveReturnShipment(dispute.disputeId(), trackingCode, carrier, "RETURN_CREATED");

        disputeRepository.recordNotification(
                buyerId,
                "DISPUTE_BUYER",
                "Đã tạo mã vận đơn hoàn trả đơn #" + orderId,
                "Mã vận đơn hoàn trả " + carrier + ": " + trackingCode + ". Vui lòng đóng gói và bấm xác nhận sau khi bàn giao kiện hàng cho GHN."
        );

        return disputeRepository.findDisputeById(dispute.disputeId())
                .orElseThrow(() -> new IllegalStateException("Không thể tải hồ sơ tranh chấp sau khi tạo vận đơn hoàn trả."));
    }

    public DisputeDetailDto confirmReturnHandover(long buyerId, long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Chỉ người mua của đơn hàng mới có thể xác nhận bàn giao kiện hàng hoàn trả cho GHN.");
        }

        DisputeDetailDto dispute = disputeRepository.findDisputeByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Đơn hàng #" + orderId + " chưa có hồ sơ tranh chấp."));

        if (!"RETURN_APPROVED".equals(dispute.status())) {
            throw new IllegalStateException("Hồ sơ tranh chấp không ở trạng thái chấp thuận trả hàng (RETURN_APPROVED).");
        }

        if (dispute.returnTrackingCode() == null || dispute.returnTrackingCode().isBlank()) {
            throw new IllegalStateException("Vui lòng tạo mã vận đơn hoàn trả trước khi xác nhận bàn giao cho GHN.");
        }

        if (!"RETURN_CREATED".equals(dispute.returnShipmentStatus())) {
            throw new IllegalStateException("Kiện hàng hoàn trả không ở trạng thái chờ bàn giao (RETURN_CREATED). Trạng thái hiện tại: " + dispute.returnShipmentStatus());
        }

        int updated = disputeRepository.markReturnHandedOver(dispute.disputeId(), buyerId, java.time.Instant.now());
        if (updated == 0) {
            throw new IllegalStateException("Kiện hàng hoàn trả đã được xác nhận bàn giao cho GHN trước đó.");
        }

        disputeRepository.recordNotification(
                order.getSellerId(),
                "DISPUTE_SELLER",
                "Người mua đã bàn giao kiện hàng hoàn trả đơn #" + orderId + " cho GHN",
                "Người mua đã bàn giao kiện hàng hoàn trả (Mã: " + dispute.returnTrackingCode() + ") cho GHN. Đơn hàng đang trên đường giao về kho của bạn!"
        );

        return disputeRepository.findDisputeById(dispute.disputeId())
                .orElseThrow(() -> new IllegalStateException("Không thể tải hồ sơ tranh chấp sau khi xác nhận bàn giao."));
    }

    public DisputeDetailDto confirmReturnDelivered(long actorId, long orderId) {
        return confirmReturnDelivered(actorId, orderId, false);
    }

    public DisputeDetailDto confirmReturnDelivered(long actorId, long orderId, boolean isAdmin) {
        if (!isAdmin) {
            throw new AccessDeniedException("Chỉ Quản trị viên (ADMIN) trong môi trường kiểm thử mới có quyền mô phỏng giao hàng hoàn trả.");
        }
        if (simulationGuard == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN_SIMULATION_IN_PRODUCTION");
        }
        simulationGuard.ensureSimulationAllowed();

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        DisputeDetailDto dispute = disputeRepository.findDisputeByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Đơn hàng #" + orderId + " chưa có hồ sơ tranh chấp."));

        if (!"RETURN_APPROVED".equals(dispute.status())) {
            throw new IllegalStateException("Hồ sơ tranh chấp không ở trạng thái chấp thuận trả hàng (RETURN_APPROVED).");
        }

        if (!"RETURN_SHIPPING".equals(dispute.returnShipmentStatus())) {
            throw new IllegalStateException("Chỉ có thể ghi nhận giao trả thành công khi kiện hàng đang vận chuyển hoàn trả (RETURN_SHIPPING). Trạng thái hiện tại: " + dispute.returnShipmentStatus());
        }

        // Stage 3: RETURN_DELIVERED ONLY — MUST NOT automatically mark order REFUNDED or settle funds
        int updated = disputeRepository.markReturnDelivered(dispute.disputeId(), java.time.Instant.now());
        if (updated == 0) {
            throw new IllegalStateException("Vận đơn hoàn trả đã được ghi nhận giao trả trước đó.");
        }

        disputeRepository.recordNotification(
                order.getSellerId(),
                "DISPUTE_SELLER",
                "Kiện hàng hoàn trả đơn #" + orderId + " đã giao tới kho của bạn",
                "Vận đơn hoàn trả " + dispute.returnTrackingCode() + " đã giao trả thành công. Vui lòng kiểm tra tình trạng hàng và xác nhận hoàn tiền cho Người mua."
        );
        disputeRepository.recordNotification(
                order.getBuyerId(),
                "DISPUTE_BUYER",
                "Kiện hàng hoàn trả đơn #" + orderId + " đã giao tới Người bán",
                "Vận đơn hoàn trả " + dispute.returnTrackingCode() + " đã giao trả thành công. Đang chờ Người bán kiểm tra hàng và hoàn tất quy trình hoàn tiền."
        );

        return disputeRepository.findDisputeById(dispute.disputeId())
                .orElseThrow(() -> new IllegalStateException("Không thể tải hồ sơ tranh chấp sau khi ghi nhận giao trả."));
    }

    public DisputeDetailDto confirmReturnReceivedAndRefund(long sellerId, long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getSellerId().equals(sellerId)) {
            throw new AccessDeniedException("Chỉ Người bán của đơn hàng mới có quyền xác nhận tình trạng hàng trả và đồng ý hoàn tiền.");
        }

        DisputeDetailDto dispute = disputeRepository.findDisputeByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Đơn hàng #" + orderId + " chưa có hồ sơ tranh chấp."));

        if ("RESOLVED_REFUND".equals(dispute.status())) {
            return dispute;
        }
        if (!"RETURN_APPROVED".equals(dispute.status())) {
            throw new IllegalStateException("Chỉ có thể xác nhận hoàn tiền khi hồ sơ đang ở trạng thái RETURN_APPROVED.");
        }
        if (!"RETURN_DELIVERED".equals(dispute.returnShipmentStatus())) {
            throw new IllegalStateException("Chỉ có thể xác nhận hoàn tiền sau khi vận đơn hoàn trả đã giao trả thành công (RETURN_DELIVERED). Trạng thái hiện tại: " + dispute.returnShipmentStatus());
        }

        java.math.BigDecimal refundAmount = dispute.refundAmount() != null && dispute.refundAmount().compareTo(java.math.BigDecimal.ZERO) > 0
                ? dispute.refundAmount()
                : order.getTotalAmount();

        commerceDisputeFacade.resolveDisputeRefund(
                orderId,
                refundAmount,
                "Người bán xác nhận đã nhận lại hàng hoàn trả (Mã vận đơn: "
                        + (dispute.returnTrackingCode() != null ? dispute.returnTrackingCode() : "TRỰC TIẾP") + ")"
        );

        disputeRepository.markReturnDeliveredAndRefunded(dispute.disputeId(), sellerId, refundAmount);

        disputeRepository.recordNotification(
                order.getBuyerId(),
                "DISPUTE_BUYER",
                "Hoàn tiền thành công cho đơn hàng #" + orderId,
                "Người bán đã kiểm tra hàng trả và xác nhận hoàn tiền cho đơn hàng #" + orderId + "."
        );
        disputeRepository.recordNotification(
                order.getSellerId(),
                "DISPUTE_SELLER",
                "Đã hoàn tất nhận hàng trả & hoàn tiền đơn #" + orderId,
                "Đơn hàng #" + orderId + " đã chuyển sang trạng thái REFUNDED sau khi bạn xác nhận nhận lại hàng trả."
        );

        return disputeRepository.findDisputeById(dispute.disputeId())
                .orElseThrow(() -> new IllegalStateException("Không thể tải hồ sơ tranh chấp sau khi hoàn tiền."));
    }
}
