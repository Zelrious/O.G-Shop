\set ON_ERROR_STOP on
\pset pager off

BEGIN;

CREATE OR REPLACE FUNCTION pg_temp.expect_sqlstate(
    test_name TEXT,
    statement TEXT,
    expected_state TEXT
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    actual_state TEXT;
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE;
    END;

    IF actual_state IS NULL THEN
        RAISE EXCEPTION 'FAIL [%]: statement succeeded; expected SQLSTATE %', test_name, expected_state;
    END IF;

    IF actual_state <> expected_state THEN
        RAISE EXCEPTION 'FAIL [%]: expected SQLSTATE %, got %', test_name, expected_state, actual_state;
    END IF;

    RAISE NOTICE 'PASS [%] rejected with SQLSTATE %', test_name, actual_state;
END;
$$;

CREATE OR REPLACE FUNCTION pg_temp.assert_true(test_name TEXT, condition BOOLEAN)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF condition IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'FAIL [%]: assertion is not true', test_name;
    END IF;
    RAISE NOTICE 'PASS [%]', test_name;
END;
$$;

INSERT INTO users (user_id, email, password_hash, full_name, phone_number) VALUES
    (1001, 'buyer@example.com',  '$2a$test-buyer',  'Buyer Test',  '+84900000001'),
    (1002, 'seller@example.com', '$2a$test-seller', 'Seller Test', '+84900000002'),
    (1003, 'admin@example.com',  '$2a$test-admin',  'Admin Test',  '+84900000003');

INSERT INTO user_roles (user_id, role_id, granted_by)
SELECT 1001, role_id, 1003 FROM roles WHERE role_name = 'BUYER';
INSERT INTO user_roles (user_id, role_id, granted_by)
SELECT 1002, role_id, 1003 FROM roles WHERE role_name = 'SELLER';
INSERT INTO user_roles (user_id, role_id, granted_by)
SELECT 1003, role_id, 1003 FROM roles WHERE role_name = 'ADMIN';

INSERT INTO addresses (
    address_id, user_id, recipient_name, phone_number,
    province, district, ward, detail_address, is_default
) VALUES (
    2001, 1001, 'Buyer Test', '+84900000001',
    'Ho Chi Minh', 'District 1', 'Ben Nghe', '1 Original Street', TRUE
);

INSERT INTO seller_verifications (
    verification_id, user_id, status, document_data, reviewed_at, verified_by
) VALUES (
    2101, 1002, 'VERIFIED', '{"objectKey":"kyc/test/seller-1002"}', CURRENT_TIMESTAMP, 1003
);

INSERT INTO categories (category_id, category_name, slug)
VALUES (2201, 'Electronics', 'electronics');

INSERT INTO products (
    product_id, seller_id, category_id, title, description, price,
    condition, status
) VALUES
    (3001, 1002, 2201, 'Used laptop', 'Smoke-test product', 10000000, 'GOOD', 'ACTIVE'),
    (3002, 1002, 2201, 'Used phone',  'Second smoke-test product', 5000000, 'FAIR', 'ACTIVE');

SELECT pg_temp.expect_sqlstate(
    'email must be lowercase',
    $test$INSERT INTO users (email, password_hash, full_name) VALUES ('UPPER@example.com', 'x', 'Invalid')$test$,
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'email is unique',
    $test$INSERT INTO users (email, password_hash, full_name) VALUES ('buyer@example.com', 'x', 'Duplicate')$test$,
    '23505'
);

SELECT pg_temp.expect_sqlstate(
    'one default address per user',
    $test$INSERT INTO addresses (user_id, recipient_name, phone_number, province, district, ward, detail_address, is_default) VALUES (1001, 'Buyer', '+84900000001', 'HCM', 'D1', 'Ward', 'Another address', TRUE)$test$,
    '23505'
);

SELECT pg_temp.expect_sqlstate(
    'one open or verified KYC per user',
    $test$INSERT INTO seller_verifications (user_id, status) VALUES (1002, 'PENDING')$test$,
    '23505'
);

INSERT INTO conversations (conversation_id, buyer_id, seller_id, product_id)
VALUES (3101, 1001, 1002, 3001);

SELECT pg_temp.expect_sqlstate(
    'conversation is unique per buyer seller product',
    $test$INSERT INTO conversations (buyer_id, seller_id, product_id) VALUES (1001, 1002, 3001)$test$,
    '23505'
);

INSERT INTO carts (cart_id, user_id) VALUES (3201, 1001);

SELECT pg_temp.expect_sqlstate(
    'MVP cart quantity is one',
    $test$INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (3201, 3001, 2)$test$,
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'order total must equal subtotal plus shipping',
    $test$
        INSERT INTO orders (
            checkout_group_id, buyer_id, seller_id, source_address_id,
            shipping_recipient_name, shipping_phone_number, shipping_province,
            shipping_district, shipping_ward, shipping_detail_address,
            subtotal, shipping_fee, total_amount, payment_due_at
        ) VALUES (
            '00000000-0000-0000-0000-000000000001', 1001, 1002, 2001,
            'Buyer Test', '+84900000001', 'Ho Chi Minh',
            'District 1', 'Ben Nghe', '1 Original Street',
            10000000, 30000, 10000000, CURRENT_TIMESTAMP + INTERVAL '15 minutes'
        )
    $test$,
    '23514'
);

INSERT INTO orders (
    order_id, checkout_group_id, buyer_id, seller_id, source_address_id,
    shipping_recipient_name, shipping_phone_number, shipping_province,
    shipping_district, shipping_ward, shipping_detail_address,
    subtotal, shipping_fee, total_amount, payment_due_at
) VALUES (
    4001, '00000000-0000-0000-0000-000000000001', 1001, 1002, 2001,
    'Buyer Test', '+84900000001', 'Ho Chi Minh',
    'District 1', 'Ben Nghe', '1 Original Street',
    10000000, 30000, 10030000, CURRENT_TIMESTAMP + INTERVAL '15 minutes'
);

INSERT INTO order_items (
    order_item_id, order_id, product_id, product_title, quantity, unit_price, line_total
) VALUES (
    4101, 4001, 3001, 'Used laptop', 1, 10000000, 10000000
);

SELECT pg_temp.expect_sqlstate(
    'reserved product requires owning order and expiry',
    $test$UPDATE products SET status = 'RESERVED', reserved_until = CURRENT_TIMESTAMP + INTERVAL '15 minutes' WHERE product_id = 3002$test$,
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'reservation owner must contain the product',
    $test$UPDATE products SET status = 'RESERVED', reserved_order_id = 4001, reserved_until = CURRENT_TIMESTAMP + INTERVAL '15 minutes' WHERE product_id = 3002$test$,
    '23503'
);

UPDATE products
SET status = 'RESERVED',
    reserved_order_id = 4001,
    reserved_until = CURRENT_TIMESTAMP + INTERVAL '15 minutes'
WHERE product_id = 3001;

SELECT pg_temp.assert_true(
    'reservation records its owning order',
    (SELECT reserved_order_id = 4001 FROM products WHERE product_id = 3001)
);

UPDATE addresses SET detail_address = '99 Changed Street' WHERE address_id = 2001;

SELECT pg_temp.assert_true(
    'order shipping address is an immutable snapshot',
    (SELECT shipping_detail_address = '1 Original Street' FROM orders WHERE order_id = 4001)
);

INSERT INTO payments (
    payment_id, order_id, amount, payment_method, status
) VALUES (
    5001, 4001, 10030000, 'BANK_TRANSFER_MOCK', 'PENDING'
);

SELECT pg_temp.expect_sqlstate(
    'one payment per order',
    $test$INSERT INTO payments (order_id, amount, payment_method) VALUES (4001, 10030000, 'COD_MOCK')$test$,
    '23505'
);

UPDATE payments
SET status = 'HELD',
    transaction_code = 'MOCK-TXN-0001',
    paid_at = CURRENT_TIMESTAMP,
    held_at = CURRENT_TIMESTAMP
WHERE payment_id = 5001;

SELECT pg_temp.expect_sqlstate(
    'MVP rejects partial refund',
    $test$UPDATE payments SET status = 'REFUND_PENDING', refund_amount = 5000000, refund_reason = 'Partial is not supported' WHERE payment_id = 5001$test$,
    '23514'
);

INSERT INTO complaints (
    complaint_id, order_id, created_by, reason, description
) VALUES (
    6001, 4001, 1001, 'NOT_AS_DESCRIBED', 'The item differs from its description.'
);

SELECT pg_temp.expect_sqlstate(
    'only one active complaint per order',
    $test$INSERT INTO complaints (order_id, created_by, reason, description) VALUES (4001, 1001, 'DAMAGED', 'Second active complaint')$test$,
    '23505'
);

SELECT pg_temp.expect_sqlstate(
    'report requires a target',
    $test$INSERT INTO reports (reporter_id, reason) VALUES (1001, 'SPAM')$test$,
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'transaction product cannot be hard-deleted',
    $test$DELETE FROM products WHERE product_id = 3001$test$,
    '23503'
);

SELECT pg_temp.assert_true(
    'all three seed roles exist',
    (SELECT count(*) = 3 FROM roles WHERE role_name IN ('BUYER', 'SELLER', 'ADMIN'))
);

ROLLBACK;

\echo 'PASS: all database smoke and invariant tests completed.'
