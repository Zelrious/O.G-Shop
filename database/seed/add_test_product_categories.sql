-- DEVELOPMENT ONLY: add multi-category examples to the original V7 fixture after V8.
-- Run once; later invocations preserve category edits made through the application.
BEGIN;
DO $categories$
DECLARE
    manifest JSONB;
BEGIN
    PERFORM pg_advisory_xact_lock(73939002);
    IF to_regclass('public.product_categories') IS NULL THEN
        RAISE EXCEPTION 'Flyway V8 is required.';
    END IF;
    SELECT new_values INTO manifest FROM audit_logs WHERE action='DEV_SEED_CREATED'
        AND request_id='og-shop-development-test-v1' ORDER BY log_id LIMIT 1;
    IF manifest IS NULL THEN RAISE EXCEPTION 'Import development_test_data.sql first.'; END IF;
    IF EXISTS (SELECT 1 FROM audit_logs WHERE action='DEV_TEST_CATEGORIES_ADDED'
        AND request_id='og-shop-development-test-v1') THEN
        RAISE NOTICE 'Category examples already added; preserving edited selections.';
        RETURN;
    END IF;
    WITH added AS (
    INSERT INTO product_categories(product_id,category_id)
    SELECT p.product_id,c.category_id
    FROM (VALUES ('P01','collectibles'),('P02','fashion'),('P03','books-stationery'),('P05','other'))
        AS extra(product_code,category_slug)
    JOIN products p ON p.product_id=(manifest -> 'products' ->> extra.product_code)::BIGINT
        AND p.seller_id=(manifest -> 'accounts' ->> 'seller')::BIGINT
        AND p.status IN ('DRAFT','ACTIVE','HIDDEN','REJECTED') AND p.deleted_at IS NULL
    JOIN categories c ON c.slug=extra.category_slug AND c.is_active
    ON CONFLICT DO NOTHING RETURNING product_id
    )
    UPDATE products SET version=version+1,updated_at=CURRENT_TIMESTAMP
    WHERE product_id IN (SELECT product_id FROM added);
    INSERT INTO audit_logs(action,entity_type,new_values,request_id)
    VALUES ('DEV_TEST_CATEGORIES_ADDED','DevelopmentSeed','{"fixtures":["P01","P02","P03","P05"]}',
            'og-shop-development-test-v1');
    RAISE NOTICE 'Added multi-category examples without resetting orders, profiles or media.';
END;
$categories$;
COMMIT;
