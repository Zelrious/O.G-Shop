-- ============================================================================
-- SQL Invariant Tests for Flyway V10 (UC70 Product Moderation Enforcement)
-- Run inside a transaction that is rolled back at the end.
-- ============================================================================

BEGIN;

-- Setup test user and category
INSERT INTO users (user_id, email, password_hash, full_name, phone_number)
VALUES (99991, 'v10-seller@example.test', 'hash', 'V10 Seller', '0999999991'),
       (99992, 'v10-reviewer@example.test', 'hash', 'V10 Reviewer', '0999999992')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO categories (category_id, category_name, slug)
VALUES (99991, 'V10 Category', 'v10-cat')
ON CONFLICT (category_id) DO NOTHING;

INSERT INTO products (product_id, seller_id, category_id, title, description, listed_price, condition, status, content_revision)
VALUES (99991, 99991, 99991, 'V10 Test Product', 'Test Description', 100000, 'GOOD', 'PENDING', 1)
ON CONFLICT (product_id) DO UPDATE SET content_revision = 1;

INSERT INTO product_categories (product_id, category_id)
VALUES (99991, 99991)
ON CONFLICT DO NOTHING;

-- Test 1: Negative product_version in product_moderation_decisions must fail
DO $$
BEGIN
    INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
    VALUES (99991, 99992, 'APPROVED', NULL, -1, 'ck-neg-ver');
    RAISE EXCEPTION 'TEST 1 FAILED: Negative product_version was unexpectedly allowed!';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'TEST 1 PASSED: Negative product_version was rejected by check constraint.';
END $$;

-- Test 2: APPROVED decision with reason must fail
DO $$
BEGIN
    INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
    VALUES (99991, 99992, 'APPROVED', 'Reason for approval should fail', 1, 'ck-app-reason');
    RAISE EXCEPTION 'TEST 2 FAILED: APPROVED with reason was unexpectedly allowed!';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'TEST 2 PASSED: APPROVED with reason was rejected by check constraint.';
END $$;

-- Test 3: REJECTED decision with NULL reason must fail
DO $$
BEGIN
    INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
    VALUES (99991, 99992, 'REJECTED', NULL, 1, 'ck-rej-null');
    RAISE EXCEPTION 'TEST 3 FAILED: REJECTED with NULL reason was unexpectedly allowed!';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'TEST 3 PASSED: REJECTED with NULL reason was rejected by check constraint.';
END $$;

-- Test 4: REJECTED decision with blank reason must fail
DO $$
BEGIN
    INSERT INTO product_moderation_decisions (product_id, reviewer_id, decision, reason, product_version, command_key)
    VALUES (99991, 99992, 'REJECTED', '   ', 1, 'ck-rej-blank');
    RAISE EXCEPTION 'TEST 4 FAILED: REJECTED with blank reason was unexpectedly allowed!';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'TEST 4 PASSED: REJECTED with blank reason was rejected by check constraint.';
END $$;

-- Test 5: Valid decision insertion must succeed
INSERT INTO product_moderation_decisions (decision_id, product_id, reviewer_id, decision, reason, product_version, command_key)
VALUES (99991, 99991, 99992, 'APPROVED', NULL, 1, 'ck-v10-valid-app');

-- Test 6: UPDATE on product_moderation_decisions must fail (Append-only Trigger)
DO $$
BEGIN
    UPDATE product_moderation_decisions
    SET reason = 'Tampered reason'
    WHERE decision_id = 99991;
    RAISE EXCEPTION 'TEST 6 FAILED: UPDATE was unexpectedly allowed on append-only table!';
EXCEPTION
    WHEN raise_exception THEN
        RAISE NOTICE 'TEST 6 PASSED: UPDATE was blocked by append-only trigger.';
END $$;

-- Test 7: DELETE on product_moderation_decisions must fail (Append-only Trigger)
DO $$
BEGIN
    DELETE FROM product_moderation_decisions
    WHERE decision_id = 99991;
    RAISE EXCEPTION 'TEST 7 FAILED: DELETE was unexpectedly allowed on append-only table!';
EXCEPTION
    WHEN raise_exception THEN
        RAISE NOTICE 'TEST 7 PASSED: DELETE was blocked by append-only trigger.';
END $$;

-- Test 8: products.content_revision < 1 must fail
DO $$
BEGIN
    UPDATE products
    SET content_revision = 0
    WHERE product_id = 99991;
    RAISE EXCEPTION 'TEST 8 FAILED: content_revision < 1 was unexpectedly allowed!';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'TEST 8 PASSED: content_revision < 1 was rejected by check constraint.';
END $$;

ROLLBACK;
