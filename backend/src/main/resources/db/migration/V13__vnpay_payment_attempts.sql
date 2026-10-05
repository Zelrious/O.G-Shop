ALTER TABLE payments DROP CONSTRAINT ck_payments_method;
ALTER TABLE payments ADD CONSTRAINT ck_payments_method CHECK (payment_method IN ('COD_MOCK','BANK_TRANSFER_MOCK','E_WALLET_MOCK','VNPAY'));
ALTER TABLE payments DROP CONSTRAINT ck_payments_state_fields;
ALTER TABLE payments ADD CONSTRAINT ck_payments_state_fields CHECK (
        (status = 'PENDING' AND paid_at IS NULL AND held_at IS NULL AND released_at IS NULL AND refunded_at IS NULL AND failure_reason IS NULL)
        OR
        (status = 'PAID' AND paid_at IS NOT NULL AND held_at IS NULL AND released_at IS NULL AND refunded_at IS NULL AND failure_reason IS NULL)
        OR
        (status = 'HELD' AND paid_at IS NOT NULL AND held_at IS NOT NULL AND held_at >= paid_at AND released_at IS NULL AND refunded_at IS NULL AND failure_reason IS NULL)
        OR
        (status = 'RELEASED' AND paid_at IS NOT NULL AND held_at IS NOT NULL AND held_at >= paid_at AND released_at IS NOT NULL AND released_at >= held_at AND refunded_at IS NULL AND failure_reason IS NULL)
        OR
        (status = 'REFUND_PENDING' AND paid_at IS NOT NULL AND ((payment_method = 'VNPAY' AND held_at IS NULL) OR (held_at IS NOT NULL AND held_at >= paid_at)) AND released_at IS NULL AND refunded_at IS NULL AND refund_amount IS NOT NULL AND refund_reason IS NOT NULL AND failure_reason IS NULL)
        OR
        (status = 'REFUNDED' AND paid_at IS NOT NULL AND ((payment_method = 'VNPAY' AND held_at IS NULL) OR (held_at IS NOT NULL AND held_at >= paid_at)) AND released_at IS NULL AND refunded_at IS NOT NULL AND refunded_at >= paid_at AND (held_at IS NULL OR refunded_at >= held_at) AND refund_amount IS NOT NULL AND refund_reason IS NOT NULL AND failure_reason IS NULL)
        OR
        (status = 'FAILED' AND paid_at IS NULL AND held_at IS NULL AND released_at IS NULL AND refunded_at IS NULL AND failure_reason IS NOT NULL)
    );
CREATE TABLE vnpay_payment_attempts (
    transaction_ref VARCHAR(100) PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id),
    payment_id BIGINT NOT NULL REFERENCES payments(payment_id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 1000 AND amount * 100 <= 999999999999 AND amount = trunc(amount)),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    provider_transaction_no VARCHAR(100),
    outcome VARCHAR(20) CHECK (outcome IN ('HELD','FAILED','REFUND_PENDING')),
    response_code VARCHAR(2),
    transaction_status VARCHAR(2),
    CHECK ((processed_at IS NULL AND outcome IS NULL) OR (processed_at IS NOT NULL AND outcome IS NOT NULL))
);
CREATE INDEX ix_vnpay_attempt_order ON vnpay_payment_attempts(order_id, created_at DESC);
CREATE UNIQUE INDEX ux_vnpay_pending_order ON vnpay_payment_attempts(order_id) WHERE processed_at IS NULL;
CREATE UNIQUE INDEX ux_vnpay_success_transaction ON vnpay_payment_attempts(provider_transaction_no)
    WHERE outcome IN ('HELD','REFUND_PENDING');
