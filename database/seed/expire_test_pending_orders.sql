-- DEVELOPMENT ONLY: move still-unpaid fixture orders just past their one-hour deadline.
-- Backend scheduler, NOT this script, must cancel orders and release reservations.
BEGIN;
DO $expiry$
DECLARE
    manifest JSONB;
    fixture_order_ids BIGINT[];
    fixture_now TIMESTAMPTZ := CURRENT_TIMESTAMP;
BEGIN
    PERFORM pg_advisory_xact_lock(73939002);
    SELECT new_values INTO manifest FROM audit_logs
    WHERE action = 'DEV_SEED_CREATED' AND request_id = 'og-shop-development-test-v1' ORDER BY log_id LIMIT 1;
    IF manifest IS NULL THEN RAISE EXCEPTION 'Import development_test_data.sql first.'; END IF;
    SELECT array_agg(order_id ORDER BY order_id) INTO fixture_order_ids FROM orders
    WHERE order_id IN ((manifest -> 'orders' ->> 'O01')::BIGINT,(manifest -> 'orders' ->> 'O02')::BIGINT)
      AND status = 'PAYMENT_PENDING';
    PERFORM 1 FROM orders WHERE order_id = ANY(fixture_order_ids) ORDER BY order_id FOR UPDATE;
    UPDATE orders SET created_at = fixture_now - INTERVAL '61 minutes',
                      payment_due_at = fixture_now - INTERVAL '1 minute',updated_at = fixture_now,version = version + 1
    WHERE order_id = ANY(fixture_order_ids) AND status = 'PAYMENT_PENDING';
    UPDATE products SET reserved_until = fixture_now - INTERVAL '1 minute',updated_at = fixture_now,version = version + 1
    WHERE reserved_order_id = ANY(fixture_order_ids) AND status = 'RESERVED';
    RAISE NOTICE 'Pending fixture deadlines moved into the past. Run the backend scheduler and check CANCELLED/ACTIVE.';
END;
$expiry$;
COMMIT;
