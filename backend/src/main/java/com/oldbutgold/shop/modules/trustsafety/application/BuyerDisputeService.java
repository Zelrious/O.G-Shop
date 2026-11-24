package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.application.CommerceDisputeFacade;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.*;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.DisputeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@Transactional
public class BuyerDisputeService {

    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final CommerceDisputeFacade commerceDisputeFacade;

    public BuyerDisputeService(
            DisputeRepository disputeRepository,
            OrderRepository orderRepository,
            CommerceDisputeFacade commerceDisputeFacade
    ) {
        this.disputeRepository = disputeRepository;
        this.orderRepository = orderRepository;
        this.commerceDisputeFacade = commerceDisputeFacade;
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

        long disputeId = disputeRepository.createDispute(
                order.getId(),
                buyerId,
                order.getSellerId(),
                request.reason(),
                request.description(),
                request.buyerEvidenceUrls(),
                request.videoUnboxingUrl()
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

        disputeRepository.saveSellerResponse(dispute.disputeId(), request.sellerResponse(), request.sellerEvidenceUrls());

        return disputeRepository.findDisputeById(dispute.disputeId())
                .orElseThrow(() -> new IllegalStateException("Không thể tải hồ sơ tranh chấp cập nhật."));
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

        String carrier = (request != null && request.carrier() != null && !request.carrier().isBlank())
                ? request.carrier().trim().toUpperCase()
                : "GHN";
        String trackingCode = (request != null && request.trackingCode() != null && !request.trackingCode().isBlank())
                ? request.trackingCode().trim().toUpperCase()
                : "GHN-RET-" + orderId + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        disputeRepository.saveReturnShipment(dispute.disputeId(), trackingCode, carrier, "RETURN_SHIPPING");

        disputeRepository.recordNotification(
                order.getSellerId(),
                "DISPUTE_SELLER",
                "Người mua đã gửi hàng hoàn trả đơn #" + orderId,
                "Vận đơn hoàn trả " + carrier + " (" + trackingCode + ") đang trên đường giao về kho của bạn. Vui lòng kiểm tra và xác nhận khi nhận được hàng."
        );
        disputeRepository.recordNotification(
                buyerId,
                "DISPUTE_BUYER",
                "Đã khởi tạo vận đơn hoàn trả đơn #" + orderId,
                "Mã vận đơn hoàn trả " + carrier + ": " + trackingCode + ". Tiền ký quỹ sẽ được hoàn về ngay khi Người bán xác nhận nhận lại hàng."
        );

        return disputeRepository.findDisputeById(dispute.disputeId())
                .orElseThrow(() -> new IllegalStateException("Không thể tải hồ sơ tranh chấp sau khi tạo vận đơn hoàn trả."));
    }

    public DisputeDetailDto confirmReturnReceivedAndRefund(long actorId, long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getSellerId().equals(actorId) && !order.getBuyerId().equals(actorId)) {
            throw new AccessDeniedException("Bạn không có quyền xác nhận hoàn trả cho đơn hàng này.");
        }

        DisputeDetailDto dispute = disputeRepository.findDisputeByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Đơn hàng #" + orderId + " chưa có hồ sơ tranh chấp."));

        if ("RESOLVED_REFUND".equals(dispute.status())) {
            return dispute;
        }
        if (!"RETURN_APPROVED".equals(dispute.status())) {
            throw new IllegalStateException("Chỉ có thể xác nhận nhận hàng trả và hoàn tiền khi hồ sơ đang ở trạng thái RETURN_APPROVED.");
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

        disputeRepository.markReturnDeliveredAndRefunded(dispute.disputeId(), actorId, refundAmount);

        disputeRepository.recordNotification(
                order.getBuyerId(),
                "DISPUTE_BUYER",
                "Hoàn tiền thành công cho đơn hàng #" + orderId,
                "Người bán đã xác nhận nhận lại hàng hoàn trả. Tiền ký quỹ đã được hoàn về cho bạn."
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
