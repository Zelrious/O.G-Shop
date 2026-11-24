package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.application.CommerceDisputeFacade;
import com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.*;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.DisputeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@Transactional
public class KtvDisputeService {

    private static final Set<String> VALID_VERDICTS = Set.of(
            "RETURN_APPROVED",
            "RESOLVED_REFUND",
            "RESOLVED_RELEASE"
    );

    private final DisputeRepository disputeRepository;
    private final CommerceDisputeFacade commerceDisputeFacade;

    public KtvDisputeService(
            DisputeRepository disputeRepository,
            CommerceDisputeFacade commerceDisputeFacade
    ) {
        this.disputeRepository = disputeRepository;
        this.commerceDisputeFacade = commerceDisputeFacade;
    }

    @Transactional(readOnly = true)
    public DisputePageResponse searchDisputes(String status, String query, int page, int size) {
        return disputeRepository.searchDisputes(status, query, page, size);
    }

    @Transactional(readOnly = true)
    public DisputeDetailDto getDisputeDetail(long disputeId) {
        return disputeRepository.findDisputeById(disputeId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vụ việc tranh chấp #" + disputeId));
    }

    public DisputeDecisionResponse submitDecision(long disputeId, long ktvId, DisputeDecisionRequest request) {
        String verdict = request.verdictAction() != null ? request.verdictAction().trim().toUpperCase() : "";
        if (!VALID_VERDICTS.contains(verdict)) {
            throw new IllegalArgumentException("Quyết định phán quyết không hợp lệ: " + request.verdictAction() +
                    ". Các lựa chọn hợp lệ: RETURN_APPROVED, RESOLVED_REFUND, RESOLVED_RELEASE");
        }

        DisputeDetailDto dispute = disputeRepository.findDisputeById(disputeId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vụ việc tranh chấp #" + disputeId));

        if ("RESOLVED_REFUND".equals(dispute.status()) || "RESOLVED_RELEASE".equals(dispute.status())) {
            throw new IllegalStateException("Vụ việc tranh chấp #" + disputeId + " đã có phán quyết kết thúc trước đó (" + dispute.status() + ").");
        }

        BigDecimal refundAmount = BigDecimal.ZERO;
        String message;

        int penaltyPoints = request.penaltyPoints() != null ? Math.max(0, Math.min(20, request.penaltyPoints())) : 0;
        String notes = request.verdictNotes() != null ? request.verdictNotes().trim() : "";

        switch (verdict) {
            case "RETURN_APPROVED" -> {
                refundAmount = dispute.disputeAmount();
                commerceDisputeFacade.resolveDisputeReturnApproved(dispute.orderId(), notes);
                message = "Đã chấp thuận cho Người mua trả hàng theo UC62. Đơn hàng chuyển sang chờ Người mua giao trả về Người bán.";
                if (penaltyPoints > 0) {
                    disputeRepository.recordPenalty(dispute.sellerId(), penaltyPoints,
                            "KTV phán quyết chấp thuận trả hàng do lỗi người bán (Đơn #" + dispute.orderId() + "): " + notes, ktvId);
                }
                disputeRepository.recordNotification(
                        dispute.buyerId(),
                        "DISPUTE_BUYER",
                        "KTV đã chấp thuận yêu cầu trả hàng đơn #" + dispute.orderId(),
                        "Yêu cầu trả hàng của bạn đã được chấp thuận. Vui lòng tạo vận đơn hoàn trả trên trang chi tiết đơn hàng #" + dispute.orderId() + ". Ghi chú KTV: " + notes
                );
                disputeRepository.recordNotification(
                        dispute.sellerId(),
                        "DISPUTE_SELLER",
                        "KTV chấp thuận cho Người mua trả hàng đơn #" + dispute.orderId(),
                        "KTV đã duyệt cho Người mua gửi trả lại hàng đơn #" + dispute.orderId() + ". Vui lòng theo dõi vận đơn hoàn trả và xác nhận khi nhận lại hàng. Ghi chú KTV: " + notes
                );
            }
            case "RESOLVED_REFUND" -> {
                refundAmount = dispute.disputeAmount();
                commerceDisputeFacade.resolveDisputeRefund(dispute.orderId(), refundAmount, notes);
                message = "Đã phán quyết hoàn tiền 100% cho Người mua theo UC83 và giải phóng khoản ký quỹ.";
                if (penaltyPoints > 0) {
                    disputeRepository.recordPenalty(dispute.sellerId(), penaltyPoints,
                            "KTV phán quyết hoàn tiền do lỗi người bán (Đơn #" + dispute.orderId() + "): " + notes, ktvId);
                }
                disputeRepository.recordNotification(
                        dispute.buyerId(),
                        "DISPUTE_BUYER",
                        "Đã hoàn tiền 100% cho đơn hàng #" + dispute.orderId(),
                        "KTV đã phán quyết hoàn tiền cho đơn hàng #" + dispute.orderId() + ". Ghi chú: " + notes
                );
                disputeRepository.recordNotification(
                        dispute.sellerId(),
                        "DISPUTE_SELLER",
                        "Phán quyết hoàn tiền cho Người mua đơn #" + dispute.orderId(),
                        "KTV đã phán quyết hoàn tiền cho Người mua ở đơn hàng #" + dispute.orderId() + ". Ghi chú: " + notes
                );
            }
            case "RESOLVED_RELEASE" -> {
                refundAmount = BigDecimal.ZERO;
                commerceDisputeFacade.resolveDisputeRelease(dispute.orderId(), notes);
                message = "Đã bác khiếu nại và giải ngân tiền ký quỹ Escrow cho Người bán theo UC71.";
                if (penaltyPoints > 0) {
                    disputeRepository.recordPenalty(dispute.buyerId(), penaltyPoints,
                            "KTV phán quyết bác khiếu nại do người mua khiếu nại sai/bom hàng (Đơn #" + dispute.orderId() + "): " + notes, ktvId);
                }
                disputeRepository.recordNotification(
                        dispute.buyerId(),
                        "DISPUTE_BUYER",
                        "KTV đã bác khiếu nại đơn hàng #" + dispute.orderId(),
                        "Khiếu nại đơn hàng #" + dispute.orderId() + " đã bị bác và tiền ký quỹ đã được giải ngân cho Người bán. Ghi chú: " + notes
                );
                disputeRepository.recordNotification(
                        dispute.sellerId(),
                        "DISPUTE_SELLER",
                        "Đã giải ngân tiền ký quỹ đơn hàng #" + dispute.orderId(),
                        "KTV đã bác khiếu nại của Người mua và giải ngân tiền bán hàng đơn #" + dispute.orderId() + " cho bạn."
                );
            }
            default -> throw new IllegalStateException("Trạng thái phán quyết không mong muốn: " + verdict);
        }

        disputeRepository.applyDecision(disputeId, ktvId, verdict, refundAmount, notes, penaltyPoints);

        return new DisputeDecisionResponse(disputeId, verdict, verdict, message);
    }
}
