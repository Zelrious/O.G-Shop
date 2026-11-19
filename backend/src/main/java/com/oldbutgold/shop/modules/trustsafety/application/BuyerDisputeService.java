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
}
