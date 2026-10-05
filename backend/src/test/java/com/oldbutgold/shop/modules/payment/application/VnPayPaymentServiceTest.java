package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.commerce.application.CommercePaymentFacade;
import com.oldbutgold.shop.modules.commerce.application.CommercePaymentFacade.OrderPaymentSnapshot;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.*;
import com.oldbutgold.shop.shared.config.VnPayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VnPayPaymentServiceTest {
    private final Instant now = Instant.parse("2026-10-05T02:00:00Z");
    private final BigDecimal amount = new BigDecimal("150000.00");
    private CommercePaymentFacade commerce;
    private PaymentRepository payments;
    private VnPayAttemptRepository attempts;
    private VnPayService gateway;
    private VnPayPaymentService service;
    private PaymentEntity payment;
    private VnPayAttemptEntity attempt;
    @BeforeEach void setup() throws Exception {
        commerce = mock(CommercePaymentFacade.class); payments = mock(PaymentRepository.class); attempts = mock(VnPayAttemptRepository.class);
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        gateway = new VnPayService(new VnPayProperties("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "TESTCODE",
                "test-only-secret-never-production", "http://localhost/ipn", "http://localhost/payment/vnpay-return"), clock);
        service = new VnPayPaymentService(commerce, payments, attempts, gateway, clock);
        when(commerce.lockOrder(42L)).thenReturn(Optional.of(new OrderPaymentSnapshot(42, 7, amount, "VND", "PAYMENT_PENDING", now.plusSeconds(120))));
        payment = new PaymentEntity(42L, amount, PaymentEntity.METHOD_VNPAY, now);
        var id = PaymentEntity.class.getDeclaredField("id"); id.setAccessible(true); id.set(payment, 9L);
        attempt = new VnPayAttemptEntity("test-ref", 42, 9, amount, now.plusSeconds(120), now);
        when(payments.findByOrderIdForUpdate(42L)).thenReturn(Optional.of(payment));
        when(attempts.findFirstByOrderIdAndProcessedAtIsNullOrderByCreatedAtDesc(42L)).thenReturn(Optional.of(attempt));
        when(attempts.findOrderIdByReference("test-ref")).thenReturn(Optional.of(42L));
        when(attempts.findById("test-ref")).thenReturn(Optional.of(attempt));
    }
    private Map<String,String> callback(String code, String status) {
        var p = new HashMap<>(Map.of("vnp_TmnCode", "TESTCODE", "vnp_TxnRef", "test-ref", "vnp_Amount", "15000000",
                "vnp_ResponseCode", code, "vnp_TransactionStatus", status, "vnp_TransactionNo", "888"));
        sign(p); return p;
    }
    private void sign(Map<String,String> p) { p.put("vnp_SecureHash", gateway.sign(p)); }
    @Test void usesStoredAmountVietnamTimezoneAndExactOrderDeadline() {
        String url = service.createUrl(7, 42, "127.0.0.1");
        assertThat(url).contains("vnp_Amount=15000000", "vnp_CreateDate=20261005090000", "vnp_ExpireDate=20261005090200", "orderId%3D42");
        assertThat(service.createUrl(7, 42, "127.0.0.1")).contains("vnp_TxnRef=test-ref");
    }
    @Test void rejectsOtherBuyerWithoutMutatingPayment() {
        assertThatThrownBy(() -> service.createUrl(99, 42, "127.0.0.1")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(payments, attempts);
    }
    @Test void rejectsCancelledAndExpiredOrders() {
        when(commerce.lockOrder(42L)).thenReturn(Optional.of(new OrderPaymentSnapshot(42, 7, amount, "VND", "CANCELLED", now.plusSeconds(120))));
        assertThatThrownBy(() -> service.createUrl(7, 42, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
        when(commerce.lockOrder(42L)).thenReturn(Optional.of(new OrderPaymentSnapshot(42, 7, amount, "VND", "PAYMENT_PENDING", now)));
        assertThatThrownBy(() -> service.createUrl(7, 42, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(payments, attempts);
    }
    @Test void successPersistsHeldOrderAndDuplicateDoesNotRepeatMutation() {
        var p = callback("00", "00");
        assertThat(service.handleIpn(p).rspCode()).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo("HELD");
        assertThat(service.handleIpn(p).rspCode()).isEqualTo("02");
        verify(payments, times(1)).save(payment); verify(attempts, times(1)).save(attempt);
        verify(commerce, times(1)).markPaidHeld(42, now);
    }
    @Test void failedTransactionStatusIsPersistedAndAcknowledgedWithoutPayingOrder() {
        assertThat(service.handleIpn(callback("00", "02")).rspCode()).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo("FAILED");
        verify(commerce, never()).markPaidHeld(anyLong(), any());
    }
    @Test void lateSuccessIsReconciliationPendingAndNeverRevivesCancelledOrder() {
        when(commerce.lockOrder(42L)).thenReturn(Optional.of(new OrderPaymentSnapshot(42, 7, amount, "VND", "CANCELLED", now.minusSeconds(1))));
        assertThat(service.handleIpn(callback("00", "00")).rspCode()).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo("REFUND_PENDING");
        assertThat(payment.getHeldAt()).isNull();
        verify(commerce, never()).markPaidHeld(anyLong(), any());
    }
    @Test void rejectsSignedWrongAmountMerchantAndUnknownReference() {
        var p = callback("00", "00"); p.put("vnp_Amount", "100"); sign(p);
        assertThat(service.handleIpn(p).rspCode()).isEqualTo("04");
        p = callback("00", "00"); p.put("vnp_TmnCode", "OTHER"); sign(p);
        assertThat(service.handleIpn(p).rspCode()).isEqualTo("99");
        p = callback("00", "00"); p.put("vnp_TxnRef", "unknown"); sign(p);
        assertThat(service.handleIpn(p).rspCode()).isEqualTo("01");
        verify(payments, never()).save(any());
    }
    @Test void rejectsTamperedSignatureAndConflictingReplay() {
        var p = callback("00", "00"); p.put("vnp_Amount", "1");
        assertThat(service.handleIpn(p).rspCode()).isEqualTo("97");
        service.handleIpn(callback("00", "00"));
        assertThat(service.handleIpn(callback("24", "02")).rspCode()).isEqualTo("99");
    }
    @Test void amountScalingRejectsOverflowFractionAndProtocolLimit() {
        assertThatThrownBy(() -> VnPayService.scaledAmount(BigDecimal.valueOf(Long.MAX_VALUE))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VnPayService.scaledAmount(new BigDecimal("1000.01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VnPayService.scaledAmount(new BigDecimal("10000000000"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(VnPayService.scaledAmount(new BigDecimal("9999999999"))).isEqualTo(999999999900L);
    }
}
