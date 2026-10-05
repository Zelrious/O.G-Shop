package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.payment.api.PaymentDtos;
import com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentInfoResponse;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private ProductMediaRepository productMediaRepository;
    private PaymentAttemptRepository paymentAttemptRepository;
    private PaymentIpnEventRepository paymentIpnEventRepository;
    private PaymentReviewAuditRepository paymentReviewAuditRepository;
    private PaymentReconciliationCaseRepository paymentReconciliationCaseRepository;
    private PaymentAlertRequestRepository paymentAlertRequestRepository;
    private PaymentIpnRawReceiptRepository rawReceiptRepository;
    private VnPayService vnPayService;
    private CatalogCommerceFacade catalogCommerceFacade;
    private Clock clock;

    private PaymentService paymentService;
    private Instant now;
    private OrderEntity order;
    private PaymentEntity payment;
    private PaymentAttemptEntity attempt;

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        productMediaRepository = mock(ProductMediaRepository.class);
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        paymentIpnEventRepository = mock(PaymentIpnEventRepository.class);
        paymentReviewAuditRepository = mock(PaymentReviewAuditRepository.class);
        paymentReconciliationCaseRepository = mock(PaymentReconciliationCaseRepository.class);
        paymentAlertRequestRepository = mock(PaymentAlertRequestRepository.class);
        rawReceiptRepository = mock(PaymentIpnRawReceiptRepository.class);
        vnPayService = mock(VnPayService.class);
        catalogCommerceFacade = mock(CatalogCommerceFacade.class);

        now = Instant.parse("2026-09-21T10:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));

        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                orderItemRepository,
                productMediaRepository,
                paymentAttemptRepository,
                paymentIpnEventRepository,
                paymentReviewAuditRepository,
                paymentReconciliationCaseRepository,
                paymentAlertRequestRepository,
                rawReceiptRepository,
                vnPayService,
                catalogCommerceFacade,
                clock
        );

        order = new OrderEntity(
                UUID.randomUUID(),
                100L, // buyerId
                200L, // sellerId
                1L,
                "Nguyễn Văn A",
                "0901234567",
                "Hồ Chí Minh",
                "Quận 1",
                "Phường Bến Nghé",
                "123 Lê Lợi",
                new BigDecimal("500000.00"),
                new BigDecimal("30000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "PLATFORM",
                new BigDecimal("530000.00"),
                now.plus(Duration.ofHours(1)),
                now
        );
        setId(order, 1L);

        payment = new PaymentEntity(1L, new BigDecimal("530000.00"), PaymentEntity.METHOD_E_WALLET, now);
        setId(payment, 10L);

        attempt = new PaymentAttemptEntity(1L, 10L, "OG_1_1000", new BigDecimal("530000.00"), now);
        setId(attempt, 20L);

        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> {
            PaymentEntity p = inv.getArgument(0);
            if (p.getId() == null) setId(p, 10L);
            return p;
        });
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentAttemptRepository.save(any(PaymentAttemptEntity.class))).thenAnswer(inv -> {
            PaymentAttemptEntity a = inv.getArgument(0);
            if (a.getId() == null) setId(a, 20L);
            return a;
        });
        when(paymentIpnEventRepository.save(any(PaymentIpnEventEntity.class))).thenAnswer(inv -> {
            PaymentIpnEventEntity e = inv.getArgument(0);
            if (e.getId() == null) setId(e, 30L);
            return e;
        });
        when(paymentReconciliationCaseRepository.save(any(PaymentReconciliationCaseEntity.class))).thenAnswer(inv -> {
            PaymentReconciliationCaseEntity c = inv.getArgument(0);
            if (c.getId() == null) setId(c, 40L);
            return c;
        });
        when(paymentAlertRequestRepository.save(any(PaymentAlertRequestEntity.class))).thenAnswer(inv -> {
            PaymentAlertRequestEntity a = inv.getArgument(0);
            if (a.getId() == null) setId(a, 50L);
            return a;
        });
        when(paymentReviewAuditRepository.save(any(PaymentReviewAuditEntity.class))).thenAnswer(inv -> {
            PaymentReviewAuditEntity a = inv.getArgument(0);
            if (a.getId() == null) setId(a, 60L);
            return a;
        });
    }

    @Test
    @DisplayName("getPaymentInfo returns order and payment details")
    void getPaymentInfo_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        OrderItemEntity item = new OrderItemEntity(
                1L, 10L, "Máy ảnh cơ Canon QL17 GIII", (short) 1,
                new BigDecimal("500000.00"), new BigDecimal("500000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000.00"),
                new BigDecimal("500000.00"), 1L, null, "LIST_PRICE"
        );
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item));

        PaymentInfoResponse response = paymentService.getPaymentInfo(100L, 1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.productTitle()).isEqualTo("Máy ảnh cơ Canon QL17 GIII");
        assertThat(response.totalAmount()).isEqualByComparingTo("530000.00");
        assertThat(response.orderStatus()).isEqualTo("PAYMENT_PENDING");
        assertThat(response.remainingSeconds()).isEqualTo(3600);
        assertThat(response.recipientName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.fullAddress()).contains("123 Lê Lợi");
    }

    @Test
    @DisplayName("getPaymentInfo throws AccessDeniedException when buyerId does not match")
    void getPaymentInfo_wrongBuyer_throwsAccessDenied() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.getPaymentInfo(999L, 1L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền truy cập");
    }

    @Test
    @DisplayName("createVnPayUrl supersedes previous pending attempt and builds payment URL")
    void createVnPayUrl_success() throws Exception {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

        PaymentAttemptEntity previousPending = new PaymentAttemptEntity(1L, 10L, "OG_1_0999", new BigDecimal("530000.00"), now.minusSeconds(60));
        setId(previousPending, 19L);
        when(paymentAttemptRepository.findByPaymentIdAndStatus(10L, PaymentAttemptEntity.STATUS_PENDING))
                .thenReturn(Optional.of(previousPending));

        when(paymentAttemptRepository.save(any(PaymentAttemptEntity.class))).thenAnswer(inv -> {
            PaymentAttemptEntity saved = inv.getArgument(0);
            setId(saved, 21L);
            return saved;
        });

        when(vnPayService.createPaymentUrl(any(), anyLong(), any(), any()))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=53000000");

        PaymentDtos.PaymentUrlResponse response = paymentService.createVnPayUrl(100L, 1L, "127.0.0.1");

        assertThat(response.paymentUrl()).contains("https://sandbox.vnpayment.vn");
        assertThat(previousPending.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_SUPERSEDED);
        verify(paymentAttemptRepository, times(1)).save(previousPending);
    }

    @Test
    @DisplayName("IPN Step 1: Amount mismatch records REJECTED_AMOUNT_MISMATCH, creates MISMATCH_CHARGE case & alert, returns 04")
    void processIpnBusiness_amountMismatch_recordsEventAndReturns04() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByTxnRefForUpdate("OG_1_1000")).thenReturn(Optional.of(attempt));
        when(paymentIpnEventRepository.save(any(PaymentIpnEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "OG_1_1000");
        params.put("vnp_Amount", "10000000"); // 100,000 VND instead of 530,000 VND
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14000001");

        String rspCode = paymentService.processIpnBusinessTransaction(1L, 1L, "OG_1_1000", params, "127.0.0.1");

        assertThat(rspCode).isEqualTo("04");
        // Invariant: Order, Payment, and Attempt statuses remain unchanged
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);
        assertThat(payment.getStatus()).isEqualTo(PaymentEntity.STATUS_PENDING);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_PENDING);
        assertThat(attempt.getIpnCount()).isEqualTo(1);

        verify(paymentIpnEventRepository).save(argThat(event ->
                PaymentIpnEventEntity.OUTCOME_REJECTED_AMOUNT_MISMATCH.equals(event.getProcessingOutcome())));
        verify(paymentReconciliationCaseRepository).save(argThat(c ->
                PaymentReconciliationCaseEntity.CASE_MISMATCH_CHARGE.equals(c.getCaseType())));
        verify(paymentAlertRequestRepository).save(any());
    }

    @Test
    @DisplayName("IPN Step 1: Currency mismatch records REJECTED_CURRENCY_MISMATCH and returns 99")
    void processIpnBusiness_currencyMismatch_recordsEventAndReturns99() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByTxnRefForUpdate("OG_1_1000")).thenReturn(Optional.of(attempt));
        when(paymentIpnEventRepository.save(any(PaymentIpnEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "OG_1_1000");
        params.put("vnp_Amount", "53000000"); // 530,000 VND
        params.put("vnp_CurrCode", "USD"); // Mismatch: attempt is VND
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14000002");

        String rspCode = paymentService.processIpnBusinessTransaction(1L, 1L, "OG_1_1000", params, "127.0.0.1");

        assertThat(rspCode).isEqualTo("99");
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_PENDING);
        verify(paymentIpnEventRepository).save(argThat(event ->
                PaymentIpnEventEntity.OUTCOME_REJECTED_CURRENCY_MISMATCH.equals(event.getProcessingOutcome())));
    }

    @Test
    @DisplayName("IPN Step 2: Terminal Immutability - duplicate callback with same txnNo returns 02")
    void processIpnBusiness_terminalImmutability_duplicateAcknowledged_returns02() {
        attempt.markSuccess(now);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByTxnRefForUpdate("OG_1_1000")).thenReturn(Optional.of(attempt));

        PaymentIpnEventEntity pastEvent = new PaymentIpnEventEntity(
                1L, 20L, 1L, 10L, "OG_1_1000", "14000001", "00", "00", "VND",
                "53000000", 53000000L, new BigDecimal("530000.00"), "NCB", null, "ATM", "20260921100500",
                "127.0.0.1", PaymentIpnEventEntity.OUTCOME_APPLIED_SUCCESS, now
        );
        when(paymentIpnEventRepository.findByAttemptId(20L)).thenReturn(List.of(pastEvent));
        when(paymentIpnEventRepository.save(any(PaymentIpnEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "OG_1_1000");
        params.put("vnp_Amount", "53000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14000001");

        String rspCode = paymentService.processIpnBusinessTransaction(2L, 1L, "OG_1_1000", params, "127.0.0.1");

        assertThat(rspCode).isEqualTo("02");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_SUCCESS);
        verify(paymentIpnEventRepository).save(argThat(event ->
                PaymentIpnEventEntity.OUTCOME_DUPLICATE_ACKNOWLEDGED.equals(event.getProcessingOutcome())));
        // No new case or alert created for exact duplicate
        verify(paymentReconciliationCaseRepository, never()).save(any());
        verify(paymentAlertRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("IPN Step 2: Terminal Immutability - late success on FAILED attempt creates LATE_SUCCESS_ON_FAILED case and returns 02")
    void processIpnBusiness_terminalImmutability_lateSuccessOnFailed_returns02() {
        attempt.markFailed("User cancelled", now);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByTxnRefForUpdate("OG_1_1000")).thenReturn(Optional.of(attempt));
        when(paymentIpnEventRepository.findByAttemptId(20L)).thenReturn(List.of());
        when(paymentIpnEventRepository.save(any(PaymentIpnEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "OG_1_1000");
        params.put("vnp_Amount", "53000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14000099");

        String rspCode = paymentService.processIpnBusinessTransaction(2L, 1L, "OG_1_1000", params, "127.0.0.1");

        assertThat(rspCode).isEqualTo("02");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_FAILED); // Must NOT overwrite FAILED
        verify(paymentIpnEventRepository).save(argThat(event ->
                PaymentIpnEventEntity.OUTCOME_CONFLICT_CHARGE_REQUIRES_RECONCILIATION.equals(event.getProcessingOutcome())));
        verify(paymentReconciliationCaseRepository).save(argThat(c ->
                PaymentReconciliationCaseEntity.CASE_LATE_SUCCESS_ON_FAILED.equals(c.getCaseType())));
        verify(paymentAlertRequestRepository).save(any());
    }

    @Test
    @DisplayName("IPN Step 3: Branch A - Valid success 00/00 marks Order PAID_HELD, Payment HELD, Attempt SUCCESS")
    void processIpnBusiness_branchA_validSuccess_marksPaidHeldAndHeld() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByTxnRefForUpdate("OG_1_1000")).thenReturn(Optional.of(attempt));
        when(paymentAttemptRepository.findByPaymentId(10L)).thenReturn(List.of(attempt));
        when(paymentIpnEventRepository.save(any(PaymentIpnEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "OG_1_1000");
        params.put("vnp_Amount", "53000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14000001");
        params.put("vnp_PayDate", "20260921100500");
        params.put("vnp_BankCode", "NCB");

        String rspCode = paymentService.processIpnBusinessTransaction(1L, 1L, "OG_1_1000", params, "127.0.0.1");

        assertThat(rspCode).isEqualTo("00");
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAID_HELD);
        assertThat(payment.getStatus()).isEqualTo(PaymentEntity.STATUS_HELD);
        assertThat(payment.getPaidAt()).isEqualTo(now);
        assertThat(payment.getHeldAt()).isEqualTo(now);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_SUCCESS);
        assertThat(attempt.getVnpTransactionNo()).isEqualTo("14000001");

        verify(paymentIpnEventRepository).save(argThat(event ->
                PaymentIpnEventEntity.OUTCOME_APPLIED_SUCCESS.equals(event.getProcessingOutcome())));
    }

    @Test
    @DisplayName("IPN Step 3: Branch B - Fraud suspicion code 07 transitions Order and Payment to UNDER_REVIEW and Attempt to FLAGGED_SUSPICIOUS")
    void processIpnBusiness_branchB_fraudCode07_marksUnderReview() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByTxnRefForUpdate("OG_1_1000")).thenReturn(Optional.of(attempt));
        when(paymentAttemptRepository.findByPaymentId(10L)).thenReturn(List.of(attempt));
        when(paymentIpnEventRepository.save(any(PaymentIpnEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "OG_1_1000");
        params.put("vnp_Amount", "53000000");
        params.put("vnp_ResponseCode", "07");
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_TransactionNo", "14000007");

        String rspCode = paymentService.processIpnBusinessTransaction(1L, 1L, "OG_1_1000", params, "127.0.0.1");

        assertThat(rspCode).isEqualTo("00");
        assertThat(order.getStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(payment.getStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_FLAGGED_SUSPICIOUS);

        verify(paymentIpnEventRepository).save(argThat(event ->
                PaymentIpnEventEntity.OUTCOME_FLAGGED_SUSPICIOUS.equals(event.getProcessingOutcome())));
    }

    @Test
    @DisplayName("IPN Step 3: Branch C - Failure code 24 marks Attempt FAILED and keeps Order PAYMENT_PENDING")
    void processIpnBusiness_branchC_failed_marksFailedAndKeepsOrderPending() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByTxnRefForUpdate("OG_1_1000")).thenReturn(Optional.of(attempt));
        when(paymentAttemptRepository.findByPaymentId(10L)).thenReturn(List.of(attempt));
        when(paymentIpnEventRepository.save(any(PaymentIpnEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "OG_1_1000");
        params.put("vnp_Amount", "53000000");
        params.put("vnp_ResponseCode", "24"); // User cancelled
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_TransactionNo", "14000024");

        String rspCode = paymentService.processIpnBusinessTransaction(1L, 1L, "OG_1_1000", params, "127.0.0.1");

        assertThat(rspCode).isEqualTo("00");
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_FAILED);

        verify(paymentIpnEventRepository).save(argThat(event ->
                PaymentIpnEventEntity.OUTCOME_APPLIED_FAILED.equals(event.getProcessingOutcome())));
    }

    @Test
    @DisplayName("Admin review decision: APPROVED_SAFE transitions Order to PAID_HELD and Payment to HELD")
    void processAdminReviewDecision_approvedSafe_marksPaidHeldAndHeld() {
        order.markUnderReview(now);
        payment.markUnderReview("14000007", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByPaymentId(10L)).thenReturn(List.of(attempt));
        when(paymentReviewAuditRepository.save(any(PaymentReviewAuditEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = paymentService.processAdminReviewDecision(
                "admin@ogshop.test", 1L, "APPROVED_SAFE", "Đã xác minh qua cổng VNPay", "VNP_REF_01"
        );

        assertThat(response.decision()).isEqualTo("APPROVED_SAFE");
        assertThat(response.orderStatus()).isEqualTo("PAID_HELD");
        assertThat(response.paymentStatus()).isEqualTo("HELD");
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAID_HELD);
        assertThat(payment.getStatus()).isEqualTo(PaymentEntity.STATUS_HELD);

        verify(paymentReviewAuditRepository).save(argThat(audit ->
                "APPROVED_SAFE".equals(audit.getDecision()) && "admin@ogshop.test".equals(audit.getDecidedBy())));
    }

    @Test
    @DisplayName("Admin review decision: REJECTED_FRAUD cancels Order, marks REFUND_PENDING and releases inventory reservation")
    void processAdminReviewDecision_rejectedFraud_cancelsAndReleasesInventory() {
        order.markUnderReview(now);
        payment.markUnderReview("14000007", now);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByPaymentId(10L)).thenReturn(List.of(attempt));
        when(paymentReviewAuditRepository.save(any(PaymentReviewAuditEntity.class))).thenAnswer(inv -> {
            PaymentReviewAuditEntity a = inv.getArgument(0);
            setId(a, 99L);
            return a;
        });

        OrderItemEntity item = new OrderItemEntity(
                1L, 10L, "Máy ảnh cơ Canon QL17 GIII", (short) 1,
                new BigDecimal("500000.00"), new BigDecimal("500000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000.00"),
                new BigDecimal("500000.00"), 1L, null, "LIST_PRICE"
        );
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item));

        var response = paymentService.processAdminReviewDecision(
                "ktv@ogshop.test", 1L, "REJECTED_FRAUD", "Xác nhận hành vi gian lận", "VNP_REF_02"
        );

        assertThat(response.decision()).isEqualTo("REJECTED_FRAUD");
        assertThat(response.orderStatus()).isEqualTo(OrderEntity.STATUS_CANCELLED);
        assertThat(response.paymentStatus()).isEqualTo("REFUND_PENDING");
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_CANCELLED);
        assertThat(payment.getStatus()).isEqualTo("REFUND_PENDING");

        verify(catalogCommerceFacade).releaseProductReservation(eq(10L), eq(1L), any());
        verify(paymentReconciliationCaseRepository).save(argThat(c ->
                PaymentReconciliationCaseEntity.CASE_REFUND_PENDING.equals(c.getCaseType())));
        verify(paymentAlertRequestRepository).save(any());
    }

    @Test
    @DisplayName("verifyVnPayReturn throws AccessDeniedException on IDOR mismatch")
    void verifyVnPayReturn_idorCheck_throwsAccessDenied() {
        String queryString = "vnp_TxnRef=OG_1_1000&vnp_Amount=53000000";
        var parseResult = VnPayQueryParser.parse(queryString);
        when(vnPayService.verifyCallback(any())).thenReturn(true);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order)); // buyerId is 100L

        assertThatThrownBy(() -> paymentService.verifyVnPayReturn(999L, parseResult))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền truy cập thông tin thanh toán của đơn hàng này.");
    }

    @Test
    @DisplayName("verifyVnPayReturn returns SUCCESS when Payment is HELD")
    void verifyVnPayReturn_successWhenHeld() {
        payment.markHeld("14000001", now);
        order.markPaidHeld(now);

        String queryString = "vnp_TxnRef=OG_1_1000&vnp_Amount=53000000";
        var parseResult = VnPayQueryParser.parse(queryString);
        when(vnPayService.verifyCallback(any())).thenReturn(true);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findByTxnRef("OG_1_1000")).thenReturn(Optional.of(attempt));

        PaymentDtos.VnPayVerifyResponse response = paymentService.verifyVnPayReturn(100L, parseResult);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.txnRef()).isEqualTo("OG_1_1000");
    }

    private static void setId(Object target, Long id) throws Exception {
        Class<?> clazz = target.getClass();
        Field idField = null;
        while (clazz != null && idField == null) {
            try {
                idField = clazz.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (idField != null) {
            idField.setAccessible(true);
            idField.set(target, id);
        }
    }
}
