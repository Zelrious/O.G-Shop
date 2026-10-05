package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.commerce.application.CommercePaymentFacade;
import com.oldbutgold.shop.modules.payment.api.PaymentDtos.VnPayIpnResponse;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class VnPayPaymentService {
    private final CommercePaymentFacade commerce;
    private final PaymentRepository payments;
    private final VnPayAttemptRepository attempts;
    private final VnPayService gateway;
    private final Clock clock;
    public VnPayPaymentService(CommercePaymentFacade commerce, PaymentRepository payments,
                              VnPayAttemptRepository attempts, VnPayService gateway, Clock clock) {
        this.commerce = commerce; this.payments = payments; this.attempts = attempts; this.gateway = gateway; this.clock = clock;
    }
    @Transactional
    public String createUrl(long buyerId, long orderId, String clientIp) {
        var order = commerce.lockOrder(orderId).orElseThrow(() -> new IllegalArgumentException("Đơn hàng không tồn tại."));
        if (order.buyerId() != buyerId) throw new AccessDeniedException("Không có quyền thanh toán đơn hàng này.");
        if (!order.payableAt(clock.instant())) throw new PaymentStateConflictException("Đơn không còn chờ thanh toán hoặc đã hết hạn.");
        if (!"VND".equals(order.currency())) throw new PaymentStateConflictException("VNPAY chỉ hỗ trợ VND.");
        VnPayService.scaledAmount(order.amount());
        var payment = payments.findByOrderIdForUpdate(orderId)
                .orElseGet(() -> payments.saveAndFlush(new PaymentEntity(orderId, order.amount(), PaymentEntity.METHOD_VNPAY, clock.instant())));
        if (!PaymentEntity.STATUS_PENDING.equals(payment.getStatus()) && !PaymentEntity.STATUS_FAILED.equals(payment.getStatus()))
            throw new PaymentStateConflictException("Giao dịch đã được thanh toán hoặc đang xử lý hoàn tiền.");
        if (payment.getAmount().compareTo(order.amount()) != 0 || !"VND".equals(payment.getCurrency()))
            throw new PaymentStateConflictException("Số tiền giao dịch không khớp đơn hàng.");
        payment.setPaymentMethod(PaymentEntity.METHOD_VNPAY);
        payments.save(payment);
        var attempt = attempts.findFirstByOrderIdAndProcessedAtIsNullOrderByCreatedAtDesc(orderId).orElseGet(() -> {
            var created = new VnPayAttemptEntity(UUID.randomUUID().toString().replace("-", ""), orderId,
                    payment.getId(), order.amount(), order.deadline().truncatedTo(ChronoUnit.SECONDS), clock.instant());
            return attempts.save(created);
        });
        if (!attempt.getExpiresAt().isAfter(clock.instant())) throw new PaymentStateConflictException("Đơn đã hết hạn thanh toán.");
        return gateway.createPaymentUrl(attempt.getReference(), attempt.getAmount(), "Thanh toan don " + orderId,
                clientIp, attempt.getExpiresAt(), orderId);
    }
    @Transactional
    public VnPayIpnResponse handleIpn(Map<String, String> params) {
        if (!gateway.verifyCallback(params)) return VnPayIpnResponse.invalidChecksum();
        if (!gateway.merchantCode().equals(params.get("vnp_TmnCode"))) return reply("99", "Invalid merchant");
        String reference = params.get("vnp_TxnRef");
        if (reference == null || reference.isBlank()) return VnPayIpnResponse.orderNotFound();
        var orderId = attempts.findOrderIdByReference(reference);
        if (orderId.isEmpty()) return VnPayIpnResponse.orderNotFound();
        // Lock order before loading managed attempt/payment; concurrent callbacks then see committed state.
        var order = commerce.lockOrder(orderId.get());
        if (order.isEmpty()) return VnPayIpnResponse.orderNotFound();
        var attempt = attempts.findById(reference).orElseThrow();
        String rawAmount = params.get("vnp_Amount");
        if (rawAmount == null || !rawAmount.matches("[0-9]{1,12}")
                || Long.parseLong(rawAmount) != VnPayService.scaledAmount(attempt.getAmount())
                || (params.containsKey("vnp_CurrCode") && !"VND".equals(params.get("vnp_CurrCode"))))
            return reply("04", "Invalid amount");
        String code = params.get("vnp_ResponseCode"), status = params.get("vnp_TransactionStatus");
        String transaction = params.get("vnp_TransactionNo");
        if (code == null || status == null || !code.matches("[0-9]{2}") || !status.matches("[0-9]{2}")
                || transaction == null || !transaction.matches("[0-9]{1,100}")) return reply("99", "Invalid transaction result");
        if (attempt.isProcessed()) return attempt.matchesResult(code, status, transaction)
                ? reply("02", "Order already confirmed") : reply("99", "Conflicting callback; reconciliation required");
        var payment = payments.findByOrderIdForUpdate(orderId.get()).orElseThrow();
        if (!payment.getId().equals(attempt.getPaymentId()) || payment.getAmount().compareTo(attempt.getAmount()) != 0
                || order.get().amount().compareTo(attempt.getAmount()) != 0 || !"VND".equals(order.get().currency())
                || !"VND".equals(payment.getCurrency()) || !PaymentEntity.METHOD_VNPAY.equals(payment.getPaymentMethod()))
            return reply("99", "Payment record mismatch");
        if (!PaymentEntity.STATUS_PENDING.equals(payment.getStatus()) && !PaymentEntity.STATUS_FAILED.equals(payment.getStatus()))
            return reply("02", "Order already confirmed");
        boolean paid = "00".equals(code) && "00".equals(status);
        String outcome;
        if (paid) {
            if ("0".equals(transaction)) return reply("99", "Invalid provider transaction");
            if (order.get().payableAt(clock.instant()) && clock.instant().isBefore(attempt.getExpiresAt())) {
                payment.markHeld("VNPAY-" + transaction, clock.instant());
                commerce.markPaidHeld(orderId.get(), clock.instant());
                outcome = PaymentEntity.STATUS_HELD;
            } else {
                payment.markProviderRefundPending("VNPAY-" + transaction, clock.instant());
                outcome = PaymentEntity.STATUS_REFUND_PENDING;
            }
        } else {
            payment.markFailed("VNPAY response " + code + "/" + status, clock.instant());
            outcome = PaymentEntity.STATUS_FAILED;
        }
        attempt.finish(outcome, code, status, transaction, clock.instant());
        payments.save(payment);
        attempts.save(attempt);
        // Spring commits both records before the controller sends ACK 00.
        return VnPayIpnResponse.success();
    }
    private static VnPayIpnResponse reply(String code, String message) { return new VnPayIpnResponse(code, message); }
}
