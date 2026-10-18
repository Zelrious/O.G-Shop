# Danh sách công việc (Todo List) - Tích hợp VNPay Sandbox (Kiến Trúc Chuẩn Hóa Chuyên Sâu)

## Phase 1: Database Migration & Backend Core
- [x] **BE-01**: Viết Flyway migration `V12__add_vnpay_attempts.sql`:
  - `ALTER TABLE payments DROP CONSTRAINT ck_payments_method;`
  - `ALTER TABLE payments ADD CONSTRAINT ck_payments_method CHECK (payment_method IN ('COD_MOCK', 'BANK_TRANSFER_MOCK', 'E_WALLET_MOCK', 'VNPAY'));`
  - Mở rộng `ck_orders_status` và `ck_payments_status` thêm trạng thái `'UNDER_REVIEW'`
  - **Sửa `ck_payments_state_fields`:** DROP và ADD lại constraint với nhánh `UNDER_REVIEW` (`paid_at IS NOT NULL`, `held_at IS NULL`, `released_at IS NULL`, `refunded_at IS NULL`, `failure_reason IS NULL`), đồng thời mở rộng `REFUND_PENDING` và `REFUNDED` cho phép `held_at IS NULL OR held_at >= paid_at` để hợp lệ khi chuyển tiếp từ `UNDER_REVIEW`
  - `ALTER TABLE payments ADD CONSTRAINT uq_payments_payment_order UNIQUE (payment_id, order_id);` (hỗ trợ composite foreign key)
  - `CREATE TABLE payment_attempts` với composite FK `(payment_id, order_id) REFERENCES payments (payment_id, order_id) ON DELETE RESTRICT`, `txn_ref UNIQUE`, `amount > 0`, `currency = 'VND'`, check constraint trạng thái đầy đủ 9 trạng thái
  - `CREATE UNIQUE INDEX uq_payment_attempts_single_pending ON payment_attempts(payment_id) WHERE status = 'PENDING';`
  - `CREATE TABLE payment_ipn_raw_receipts` lưu MỌI HTTP request đến controller: `receipt_id`, `raw_query_params` (TEXT NOT NULL), `raw_query_original_length` (INT NOT NULL), `raw_query_truncated` (BOOLEAN DEFAULT FALSE), `raw_query_sanitized` (BOOLEAN DEFAULT FALSE), `raw_query_sha256` (CHAR(64) NOT NULL), `ip_address`, `received_at`, `vnp_txn_ref` (VARCHAR(100) NULL), `vnp_transaction_no` (VARCHAR(100) NULL), `vnp_amount_text` (TEXT NULL), `verification_status` (CHECK 5 trạng thái), `processing_status` (CHECK 4 trạng thái), `processing_error` (VARCHAR(200) NULL); kèm đầy đủ 4 ràng buộc CHECK toàn vẹn (a)-(d)
  - `CREATE TABLE payment_ipn_events` chỉ lưu callback đã xác thực chữ ký và liên kết attempt: `event_id`, `receipt_id BIGINT NOT NULL UNIQUE REFERENCES payment_ipn_raw_receipts(receipt_id) ON DELETE RESTRICT`, `attempt_id BIGINT NOT NULL REFERENCES payment_attempts(attempt_id) ON DELETE RESTRICT`, `order_id`, `payment_id`, `txn_ref`, `vnp_transaction_no`, `vnp_response_code`, `vnp_transaction_status`, `vnp_curr_code`, `vnp_amount_text`, `vnp_amount_raw`, `vnp_amount`, `vnp_bank_code`, `vnp_bank_tran_no`, `vnp_card_type`, `vnp_pay_date`, `ip_address`, `processing_outcome`, `received_at`; bỏ cột `raw_query_params`; CHECK outcome đầy đủ 11 nhánh; CHECK amount conditional cho phép NULL khi outcome là `REJECTED_AMOUNT_MISMATCH`
  - `CREATE TABLE payment_review_audits` với `ON DELETE RESTRICT` để lưu vết thẩm định đơn `UNDER_REVIEW` (`order_id`, `payment_id`, `attempt_id`, `decided_by`, `decision`, `reason_note`, `vnp_verification_ref`)
  - `CREATE TABLE payment_reconciliation_cases` lưu hàng đợi đối soát: `case_id`, `order_id`, `payment_id`, `attempt_id` (NULL), `case_type`, `status` (OPEN/RESOLVED), `source_type` (IPN_EVENT/REVIEW_DECISION), `event_id` (NULL REFERENCES payment_ipn_events ON DELETE RESTRICT), `review_audit_id` (NULL REFERENCES payment_review_audits ON DELETE RESTRICT), `vnp_transaction_no` (NULL), `dedup_key VARCHAR(150) NOT NULL UNIQUE`, `assigned_to`, `resolution_note`, timestamps; kèm 3 ràng buộc CHECK (case_type, source_fk khớp source_type, tổ hợp case_type và source_type)
  - `CREATE TABLE payment_alert_requests` outbox cảnh báo: `alert_id`, `case_id BIGINT NOT NULL UNIQUE REFERENCES payment_reconciliation_cases(case_id) ON DELETE RESTRICT`, `alert_type`, `severity`, `status` (PENDING/SENT/FAILED), `attempt_count`, `last_error`, `created_at`, `sent_at`
  - Phân quyền Database: Cấp `INSERT, SELECT` cho user ứng dụng trên `payment_ipn_events`; thu hồi `UPDATE, DELETE` (bảo toàn append-only); chính sách lưu trữ kiểm toán nội bộ (retention) 3–5 năm
  - **Files:** `backend/src/main/resources/db/migration/V12__add_vnpay_attempts.sql`
  - **Phụ thuộc:** `V1` đến `V11`
- [x] **BE-02**: Tạo Entity, Repository, Parser & Method Chuyển Trạng Thái Entity:
  - Cập nhật `OrderEntity.java`: Thêm `STATUS_UNDER_REVIEW = "UNDER_REVIEW"`, thêm method `markUnderReview(Instant now)` (từ `PAYMENT_PENDING`), `approveFromReview(Instant now)` (từ `UNDER_REVIEW`), `cancelFromReview(String reason, Instant now)` (từ `UNDER_REVIEW`); ném `IllegalStateException` khi trạng thái nguồn sai
  - Cập nhật `PaymentEntity.java`: Thêm `METHOD_VNPAY = "VNPAY"`, `STATUS_UNDER_REVIEW = "UNDER_REVIEW"`, thêm method `markUnderReview(String txnCode, Instant now)` chỉ chuyển Payment từ `PENDING` (gán `paidAt = now`, `heldAt = null`), `approveFromReview(Instant now)` (gán `status = HELD, heldAt = now`), `markRefundPending(BigDecimal refundAmount, String reason, Instant now)` (chấp nhận từ `HELD` hoặc `UNDER_REVIEW`); ném `IllegalStateException` khi trạng thái nguồn sai. Khi Payment đã `UNDER_REVIEW`, Nhánh B không gọi lại method này.
  - Xây dựng `VnPayQueryParser.java`: Đọc `getQueryString()` 1 lần, làm sạch lone surrogate (thay bằng `?`, bật `raw_query_sanitized`), tính code point và cắt chuỗi nếu vượt 4096 (bật `raw_query_truncated`), tính SHA-256; trả về map tham số hoặc mã lỗi cố định (`NON_ASCII_RAW_QUERY`, `RAW_QUERY_TOO_LARGE`, `INVALID_PERCENT_ENCODING`, `EMPTY_PARAM_NAME`, `DUPLICATE_VNP_PARAM:<tên>`, `NONCANONICAL_VNP_PARAM_CASE:<tên>`)
  - Xây dựng `IpnReceiptService.java`: Service riêng chạy `REQUIRES_NEW` để INSERT raw receipt ở trạng thái ban đầu và UPDATE khi từ chối mà không bị rollback bởi transaction nghiệp vụ
  - Tạo Entity và Repository cho 6 bảng mới: `PaymentAttemptEntity` (kèm method khóa bi quan `findByTxnRefForUpdate`), `PaymentIpnRawReceiptEntity`, `PaymentIpnEventEntity`, `PaymentReviewAuditEntity`, `PaymentReconciliationCaseEntity`, `PaymentAlertRequestEntity`
  - **Files:**
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/infrastructure/persistence/OrderEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/VnPayQueryParser.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/IpnReceiptService.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentAttemptEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentAttemptRepository.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentIpnRawReceiptEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentIpnRawReceiptRepository.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentIpnEventEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentIpnEventRepository.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentReviewAuditEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentReviewAuditRepository.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentReconciliationCaseEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentReconciliationCaseRepository.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentAlertRequestEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/infrastructure/persistence/PaymentAlertRequestRepository.java`
  - **Phụ thuộc:** BE-01
- [x] **BE-03**: Cập nhật `VnPayService.java` dùng `Clock` bean:
  - Inject `Clock` và khai báo timezone `Asia/Ho_Chi_Minh`
  - Sinh `vnp_CreateDate` và `vnp_ExpireDate` (+15 phút theo cấu hình O.G-Shop) chuẩn định dạng `yyyyMMddHHmmss`
  - Cập nhật `VnPayServiceTest.java` với clock cố định
  - **Files:**
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/VnPayService.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/payment/application/VnPayServiceTest.java`
  - **Phụ thuộc:** Không
- [x] **BE-04**: Refactor API Tạo Link Thanh Toán `POST /api/v1/payments/vnpay-url`:
  - Request body chỉ nhận `{ "orderId": Long }`
  - Kiểm tra JWT, lấy `buyerId`, kiểm tra quyền sở hữu đơn hàng, kiểm tra `PAYMENT_PENDING`, chưa quá hạn 1 giờ
  - Khóa theo thứ tự cố định: `Order → Payment → PaymentAttempt`
  - Khởi tạo hoặc lấy `PaymentEntity` hợp lệ với `paymentMethod = "VNPAY"` và status `PENDING`
  - Nếu có attempt `PENDING` cũ -> chuyển sang `SUPERSEDED`
  - Tạo attempt mới `OG_{orderId}_{attemptTimestamp}` với status `PENDING`, lưu vào DB
  - Sinh URL VNPay với số tiền lấy từ `order.getTotalAmount()`
  - **Files:**
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentController.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentDtos.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/PaymentService.java`
  - **Phụ thuộc:** BE-02, BE-03
- [x] **BE-05**: Xử lý Webhook IPN `GET /api/v1/payments/vnpay-ipn` với Khóa Bi Quan Theo Thứ Tự Cố Định:
  - Bỏ `@RequestParam Map` và `request.getParameter*`; gọi `VnPayQueryParser` phân tích query đúng 1 lần từ `request.getQueryString()`
  - INSERT raw receipt trước mọi quyết định từ chối qua `IpnReceiptService` (`REQUIRES_NEW`); nếu vi phạm cấu trúc query -> UPDATE receipt sang `(MALFORMED_REQUEST, PROCESSED, mã_lỗi)` và trả mã `97` (🧪)
  - Nếu query hợp lệ: Điền 3 cột tra cứu forensic trên receipt (bỏ giá trị chứa NUL `%00`) qua transaction ngắn; kiểm tra chữ ký HMAC-SHA512 (sai -> UPDATE receipt sang `SIGNATURE_INVALID, PROCESSED`, trả `97`)
  - Parse `orderId` từ `txnRef` và khóa bi quan trong transaction nghiệp vụ: `Order → Payment → PaymentAttempt` (không tìm thấy attempt -> UPDATE receipt sang `SIGNATURE_VALID_UNLINKED, PROCESSED`, trả `01`)
  - **Bước kiểm tra 1 (Amount & Currency Mismatch):** So sánh số tiền nguyên `vnp_Amount` với `attempt.amount * 100` và currency với `attempt.currency`. Nếu sai lệch:
    + Amount sai -> ghi event `REJECTED_AMOUNT_MISMATCH`. Currency sai -> ghi event `REJECTED_CURRENCY_MISMATCH` (ưu tiên amount nếu sai cả hai)
    + Tăng `attempt.ipn_count`, cập nhật `first/last_ipn_received_at`
    + Nếu callback báo đã thu tiền hoặc nghi ngờ (`00/00` hoặc `07`): Tạo 1 case `MISMATCH_CHARGE` + 1 alert request (khử trùng qua `dedup_key`). Nếu callback báo thất bại mà sai amount/currency: chỉ ghi event `REJECTED_*`, không tạo case/alert
    + Tuyệt đối KHÔNG đổi trạng thái hay trường bằng chứng của Order/Payment/Attempt (không dùng `FLAGGED_SUSPICIOUS`, không đưa vào `UNDER_REVIEW`, không trả `00`)
    + UPDATE receipt sang `(LINKED, PROCESSED)`
    + COMMIT transaction nghiệp vụ thành công rồi mới ánh xạ mã phản hồi: Amount sai trả mã `04` (🧪), Currency sai trả mã `99` (🧪); tuyệt đối cấm ném exception làm rollback mất audit
  - **Bước kiểm tra 2 (Terminal Immutability):** Nếu attempt đã có kết quả cuối:
    - Trước khi phân loại callback thành công muộn/mâu thuẫn, so transaction number với các event thành công đã ghi nhận cho attempt. Cùng `vnp_TransactionNo` (hoặc cả hai cùng thiếu mã theo `NO_TXN_NO`) -> event `DUPLICATE_ACKNOWLEDGED`, tăng `ipn_count`, cập nhật receipt, trả `02` (🧪), không tạo case/alert mới
    - Callback `00/00` có transaction number mới khi attempt vẫn `FAILED` -> giữ nguyên trạng thái, ghi event `CONFLICT_CHARGE_REQUIRES_RECONCILIATION`, tạo case `LATE_SUCCESS_ON_FAILED` riêng theo transaction number mới và alert request nếu case mới, tăng `ipn_count`, cập nhật receipt, trả `02` (🧪)
    - Callback `00/00` có transaction number mới khi attempt đã ghi nhận khoản thu thành công -> giữ nguyên Order/Payment/Attempt, ghi event `CONFLICT_CHARGE_REQUIRES_RECONCILIATION`, tạo case `DUPLICATE_CHARGE` riêng theo transaction number mới và alert request nếu case mới, tăng `ipn_count`, cập nhật receipt, trả `02` (🧪)
    - Callback mâu thuẫn khác không phải khoản thu mới: giữ nguyên trạng thái, ghi event `CONFLICT_IGNORED`, tăng `ipn_count`, trả mã `02` (🧪)
  - **Bước kiểm tra 3 (Rẽ nhánh nghiệp vụ A/B/C):**
    - Nhánh A: Thành công (`00/00`):
      + Đã có khoản thu trước (`Payment` đã thu tiền hoặc có attempt `status IN ('SUCCESS', 'SUCCESS_PENDING_REVIEW', 'FLAGGED_SUSPICIOUS')`): `attempt.status = SUCCESS_DUPLICATE_CHARGE`, ghi event `APPLIED_DUPLICATE_CHARGE`, tạo case `DUPLICATE_CHARGE` + alert request, trả `00`
      + Đơn đang `UNDER_REVIEW`: `attempt.status = SUCCESS_PENDING_REVIEW`, ghi event `APPLIED_PENDING_REVIEW`, giữ `UNDER_REVIEW`, trả `00`
      + Đơn đã hủy hoặc quá hạn 1h: `attempt.status = SUCCESS_ON_EXPIRED_ORDER`, ghi event `APPLIED_EXPIRED_ORDER`, tạo case `EXPIRED_ORDER` + alert request, trả `00`
      + Thành công hợp lệ: `attempt.status = SUCCESS`, chuyển attempt pending khác sang `SUPERSEDED`, gọi `order.markPaidHeld(now)`, `payment.markHeld(vnp_TransactionNo, now)`, ghi event `APPLIED_SUCCESS`, trả `00`
    - Nhánh B: Nghi ngờ gian lận (mã 07): Chỉ xét khi amount/currency hợp lệ (đã vượt qua Bước 1). Có guard chặt chẽ (nếu đơn đã có khoản thu trước -> chuyển nhánh thu trùng; nếu đơn đã hủy/quá hạn -> chuyển nhánh đơn hết hạn). Nếu Order=`PAYMENT_PENDING`, gọi các method chuyển trạng thái Order/Payment sang `UNDER_REVIEW`; nếu Order và Payment đã cùng `UNDER_REVIEW`, không gọi lại method chuyển trạng thái và giữ nguyên dữ liệu review. Cả hai trường hợp đều đặt attempt hiện tại thành `FLAGGED_SUSPICIOUS`, ghi event `FLAGGED_SUSPICIOUS`, trả `00`. Nếu trạng thái Order/Payment lệch nhau, rollback nghiệp vụ, receipt `PROCESSING_FAILED`, trả `99` theo Mục 13; không tự sửa trạng thái.
    - Nhánh C: Thất bại / Hủy: Đơn đã có khoản thu trước -> ghi event `APPLIED_FAILED`, trả `02`; đơn pending -> ghi event `APPLIED_FAILED`, giữ Order `PAYMENT_PENDING`, trả `00`
  - Trong cùng transaction nghiệp vụ: Cập nhật Order/Payment/Attempt, tăng `ipn_count`, ghi `payment_ipn_events`, tạo case + alert request, UPDATE receipt sang `LINKED/PROCESSED`
  - Đảm bảo bất biến: `attempt.ipn_count = COUNT(payment_ipn_events)`
  - Xử lý khi INSERT receipt ban đầu thất bại (DB sập): Không chạm bảng nghiệp vụ, trả HTTP 500 hoặc mã retryable (🧪), log ERROR
  - **Files:**
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/PaymentService.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentController.java`
  - **Phụ thuộc:** BE-02, BE-04
- [x] **BE-06**: Xây dựng API Xác minh Return URL `GET /api/v1/payments/vnpay-verify`:
  - Yêu cầu JWT, kiểm tra `buyerId` sở hữu đơn hàng (chống IDOR, sai trả 403)
  - Dùng chung `VnPayQueryParser`: Từ chối ngay nếu query sai cấu trúc, trả DTO `INVALID_SIGNATURE` (hoặc `MALFORMED_REQUEST`), không gọi DB nghiệp vụ
  - Xác thực chữ ký HMAC-SHA512
  - Chỉ đọc trạng thái DB, tuyệt đối không gọi `save()` hay mutation trạng thái đơn hàng
  - Trả về DTO: `SUCCESS`, `PENDING_CONFIRMATION`, `UNDER_REVIEW`, `FAILED`, `INVALID_SIGNATURE`, `NOT_FOUND`, `AMOUNT_MISMATCH`
  - **Files:**
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentController.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/PaymentService.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentDtos.java`
  - **Phụ thuộc:** BE-02, BE-04
- [x] **BE-07**: Xây dựng Nghiệp vụ Thẩm định & Đóng Trạng thái `UNDER_REVIEW` (Admin / KTV Resolution Runbook):
  - Cập nhật `SecurityConfig.java`: Thêm matcher `requestMatchers("/api/v1/admin/payments/**").hasAnyRole("ADMIN", "KTV")` đặt trước `.anyRequest().denyAll()`
  - Triển khai controller `AdminPaymentController.java` endpoint `POST /api/v1/admin/payments/review-decision`
  - Tiếp nhận quyết định:
    + `APPROVED_SAFE`: Gọi `order.approveFromReview(now)` -> `PAID_HELD`, `payment.approveFromReview(now)` -> `HELD`, `attempt.status` giữ nguyên; ghi bản ghi vào `payment_review_audits`
    + `REJECTED_FRAUD`: Gọi `order.cancelFromReview(reason, now)` -> `CANCELLED`, `payment.markRefundPending(amount, reason, now)` -> `REFUND_PENDING`, `attempt.status` giữ nguyên, gọi `catalogCommerceFacade.releaseProductReservation(...)` nhả hàng kho; ghi `payment_review_audits`; tạo 1 case đối soát `REFUND_PENDING` (`dedup_key = REFUND_PENDING:{payment_id}`) + 1 alert request tương ứng trong cùng transaction
  - **Files:**
    - `backend/src/main/java/com/oldbutgold/shop/shared/config/SecurityConfig.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/api/AdminPaymentController.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/PaymentService.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/api/PaymentDtos.java`
  - **Phụ thuộc:** BE-02, BE-05

## Phase 2: Frontend Integration & User Experience
- [x] **FE-01**: Cập nhật API Client & Types:
  - Thêm `createVnPayUrl(orderId)` và `verifyVnPayReturn(params)` vào `frontend/src/features/payment/paymentApi.ts` (gửi kèm JWT Bearer token)
  - Khai báo kiểu dữ liệu phản hồi xác minh trong `types.ts`
  - **Files:**
    - `frontend/src/features/payment/paymentApi.ts`
    - `frontend/src/features/payment/types.ts`
  - **Phụ thuộc:** BE-04, BE-06
- [x] **FE-02**: Tích hợp Lựa chọn VNPay vào `PaymentView` & Cô lập Mock:
  - Thêm phương thức "Cổng VNPAY (QR Code / Thẻ ATM / Visa)" trong `PaymentView.tsx`
  - Xử lý nút "Thanh toán qua VNPAY": gọi API lấy link và redirect `window.location.href = paymentUrl`
  - Đóng gói các nút mock thanh toán vào điều kiện `DEV`
  - **Files:** `frontend/src/features/payment/components/PaymentView.tsx`
  - **Phụ thuộc:** FE-01
- [x] **FE-03**: Xây dựng Route & Component Tiếp nhận Kết quả Return URL:
  - Khai báo route `/payment/vnpay-return` trong `App.tsx`
  - Tạo page wrapper `pages/VnPayReturnPage.tsx`
  - Tạo feature component `features/payment/components/VnPayReturnView.tsx`:
    - Gọi API verify các params từ VNPay
    - Hiển thị giao diện thành công (Ký quỹ Escrow an toàn)
    - Hiển thị giao diện đang rà soát an toàn (`UNDER_REVIEW`) khi gặp mã 07
    - Xử lý tình huống Return đến trước IPN: hiển thị "Đang xác nhận kết quả thanh toán...", tự động polling mỗi 2 giây (tối đa 5 lần) kèm nút kiểm tra lại thủ công
    - Hiển thị giao diện thất bại kèm nút "Thử lại thanh toán" dẫn về `/checkout/payment?orderId=...`
  - **Files:**
    - `frontend/src/pages/VnPayReturnPage.tsx`
    - `frontend/src/features/payment/components/VnPayReturnView.tsx`
    - `frontend/src/app/App.tsx`
  - **Phụ thuộc:** FE-01

## Phase 3: Automated Testing, Edge Cases & Verification
- [x] **TEST-01**: Mở rộng Test Suite Backend (Unit & Integration Tests Với PostgreSQL/Testcontainers):
  - **Quy tắc bắt buộc:** Phải sử dụng cơ sở dữ liệu thực (PostgreSQL qua Testcontainers) để kiểm thử khóa bi quan, composite foreign key, check constraints, và partial unique index; tuyệt đối không dùng unit test mock để khẳng định khóa DB hoạt động
  - Bổ sung 32 kịch bản kiểm thử bắt buộc:
    1. Kiểm tra `ck_payments_state_fields` cho `UNDER_REVIEW`, chuyển `UNDER_REVIEW -> HELD`, chuyển `UNDER_REVIEW -> REFUND_PENDING` (thỏa mãn khi `held_at IS NULL`)
    2. Duyệt rà soát giữ nguyên trạng thái attempt (`FLAGGED_SUSPICIOUS`), chỉ ghi `payment_review_audits`; nhận diện khoản thu trước vẫn đúng sau duyệt
    3. Ghi raw receipt thành công khi sai chữ ký, thiếu/quá dài `vnp_TxnRef`; chữ ký sai -> `(SIGNATURE_INVALID, PROCESSED)`; unlinked -> `(SIGNATURE_VALID_UNLINKED, PROCESSED)`; không sinh event/case
    4. Ghi receipt hai bước: Sau INSERT 3 cột forensic là NULL; request hợp lệ được điền; ép lỗi điền -> receipt vẫn tồn tại với cột NULL
    5. Receipt trước khi từ chối (Mục 4.1): Ép lỗi ở bước UPDATE từ chối -> receipt còn `UNVERIFIED/RECEIVED`, request vẫn bị từ chối với mã chuẩn, không chạm bảng nghiệp vụ
    6. `vnp_amount_text` giữ nguyên chuỗi nhận được (`12abc`, `1e5`, `-100`, rỗng, thập phân, có `+`, `%20`) với query ASCII hợp lệ
    7. Request chữ ký sai có mã trùng giao dịch thật -> không sinh event/case, không tăng `ipn_count`; `dedup_key` chỉ sinh từ callback đã xác thực
    8. Query chứa non-ASCII chưa percent-encode -> `(MALFORMED_REQUEST, PROCESSED, NON_ASCII_RAW_QUERY)`, không parse, không chạm bảng nghiệp vụ, trả `97`; query percent-encode (`Thanh%20to%C3%A1n`) xử lý bình thường; ký tự điều khiển/NUL -> `NON_ASCII_RAW_QUERY`
    9. Emoji ghi thành công, CHECK độ dài đúng cả Java codePoint và DB `char_length`; lone surrogate thay bằng `?`, đặt `sanitized=true`, `NON_ASCII_RAW_QUERY`, không lỗi INSERT
    10. Query đúng 4096 code points xử lý bình thường; vượt 1 code point -> `(MALFORMED_REQUEST, PROCESSED, RAW_QUERY_TOO_LARGE)`, `truncated=true`, lưu đúng 4096 code points, hash tính trên chuỗi đầy đủ; INSERT ban đầu ở `UNVERIFIED/RECEIVED` với `truncated=true` thành công
    11. DB CHECK từ chối: UPDATE receipt `truncated=true` hoặc `sanitized=true` sang `LINKED`; receipt có ký tự ngoài ASCII in được ở `LINKED`; INSERT `truncated=false` mà độ dài lưu khác `original_length`; query vượt 8192 code points
    12. Query ASCII hợp lệ có `%00` trong giá trị giải mã -> receipt ghi được, `vnp_amount_text` NULL, `raw_query_params` giữ `%00`
    13. Ép lỗi DB sập ở bước INSERT receipt ban đầu -> không xử lý nghiệp vụ, trả HTTP 500 / mã retryable (🧪), không sinh event
    14. Parser: Giá trị chứa dấu `=` (base64) giữ nguyên; cặp không có `=` có giá trị rỗng; `&&` bỏ qua; tên rỗng -> `EMPTY_PARAM_NAME`; `%C3%28`, `%zz` -> `INVALID_PERCENT_ENCODING`; `vnp%5FAmount` giải mã thành `vnp_Amount`
    15. Tham số trùng tên (`vnp_Amount` + `vnp_amount`) -> `DUPLICATE_VNP_PARAM:<tên>`; tên đứng riêng sai kiểu chữ (`VNP_Amount`, `vnp_amount`) -> `NONCANONICAL_VNP_PARAM_CASE:<tên>`
    16. Sai Amount có chữ ký hợp lệ (callback 00/00 hoặc 07) -> Event `REJECTED_AMOUNT_MISMATCH`, receipt `(LINKED, PROCESSED)`, `ipn_count` +1, Order/Payment/Attempt không đổi (không sang `UNDER_REVIEW`, không gán `FLAGGED_SUSPICIOUS`), đúng 1 case `MISMATCH_CHARGE` + 1 alert request, commit trước khi trả mã `04` (không trả `00`); event tồn tại sau khi trả mã (chứng minh không bị rollback)
    17. Amount không parse được / âm / tràn / không phải bội 100 -> Event `REJECTED_AMOUNT_MISMATCH` với `vnp_amount_raw/vnp_amount` NULL, lưu `vnp_amount_text`; DB từ chối event NULL amount với outcome `APPLIED_*`
    18. Sai Currency (`vnp_CurrCode=USD`, callback 00/00 hoặc 07) -> Event `REJECTED_CURRENCY_MISMATCH`, tạo case + alert, trả mã `99` (không đổi trạng thái, không gán `FLAGGED_SUSPICIOUS`, không trả `00`); thiếu `vnp_CurrCode` -> dùng currency attempt, không mismatch
    19. Callback thất bại sai amount -> Event `REJECTED_*`, không tạo case/alert, trạng thái không đổi
    20. Sai amount trên attempt đã Terminal -> Event `REJECTED_*`, trạng thái attempt không đổi
    21. Sai amount gửi 3 lần -> 3 receipt, 3 event `REJECTED_*`, đúng 1 case, đúng 1 alert request, `ipn_count = 3 = COUNT(events)`
    22. Rollback nghiệp vụ (Lựa chọn A) -> receipt ở `PROCESSING_FAILED` hoặc `RETRYABLE_ERROR`, không có event; gửi lại thành công -> đúng 1 event áp dụng, đúng 1 case, 2 receipt liên kết qua `vnp_txn_ref`; gửi lần ba -> thêm 1 event `DUPLICATE_ACKNOWLEDGED`
    23. N lần gọi lặp thành công muộn sau FAILED gửi 3 lần -> 3 receipt, 3 event (1 `CONFLICT_CHARGE_REQUIRES_RECONCILIATION` + 2 `DUPLICATE_ACKNOWLEDGED`), đúng 1 case `LATE_SUCCESS_ON_FAILED`, đúng 1 alert request, `ipn_count = 3`
    24. Cùng attempt nhưng khác `vnp_TransactionNo` báo thành công -> event `CONFLICT_CHARGE_REQUIRES_RECONCILIATION`, case riêng theo mã mới (`LATE_SUCCESS_ON_FAILED` nếu attempt vẫn `FAILED`; `DUPLICATE_CHARGE` nếu attempt đã có khoản thu thành công) và alert request khi case mới; lặp lại mã mới -> event `DUPLICATE_ACKNOWLEDGED`, không tạo case/alert thêm; `ipn_count = COUNT(events)` đúng. Thiếu mã -> dùng `NO_TXN_NO` và gom chung case
    25. KTV duyệt `REJECTED_FRAUD` -> đúng 1 case `source_type=REVIEW_DECISION`, `review_audit_id` có giá trị, `event_id` NULL, kèm 1 alert request; duyệt lần hai trên đơn đã hủy bị chặn bởi kiểm tra trạng thái
    26. CHECK của Case: DB từ chối: `IPN_EVENT` thiếu `event_id`; có cả `event_id` lẫn `review_audit_id`; `REFUND_PENDING` với `source_type=IPN_EVENT`; case bất thường với `source_type=REVIEW_DECISION`; trùng `dedup_key`
    27. Quan hệ Receipt <-> Event: DB từ chối INSERT 2 event cùng `receipt_id`; từ chối `receipt_id` NULL hoặc không tồn tại
    28. Bất biến `ipn_count = COUNT(events của attempt)` đúng trong mọi kịch bản
    29. Quét receipt kẹt bằng `SELECT ... FOR UPDATE SKIP LOCKED` không gây tranh chấp với transaction IPN đang lock; quét không phát sinh khóa trên Order/Payment/Attempt
    30. Nhận mã 07 khi đơn đã `PAID_HELD`, `CANCELLED` hoặc quá hạn -> không kéo ngược trạng thái; khi Order và Payment đã cùng `UNDER_REVIEW`, không gọi lại transition methods, giữ dữ liệu review và ghi event cho attempt hiện tại; trạng thái Order/Payment chỉ lệch một bên thì xử lý như vi phạm invariant, không tự sửa
    31. Security: Endpoint `/api/v1/admin/payments/**` trả 401 khi chưa đăng nhập; 403 khi đăng nhập role Buyer/Seller; 200 khi đăng nhập role ADMIN/KTV
    32. Ràng buộc toàn vẹn xóa: DB chặn xóa attempt khi đã có IPN event (`ON DELETE RESTRICT`); user ứng dụng không thể chạy `UPDATE` hoặc `DELETE` trên `payment_ipn_events`
  - **Files:**
    - `backend/src/test/java/com/oldbutgold/shop/modules/payment/application/PaymentServiceTest.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/payment/api/PaymentControllerTest.java`
  - **Phụ thuộc:** BE-01 đến BE-07
- [ ] **TEST-02**: Kiểm thử Toàn trình Sandbox (E2E Integration Verification):
  - Setup ngrok tunnel trỏ vào cổng 8080 và cấu hình vào Merchant Portal của VNPay Sandbox
  - Đặt hàng mới -> Chọn VNPay -> Thanh toán bằng thẻ test Sandbox (`9704198526191432198`) -> Nhận IPN cập nhật DB -> Trở về trang Return URL hiển thị thành công -> Đơn hàng hiển thị `PAID_HELD` trong danh sách đơn mua
  - Kiểm tra frontend build không có lỗi TypeScript hay linting (`npm run build`)
  - **Phụ thuộc:** BE-01 đến BE-06, FE-01 đến FE-03, TEST-01
