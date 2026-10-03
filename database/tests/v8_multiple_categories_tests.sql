-- Run on an isolated V8 test database containing at least one product.
-- Negative paths roll back; never reset application data.
BEGIN;
DO $tests$
DECLARE
    fixture_product BIGINT;
    fixture_category BIGINT;
BEGIN
    SELECT product_id,category_id INTO STRICT fixture_product,fixture_category
    FROM products ORDER BY product_id LIMIT 1;
    BEGIN
        INSERT INTO product_categories(product_id,category_id) VALUES(fixture_product,fixture_category);
        RAISE EXCEPTION 'FAIL: duplicate category membership accepted';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS: duplicate category membership rejected'; END;
    BEGIN
        INSERT INTO product_categories(product_id,category_id) VALUES(fixture_product,9223372036854775000);
        RAISE EXCEPTION 'FAIL: nonexistent category accepted';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'PASS: nonexistent category rejected'; END;
    BEGIN
        DELETE FROM product_categories WHERE product_id=fixture_product;
        SET CONSTRAINTS fk_products_compatibility_category_membership IMMEDIATE;
        RAISE EXCEPTION 'FAIL: product without any membership accepted';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'PASS: empty category membership rejected'; END;
    BEGIN
        INSERT INTO product_categories(product_id,category_id) VALUES(9223372036854775000,fixture_category);
        RAISE EXCEPTION 'FAIL: nonexistent product accepted';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'PASS: nonexistent product rejected'; END;
END;
$tests$;
ROLLBACK;
