# Kế hoạch Tích hợp Cổng thanh toán VNPAY Sandbox vào O.G-Shop (Phiên Bản Toàn Diện & Chuẩn Hóa Kiến Trúc Chuyên Sâu)

---

## 1. Hiện trạng Repo (Current Repository State & Baseline Verification)

Đối chiếu trực tiếp mã nguồn Java, migration Flyway (`V1` đến `V11`), cấu hình `application.yml`, và các test suite hiện hữu trong repository. Kết quả kiểm tra kỹ thuật Bước 0 được ghi nhận minh bạch:

### 1.1. Bảng Kết Quả Xác Minh Kỹ Thuật Bước 0 (Ground Truth Baseline)

| Hạng mục kiểm tra | Kết quả đối chiếu | File & Dòng tham chiếu | Ghi chú kỹ thuật |
| :--- | :---: | :--- | :--- |
| **`ck_payments_status`** | Có trong V1 | [V1__initial_schema.sql](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/resources/db/migration/V1__initial_schema.sql#L422) | Chỉ chấp nhận: `'PENDING', 'PAID', 'HELD', 'RELEASED', 'REFUND_PENDING', 'REFUNDED', 'FAILED'`. Chưa có `UNDER_REVIEW`. |
| **`ck_payments_state_fields`** | Có trong V1 | [V1__initial_schema.sql](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/resources/db/migration/V1__initial_schema.sql#L424-L438) | Ràng buộc chặt các trường `paid_at`, `held_at`, `released_at`, `refunded_at`, `failure_reason` cho từng trạng thái. Chưa có nhánh cho `UNDER_REVIEW`. Nhánh `REFUND_PENDING` hiện bắt buộc `paid_at IS NOT NULL AND held_at IS NOT NULL`. |
| **Cột của bảng `payments`** | Đã xác minh | [V1__initial_schema.sql](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/resources/db/migration/V1__initial_schema.sql#L406-L413) | `paid_at TIMESTAMPTZ`, `held_at TIMESTAMPTZ`, `failure_reason VARCHAR(500)`. |
| **Ràng buộc duy nhất `order_id`** | Khai báo inline | [V1__initial_schema.sql](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/resources/db/migration/V1__initial_schema.sql#L400) | `order_id BIGINT NOT NULL UNIQUE` (không có tên constraint tường minh). |
| **Method chuyển trạng thái `OrderEntity`** | Còn thiếu | [OrderEntity.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/commerce/infrastructure/persistence/OrderEntity.java#L182-L209) | Đã có: `markPaidHeld` (chỉ từ `PAYMENT_PENDING`), `confirmBySeller` (từ `PAID_HELD`), `cancel` (chỉ cho phép từ `PAYMENT_PENDING` và `PAID_HELD`). **Chưa có:** `markUnderReview`, `approveFromReview` (`UNDER_REVIEW -> PAID_HELD`), và `cancel` chưa hỗ trợ hủy từ `UNDER_REVIEW`. Hằng số `STATUS_UNDER_REVIEW` chưa có. |
| **Method chuyển trạng thái `PaymentEntity`** | Còn thiếu | [PaymentEntity.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentEntity.java#L98-L126) | Đã có: `markHeld` (gán `status=HELD, paidAt=now, heldAt=now`), `markFailed`, `markRefunded`. **Chưa có:** `markUnderReview`, `approveFromReview`, `markRefundPending`. Hằng số `STATUS_UNDER_REVIEW` chưa có. |
| **`SecurityConfig` matcher & `denyAll()`** | Đã xác minh | [SecurityConfig.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/shared/config/SecurityConfig.java#L46-L72) | Có `.anyRequest().denyAll()` ở dòng 69. `GET /api/v1/payments/vnpay-ipn` được `permitAll()` (dòng 58). Các endpoint `/api/v1/payments/**` yêu cầu `authenticated()` (dòng 67). **Chưa có matcher cho endpoint admin duyệt review** (`/api/v1/admin/payments/review-decision`); nếu gọi sẽ bị `denyAll()` chặn 403. |
| **JWT Role mapping trong Security** | Đã xác minh | [SecurityConfig.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/shared/config/SecurityConfig.java#L103-L110) | `JwtGrantedAuthoritiesConverter`: đọc claim `"roles"` (`setAuthoritiesClaimName("roles")`) và **tự động thêm tiền tố `"ROLE_"`** (`setAuthorityPrefix("ROLE_")`). Do đó, role `"ADMIN"` chuyển thành authority `"ROLE_ADMIN"`, dùng được với `hasAnyRole("ADMIN", "KTV")` hoặc `hasAnyAuthority("ROLE_ADMIN", "ROLE_KTV")`. |
| **Cách Controller lấy tham số IPN** | Đang dùng `@RequestParam Map` | [PaymentController.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentController.java#L72) | Nhận `@RequestParam Map<String, String> allParams`. Spring MVC tự động giải mã URL decoding khiến map này không giữ nguyên dạng thô; đồng thời không kiểm soát được lỗi tham số trùng tên hoặc sai kiểu chữ. |
| **Cách `VnPayService.verifyCallback` ký** | Nhận `Map<String, String>` | [VnPayService.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/application/VnPayService.java#L24-L57) | Nhận `Map<String, String>`. Loại bỏ `vnp_SecureHash` và `vnp_SecureHashType`, sắp xếp key A-Z, rồi băm chuỗi nối `key=URLEncoder.encode(value, StandardCharsets.US_ASCII)`. **Không kiểm tra tiền tố `vnp_`** (lấy mọi key trong map). Băm từ giá trị đã giải mã rồi mã hóa lại bằng `US_ASCII`. |
| **Phiên bản Spring Boot & Container** | Đã xác minh | [pom.xml](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/pom.xml#L10) | Spring Boot `3.5.16` (sử dụng Embedded Apache Tomcat 10.1.x). |
| **`server.max-http-request-header-size`** | Chưa cấu hình trong repo | [application.yml](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/resources/application.yml#L50-L55) | Chưa khai báo thuộc tính này; Spring Boot / Tomcat mặc định là **8192 bytes (8KB)**. |
| **Cách container giải mã `getQueryString()`** | 🧪 CHƯA XÁC MINH | Runtime / Container | Tomcat giữ raw bytes của query line và giải mã khi gọi `getQueryString()`. Cần kiểm chứng thực tế xem byte ngoài ASCII được giải mã theo UTF-8 hay ISO-8859-1. |
| **Cận trên D của phần ngoài query** | CHƯA XÁC MINH | Runtime / Môi trường mạng | "Request line ngoài query + toàn bộ headers (Cookie, Authorization, Ngrok headers...)" phụ thuộc vào client, proxy, WAF, tunnel nên **không thể xác định tĩnh một hằng số D chắc chắn**. |

---

## 2. Nguyên tắc Thống nhất & Phân chia Phạm vi (Scope & Boundaries)

### 2.1. Ranh giới tính năng (Feature Scope Boundary)
- **Phạm vi của tính năng này (In Scope):**
  - Tích hợp cổng thanh toán VNPay Sandbox để đưa tiền từ tài khoản người mua vào tài khoản ký quỹ nội bộ của sàn O.G-Shop.
  - Sau khi IPN xác nhận thành công: `OrderEntity.status = PAID_HELD` và `PaymentEntity.status = HELD`.
  - Tiếp nhận, xử lý và lưu vết toàn bộ các tình huống callback IPN (thành công, thất bại, đơn hủy/quá hạn, khoản thu trùng lặp, nghi ngờ gian lận mã 07, callback mâu thuẫn, request sai cấu trúc).
  - Quy trình vận hành đối soát cho các khoản thu bất thường và quy trình đóng trạng thái `UNDER_REVIEW` của nhân viên thẩm định.
- **Phạm vi tách biệt cho Phase tương lai (Out of Scope / Future Phase):**
  - Luồng tích hợp webhook giao hàng từ GHN (`SHIPPED` -> `DELIVERED`).
  - Luồng tự động đếm ngược 3 ngày kiểm hàng/khiếu nại của người mua sau khi giao hàng thành công.
  - Luồng giải ngân tiền ký quỹ nội bộ từ sàn cho người bán (`PaymentEntity.status: HELD -> RELEASED` và `OrderEntity.status: DELIVERED -> COMPLETED`).
  - Giao diện Admin Review Queue trực quan trên Frontend (trong phase này sử dụng Endpoint nội bộ có phân quyền).

### 2.2. Các nguyên tắc kỹ thuật cốt lõi
1. **VNPay là Gateway, O.G-Shop là Escrow:** VNPay chỉ xác nhận đã trừ tiền thẻ người mua (`vnp_ResponseCode = "00"` và `vnp_TransactionStatus = "00"`). Việc giữ tiền ký quỹ và bảo vệ giao dịch là do logic O.G-Shop nội bộ đảm nhiệm.
2. **IPN là nguồn chân lý duy nhất (Single Source of Truth):** Chỉ webhook IPN Server-to-Server mới được phép ghi vào DB để chuyển trạng thái Order sang `PAID_HELD` và Payment sang `HELD`.
3. **Return URL chỉ đọc (Read-Only):** Return URL chỉ xác minh chữ ký tham số, đối chiếu dữ liệu với DB và trả kết quả cho Frontend hiển thị; tuyệt đối không cập nhật trạng thái thanh toán.
4. **Không ghi đè kết quả Gateway đã ghi nhận:** Trạng thái kết quả cuối của `payment_attempts` là bất biến. Callback mâu thuẫn đến sau không được ghi đè trạng thái của attempt.
5. **Hành vi Retry của VNPay là giả định chưa kiểm chứng (🧪):** Số lần retry (10 lần), khoảng cách (5 phút), các mã kích hoạt retry (`01`, `04`, `97`, `99` hoặc timeout) đều được dẫn chiếu từ tài liệu VNPay nhưng gắn nhãn 🧪 cần kiểm chứng qua Sandbox/Merchant Portal. Hệ thống phải thiết kế an toàn và idempotent trong cả hai trường hợp (VNPay có retry hoặc không retry).
6. **Thứ tự khóa cố định triệt tiêu Deadlock:** Trong transaction nghiệp vụ:
   $$\mathbf{Order \longrightarrow Payment \longrightarrow PaymentAttempt \longrightarrow Receipt}$$
   *Lưu ý:* "Khóa Receipt" chỉ là thao tác `UPDATE` hàng receipt của chính request ở bước cuối transaction nghiệp vụ. Các thao tác ghi receipt trước đó chạy trong transaction độc lập đã commit, không giữ khóa nghiệp vụ.
7. **Chỉ dùng dữ liệu đã xác thực để tạo Event và Case:** Các cột `vnp_txn_ref`, `vnp_transaction_no`, `vnp_amount_text` trên bảng raw receipt là dữ liệu forensic **chưa tin cậy**. Bảng `payment_ipn_events`, `payment_reconciliation_cases` và `dedup_key` chỉ được tạo từ giá trị của callback đã qua kiểm tra cấu trúc, xác thực chữ ký và liên kết được attempt.
8. **Đơn vị độ dài thống nhất:** Mọi độ dài chuỗi query tính bằng **số Unicode Code Point** ở cả Java (`codePointCount`) lẫn PostgreSQL (`char_length`, encoding UTF8); cắt chuỗi bằng `offsetByCodePoints`. Tuyệt đối không dùng `String.length()` (vốn chỉ đếm UTF-16 code units).

---

## 3. Thiết kế Lưu Payment Attempt, Ràng buộc DB & Quản lý Trạng thái

### 3.1. Rà soát Schema & Cập Nhật Ràng Buộc Trạng Thái
Trong migration `V12__add_vnpay_attempts.sql`, ta cần cập nhật toàn diện cả 3 check constraint trên bảng `payments` và `orders`:
1. Mở rộng `ck_payments_method` cho `'VNPAY'`.
2. Mở rộng `ck_orders_status` bổ sung `'UNDER_REVIEW'`.
3. Mở rộng `ck_payments_status` bổ sung `'UNDER_REVIEW'`.
4. **Cập nhật `ck_payments_state_fields`:** Định nghĩa chính xác các trường trạng thái cho `UNDER_REVIEW`, đồng thời mở rộng `REFUND_PENDING` và `REFUNDED` để cho phép `held_at IS NULL` khi chuyển tiếp từ `UNDER_REVIEW` (sau quyết định `REJECTED_FRAUD`).

### 3.2. Migration `V12__add_vnpay_attempts.sql`
```sql
-- V12__add_vnpay_attempts.sql

-- 1. Mở rộng check constraint cho payment_method
ALTER TABLE payments DROP CONSTRAINT ck_payments_method;
ALTER TABLE payments ADD CONSTRAINT ck_payments_method 
    CHECK (payment_method IN ('COD_MOCK', 'BANK_TRANSFER_MOCK', 'E_WALLET_MOCK', 'VNPAY'));

-- 2. Bổ sung trạng thái UNDER_REVIEW cho orders
ALTER TABLE orders DROP CONSTRAINT ck_orders_status;
ALTER TABLE orders ADD CONSTRAINT ck_orders_status CHECK (status IN (
    'PAYMENT_PENDING', 'PAID_HELD', 'SELLER_CONFIRMED', 'SHIPPED', 'DELIVERED',
    'COMPLETED', 'CANCELLED', 'DISPUTED', 'REFUNDED', 'UNDER_REVIEW'
));

-- 3. Bổ sung trạng thái UNDER_REVIEW cho payments
ALTER TABLE payments DROP CONSTRAINT ck_payments_status;
ALTER TABLE payments ADD CONSTRAINT ck_payments_status CHECK (status IN (
    'PENDING', 'PAID', 'HELD', 'RELEASED', 'REFUND_PENDING', 'REFUNDED', 'FAILED', 'UNDER_REVIEW'
));

-- 4. Cập nhật ràng buộc toàn vẹn các trường trạng thái của payments (ck_payments_state_fields)
-- Định nghĩa rõ: UNDER_REVIEW có paid_at (tiền đã trừ) nhưng held_at NULL (chưa vào escrow).
-- Cho phép REFUND_PENDING và REFUNDED xuất phát từ UNDER_REVIEW (held_at IS NULL) hoặc từ HELD (held_at IS NOT NULL).
ALTER TABLE payments DROP CONSTRAINT ck_payments_state_fields;
ALTER TABLE payments ADD CONSTRAINT ck_payments_state_fields CHECK (
    (status = 'PENDING' AND paid_at IS NULL AND held_at IS NULL AND released_at IS NULL AND refunded_at IS NULL AND failure_reason IS NULL)
    OR
    (status = 'PAID' AND paid_at IS NOT NULL AND held_at IS NULL AND released_at IS NULL AND refunded_at IS NULL AND failure_reason IS NULL)
    OR
    (status = 'HELD' AND paid_at IS NOT NULL AND held_at IS NOT NULL AND held_at >= paid_at AND released_at IS NULL AND refunded_at IS NULL AND failure_reason IS NULL)
    OR
    (status = 'RELEASED' AND paid_at IS NOT NULL AND held_at IS NOT NULL AND held_at >= paid_at AND released_at IS NOT NULL AND released_at >= held_at AND refunded_at IS NULL AND failure_reason IS NULL)
    OR
    (status = 'UNDER_REVIEW' AND paid_at IS NOT NULL AND held_at IS NULL AND released_at IS NULL AND refunded_at IS NULL AND failure_reason IS NULL)
    OR
    (status = 'REFUND_PENDING' AND paid_at IS NOT NULL AND (held_at IS NULL OR held_at >= paid_at) AND released_at IS NULL AND refunded_at IS NULL AND refund_amount IS NOT NULL AND refund_reason IS NOT NULL AND failure_reason IS NULL)
    OR
    (status = 'REFUNDED' AND paid_at IS NOT NULL AND (held_at IS NULL OR held_at >= paid_at) AND released_at IS NULL AND refunded_at IS NOT NULL AND (held_at IS NULL OR refunded_at >= held_at) AND refund_amount IS NOT NULL AND refund_reason IS NOT NULL AND failure_reason IS NULL)
    OR
    (status = 'FAILED' AND paid_at IS NULL AND held_at IS NULL AND released_at IS NULL AND refunded_at IS NULL AND failure_reason IS NOT NULL)
);

-- 5. Thêm composite unique trên payments để hỗ trợ composite foreign key
ALTER TABLE payments ADD CONSTRAINT uq_payments_payment_order UNIQUE (payment_id, order_id);

-- 6. Tạo bảng lưu lịch sử các lần thử thanh toán (Attempts)
CREATE TABLE payment_attempts (
    attempt_id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id                BIGINT NOT NULL,
    payment_id              BIGINT NOT NULL,
    txn_ref                 VARCHAR(100) NOT NULL UNIQUE,
    amount                  NUMERIC(19,2) NOT NULL,
    currency                CHAR(3) NOT NULL DEFAULT 'VND',
    provider                VARCHAR(30) NOT NULL DEFAULT 'VNPAY',
    status                  VARCHAR(35) NOT NULL DEFAULT 'PENDING',
    vnp_amount_raw          BIGINT,
    vnp_amount              NUMERIC(19,2),
    vnp_transaction_no      VARCHAR(100),
    vnp_response_code       VARCHAR(10),
    vnp_transaction_status  VARCHAR(10),
    vnp_bank_code           VARCHAR(50),
    vnp_pay_date            VARCHAR(20),
    failure_reason          VARCHAR(500),
    ipn_count               INT NOT NULL DEFAULT 0,
    first_ipn_received_at   TIMESTAMPTZ,
    last_ipn_received_at    TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ,

    CONSTRAINT fk_attempt_payment_order FOREIGN KEY (payment_id, order_id) 
        REFERENCES payments (payment_id, order_id) ON DELETE RESTRICT,
    CONSTRAINT ck_attempt_amount CHECK (amount > 0),
    CONSTRAINT ck_attempt_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_attempt_status CHECK (status IN (
        'PENDING', 
        'SUCCESS', 
        'FAILED', 
        'SUPERSEDED', 
        'EXPIRED', 
        'SUCCESS_ON_EXPIRED_ORDER',
        'SUCCESS_DUPLICATE_CHARGE',
        'FLAGGED_SUSPICIOUS',
        'SUCCESS_PENDING_REVIEW'
    ))
);

CREATE INDEX ix_payment_attempts_order_id ON payment_attempts(order_id);
CREATE UNIQUE INDEX uq_payment_attempts_single_pending 
    ON payment_attempts(payment_id) WHERE status = 'PENDING';

-- 7. Bảng ghi nhận Raw HTTP Request tiếp nhận tại Controller (payment_ipn_raw_receipts)
CREATE TABLE payment_ipn_raw_receipts (
    receipt_id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    raw_query_params            TEXT NOT NULL,
    raw_query_original_length   INT NOT NULL,
    raw_query_truncated         BOOLEAN NOT NULL DEFAULT FALSE,
    raw_query_sanitized         BOOLEAN NOT NULL DEFAULT FALSE,
    raw_query_sha256            CHAR(64) NOT NULL,
    ip_address                  VARCHAR(50),
    received_at                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    vnp_txn_ref                 VARCHAR(100),       -- Dữ liệu forensic chưa tin cậy
    vnp_transaction_no          VARCHAR(100),       -- Dữ liệu forensic chưa tin cậy
    vnp_amount_text             TEXT,               -- Chuỗi thô nhận được, không ép kiểu số
    verification_status         VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
    processing_status           VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    processing_error            VARCHAR(200),

    CONSTRAINT ck_receipt_verification_status CHECK (verification_status IN (
        'UNVERIFIED', 'MALFORMED_REQUEST', 'SIGNATURE_INVALID', 'SIGNATURE_VALID_UNLINKED', 'LINKED'
    )),
    CONSTRAINT ck_receipt_processing_status CHECK (processing_status IN (
        'RECEIVED', 'PROCESSED', 'PROCESSING_FAILED', 'RETRYABLE_ERROR'
    )),
    -- Ràng buộc toàn vẹn kích thước và trạng thái query
    CONSTRAINT ck_receipt_char_length CHECK (char_length(raw_query_params) <= 8192),
    CONSTRAINT ck_receipt_length_consistency CHECK (
        (NOT raw_query_truncated AND char_length(raw_query_params) = raw_query_original_length)
        OR
        (raw_query_truncated AND char_length(raw_query_params) < raw_query_original_length)
    ),
    CONSTRAINT ck_receipt_truncation_status CHECK (
        NOT (raw_query_truncated OR raw_query_sanitized) 
        OR verification_status IN ('UNVERIFIED', 'MALFORMED_REQUEST')
    ),
    CONSTRAINT ck_receipt_verified_integrity CHECK (
        verification_status NOT IN ('SIGNATURE_VALID_UNLINKED', 'LINKED')
        OR (NOT raw_query_truncated AND NOT raw_query_sanitized AND raw_query_params ~ '^[ -~]*$')
    )
);

CREATE INDEX ix_raw_receipts_txn_ref ON payment_ipn_raw_receipts(vnp_txn_ref);
CREATE INDEX ix_raw_receipts_status ON payment_ipn_raw_receipts(processing_status, received_at);

-- 8. Bảng lưu sự kiện IPN đã qua kiểm tra cấu trúc, xác thực chữ ký và liên kết attempt
CREATE TABLE payment_ipn_events (
    event_id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    receipt_id              BIGINT NOT NULL UNIQUE REFERENCES payment_ipn_raw_receipts(receipt_id) ON DELETE RESTRICT,
    attempt_id              BIGINT NOT NULL REFERENCES payment_attempts(attempt_id) ON DELETE RESTRICT,
    order_id                BIGINT NOT NULL,
    payment_id              BIGINT NOT NULL,
    txn_ref                 VARCHAR(100) NOT NULL,
    vnp_transaction_no      VARCHAR(100),
    vnp_response_code       VARCHAR(10),
    vnp_transaction_status  VARCHAR(10),
    vnp_curr_code           VARCHAR(10),
    vnp_amount_text         TEXT,
    vnp_amount_raw          BIGINT,
    vnp_amount              NUMERIC(19,2),
    vnp_bank_code           VARCHAR(50),
    vnp_bank_tran_no        VARCHAR(100),
    vnp_card_type           VARCHAR(20),
    vnp_pay_date            VARCHAR(20),
    ip_address              VARCHAR(50),
    processing_outcome      VARCHAR(50) NOT NULL,
    received_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_ipn_events_amount CHECK (
        processing_outcome IN ('REJECTED_AMOUNT_MISMATCH') 
        OR (vnp_amount_raw IS NOT NULL AND vnp_amount IS NOT NULL)
    ),
    CONSTRAINT ck_ipn_events_outcome CHECK (processing_outcome IN (
        'APPLIED_SUCCESS', 'APPLIED_FAILED', 'DUPLICATE_ACKNOWLEDGED', 'CONFLICT_IGNORED',
        'CONFLICT_CHARGE_REQUIRES_RECONCILIATION', 'FLAGGED_SUSPICIOUS', 'APPLIED_EXPIRED_ORDER',
        'APPLIED_DUPLICATE_CHARGE', 'APPLIED_PENDING_REVIEW', 'REJECTED_AMOUNT_MISMATCH', 'REJECTED_CURRENCY_MISMATCH'
    ))
);

CREATE INDEX ix_payment_ipn_events_attempt_id ON payment_ipn_events(attempt_id);
CREATE INDEX ix_payment_ipn_events_txn_ref ON payment_ipn_events(txn_ref);

-- 9. Bảng lưu vết thẩm định UNDER_REVIEW (Audit Log Vận Hành)
CREATE TABLE payment_review_audits (
    review_audit_id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id                BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE RESTRICT,
    payment_id              BIGINT NOT NULL REFERENCES payments(payment_id) ON DELETE RESTRICT,
    attempt_id              BIGINT REFERENCES payment_attempts(attempt_id) ON DELETE RESTRICT,
    decided_by              VARCHAR(50) NOT NULL,
    decision                VARCHAR(30) NOT NULL,
    reason_note             TEXT NOT NULL,
    vnp_verification_ref    VARCHAR(100),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_review_decision CHECK (decision IN ('APPROVED_SAFE', 'REJECTED_FRAUD'))
);

CREATE INDEX ix_payment_review_audits_order_id ON payment_review_audits(order_id);
CREATE INDEX ix_payment_review_audits_payment_id ON payment_review_audits(payment_id);

-- 10. Hàng đợi Đối soát Bền vững (payment_reconciliation_cases)
CREATE TABLE payment_reconciliation_cases (
    case_id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id                BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE RESTRICT,
    payment_id              BIGINT NOT NULL REFERENCES payments(payment_id) ON DELETE RESTRICT,
    attempt_id              BIGINT REFERENCES payment_attempts(attempt_id) ON DELETE RESTRICT,
    case_type               VARCHAR(40) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    source_type             VARCHAR(30) NOT NULL,
    event_id                BIGINT REFERENCES payment_ipn_events(event_id) ON DELETE RESTRICT,
    review_audit_id         BIGINT REFERENCES payment_review_audits(review_audit_id) ON DELETE RESTRICT,
    vnp_transaction_no      VARCHAR(100),
    dedup_key               VARCHAR(150) NOT NULL UNIQUE,
    assigned_to             VARCHAR(50),
    resolution_note         TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at             TIMESTAMPTZ,

    CONSTRAINT ck_rec_case_type CHECK (case_type IN (
        'EXPIRED_ORDER', 'DUPLICATE_CHARGE', 'LATE_SUCCESS_ON_FAILED', 'MISMATCH_CHARGE', 'REFUND_PENDING'
    )),
    CONSTRAINT ck_rec_source_type CHECK (source_type IN ('IPN_EVENT', 'REVIEW_DECISION')),
    CONSTRAINT ck_rec_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_rec_source_fk CHECK (
        (source_type = 'IPN_EVENT' AND event_id IS NOT NULL AND review_audit_id IS NULL)
        OR
        (source_type = 'REVIEW_DECISION' AND review_audit_id IS NOT NULL AND event_id IS NULL)
    ),
    CONSTRAINT ck_rec_type_source_combination CHECK (
        (case_type = 'REFUND_PENDING' AND source_type = 'REVIEW_DECISION')
        OR
        (case_type IN ('EXPIRED_ORDER', 'DUPLICATE_CHARGE', 'LATE_SUCCESS_ON_FAILED', 'MISMATCH_CHARGE') AND source_type = 'IPN_EVENT')
    )
);

CREATE INDEX ix_rec_cases_status ON payment_reconciliation_cases(status, case_type);

-- 11. Bảng Outbox Cảnh báo Bất thường (payment_alert_requests)
CREATE TABLE payment_alert_requests (
    alert_id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    case_id                 BIGINT NOT NULL UNIQUE REFERENCES payment_reconciliation_cases(case_id) ON DELETE RESTRICT,
    alert_type              VARCHAR(50) NOT NULL,
    severity                VARCHAR(20) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count           INT NOT NULL DEFAULT 0,
    last_error              TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at                 TIMESTAMPTZ,

    CONSTRAINT ck_alert_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX ix_alert_requests_pending ON payment_alert_requests(status, created_at);
```

### 3.3. Phân Định Rõ Ràng Các Trạng Thái Của PaymentAttempt
- **Trạng thái chuyển tiếp (Transitional States):**
  - `PENDING`: Phiên đang chờ người mua thao tác trên cổng VNPay (tối đa 1 attempt PENDING cho mỗi payment nhờ partial unique index).
  - `SUPERSEDED`: Attempt đã bị thay thế bởi attempt mới hơn khi khách bấm "Thanh toán lại", nhưng phiên VNPay cũ vẫn có thể hoàn tất nếu khách đã mở sẵn tab thanh toán.
- **Trạng thái kết quả cuối (Terminal / Settled States - Bất biến về kết quả Gateway):**
  - `SUCCESS`: Giao dịch thành công đã được áp dụng vào đơn hàng (`Order: PAID_HELD`, `Payment: HELD`).
  - `FAILED`: Giao dịch thất bại (khách hủy, thẻ lỗi, số dư không đủ). *Bất biến: Không bao giờ bị ghi đè thành SUCCESS.*
  - `SUCCESS_DUPLICATE_CHARGE`: Giao dịch thành công nhưng đơn hàng đã có khoản thu trước đó (khoản thu trùng lặp).
  - `SUCCESS_ON_EXPIRED_ORDER`: Giao dịch thành công nhưng đơn hàng đã bị hủy hoặc quá hạn 1 giờ.
  - `FLAGGED_SUSPICIOUS`: Giao dịch trừ tiền thành công nhưng ngân hàng nghi ngờ gian lận (mã 07).
  - `SUCCESS_PENDING_REVIEW`: Giao dịch thành công đến khi đơn hàng đang bị phong tỏa rà soát (`UNDER_REVIEW`).
- **Quy tắc bất biến khi duyệt rà soát (Review Invariance):** Khi Admin/KTV duyệt `UNDER_REVIEW` (thành `APPROVED_SAFE` hoặc `REJECTED_FRAUD`), **`payment_attempts.status` giữ nguyên không đổi** (vẫn là `FLAGGED_SUSPICIOUS` hoặc `SUCCESS_PENDING_REVIEW`). Quyết định vận hành được ghi nhận vào `payment_review_audits`; chỉ `OrderEntity` và `PaymentEntity` đổi trạng thái.

---

## 4. Xử lý IPN Webhook An toàn, Nhất quán & Thứ tự Khóa Tránh Deadlock

### 4.1. Thành Phần Parse Query Chung (`VnPayQueryParser`)
Cả `GET /api/v1/payments/vnpay-ipn` và `GET /api/v1/payments/vnpay-verify` đều sử dụng **duy nhất một thành phần phân tích query** độc lập:
- **CẤM** gọi `request.getParameter*` và `@RequestParam Map` trên controller.
- Controller đọc `request.getQueryString()` **đúng một lần**.
- **Thứ tự xử lý chuẩn mực (Receipt luôn được INSERT trước mọi quyết định từ chối):**
  1. Đọc `rawQueryString = request.getQueryString()`. Nếu `null`, coi là chuỗi rỗng `""`.
  2. Làm sạch riêng surrogate lẻ: Duyệt qua chuỗi, thay từng lone surrogate bằng ký tự `?` (thay 1-1, số code point không đổi). Nếu có thay thế, đánh dấu cờ `raw_query_sanitized = true`.
  3. Tính `raw_query_original_length = rawQueryString.codePointCount(0, rawQueryString.length())`. Nếu vượt ngưỡng `app.vnpay.ipn.raw-query-max-chars` (mặc định 4096 🧪), cắt chuỗi tại vị trí code point thứ 4096 bằng `offsetByCodePoints`, đánh dấu `raw_query_truncated = true`. Tính `raw_query_sha256` trên UTF-8 bytes của chuỗi đầy đủ sau làm sạch.
  4. **INSERT Raw Receipt (Transaction Độc Lập `REQUIRES_NEW`):** Lưu ngay bản ghi vào `payment_ipn_raw_receipts` với trạng thái `(verification_status = 'UNVERIFIED', processing_status = 'RECEIVED')`, 3 cột `vnp_*` tạm để `NULL`.
  5. **Kiểm tra cấu trúc và tính hợp lệ theo thứ tự ưu tiên:**
     - `NON_ASCII_RAW_QUERY`: Nếu chuỗi query gốc chứa ký tự ngoài khoảng ASCII in được (U+0020 đến U+007E, bao gồm ký tự điều khiển, ký tự có dấu chưa percent-encode, NUL, surrogate lẻ).
     - `RAW_QUERY_TOO_LARGE`: Nếu `raw_query_truncated == true`.
     - Phân tích từng cặp `key=value` (tách theo `&`, chỉ tách tại dấu `=` đầu tiên; cặp không có `=` có giá trị rỗng; bỏ qua `&&`).
     - `EMPTY_PARAM_NAME`: Nếu tên tham số rỗng.
     - `INVALID_PERCENT_ENCODING`: Nếu `%` sai cú pháp (`%zz`, `%4` cụt) hoặc giải mã ra byte UTF-8 không hợp lệ (`%C3%28`).
     - `DUPLICATE_VNP_PARAM:<tên>`: Nếu có từ 2 tham số có tên khớp `vnp_*` sau khi giải mã và so sánh không phân biệt hoa/thường (ví dụ `vnp_Amount` và `vnp_amount`, hoặc `vnp_TxnRef` và `vnp%5FTxnRef`). Tên trong mã lỗi được chuẩn hóa chữ thường, ký tự ngoài `[a-z0-9_]` thay bằng `?`, tối đa 64 ký tự.
     - `NONCANONICAL_VNP_PARAM_CASE:<tên>`: Tham số đứng riêng nhưng sai kiểu chữ: Tiền tố khớp `vnp_` không phân biệt hoa/thường nhưng không phải đúng `vnp_` chữ thường (ví dụ `VNP_Amount`), hoặc tên khớp danh sách `KNOWN_VNP_PARAM_NAMES` nhưng sai từng ký tự (ví dụ `vnp_amount` thay vì `vnp_Amount`).
  6. **Nếu vi phạm cấu trúc:** UPDATE receipt sang `(verification_status = 'MALFORMED_REQUEST', processing_status = 'PROCESSED', processing_error = '<MÃ_LỖI>')` trong transaction độc lập ngắn; dừng xử lý, không đọc DB nghiệp vụ, trả mã `97` ("Invalid Checksum" 🧪).
  7. **Nếu cấu trúc hợp lệ:**
     - Điền 3 cột tra cứu forensic: `vnp_txn_ref`, `vnp_transaction_no`, `vnp_amount_text` (nếu giá trị giải mã chứa NUL `%00` hoặc vượt độ dài cột thì để `NULL`). UPDATE receipt trong transaction ngắn.
     - Chuyển `Map<String, String>` chuẩn hóa cho bộ xác thực chữ ký và service nghiệp vụ.

### 4.2. Quy tắc Khóa và Thứ tự Kiểm Tra Trong Transaction Nghiệp Vụ
Sau khi xác thực chữ ký thành công (nếu chữ ký sai -> UPDATE receipt sang `SIGNATURE_INVALID`, `PROCESSED`, trả `97`):
Trích xuất `orderId` từ `txnRef` (cấu trúc `OG_{orderId}_{timestamp}`). Mở transaction nghiệp vụ `@Transactional`:

1. **Khóa bi quan theo thứ tự:**
   `OrderEntity` -> `PaymentEntity` -> `PaymentAttemptEntity`.
   Nếu không tìm thấy attempt tương ứng: Rollback, UPDATE receipt sang `SIGNATURE_VALID_UNLINKED`, `PROCESSED`, trả `01` ("Order not found").
2. **Thứ tự kiểm tra logic nghiệp vụ (ĐÃ CHỐT CỨNG):**
   - **Bước 1: So sánh Amount & Currency (Ưu tiên số 1):**
     + So sánh `attempt.getAmount().multiply(100).longValueExact()` với `parseLong(vnp_Amount)`.
     + So sánh currency: Căn cứ chính là currency lưu trong attempt (`"VND"`). Nếu callback có `vnp_CurrCode` mà khác attempt -> mismatch. Nếu callback thiếu `vnp_CurrCode` -> dùng mặc định của attempt.
     + **Nguyên tắc ghi nhận (Không phải lỗi rollback):** Nếu sai amount hoặc sai currency:
       - Trong CÙNG transaction nghiệp vụ: Ghi event `payment_ipn_events` với outcome `REJECTED_AMOUNT_MISMATCH` (nếu sai amount) hoặc `REJECTED_CURRENCY_MISMATCH` (nếu sai currency; ưu tiên amount nếu sai cả hai). `vnp_amount_raw` và `vnp_amount` được phép NULL nếu không parse được, lưu `vnp_amount_text`.
       - Tăng `attempt.ipn_count`, cập nhật `first/last_ipn_received_at`.
       - Nếu callback báo đã thu tiền hoặc nghi ngờ (`00/00` hoặc `07`): Tạo 1 case `MISMATCH_CHARGE` trên `payment_reconciliation_cases` và 1 alert request trên `payment_alert_requests` (khử trùng qua `dedup_key`). Nếu callback báo THẤT BẠI mà sai amount/currency: chỉ ghi event `REJECTED_*`, KHÔNG tạo case hay alert request.
       - **Bảo toàn trạng thái nghiệp vụ:** Tuyệt đối KHÔNG đổi trạng thái hay trường bằng chứng của Order, Payment và Attempt (Order/Payment giữ nguyên, Attempt giữ nguyên status hiện tại). Tuyệt đối KHÔNG gán `FLAGGED_SUSPICIOUS`, KHÔNG chuyển Order sang `UNDER_REVIEW`, và KHÔNG trả mã `00` cho nhánh mismatch (kể cả khi callback mang mã `07` nhưng sai amount/currency). Quyết định `FLAGGED_SUSPICIOUS` và chuyển `UNDER_REVIEW` (kèm trả mã `00`) CHỈ áp dụng ở Nhánh B khi callback báo mã 07 và amount/currency hoàn toàn hợp lệ.
       - UPDATE receipt sang `(verification_status = 'LINKED', processing_status = 'PROCESSED')`.
       - **COMMIT transaction** (Tuyệt đối không throw exception để tránh rollback mất audit).
       - Controller ánh xạ mã phản hồi SAU KHI commit: Amount sai -> trả mã `04` ("Invalid Amount" 🧪); Currency sai -> trả mã `99` ("Unknown error" 🧪).
   - **Bước 2: Kiểm tra Tính Bất Biến Của Attempt (Đã Terminal):**
     + Nếu `attempt.status` đã thuộc các trạng thái kết quả cuối (`SUCCESS`, `FAILED`, `SUCCESS_DUPLICATE_CHARGE`, `SUCCESS_ON_EXPIRED_ORDER`, `FLAGGED_SUSPICIOUS`, `SUCCESS_PENDING_REVIEW`):
       - **Kịch bản callback lặp đúng giao dịch đã ghi nhận:** Trước các nhánh thành công muộn/mâu thuẫn, kiểm tra event của attempt. Nếu callback `00/00` có cùng `vnp_TransactionNo` với một callback thành công đã ghi nhận (hoặc cả hai cùng thiếu mã theo quy tắc `NO_TXN_NO`), ghi event `DUPLICATE_ACKNOWLEDGED`, tăng `ipn_count`, cập nhật receipt `LINKED/PROCESSED`, trả `02`; không tạo case/alert mới. Thứ tự này bảo đảm các lần lặp sau lần thành công muộn đầu tiên không tạo lại case.
       - **Callback thành công mới sau `FAILED`:** Nếu callback `00/00` có transaction number mới và `attempt.status = FAILED`, giữ nguyên trạng thái `FAILED`; ghi event `CONFLICT_CHARGE_REQUIRES_RECONCILIATION`, tạo case `LATE_SUCCESS_ON_FAILED` + alert request theo transaction number mới, tăng `ipn_count`, cập nhật receipt `LINKED/PROCESSED`, trả mã `02` (🧪). Mỗi transaction number mới là một khoản thu riêng và có case riêng.
       - **Cùng attempt đã có khoản thu thành công, callback lại báo khoản thu mới:** Nếu callback `00/00` có transaction number mới so với mọi giao dịch thành công đã ghi trong `payment_ipn_events` của attempt, giữ nguyên trạng thái và bằng chứng gốc của Order/Payment/Attempt; ghi event `CONFLICT_CHARGE_REQUIRES_RECONCILIATION`, tạo case `DUPLICATE_CHARGE` + alert request theo transaction number mới, chỉ tạo alert request nếu case mới, tăng `ipn_count`, cập nhật receipt `LINKED/PROCESSED`, trả `02`. Không gộp khoản thu mới vào case cũ.
       - **Kịch bản callback mâu thuẫn khác:** Giữ nguyên trạng thái, ghi event `CONFLICT_IGNORED`, tăng `ipn_count`, trả `02`. Nhánh này không áp dụng cho callback thành công có `vnp_TransactionNo` mới.
   - **Bước 3: Rẽ nhánh nghiệp vụ (Khi attempt chưa Terminal và Amount/Currency hợp lệ):**
     - **Nhánh A: VNPay Thành công (`00/00`):**
       + *A1. Đơn hàng ĐÃ CÓ KHOẢN THU TRƯỚC:* Khi `payment.status IN ('HELD', 'RELEASED', 'REFUND_PENDING', 'REFUNDED')` HOẶC có bất kỳ attempt nào khác của đơn hàng đã có `status IN ('SUCCESS', 'SUCCESS_PENDING_REVIEW', 'FLAGGED_SUSPICIOUS')`:
         `attempt.status = SUCCESS_DUPLICATE_CHARGE`. Ghi event `APPLIED_DUPLICATE_CHARGE`. Tạo case `DUPLICATE_CHARGE` + alert request. Trả `00`.
       + *A2. Đơn hàng đang `UNDER_REVIEW` (chưa có khoản thu nào khác):*
         `attempt.status = SUCCESS_PENDING_REVIEW`. Ghi event `APPLIED_PENDING_REVIEW`. Giữ nguyên `UNDER_REVIEW`. Trả `00`.
       + *A3. Đơn hàng ĐÃ HỦY HOẶC QUÁ HẠN 1H (`CANCELLED` hoặc `now > paymentDueAt`):*
         `attempt.status = SUCCESS_ON_EXPIRED_ORDER`. Ghi event `APPLIED_EXPIRED_ORDER`. Tạo case `EXPIRED_ORDER` + alert request. Trả `00`.
       + *A4. Thành công hợp lệ (Order đang `PAYMENT_PENDING`, chưa có khoản thu nào):*
         `attempt.status = SUCCESS`. Chuyển các attempt pending khác sang `SUPERSEDED`.
         `order.markPaidHeld(now)`. `payment.markHeld(vnp_TransactionNo, now)`.
         Ghi event `APPLIED_SUCCESS`. Trả `00`.
      - **Nhánh B: Giao dịch Nghi ngờ Gian lận (mã 07):**
        + *Tiền điều kiện bắt buộc:* Nhánh B chỉ được xét tới khi amount và currency hoàn toàn hợp lệ (đã vượt qua Bước 1). Nếu callback mang mã 07 nhưng sai amount hoặc currency, yêu cầu đã được xử lý dứt điểm tại Bước 1 (ghi event `REJECTED_AMOUNT_MISMATCH` hoặc `REJECTED_CURRENCY_MISMATCH`, tạo case `MISMATCH_CHARGE` + alert request, commit và trả mã `04` hoặc `99` 🧪), tuyệt đối không vào Nhánh B, không gán `FLAGGED_SUSPICIOUS`, không chuyển `UNDER_REVIEW` và không trả mã `00`.
        + *Guard điều kiện:* Nếu đơn hàng đã có khoản thu trước -> chuyển sang A1 (`SUCCESS_DUPLICATE_CHARGE`). Nếu đơn hàng đã hủy hoặc quá hạn -> chuyển sang A3 (`SUCCESS_ON_EXPIRED_ORDER`). Tuyệt đối không kéo ngược đơn hàng đã hoàn tất hoặc đã hủy sang `UNDER_REVIEW`.
        + *B1. Nghi ngờ hợp lệ khi Order đang `PAYMENT_PENDING`:* `attempt.status = FLAGGED_SUSPICIOUS`. Gọi `order.markUnderReview(now)` và `payment.markUnderReview(vnp_TransactionNo, now)` để chuyển cặp trạng thái sang `UNDER_REVIEW`. Ghi event `FLAGGED_SUSPICIOUS`. Trả `00`.
        + *B2. Order và Payment đã cùng ở `UNDER_REVIEW`:* Không gọi lại các method chuyển trạng thái; giữ nguyên trạng thái và dữ liệu review hiện tại, đặt attempt hiện tại thành `FLAGGED_SUSPICIOUS`, ghi event `FLAGGED_SUSPICIOUS`, tăng bộ đếm và cập nhật receipt trong transaction. Trả `00`. Nếu chỉ một trong Order/Payment là `UNDER_REVIEW`, coi là lỗi xác định/invariant vi phạm: rollback nghiệp vụ, cập nhật receipt độc lập sang `PROCESSING_FAILED`, trả `99` theo Mục 5.1/13; không tự sửa trạng thái.
     - **Nhánh C: VNPay Báo Thất Bại / Khách Hủy:**
       + *C1. Đơn hàng đã có khoản thu trước đó hoặc đang `UNDER_REVIEW`:*
         `attempt.status = FAILED`. Không đổi trạng thái Order/Payment. Ghi event `APPLIED_FAILED`. Trả mã `02` ("Transaction already processed" 🧪).
       + *C2. Thất bại thông thường khi Order đang `PAYMENT_PENDING`:*
         `attempt.status = FAILED`. Giữ nguyên Order `PAYMENT_PENDING` cho khách thanh toán lại nếu chưa quá hạn 1h. Ghi event `APPLIED_FAILED`. Trả mã `00` ("Confirm Success" 🧪).

---

## 5. Xử lý Giao dịch Bất thường, Khử Trùng Case & Chuẩn Hóa Mã IPN

### 5.1. Bảng Ánh Xạ Mã Phản Hồi IPN Duy Nhất (Single Source of Truth)
Bảng dưới đây là căn cứ duy nhất ánh xạ giữa kết quả xử lý và phản hồi cho VNPay. Tất cả các mục khác trong tài liệu đều tham chiếu bảng này:

| Tình huống / Outcome | Verification Status | Processing Status | Event Outcome ghi nhận | RspCode | Message | VNPay có thể gửi lại hay không? (🧪) |
| :--- | :--- | :--- | :--- | :---: | :--- | :---: |
| **Request sai cấu trúc** (Mục 4.1) | `MALFORMED_REQUEST` | `PROCESSED` | (Không có event) | `97` | Invalid Checksum | Có thể retry (🧪) |
| **Chữ ký không hợp lệ** | `SIGNATURE_INVALID` | `PROCESSED` | (Không có event) | `97` | Invalid Checksum | Có thể retry (🧪) |
| **TxnRef sai / Không tìm thấy attempt** | `SIGNATURE_VALID_UNLINKED` | `PROCESSED` | (Không có event) | `01` | Order not found | Có thể retry (🧪) |
| **Số tiền không khớp** | `LINKED` | `PROCESSED` | `REJECTED_AMOUNT_MISMATCH` | `04` | Invalid Amount | Có thể retry (🧪) |
| **Tiền tệ không khớp** | `LINKED` | `PROCESSED` | `REJECTED_CURRENCY_MISMATCH` | `99` | Unknown error | Có thể retry (🧪) |
| **Áp dụng thành công lần đầu** | `LINKED` | `PROCESSED` | `APPLIED_SUCCESS` | `00` | Confirm Success | Dừng retry (🧪) |
| **Khoản thu trùng lặp** | `LINKED` | `PROCESSED` | `APPLIED_DUPLICATE_CHARGE` | `00` | Confirm Success | Dừng retry (🧪) |
| **Đơn hủy / Quá hạn 1h thu tiền** | `LINKED` | `PROCESSED` | `APPLIED_EXPIRED_ORDER` | `00` | Confirm Success | Dừng retry (🧪) |
| **Nghi ngờ gian lận (mã 07)** | `LINKED` | `PROCESSED` | `FLAGGED_SUSPICIOUS` | `00` | Confirm Success | Dừng retry (🧪) |
| **Thành công khi đơn đang review** | `LINKED` | `PROCESSED` | `APPLIED_PENDING_REVIEW` | `00` | Confirm Success | Dừng retry (🧪) |
| **Thất bại thông thường (đơn pending)** | `LINKED` | `PROCESSED` | `APPLIED_FAILED` | `00` | Confirm Success | Dừng retry (🧪) |
| **Thất bại trên đơn đã có khoản thu** | `LINKED` | `PROCESSED` | `APPLIED_FAILED` | `02` | Transaction already processed | Dừng retry (🧪) |
| **Callback lặp Idempotent** | `LINKED` | `PROCESSED` | `DUPLICATE_ACKNOWLEDGED` | `02` | Transaction already processed | Dừng retry (🧪) |
| **Callback thành công cùng attempt, transaction number mới** | `LINKED` | `PROCESSED` | `CONFLICT_CHARGE_REQUIRES_RECONCILIATION` | `02` | Transaction already processed | Dừng retry (🧪) |
| **Callback mâu thuẫn trên attempt cuối** | `LINKED` | `PROCESSED` | `CONFLICT_IGNORED` | `02` | Transaction already processed | Dừng retry (🧪) |
| **Thành công mới sau FAILED hoặc khoản thu mới trên attempt đã thành công** | `LINKED` | `PROCESSED` | `CONFLICT_CHARGE_REQUIRES_RECONCILIATION` | `02` | Transaction already processed | Dừng retry (🧪) |
| **Lỗi tạm thời hệ thống (Timeout, Lock)** | `LINKED` | `RETRYABLE_ERROR` | (Không có event - rollback) | `99` | Unknown error | Có thể retry (🧪) |
| **Lỗi xác định khi chạy nghiệp vụ** | `LINKED` | `PROCESSING_FAILED` | (Không có event - rollback) | `99` | Unknown error | Có thể retry (🧪) |

> [!NOTE]
> **Đánh giá về việc ACK hay trả mã lỗi cho Amount/Currency Mismatch (🧪):**
> - *Amount Mismatch:* Hệ thống chọn **Phương án B (Trả mã `04`)** để giữ nguyên tín hiệu cảnh báo số tiền sai theo chuẩn VNPay. Nếu VNPay kích hoạt retry (🧪), các lần gọi lại đều được ghi nhận thành raw receipt và event `REJECTED_AMOUNT_MISMATCH` mới, trong khi hàng đợi đối soát và outbox cảnh báo được bảo vệ an toàn nhờ cơ chế khử trùng `dedup_key`.
> - *Currency Mismatch:* Hệ thống chọn **Phương án B (Trả mã `99`)** vì VNPay chưa công bố mã lỗi riêng cho currency mismatch; việc xác minh Sandbox sẽ chốt mã chính thức.
> - *Nguyên tắc bảo toàn trạng thái & cam kết transaction:* Cả hai trường hợp sai amount (`04` 🧪) và sai currency (`99` 🧪) đều ghi nhận event `REJECTED_*`, tăng `ipn_count`, tạo case `MISMATCH_CHARGE` + alert request (nếu callback báo đã thu tiền hoặc nghi ngờ `00/00` hoặc `07`), cập nhật receipt sang `(LINKED, PROCESSED)` và COMMIT transaction nghiệp vụ trước khi controller trả mã lỗi. Tuyệt đối không đổi trạng thái Order/Payment/Attempt, không dùng `FLAGGED_SUSPICIOUS`, không đưa vào `UNDER_REVIEW` và không trả mã `00`.

### 5.2. Cơ Chế Khử Trùng Case và Cảnh Báo Bền Vững
- **Nguyên tắc tạo Case:** Chỉ dữ liệu callback đã xác thực chữ ký và liên kết attempt mới được phép sinh case đối soát.
- **Quy tắc sinh `dedup_key` (NOT NULL UNIQUE):**
  - Case sinh từ IPN (`EXPIRED_ORDER`, `DUPLICATE_CHARGE`, `LATE_SUCCESS_ON_FAILED`, `MISMATCH_CHARGE`):
    `dedup_key = String.format("%s:%d:%s", caseType, attemptId, vnpTransactionNo != null ? vnpTransactionNo : "NO_TXN_NO");`
    *(Nếu VNPay không gửi mã giao dịch, dùng `NO_TXN_NO` 🧪; hệ quả là các callback cùng attempt thiếu mã sẽ được gom vào chung 1 case).*
  - Case sinh từ quyết định Admin (`REFUND_PENDING`):
    `dedup_key = String.format("REFUND_PENDING:%d", paymentId);`
- **Khử trùng Outbox Cảnh báo:** Bảng `payment_alert_requests` có ràng buộc `case_id UNIQUE`. Khi thực hiện `INSERT ... ON CONFLICT (dedup_key) DO NOTHING` trên `payment_reconciliation_cases`:
  - Nếu case được tạo mới thành công -> Tạo bản ghi alert request tương ứng ở trạng thái `PENDING`.
  - Nếu case bị trùng (do callback lặp) -> Không có dòng nào được tạo, do đó không sinh alert request thừa.
  - Log `[PAYMENT_CONFLICT_ALERT]` chỉ đóng vai trò kênh phụ best-effort, không dùng làm bằng chứng nghiệm thu.

---

## 6. Xử lý Mã 07 và Quy Trình Vận Hành Kết Thúc `UNDER_REVIEW`

### 6.1. Bảo Vệ Đơn Hàng Nghi Ngờ Khỏi Job Hết Hạn
Khi nhận mã 07, `OrderEntity.status = UNDER_REVIEW` và `PaymentEntity.status = UNDER_REVIEW`. Cronjob `expireOverdueOrders` tìm theo điều kiện `status = 'PAYMENT_PENDING'`, do đó đơn hàng nghi ngờ gian lận hoàn toàn không bị hủy nhầm và hàng tồn kho không bị nhả sai.

### 6.2. Quy Trình Vận Hành Kết Thúc `UNDER_REVIEW` (Resolution Runbook)
1. **Thẩm quyền quyết định:** Chỉ tài khoản có vai trò `ROLE_ADMIN` hoặc `ROLE_KTV` mới có quyền thẩm định.
2. **Hai kết quả quyết định dứt điểm:**
   - **`APPROVED_SAFE` (Xác nhận An toàn):**
     - Đã xác minh với VNPay / Ngân hàng phát hành, loại trừ gian lận.
     - Cập nhật trong 1 Transaction:
       + `order.approveFromReview(now)` -> chuyển sang `PAID_HELD`.
       + `payment.approveFromReview(now)` -> chuyển sang `HELD`, `heldAt = now`.
       + `attempt.status` **GIỮ NGUYÊN** (`FLAGGED_SUSPICIOUS` hoặc `SUCCESS_PENDING_REVIEW`).
       + Ghi bản ghi vào `payment_review_audits` (`decision = 'APPROVED_SAFE'`, `decided_by`, `reason_note`, `vnp_verification_ref`).
   - **`REJECTED_FRAUD` (Xác định Gian lận / Từ chối):**
     - Ngân hàng xác nhận thẻ đánh cắp hoặc quá hạn thẩm định không thể xác minh.
     - Cập nhật trong 1 Transaction:
       + `order.cancelFromReview(reason, now)` -> chuyển sang `CANCELLED`.
       + `payment.markRefundPending(amount, reason, now)` -> chuyển sang `REFUND_PENDING` (thỏa mãn `ck_payments_state_fields` vì cho phép `held_at IS NULL`).
       + `attempt.status` **GIỮ NGUYÊN**.
       + Gọi `catalogCommerceFacade.releaseProductReservation(...)` để hoàn lại hàng vào kho.
       + Ghi bản ghi vào `payment_review_audits` (`decision = 'REJECTED_FRAUD'`).
       + Tạo 1 case đối soát `REFUND_PENDING` trên `payment_reconciliation_cases` và 1 alert request tương ứng (cùng transaction).
3. **SLA Xử lý:** Đặt thời hạn xử lý tối đa **48 giờ làm việc**. Một scheduled job định kỳ quét các đơn `UNDER_REVIEW` quá 48h để phát sinh cảnh báo mức HIGH (nếu job này chưa triển khai trong phase này, đánh dấu ngoài phạm vi và theo dõi qua query kiểm toán).
4. **Vận hành có kiểm soát khi chưa có Admin UI:** Thao tác qua API nội bộ bảo mật `POST /api/v1/admin/payments/review-decision` yêu cầu token có `ROLE_ADMIN` hoặc `ROLE_KTV`. Tuyệt đối cấm chạy câu lệnh SQL `UPDATE` thủ công trên DB.

---

## 7. Vòng Đời Raw Receipt, Quản Lý Dữ Liệu IPN & Bảo Toàn Audit

> [!IMPORTANT]
> **Giới hạn ghi nhận của Raw Receipt:** Bảng `payment_ipn_raw_receipts` chỉ ghi nhận các request **đến được tầng controller**. Những request bị Reverse Proxy, WAF, Tunnel hoặc Container từ chối trước Controller (ví dụ vượt giới hạn header/request line gây lỗi 400 Bad Request) chắc chắn **không tạo raw receipt** trong cơ sở dữ liệu. Access log ở tầng proxy/server chỉ có thể là dấu vết truy vết nếu hệ thống hạ tầng mạng đã được cấu hình bật ghi log và chính sách lưu trữ (retention) log đủ lâu.

### 7.1. Bảng Trạng Thái Raw Receipt Khi Kết Thúc (Nguồn Sự Thật Duy Nhất)

| Tình huống kết thúc | verification_status | processing_status | processing_error | Cờ phụ (truncated / sanitized) | Event liên kết (qua `receipt_id`) | Mã trả về |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **Vừa nhận, đang xử lý** | `UNVERIFIED` | `RECEIVED` | `NULL` | Theo query ban đầu | Không | (Chưa trả lời) |
| **Vi phạm cấu trúc query** (Mục 4.1) | `MALFORMED_REQUEST` | `PROCESSED` | Mã cố định mục 4.1 | Có thể `TRUE` | Không | `97` (🧪) |
| **Chữ ký không hợp lệ** | `SIGNATURE_INVALID` | `PROCESSED` | `NULL` | `FALSE / FALSE` | Không | `97` |
| **Chữ ký đúng, TxnRef không liên kết được** | `SIGNATURE_VALID_UNLINKED` | `PROCESSED` | `NULL` | `FALSE / FALSE` | Không | `01` |
| **Liên kết thành công, commit nghiệp vụ** | `LINKED` | `PROCESSED` | `NULL` | `FALSE / FALSE` | Có | Theo Mục 5.1 |
| **Sai Amount hoặc Currency** | `LINKED` | `PROCESSED` | `NULL` | `FALSE / FALSE` | Có (`REJECTED_*`) | `04` hoặc `99` (🧪) |
| **Rollback do lỗi tạm thời (Lock, Timeout)** | `LINKED` | `RETRYABLE_ERROR` | Tên exception ngắn | `FALSE / FALSE` | Không | `99` (🧪) |
| **Rollback do lỗi xác định khác** | `LINKED` | `PROCESSING_FAILED` | Tên exception ngắn | `FALSE / FALSE` | Không | `99` (🧪) |

### 7.2. Bảng Quy Tắc Ghi Nhận (Nguồn Duy Nhất)

| Bảng dữ liệu | Ghi nhận khi nào? | Cơ chế Khử trùng (Deduplication)? |
| :--- | :--- | :--- |
| `payment_ipn_raw_receipts` | Mọi HTTP request tới được controller IPN (INSERT trước mọi quyết định từ chối) | **Không khử trùng** (Lưu mọi request để phục vụ giám sát forensic) |
| `payment_ipn_events` | Mỗi callback đã kiểm tra cấu trúc, xác thực chữ ký, liên kết attempt và transaction nghiệp vụ commit | **Không khử trùng** (Callback lặp idempotent lưu event `DUPLICATE_ACKNOWLEDGED` riêng) |
| `payment_reconciliation_cases` | Khi IPN event có outcome cần đối soát HOẶC khi Admin ra quyết định `REJECTED_FRAUD` (cùng transaction) | **Có khử trùng:** Ràng buộc `dedup_key UNIQUE` |
| `payment_alert_requests` | Cùng transaction với việc tạo mới reconciliation case | **Có khử trùng:** Ràng buộc `case_id UNIQUE` (1-1 với case mới) |

*Phân loại 3 nhóm event:*
- **Event áp dụng:** `APPLIED_SUCCESS`, `APPLIED_DUPLICATE_CHARGE`, `APPLIED_EXPIRED_ORDER`, `FLAGGED_SUSPICIOUS`, `APPLIED_PENDING_REVIEW`, `APPLIED_FAILED`.
- **Event xác nhận (ACK):** `DUPLICATE_ACKNOWLEDGED`, `CONFLICT_IGNORED`.
- **Event từ chối (Rejected):** `REJECTED_AMOUNT_MISMATCH`, `REJECTED_CURRENCY_MISMATCH`.

### 7.3. Định Nghĩa Chuẩn `ipn_count` và Tính Nhất Quán
- `ipn_count` trên `payment_attempts` là **tổng số callback đã xác thực, liên kết attempt và commit thành công vào DB**.
- Cả event áp dụng, event ACK và event từ chối (`REJECTED_*`) đều làm tăng `ipn_count` thêm 1.
- **Bất biến toàn vẹn:** Với mỗi attempt, luôn thỏa mãn:
  $$\mathbf{attempt.ipn\_count = COUNT(payment\_ipn\_events \ WHERE \ attempt\_id = attempt.id)}$$
- `first_ipn_received_at` và `last_ipn_received_at` cũng được tính theo các callback đã commit thành công. Request sai cấu trúc, sai chữ ký hoặc unlinked không làm tăng `ipn_count`.

### 7.4. Vòng Đời Receipt & Tác Vụ Quét Kẹt (Stuck Receipt Scanner)
- **Lựa chọn kiến trúc (Lựa chọn A):** `payment_ipn_events` ghi trong transaction nghiệp vụ; cùng commit hoặc cùng rollback với `Order/Payment/Attempt`. Nếu nghiệp vụ bị rollback, dấu vết được lưu tại raw receipt với `processing_status = 'PROCESSING_FAILED'` hoặc `'RETRYABLE_ERROR'`.
- **Tác vụ quét receipt bất thường (Background Scanner):**
  - Định kỳ (ví dụ mỗi 15 phút, lớn hơn thời gian timeout tối đa của transaction nghiệp vụ), quét tìm:
    (a) Receipt ở trạng thái `RECEIVED` quá 15 phút và không có event liên kết -> cập nhật sang `PROCESSING_FAILED`.
    (b) Receipt ở trạng thái `RECEIVED` nhưng đã có event liên kết -> cập nhật sang `LINKED/PROCESSED`, log ERROR cảnh báo vi phạm.
  - **Quy tắc khóa:** Quét bằng `SELECT ... FOR UPDATE SKIP LOCKED` chỉ trên bảng `payment_ipn_raw_receipts`; **tuyệt đối không khóa hay chạm vào bảng Order, Payment hay Attempt**.

### 7.5. Bảo Toàn Audit & Retention Policy
- `payment_ipn_events.receipt_id` và `attempt_id` đều dùng **`ON DELETE RESTRICT`**.
- User ứng dụng (`shop_app`) chỉ được cấp quyền `INSERT` và `SELECT` trên `payment_ipn_events`; thu hồi toàn bộ quyền `UPDATE` và `DELETE`.
- Dữ liệu đối soát và sự kiện IPN lưu trữ từ 3 đến 5 năm theo chính sách lưu trữ kiểm toán nội bộ của dự án (không tự ý xem là nghĩa vụ pháp lý/kế toán khi chưa xác minh quy định áp dụng).

---

## 8. Return URL, Xác minh Phía Máy chủ (Verify API) & Polling

### 8.1. Đặc tả API `GET /api/v1/payments/vnpay-verify`
- Yêu cầu JWT xác thực (`@AuthenticationPrincipal Jwt jwt`), kiểm tra quyền sở hữu `order.getBuyerId().equals(buyerId)` (chống IDOR).
- Dùng chung thành phần `VnPayQueryParser` với IPN: Nếu vi phạm cấu trúc query, trả ngay trạng thái `INVALID_SIGNATURE` (hoặc `MALFORMED_REQUEST`), không gọi DB nghiệp vụ.
- **Nguyên tắc Read-Only:** Tuyệt đối không gọi `save()` hay mutation trạng thái đơn hàng.
- Trả về DTO phân loại: `SUCCESS`, `PENDING_CONFIRMATION`, `UNDER_REVIEW`, `FAILED`, `INVALID_SIGNATURE`, `NOT_FOUND`, `AMOUNT_MISMATCH`.

### 8.2. Polling tại Frontend
- Khi nhận `PENDING_CONFIRMATION`: Hiển thị spinner, tự động polling mỗi 2 giây, tối đa 5 lần (10 giây). Hết 5 lần dừng tự động và hiển thị nút "Kiểm tra lại trạng thái" thủ công.

---

## 9. Frontend & Cô lập Luồng Mock

### 9.1. Giao diện Người dùng
- Trong `PaymentView.tsx`, bổ sung lựa chọn "Cổng VNPAY (QR Code / Thẻ ATM / Visa)". Bấm thanh toán gọi `paymentApi.createVnPayUrl(orderId)` và redirect: `window.location.href = paymentUrl`.

### 9.2. Cô lập nút Mock
- Nút mock thanh toán trong `PaymentView` được bọc trong điều kiện:
  `{(import.meta.env.DEV || import.meta.env.VITE_ENABLE_MOCK_PAYMENT === 'true') && ( ... )}`
  Tuyệt đối không hiển thị ở production.

---

## 10. Nguồn và Mức độ Xác minh (Verification Evidence Matrix) & Điểm Còn Mở

### 10.1. Ma Trận Xác Minh Kỹ Thuật

| Nội dung / Tham số | Mức độ xác minh | Căn cứ kỹ thuật / Nguồn tài liệu |
| :--- | :---: | :--- |
| **Check constraint `ck_payments_state_fields`** | ✅ Đã xác minh từ Repo | Khai báo trong [V1__initial_schema.sql](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/resources/db/migration/V1__initial_schema.sql#L424-L438). Cần DROP/ADD trong `V12`. |
| **`SecurityConfig` `anyRequest().denyAll()`** | ✅ Đã xác minh từ Repo | [SecurityConfig.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/shared/config/SecurityConfig.java#L69). Matcher admin bắt buộc phải đặt trước. |
| **JWT converter tự động thêm `ROLE_`** | ✅ Đã xác minh từ Repo | [SecurityConfig.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/shared/config/SecurityConfig.java#L106). |
| **Method entity chuyển trạng thái còn thiếu** | ✅ Đã xác minh từ Repo | Kiểm tra `OrderEntity.java` và `PaymentEntity.java` xác nhận chưa có `markUnderReview`, `approveFromReview`, `markRefundPending`. |
| **Mã IPN `00` và `02` kết thúc webhook (không retry)** | 📘 Tài liệu VNPay chính thức (🧪) | [Tài liệu Tích hợp VNPay](https://sandbox.vnpayment.vn/apis/docs/huong-dan-tich-hop/) — Gắn 🧪 cần xác minh Sandbox. |
| **Mã IPN `01`, `04`, `97`, `99` hoặc timeout kích hoạt Retry** | 📘 Tài liệu VNPay chính thức (🧪) | [Bảng mã lỗi IPN VNPay](https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/) — Gắn 🧪 cần xác minh Sandbox. |
| **Mã phản hồi `vnp_ResponseCode = 07`** | 📘 Tài liệu VNPay chính thức | Trừ tiền thành công, giao dịch nghi ngờ gian lận. Cần đưa vào `UNDER_REVIEW`. |
| **Hành vi retry thực tế & Giới hạn `vnp_ExpireDate`** | 🧪 Cần kiểm tra bằng Merchant Sandbox | Xác minh số lần retry, khoảng cách retry và khoảng giá trị expireDate được Sandbox chấp nhận. |

### 10.2. Danh Sách Các Điểm Còn Mở (Open Items)
1. **Cần kiểm chứng bằng Sandbox / Kênh Merchant VNPay (🧪):**
   - Mã phản hồi chuẩn cho Currency Mismatch (tạm chọn mã `99` theo Phương án B).
   - Phản hồi mã `97` cho request `MALFORMED_REQUEST` có khiến VNPay ngừng hay kích hoạt retry.
   - Hành vi retry thực tế của VNPay Sandbox (tần suất, số lần tối đa, các mã thực sự kích hoạt retry).
   - Kiểm chứng VNPay Sandbox luôn percent-encode các ký tự tiếng Việt / ngoài ASCII trong các tham số như `vnp_OrderInfo`, `vnp_BankTranNo`.
   - Kiểm chứng callback thành công có trường hợp nào bị thiếu `vnp_TransactionNo` hay không.
   - Độ đầy đủ của danh sách `KNOWN_VNP_PARAM_NAMES`.
   - Kích thước query string lớn nhất ghi nhận được trong thực tế Sandbox (để kiểm chứng ngưỡng 4096 code points).
   - Cách Embedded Tomcat giải mã byte của `request.getQueryString()`.
2. **Cần chủ dự án / Lead quyết định:**
   - Bộ phận gửi cảnh báo outbox (`payment_alert_requests` -> Slack/Telegram/Email) triển khai trong phase này hay phase sau (trong kế hoạch hiện tại, phase này hoàn thành việc ghi bản ghi outbox bền vững trong cùng transaction).
   - Job tự động quét và cảnh báo các đơn `UNDER_REVIEW` quá 48h (triển khai ngay hay giám sát qua query).
   - Tác vụ quét receipt kẹt (`Stuck Receipt Scanner`) có cần viết background job hoàn chỉnh hay thực hiện qua runbook bảo trì định kỳ.

---

## 11. Bảng Phân Rã Công Việc Triển Khai (Actionable Task Breakdown)

### Phase 1: Database Migration & Backend Core

#### Task BE-01: Flyway Migration `V12` tạo bảng Attempt, Receipt, Event, Case, Alert & Sửa Constraint State Fields
- **Phạm vi:** 
  - DROP/ADD `ck_payments_method`, `ck_orders_status`, `ck_payments_status`.
  - DROP/ADD `ck_payments_state_fields` hỗ trợ `UNDER_REVIEW`, `REFUND_PENDING` (cho phép `held_at IS NULL`), `REFUNDED`.
  - Composite unique `uq_payments_payment_order`.
  - Tạo `payment_attempts` với composite FK, partial index `uq_payment_attempts_single_pending`.
  - Tạo `payment_ipn_raw_receipts` với đầy đủ 14 cột, CHECK (a)-(d), CHECK verification_status, CHECK processing_status.
  - Tạo `payment_ipn_events` với `receipt_id UNIQUE REFERENCES payment_ipn_raw_receipts(receipt_id) ON DELETE RESTRICT`, `attempt_id ON DELETE RESTRICT`, bỏ `raw_query_params`, thêm `vnp_curr_code`, `vnp_amount_text`, CHECK amount conditional.
  - Tạo `payment_review_audits` với `ON DELETE RESTRICT`.
  - Tạo `payment_reconciliation_cases` với `dedup_key VARCHAR UNIQUE`, CHECK tổ hợp, CHECK nguồn khóa ngoại.
  - Tạo `payment_alert_requests` outbox với `case_id UNIQUE REFERENCES payment_reconciliation_cases(case_id) ON DELETE RESTRICT`.
  - Phân quyền DB: Grant `INSERT, SELECT` trên `payment_ipn_events` cho app user; thu hồi `UPDATE, DELETE`.
- **Phụ thuộc:** `V1` đến `V11`.
- **Files:** `backend/src/main/resources/db/migration/V12__add_vnpay_attempts.sql`.
- **Tiêu chí hoàn tất:** Migration chạy trơn tru trên PostgreSQL mới lẫn schema đã có `V1`–`V11`; toàn bộ constraint, partial index, và foreign key hoạt động chuẩn xác.

#### Task BE-02: Tạo Entity, Repository, Parser & Method Chuyển Trạng Thái
- **Phạm vi:** 
  - Cập nhật [OrderEntity.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/commerce/infrastructure/persistence/OrderEntity.java): Thêm hằng số `STATUS_UNDER_REVIEW`, thêm methods: `markUnderReview(now)`, `approveFromReview(now)`, `cancelFromReview(reason, now)` (kiểm tra trạng thái nguồn hợp lệ và ném `IllegalStateException`).
  - Cập nhật [PaymentEntity.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentEntity.java): Thêm hằng số `METHOD_VNPAY`, `STATUS_UNDER_REVIEW`, thêm methods: `markUnderReview(txnCode, now)` (chỉ chuyển từ `PENDING`, kiểm tra trạng thái nguồn), `approveFromReview(now)`, `markRefundPending(amount, reason, now)`.
  - Xây dựng `VnPayQueryParser.java`: Thành phần phân tích query dùng chung, đọc `getQueryString()` 1 lần, làm sạch surrogate lẻ, cắt/hash, phân tích param, trả về `ParseResult` kèm mã `processing_error` cố định.
  - Xây dựng `IpnReceiptService.java`: Service riêng chạy `REQUIRES_NEW` để INSERT raw receipt ban đầu và UPDATE kết quả từ chối độc lập với transaction nghiệp vụ.
  - Tạo Entity và Repository cho: `PaymentAttemptEntity`, `PaymentIpnRawReceiptEntity`, `PaymentIpnEventEntity`, `PaymentReviewAuditEntity`, `PaymentReconciliationCaseEntity`, `PaymentAlertRequestEntity`.
- **Phụ thuộc:** BE-01.
- **Files:**
  - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/infrastructure/persistence/OrderEntity.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentEntity.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/VnPayQueryParser.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/IpnReceiptService.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentAttemptEntity.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentIpnRawReceiptEntity.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentIpnEventEntity.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentReviewAuditEntity.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentReconciliationCaseEntity.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentAlertRequestEntity.java`
- **Tiêu chí hoàn tất:** Entity mapping đầy đủ; parser chạy đúng 100% test cases; methods entity ném exception chính xác khi sai trạng thái nguồn.

#### Task BE-03: Cập nhật `VnPayService` sử dụng `Clock` & Múi giờ Việt Nam
- **Phạm vi:** Inject bean `Clock` vào `VnPayService`, định dạng `vnp_CreateDate` và `vnp_ExpireDate` (+15 phút theo cấu hình O.G-Shop) theo timezone `Asia/Ho_Chi_Minh`.
- **Phụ thuộc:** Không.
- **Files:**
  - [VnPayService.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/application/VnPayService.java)
  - [VnPayServiceTest.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/test/java/com/oldbutgold/shop/modules/payment/application/VnPayServiceTest.java)
- **Tiêu chí hoàn tất:** Unit test kiểm thử tạo URL với clock cố định pass 100%.

#### Task BE-04: Refactor API Tạo Link Thanh Toán `POST /api/v1/payments/vnpay-url`
- **Phạm vi:**
  - Sửa request body chỉ nhận `{ "orderId": Long }`.
  - Lấy `buyerId` từ JWT, kiểm tra quyền sở hữu đơn hàng, kiểm tra status `PAYMENT_PENDING`, chưa quá hạn 1 giờ.
  - Khóa theo thứ tự: `Order → Payment → PaymentAttempt`.
  - Chuyển attempt `PENDING` cũ sang `SUPERSEDED`.
  - Sinh attempt mới với `txn_ref = OG_{orderId}_{timestamp}`, lưu vào DB.
  - Sinh URL VNPay với số tiền lấy từ `order.getTotalAmount()`.
- **Phụ thuộc:** BE-02, BE-03.
- **Files:**
  - [PaymentController.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentController.java)
  - [PaymentDtos.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentDtos.java)
  - [PaymentService.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/application/PaymentService.java)
- **Tiêu chí hoàn tất:** Chặn 401 khi thiếu JWT, chặn 403 khi gọi đơn người khác, chặn 400 khi đơn quá hạn; sinh URL chuẩn khi hợp lệ.

#### Task BE-05: Triển khai Webhook IPN `GET /api/v1/payments/vnpay-ipn` với Raw Receipts, Khóa Cố Định & Bảng Mã Chuẩn
- **Phạm vi:** 
  - Gọi `VnPayQueryParser`: Đọc query đúng 1 lần, ghi raw receipt trước khi từ chối; xử lý 6 mã `processing_error` cố định.
  - Xác thực chữ ký. Nếu hợp lệ, điền cột tra cứu forensic trên receipt.
  - Transaction nghiệp vụ: Khóa theo thứ tự `Order → Payment → PaymentAttempt`.
  - Thứ tự kiểm tra: (1) So amount & currency -> ghi nhận event `REJECTED_AMOUNT_MISMATCH` (mã `04` 🧪) hoặc `REJECTED_CURRENCY_MISMATCH` (mã `99` 🧪), tăng `ipn_count`, tạo case `MISMATCH_CHARGE` + alert request nếu callback báo đã thu tiền hoặc nghi ngờ (`00/00` hoặc `07`) (báo thất bại mà sai amount/currency thì chỉ ghi event, không tạo case/alert), không đổi trạng thái Order/Payment/Attempt (không dùng `FLAGGED_SUSPICIOUS`, không đưa vào `UNDER_REVIEW`, không trả `00`), commit transaction rồi mới ánh xạ mã phản hồi; (2) Kiểm tra terminal attempt -> ghi event `CONFLICT_CHARGE_REQUIRES_RECONCILIATION` nếu thành công sau `FAILED`, trả `02`; (3) Rẽ nhánh A/B/C với guard chặt chẽ cho mã 07 (chỉ vào nhánh B khi amount/currency hợp lệ).
  - Trong cùng transaction: Cập nhật Order/Payment/Attempt; tăng `ipn_count`; ghi `payment_ipn_events`; tạo case + alert request; UPDATE receipt sang `LINKED/PROCESSED`.
  - Ánh xạ mã phản hồi duy nhất theo Bảng Mục 5.1.
- **Phụ thuộc:** BE-02, BE-04.
- **Files:**
  - [PaymentService.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/application/PaymentService.java)
  - [PaymentController.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentController.java)
- **Tiêu chí hoàn tất:** Xử lý chính xác mọi kịch bản; bất biến `ipn_count = COUNT(events)` thỏa mãn 100%.

#### Task BE-06: Xây dựng API Xác minh Return URL `GET /api/v1/payments/vnpay-verify`
- **Phạm vi:** Dùng chung `VnPayQueryParser`; từ chối ngay nếu query sai cấu trúc; xác thực chữ ký; kiểm tra JWT và quyền sở hữu đơn hàng (chống IDOR); chỉ đọc DB, tuyệt đối không gọi `save()`.
- **Phụ thuộc:** BE-02, BE-04.
- **Files:**
  - [PaymentController.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentController.java)
  - [PaymentService.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/application/PaymentService.java)
  - [PaymentDtos.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentDtos.java)
- **Tiêu chí hoàn tất:** Endpoint read-only tuyệt đối; chặn 403 nếu IDOR; dùng chung parser nhất quán.

#### Task BE-07: Xây dựng Nghiệp vụ Thẩm định & Đóng Trạng thái `UNDER_REVIEW` (Admin / KTV Resolution Runbook)
- **Phạm vi:**
  - Cập nhật [SecurityConfig.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/shared/config/SecurityConfig.java): Thêm matcher `requestMatchers("/api/v1/admin/payments/**").hasAnyRole("ADMIN", "KTV")` đặt trước `.anyRequest().denyAll()`.
  - Triển khai `AdminPaymentController.java` endpoint `POST /api/v1/admin/payments/review-decision`.
  - Tiếp nhận quyết định `APPROVED_SAFE` (`Order -> PAID_HELD`, `Payment -> HELD`, `Attempt` giữ nguyên) hoặc `REJECTED_FRAUD` (`Order -> CANCELLED`, `Payment -> REFUND_PENDING`, `Attempt` giữ nguyên, nhả hàng tồn kho, tạo case `REFUND_PENDING` + alert request trong cùng transaction).
  - Ghi bản ghi vào `payment_review_audits`.
- **Phụ thuộc:** BE-02, BE-05.
- **Files:**
  - [SecurityConfig.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/shared/config/SecurityConfig.java)
  - `backend/src/main/java/com/oldbutgold/shop/modules/payment/api/AdminPaymentController.java`
  - [PaymentService.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/application/PaymentService.java)
  - [PaymentDtos.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentDtos.java)
- **Tiêu chí hoàn tất:** Endpoint admin được bảo vệ chặt chẽ (401 khi chưa đăng nhập, 403 khi sai role, 200 khi đúng role); trạng thái attempt giữ nguyên sau duyệt; case và audit được tạo trọn vẹn.

---

### Phase 2: Frontend Integration & User Experience

#### Task FE-01: Bổ sung API Client & Types
- **Phạm vi:** Cập nhật `paymentApi.ts` thêm `createVnPayUrl(orderId)` và `verifyVnPayReturn(params)` (gửi kèm JWT Bearer token). Cập nhật `types.ts` với DTO phản hồi xác minh đầy đủ các trạng thái.
- **Phụ thuộc:** BE-04, BE-06.
- **Files:**
  - [paymentApi.ts](file:///d:/ĐỒ ÁN 1/O.G-Shop/frontend/src/features/payment/paymentApi.ts)
  - [types.ts](file:///d:/ĐỒ ÁN 1/O.G-Shop/frontend/src/features/payment/types.ts)
- **Tiêu chí hoàn tất:** Hàm API có kiểu dữ liệu TypeScript chặt chẽ, xử lý lỗi chuẩn.

#### Task FE-02: Tích hợp Lựa chọn VNPay vào `PaymentView` & Cô lập Mock
- **Phạm vi:** Thêm phương thức "Cổng VNPAY (QR Code / Thẻ ATM / Visa)" trong `PaymentView.tsx`. Bấm thanh toán gọi API lấy link và redirect. Đưa các nút Mock vào điều kiện `DEV`.
- **Phụ thuộc:** FE-01.
- **Files:** [PaymentView.tsx](file:///d:/ĐỒ ÁN 1/O.G-Shop/frontend/src/features/payment/components/PaymentView.tsx).
- **Tiêu chí hoàn tất:** Giao diện trực quan; bấm nút chuyển hướng sang đúng cổng Sandbox.

#### Task FE-03: Xây dựng Route & Component Tiếp nhận Kết quả Return URL
- **Phạm vi:** Khai báo route `/payment/vnpay-return` trong `App.tsx`. Tạo page wrapper `pages/VnPayReturnPage.tsx`. Tạo feature component `features/payment/components/VnPayReturnView.tsx` xử lý gọi verify, hiển thị thành công, đang rà soát, thất bại, và cơ chế polling tối đa 5 lần (2s/lần) khi nhận `PENDING_CONFIRMATION` kèm nút kiểm tra lại thủ công.
- **Phụ thuộc:** FE-01.
- **Files:**
  - `frontend/src/pages/VnPayReturnPage.tsx`
  - `frontend/src/features/payment/components/VnPayReturnView.tsx`
  - [App.tsx](file:///d:/ĐỒ ÁN 1/O.G-Shop/frontend/src/app/App.tsx)
- **Tiêu chí hoàn tất:** Giao diện đẹp, phản hồi mượt mà; polling tự động dừng sau 5 lần nếu chưa có kết quả.

---

### Phase 3: Automated Testing, Edge Cases & Verification

#### Task TEST-01: Mở rộng Test Suite Backend (Unit & Integration Tests Với PostgreSQL/Testcontainers)
- **Phạm vi:** Mở rộng [PaymentServiceTest.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/test/java/com/oldbutgold/shop/modules/payment/application/PaymentServiceTest.java) và [PaymentControllerTest.java](file:///d:/ĐỒ ÁN 1/O.G-Shop/backend/src/test/java/com/oldbutgold/shop/modules/payment/api/PaymentControllerTest.java).
- **Quy tắc kiểm thử bắt buộc:** **Phải sử dụng cơ sở dữ liệu thực (PostgreSQL thông qua Testcontainers)** để kiểm thử khóa bi quan (`SELECT FOR UPDATE`), composite foreign key, check constraints, và partial unique index. **Tuyệt đối không dùng unit test mock (Mockito) để khẳng định khóa DB hoặc ràng buộc toàn vẹn hoạt động.**
- **Danh sách kịch bản kiểm thử bắt buộc:**
  1. **Constraint payments:** Kiểm tra `ck_payments_state_fields` cho `UNDER_REVIEW`, chuyển `UNDER_REVIEW -> HELD`, và chuyển `UNDER_REVIEW -> REFUND_PENDING` (thỏa mãn khi `held_at IS NULL`).
  2. **Duyệt rà soát giữ nguyên trạng thái attempt:** Xác nhận `attempt.status` giữ nguyên `FLAGGED_SUSPICIOUS` sau khi KTV duyệt `APPROVED_SAFE` hoặc `REJECTED_FRAUD`; chỉ ghi `payment_review_audits`; nhận diện khoản thu trước vẫn hoạt động chính xác sau duyệt.
  3. **Raw receipt cơ bản:** Ghi receipt thành công khi sai chữ ký, thiếu/quá dài `vnp_TxnRef`; chữ ký sai -> `(SIGNATURE_INVALID, PROCESSED)`; txnRef không tìm thấy -> `(SIGNATURE_VALID_UNLINKED, PROCESSED)`; không sinh event/case, không đổi trạng thái nghiệp vụ.
  4. **Ghi receipt hai bước:** Sau INSERT ban đầu, 3 cột tra cứu forensic `vnp_*` là `NULL`; request hợp lệ đơn trị được điền; ép lỗi ở bước điền -> receipt vẫn tồn tại với cột NULL và request vẫn xử lý bình thường.
  5. **Receipt trước khi từ chối (Thứ tự Mục 4.1):** Với query non-ASCII, quá lớn, `%` sai, tham số trùng -> receipt được INSERT trước khi có kết luận. Ép lỗi ở bước UPDATE từ chối: receipt vẫn còn `(UNVERIFIED, RECEIVED)`, request bị từ chối với mã chuẩn, không chạm bảng nghiệp vụ.
  6. **Chuỗi thô `vnp_amount_text`:** Giữ nguyên chuỗi nhận được (`12abc`, `1e5`, `-100`, rỗng, thập phân, có `+`, `%20`) với query ASCII hợp lệ; receipt ghi được; `vnp_amount_text` giữ nguyên không trim/parse; không sinh event, không đổi trạng thái nghiệp vụ.
  7. **Dữ liệu thô không tin cậy:** Request chữ ký sai có `vnp_TxnRef`/`vnp_TransactionNo` trùng giao dịch thật -> không sinh event/case, không đổi `ipn_count`; `dedup_key` chỉ sinh từ callback đã xác thực.
  8. **Xử lý Non-ASCII:** Query chứa ký tự ngoài ASCII chưa percent-encode (`vnp_OrderInfo=Thanh toán`) -> `(MALFORMED_REQUEST, PROCESSED, NON_ASCII_RAW_QUERY)`, không parse, không chạm bảng nghiệp vụ, trả `97`. Cùng giá trị đã percent-encode (`Thanh%20to%C3%A1n`) -> xử lý bình thường. Ký tự điều khiển và NUL thật -> `NON_ASCII_RAW_QUERY`.
  9. **Ký tự ngoài BMP & Surrogate lẻ:** Emoji trong query ghi thành công, CHECK độ dài đúng ở cả Java (`codePointCount`) và PostgreSQL (`char_length`); surrogate lẻ được thay bằng `?`, đặt `raw_query_sanitized = true`, `processing_error = NON_ASCII_RAW_QUERY`, không lỗi INSERT.
  10. **Giới hạn kích thước:** Query đúng bằng giới hạn 4096 code points -> xử lý bình thường, `raw_query_truncated = false`; vượt 1 code point -> `(MALFORMED_REQUEST, PROCESSED, RAW_QUERY_TOO_LARGE)`, `truncated = true`, lưu đúng 4096 code points, `raw_query_sha256` tính trên chuỗi đầy đủ. INSERT ban đầu ở `UNVERIFIED/RECEIVED` với `truncated=true` thành công (thỏa CHECK (c)).
  11. **Ràng buộc CHECK receipt từ chối dữ liệu sai:** DB từ chối UPDATE receipt `truncated=true` hoặc `sanitized=true` sang `LINKED`; từ chối receipt có ký tự ngoài ASCII in được ở `LINKED`; từ chối INSERT `truncated=false` mà độ dài khác `original_length`; từ chối query vượt 8192 code points.
  12. **Ký tự NUL trong giá trị giải mã (`%00`):** Query ASCII hợp lệ, receipt ghi được, `vnp_amount_text` NULL, `raw_query_params` giữ `%00`.
  13. **INSERT receipt thất bại:** Ép lỗi DB sập ở bước INSERT receipt -> không xử lý nghiệp vụ, trả HTTP 500 / mã có thể gửi lại, Order/Payment/Attempt không đổi, không có event.
  14. **Bộ phân tích cú pháp (Parser):** Giá trị chứa dấu `=` (base64) giữ nguyên; cặp không có `=` có giá trị rỗng; `&&` bỏ qua; tên rỗng -> `EMPTY_PARAM_NAME`; `%C3%28`, `%zz`, `%4` cụt -> `INVALID_PERCENT_ENCODING`; `vnp%5FAmount` được giải mã thành `vnp_Amount`.
  15. **Tham số trùng tên & Sai kiểu chữ:** `vnp_Amount` + `vnp_amount`, hoặc `vnp_TxnRef` + `VNP%5FTXNREF` -> `DUPLICATE_VNP_PARAM:<tên>`; tên đứng riêng sai kiểu chữ (`VNP_Amount`, `vnp_amount`) -> `NONCANONICAL_VNP_PARAM_CASE:<tên>`, không chạm bảng nghiệp vụ dù chữ ký tính đúng trên tên đó.
  16. **Sai Amount có chữ ký hợp lệ (callback 00/00 hoặc 07):** Event `REJECTED_AMOUNT_MISMATCH`, receipt `(LINKED, PROCESSED)`, `ipn_count` +1, Order/Payment/Attempt không đổi (không chuyển sang `UNDER_REVIEW`, không gán `FLAGGED_SUSPICIOUS`), đúng 1 case `MISMATCH_CHARGE` + 1 alert request, commit trước khi trả mã `04` (không trả `00`). Event tồn tại sau khi endpoint trả mã (chứng minh không bị rollback do exception).
  17. **Amount không parse được / âm / tràn / không phải bội 100:** Event `REJECTED_AMOUNT_MISMATCH` với `vnp_amount_raw/vnp_amount` NULL, lưu `vnp_amount_text`. DB từ chối INSERT event NULL amount với outcome `APPLIED_*`.
  18. **Sai Currency (`vnp_CurrCode=USD`, callback 00/00 hoặc 07):** Event `REJECTED_CURRENCY_MISMATCH`, tạo case + alert, trả mã `99` (không đổi trạng thái, không gán `FLAGGED_SUSPICIOUS`, không trả `00`). Thiếu `vnp_CurrCode` -> dùng currency attempt, không mismatch.
  19. **Callback thất bại sai amount:** Event `REJECTED_*`, không tạo case/alert, trạng thái không đổi.
  20. **Sai amount trên attempt đã Terminal:** Event `REJECTED_*` (không phải `DUPLICATE_ACKNOWLEDGED`), trạng thái attempt không đổi.
  21. **Sai amount gửi 3 lần:** 3 receipt, 3 event `REJECTED_*`, đúng 1 case, đúng 1 alert request, `ipn_count = 3 = COUNT(events)`.
  22. **Rollback trong nghiệp vụ (Lựa chọn A):** Ép lỗi DB giữa transaction -> receipt ở `PROCESSING_FAILED` hoặc `RETRYABLE_ERROR`, không có event, Order/Payment/Attempt không đổi. Gửi lại cùng IPN -> thành công, đúng 1 event áp dụng, đúng 1 case, 2 receipt liên kết qua `vnp_txn_ref`. Gửi lần ba -> thêm 1 event `DUPLICATE_ACKNOWLEDGED`, vẫn đúng 1 event áp dụng và 1 case.
  23. **N lần gọi lặp thành công muộn sau FAILED:** Gửi 3 lần -> 3 receipt (LINKED/PROCESSED), 3 event (1 `CONFLICT_CHARGE_REQUIRES_RECONCILIATION` + 2 `DUPLICATE_ACKNOWLEDGED`), đúng 1 case `LATE_SUCCESS_ON_FAILED`, đúng 1 alert request, `ipn_count = 3`. Không đếm dòng log để khẳng định cảnh báo.
  24. **Cùng attempt nhưng khác `vnp_TransactionNo`:** Callback thành công có mã mới -> event `CONFLICT_CHARGE_REQUIRES_RECONCILIATION` và case riêng theo mã mới (`LATE_SUCCESS_ON_FAILED` nếu attempt vẫn `FAILED`; `DUPLICATE_CHARGE` nếu attempt đã có khoản thu thành công); tạo alert request khi case mới. Lặp lại cùng mã mới -> event `DUPLICATE_ACKNOWLEDGED`, không tạo case/alert thêm. `ipn_count = COUNT(events)` đúng. Thiếu mã -> dùng `NO_TXN_NO` và gom chung case.
  25. **Case REFUND_PENDING từ quyết định Review:** KTV duyệt `REJECTED_FRAUD` -> đúng 1 case `source_type=REVIEW_DECISION`, `review_audit_id` có giá trị, `event_id` NULL, kèm 1 alert request. Quyết định lần hai trên đơn đã `CANCELLED` bị chặn bởi kiểm tra trạng thái, không tạo case thứ hai.
  26. **Ràng buộc CHECK của Case:** DB từ chối: `IPN_EVENT` thiếu `event_id`; có cả `event_id` lẫn `review_audit_id`; `REFUND_PENDING` với `source_type=IPN_EVENT`; case bất thường với `source_type=REVIEW_DECISION`; trùng `dedup_key`.
  27. **Quan hệ Receipt <-> Event:** DB từ chối INSERT 2 event cùng `receipt_id`; từ chối `receipt_id` NULL hoặc không tồn tại.
  28. **Bất biến `ipn_count`:** `ipn_count = COUNT(events của attempt)` đúng trong mọi kịch bản.
  29. **Quét không tranh chấp:** Giữ một transaction nghiệp vụ đang chạy IPN (đang lock), chạy truy vấn quét song song -> bỏ qua receipt đó (`SKIP LOCKED`), không deadlock; quét không phát sinh khóa trên Order/Payment/Attempt.
  30. **Guard mã 07:** Nhận mã 07 khi đơn đã `PAID_HELD`, `CANCELLED` hoặc quá hạn -> không kéo ngược trạng thái đơn sang `UNDER_REVIEW`. Khi Order và Payment đã cùng `UNDER_REVIEW`, callback mới không gọi lại transition methods, giữ dữ liệu review và ghi event cho attempt hiện tại; nếu hai trạng thái lệch nhau thì không tự sửa mà xử lý như vi phạm invariant.
  31. **Security matcher:** 401 khi chưa đăng nhập; 403 khi đăng nhập role Buyer/Seller; 200 khi đăng nhập role ADMIN/KTV.
  32. **Ràng buộc toàn vẹn xóa:** DB chặn xóa attempt khi đã có IPN event (`ON DELETE RESTRICT`); user ứng dụng không thể chạy `UPDATE` hoặc `DELETE` trên `payment_ipn_events`.
- **Tiêu chí hoàn tất:** Test suite `./mvnw clean test` chạy thành công 100%.

#### Task TEST-02: Kiểm thử Toàn trình Sandbox (E2E Integration Verification)
- **Phạm vi:** Setup ngrok tunnel trỏ vào cổng 8080, cấu hình IPN URL trên Merchant Portal Sandbox, thực hiện thanh toán toàn trình và kiểm thử Return polling.
- **Tiêu chí hoàn tất:**
  1. Bật `ngrok http 8080`, cấu hình IPN URL trên Merchant Portal.
  2. Đặt đơn hàng mới -> thanh toán thẻ test ATM Sandbox (`9704198526191432198`).
  3. Backend nhận IPN webhook, cập nhật `OrderEntity` sang `PAID_HELD` và `PaymentEntity` sang `HELD`.
  4. Trình duyệt quay về Return URL hiển thị màn hình thanh toán thành công với mã giao dịch VNPay.
  5. Kiểm tra `npm run build` trên frontend không có lỗi TypeScript hay linting.
