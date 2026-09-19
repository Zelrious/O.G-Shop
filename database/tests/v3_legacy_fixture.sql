\set ON_ERROR_STOP on

-- Fixture representing valid V1/V2 data before V3 is applied.
INSERT INTO users (user_id, email, password_hash, full_name) VALUES
    (9101, 'legacy-buyer@example.com', '$2a$legacy-buyer', 'Legacy Buyer'),
    (9102, 'legacy-seller@example.com', '$2a$legacy-seller', 'Legacy Seller');

INSERT INTO categories (category_id, category_name, slug)
VALUES (9201, 'Legacy category', 'legacy-category');

INSERT INTO products (
    product_id, seller_id, category_id, title, description, price, condition, status
) VALUES
    (9301, 9102, 9201, 'Legacy chat product', 'Existing conversation fixture', 7000000, 'GOOD', 'ACTIVE'),
    (9302, 9102, 9201, 'Legacy offer product', 'Offer without conversation fixture', 9000000, 'FAIR', 'ACTIVE');

INSERT INTO product_media (media_id, product_id, media_type, media_url, display_order)
VALUES (9351, 9301, 'IMAGE', 'private://legacy/chat-product.jpg', 0);

INSERT INTO conversations (
    conversation_id, buyer_id, seller_id, product_id, created_at, last_message_at
) VALUES (
    9401, 9101, 9102, 9301,
    TIMESTAMPTZ '2026-09-01 08:00:00+00',
    TIMESTAMPTZ '2026-09-01 08:05:00+00'
);

INSERT INTO messages (
    message_id, conversation_id, sender_id, content, message_type, created_at, read_at
) VALUES (
    9501, 9401, 9101, 'Legacy message', 'TEXT',
    TIMESTAMPTZ '2026-09-01 08:05:00+00',
    TIMESTAMPTZ '2026-09-01 08:06:00+00'
);

-- V1 can hold an offer even when the matching conversation does not exist.
INSERT INTO offers (
    offer_id, product_id, buyer_id, seller_id, offer_price, status, created_at
) VALUES (
    9601, 9302, 9101, 9102, 8500000, 'PENDING',
    TIMESTAMPTZ '2026-09-02 09:00:00+00'
);

INSERT INTO orders (
    order_id, checkout_group_id, buyer_id, seller_id,
    shipping_recipient_name, shipping_phone_number, shipping_province,
    shipping_district, shipping_ward, shipping_detail_address,
    subtotal, shipping_fee, total_amount, payment_due_at, created_at
) VALUES (
    9701, '97010000-0000-0000-0000-000000000001', 9101, 9102,
    'Legacy Buyer', '+84900009101', 'Ho Chi Minh',
    'District 1', 'Ben Nghe', '1 Legacy Street',
    9000000, 30000, 9030000,
    TIMESTAMPTZ '2026-09-03 10:15:00+00',
    TIMESTAMPTZ '2026-09-03 10:00:00+00'
);

INSERT INTO order_items (
    order_item_id, order_id, product_id, product_title, quantity, unit_price, line_total
) VALUES (
    9751, 9701, 9302, 'Legacy offer product', 1, 9000000, 9000000
);
