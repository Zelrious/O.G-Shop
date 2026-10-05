package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.payment.api.PaymentDtos;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {
    public static final String BANK_NAME = "Ngân hàng Quân Đội (MB Bank)";
    public static final String ACCOUNT_NUMBER = "0388654321";
    public static final String ACCOUNT_NAME = "CONG TY CP OLD BUT GOLD";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductMediaRepository productMediaRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentIpnEventRepository paymentIpnEventRepository;
    private final PaymentReviewAuditRepository paymentReviewAuditRepository;
    private final PaymentReconciliationCaseRepository paymentReconciliationCaseRepository;
    private final PaymentAlertRequestRepository paymentAlertRequestRepository;
    private final PaymentIpnRawReceiptRepository rawReceiptRepository;
    private final VnPayService vnPayService;
    private final CatalogCommerceFacade catalogCommerceFacade;
    private final Clock clock;

    @Autowired
    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductMediaRepository productMediaRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentIpnEventRepository paymentIpnEventRepository,
            PaymentReviewAuditRepository paymentReviewAuditRepository,
            PaymentReconciliationCaseRepository paymentReconciliationCaseRepository,
            PaymentAlertRequestRepository paymentAlertRequestRepository,
            PaymentIpnRawReceiptRepository rawReceiptRepository,
            VnPayService vnPayService,
            CatalogCommerceFacade catalogCommerceFacade,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productMediaRepository = productMediaRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentIpnEventRepository = paymentIpnEventRepository;
        this.paymentReviewAuditRepository = paymentReviewAuditRepository;
        this.paymentReconciliationCaseRepository = paymentReconciliationCaseRepository;
        this.paymentAlertRequestRepository = paymentAlertRequestRepository;
        this.rawReceiptRepository = rawReceiptRepository;
        this.vnPayService = vnPayService;
        this.catalogCommerceFacade = catalogCommerceFacade;
        this.clock = clock;
    }

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductMediaRepository productMediaRepository,
            Clock clock
    ) {
        this(paymentRepository, orderRepository, orderItemRepository, productMediaRepository,
                null, null, null, null, null, null, null, null, clock);
    }

    @Transactional(readOnly = true)
    public com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentInfoResponse getPaymentInfo(Long buyerId, Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập thông tin thanh toán của đơn hàng này.");
        }

        Instant now = clock.instant();
        long remainingSeconds = 0;
        if (OrderEntity.STATUS_PAYMENT_PENDING.equals(order.getStatus())) {
            remainingSeconds = Math.max(0, Duration.between(now, order.getPaymentDueAt()).getSeconds());
        }

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
        String productTitle = !items.isEmpty() ? items.get(0).getProductTitle() : "Đơn hàng #" + orderId;
        String productThumbnail = null;
        if (!items.isEmpty()) {
            List<ProductMediaEntity> media = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(items.get(0).getProductId());
            if (!media.isEmpty()) {
                productThumbnail = media.get(0).getMediaUrl();
            }
        }

        PaymentEntity payment = paymentRepository.findByOrderId(orderId).orElse(null);

        String transferContent = "OGSHOP " + orderId;
        String qrCodeMockUrl = String.format(
                "https://img.vietqr.io/image/MB-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                ACCOUNT_NUMBER,
                order.getTotalAmount().toPlainString(),
                transferContent.replace(" ", "%20"),
                ACCOUNT_NAME.replace(" ", "%20")
        );

        String fullAddress = String.format("%s, %s, %s, %s",
                order.getShippingDetailAddress(),
                order.getShippingWard(),
                order.getShippingDistrict(),
                order.getShippingProvince());

        return new com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentInfoResponse(
                order.getId(),
                payment != null ? payment.getId() : null,
                productTitle,
                productThumbnail,
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus(),
                payment != null ? payment.getStatus() : "CHUA_KHOI_TAO",
                payment != null ? payment.getPaymentMethod() : PaymentEntity.METHOD_BANK_TRANSFER,
                payment != null ? payment.getTransactionCode() : null,
                order.getPaymentDueAt(),
                remainingSeconds,
                qrCodeMockUrl,
                BANK_NAME,
                ACCOUNT_NUMBER,
                ACCOUNT_NAME,
                transferContent,
                order.getShippingRecipientName(),
                order.getShippingPhoneNumber(),
                fullAddress,
                order.getSubtotal(),
                order.getShippingFee(),
                order.getVoucherDiscountAmount(),
                order.getSellerId()
        );
    }

    @Transactional
    public com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentProcessResultResponse processMockPayment(Long buyerId, com.oldbutgold.shop.modules.payment.application.PaymentDtos.ProcessMockPaymentRequest request) {
        Instant now = clock.instant();

        OrderEntity order = orderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + request.orderId()));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Bạn không có quyền thanh toán cho đơn hàng này.");
        }

        if (!OrderEntity.STATUS_PAYMENT_PENDING.equals(order.getStatus())) {
            throw new IllegalStateException("Đơn hàng không ở trạng thái chờ thanh toán. Trạng thái hiện tại: " + order.getStatus());
        }

        if (now.isAfter(order.getPaymentDueAt())) {
            throw new IllegalStateException("Đơn hàng đã quá hạn thanh toán. Vui lòng đặt hàng lại.");
        }

        String method = request.paymentMethod().trim().toUpperCase();
        if (!PaymentEntity.METHOD_BANK_TRANSFER.equals(method) &&
            !PaymentEntity.METHOD_E_WALLET.equals(method) &&
            !PaymentEntity.METHOD_COD.equals(method)) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ: " + method);
        }

        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(order.getId())
                .orElseGet(() -> new PaymentEntity(order.getId(), order.getTotalAmount(), method, now));
        payment.setPaymentMethod(method);

        if (request.simulateSuccess()) {
            String transactionCode = "TXN_MOCK_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            payment.markHeld(transactionCode, now);
            order.markPaidHeld(now);

            paymentRepository.save(payment);
            orderRepository.save(order);

            return new com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentProcessResultResponse(
                    payment.getId(),
                    order.getId(),
                    order.getStatus(),
                    payment.getStatus(),
                    payment.getTransactionCode(),
                    "Thanh toán thành công! Tiền được đưa vào tài khoản Ký quỹ Escrow của Old but Gold để bảo vệ người mua và người bán."
            );
        } else {
            payment.markFailed("Người mua giả lập lỗi giao dịch thanh toán", now);
            paymentRepository.save(payment);

            return new com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentProcessResultResponse(
                    payment.getId(),
                    order.getId(),
                    order.getStatus(),
                    payment.getStatus(),
                    null,
                    "Giao dịch thanh toán thất bại (Mô phỏng). Bạn có thể thử lại trước khi hết thời gian giữ đơn 15 phút."
            );
        }
    }

    @Transactional
    public PaymentDtos.PaymentUrlResponse createVnPayUrl(Long buyerId, Long orderId, String clientIp) {
        Instant now = clock.instant();

        // 1. Lock Order
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Bạn không có quyền thực hiện thanh toán cho đơn hàng này.");
        }

        if (!OrderEntity.STATUS_PAYMENT_PENDING.equals(order.getStatus())) {
            throw new IllegalStateException("Đơn hàng không ở trạng thái chờ thanh toán. Trạng thái hiện tại: " + order.getStatus());
        }

        if (now.isAfter(order.getPaymentDueAt()) || now.isAfter(order.getCreatedAt().plus(Duration.ofHours(1)))) {
            throw new IllegalStateException("Đơn hàng đã quá hạn thanh toán. Vui lòng đặt hàng lại.");
        }

        // 2. Lock Payment
        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(order.getId())
                .orElseGet(() -> new PaymentEntity(order.getId(), order.getTotalAmount(), PaymentEntity.METHOD_VNPAY, now));

        if (!PaymentEntity.STATUS_PENDING.equals(payment.getStatus())) {
            throw new IllegalStateException("Đơn hàng không thể thanh toán tiếp do trạng thái thanh toán hiện tại: " + payment.getStatus());
        }

        payment.setPaymentMethod(PaymentEntity.METHOD_VNPAY);
        payment = paymentRepository.save(payment);

        // 3. Supersede prior PENDING attempt if any
        paymentAttemptRepository.findByPaymentIdAndStatus(payment.getId(), PaymentAttemptEntity.STATUS_PENDING)
                .ifPresent(existingAttempt -> {
                    existingAttempt.markSuperseded(now);
                    paymentAttemptRepository.save(existingAttempt);
                });

        // 4. Create new attempt
        String txnRef = "OG_" + order.getId() + "_" + now.toEpochMilli();
        PaymentAttemptEntity attempt = new PaymentAttemptEntity(
                order.getId(),
                payment.getId(),
                txnRef,
                order.getTotalAmount(),
                now
        );
        paymentAttemptRepository.save(attempt);

        // 5. Generate VNPay URL
        String orderInfo = "Thanh toan don hang #" + order.getId();
        long amountVnd = order.getTotalAmount().longValueExact();
        String paymentUrl = vnPayService.createPaymentUrl(txnRef, amountVnd, orderInfo, clientIp);

        return new PaymentDtos.PaymentUrlResponse(paymentUrl);
    }

    public static class UnlinkedIpnException extends RuntimeException {
        public UnlinkedIpnException(String message) {
            super(message);
        }
    }

    @Transactional
    public String processIpnBusinessTransaction(Long receiptId, Long orderId, String txnRef, Map<String, String> cleanParams, String clientIp) {
        Instant now = clock.instant();

        // 1. Strict lock order: Order -> Payment -> PaymentAttempt
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new UnlinkedIpnException("Order not found: " + orderId));

        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new UnlinkedIpnException("Payment not found for order: " + orderId));

        PaymentAttemptEntity attempt = paymentAttemptRepository.findByTxnRefForUpdate(txnRef)
                .orElseThrow(() -> new UnlinkedIpnException("Payment attempt not found: " + txnRef));

        // 2. Extract fields
        String vnpAmountStr = cleanParams.get("vnp_Amount");
        String vnpCurrCode = cleanParams.get("vnp_CurrCode");
        String vnpResponseCode = cleanParams.get("vnp_ResponseCode");
        String vnpTransactionStatus = cleanParams.get("vnp_TransactionStatus");
        String vnpTransactionNo = cleanParams.get("vnp_TransactionNo");
        String vnpBankCode = cleanParams.get("vnp_BankCode");
        String vnpBankTranNo = cleanParams.get("vnp_BankTranNo");
        String vnpCardType = cleanParams.get("vnp_CardType");
        String vnpPayDate = cleanParams.get("vnp_PayDate");

        Long vnpAmountRaw = null;
        BigDecimal vnpAmount = null;
        try {
            if (vnpAmountStr != null && !vnpAmountStr.isBlank()) {
                vnpAmountRaw = Long.parseLong(vnpAmountStr.trim());
                vnpAmount = BigDecimal.valueOf(vnpAmountRaw).divide(BigDecimal.valueOf(100));
            }
        } catch (Exception ignored) {}

        String normalizedTxnNo = (vnpTransactionNo != null && !vnpTransactionNo.isBlank()) ? vnpTransactionNo.trim() : "NO_TXN_NO";

        // Step 1: Amount & Currency Check (Top Priority)
        long expectedAmountRaw = attempt.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
        boolean amountMismatch = (vnpAmountRaw == null || vnpAmountRaw != expectedAmountRaw);
        boolean currencyMismatch = (vnpCurrCode != null && !vnpCurrCode.isBlank() && !attempt.getCurrency().equalsIgnoreCase(vnpCurrCode.trim()));

        if (amountMismatch || currencyMismatch) {
            String outcome = amountMismatch ? PaymentIpnEventEntity.OUTCOME_REJECTED_AMOUNT_MISMATCH : PaymentIpnEventEntity.OUTCOME_REJECTED_CURRENCY_MISMATCH;
            attempt.incrementIpnCount(now);
            paymentAttemptRepository.save(attempt);

            PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                    receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                    vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                    vnpAmountStr, vnpAmountRaw, vnpAmount,
                    vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                    clientIp, outcome, now
            );
            paymentIpnEventRepository.save(event);

            // If callback indicated successful charge or fraud suspicion
            if (("00".equals(vnpResponseCode) && "00".equals(vnpTransactionStatus)) || "07".equals(vnpResponseCode)) {
                String dedupKey = String.format("MISMATCH_CHARGE:%d:%s", attempt.getId(), normalizedTxnNo);
                if (!paymentReconciliationCaseRepository.existsByDedupKey(dedupKey)) {
                    PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIpnEvent(
                            order.getId(), payment.getId(), attempt.getId(),
                            PaymentReconciliationCaseEntity.CASE_MISMATCH_CHARGE,
                            event.getId(), vnpTransactionNo, dedupKey, now
                    );
                    recCase = paymentReconciliationCaseRepository.save(recCase);
                    PaymentAlertRequestEntity alert = new PaymentAlertRequestEntity(
                            recCase.getId(), PaymentReconciliationCaseEntity.CASE_MISMATCH_CHARGE,
                            PaymentAlertRequestEntity.SEVERITY_CRITICAL, now
                    );
                    paymentAlertRequestRepository.save(alert);
                }
            }

            markReceiptLinked(receiptId);
            return amountMismatch ? "04" : "99";
        }

        // Step 2: Terminal Immutability Check
        if (attempt.isTerminal()) {
            List<PaymentIpnEventEntity> pastEvents = paymentIpnEventRepository.findByAttemptId(attempt.getId());
            boolean sameTxnNoAcknowledged = pastEvents.stream().anyMatch(e -> {
                String eTxnNo = (e.getVnpTransactionNo() != null && !e.getVnpTransactionNo().isBlank()) ? e.getVnpTransactionNo().trim() : "NO_TXN_NO";
                return normalizedTxnNo.equals(eTxnNo) && !e.getProcessingOutcome().startsWith("REJECTED_");
            });

            if (sameTxnNoAcknowledged) {
                attempt.incrementIpnCount(now);
                paymentAttemptRepository.save(attempt);

                PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                        receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                        vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                        vnpAmountStr, vnpAmountRaw, vnpAmount,
                        vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                        clientIp, PaymentIpnEventEntity.OUTCOME_DUPLICATE_ACKNOWLEDGED, now
                );
                paymentIpnEventRepository.save(event);
                markReceiptLinked(receiptId);
                return "02";
            }

            if ("00".equals(vnpResponseCode) && "00".equals(vnpTransactionStatus)) {
                if (PaymentAttemptEntity.STATUS_FAILED.equals(attempt.getStatus())) {
                    attempt.incrementIpnCount(now);
                    paymentAttemptRepository.save(attempt);

                    PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                            receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                            vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                            vnpAmountStr, vnpAmountRaw, vnpAmount,
                            vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                            clientIp, PaymentIpnEventEntity.OUTCOME_CONFLICT_CHARGE_REQUIRES_RECONCILIATION, now
                    );
                    paymentIpnEventRepository.save(event);

                    String dedupKey = String.format("LATE_SUCCESS_ON_FAILED:%d:%s", attempt.getId(), normalizedTxnNo);
                    if (!paymentReconciliationCaseRepository.existsByDedupKey(dedupKey)) {
                        PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIpnEvent(
                                order.getId(), payment.getId(), attempt.getId(),
                                PaymentReconciliationCaseEntity.CASE_LATE_SUCCESS_ON_FAILED,
                                event.getId(), vnpTransactionNo, dedupKey, now
                        );
                        recCase = paymentReconciliationCaseRepository.save(recCase);
                        PaymentAlertRequestEntity alert = new PaymentAlertRequestEntity(
                                recCase.getId(), PaymentReconciliationCaseEntity.CASE_LATE_SUCCESS_ON_FAILED,
                                PaymentAlertRequestEntity.SEVERITY_CRITICAL, now
                        );
                        paymentAlertRequestRepository.save(alert);
                    }
                    markReceiptLinked(receiptId);
                    return "02";
                } else {
                    attempt.incrementIpnCount(now);
                    paymentAttemptRepository.save(attempt);

                    PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                            receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                            vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                            vnpAmountStr, vnpAmountRaw, vnpAmount,
                            vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                            clientIp, PaymentIpnEventEntity.OUTCOME_CONFLICT_CHARGE_REQUIRES_RECONCILIATION, now
                    );
                    paymentIpnEventRepository.save(event);

                    String dedupKey = String.format("DUPLICATE_CHARGE:%d:%s", attempt.getId(), normalizedTxnNo);
                    if (!paymentReconciliationCaseRepository.existsByDedupKey(dedupKey)) {
                        PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIpnEvent(
                                order.getId(), payment.getId(), attempt.getId(),
                                PaymentReconciliationCaseEntity.CASE_DUPLICATE_CHARGE,
                                event.getId(), vnpTransactionNo, dedupKey, now
                        );
                        recCase = paymentReconciliationCaseRepository.save(recCase);
                        PaymentAlertRequestEntity alert = new PaymentAlertRequestEntity(
                                recCase.getId(), PaymentReconciliationCaseEntity.CASE_DUPLICATE_CHARGE,
                                PaymentAlertRequestEntity.SEVERITY_CRITICAL, now
                        );
                        paymentAlertRequestRepository.save(alert);
                    }
                    markReceiptLinked(receiptId);
                    return "02";
                }
            } else {
                attempt.incrementIpnCount(now);
                paymentAttemptRepository.save(attempt);

                PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                        receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                        vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                        vnpAmountStr, vnpAmountRaw, vnpAmount,
                        vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                        clientIp, PaymentIpnEventEntity.OUTCOME_CONFLICT_IGNORED, now
                );
                paymentIpnEventRepository.save(event);
                markReceiptLinked(receiptId);
                return "02";
            }
        }

        // Step 3: Branching Logic A / B / C for Non-Terminal Attempts (PENDING / SUPERSEDED)
        boolean hasPriorSuccessfulCharge = PaymentEntity.STATUS_HELD.equals(payment.getStatus())
                || PaymentEntity.STATUS_PAID.equals(payment.getStatus())
                || PaymentEntity.STATUS_RELEASED.equals(payment.getStatus())
                || paymentAttemptRepository.findByOrderId(order.getId()).stream().anyMatch(a ->
                        !a.getId().equals(attempt.getId()) && (
                                PaymentAttemptEntity.STATUS_SUCCESS.equals(a.getStatus())
                                || PaymentAttemptEntity.STATUS_SUCCESS_PENDING_REVIEW.equals(a.getStatus())
                                || PaymentAttemptEntity.STATUS_FLAGGED_SUSPICIOUS.equals(a.getStatus())
                        )
                );

        if ("00".equals(vnpResponseCode) && "00".equals(vnpTransactionStatus)) {
            // Branch A: Success
            if (hasPriorSuccessfulCharge) {
                attempt.markSuccessDuplicateCharge(now);
                setAttemptVnpFields(attempt, vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpBankCode, vnpPayDate, vnpAmountRaw, vnpAmount);
                attempt.incrementIpnCount(now);
                paymentAttemptRepository.save(attempt);

                PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                        receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                        vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                        vnpAmountStr, vnpAmountRaw, vnpAmount,
                        vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                        clientIp, PaymentIpnEventEntity.OUTCOME_APPLIED_DUPLICATE_CHARGE, now
                );
                paymentIpnEventRepository.save(event);

                String dedupKey = String.format("DUPLICATE_CHARGE:%d:%s", attempt.getId(), normalizedTxnNo);
                if (!paymentReconciliationCaseRepository.existsByDedupKey(dedupKey)) {
                    PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIpnEvent(
                            order.getId(), payment.getId(), attempt.getId(),
                            PaymentReconciliationCaseEntity.CASE_DUPLICATE_CHARGE,
                            event.getId(), vnpTransactionNo, dedupKey, now
                    );
                    recCase = paymentReconciliationCaseRepository.save(recCase);
                    PaymentAlertRequestEntity alert = new PaymentAlertRequestEntity(
                            recCase.getId(), PaymentReconciliationCaseEntity.CASE_DUPLICATE_CHARGE,
                            PaymentAlertRequestEntity.SEVERITY_CRITICAL, now
                    );
                    paymentAlertRequestRepository.save(alert);
                }
                markReceiptLinked(receiptId);
                return "00";
            } else if (OrderEntity.STATUS_UNDER_REVIEW.equals(order.getStatus())) {
                attempt.markSuccessPendingReview(now);
                setAttemptVnpFields(attempt, vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpBankCode, vnpPayDate, vnpAmountRaw, vnpAmount);
                attempt.incrementIpnCount(now);
                paymentAttemptRepository.save(attempt);

                PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                        receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                        vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                        vnpAmountStr, vnpAmountRaw, vnpAmount,
                        vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                        clientIp, PaymentIpnEventEntity.OUTCOME_APPLIED_PENDING_REVIEW, now
                );
                paymentIpnEventRepository.save(event);
                markReceiptLinked(receiptId);
                return "00";
            } else if (OrderEntity.STATUS_CANCELLED.equals(order.getStatus()) || now.isAfter(order.getPaymentDueAt()) || now.isAfter(order.getCreatedAt().plus(Duration.ofHours(1)))) {
                attempt.markSuccessOnExpiredOrder(now);
                setAttemptVnpFields(attempt, vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpBankCode, vnpPayDate, vnpAmountRaw, vnpAmount);
                attempt.incrementIpnCount(now);
                paymentAttemptRepository.save(attempt);

                PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                        receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                        vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                        vnpAmountStr, vnpAmountRaw, vnpAmount,
                        vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                        clientIp, PaymentIpnEventEntity.OUTCOME_APPLIED_EXPIRED_ORDER, now
                );
                paymentIpnEventRepository.save(event);

                String dedupKey = String.format("EXPIRED_ORDER:%d:%s", attempt.getId(), normalizedTxnNo);
                if (!paymentReconciliationCaseRepository.existsByDedupKey(dedupKey)) {
                    PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIpnEvent(
                            order.getId(), payment.getId(), attempt.getId(),
                            PaymentReconciliationCaseEntity.CASE_EXPIRED_ORDER,
                            event.getId(), vnpTransactionNo, dedupKey, now
                    );
                    recCase = paymentReconciliationCaseRepository.save(recCase);
                    PaymentAlertRequestEntity alert = new PaymentAlertRequestEntity(
                            recCase.getId(), PaymentReconciliationCaseEntity.CASE_EXPIRED_ORDER,
                            PaymentAlertRequestEntity.SEVERITY_HIGH, now
                    );
                    paymentAlertRequestRepository.save(alert);
                }
                markReceiptLinked(receiptId);
                return "00";
            } else {
                attempt.markSuccess(now);
                setAttemptVnpFields(attempt, vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpBankCode, vnpPayDate, vnpAmountRaw, vnpAmount);
                attempt.incrementIpnCount(now);
                paymentAttemptRepository.save(attempt);

                paymentAttemptRepository.findByPaymentId(payment.getId()).stream()
                        .filter(a -> !a.getId().equals(attempt.getId()) && PaymentAttemptEntity.STATUS_PENDING.equals(a.getStatus()))
                        .forEach(a -> {
                            a.markSuperseded(now);
                            paymentAttemptRepository.save(a);
                        });

                order.markPaidHeld(now);
                orderRepository.save(order);

                payment.markHeld(vnpTransactionNo, now);
                paymentRepository.save(payment);

                PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                        receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                        vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                        vnpAmountStr, vnpAmountRaw, vnpAmount,
                        vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                        clientIp, PaymentIpnEventEntity.OUTCOME_APPLIED_SUCCESS, now
                );
                paymentIpnEventRepository.save(event);
                markReceiptLinked(receiptId);
                return "00";
            }
        } else if ("07".equals(vnpResponseCode)) {
            // Branch B: Suspicious Fraud Transaction
            if (hasPriorSuccessfulCharge) {
                attempt.markSuccessDuplicateCharge(now);
                setAttemptVnpFields(attempt, vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpBankCode, vnpPayDate, vnpAmountRaw, vnpAmount);
                attempt.incrementIpnCount(now);
                paymentAttemptRepository.save(attempt);

                PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                        receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                        vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                        vnpAmountStr, vnpAmountRaw, vnpAmount,
                        vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                        clientIp, PaymentIpnEventEntity.OUTCOME_APPLIED_DUPLICATE_CHARGE, now
                );
                paymentIpnEventRepository.save(event);

                String dedupKey = String.format("DUPLICATE_CHARGE:%d:%s", attempt.getId(), normalizedTxnNo);
                if (!paymentReconciliationCaseRepository.existsByDedupKey(dedupKey)) {
                    PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIpnEvent(
                            order.getId(), payment.getId(), attempt.getId(),
                            PaymentReconciliationCaseEntity.CASE_DUPLICATE_CHARGE,
                            event.getId(), vnpTransactionNo, dedupKey, now
                    );
                    recCase = paymentReconciliationCaseRepository.save(recCase);
                    PaymentAlertRequestEntity alert = new PaymentAlertRequestEntity(
                            recCase.getId(), PaymentReconciliationCaseEntity.CASE_DUPLICATE_CHARGE,
                            PaymentAlertRequestEntity.SEVERITY_CRITICAL, now
                    );
                    paymentAlertRequestRepository.save(alert);
                }
                markReceiptLinked(receiptId);
                return "00";
            } else if (OrderEntity.STATUS_CANCELLED.equals(order.getStatus()) || now.isAfter(order.getPaymentDueAt()) || now.isAfter(order.getCreatedAt().plus(Duration.ofHours(1)))) {
                attempt.markSuccessOnExpiredOrder(now);
                setAttemptVnpFields(attempt, vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpBankCode, vnpPayDate, vnpAmountRaw, vnpAmount);
                attempt.incrementIpnCount(now);
                paymentAttemptRepository.save(attempt);

                PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                        receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                        vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                        vnpAmountStr, vnpAmountRaw, vnpAmount,
                        vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                        clientIp, PaymentIpnEventEntity.OUTCOME_APPLIED_EXPIRED_ORDER, now
                );
                paymentIpnEventRepository.save(event);

                String dedupKey = String.format("EXPIRED_ORDER:%d:%s", attempt.getId(), normalizedTxnNo);
                if (!paymentReconciliationCaseRepository.existsByDedupKey(dedupKey)) {
                    PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIpnEvent(
                            order.getId(), payment.getId(), attempt.getId(),
                            PaymentReconciliationCaseEntity.CASE_EXPIRED_ORDER,
                            event.getId(), vnpTransactionNo, dedupKey, now
                    );
                    recCase = paymentReconciliationCaseRepository.save(recCase);
                    PaymentAlertRequestEntity alert = new PaymentAlertRequestEntity(
                            recCase.getId(), PaymentReconciliationCaseEntity.CASE_EXPIRED_ORDER,
                            PaymentAlertRequestEntity.SEVERITY_HIGH, now
                    );
                    paymentAlertRequestRepository.save(alert);
                }
                markReceiptLinked(receiptId);
                return "00";
            } else if (OrderEntity.STATUS_PAYMENT_PENDING.equals(order.getStatus())) {
                attempt.markFlaggedSuspicious(now);
                setAttemptVnpFields(attempt, vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpBankCode, vnpPayDate, vnpAmountRaw, vnpAmount);
                attempt.incrementIpnCount(now);
                paymentAttemptRepository.save(attempt);

                order.markUnderReview(now);
                orderRepository.save(order);

                payment.markUnderReview(vnpTransactionNo, now);
                paymentRepository.save(payment);

                PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                        receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                        vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                        vnpAmountStr, vnpAmountRaw, vnpAmount,
                        vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                        clientIp, PaymentIpnEventEntity.OUTCOME_FLAGGED_SUSPICIOUS, now
                );
                paymentIpnEventRepository.save(event);
                markReceiptLinked(receiptId);
                return "00";
            } else if (OrderEntity.STATUS_UNDER_REVIEW.equals(order.getStatus()) && PaymentEntity.STATUS_UNDER_REVIEW.equals(payment.getStatus())) {
                attempt.markFlaggedSuspicious(now);
                setAttemptVnpFields(attempt, vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpBankCode, vnpPayDate, vnpAmountRaw, vnpAmount);
                attempt.incrementIpnCount(now);
                paymentAttemptRepository.save(attempt);

                PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                        receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                        vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                        vnpAmountStr, vnpAmountRaw, vnpAmount,
                        vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                        clientIp, PaymentIpnEventEntity.OUTCOME_FLAGGED_SUSPICIOUS, now
                );
                paymentIpnEventRepository.save(event);
                markReceiptLinked(receiptId);
                return "00";
            } else {
                throw new IllegalStateException("Trạng thái Order (" + order.getStatus() + ") và Payment (" + payment.getStatus() + ") lệch nhau khi xử lý UNDER_REVIEW");
            }
        } else {
            // Branch C: Failure or Cancellation
            attempt.markFailed("VNPay response code: " + vnpResponseCode + ", transaction status: " + vnpTransactionStatus, now);
            setAttemptVnpFields(attempt, vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpBankCode, vnpPayDate, vnpAmountRaw, vnpAmount);
            attempt.incrementIpnCount(now);
            paymentAttemptRepository.save(attempt);

            PaymentIpnEventEntity event = new PaymentIpnEventEntity(
                    receiptId, attempt.getId(), order.getId(), payment.getId(), txnRef,
                    vnpTransactionNo, vnpResponseCode, vnpTransactionStatus, vnpCurrCode,
                    vnpAmountStr, vnpAmountRaw, vnpAmount,
                    vnpBankCode, vnpBankTranNo, vnpCardType, vnpPayDate,
                    clientIp, PaymentIpnEventEntity.OUTCOME_APPLIED_FAILED, now
            );
            paymentIpnEventRepository.save(event);
            markReceiptLinked(receiptId);

            if (hasPriorSuccessfulCharge || OrderEntity.STATUS_UNDER_REVIEW.equals(order.getStatus())) {
                return "02";
            } else {
                return "00";
            }
        }
    }

    @Transactional(readOnly = true)
    public PaymentDtos.VnPayVerifyResponse verifyVnPayReturn(Long buyerId, VnPayQueryParser.ParseResult parseResult) {
        if (!parseResult.isValid()) {
            return new PaymentDtos.VnPayVerifyResponse("INVALID_SIGNATURE", null, null, "Query parameters malformed: " + parseResult.processingError());
        }

        boolean validSig = vnPayService.verifyCallback(parseResult.cleanParams());
        if (!validSig) {
            return new PaymentDtos.VnPayVerifyResponse("INVALID_SIGNATURE", null, null, "Chữ ký số không hợp lệ.");
        }

        String txnRef = parseResult.cleanParams().get("vnp_TxnRef");
        if (txnRef == null || !txnRef.startsWith("OG_")) {
            return new PaymentDtos.VnPayVerifyResponse("NOT_FOUND", null, txnRef, "Không tìm thấy giao dịch.");
        }

        String[] parts = txnRef.split("_");
        if (parts.length < 3) {
            return new PaymentDtos.VnPayVerifyResponse("NOT_FOUND", null, txnRef, "Mã giao dịch không đúng định dạng.");
        }

        Long orderId;
        try {
            orderId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return new PaymentDtos.VnPayVerifyResponse("NOT_FOUND", null, txnRef, "Mã đơn hàng không hợp lệ.");
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return new PaymentDtos.VnPayVerifyResponse("NOT_FOUND", orderId, txnRef, "Không tìm thấy đơn hàng #" + orderId);
        }

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập thông tin thanh toán của đơn hàng này.");
        }

        PaymentAttemptEntity attempt = paymentAttemptRepository.findByTxnRef(txnRef).orElse(null);
        if (attempt == null) {
            return new PaymentDtos.VnPayVerifyResponse("NOT_FOUND", orderId, txnRef, "Không tìm thấy phiên thanh toán.");
        }

        // Amount & currency verification
        String vnpAmountStr = parseResult.cleanParams().get("vnp_Amount");
        String vnpCurrCode = parseResult.cleanParams().get("vnp_CurrCode");
        Long vnpAmountRaw = null;
        try {
            if (vnpAmountStr != null && !vnpAmountStr.isBlank()) {
                vnpAmountRaw = Long.parseLong(vnpAmountStr.trim());
            }
        } catch (Exception ignored) {}

        long expectedAmountRaw = attempt.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
        if (vnpAmountRaw == null || vnpAmountRaw != expectedAmountRaw
                || (vnpCurrCode != null && !vnpCurrCode.isBlank() && !attempt.getCurrency().equalsIgnoreCase(vnpCurrCode.trim()))) {
            return new PaymentDtos.VnPayVerifyResponse("AMOUNT_MISMATCH", orderId, txnRef, "Số tiền hoặc loại tiền tệ không khớp.");
        }

        if (OrderEntity.STATUS_PAID_HELD.equals(order.getStatus()) || PaymentAttemptEntity.STATUS_SUCCESS.equals(attempt.getStatus())) {
            return new PaymentDtos.VnPayVerifyResponse("SUCCESS", orderId, txnRef, "Thanh toán thành công! Tiền đã được giữ an toàn trong Escrow.");
        }

        if (OrderEntity.STATUS_UNDER_REVIEW.equals(order.getStatus()) || PaymentAttemptEntity.STATUS_FLAGGED_SUSPICIOUS.equals(attempt.getStatus())) {
            return new PaymentDtos.VnPayVerifyResponse("UNDER_REVIEW", orderId, txnRef, "Giao dịch đang được rà soát an toàn.");
        }

        if (PaymentAttemptEntity.STATUS_FAILED.equals(attempt.getStatus())) {
            return new PaymentDtos.VnPayVerifyResponse("FAILED", orderId, txnRef, "Giao dịch thanh toán thất bại.");
        }

        if (PaymentAttemptEntity.STATUS_PENDING.equals(attempt.getStatus()) || OrderEntity.STATUS_PAYMENT_PENDING.equals(order.getStatus())) {
            return new PaymentDtos.VnPayVerifyResponse("PENDING_CONFIRMATION", orderId, txnRef, "Đang chờ xác nhận từ cổng thanh toán...");
        }

        return new PaymentDtos.VnPayVerifyResponse("FAILED", orderId, txnRef, "Trạng thái đơn hàng: " + order.getStatus());
    }

    @Transactional
    public PaymentDtos.AdminReviewDecisionResponse processAdminReviewDecision(
            String adminUsername,
            Long orderId,
            String decision,
            String reasonNote,
            String vnpVerificationRef
    ) {
        Instant now = clock.instant();

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (!OrderEntity.STATUS_UNDER_REVIEW.equals(order.getStatus())) {
            throw new IllegalStateException("Đơn hàng không ở trạng thái UNDER_REVIEW. Trạng thái hiện tại: " + order.getStatus());
        }

        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thanh toán của đơn hàng #" + orderId));

        if (!PaymentEntity.STATUS_UNDER_REVIEW.equals(payment.getStatus())) {
            throw new IllegalStateException("Thanh toán không ở trạng thái UNDER_REVIEW. Trạng thái hiện tại: " + payment.getStatus());
        }

        PaymentAttemptEntity attempt = paymentAttemptRepository.findByOrderId(orderId).stream()
                .max(Comparator.comparing(PaymentAttemptEntity::getId))
                .orElse(null);

        Long attemptId = attempt != null ? attempt.getId() : null;

        if (PaymentReviewAuditEntity.DECISION_APPROVED_SAFE.equalsIgnoreCase(decision)) {
            order.approveFromReview(now);
            payment.approveFromReview(now);

            orderRepository.save(order);
            paymentRepository.save(payment);

            PaymentReviewAuditEntity audit = new PaymentReviewAuditEntity(
                    order.getId(),
                    payment.getId(),
                    attemptId,
                    adminUsername,
                    PaymentReviewAuditEntity.DECISION_APPROVED_SAFE,
                    reasonNote,
                    vnpVerificationRef,
                    now
            );
            paymentReviewAuditRepository.save(audit);

            return new PaymentDtos.AdminReviewDecisionResponse(
                    order.getId(),
                    payment.getId(),
                    order.getStatus(),
                    payment.getStatus(),
                    PaymentReviewAuditEntity.DECISION_APPROVED_SAFE,
                    "Phê duyệt an toàn thành công. Đơn hàng chuyển sang PAID_HELD, thanh toán chuyển sang HELD."
            );
        } else if (PaymentReviewAuditEntity.DECISION_REJECTED_FRAUD.equalsIgnoreCase(decision)) {
            order.cancelFromReview(reasonNote, now);
            payment.markRefundPending(payment.getAmount(), reasonNote, now);

            orderRepository.save(order);
            paymentRepository.save(payment);

            PaymentReviewAuditEntity audit = new PaymentReviewAuditEntity(
                    order.getId(),
                    payment.getId(),
                    attemptId,
                    adminUsername,
                    PaymentReviewAuditEntity.DECISION_REJECTED_FRAUD,
                    reasonNote,
                    vnpVerificationRef,
                    now
            );
            audit = paymentReviewAuditRepository.save(audit);

            // Release inventory reservation
            if (catalogCommerceFacade != null) {
                List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
                for (OrderItemEntity item : items) {
                    catalogCommerceFacade.releaseProductReservation(item.getProductId(), order.getId(), now);
                }
            }

            // Create REFUND_PENDING reconciliation case
            String dedupKey = String.format("REFUND_PENDING:%d", payment.getId());
            if (!paymentReconciliationCaseRepository.existsByDedupKey(dedupKey)) {
                PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromReviewDecision(
                        order.getId(),
                        payment.getId(),
                        attemptId,
                        audit.getId(),
                        attempt != null ? attempt.getVnpTransactionNo() : null,
                        dedupKey,
                        now
                );
                recCase = paymentReconciliationCaseRepository.save(recCase);

                PaymentAlertRequestEntity alert = new PaymentAlertRequestEntity(
                        recCase.getId(),
                        PaymentReconciliationCaseEntity.CASE_REFUND_PENDING,
                        PaymentAlertRequestEntity.SEVERITY_HIGH,
                        now
                );
                paymentAlertRequestRepository.save(alert);
            }

            return new PaymentDtos.AdminReviewDecisionResponse(
                    order.getId(),
                    payment.getId(),
                    order.getStatus(),
                    payment.getStatus(),
                    PaymentReviewAuditEntity.DECISION_REJECTED_FRAUD,
                    "Từ chối gian lận thành công. Đơn hàng hủy, hoàn hàng vào kho và chuyển hoàn tiền REFUND_PENDING."
            );
        } else {
            throw new IllegalArgumentException("Quyết định thẩm định không hợp lệ: " + decision + ". Chấp nhận: APPROVED_SAFE hoặc REJECTED_FRAUD");
        }
    }

    private void markReceiptLinked(Long receiptId) {
        rawReceiptRepository.findById(receiptId).ifPresent(r -> {
            r.markLinked();
            r.markProcessed();
            rawReceiptRepository.save(r);
        });
    }

    private void setAttemptVnpFields(PaymentAttemptEntity attempt,
                                     String vnpTxnNo,
                                     String vnpRespCode,
                                     String vnpTxnStatus,
                                     String vnpBankCode,
                                     String vnpPayDate,
                                     Long vnpAmountRaw,
                                     BigDecimal vnpAmount) {
        if (vnpTxnNo != null) attempt.setVnpTransactionNo(vnpTxnNo);
        if (vnpRespCode != null) attempt.setVnpResponseCode(vnpRespCode);
        if (vnpTxnStatus != null) attempt.setVnpTransactionStatus(vnpTxnStatus);
        if (vnpBankCode != null) attempt.setVnpBankCode(vnpBankCode);
        if (vnpPayDate != null) attempt.setVnpPayDate(vnpPayDate);
        if (vnpAmountRaw != null) attempt.setVnpAmountRaw(vnpAmountRaw);
        if (vnpAmount != null) attempt.setVnpAmount(vnpAmount);
    }
}
