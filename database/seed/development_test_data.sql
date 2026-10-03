-- O.G Shop UI/logic fixtures, schema V1-V8, PostgreSQL 17.
-- DEVELOPMENT ONLY. All identities, addresses, bank details and payments are fictitious.
-- Execute the entire file. Any error rolls back every fixture.
-- Rerunning preserves changes made while testing; it does NOT reset the dataset.
BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL timezone = 'UTC';
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

DO $seed$
DECLARE
    seed_request CONSTANT TEXT := 'og-shop-development-test-v1';
    -- PasswordEncoderFactories.createDelegatingPasswordEncoder(), password: 12345678.
    test_password CONSTANT TEXT := '{bcrypt}$2b$10$.r8pXzsaJ8zjiz5EMqDjY.Vmxc1rSy8HPHgq3oCVvPvq7UOCuGJhi';
    seed_now TIMESTAMPTZ := CURRENT_TIMESTAMP;
    admin_id BIGINT;
    buyer_id BIGINT;
    seller_id BIGINT;
    technician_id BIGINT;
    buyer_address_id BIGINT;
    policy_id BIGINT;
    product_id_value BIGINT;
    order_id_value BIGINT;
    voucher_id_value BIGINT;
    row_data RECORD;
    image_index INTEGER;
    image_url_value TEXT;
    subtotal_value NUMERIC(19,2);
    seed_discount_amount NUMERIC(19,2);
    proceeds_value NUMERIC(19,2);
    order_created TIMESTAMPTZ;
    order_due TIMESTAMPTZ;
    manifest JSONB := '{"products":{},"orders":{},"vouchers":{}}'::jsonb;
    previous_manifest JSONB;
BEGIN
    -- Serialize imports of this fixture; no schema changes or table truncation.
    PERFORM pg_advisory_xact_lock(73939002);
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'bank_name')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'requires_buyer_ekyc')
       OR to_regclass('public.vouchers') IS NULL
       OR to_regclass('public.product_categories') IS NULL THEN
        RAISE EXCEPTION 'Seed requires Flyway V1-V8. Start the backend to migrate before importing.';
    END IF;

    SELECT new_values INTO previous_manifest FROM audit_logs
    WHERE action = 'DEV_SEED_CREATED' AND request_id = seed_request
    ORDER BY log_id LIMIT 1;
    IF previous_manifest IS NOT NULL THEN
        IF (SELECT count(*) FROM users WHERE email IN
            ('admin@gmail.com', 'mua@gmail.com', 'ban@gmail.com', 'ktv@gmail.com')) <> 4 THEN
            RAISE EXCEPTION 'Seed manifest exists but a fixture account is missing. Restore the test database; no partial repair performed.';
        END IF;
        RAISE NOTICE 'Seed already imported. Preserving edited data, passwords, vouchers and order deadlines.';
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM users WHERE email IN
        ('admin@gmail.com', 'mua@gmail.com', 'ban@gmail.com', 'ktv@gmail.com')) THEN
        RAISE EXCEPTION 'A requested email already exists outside this seed. Import into a separate test database; existing accounts are not overwritten.';
    END IF;
    IF EXISTS (SELECT 1 FROM vouchers WHERE code IN
        ('TEST50K','TESTSHIP30','TEST10PC','TESTSELLER20','TESTEXPIRED','TESTFUTURE','TESTUSEDUP','TESTINACTIVE')) THEN
        RAISE EXCEPTION 'A TEST voucher code already exists outside this seed. No data was overwritten.';
    END IF;
    IF (SELECT count(*) FROM roles WHERE role_name IN ('ADMIN','BUYER','SELLER')) <> 3 THEN
        RAISE EXCEPTION 'Expected ADMIN/BUYER/SELLER roles are missing.';
    END IF;
    IF (SELECT count(*) FROM categories WHERE is_active AND slug IN
        ('electronics','fashion','home-living','books-stationery','sports-outdoors','collectibles','mother-baby','other')) <> 8 THEN
        RAISE EXCEPTION 'Expected eight active V5 categories are missing. Seed does not overwrite category configuration.';
    END IF;
    SELECT fee_policy_id INTO policy_id FROM system_fee_policies
    WHERE policy_code = 'DEFAULT_ZERO_V1';
    IF policy_id IS NULL THEN
        RAISE EXCEPTION 'V3 compatibility fee policy DEFAULT_ZERO_V1 is missing.';
    END IF;

    INSERT INTO users (email,password_hash,full_name,phone_number)
    VALUES ('admin@gmail.com',test_password,'[TEST] Quản trị viên','0900000001') RETURNING user_id INTO admin_id;
    INSERT INTO users (email,password_hash,full_name,phone_number)
    VALUES ('mua@gmail.com',test_password,'[TEST] Người mua','0900000002') RETURNING user_id INTO buyer_id;
    INSERT INTO users (email,password_hash,full_name,phone_number,bank_name,bank_account_number,bank_account_holder)
    VALUES ('ban@gmail.com',test_password,'[TEST] Người bán','0900000003',
            'Ngân hàng giả lập TEST BANK','000000001234','NGUOI BAN TEST') RETURNING user_id INTO seller_id;
    INSERT INTO users (email,password_hash,full_name,phone_number)
    VALUES ('ktv@gmail.com',test_password,'[TEST] Kiểm tra viên','0900000004') RETURNING user_id INTO technician_id;

    INSERT INTO user_roles (user_id,role_id,granted_by)
    SELECT wanted.user_id,r.role_id,admin_id FROM
        (VALUES (admin_id,'ADMIN'),(buyer_id,'BUYER'),(seller_id,'BUYER'),
                (seller_id,'SELLER'),(technician_id,'KTV')) AS wanted(user_id,role_name)
    JOIN roles r ON r.role_name = wanted.role_name;

    -- MVP fixture activation, NOT biometric/AI verification. Buyer starts unverified.
    INSERT INTO seller_verifications (user_id,verification_method,status,document_data,submitted_at,reviewed_at)
    VALUES (seller_id,'MVP_BYPASS','VERIFIED',
            '{"fixture":"og-shop-development-test-v1","note":"Synthetic seller activation; no real KYC evidence"}',
            seed_now - INTERVAL '30 days',seed_now - INTERVAL '30 days');

    -- Distinct timestamps make list order and default-deletion tests deterministic.
    INSERT INTO addresses (user_id,recipient_name,phone_number,province,district,ward,detail_address,is_default,created_at)
    VALUES (buyer_id,'[TEST] Người mua','0900000002','TP. Hồ Chí Minh','Quận 1','Phường Bến Nghé',
            '[TEST A1] 01 Đường Dữ Liệu Giả',TRUE,seed_now - INTERVAL '3 days') RETURNING address_id INTO buyer_address_id;
    INSERT INTO addresses (user_id,recipient_name,phone_number,province,district,ward,detail_address,is_default,created_at)
    VALUES (buyer_id,'[TEST] Người mua','0900000002','TP. Hồ Chí Minh','Quận 3','Phường Võ Thị Sáu',
            '[TEST A2] 02 Đường Dữ Liệu Giả',FALSE,seed_now - INTERVAL '2 days'),
           (buyer_id,'[TEST] Người mua','0900000002','Hà Nội','Cầu Giấy','Phường Dịch Vọng',
            '[TEST A3] 03 Đường Dữ Liệu Giả',FALSE,seed_now - INTERVAL '1 day'),
           (seller_id,'[TEST] Người bán','0900000003','Đà Nẵng','Hải Châu','Phường Hải Châu I',
            '[TEST S1] Kho hàng giả lập',TRUE,seed_now - INTERVAL '2 days'),
           (seller_id,'[TEST] Người bán','0900000003','Đà Nẵng','Thanh Khê','Phường Chính Gián',
            '[TEST S2] Điểm lấy hàng giả lập',FALSE,seed_now - INTERVAL '1 day'),
           (technician_id,'[TEST] KTV','0900000004','TP. Hồ Chí Minh','Quận 1','Phường Bến Nghé',
            '[TEST K1] Địa chỉ thử quyền sở hữu',TRUE,seed_now);

    -- Reuse existing categories. Public demo media is for layout/logic only.
    -- Every published fixture has 1-5 images and one 10-second sample video.
    FOR row_data IN SELECT * FROM (VALUES
        ('P01','Túi da đã qua sử dụng','fashion',450000,'GOOD','ACTIVE',FALSE,3,'bag'),
        ('P02','Giày thể thao cũ size 40','sports-outdoors',650000,'LIKE_NEW','ACTIVE',FALSE,2,'shoes'),
        ('P03','Bàn phím cơ còn đầy đủ phụ kiện','electronics',1250000,'GOOD','ACTIVE',FALSE,2,'sample'),
        ('P04','Đèn bàn đọc sách','home-living',180000,'GOOD','ACTIVE',FALSE,1,'sample'),
        ('P05','Bộ sách lập trình cơ bản','books-stationery',250000,'FAIR','ACTIVE',FALSE,2,'sample'),
        ('P06','Mô hình sưu tầm cổ điển','collectibles',890000,'LIKE_NEW','ACTIVE',FALSE,2,'sample'),
        ('P07','Xe đẩy em bé đã vệ sinh','mother-baby',950000,'GOOD','ACTIVE',FALSE,2,'sample'),
        ('P08','Hộp dụng cụ gia đình','other',320000,'GOOD','ACTIVE',FALSE,2,'sample'),
        ('P09','Túi da cao cấp — yêu cầu eKYC người mua','fashion',3500000,'LIKE_NEW','ACTIVE',TRUE,3,'bag'),
        ('P10','Bộ thiết bị sưu tầm giá cao','collectibles',50000000,'GOOD','ACTIVE',FALSE,2,'sample'),
        ('P11','Sách cũ giá thấp — thử ngưỡng voucher','books-stationery',65000,'FAIR','ACTIVE',FALSE,1,'sample'),
        ('P12','Túi xám — tối thiểu một ảnh một video','fashion',280000,'GOOD','ACTIVE',FALSE,1,'gray'),
        ('P13','Túi da chờ KTV duyệt — đủ năm ảnh','fashion',720000,'LIKE_NEW','PENDING',FALSE,5,'bag'),
        ('P14','Giày chờ kiểm duyệt thủ công','sports-outdoors',480000,'GOOD','PENDING',FALSE,1,'shoes'),
        ('P15','Tin nháp thiếu video — thử chặn gửi duyệt','electronics',700000,'GOOD','DRAFT',FALSE,1,'sample'),
        ('P16','Tin nháp chưa có media','other',100000,'POOR','DRAFT',FALSE,0,'sample'),
        ('P17','Tin bị từ chối — thử sửa và gửi lại','fashion',390000,'FAIR','REJECTED',FALSE,2,'gray'),
        ('P18','Tin đã ẩn — không công khai','fashion',350000,'GOOD','HIDDEN',FALSE,2,'bag'),
        ('P19','Túi đang giữ cho đơn chờ thanh toán','fashion',500000,'GOOD','RESERVED',FALSE,2,'bag'),
        ('P20','Giày đang giữ — thanh toán mock lỗi','sports-outdoors',750000,'GOOD','RESERVED',FALSE,2,'shoes'),
        ('P21','Túi đã bán — đơn PAID_HELD','fashion',900000,'GOOD','SOLD',FALSE,2,'gray'),
        ('P22','Túi đã bán — đơn SHIPPED','fashion',1200000,'LIKE_NEW','SOLD',FALSE,2,'bag'),
        ('P23','Giày đã bán — đơn COMPLETED','sports-outdoors',800000,'GOOD','SOLD',FALSE,2,'shoes'),
        ('P24','Túi được nhả hàng sau hủy đơn','fashion',600000,'GOOD','ACTIVE',FALSE,2,'gray')
    ) AS fixtures(code,title,category_slug,price,item_condition,target_status,requires_ekyc,image_count,image_kind)
    LOOP
        INSERT INTO products (seller_id,category_id,title,description,listed_price,condition,
                              usage_duration,defects,repair_history,included_accessories,location,
                              requires_buyer_ekyc,created_at)
        SELECT seller_id,c.category_id,'[TEST ' || row_data.code || '] ' || row_data.title,
               'Dữ liệu giả để kiểm tra UI và logic. Ảnh/video là media demo, không phải bằng chứng tình trạng hàng. Mã fixture: ' || row_data.code,
               row_data.price,row_data.item_condition,'Đã sử dụng khoảng 6 tháng',
               'Vết xước nhỏ được mô tả cho mục đích kiểm thử','Chưa sửa chữa (thông tin giả lập)',
               'Hộp và phụ kiện theo mô tả test','Hải Châu, Đà Nẵng',row_data.requires_ekyc,
               seed_now - (substring(row_data.code FROM 2)::INTEGER || ' hours')::INTERVAL
        FROM categories c WHERE c.slug = row_data.category_slug
        RETURNING product_id INTO product_id_value;
        INSERT INTO product_categories(product_id,category_id)
        SELECT product_id_value,category_id FROM categories WHERE slug = row_data.category_slug;
        manifest := jsonb_set(manifest,ARRAY['products',row_data.code],to_jsonb(product_id_value));

        image_url_value := CASE row_data.image_kind
            WHEN 'bag' THEN 'https://res.cloudinary.com/demo/image/upload/samples/ecommerce/accessories-bag.jpg'
            WHEN 'gray' THEN 'https://res.cloudinary.com/demo/image/upload/samples/ecommerce/leather-bag-gray.jpg'
            WHEN 'shoes' THEN 'https://res.cloudinary.com/demo/image/upload/samples/ecommerce/shoes.jpg'
            ELSE 'https://res.cloudinary.com/demo/image/upload/sample.jpg' END;
        FOR image_index IN 1..row_data.image_count LOOP
            INSERT INTO product_media (product_id,media_type,media_url,thumbnail_url,display_order,file_size_bytes,mime_type)
            VALUES (product_id_value,'IMAGE',image_url_value,image_url_value,image_index - 1,
                    CASE row_data.image_kind WHEN 'bag' THEN 1459256 WHEN 'gray' THEN 1323582
                         WHEN 'shoes' THEN 66416 ELSE 109669 END,'image/jpeg');
        END LOOP;
        IF row_data.target_status <> 'DRAFT' THEN
            INSERT INTO product_media (product_id,media_type,media_url,thumbnail_url,display_order,
                                       duration_seconds,file_size_bytes,mime_type)
            VALUES (product_id_value,'VIDEO','https://res.cloudinary.com/demo/video/upload/du_10/dog.mp4',
                    'https://res.cloudinary.com/demo/video/upload/du_10/dog.jpg',row_data.image_count,10,560910,'video/mp4');
        END IF;
        -- RESERVED needs a valid order item first; fill it in the order loop below.
        IF row_data.target_status <> 'RESERVED' THEN
            UPDATE products SET status = row_data.target_status,updated_at = seed_now WHERE product_id = product_id_value;
        END IF;
    END LOOP;

    INSERT INTO product_categories(product_id,category_id)
    SELECT (manifest -> 'products' ->> extra.product_code)::BIGINT,c.category_id
    FROM (VALUES ('P01','collectibles'),('P02','fashion'),('P03','books-stationery'),('P05','other'))
        AS extra(product_code,category_slug)
    JOIN categories c ON c.slug = extra.category_slug;

    FOR row_data IN SELECT * FROM (VALUES
        ('TEST50K','Giảm 50.000đ — hợp lệ','ORDER_DISCOUNT','FIXED_AMOUNT',50000,50000,200000,'PLATFORM',100,1,-1,30,TRUE),
        ('TESTSHIP30','Giảm phí ship 30.000đ','SHIPPING_DISCOUNT','FIXED_AMOUNT',30000,30000,100000,'PLATFORM',100,0,-1,30,TRUE),
        ('TEST10PC','Giảm 10% tối đa 100.000đ','ORDER_DISCOUNT','PERCENTAGE',10,100000,100000,'PLATFORM',100,0,-1,30,TRUE),
        ('TESTSELLER20','Seller tài trợ 20.000đ','ORDER_DISCOUNT','FIXED_AMOUNT',20000,20000,200000,'SELLER',100,1,-1,30,TRUE),
        ('TESTEXPIRED','Voucher đã hết hạn','ORDER_DISCOUNT','FIXED_AMOUNT',10000,10000,100000,'PLATFORM',100,0,-30,-1,TRUE),
        ('TESTFUTURE','Voucher chưa đến ngày','ORDER_DISCOUNT','FIXED_AMOUNT',10000,10000,100000,'PLATFORM',100,0,7,30,TRUE),
        ('TESTUSEDUP','Voucher đã hết lượt','ORDER_DISCOUNT','FIXED_AMOUNT',40000,40000,100000,'PLATFORM',1,1,-30,30,TRUE),
        ('TESTINACTIVE','Voucher bị tắt','ORDER_DISCOUNT','FIXED_AMOUNT',10000,10000,100000,'PLATFORM',100,0,-1,30,FALSE)
    ) AS fixtures(code,title,vtype,dtype,dvalue,max_amount,min_amount,sponsor,usage_limit,usage_count,start_days,end_days,active)
    LOOP
        INSERT INTO vouchers (code,title,description,voucher_type,discount_type,discount_value,max_discount_amount,
                              min_order_amount,sponsor_type,seller_id,total_usage_limit,current_usage_count,
                              max_usage_per_user,start_time,end_time,is_active)
        VALUES (row_data.code,'[TEST] ' || row_data.title,'Ưu đãi giả để thử validation; không phải chính sách kinh doanh.',
                row_data.vtype,row_data.dtype,row_data.dvalue,row_data.max_amount,row_data.min_amount,row_data.sponsor,
                CASE WHEN row_data.sponsor = 'SELLER' THEN seller_id ELSE NULL END,
                row_data.usage_limit,row_data.usage_count,5,
                seed_now + (row_data.start_days || ' days')::INTERVAL,
                seed_now + (row_data.end_days || ' days')::INTERVAL,row_data.active)
        RETURNING voucher_id INTO voucher_id_value;
        manifest := jsonb_set(manifest,ARRAY['vouchers',row_data.code],to_jsonb(voucher_id_value));
    END LOOP;

    FOR row_data IN SELECT * FROM (VALUES
        ('O01','P19','PAYMENT_PENDING','PENDING',NULL,0,'PLATFORM',0),
        ('O02','P20','PAYMENT_PENDING','FAILED',NULL,0,'PLATFORM',0),
        ('O03','P21','PAID_HELD','HELD','TESTSELLER20',20000,'SELLER',-1),
        ('O04','P22','SHIPPED','HELD','TEST50K',50000,'PLATFORM',-2),
        ('O05','P23','COMPLETED','RELEASED','TESTUSEDUP',40000,'PLATFORM',-7),
        ('O06','P24','CANCELLED','FAILED',NULL,0,'PLATFORM',-3)
    ) AS fixtures(code,product_code,order_status,payment_status,voucher_code,discount,sponsor,age_days)
    LOOP
        product_id_value := (manifest -> 'products' ->> row_data.product_code)::BIGINT;
        SELECT listed_price INTO subtotal_value FROM products WHERE product_id = product_id_value;
        seed_discount_amount := row_data.discount;
        proceeds_value := subtotal_value - CASE WHEN row_data.sponsor = 'SELLER' THEN seed_discount_amount ELSE 0 END;
        order_created := seed_now + (row_data.age_days || ' days')::INTERVAL;
        order_due := order_created + INTERVAL '1 hour';

        INSERT INTO orders (checkout_group_id,buyer_id,seller_id,source_address_id,
                            shipping_recipient_name,shipping_phone_number,shipping_province,shipping_district,
                            shipping_ward,shipping_detail_address,subtotal,shipping_fee,buyer_system_fee,
                            seller_system_fee,seller_proceeds,voucher_discount_amount,shipping_discount_amount,
                            sponsor_type,total_amount,status,payment_due_at,created_at,completed_at,cancelled_at,cancellation_reason)
        SELECT gen_random_uuid(),buyer_id,seller_id,buyer_address_id,
               a.recipient_name,a.phone_number,a.province,a.district,a.ward,a.detail_address,
               subtotal_value,30000,0,0,proceeds_value,seed_discount_amount,0,row_data.sponsor,
               subtotal_value + 30000 - seed_discount_amount,row_data.order_status,order_due,order_created,
               CASE WHEN row_data.order_status = 'COMPLETED' THEN order_created + INTERVAL '3 days' ELSE NULL END,
               CASE WHEN row_data.order_status = 'CANCELLED' THEN order_due ELSE NULL END,
               CASE WHEN row_data.order_status = 'CANCELLED' THEN '[TEST] Hết hạn thanh toán; hàng đã được nhả' ELSE NULL END
        FROM addresses a WHERE a.address_id = buyer_address_id RETURNING order_id INTO order_id_value;
        manifest := jsonb_set(manifest,ARRAY['orders',row_data.code],to_jsonb(order_id_value));

        INSERT INTO order_items (order_id,product_id,product_title,quantity,listed_price,agreed_price,
                                 buyer_system_fee,seller_system_fee,buyer_line_total,seller_line_proceeds,fee_policy_id,pricing_source)
        SELECT order_id_value,p.product_id,p.title,1,subtotal_value,subtotal_value,0,0,subtotal_value,subtotal_value,policy_id,'LIST_PRICE'
        FROM products p WHERE p.product_id = product_id_value;
        IF row_data.order_status = 'PAYMENT_PENDING' THEN
            UPDATE products SET status = 'RESERVED',reserved_order_id = order_id_value,reserved_until = order_due,updated_at = seed_now
            WHERE product_id = product_id_value;
        END IF;

        INSERT INTO payments (order_id,amount,payment_method,status,transaction_code,created_at,paid_at,held_at,released_at,failure_reason)
        VALUES (order_id_value,subtotal_value + 30000 - seed_discount_amount,'BANK_TRANSFER_MOCK',row_data.payment_status,
                CASE WHEN row_data.payment_status IN ('HELD','RELEASED') THEN 'TEST_MOCK_' || row_data.code ELSE NULL END,
                order_created,
                CASE WHEN row_data.payment_status IN ('HELD','RELEASED') THEN order_created + INTERVAL '5 minutes' ELSE NULL END,
                CASE WHEN row_data.payment_status IN ('HELD','RELEASED') THEN order_created + INTERVAL '5 minutes' ELSE NULL END,
                CASE WHEN row_data.payment_status = 'RELEASED' THEN order_created + INTERVAL '3 days' ELSE NULL END,
                CASE WHEN row_data.payment_status = 'FAILED' THEN '[TEST] Thanh toán lỗi/hết hạn giả lập' ELSE NULL END);
        IF row_data.voucher_code IS NOT NULL THEN
            INSERT INTO order_vouchers (order_id,voucher_id,voucher_code_snapshot,voucher_type_snapshot,sponsor_type_snapshot,discount_amount,applied_at)
            SELECT order_id_value,v.voucher_id,v.code,v.voucher_type,v.sponsor_type,seed_discount_amount,order_created
            FROM vouchers v WHERE v.voucher_id = (manifest -> 'vouchers' ->> row_data.voucher_code)::BIGINT;
        END IF;
        IF row_data.order_status = 'PAYMENT_PENDING' THEN
            INSERT INTO notifications (user_id,type,title,content,reference_type,reference_id)
            VALUES (buyer_id,'ORDER_PAYMENT_PENDING','[TEST] Đơn hàng chưa thanh toán',
                    'Đơn ' || row_data.code || ' đang chờ thanh toán. Tiếp tục trong thời hạn một giờ.','ORDER',order_id_value);
        END IF;
    END LOOP;

    manifest := manifest || jsonb_build_object('seed',seed_request,'accounts',jsonb_build_object(
        'admin',admin_id,'buyer',buyer_id,'seller',seller_id,'technician',technician_id),'buyer_address',buyer_address_id);
    INSERT INTO audit_logs (user_id,action,entity_type,new_values,request_id)
    VALUES (admin_id,'DEV_SEED_CREATED','DevelopmentSeed',manifest,seed_request);
    RAISE NOTICE 'Created 4 accounts, 6 addresses, 24 products, 8 vouchers and 6 orders with mock payments. KTV uses BUYER by owner decision.';
END;
$seed$;

-- Account/result summary; no password hash or Cloudinary credentials displayed.
SELECT u.email,u.status,string_agg(r.role_name,', ' ORDER BY r.role_name) AS roles
FROM users u JOIN user_roles ur USING (user_id) JOIN roles r USING (role_id)
WHERE u.email IN ('admin@gmail.com','mua@gmail.com','ban@gmail.com','ktv@gmail.com') GROUP BY u.user_id ORDER BY u.email;
SELECT p.product_id,p.title,p.status,p.requires_buyer_ekyc FROM products p
WHERE p.product_id IN (SELECT value::TEXT::BIGINT FROM audit_logs a,
    jsonb_each(a.new_values -> 'products') WHERE a.action = 'DEV_SEED_CREATED' AND a.request_id = 'og-shop-development-test-v1')
ORDER BY p.product_id;
SELECT o.order_id,o.status,o.total_amount,o.payment_due_at FROM orders o
WHERE o.order_id IN (SELECT value::TEXT::BIGINT FROM audit_logs a,
    jsonb_each(a.new_values -> 'orders') WHERE a.action = 'DEV_SEED_CREATED' AND a.request_id = 'og-shop-development-test-v1')
ORDER BY o.order_id;
COMMIT;
