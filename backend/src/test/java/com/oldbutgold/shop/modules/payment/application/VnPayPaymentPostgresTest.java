package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.payment.infrastructure.persistence.VnPayAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
class VnPayPaymentPostgresTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("OGSHOP_TEST_DB_URL"));
        p.add("spring.datasource.username", () -> System.getenv("OGSHOP_TEST_DB_USER"));
        p.add("spring.datasource.password", () -> System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        p.add("spring.flyway.enabled", () -> true);
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @Autowired VnPayPaymentService payments;
    @Autowired VnPayService gateway;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoSpyBean VnPayAttemptRepository attempts;
    long order, buyer;
    String reference;
    @BeforeEach void fixture() {
        reset(attempts);
        buyer = user(); long seller = user();
        order = jdbc.queryForObject("""
            INSERT INTO orders(checkout_group_id,buyer_id,seller_id,shipping_recipient_name,shipping_phone_number,
                shipping_province,shipping_district,shipping_ward,shipping_detail_address,
                subtotal,shipping_fee,total_amount,buyer_system_fee,seller_system_fee,seller_proceeds,payment_due_at)
            VALUES(?,?,?,'Fixture','0912345678','HN','BD','Ward','Address',150000,0,150000,0,0,150000,NOW()+INTERVAL '1 hour') RETURNING order_id
            """, Long.class, UUID.randomUUID(), buyer, seller);
        reference = query(payments.createUrl(buyer, order, "127.0.0.1")).get("vnp_TxnRef");
    }
    private long user() { return jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name) VALUES(?,'fixture','Fixture') RETURNING user_id", Long.class, UUID.randomUUID()+"@example.test"); }
    private Map<String,String> query(String url) {
        var result = new HashMap<String,String>();
        for (String part : URI.create(url).getRawQuery().split("&")) {
            var pair = part.split("=",2); result.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        } return result;
    }
    private Map<String,String> callback(String code, String status) {
        var p = new HashMap<>(Map.of("vnp_TmnCode", gateway.merchantCode(), "vnp_TxnRef", reference,
                "vnp_Amount", "15000000", "vnp_ResponseCode", code, "vnp_TransactionStatus", status,
                "vnp_TransactionNo", Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits())));
        p.put("vnp_SecureHash", gateway.sign(p)); return p;
    }
    @Test void concurrentNotificationsPersistOnceWithSpecificAcknowledgements() throws Exception {
        var p = callback("00","00");
        var pool = Executors.newFixedThreadPool(4);
        var futures = new ArrayList<Future<String>>();
        try {
            for (int i=0; i<20; i++) futures.add(pool.submit(() -> payments.handleIpn(p).rspCode()));
            var codes = new ArrayList<String>();
            for (var f : futures) codes.add(f.get(20, TimeUnit.SECONDS));
            assertThat(codes).containsOnly("00","02");
            assertThat(Collections.frequency(codes,"00")).isEqualTo(1);
            assertThat(Collections.frequency(codes,"02")).isEqualTo(19);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order)).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,order)).isEqualTo("PAID_HELD");
        assertThat(jdbc.queryForObject("SELECT version FROM payments WHERE order_id=?",Long.class,order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vnpay_payment_attempts WHERE order_id=? AND processed_at IS NOT NULL",Integer.class,order)).isEqualTo(1);
    }
    @Test void persistenceFailureRollsBackMoneyAndOrderBeforeSendingRetryAck() throws Exception {
        var p = callback("00","00");
        doThrow(new org.springframework.dao.DataIntegrityViolationException("Injected save failure")).when(attempts).save(any());
        assertThatThrownBy(() -> payments.handleIpn(p))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,order)).isEqualTo("PAYMENT_PENDING");
        reset(attempts);
        assertThat(payments.handleIpn(p).rspCode()).isEqualTo("00");
    }
    @Test void cancelledOrderRecordsLateFundsForReconciliationWithoutEscrowOrRevival() {
        jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=NOW(),cancellation_reason='Fixture cancellation' WHERE order_id=?",order);
        assertThat(payments.handleIpn(callback("00","00")).rspCode()).isEqualTo("00");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order)).isEqualTo("REFUND_PENDING");
        assertThat(jdbc.queryForObject("SELECT held_at FROM payments WHERE order_id=?",java.sql.Timestamp.class,order)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,order)).isEqualTo("CANCELLED");
    }
    @Test void failedResultIsRecordedAndRetryUsesNewReference() {
        assertThat(payments.handleIpn(callback("24","02")).rspCode()).isEqualTo("00");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order)).isEqualTo("FAILED");
        assertThat(query(payments.createUrl(buyer,order,"127.0.0.1")).get("vnp_TxnRef")).isNotEqualTo(reference);
    }
    @Test void repeatedUrlRequestUsesSamePendingAttemptAndServerDeadline() {
        var result = query(payments.createUrl(buyer,order,"127.0.0.1"));
        assertThat(result.get("vnp_TxnRef")).isEqualTo(reference);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vnpay_payment_attempts WHERE order_id=?",Integer.class,order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT a.expires_at <= o.payment_due_at FROM vnpay_payment_attempts a JOIN orders o USING(order_id) WHERE a.transaction_ref=?",Boolean.class,reference)).isTrue();
    }
    @Test void legacyMockRefundStillRequiresActualEscrowFields() {
        assertThatThrownBy(() -> jdbc.update("UPDATE payments SET payment_method='BANK_TRANSFER_MOCK',status='REFUND_PENDING',paid_at=NOW(),held_at=NULL,refund_amount=amount,refund_reason='invalid fixture' WHERE order_id=?",order))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("ck_payments_state_fields");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order)).isEqualTo("PENDING");
    }
}
