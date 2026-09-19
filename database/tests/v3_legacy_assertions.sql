\set ON_ERROR_STOP on

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

SELECT pg_temp.assert_true(
    'legacy product price becomes listed price',
    (SELECT listed_price = 9000000 FROM products WHERE product_id = 9302)
);

SELECT pg_temp.assert_true(
    'existing conversation receives product snapshot and latest message',
    (
        SELECT product_title_snapshot = 'Legacy chat product'
           AND product_price_at_start = 7000000
           AND product_thumbnail_snapshot = 'private://legacy/chat-product.jpg'
           AND last_message_id = 9501
           AND last_activity_at = TIMESTAMPTZ '2026-09-01 08:05:00+00'
        FROM conversations
        WHERE conversation_id = 9401
    )
);

SELECT pg_temp.assert_true(
    'legacy offer receives a matching conversation',
    (
        SELECT count(*) = 1
        FROM conversations
        WHERE buyer_id = 9101 AND seller_id = 9102 AND product_id = 9302
    )
);

SELECT pg_temp.assert_true(
    'legacy offer is backfilled as buyer proposal with zero-fee snapshot',
    (
        SELECT proposer_id = 9101
           AND offered_item_price = 8500000
           AND buyer_system_fee = 0
           AND seller_system_fee = 0
           AND buyer_subtotal = 8500000
           AND seller_proceeds = 8500000
        FROM offers
        WHERE offer_id = 9601
    )
);

SELECT pg_temp.assert_true(
    'both participants are backfilled for every conversation',
    (
        SELECT count(*) = 4
        FROM conversation_user_state
        WHERE conversation_id IN (
            SELECT conversation_id
            FROM conversations
            WHERE buyer_id = 9101 AND seller_id = 9102
        )
    )
);

SELECT pg_temp.assert_true(
    'legacy message receives an idempotency key',
    (SELECT client_message_id IS NOT NULL FROM messages WHERE message_id = 9501)
);

SELECT pg_temp.assert_true(
    'legacy order receives zero-fee aggregates',
    (
        SELECT buyer_system_fee = 0
           AND seller_system_fee = 0
           AND seller_proceeds = subtotal
           AND total_amount = subtotal + shipping_fee
        FROM orders
        WHERE order_id = 9701
    )
);

SELECT pg_temp.assert_true(
    'legacy order item becomes list-price snapshot',
    (
        SELECT listed_price = 9000000
           AND agreed_price = 9000000
           AND buyer_system_fee = 0
           AND seller_system_fee = 0
           AND buyer_line_total = 9000000
           AND seller_line_proceeds = 9000000
           AND pricing_source = 'LIST_PRICE'
           AND accepted_offer_id IS NULL
        FROM order_items
        WHERE order_item_id = 9751
    )
);

SELECT pg_temp.assert_true(
    'removed legacy columns are absent',
    NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND (
              (table_name = 'products' AND column_name = 'price')
              OR (table_name = 'messages' AND column_name = 'read_at')
              OR (table_name = 'offers' AND column_name IN ('product_id', 'buyer_id', 'seller_id', 'offer_price'))
              OR (table_name = 'order_items' AND column_name IN ('unit_price', 'line_total'))
          )
    )
);

\echo 'PASS: V3 legacy migration/backfill assertions completed.'
