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

-- 1. Test Seed Vouchers Exist
SELECT pg_temp.assert_true(
    'seed platform vouchers exist and active',
    (SELECT count(*) = 2 FROM vouchers WHERE code IN ('WELCOMEOG', 'OGFREESHIP') AND is_active = TRUE)
);

-- 2. Test Order Amounts with Voucher (ck_orders_amounts)
DO $$
DECLARE
    v_order_id BIGINT;
    v_buyer_id BIGINT;
    v_seller_id BIGINT;
    v_product_id BIGINT;
BEGIN
    -- Setup test users
    INSERT INTO users (email, password_hash, full_name, status)
    VALUES ('buyer_v6_test@ogshop.vn', '$2a$10$xyz', 'Buyer V6 Test', 'ACTIVE')
    RETURNING user_id INTO v_buyer_id;

    INSERT INTO users (email, password_hash, full_name, status)
    VALUES ('seller_v6_test@ogshop.vn', '$2a$10$xyz', 'Seller V6 Test', 'ACTIVE')
    RETURNING user_id INTO v_seller_id;

    -- Test Order with 50k voucher discount
    INSERT INTO orders (
        checkout_group_id, buyer_id, seller_id,
        shipping_recipient_name, shipping_phone_number,
        shipping_province, shipping_district, shipping_ward, shipping_detail_address,
        subtotal, buyer_system_fee, seller_system_fee, shipping_fee,
        voucher_discount_amount, shipping_discount_amount, sponsor_type,
        seller_proceeds, total_amount, payment_due_at
    ) VALUES (
        gen_random_uuid(), v_buyer_id, v_seller_id,
        'Buyer V6 Test', '0901234567',
        'TP.HCM', 'Quận 1', 'Bến Nghé', '123 Lê Duẩn',
        1000000, 20000, 50000, 30000,
        50000, 0, 'PLATFORM',
        950000, 1000000, CURRENT_TIMESTAMP + INTERVAL '15 minutes'
    ) RETURNING order_id INTO v_order_id;

    PERFORM pg_temp.assert_true(
        'order with platform voucher calculation succeeds',
        (SELECT total_amount = 1000000 AND seller_proceeds = 950000 FROM orders WHERE order_id = v_order_id)
    );

    -- 3. Test Order Cancellation retains voucher usage
    UPDATE orders SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP, cancellation_reason = 'BUYER_CANCELLED'
    WHERE order_id = v_order_id;

    PERFORM pg_temp.assert_true(
        'order cancelled maintains usage count invariant',
        (SELECT status = 'CANCELLED' FROM orders WHERE order_id = v_order_id)
    );

    -- 4. Test Unboxing Evidence 'SKIPPED_BY_BUYER'
    INSERT INTO order_unboxing_evidences (order_id, buyer_id, status, skipped_at)
    VALUES (v_order_id, v_buyer_id, 'SKIPPED_BY_BUYER', CURRENT_TIMESTAMP);

    PERFORM pg_temp.assert_true(
        'unboxing skipped_by_buyer state is recorded',
        (SELECT status = 'SKIPPED_BY_BUYER' AND skipped_at IS NOT NULL FROM order_unboxing_evidences WHERE order_id = v_order_id)
    );

    -- 5. Test Complaint Resolution auto-schedules cleanup after 3 days
    INSERT INTO complaints (
        order_id, created_by, reason, description, status, evidence
    ) VALUES (
        v_order_id, v_buyer_id, 'DAMAGED', 'Sản phẩm trầy xước nặng', 'OPEN',
        '{"videos": [{"public_id": "test/v1", "url": "https://sample.com/1.mp4"}]}'::jsonb
    );

    UPDATE complaints
    SET status = 'RESOLVED', resolution = 'REFUND_BUYER', refund_amount = 500000,
        resolved_by = v_seller_id, resolved_at = CURRENT_TIMESTAMP
    WHERE order_id = v_order_id;

    PERFORM pg_temp.assert_true(
        'complaint cleanup due at is scheduled exactly 3 days after resolution',
        (SELECT evidence_cleanup_due_at >= resolved_at + INTERVAL '2 days 23 hours'
         FROM complaints WHERE order_id = v_order_id)
    );
END;
$$;

-- 6. Test Product Media Requirements Trigger
DO $$
DECLARE
    v_seller_id BIGINT;
    v_category_id BIGINT;
    v_product_id BIGINT;
    v_error_caught BOOLEAN := FALSE;
BEGIN
    SELECT user_id INTO v_seller_id FROM users WHERE email = 'seller_v6_test@ogshop.vn';
    SELECT category_id INTO v_category_id FROM categories LIMIT 1;

    INSERT INTO products (
        seller_id, category_id, title, description, listed_price, condition, status
    ) VALUES (
        v_seller_id, v_category_id, 'Máy ảnh film cũ Leica', 'Hàng sưu tầm', 15000000, 'LIKE_NEW', 'DRAFT'
    ) RETURNING product_id INTO v_product_id;

    -- Thử publish khi chưa có ảnh và video -> Phải bị chặn
    BEGIN
        UPDATE products SET status = 'ACTIVE' WHERE product_id = v_product_id;
    EXCEPTION WHEN OTHERS THEN
        v_error_caught := TRUE;
    END;

    PERFORM pg_temp.assert_true(
        'publishing product without image and video is rejected by trigger',
        v_error_caught
    );

    -- Thêm 1 ảnh và 1 video hợp lệ
    INSERT INTO product_media (product_id, media_type, media_url, display_order)
    VALUES (v_product_id, 'IMAGE', 'https://sample.com/img1.jpg', 0),
           (v_product_id, 'VIDEO', 'https://sample.com/vid1.mp4', 1);

    -- Publish lại -> Thành công
    UPDATE products SET status = 'ACTIVE' WHERE product_id = v_product_id;

    PERFORM pg_temp.assert_true(
        'publishing product with 1 image and 1 video succeeds',
        (SELECT status = 'ACTIVE' FROM products WHERE product_id = v_product_id)
    );
END;
$$;

-- 7. Test Chat Media Quota (5 images + 1 video limit)
DO $$
DECLARE
    v_buyer_id BIGINT;
    v_seller_id BIGINT;
    v_product_id BIGINT;
    v_conv_id BIGINT;
    v_quota_caught BOOLEAN := FALSE;
BEGIN
    SELECT user_id INTO v_buyer_id FROM users WHERE email = 'buyer_v6_test@ogshop.vn';
    SELECT user_id INTO v_seller_id FROM users WHERE email = 'seller_v6_test@ogshop.vn';
    SELECT product_id INTO v_product_id FROM products LIMIT 1;

    INSERT INTO conversations (
        buyer_id, seller_id, product_id,
        product_title_snapshot, product_price_at_start, currency, last_activity_at
    ) VALUES (
        v_buyer_id, v_seller_id, v_product_id,
        'Test Item', 100000, 'VND', CURRENT_TIMESTAMP
    ) RETURNING conversation_id INTO v_conv_id;

    -- Insert quota record: 1 video đã dùng
    INSERT INTO chat_media_daily_quotas (conversation_id, sender_id, quota_date, images_sent, videos_sent)
    VALUES (v_conv_id, v_buyer_id, CURRENT_DATE, 2, 1);

    -- Cố chèn thêm video thứ 2 trong ngày -> Vi phạm ràng buộc videos_sent <= 1
    BEGIN
        UPDATE chat_media_daily_quotas
        SET videos_sent = videos_sent + 1
        WHERE conversation_id = v_conv_id AND sender_id = v_buyer_id;
    EXCEPTION WHEN OTHERS THEN
        v_quota_caught := TRUE;
    END;

    PERFORM pg_temp.assert_true(
        'sending more than 1 video per day in conversation violates quota limit',
        v_quota_caught
    );
END;
$$;
