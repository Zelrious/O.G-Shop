-- Read-only fixture verification. Safe after running UI tests; initial states may change.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '30s';
DO $verify$
DECLARE
    manifest JSONB;
    account_ids BIGINT[];
    product_ids BIGINT[];
    order_ids BIGINT[];
    voucher_ids BIGINT[];
BEGIN
    SELECT new_values INTO manifest FROM audit_logs
    WHERE action = 'DEV_SEED_CREATED' AND request_id = 'og-shop-development-test-v1'
    ORDER BY log_id LIMIT 1;
    IF manifest IS NULL THEN RAISE EXCEPTION 'Development seed manifest not found.'; END IF;
    SELECT array_agg(value::TEXT::BIGINT) INTO account_ids FROM jsonb_each(manifest -> 'accounts');
    SELECT array_agg(value::TEXT::BIGINT) INTO product_ids FROM jsonb_each(manifest -> 'products');
    SELECT array_agg(value::TEXT::BIGINT) INTO order_ids FROM jsonb_each(manifest -> 'orders');
    SELECT array_agg(value::TEXT::BIGINT) INTO voucher_ids FROM jsonb_each(manifest -> 'vouchers');
    IF (SELECT count(*) FROM users WHERE user_id = ANY(account_ids)) <> 4
       OR (SELECT count(*) FROM products WHERE product_id = ANY(product_ids)) <> 24
       OR (SELECT count(*) FROM orders WHERE order_id = ANY(order_ids)) <> 6
       OR (SELECT count(*) FROM vouchers WHERE voucher_id = ANY(voucher_ids)) <> 8 THEN
        RAISE EXCEPTION 'A fixture record is missing. Restore the test DB rather than resetting live records.';
    END IF;
    IF EXISTS (SELECT 1 FROM products p WHERE p.product_id = ANY(product_ids) AND p.seller_id <> (manifest -> 'accounts' ->> 'seller')::BIGINT)
       OR EXISTS (SELECT 1 FROM orders o WHERE o.order_id = ANY(order_ids)
                  AND (o.buyer_id <> (manifest -> 'accounts' ->> 'buyer')::BIGINT
                       OR o.seller_id <> (manifest -> 'accounts' ->> 'seller')::BIGINT)) THEN
        RAISE EXCEPTION 'Fixture product/order ownership mismatch.';
    END IF;
    IF EXISTS (SELECT 1 FROM products p WHERE p.product_id = ANY(product_ids)
        AND NOT EXISTS (SELECT 1 FROM product_categories pc WHERE pc.product_id=p.product_id AND pc.category_id=p.category_id)) THEN
        RAISE EXCEPTION 'Fixture product has no valid category membership.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM products p LEFT JOIN product_media m USING(product_id)
        WHERE p.product_id = ANY(product_ids) AND p.status IN ('ACTIVE','PENDING') AND p.deleted_at IS NULL
        GROUP BY p.product_id
        HAVING count(*) FILTER (WHERE m.media_type = 'IMAGE') NOT BETWEEN 1 AND 5
            OR count(*) FILTER (WHERE m.media_type = 'VIDEO') <> 1
    ) THEN RAISE EXCEPTION 'Published fixture violates image/video counts.'; END IF;
    IF EXISTS (SELECT 1 FROM product_media WHERE product_id = ANY(product_ids)
        AND ((media_type = 'IMAGE' AND file_size_bytes > 5242880)
             OR (media_type = 'VIDEO' AND (file_size_bytes > 52428800 OR duration_seconds > 60)))) THEN
        RAISE EXCEPTION 'Fixture media exceeds V6 limits.';
    END IF;
    IF EXISTS (SELECT 1 FROM products p JOIN orders o ON o.order_id = p.reserved_order_id
        WHERE p.product_id = ANY(product_ids) AND p.status = 'RESERVED' AND p.reserved_until <> o.payment_due_at) THEN
        RAISE EXCEPTION 'Product hold deadline differs from order deadline.';
    END IF;
    IF EXISTS (SELECT 1 FROM orders o WHERE o.order_id = ANY(order_ids)
        AND (o.payment_due_at <> o.created_at + INTERVAL '1 hour'
             OR o.total_amount <> o.subtotal + o.buyer_system_fee + o.shipping_fee
                                  - o.voucher_discount_amount - o.shipping_discount_amount
             OR o.seller_proceeds <> o.subtotal - o.seller_system_fee
                                    - CASE WHEN o.sponsor_type = 'SELLER' THEN o.voucher_discount_amount ELSE 0 END
             OR o.subtotal <> (SELECT sum(i.agreed_price * i.quantity) FROM order_items i WHERE i.order_id = o.order_id))) THEN
        RAISE EXCEPTION 'Order deadline or money snapshot mismatch.';
    END IF;
    IF EXISTS (SELECT 1 FROM payments p JOIN orders o USING(order_id)
        WHERE p.order_id = ANY(order_ids) AND (p.amount <> o.total_amount OR p.payment_method NOT LIKE '%MOCK')) THEN
        RAISE EXCEPTION 'Fixture payment amount or mock method mismatch.';
    END IF;
    IF (SELECT count(*) FROM payments WHERE order_id = ANY(order_ids)) <> 6 THEN
        RAISE EXCEPTION 'Expected six mock payment records.';
    END IF;
    RAISE NOTICE 'PASS: fixture records, ownership, publication media, holds, price snapshots and mock payments.';
END;
$verify$;

SELECT u.email,u.status,string_agg(r.role_name,', ' ORDER BY r.role_name) AS roles
FROM users u JOIN user_roles ur USING(user_id) JOIN roles r USING(role_id)
WHERE u.email IN ('admin@gmail.com','mua@gmail.com','ban@gmail.com','ktv@gmail.com') GROUP BY u.user_id ORDER BY u.email;
SELECT p.status,count(*) AS fixture_products FROM products p
WHERE p.product_id IN (SELECT value::TEXT::BIGINT FROM audit_logs a,jsonb_each(a.new_values -> 'products')
    WHERE a.action = 'DEV_SEED_CREATED' AND a.request_id = 'og-shop-development-test-v1') GROUP BY p.status ORDER BY p.status;
SELECT o.order_id,o.status,p.status AS payment_status,o.total_amount,o.payment_due_at,
       greatest(0,floor(extract(epoch FROM o.payment_due_at - CURRENT_TIMESTAMP))) AS remaining_seconds
FROM orders o JOIN payments p USING(order_id)
WHERE o.order_id IN (SELECT value::TEXT::BIGINT FROM audit_logs a,jsonb_each(a.new_values -> 'orders')
    WHERE a.action = 'DEV_SEED_CREATED' AND a.request_id = 'og-shop-development-test-v1') ORDER BY o.order_id;
COMMIT;
