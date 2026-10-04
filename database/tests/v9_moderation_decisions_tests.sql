-- Run on an isolated V9 test database containing at least one product and user.
-- Negative paths roll back; never mutate or reset application data.
BEGIN;
DO $tests$
DECLARE
    fixture_product BIGINT;
    fixture_reviewer BIGINT;
    fixture_version BIGINT;
    key_test VARCHAR(100) := 'v9-sql-test-' || gen_random_uuid();
BEGIN
    SELECT product_id, version INTO STRICT fixture_product, fixture_version
    FROM products ORDER BY product_id LIMIT 1;

    SELECT user_id INTO STRICT fixture_reviewer
    FROM users ORDER BY user_id LIMIT 1;

    -- 1. Valid APPROVED insert
    INSERT INTO product_moderation_decisions(product_id, reviewer_id, decision, reason, product_version, command_key)
    VALUES (fixture_product, fixture_reviewer, 'APPROVED', NULL, fixture_version, key_test);
    RAISE NOTICE 'PASS: valid APPROVED decision inserted';

    -- 2. Duplicate command_key rejected
    BEGIN
        INSERT INTO product_moderation_decisions(product_id, reviewer_id, decision, reason, product_version, command_key)
        VALUES (fixture_product, fixture_reviewer, 'APPROVED', NULL, fixture_version, key_test);
        RAISE EXCEPTION 'FAIL: duplicate command_key accepted';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS: duplicate command_key rejected'; END;

    -- 3. Invalid decision string rejected
    BEGIN
        INSERT INTO product_moderation_decisions(product_id, reviewer_id, decision, reason, product_version, command_key)
        VALUES (fixture_product, fixture_reviewer, 'PENDING', NULL, fixture_version, 'key-' || gen_random_uuid());
        RAISE EXCEPTION 'FAIL: invalid decision value accepted';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: invalid decision value rejected'; END;

    -- 4. REJECTED with NULL reason rejected
    BEGIN
        INSERT INTO product_moderation_decisions(product_id, reviewer_id, decision, reason, product_version, command_key)
        VALUES (fixture_product, fixture_reviewer, 'REJECTED', NULL, fixture_version, 'key-' || gen_random_uuid());
        RAISE EXCEPTION 'FAIL: REJECTED with NULL reason accepted';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: REJECTED with NULL reason rejected'; END;

    -- 5. REJECTED with blank reason rejected
    BEGIN
        INSERT INTO product_moderation_decisions(product_id, reviewer_id, decision, reason, product_version, command_key)
        VALUES (fixture_product, fixture_reviewer, 'REJECTED', '    ', fixture_version, 'key-' || gen_random_uuid());
        RAISE EXCEPTION 'FAIL: REJECTED with whitespace reason accepted';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: REJECTED with whitespace reason rejected'; END;

    -- 6. Invalid product foreign key rejected
    BEGIN
        INSERT INTO product_moderation_decisions(product_id, reviewer_id, decision, reason, product_version, command_key)
        VALUES (9223372036854775000, fixture_reviewer, 'APPROVED', NULL, 1, 'key-' || gen_random_uuid());
        RAISE EXCEPTION 'FAIL: nonexistent product accepted';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'PASS: nonexistent product rejected'; END;

    -- 7. Invalid reviewer foreign key rejected
    BEGIN
        INSERT INTO product_moderation_decisions(product_id, reviewer_id, decision, reason, product_version, command_key)
        VALUES (fixture_product, 9223372036854775000, 'APPROVED', NULL, fixture_version, 'key-' || gen_random_uuid());
        RAISE EXCEPTION 'FAIL: nonexistent reviewer accepted';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'PASS: nonexistent reviewer rejected'; END;
END;
$tests$;
ROLLBACK;
