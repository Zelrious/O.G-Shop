# Danh sách Công việc (Todo List): Luồng Trả hàng & Tranh chấp Nhiều Vòng

> Tài liệu theo dõi tiến độ chi tiết của `tasks/complaint_plan.md` dựa trên Báo cáo đồ án (`Báo_cáo_đồ_án.docx`: UC22, UC26, UC27, UC33, UC40, UC52, UC62, UC63, UC71, UC75, UC83).

---

## Giai đoạn 1: Backend Domain Models & Persistence Layer
- [x] **Task 1.1: Tạo các Entity JPA và Enum mã lý do (JPQL Smoke Test)**
  - **Mô tả**: Tạo `CaseEntity`, `CaseRoundEntity`, `CaseDecisionEntity`, `CaseEvidenceEntity`, `CaseEventEntity`, `ReturnReasonCode` và test smoke JPQL khởi tạo/biên dịch trên H2 (không kiểm tra schema PostgreSQL thật).
  - **Tiêu chí chấp nhận**:
    - [x] Tạo `CaseEntity`, `CaseRoundEntity`, `CaseDecisionEntity`, `CaseEvidenceEntity`, `CaseEventEntity`, `ReturnReasonCode`.
    - [x] `CaseEntity` hỗ trợ các trạng thái: `OPEN`, `REVIEWING`, `RETURN_APPROVED`, `RETURNING`, `POST_RETURN`, `CLOSED`, `REJECTED`.
    - [x] `CaseRoundEntity` hỗ trợ stages: `RETURN_APPROVAL`, `POST_RETURN_RESOLUTION`.
    - [x] `CaseDecisionEntity` hỗ trợ outcomes: `APPROVE_RETURN`, `REJECT_RETURN`, `SELLER_DECLINED`, `FULL_GOODS_REFUND`, `EXEMPT_GOODS_REFUND`, `SYSTEM_RESOLVED`.
    - [x] `CaseEvidenceEntity` hỗ trợ media types: `IMAGE`, `VIDEO` (<= 60s, <= 50MB), `TEXT` (khớp DB CHECK `case_evidence_media_type_check`).
    - [x] `CaseEventEntity` hỗ trợ đúng 8 event types theo DB CHECK `case_events_event_type_check`: `SUBMITTED`, `SUPPLEMENTED`, `VALID_DAMAGE_REQUEST`, `ESCALATED`, `ASSIGNED`, `COMPENSATION`, `DEADLINE_EXTENSION`, `CLEANUP`.
    - [x] `ReturnReasonCode` enum và parser tiền tố chuẩn hóa được kiểm thử đơn vị thành công (9 tests pass).
    - [x] `CaseEntitiesSmokeTest`: Smoke test JPQL kiểm tra biên dịch và khởi tạo entity trên H2 (chỉ kiểm tra cú pháp JPA & quan hệ, không kiểm tra schema thật hay trigger DB).
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseRoundEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseDecisionEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseEvidenceEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseEventEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/ReturnReasonCode.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/ReturnReasonCodeTest.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseEntitiesSmokeTest.java`

- [x] **Task 1.1b: Kiểm thử tích hợp PostgreSQL thật (og_compat schema & triggers)**
  - **Mô tả**: Kiểm thử tích hợp thực tế trên PostgreSQL theo mẫu `ShipmentPostgresTest` / `VouchersPostgresTest`, sử dụng `PostgresTestSafetyGuard` và `@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")`. Cổng DB container thực tế là 55432 (URL: `jdbc:postgresql://127.0.0.1:55432/og_shop_test`).
  - **Tiêu chí chấp nhận**:
    - [x] Kết nối DB test an toàn biệt lập qua `PostgresTestSafetyGuard` (`OGSHOP_TEST_DB_ISOLATED=true`, `OGSHOP_TEST_DB_URL`).
    - [x] Đọc SELECT thành công từ cả 5 view: `og_compat.cases`, `og_compat.case_rounds`, `og_compat.case_decisions`, `og_compat.case_evidence`, `og_compat.case_events`.
    - [x] Insert đơn hàng fixture thỏa các điều kiện của `og_guard_return_case` (`workflow_model = 'UC83'`, `buyer_id = buyer`, `return_allowed = true`, `valid_delivered_at` trong hạn 3 ngày, `early_completed_at IS NULL`) và có bản ghi `order_fund_components` (`GOODS_CASH`).
    - [x] Insert hồ sơ mẫu `CaseEntity` và xác nhận trigger `trg_return_case_holds` tự động tạo bản ghi `money_holds` cho hồ sơ.
    - [x] 7 Test âm kiểm chứng đúng ngoại lệ SQLSTATE `23514` của DB trigger: (1) state != OPEN; (2) workflow_model != UC83; (3) created_by != buyer_id; (4) return_allowed = false; (5) valid_delivered_at IS NULL; (6) early_completed_at IS NOT NULL; (7) quá hạn 3 ngày.
    - [x] Ánh xạ khớp hoàn toàn database schema thật `og_compat` (chỉ tích mục này sau khi Task 1.1b pass).
    - [x] Dọn dẹp dữ liệu fixture hoặc tự động rollback trong transaction, không để lại dữ liệu rác.
  - **Files**:
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseEntitiesPostgresTest.java`

- [x] **Task 1.2: Ánh xạ các trường nghiệp vụ UC83 vào `OrderEntity` (Chỉ Đọc) & Cập nhật Repositories**
  - **Mô tả**: Ánh xạ `workflow_model`, `return_allowed`, `valid_delivered_at`, `return_due_at`, `early_completed_at`, `early_completion_warning_version`, `groupId`, `deliveryMethod`, `handoverDay` vào `OrderEntity` ở dạng **CHỈ ĐỌC** (`insertable=false, updatable=false`).
  - **Lý do chỉ đọc**: Đảm bảo 100% không làm thay đổi câu lệnh INSERT của luồng checkout hiện tại; Hibernate tự bỏ qua các cột này khi lưu đơn mới, nhưng đọc được đầy đủ khi load đơn hàng.
  - **Ghi chú rủi ro & Rà soát**:
    * Trong cùng transaction sau khi gọi `orderRepository.save(order)`, các trường read-only trên đối tượng Java `OrderEntity` sẽ có giá trị `null` cho đến khi `refresh()` hoặc reload từ DB (vì Hibernate bỏ qua không insert các cột này, và view/DB trigger tự sinh default).
    * Kết quả rà soát repo (`grep` trên `getWorkflowModel`, `isReturnAllowed`, `getDeliveryMethod`, `getGroupId`...): **Hiện tại KHÔNG CÓ nơi nào trong code đọc lại các trường này ngay sau save()**, do đó rủi ro này an toàn đối với luồng code hiện hữu.
  - **Tiêu chí chấp nhận**:
    - [x] `OrderEntity` ánh xạ đủ 9 trường trên ở dạng `insertable=false, updatable=false`.
    - [x] `CaseRepository` hỗ trợ `findByOrderId` và `findActiveCaseByOrderId`.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/infrastructure/persistence/OrderEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseRepository.java`

- [ ] **Task 1.2b: Nâng cấp luồng Checkout & Payment sang mô hình UC83 (Tiền đề BẮT BUỘC của Giai đoạn 6 UI và E2E)**
  - **Mô tả**: Tiền đề bắt buộc để Giai đoạn 6 (UI) và bước kiểm thử End-to-End (E2E) tạo được đơn hàng UC83 thật từ màn hình mua hàng. Cho tới khi hoàn thành, E2E chỉ chạy bằng dữ liệu fixture/script. (CHƯA LÀM LÚC NÀY).
  - **Checklist gỡ cờ read-only trên `OrderEntity` (Bắt buộc thực hiện ở Task 1.2b)**:
    - [ ] Gỡ `insertable=false` trên `OrderEntity` cho các cột: `workflow_model`, `group_id`, `delivery_method`, `handover_day`, `return_allowed`.
  - **Các việc con (Subtasks đầy đủ theo `ck_orders_amounts`, `ck_order_items_prices`, `og_validate_checkout_aggregate`)**:
    - [ ] 1. Tạo bản ghi `checkout_groups` + liên kết `orders.group_id`. Bắt buộc `orders.group_id = orders.checkout_group_id` (thỏa mãn DB CHECK `ck_order_uc83_snapshot`).
    - [ ] 2. Cập nhật `order_items`: gán `workflow_model = 'UC83'`, bắt buộc `product_revision_id IS NOT NULL` và `return_allowed IS NOT NULL` (thỏa mãn DB CHECK `ck_item_uc83_revision`).
    - [ ] 3. Phân bổ voucher và xu ở cấp item (`order_items`), bảo đảm:
      * `voucher_allocation + points_allocation <= agreed_price * quantity`
      * `buyer_line_total = agreed_price * quantity - voucher_allocation - points_allocation`
      * `seller_line_proceeds = agreed_price * quantity`
      * (Thỏa mãn DB CHECK `ck_order_items_prices`).
    - [ ] 4. Ràng buộc cấp order (`orders`), bảo đảm:
      * `seller_proceeds = subtotal`
      * `total_amount = subtotal + shipping_fee - voucher_discount_amount - shipping_discount_amount - points_discount_amount`
      * `total_amount >= 0`
      * `shipping_discount_amount`: lấy từ voucher giảm phí ship (`benefit_component = 'SHIPPING'`), nếu không có voucher ship thì `= 0`; luôn thỏa mãn `0 <= shipping_discount_amount <= shipping_fee`.
      * Khi `return_due_at IS NOT NULL` thì bắt buộc `return_allowed = true` và `return_due_at = valid_delivered_at + '3 days'::interval` (thỏa mãn DB CHECK `ck_order_return_window`).
      * (Thỏa mãn DB CHECK `ck_orders_amounts` và `ck_order_return_window`).
    - [ ] 5. Đảm bảo tổng phân bổ của các items khớp chính xác với chiết khấu cấp order:
      * `orders.subtotal = sum(order_items.agreed_price * quantity)`
      * `orders.voucher_discount_amount = sum(order_items.voucher_allocation)`
      * `orders.points_discount_amount = sum(order_items.points_allocation)`
      * (Ràng buộc DB Trigger: `og_validate_checkout_aggregate()` / `trg_group_aggregate`, `trg_order_aggregate`, `trg_item_aggregate` tại V17 dòng 200-235).
    - [ ] 6. Tạo bản ghi giữ chỗ kho `inventory_reservations` cho từng item khớp số lượng (yêu cầu của trigger `og_validate_checkout_aggregate()`).
    - [ ] 7. Áp dụng phí UC83 bằng 0: `buyer_system_fee = 0`, `seller_system_fee = 0`, `fee_policy_id = NULL`, `sponsor_type = 'PLATFORM'`.
    - [ ] 8. Phí vận chuyển theo `delivery_method`: nếu `delivery_method <> 'CARRIER'` (ví dụ `SELF_PICKUP`, `SELF_DELIVERY`) thì bắt buộc `shipping_fee = 0` (thỏa mãn `ck_orders_amounts`). Nếu là `CARRIER`, tính phí GHN bình thường.
    - [ ] 9. Chuỗi thanh toán `payment_intents` -> `attempts` -> `confirmations` -> `allocations`.
    - [ ] 10. Tự động tạo `order_fund_components` (`GOODS_CASH`, `PLATFORM_GOODS_SUBSIDY`, `SHIPPING_CASH`, `PLATFORM_SHIPPING_SUBSIDY`) theo công thức `og_guard_fund_component`.
  - **Rủi ro**: Phá vỡ luồng mua hàng và VNPay hiện hành; vi phạm các ràng buộc DB chéo (`ck_orders_amounts`, `ck_order_items_prices`, `og_guard_fund_component`, `og_validate_checkout_aggregate`); lệch đồng bộ trạng thái đơn.

---

## Checkpoint 1: Domain Entities & JPA Mapping
- [x] Biên dịch sạch sẽ: `cd backend; .\mvnw.cmd test-compile`
- [x] Chạy smoke test H2: `cd backend; .\mvnw.cmd test "-Dtest=ReturnReasonCodeTest,CaseEntitiesSmokeTest"`
- [x] Chạy Postgres test (khi bật DB test): `cd backend; .\mvnw.cmd test "-Dtest=CaseEntitiesPostgresTest"`
- [x] [Đề xuất Phía User] Chạy kiểm thử xác minh luồng checkout hiện hành không bị ảnh hưởng bởi Task 1.2:
  * Test đề xuất: `LegacyCheckoutPersistencePostgresTest` (xác minh `CheckoutService.buyNow()` lưu đơn thành công; bắt buộc gọi `entityManager.flush() + clear()` trước `findById` để xóa L1 cache; assert reloaded entity có `workflow_model = 'LEGACY_V14'`, 8 trường read-only còn lại nhận giá trị `null`).

> **Baseline quality gate hiện tại**: `679 run / 212 skipped / 0 fail` (trong đó 212 skip chủ yếu là test Postgres bị gate bởi biến môi trường `OGSHOP_TEST_DB_*`).

---

## Giai đoạn 2: Tách Hoàn tất sớm (UC27) & Nghiệp vụ Vòng 1 (UC62 & UC75)
- [x] **Task 2.1: Refactor `earlyCompleteOrder`, `confirmReceived` & Xử lý Endpoint `/complete` cũ (UC27)**
  - **Mô tả**:
    * Nút "Đã nhận hàng" (`confirmReceived`): Ghi nhận `orders.received_at = now()`. Idempotent: gọi lần hai không ghi đè `received_at` ban đầu. Không đổi trạng thái giải ngân, không đóng quyền trả hàng, không gán lại `valid_delivered_at`. Ánh xạ `received_at` vào `OrderEntity` như trường ghi thường (`@Column(name = "received_at")`, getter/setter).
    * Nút "Hoàn tất sớm" (`earlyCompleteOrder`): Yêu cầu cờ boolean `warningConsent: true` (server gắn hằng số `EARLY_COMPLETION_WARNING_VERSION = "v1.0"`). Gọi hàm DB `og_early_complete_order(orderId, buyerId, warningVersion, commandKey)`. Hàm này CHỈ ghi nhận mốc `early_completed_at` và sự kiện `EARLY_COMPLETION_ACCEPTED`, KHÔNG trực tiếp đổi trạng thái giải ngân và KHÔNG đóng đơn COMPLETED (UC71 3a).
    * Stale Entity: Sau khi gọi `og_early_complete_order`, mọi logic đọc `early_completed_at` trong cùng transaction phải `entityManager.refresh(order)` hoặc query DB do cột này là `updatable = false`.
    * Endpoint `/complete` cũ: Giữ nguyên hành vi cho đơn `LEGACY_V14`; nếu đơn là `UC83` ném lỗi nghiệp vụ rõ ràng (HTTP 400) hướng dẫn dùng `/confirm-received` hoặc `/early-complete`.
    * KHÔNG giải ngân UC83 trong Task 2.1 (đã tách sang Task 2.1b).
  - **Tiêu chí chấp nhận**:
    - [x] Ánh xạ `received_at` vào `OrderEntity` như trường ghi thường (`@Column(name="received_at")`, getter/setter).
    - [x] Chặn Hoàn tất sớm nếu đơn hàng đang có hồ sơ mở (`og_order_has_blocking_case(orderId) == true`) hoặc thiếu consent (`warningConsent = false`).
    - [x] Khi Hoàn tất sớm thành công: Chỉ ghi nhận mốc `early_completed_at`, tạo `order_events`, đơn VẪN Ở TRẠNG THÁI `DELIVERED`, tiền VẪN CHƯA GIẢI NGÂN.
    - [x] `confirmReceived` idempotent: không ghi đè `received_at` đầu tiên, không đổi trạng thái đơn, không giải ngân.
    - [x] Endpoint `/complete` cũ giữ nguyên cho `LEGACY_V14`, trả lỗi nghiệp vụ rõ ràng khi gọi với đơn `UC83`.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/domain/OrderEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/application/OrderManagementService.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/api/OrderController.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/application/OrderManagementDtos.java`

- [x] **Task 2.1b: Tiến trình Giải ngân cho Người bán (`disburseSellerFunds` - UC71)**
  - **Mô tả**:
    * Xử lý giải phóng tiền và đóng đơn sang `COMPLETED` khi đủ 3 điều kiện BR-UC71-04:
      (1) Đơn hàng đã giao thành công hợp lệ (`valid_delivered_at` có giá trị);
      (2) Hết 3 ngày từ `valid_delivered_at` HOẶC có mốc `early_completed_at`;
      (3) Không có hồ sơ ngăn chặn (`og_order_has_blocking_case = false`) và không có `money_holds` chưa đóng.
    * Nhánh `LEGACY_V14`: Giải ngân qua `PaymentEntity.markReleased(now)`, cập nhật `orders.status = 'COMPLETED'`.
    * Nhánh `UC83`: Giải ngân qua `settlement_operations` (`operation_type = 'RELEASE_SELLER'`, `cause_type = 'COMPLETION'`) tác động `order_fund_components`, không dùng `payment.markReleased`.
    * Thứ tự khóa tài nguyên (Lock Order Invariant): `orders` (1) -> `order_fund_components` (2) -> `settlement_operations` (3) (theo DB trigger `og_guard_settlement_operation` tại V18 dòng 269-270).
    * Thiết kế kỹ thuật chi tiết theo DB Schema V18/V22:
      a. **Chuyển state `settlement_operations`**: Service INSERT với `state = 'REQUESTED'` (bắt buộc theo V18:271), sau đó Service (hoặc Worker mô phỏng) UPDATE sang `state = 'CONFIRMED'` với `confirmed_at = now()`, `provider = 'INTERNAL_SIMULATED'`, `provider_operation_id = 'SIM-RELEASE-' + operation_id` (V18:258, V22:9284).
      b. **Nguồn `amount` và trigger cập nhật `released_amount`**: `amount` lấy từ số dư khả dụng của từng component (`confirmed_amount - released_amount - refunded_amount - reserved_amount`). Khi UPDATE sang `CONFIRMED`, trigger `trg_settlement_operation_guard` (`og_guard_settlement_operation`, V18:316-320) tự động cộng `released_amount` trên `order_fund_components`. Java cấm update trực tiếp `order_fund_components` do ràng buộc trigger depth (V18:340).
      c. **Số lượng operation & quy tắc `command_key`**: Bắt buộc tạo 2 bản ghi `settlement_operations` riêng biệt cho 2 components `GOODS_CASH` và `PLATFORM_GOODS_SUBSIDY` do `component_id` là khóa ngoại đơn 1-1 (V18:243-244). Quy tắc `command_key` ổn định để idempotent: `"DISBURSE:SELLER:" + orderId + ":" + componentType + ":COMPLETION"` (V18:256).
      d. **Chuyển trạng thái đơn sang `COMPLETED`**: DB KHÔNG có trigger tự động đóng đơn sang `COMPLETED`. Sau khi toàn bộ các component hàng được giải ngân, chính Service phải gọi `order.markCompleted(now)` và lưu đơn hàng.
  - **Tiêu chí chấp nhận**:
    - [x] Kiểm tra chặt chẽ đủ 3 điều kiện theo BR-UC71-04 trước khi giải ngân. (Pass Unit & Postgres tests)
    - [x] Với đơn `LEGACY_V14`: chuyển đơn `COMPLETED`, `payment.markReleased()`. (Pass Unit & Postgres tests)
    - [x] Với đơn `UC83`: tạo `settlement_operations` riêng cho từng component hàng, chuyển `CONFIRMED`, trigger DB tự tăng `released_amount`. (Pass Unit & Postgres tests)
    - [x] Service đóng đơn sang `COMPLETED` sau khi giải ngân thành công. (Pass Unit & Postgres tests)
    - [x] Đảm bảo đúng thứ tự lock và idempotent theo `command_key`. (Pass Unit & Postgres tests)
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/application/OrderManagementService.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/application/OrderManagementDtos.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/api/AdminPaymentController.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/commerce/application/OrderManagementServiceTest.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/commerce/application/SellerDisbursementPostgresTest.java`
  - **Phụ thuộc**: Task 2.1 xong trước.
  - **Chặn**: Task 3.3 (Worker auto-disburse) và Task 4.1.

- [ ] **Task 2.2: Triển khai Mở yêu cầu trả hàng đủ 6 điều kiện (`createReturnRequest` - UC62)**
  - **Mô tả**: Kiểm tra đủ 6 điều kiện bắt buộc, khởi tạo hồ sơ `cases` ở trạng thái `OPEN` và Round 1 `RETURN_APPROVAL`.
  - **Tiêu chí chấp nhận**:
    - [ ] Kiểm tra đủ 6 điều kiện: `DELIVERED`, có `valid_delivered_at`, `return_allowed == true`, trong hạn 3 ngày từ `valid_delivered_at`, chưa Hoàn tất sớm, tiền chưa giải ngân cuối, không có hồ sơ mở.
    - [ ] Validate 3 lý do chuẩn: `CHANGE_MIND`, `NOT_AS_DESCRIBED`, `DAMAGED_OR_DEFECTIVE`.
    - [ ] Validate giới hạn bằng chứng: Tối đa 5 ảnh, 2 video <= 60 giây.
    - [ ] Tiền ký quỹ bị đóng băng (trigger `trg_return_case_holds` tự động tạo `money_holds`).
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseDtos.java`

- [ ] **Task 2.3: Triển khai Người bán phản hồi & KTV phân xử Vòng 1 (UC62 & UC75)**
  - **Mô tả**:
    * KTV thấy hồ sơ ngay khi `OPEN`, có thể chủ động duyệt `APPROVE_RETURN` hoặc bác `REJECT_RETURN` (kèm lý do).
    * Người bán có 2 lựa chọn: `[Chấp thuận trả]` (`APPROVE_RETURN`) hoặc `[Không đồng ý, chuyển KTV]` (`SELLER_DECLINED`).
    * Xử lý xung đột đua quyết định (first-write wins).
  - **Tiêu chí chấp nhận**:
    - [ ] Người bán `APPROVE_RETURN` -> Case chuyển `RETURN_APPROVED` (**chưa hoàn tiền**, chờ gửi hàng trả).
    - [ ] Người bán `SELLER_DECLINED` -> Case chuyển `REVIEWING`, hạn KTV 48h tính từ khi hồ sơ đủ nội dung.
    - [ ] KTV duyệt `APPROVE_RETURN` hoặc bác `REJECT_RETURN` (bắt buộc nhập lý do).
    - [ ] KTV bác (`REJECT_RETURN`) -> Hồ sơ chuyển `REJECTED`, **không giải ngân ngay** mà chờ đủ điều kiện UC71 (hết 3 ngày hoặc Hoàn tất sớm).
    - [ ] Quá hạn KTV 48h: Ghi nhận event `ESCALATED`, không tự động duyệt/hoàn tiền.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`

- [ ] **Task 2.4: Triển khai Người mua rút yêu cầu trả hàng (`withdrawReturnRequest` - UC62 7c)**
  - **Mô tả**: Người mua chủ động rút yêu cầu trước khi gửi hàng trả.
  - **Tiêu chí chấp nhận**:
    - [ ] Chỉ cho rút khi hồ sơ ở `OPEN`, `REVIEWING`, `RETURN_APPROVED` và chưa bàn giao bưu tá / chưa gửi hàng.
    - [ ] Đóng lệnh giữ tiền (`money_holds.closed_at = now()`), giải phóng cản trở (`has_blocking_case = false`), tiền tiếp tục xử lý giải ngân theo điều kiện UC71.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`

---

## Giai đoạn 3: Vận chuyển Trả hàng (UC33) & Nghiệp vụ Vòng 2 (UC63 & UC75)
- [ ] **Task 3.1: Xử lý Vận chuyển hàng trả & Ghi nhận nhận lại hàng (UC33)**
  - **Mô tả**: Theo dõi vận đơn trả (`shipments.leg = 'RETURN'`), ghi nhận `valid_returned_at` từ sự kiện vận đơn hoặc OTP/bàn giao hai bên, mở hạn 2 ngày phản hồi của Người bán.
  - **Tiêu chí chấp nhận**:
    - [ ] Hạn gửi hàng trả: Hết 3 ngày hoặc 3 lần lấy thất bại. Lỗi ĐVVC mô phỏng không tự quy kết lỗi Người mua.
    - [ ] Ghi nhận `valid_returned_at` từ webhook giao thành công chiều về hoặc qua endpoint xác nhận bàn giao trực tiếp hai bên (`og_confirm_direct_handover`).
    - [ ] Tự động gán thời hạn phản hồi: `seller_response_due_at = valid_returned_at + 2 days`.
    - [ ] Chuyển trạng thái hồ sơ sang `POST_RETURN`, khởi tạo Round 2 với `stage = 'POST_RETURN_RESOLUTION'`.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`

- [ ] **Task 3.2: Nghiệp vụ Vòng 2 của Người bán (UC63 & UC83)**
  - **Mô tả**: Người bán kiểm tra hàng thực tế với 2 lựa chọn:
    1. `[Hàng đúng, đồng ý hoàn tiền]`: Gọi endpoint `agree-refund` -> kích hoạt hoàn tiền ngay (UC83).
    2. `[Hàng có vấn đề, gửi đề nghị xét lại]`: Gọi endpoint `damage-exemption` (`VALID_DAMAGE_REQUEST` - hỗ trợ hàng hư hỏng nặng HOẶC tráo hàng, sai hàng, thiếu hàng).
  - **Tiêu chí chấp nhận**:
    - [ ] Người bán đồng ý hoàn tiền -> Case chuyển sang giải quyết hoàn tiền theo UC83.
    - [ ] Người bán gửi đề nghị xét lại kèm <= 5 ảnh, 2 video <= 60s -> Tiếp tục giữ tiền, chuyển KTV thụ lý Vòng 2 (SLA 48h).
    - [ ] Người bán không được tự quyết từ chối hoàn tiền; chỉ KTV mới có thẩm quyền miễn hoàn.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`

- [ ] **Task 3.3: Hệ thống Workers tự động định kỳ**
  - **Mô tả**: Worker quét quá hạn 2 ngày tự hoàn tiền (`reconcileOverdueReturnResponses`) và Worker tự giải ngân sau 3 ngày cho đơn `DELIVERED` đủ điều kiện UC71 (`reconcileOverdueDeliveries`).
  - **Phụ thuộc**: Nhánh tự giải ngân phụ thuộc trực tiếp vào **Task 2.1b** (`OrderManagementService.disburseSellerFunds`).
  - **Tiêu chí chấp nhận**:
    - [ ] Hồ sơ `POST_RETURN` quá hạn 2 ngày không có đề nghị hợp lệ -> Tự động ra quyết định `SYSTEM_TIMEOUT` + `FULL_GOODS_REFUND` hoàn 100% tiền hàng.
    - [ ] Đơn `DELIVERED` quá hạn 3 ngày từ `valid_delivered_at`, không có hồ sơ mở -> Tự động gọi `disburseSellerFunds` (Task 2.1b) để giải ngân cho Người bán (áp dụng cho cả đơn `return_allowed = false`).
    - [ ] Đảm bảo tính idempotent khi chạy lại nhiều lần.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnReconciliationScheduler.java`

- [ ] **Task 3.4: KTV phân xử Vòng 2 (UC75)**
  - **Mô tả**: KTV quyết định miễn hoàn tiền (`EXEMPT_GOODS_REFUND`) hoặc bác đề nghị để hoàn tiền (`FULL_GOODS_REFUND`).
  - **Tiêu chí chấp nhận**:
    - [ ] Chỉ KTV mới được quyền quyết định miễn hoàn khi có căn cứ xác thực (hàng hỏng nặng hoặc sai khác hàng đã giao).
    - [ ] Chấp thuận miễn hoàn (`EXEMPT_GOODS_REFUND`): Tiền hàng giải ngân cho Người bán theo BR-UC71-01/05 (tiền hàng thực trả + sàn tài trợ voucher/xu cho hàng; không trừ phí sàn bán; không gồm ship/phí đăng), không hoàn tiền/không trả xu cho Người mua.
    - [ ] Bác đề nghị (`FULL_GOODS_REFUND`): Kích hoạt hoàn 100% tiền hàng thực trả cho Người mua theo UC83.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`

---

## Giai đoạn 4: Hạch toán Hoàn tiền Chuẩn xác (UC83, UC71, UC22)
- [ ] **Task 4.1: Triển khai Service tính toán hoàn tiền (`RefundSettlementService`)**
  - **Mô tả**: Tính tiền mặt hoàn = tiền hàng thực trả sau voucher và xu phân bổ cho hàng. Hoàn 100% xu đã dùng của đơn. Không hoàn phí ship tiền mặt, không hoàn phí đăng tin, không cấp lại voucher. Xử lý đơn 0đ không tạo giao dịch hoàn tiền 0đ.
  - **Phụ thuộc**: Tái sử dụng bảng `settlement_operations` (`operation_type = 'REFUND_BUYER'`) và cơ chế locking / state machine tương tự **Task 2.1b**.
  - **Tiêu chí chấp nhận**:
    - [ ] Trạng thái thanh toán chuyển `REFUND_PENDING` -> `REFUNDED` khi dịch vụ thanh toán xác nhận thành công.
    - [ ] Tái sử dụng bảng `og_compat.settlement_operations` để ghi nhận giao dịch hoàn/giải ngân an toàn, idempotent qua `command_key`.
    - [ ] Khóa tài nguyên theo đúng thứ tự: `OrderEntity` (1) -> `PaymentEntity` (2) -> `CaseEntity` (3).
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/RefundSettlementService.java`

---

## Checkpoint 2: Kiểm thử Tự động Backend (27 Test Cases)
- [ ] Viết bộ test `ReturnCaseServiceTest` và `RefundSettlementTest` phủ đủ 27 test cases trong Test Matrix:
  - [ ] Test chặn khi đơn chưa `DELIVERED`, đơn `return_allowed = false`, quá 3 ngày, hoặc đã hoàn tất sớm.
  - [ ] Test chặn video > 60s hoặc > 5 ảnh.
  - [ ] Test giữ tiền ký quỹ và chặn hoàn tất sớm khi có hồ sơ mở.
  - [ ] Test luồng Vòng 1: Người bán duyệt, Người bán từ chối chuyển KTV, KTV bác kèm lý do không giải ngân ngay.
  - [ ] Test đua quyết định Vòng 1: first-write wins.
  - [ ] Test rút yêu cầu trả hàng trước khi gửi hàng.
  - [ ] Test luồng Vòng 2: Hạn 2 ngày, auto-refund khi quá hạn, đề nghị miễn hoàn do hàng hỏng/tráo hàng, KTV phán quyết.
  - [ ] Test công thức hoàn tiền: Đơn 0 đồng (Ví dụ UC83-07), đơn có ship tiền mặt (voucher hàng), đơn có voucher ship, nhánh `EXEMPT` không trả xu.
  - [ ] Test worker idempotent không hoàn trùng.
- [ ] Chạy test xanh 100%: `cd backend; .\mvnw.cmd test -Dtest=ReturnCaseServiceTest,RefundSettlementTest`

---

## Giai đoạn 5: REST API Controllers & DTOs
- [ ] **Task 5.1: Controller cho Người mua & Người bán (`ReturnCaseController`)**
  - **Endpoints**:
    - `POST /api/v1/commerce/orders/{orderId}/early-complete` (Hoàn tất sớm có popup cảnh báo)
    - `POST /api/v1/commerce/orders/{orderId}/confirm-received` (Xác nhận nhận hàng, không giải ngân)
    - `POST /api/v1/commerce/orders/{orderId}/return-request` (Mở yêu cầu trả hàng)
    - `GET /api/v1/commerce/orders/{orderId}/return-case` (Xem chi tiết hồ sơ trả hàng)
    - `POST /api/v1/commerce/orders/{orderId}/return-case/withdraw` (Người mua rút yêu cầu trước khi gửi hàng)
    - `POST /api/v1/commerce/orders/{orderId}/return-case/seller-response` (Người bán phản hồi Vòng 1)
    - `POST /api/v1/commerce/orders/{orderId}/return-shipment/confirm-direct-handover` (Bàn giao trực tiếp hai bên)
    - `POST /api/v1/commerce/orders/{orderId}/return-case/agree-refund` (Người bán đồng ý hoàn tiền Vòng 2)
    - `POST /api/v1/commerce/orders/{orderId}/return-case/damage-exemption` (Người bán đề nghị xét lại Vòng 2)
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/api/ReturnCaseController.java`

- [ ] **Task 5.2: Controller cho KTV (`KtvCaseController`)**
  - **Endpoints**:
    - `GET /api/v1/ktv/cases` (Queue hồ sơ KTV `OPEN` & `REVIEWING`, cảnh báo SLA 48h)
    - `POST /api/v1/ktv/cases/{caseId}/decide-round1` (KTV phán quyết Vòng 1)
    - `POST /api/v1/ktv/cases/{caseId}/decide-round2` (KTV phán quyết Vòng 2)
    - `POST /api/v1/ktv/cases/{caseId}/request-evidence` (KTV yêu cầu nộp thêm bằng chứng)
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/api/KtvCaseController.java`

- [ ] **Task 5.3: Controller cho Admin Giám sát Tranh chấp (`AdminDisputeController`)**
  - **Endpoints**:
    - `GET /api/v1/admin/cases/escalated` (Danh sách các hồ sơ bị trễ hạn SLA 48h của KTV)
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/api/AdminDisputeController.java`

---

## Giai đoạn 6: Giao diện Người dùng (Frontend)
- [ ] **Task 6.1: Giao diện Người mua (`BuyerOrderDetailView.tsx` & Modals)**
  - **Mô tả**:
    * **Thiết kế UI/UX luồng nhận hàng & hoàn tất sớm**: Giao diện gom "Đã nhận hàng" và "Hoàn tất sớm" thành một luồng ở cùng khu vực đơn `DELIVERED`. Backend giữ hai endpoint riêng biệt (`/confirm-received` và `/early-complete`).
    * **Cơ chế Hoàn tất sớm trên UI**: Nút "Hoàn tất sớm" luôn hiển thị hộp thoại cảnh báo (`EarlyCompleteModal`) và bắt buộc Người mua phải tick chọn đồng ý từ bỏ quyền trả hàng trước khi cho phép bấm xác nhận.
    * **Trường hợp chưa bấm Đã nhận hàng**: Nếu người dùng chưa bấm "Đã nhận hàng" mà bấm "Hoàn tất sớm", UI sẽ tự động gọi API `confirm-received` trước rồi mới gọi `early-complete` nối tiếp (chỉ thực hiện sau khi Người mua đã tick đồng ý cảnh báo). Backend KHÔNG bắt buộc `received_at` cho `early-complete` (stored procedure DB không kiểm tra cột này). Hàm `confirmReceived` idempotent đảm bảo an toàn.
    * Nút "Yêu cầu trả hàng" (kiểm tra 6 điều kiện), nút "Rút yêu cầu", Modal tải bằng chứng, Timeline hồ sơ nhiều vòng.
  - **Files**:
    - `frontend/src/features/orders/components/BuyerOrderDetailView.tsx`
    - `frontend/src/features/orders/components/CreateReturnRequestModal.tsx`
    - `frontend/src/features/orders/components/EarlyCompleteModal.tsx`

- [ ] **Task 6.2: Giao diện Người bán (`SellerOrderDetailView.tsx` & `SellerReturnsView.tsx`)**
  - **Mô tả**: Khối hồ sơ trả hàng ở Vòng 1, màn hình "Hàng trả" theo dõi vận đơn trả (UC33), bộ đếm ngược 2 ngày và 2 lựa chọn Vòng 2: `[Hàng đúng, đồng ý hoàn tiền]` và `[Hàng có vấn đề, gửi đề nghị xét lại]`.
  - **Files**:
    - `frontend/src/features/orders/components/SellerOrderDetailView.tsx`
    - `frontend/src/features/orders/components/SellerReturnsView.tsx`

- [ ] **Task 6.3: Giao diện KTV Workbench (`KtvCasesPage.tsx`)**
  - **Mô tả**: Hàng đợi hồ sơ `OPEN` và `REVIEWING`, cảnh báo SLA 48h màu đỏ, giao diện đối chiếu bằng chứng 2 bên, form nhập phán quyết.
  - **Files**:
    - `frontend/src/features/trustsafety/KtvCasesPage.tsx`

- [ ] **Task 6.4: Giao diện Admin Giám sát Tranh chấp (`AdminDisputesView.tsx`)**
  - **Mô tả**: Danh sách các hồ sơ bị `ESCALATED` do KTV quá hạn 48h để Quản trị viên can thiệp xử lý.
  - **Files**:
    - `frontend/src/features/admin/AdminDisputesView.tsx`

---

## Checkpoint 3: Kiểm thử Tích hợp & Nghiệm thu
- [ ] Frontend build thành công: `npm run build` không lỗi TypeScript.
- [ ] Backend test xanh 100%: 27 test cases pass.
- [ ] E2E Manual Test đầy đủ luồng Người mua -> Người bán -> Vận chuyển trả -> Người bán Vòng 2 -> KTV -> Hoàn tiền.

---

## Ghi chú Các Tính năng Hoãn (Deferred Features)
1. **UC40, UC48, UC52 (Push Notifications / WebSockets)**:
   - Hoãn triển khai socket thời gian thực do yêu cầu hạ tầng riêng. Toàn bộ sự kiện thông báo được ghi vào `case_events` và bảng thông báo hệ thống; giao diện hiển thị trạng thái hồ sơ trực tiếp trong chi tiết đơn hàng.
2. **UC64, UC76 (Khiếu nại Lỗi Hệ thống / Thu tiền ngoài đơn trả hàng)**:
   - Hoãn do thuộc phân hệ khiếu nại tài chính độc lập, không gắn với vòng đời đơn hàng vật lý.
3. **Tích hợp Tự động API Bưu tá GHN lấy hàng chiều về**:
   - Sử dụng cơ chế mô phỏng sự kiện vận chuyển theo đúng kịch bản sandbox của đồ án.
