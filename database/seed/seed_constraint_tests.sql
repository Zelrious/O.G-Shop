-- Isolated test database only, immediately after fresh development seed import.
-- Exercise negative paths against PostgreSQL. All attempted changes are rolled back.
BEGIN;
DO $tests$
DECLARE
    manifest JSONB;
    fixture_seller BIGINT;
    fixture_buyer BIGINT;
    fixture_product BIGINT;
    guard_rejected BOOLEAN := FALSE;
BEGIN
    SELECT new_values INTO STRICT manifest FROM audit_logs
    WHERE action = 'DEV_SEED_CREATED' AND request_id = 'og-shop-development-test-v1';
    fixture_seller := (manifest -> 'accounts' ->> 'seller')::BIGINT;
    fixture_buyer := (manifest -> 'accounts' ->> 'buyer')::BIGINT;
    BEGIN
        INSERT INTO users (email,password_hash,full_name) VALUES ('mua@gmail.com','unused','Duplicate seed email');
        RAISE EXCEPTION 'FAIL: duplicate email accepted';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS: duplicate email rejected'; END;
    BEGIN
        INSERT INTO addresses (user_id,recipient_name,phone_number,province,district,ward,detail_address,is_default)
        VALUES (fixture_buyer,'Test','0900000002','Test','Test','Test','Duplicate default',TRUE);
        RAISE EXCEPTION 'FAIL: second default address accepted';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS: duplicate default address rejected'; END;

    fixture_product := (manifest -> 'products' ->> 'P15')::BIGINT;
    BEGIN
        UPDATE products SET status = 'PENDING' WHERE product_id = fixture_product;
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM NOT LIKE '%ít nhất 1 video%' THEN RAISE; END IF;
        guard_rejected := TRUE;
    END;
    IF NOT guard_rejected THEN RAISE EXCEPTION 'FAIL: missing-video draft published'; END IF;
    RAISE NOTICE 'PASS: draft without video cannot submit';

    guard_rejected := FALSE;
    fixture_product := (manifest -> 'products' ->> 'P13')::BIGINT;
    BEGIN
        INSERT INTO product_media (product_id,media_type,media_url,display_order)
        VALUES (fixture_product,'IMAGE','https://example.invalid/extra.jpg',6);
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM NOT LIKE '%tối đa 5 hình ảnh%' THEN RAISE; END IF;
        guard_rejected := TRUE;
    END;
    IF NOT guard_rejected THEN RAISE EXCEPTION 'FAIL: sixth image accepted'; END IF;
    RAISE NOTICE 'PASS: sixth product image rejected';
    BEGIN
        UPDATE products SET status = 'RESERVED' WHERE product_id = (manifest -> 'products' ->> 'P01')::BIGINT;
        RAISE EXCEPTION 'FAIL: reservation without order/deadline accepted';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: incomplete reservation rejected'; END;
    IF (SELECT count(*) FROM products WHERE seller_id = fixture_seller AND status = 'PENDING') <> 2 THEN
        RAISE EXCEPTION 'FAIL: negative-path tests changed pending fixtures';
    END IF;
END;
$tests$;
ROLLBACK;
