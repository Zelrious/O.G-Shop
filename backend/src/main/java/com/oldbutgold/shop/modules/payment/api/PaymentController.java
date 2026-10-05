package com.oldbutgold.shop.modules.payment.api;

import com.oldbutgold.shop.modules.payment.application.IpnReceiptService;
import com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentInfoResponse;
import com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentProcessResultResponse;
import com.oldbutgold.shop.modules.payment.application.PaymentDtos.ProcessMockPaymentRequest;
import com.oldbutgold.shop.modules.payment.application.PaymentService;
import com.oldbutgold.shop.modules.payment.application.VnPayQueryParser;
import com.oldbutgold.shop.modules.payment.application.VnPayService;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentIpnRawReceiptEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final VnPayService vnPayService;
    private final IpnReceiptService ipnReceiptService;
    private final Clock clock;

    public PaymentController(
            PaymentService paymentService,
            VnPayService vnPayService,
            IpnReceiptService ipnReceiptService,
            Clock clock
    ) {
        this.paymentService = paymentService;
        this.vnPayService = vnPayService;
        this.ipnReceiptService = ipnReceiptService;
        this.clock = clock;
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PaymentInfoResponse> getPaymentInfo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long buyerId = extractUserId(jwt);
        return ResponseEntity.ok(paymentService.getPaymentInfo(buyerId, orderId));
    }

    @PostMapping("/mock-process")
    public ResponseEntity<PaymentProcessResultResponse> processMockPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProcessMockPaymentRequest request
    ) {
        long buyerId = extractUserId(jwt);
        return ResponseEntity.ok(paymentService.processMockPayment(buyerId, request));
    }

    @PostMapping("/vnpay-url")
    public ResponseEntity<PaymentDtos.PaymentUrlResponse> createPaymentUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PaymentDtos.CreateVnPaymentRequest request,
            HttpServletRequest servletRequest
    ) {
        long buyerId = extractUserId(jwt);
        String clientIp = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(paymentService.createVnPayUrl(buyerId, request.orderId(), clientIp));
    }

    @GetMapping("/vnpay-ipn")
    public PaymentDtos.VnPayIpnResponse handleVnPayIpn(HttpServletRequest servletRequest) {
        String rawQueryString = servletRequest.getQueryString();
        String clientIp = servletRequest.getRemoteAddr();
        Instant now = clock.instant();

        // 1. Single query parsing pass
        VnPayQueryParser.ParseResult parseResult = VnPayQueryParser.parse(rawQueryString);

        // 2. Insert raw receipt BEFORE any rejection decisions (REQUIRES_NEW)
        PaymentIpnRawReceiptEntity receipt;
        try {
            receipt = ipnReceiptService.createInitialReceipt(parseResult, clientIp, now);
        } catch (Exception ex) {
            log.error("[VNPAY_IPN_RECEIPT_ERROR] Không thể ghi nhận raw receipt ban đầu", ex);
            return PaymentDtos.VnPayIpnResponse.unknownError();
        }

        Long receiptId = receipt.getId();

        // 3. Structural validation check
        if (!parseResult.isValid()) {
            log.warn("[VNPAY_IPN_MALFORMED] Receipt #{} vi phạm cấu trúc query: {}", receiptId, parseResult.processingError());
            ipnReceiptService.markMalformed(receiptId, parseResult.processingError());
            return PaymentDtos.VnPayIpnResponse.invalidChecksum();
        }

        // 4. Record forensic search fields
        ipnReceiptService.recordForensicFields(receiptId, parseResult.vnpTxnRef(), parseResult.vnpTransactionNo(), parseResult.vnpAmountText());

        // 5. Signature verification
        boolean validChecksum = vnPayService.verifyCallback(parseResult.cleanParams());
        if (!validChecksum) {
            log.warn("[VNPAY_IPN_SIGNATURE_INVALID] Receipt #{} có chữ ký số không hợp lệ", receiptId);
            ipnReceiptService.markSignatureInvalid(receiptId);
            return PaymentDtos.VnPayIpnResponse.invalidChecksum();
        }

        // 6. TxnRef and OrderId extraction
        String txnRef = parseResult.cleanParams().get("vnp_TxnRef");
        if (txnRef == null || !txnRef.startsWith("OG_")) {
            log.warn("[VNPAY_IPN_UNLINKED] Receipt #{} TxnRef không hợp lệ hoặc thiếu tiền tố OG_: {}", receiptId, txnRef);
            ipnReceiptService.markSignatureValidUnlinked(receiptId);
            return PaymentDtos.VnPayIpnResponse.orderNotFound();
        }

        String[] parts = txnRef.split("_");
        if (parts.length < 3) {
            log.warn("[VNPAY_IPN_UNLINKED] Receipt #{} TxnRef sai cấu trúc phần tử: {}", receiptId, txnRef);
            ipnReceiptService.markSignatureValidUnlinked(receiptId);
            return PaymentDtos.VnPayIpnResponse.orderNotFound();
        }

        Long orderId;
        try {
            orderId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("[VNPAY_IPN_UNLINKED] Receipt #{} không thể parse orderId từ TxnRef: {}", receiptId, txnRef);
            ipnReceiptService.markSignatureValidUnlinked(receiptId);
            return PaymentDtos.VnPayIpnResponse.orderNotFound();
        }

        // 7. Business transaction with locking Order -> Payment -> Attempt
        try {
            String rspCode = paymentService.processIpnBusinessTransaction(receiptId, orderId, txnRef, parseResult.cleanParams(), clientIp);
            return mapIpnResponse(rspCode);
        } catch (PaymentService.UnlinkedIpnException ex) {
            log.warn("[VNPAY_IPN_UNLINKED] Receipt #{} không tìm thấy thực thể trong DB: {}", receiptId, ex.getMessage());
            ipnReceiptService.markSignatureValidUnlinked(receiptId);
            return PaymentDtos.VnPayIpnResponse.orderNotFound();
        } catch (PessimisticLockingFailureException ex) {
            log.error("[VNPAY_IPN_LOCK_TIMEOUT] Receipt #{} gặp lỗi lock/timeout hệ thống", receiptId, ex);
            ipnReceiptService.markRetryableError(receiptId, ex.getClass().getSimpleName());
            return PaymentDtos.VnPayIpnResponse.unknownError();
        } catch (Exception ex) {
            log.error("[VNPAY_IPN_PROCESSING_FAILED] Receipt #{} lỗi xử lý nghiệp vụ", receiptId, ex);
            ipnReceiptService.markProcessingFailed(receiptId, ex.getMessage());
            return PaymentDtos.VnPayIpnResponse.unknownError();
        }
    }

    @GetMapping("/vnpay-verify")
    public ResponseEntity<PaymentDtos.VnPayVerifyResponse> verifyVnPayReturn(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest
    ) {
        long buyerId = extractUserId(jwt);
        String rawQueryString = servletRequest.getQueryString();

        VnPayQueryParser.ParseResult parseResult = VnPayQueryParser.parse(rawQueryString);
        return ResponseEntity.ok(paymentService.verifyVnPayReturn(buyerId, parseResult));
    }

    private PaymentDtos.VnPayIpnResponse mapIpnResponse(String rspCode) {
        return switch (rspCode) {
            case "00" -> PaymentDtos.VnPayIpnResponse.success();
            case "02" -> PaymentDtos.VnPayIpnResponse.duplicateAcknowledged();
            case "04" -> PaymentDtos.VnPayIpnResponse.invalidAmount();
            case "01" -> PaymentDtos.VnPayIpnResponse.orderNotFound();
            case "97" -> PaymentDtos.VnPayIpnResponse.invalidChecksum();
            default -> PaymentDtos.VnPayIpnResponse.unknownError();
        };
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập để thực hiện thanh toán.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
