package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("V52ReceiptVerificationPostgresTest - Verification of V52 Payment Confirmation Guard on PostgreSQL")
class V52ReceiptVerificationPostgresTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO og_compat,public");
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("OGSHOP_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        registry.add("spring.flyway.enabled", PostgresTestSafetyGuard::isFlywayOptInEnabled);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long buyerId;
    private Long sellerId;
    private UUID intentId;
    private Long attemptId;
    private BigDecimal expectedAmount;

    @BeforeEach
    void setUp() {
        buyerId = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, 'fake', 'Buyer V48') RETURNING user_id",
                Long.class, UUID.randomUUID() + "@v48.test"
        );
        sellerId = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, 'fake', 'Seller V48') RETURNING user_id",
                Long.class, UUID.randomUUID() + "@v48.test"
        );

        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.checkout_groups(
                    group_id, buyer_id, command_key, request_digest, address_snapshot,
                    delivery_method, carrier, payment_method, expected_total, state,
                    created_at, payment_due_at
                )
                VALUES (
                    ?, ?, ?, repeat('a', 64), '{}'::jsonb,
                    'CARRIER', 'GHN', 'VNPAY', 100000, 'READY',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour'
                )
                """, groupId, buyerId, "CMD-" + UUID.randomUUID());

        expectedAmount = new BigDecimal("100000");

        intentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_intents(
                    intent_id, purpose, group_id, expected_amount, currency,
                    payment_method, state, deadline, created_at, command_key
                )
                VALUES (
                    ?, 'CHECKOUT_GROUP', ?, ?, 'VND',
                    'VNPAY', 'PENDING',
                    (SELECT payment_due_at FROM og_compat.checkout_groups WHERE group_id = ?),
                    (SELECT created_at FROM og_compat.checkout_groups WHERE group_id = ?),
                    ?
                )
                """, intentId, groupId, expectedAmount, groupId, groupId, "CMD-INTENT-" + UUID.randomUUID());

        attemptId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.payment_attempts(intent_id, provider, amount, status, txn_ref)
                VALUES (?, 'VNPAY', ?, 'PENDING', ?)
                RETURNING attempt_id
                """, Long.class, intentId, expectedAmount, "TXN-" + UUID.randomUUID());
    }

    private Long createRawReceipt(String verificationStatus) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.payment_ipn_raw_receipts(
                    raw_query_params, raw_query_original_length, raw_query_truncated,
                    raw_query_sanitized, raw_query_sha256, verification_status, ip_address
                )
                VALUES ('vnp_TxnRef=123', 14, false, false, repeat('b', 64), ?, '127.0.0.1')
                RETURNING receipt_id
                """, Long.class, verificationStatus);
    }

    private Long createIpnEvent(Long receiptId, String outcome, BigDecimal vnpAmount) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.payment_ipn_events(
                    receipt_id, attempt_id, intent_id, txn_ref,
                    vnp_transaction_no, vnp_response_code, vnp_transaction_status,
                    vnp_amount, vnp_amount_raw, processing_outcome
                )
                VALUES (?, ?, ?, 'TXN-REF', '12345678', '00', '00', ?, ?, ?)
                RETURNING event_id
                """, Long.class, receiptId, attemptId, intentId, vnpAmount, vnpAmount.longValue() * 100, outcome);
    }

    @Test
    @DisplayName("V48: LINKED receipt creates payment confirmation successfully")
    void linkedReceiptCreatesConfirmationSuccessfully() {
        Long receiptId = createRawReceipt("LINKED");
        Long eventId = createIpnEvent(receiptId, "APPLIED_SUCCESS", expectedAmount);

        UUID confirmationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, ipn_event_id, provider, provider_transaction_id, amount, disposition)
                VALUES (?, ?, ?, ?, 'VNPAY', '12345678', ?, 'ALLOCATABLE')
                """, confirmationId, intentId, attemptId, eventId, expectedAmount);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.payment_confirmations WHERE confirmation_id = ?",
                Integer.class, confirmationId
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("V48: UNVERIFIED receipt is rejected with SQLSTATE 23514")
    void unverifiedReceiptIsRejected() {
        Long receiptId = createRawReceipt("UNVERIFIED");
        Long eventId = createIpnEvent(receiptId, "APPLIED_SUCCESS", expectedAmount);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, ipn_event_id, provider, provider_transaction_id, amount, disposition)
                VALUES (?, ?, ?, ?, 'VNPAY', '12345678', ?, 'ALLOCATABLE')
                """, UUID.randomUUID(), intentId, attemptId, eventId, expectedAmount))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Confirmation source/amount/reference not verified");
    }

    @Test
    @DisplayName("V48: SIGNATURE_INVALID receipt is rejected with SQLSTATE 23514")
    void signatureInvalidReceiptIsRejected() {
        Long receiptId = createRawReceipt("SIGNATURE_INVALID");
        Long eventId = createIpnEvent(receiptId, "APPLIED_SUCCESS", expectedAmount);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, ipn_event_id, provider, provider_transaction_id, amount, disposition)
                VALUES (?, ?, ?, ?, 'VNPAY', '12345678', ?, 'ALLOCATABLE')
                """, UUID.randomUUID(), intentId, attemptId, eventId, expectedAmount))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Confirmation source/amount/reference not verified");
    }

    @Test
    @DisplayName("V48: SIGNATURE_VALID_UNLINKED receipt is rejected with SQLSTATE 23514")
    void signatureValidUnlinkedReceiptIsRejected() {
        Long receiptId = createRawReceipt("SIGNATURE_VALID_UNLINKED");
        Long eventId = createIpnEvent(receiptId, "APPLIED_SUCCESS", expectedAmount);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, ipn_event_id, provider, provider_transaction_id, amount, disposition)
                VALUES (?, ?, ?, ?, 'VNPAY', '12345678', ?, 'ALLOCATABLE')
                """, UUID.randomUUID(), intentId, attemptId, eventId, expectedAmount))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Confirmation source/amount/reference not verified");
    }

    @Test
    @DisplayName("V48: Mismatched amount is rejected with SQLSTATE 23514")
    void mismatchedAmountIsRejected() {
        Long receiptId = createRawReceipt("LINKED");
        Long eventId = createIpnEvent(receiptId, "APPLIED_SUCCESS", expectedAmount);

        // Confirmation amount differs from intent expected_amount
        BigDecimal wrongAmount = new BigDecimal("99000");
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, ipn_event_id, provider, provider_transaction_id, amount, disposition)
                VALUES (?, ?, ?, ?, 'VNPAY', '12345678', ?, 'ALLOCATABLE')
                """, UUID.randomUUID(), intentId, attemptId, eventId, wrongAmount))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Confirmation source/amount/reference not verified");
    }

    @Test
    @DisplayName("V48: Mismatched provider is rejected with SQLSTATE 23514")
    void mismatchedProviderIsRejected() {
        Long receiptId = createRawReceipt("LINKED");
        Long eventId = createIpnEvent(receiptId, "APPLIED_SUCCESS", expectedAmount);

        // Confirmation provider differs from intent payment_method
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, ipn_event_id, provider, provider_transaction_id, amount, disposition)
                VALUES (?, ?, ?, ?, 'MOCK_BANK', '12345678', ?, 'ALLOCATABLE')
                """, UUID.randomUUID(), intentId, attemptId, eventId, expectedAmount))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Confirmation source/amount/reference not verified");
    }
}

