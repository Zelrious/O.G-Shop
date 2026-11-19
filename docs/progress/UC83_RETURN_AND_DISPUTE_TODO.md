# Danh sách Công việc (Todo List): Luồng Trả hàng & Tranh chấp Nhiều Vòng

> [!NOTE]
> Tài liệu di chuyển từ nhánh PR #17 (`tasks/todo.md`) vào `docs/progress/` nhằm tránh xung đột với `tasks/todo.md` trên `main`. Các migration trong tài liệu gốc (`V37–V51`) tương ứng với **`V41–V55`** sau tích hợp Đợt 1 (`TASK-0119`). Xem thêm [UC83_RETURN_AND_DISPUTE_PLAN.md](../architecture/UC83_RETURN_AND_DISPUTE_PLAN.md).

> Tài liệu theo dõi tiến độ chi tiết của [UC83_RETURN_AND_DISPUTE_PLAN.md](../architecture/UC83_RETURN_AND_DISPUTE_PLAN.md) dựa trên Báo cáo đồ án (`Báo_cáo_đồ_án.docx`: UC22, UC26, UC27, UC33, UC40, UC52, UC62, UC63, UC71, UC75, UC83).

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
  - **Chặn**: Task 3.3 (Worker auto-disburse 3 ngày) và Task 4.1a/4.1b.

- [x] **Task 2.2: Triển khai Mở yêu cầu trả hàng đủ 6 điều kiện (`createReturnRequest` - UC62)**
  - **Mô tả**: Kiểm tra đủ 6 điều kiện bắt buộc, khởi tạo hồ sơ `cases` ở trạng thái `OPEN` và Round 1 `RETURN_APPROVAL`.
  - **Tiêu chí chấp nhận**:
    - [x] Kiểm tra đủ 6 điều kiện: `DELIVERED`, có `valid_delivered_at`, `return_allowed == true`, trong hạn 3 ngày từ `valid_delivered_at`, chưa Hoàn tất sớm, tiền chưa giải ngân cuối, không có hồ sơ mở.
    - [x] Validate 3 lý do chuẩn: `CHANGE_MIND`, `NOT_AS_DESCRIBED`, `DAMAGED_OR_DEFECTIVE`.
    - [x] Validate giới hạn bằng chứng: Tối đa 5 ảnh, 2 video <= 60 giây, <= 50MB.
    - [x] Tiền ký quỹ bị đóng băng (trigger `trg_return_case_holds` tự động tạo `money_holds`).
    - [x] Đã hoàn thành 14 unit tests trong `ReturnCaseServiceTest` (100% pass).
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServiceTest.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServicePostgresTest.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseDtos.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseRoundRepository.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseEvidenceRepository.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseDecisionRepository.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseEventRepository.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServiceTest.java`

- [x] **Task 2.3: Triển khai Người bán phản hồi & KTV phân xử Vòng 1 (UC62 & UC75)**
  - **Mô tả**:
    * KTV thấy hồ sơ ngay khi `OPEN`, có thể chủ động duyệt `APPROVE_RETURN` hoặc bác `REJECT_RETURN` (kèm lý do).
    * Người bán có 2 lựa chọn: `[Chấp thuận trả]` (`APPROVE_RETURN`) hoặc `[Không đồng ý, chuyển KTV]` (`SELLER_DECLINED`).
    * Xử lý xung đột đua quyết định (first-write wins).
  - **Tiêu chí chấp nhận**:
    - [x] Người bán `APPROVE_RETURN` -> Case chuyển `RETURN_APPROVED` (**chưa hoàn tiền**, chờ gửi hàng trả).
    - [x] Người bán `SELLER_DECLINED` -> Case chuyển `REVIEWING`, hạn KTV 48h tính từ khi hồ sơ đủ nội dung.
    - [x] KTV duyệt `APPROVE_RETURN` hoặc bác `REJECT_RETURN` (bắt buộc nhập lý do).
    - [x] KTV bác (`REJECT_RETURN`) -> Hồ sơ chuyển `REJECTED`, **không giải ngân ngay** mà chờ đủ điều kiện UC71 (hết 3 ngày hoặc Hoàn tất sớm).
    - [x] Quá hạn KTV 48h: Ghi nhận event `ESCALATED`, không tự động duyệt/hoàn tiền.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServiceTest.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServicePostgresTest.java`

- [x] **Task 2.4: Triển khai Người mua rút yêu cầu trả hàng (`withdrawReturnRequest` - UC62 7c)**
  - **Mô tả**: Người mua chủ động rút yêu cầu trước khi gửi hàng trả.
  - **Tiêu chí chấp nhận**:
    - [x] Chỉ cho rút khi hồ sơ ở `OPEN`, `REVIEWING`, `RETURN_APPROVED` và chưa bàn giao bưu tá / chưa gửi hàng.
    - [x] Đóng lệnh giữ tiền (`money_holds.closed_at = now()`), giải phóng cản trở (`has_blocking_case = false`), tiền tiếp tục xử lý giải ngân theo điều kiện UC71.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServiceTest.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServicePostgresTest.java`

---

## Giai đoạn 3: Vận chuyển Trả hàng (UC33) & Nghiệp vụ Vòng 2 (UC63 & UC75)

### Thứ tự Triển khai & Numbering
**3.0 → 3.0b → 4.1a → Feature 3.2 → 4.1b → 3.3**

| Mã | Nội dung | Trạng thái ban đầu |
|---|---|---|
| 3.0 | Architecture Gate (chỉ xác minh + chốt quyết định, không code) | TODO |
| 3.0b | Round 1 Writer Hardening + Financial Safety Guard | BLOCKED bởi 3.0 |
| 3.1 | Vận chuyển hàng trả & ghi nhận nhận lại (UC33) | DONE |
| 4.1a | Settlement Request Foundation | BLOCKED bởi 3.0, 3.0b |
| 3.2.1–3.2.6 | Feature 3.2 Round 2 Resolution | BLOCKED bởi 3.0, 3.0b, 4.1a |
| 4.1b | Settlement Confirmation & Refund Completion | sau Feature 3.2 |
| 3.3 | Worker auto-disbursement 3 ngày | BLOCKED bởi Financial Safety Gate + 4.1b |

- **Task 3.0: Architecture Gate (Không phải task implementation - Chỉ xác minh + chốt quyết định, không code)**
  - **Mô tả**: Rà soát, xác minh độc lập toàn bộ các kết luận kiến trúc từ source code và migration thực tế, giải quyết các bất định kỹ thuật và thu thập phê duyệt của User cho các quyết định nghiệp vụ (BD-1 .. BD-6) trước khi bắt đầu viết mã Feature 3.2.
  - **Phân biệt ba tầng định nghĩa DB**:
    1. *Migration lịch sử*: nội dung function trong từng file migration (V19, V22, V23–V36...).
    2. *Effective source definition*: định nghĩa cuối cùng sau khi áp dụng toàn bộ chuỗi migration theo thứ tự.
    3. *Runtime database definition*: định nghĩa thực tế ở DB đang chạy (`TO VERIFY IN TASK 3.0` do Flyway history runtime chưa kiểm chứng).
  - **Hạng mục chi tiết**:
    * **3.0.1 Unified Locking Protocol**:
      - Ghi nhận `REPORTED (chưa tự xác minh, chuyển VERIFIED khi Task 3.0 có bằng chứng)`: trigger Decision khóa `case_rounds → cases → orders`; trigger Event khóa `cases → case_rounds` (đọc `orders` không khóa).
      - Ghi nhận `REPORTED (chưa tự xác minh, chuyển VERIFIED khi Task 3.0 có bằng chứng)`: `sellerRespondRound1` và `ktvDecideRound1` hiện không lấy Order lock.
      - `PROPOSED`: Gateway Lock ở Java theo thứ tự `Order → Case → CaseRound → tài nguyên phụ thuộc`. Gateway Lock là giao thức mutex ở tầng Java, KHÔNG thay đổi thứ tự khóa nội bộ của trigger và chỉ có tác dụng khi MỌI writer tuân thủ.
      - `TO VERIFY IN TASK 3.0`: toàn văn `og_guard_case_evidence`, `og_close_case_holds`, `og_lock_money_hold`, `og_guard_settlement_operation`, `og_case_settlement_allowed` và các trigger gọi gián tiếp, kể cả `UPDATE money_holds`; danh sách đầy đủ writer Java (Round 1, Round 2, GHN webhook, direct handover, `disburseSellerFunds`, worker hiện có, repository gọi trực tiếp); khóa ngầm `FOR KEY SHARE` do FK; isolation level, refresh/reload entity sau khi lấy khóa, retry cho `40P01`, `55P03`, `40001`, `lock_timeout`.
      - Yêu cầu: Không được kết luận đã loại bỏ deadlock khi chưa liệt kê hết đường ghi dữ liệu. Thứ tự `orders → order_fund_components → settlement_operations` của Task 2.1b là thứ tự khóa tài chính con; cần đối chiếu với Gateway trong 3.0.1, không mặc định tương thích.
    * **3.0.2 Evidence Submission Batch**:
      - `TO VERIFY IN TASK 3.0`: Mâu thuẫn giữa hai báo cáo review: bản trước trích V19:168 gán `COALESCE(MAX)+1`, bản sau trích V22 chỉ đếm quota theo `(round_id, submitted_by, submission_no, media_type)` và không tự cấp số. Cần xác minh mâu thuẫn này theo cả ba tầng định nghĩa DB, kèm việc kiểm tra V23–V36 xem có migration nào định nghĩa lại `og_guard_case_evidence`.
      - Ghi nhận `REPORTED (chưa tự xác minh, chuyển VERIFIED khi Task 3.0 có bằng chứng)`: Java hiện hardcode `submission_no = 1` trong `createReturnRequest`.
      - `saveAllAndFlush()` KHÔNG phải giải pháp gom submission, và không được coi là cơ chế batch.
      - So sánh ba phương án (`PROPOSED`, chưa chốt, phụ thuộc BD-6):
        (A) Service-level allocation: Order lock → Case lock → CaseRound `PESSIMISTIC_WRITE` → mới `SELECT MAX+1` → gán chung cho cả batch → flush Evidence trước Event `VALID_DAMAGE_REQUEST`;
        (B) Submission Header;
        (C) DB function cấp số nguyên tử.
        Ghi rõ: Round Lock chỉ là lớp phòng thủ bổ sung của riêng phương án A, không phải một lựa chọn độc lập và không thay thế Gateway Lock; lock Case → Round theo phương án A ngược thứ tự trigger Decision (Round → Case) nên chỉ an toàn khi mọi writer đi qua Gateway.
      - V19/V22 không được sửa. Nếu cần DDL chỉ đề xuất migration mới; số phiên bản (ví dụ V37) cần xác minh lại khi implement.
    * **3.0.3 Event & Decision Semantics**:
      - `PROPOSED`: `CaseDecision` là audit record chính; không dùng `SUBMITTED` cho KTV ra quyết định; `VALID_DAMAGE_REQUEST` ghi sau khi Evidence hợp lệ.
      - `TO VERIFY IN TASK 3.0`: Event có được insert sau khi Case đã `CLOSED` không; `CaseDecision` có append-only ở tầng DB không (trigger, quyền UPDATE/DELETE). Chưa có quyết định thì không tạo Event `DECISION`, không sửa CHECK constraint.
    * **3.0.4 Trusted Timestamp & Worker Contract**:
      - `REPORTED (chưa tự xác minh, chuyển VERIFIED khi Task 3.0 có bằng chứng)`: `og_apply_case_decision` ghi đè `NEW.decided_at := clock_timestamp()`. Điều kiện `TO VERIFY IN TASK 3.0`: không có trigger nào khác ghi đè sau đó.
      - `TO VERIFY IN TASK 3.0`: `SYSTEM_TIMEOUT` có cho `reviewed_by = NULL` ở DDL, Entity, trigger; unique constraint chống nhiều Decision cho một Round; `complete_submission_at` có được cập nhật ở Round 2 không.
    * **3.0.5 KTV Queue, Escalation, ADMIN**:
      - `REPORTED (chưa tự xác minh, chuyển VERIFIED khi Task 3.0 có bằng chứng)`: `EXEMPT_GOODS_REFUND` cần `og_has_role(reviewed_by, 'KTV')` và `valid_damage_request_at IS NOT NULL`; ADMIN không có role KTV bị chặn với SQLSTATE `23514`.
      - `REPORTED (chưa tự xác minh, chuyển VERIFIED khi Task 3.0 có bằng chứng)`: Event `ESCALATED` yêu cầu actor có role KTV và recipient có role ADMIN, nên Worker hệ thống không thể tự ghi nếu không có thay đổi DB.
      - `TO VERIFY IN TASK 3.0`: `cases.assigned_to` và cơ chế claim/assign; `ktv_due_at` được ai set; view/queue KTV.
    * **3.0.6 Evidence Security**:
      - `REPORTED (chưa tự xác minh, chuyển VERIFIED khi Task 3.0 có bằng chứng)`: `resolveContentDigest()` chấp nhận digest từ client nếu khớp regex, hoặc băm chuỗi `objectKey`; không chứng minh digest của nội dung file; fallback còn trả một digest hard-code. Ghi nhận là security gap, mục tech-debt bắt buộc trước production.
      - Không đưa cụm "validate SHA-256" vào tiêu chí nghiệm thu như thể đã bảo mật. `TO VERIFY IN TASK 3.0`: ownership, MIME, kích thước thực, thời lượng video, quyền truy cập storage private, xử lý file mồ côi khi transaction rollback.
    * **3.0.7 Financial Safety & Settlement Contract (P0)**:
      - Invariant `PROPOSED` (đặt trong `OrderManagementService.disburseSellerFunds()`, không chỉ trong query của Worker): không cho giải ngân `cause_type = 'COMPLETION'` khi đơn đã có final decision `FULL_GOODS_REFUND` hoặc `EXEMPT_GOODS_REFUND`. `REJECT_RETURN` vẫn giải ngân theo UC71 khi đủ điều kiện.
      - Lý do: `og_close_case_holds` đóng hold ngay khi insert Decision, `og_order_has_blocking_case` hết chặn, đơn `DELIVERED` quá 3 ngày trông như đủ điều kiện giải ngân. Hiện `disburseSellerFunds` đã gọi được qua `AdminPaymentController`, nên rủi ro tồn tại ngay khi Feature 3.2 tạo Decision, không đợi tới Worker 3.3. Vì vậy invariant này thuộc phạm vi 3.0b/4.1a.
      - `money_holds.closed_at` không phải bằng chứng tiền đã hoàn hoặc đã giải ngân.
      - `PROPOSED`: interface `RoundDecisionSettlementPort.requestSettlement(orderId, caseId, decisionId, outcome, commandKey)`; production không có adapter giả báo thành công; mock chỉ trong test.
      - `PROPOSED`: ghi nghĩa vụ settlement bằng dòng `settlement_operations` trạng thái `REQUESTED` (có `case_decision_id`, `command_key` unique), cùng transaction với `CaseDecision`; không bắt buộc outbox nếu bảng này đã đủ vai trò truy vết bền vững.
      - `TO VERIFY IN TASK 3.0`: với Decision vừa insert trong cùng transaction, `og_case_settlement_allowed` có thỏa (`is_final`, `valid_returned_at`); `REQUESTED` có cần hold còn mở khi hold đã bị đóng; thứ tự khóa `orders → components → settlement_operations` so với Gateway và trigger Decision.
      - `TO VERIFY IN TASK 3.0`: Kiểm tra phụ thuộc module giữa Trust & Safety (`trustsafety`) và Payment (`payment`); xác định module nào nên sở hữu interface `RoundDecisionSettlementPort`; xem xét phương án port interface thuộc về module gọi (`trustsafety`) và adapter triển khai ở module `payment`; không tự thay đổi package/path trước khi kiểm tra kiến trúc module thực tế.
    * **3.0.8 Business Decisions (BD-1 đến BD-6)**: Tất cả `PENDING USER APPROVAL` (đưa vào Decisions Log, không đánh LOCKED, không tự chọn):
      - BD-1: ADMIN có cần được cấp thêm role KTV khi muốn phán quyết, và sau `ESCALATED` ADMIN có quyền gì?
      - BD-2: `CaseDecision` có là nguồn audit chính, hay cần thêm Event `DECISION` (cần migration)?
      - BD-3: Escalation do hệ thống (System actor, cần migration) hay do KTV chủ động (Worker chỉ cảnh báo)?
      - BD-4: SLA KTV Round 2: 48h từ `complete_submission_at` hay từ `valid_damage_request_at`?
      - BD-5: Quota 5 ảnh/2 video áp dụng cho mỗi submission hay tổng Round? Seller có được bổ sung nhiều lần không? Ảnh hưởng cả Round 1 (UC62 7a bổ sung bằng chứng).
      - BD-6: Chỉ Application Writer được ghi Evidence/Decision, hay phải bảo vệ cả writer ngoài Java? Mặc định gợi ý: chỉ Application Writer (Java layer) thông qua Gateway Lock (Round Lock chỉ là lớp bổ sung nếu chọn phương án A), kèm ghi chú rủi ro nếu có direct SQL / writer ngoài Java can thiệp.
  - **Tiêu chí hoàn thành Task 3.0**:
    * Tất cả vấn đề P0 về Locking, Evidence Integrity và Financial Safety phải được giải quyết hoặc có phương án được User phê duyệt; không còn vấn đề P0 nào chưa có phương án xử lý. Cụ thể P0 gồm: (a) Unified Locking Protocol, (b) thiết kế Evidence Submission Batch và quota (BD-5, BD-6), (c) Financial Safety & Settlement Contract.
    * Lưu ý: `resolveContentDigest()` (3.0.6) là nợ kỹ thuật (tech-debt) bắt buộc trước production, không phải P0 chặn hoàn thành Task 3.0.
    * Các vấn đề P1/P2 chưa thể xác minh có thể được ghi nhận `INCOMPLETE` nhưng phải có lý do rõ ràng, đánh giá mức độ ảnh hưởng và chỉ định Task phụ thuộc.
    * Tất cả các quyết định nghiệp vụ BD-1 đến BD-6 phải được User phê duyệt chính thức (các mục chưa phê duyệt giữ nguyên nhãn `PENDING USER APPROVAL`).
    * Phải có một Unified Locking Protocol duy nhất được User phê duyệt.

- [ ] **Task 3.0b: Round 1 Writer Hardening + Financial Safety Guard**
  - **Trạng thái**: `BLOCKED` bởi 3.0.
  - **Mô tả & Phạm vi**:
    * Nhận danh sách writer từ Task 3.0.1 làm đầu vào; không điền sẵn danh sách writer.
    * Bổ sung Gateway Lock theo Locking Protocol đã duyệt cho tất cả các Java Writer được Task 3.0.1 xác định là thiếu Gateway Lock, hoặc có lý do loại trừ được User phê duyệt. Quy định này bao gồm cả các writer thông qua Repository hoặc JdbcTemplate nếu chúng ghi trực tiếp vào các bảng `cases`, `case_rounds`, `case_events`, `case_evidence`, `case_decisions`, `orders` hoặc `money_holds`. Tuyệt đối không được tuyên bố Gateway Lock đã hoàn thiện nếu còn writer bypass giao thức.
    * Nếu Task 3.0.1 liệt kê thêm các Java Writer thiếu Gateway Lock ngoài 3 writer trọng yếu (`sellerRespondRound1`, `ktvDecideRound1`, `disburseSellerFunds`), đề xuất tách Task 3.0b thành 2 phase: `Task 3.0b-1` (hardening 3 writer trọng yếu trên) và `Task 3.0b-2` (các writer còn lại), chờ User duyệt trước khi triển khai.
    * Thêm invariant của 3.0.7 vào `OrderManagementService.disburseSellerFunds()`: từ chối giải ngân `COMPLETION` nếu đơn đã có final decision `FULL_GOODS_REFUND` hoặc `EXEMPT_GOODS_REFUND`.
    * Không thay đổi tiêu chí nghiệp vụ của Task 2.3.
  - **Tiêu chí chấp nhận**:
    - [ ] Test PostgreSQL Round 1 Concurrency:
      * (A) Seller `APPROVE_RETURN` đua với quyết định cuối của KTV (`APPROVE_RETURN` / `REJECT_RETURN`): chỉ một quyết định cuối được ghi nhận (first-write-wins), không lỗi deadlock `40P01`.
      * (B) Seller `SELLER_DECLINED` đua với KTV: cả hai có thể thành công, KTV vẫn ra quyết định được. Ghi `TO VERIFY IN TASK 3.0`: `SELLER_DECLINED` có tạo `case_decisions` và đóng Case hay không; kỳ vọng ở (B) chỉ có hiệu lực sau khi xác minh.
    - [ ] Test PostgreSQL Financial Safety Guard: `disburseSellerFunds` bị từ chối khi đơn có final decision `FULL_GOODS_REFUND` hoặc `EXEMPT_GOODS_REFUND` (TC-32).
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/application/OrderManagementService.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/commerce/application/SellerDisbursementPostgresTest.java`

- [x] **Task 3.1: Xử lý Vận chuyển hàng trả & Ghi nhận nhận lại hàng (UC33)**
  - **Mô tả**: Theo dõi vận đơn trả (`shipments.leg = 'RETURN'`), ghi nhận `valid_returned_at` từ sự kiện vận đơn hoặc bàn giao hai bên (`og_confirm_direct_handover`), mở hạn 2 ngày phản hồi của Người bán.
  - **Tiêu chí chấp nhận**:
    - [x] Hạn gửi hàng trả: Hạn 3 ngày hoặc 3 lần lấy thất bại theo cấu hình vận đơn; gán pickup_expected_at và pickup_deadline từ approved_at của Case.
    - [x] Ghi nhận `valid_returned_at` từ webhook GHN giao thành công chiều về (`GhnWebhookProcessor` / `confirmReturnDeliveryByCarrier`) hoặc qua xác nhận bàn giao trực tiếp hai bên (`og_confirm_direct_handover`).
    - [x] Tự động gán thời hạn phản hồi: `seller_response_due_at = valid_returned_at + 2 days`.
    - [x] Chuyển trạng thái hồ sơ sang `POST_RETURN`, khởi tạo Round 2 với `stage = 'POST_RETURN_RESOLUTION'` và ghi nhận CaseEvent tương ứng.
    - [x] Phân quyền tra cứu cho KTV/Admin (`getReturnShipmentByCaseId`) và các chốt chặn fail-fast chặt chẽ (`shipmentRepository`, `jdbcTemplate`).
    - [x] Đã hoàn thành 71/71 tests PASS (`ReturnCaseServiceTest`, `GhnWebhookTest`, `ReturnCaseServicePostgresTest`).
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseDtos.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/fulfillment/application/GhnWebhookProcessor.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/fulfillment/infrastructure/persistence/ShipmentEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/fulfillment/infrastructure/persistence/ShipmentRepository.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseRepository.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServiceTest.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServicePostgresTest.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/fulfillment/api/GhnWebhookTest.java`

- [ ] **Feature 3.2: Round 2 Resolution (UC63, UC75, UC83)**
  - **Trạng thái**: `BLOCKED` bởi 3.0, 3.0b, 4.1a.
  - **Nguyên tắc chung**: Mọi chỗ ghi "Gateway Lock" phải tuân theo Locking Protocol 3.0.1 (`PROPOSED` cho đến khi User duyệt). Không tự chuyển trạng thái đơn hàng sang `REFUNDED`/`COMPLETED` trong luồng Round 2 khi chưa `CONFIRMED`.
  - **Quy tắc Nghiệm thu Tài chính**: `CaseDecision` không đồng nghĩa settlement đã hoàn tất; `money_holds.closed_at` không đồng nghĩa tiền đã hoàn hoặc đã giải ngân; không chuyển Order sang `REFUNDED`/`COMPLETED` khi settlement chưa `CONFIRMED`. Feature 3.2 được nghiệm thu ở cấp Decision + Settlement Request (`REQUESTED`). Task 4.1b mới được nghiệm thu ở cấp Settlement Confirmation (`CONFIRMED`) + Wallet Points Refund (hoàn 100% xu).
  - **Subtasks**:
    * [ ] **3.2.1 Seller Agree Refund (`agree-refund`)**:
      - Chuyển `CaseDecision` sang `FULL_GOODS_REFUND`.
      - Idempotent theo `command_key`.
      - Gọi `RoundDecisionSettlementPort` (Task 4.1a) để tạo Settlement Request (`REQUESTED`); không đánh dấu đã hoàn khi settlement chưa `CONFIRMED`.
    * [ ] **3.2.2 Seller Damage Exemption (`submitDamageExemptionRequest`)**:
      - Hàng hỏng nghiêm trọng hoặc sai/tráo/thiếu hàng.
      - Evidence theo thiết kế batch đã chốt ở 3.0.2 và quota theo BD-5.
      - Evidence flush trước Event `VALID_DAMAGE_REQUEST`.
      - Trong hạn `seller_response_due_at`.
      - Seller KHÔNG tự tạo `EXEMPT_GOODS_REFUND`.
      - `BLOCKED` bởi 3.0.2, BD-5, BD-6.
    * [ ] **3.2.3 KTV Final Decision (`ktvDecideRound2`)**:
      - Hai outcome: `FULL_GOODS_REFUND` hoặc `EXEMPT_GOODS_REFUND`.
      - `EXEMPT` bắt buộc có `valid_damage_request_at` và role KTV (SQLSTATE 23514); bắt buộc lý do; idempotent; gọi settlement port tạo Settlement Request (`REQUESTED`).
      - Round 2 vẫn `cases.state = POST_RETURN`, `case_rounds.state = OPEN` sau khi Seller gửi đề nghị; không dùng state `REVIEWING_R2`.
      - `BLOCKED` bởi 3.0, 3.0b, 4.1a, BD-1.
    * [ ] **3.2.4 Worker Auto-Refund 2 ngày (`reconcileOverdueReturnResponses`)**:
      - Điều kiện quét: `cases.state = 'POST_RETURN'`, `case_rounds.state = 'OPEN'`, `now() > seller_response_due_at`, `valid_damage_request_at IS NULL`.
      - Lock và recheck điều kiện sau khi lấy khóa.
      - `actor_type = 'SYSTEM_TIMEOUT'` + `outcome = 'FULL_GOODS_REFUND'`.
      - Dùng `decided_at` do DB ghi đè; idempotent; gọi settlement port tạo Settlement Request (`REQUESTED`).
    * [ ] **3.2.5 KTV SLA Escalation**:
      - `BLOCKED` bởi BD-1, BD-3, BD-4.
      - Không tự tạo Decision, không hoàn tiền, không mặc định Worker có quyền tạo Event `ESCALATED` nếu chưa có thay đổi DB/role.
    * [ ] **3.2.6 Integration & Concurrency Testing**:
      - Concurrency pairs:
        * Seller Agree vs KTV Final Decision (TC-30)
        * Seller Damage Request vs KTV `EXEMPT_GOODS_REFUND` (TC-37)
        * Seller Agree vs Worker Auto-Refund (TC-28)
        * Seller Damage Request vs Worker Auto-Refund (TC-29)
        * Seller `agree-refund` đua với Seller `damage-exemption` (`[PENDING USER APPROVAL]` vì là câu hỏi nghiệp vụ)
        * KTV ra quyết định Vòng 2 đua với Worker 2 ngày (`reconcileOverdueReturnResponses`)
        * Hai request Evidence đồng thời (TC-31)
        * Hai Worker cùng Case; GHN webhook vs Seller/KTV.
      - Kiểm thử bắt ngoại lệ `40P01` / `55P03` / `40001` và cơ chế retry.
      - Chống duplicate Decision/settlement; Financial Safety (không vừa hoàn vừa giải ngân cùng một khoản).
      - Nghiệm thu: không race nghiêm trọng, không hai quyết định trái ngược cho một Round, không tự chuyển Order sang `REFUNDED`/`COMPLETED` trong luồng Round 2.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseService.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnReconciliationScheduler.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServiceTest.java`
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnCaseServicePostgresTest.java`

## Giai đoạn 4: Hạch toán Hoàn tiền Chuẩn xác (UC83 & UC71)

- [ ] **Task 4.1a: Settlement Request Foundation (Thực hiện TRƯỚC Feature 3.2)**
  - **Trạng thái**: `BLOCKED` bởi 3.0, 3.0b.
  - **Mô tả & Phạm vi**: Đây là task nền tảng tính tiền cốt lõi, không phải task nhỏ:
    * Định nghĩa interface `RoundDecisionSettlementPort` và implementation thật (`RoundDecisionSettlementAdapter`). Không dùng adapter giả báo thành công ở production; mock chỉ dùng trong test.
    * Cùng transaction với `CaseDecision`: tạo bản ghi `settlement_operations` ở trạng thái `REQUESTED`, có `case_decision_id`, `command_key` ổn định; nếu thất bại thì rollback toàn bộ `CaseDecision`.
    * Tính `amount` cho từng component (`GOODS_CASH`, `PLATFORM_GOODS_SUBSIDY`) theo quy tắc UC83 (sau voucher/xu phân bổ cho hàng). Đơn 0 đồng không tạo operation. Nhánh `EXEMPT_GOODS_REFUND` giải ngân Seller với `operation_type = 'RELEASE_SELLER'`, `cause_type = 'EXEMPTION'`.
    * Bảng ánh xạ:
      - Seller Agree / KTV `FULL_GOODS_REFUND` / Worker `SYSTEM_TIMEOUT` → `REFUND_BUYER` (cause `RETURN`);
      - KTV `EXEMPT_GOODS_REFUND` → `RELEASE_SELLER` (cause `EXEMPTION`).
    * Điều kiện: Toàn bộ các mục `TO VERIFY` ở 3.0.7 phải được giải quyết trước khi implement.
  - **Quy tắc Nghiệm thu**: Task 4.1a CHỈ tạo `settlement_operations` ở state `REQUESTED` trong cùng transaction với `CaseDecision`. Tuyệt đối không tự chuyển `CONFIRMED` hay đổi trạng thái Order sang `REFUNDED`/`COMPLETED`.
  - **Files**:
    - Port: `RoundDecisionSettlementPort.java` (đường dẫn: `TO VERIFY AFTER TASK 3.0.7`)
    - Adapter: `RoundDecisionSettlementAdapter.java` (đường dẫn: `TO VERIFY AFTER TASK 3.0.7`)
    - Settlement Service: `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/RefundSettlementService.java`

- [ ] **Task 4.1b: Settlement Confirmation & Refund Completion (Thực hiện SAU Feature 3.2)**
  - **Trạng thái**: `CONDITIONAL` (sau khi Feature 3.2 hoàn thành).
  - **Mô tả & Phạm vi**:
    * Xử lý chuyển trạng thái `settlement_operations` từ `REQUESTED → CONFIRMED/FAILED` (provider mô phỏng nội bộ nêu rõ là mô phỏng), hoàn xu 100% cho Buyer vào ví xu, retry an toàn, không hoàn/giải ngân trùng.
    * Trạng thái thanh toán `REFUND_PENDING → REFUNDED` chỉ khi `settlement_operations` đạt `CONFIRMED`.
    * Tiêu chí chốt chặn: Order không tự chuyển `REFUNDED`/`COMPLETED` khi settlement chưa `CONFIRMED`.
  - **Quy tắc Nghiệm thu**: Task 4.1b chịu trách nhiệm nghiệm thu chuyển `REQUESTED → CONFIRMED/FAILED`, hoàn trả 100% xu vào ví xu, và chuyển Order sang `REFUNDED`/`COMPLETED` chỉ sau khi `CONFIRMED`.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/payment/application/RefundSettlementService.java`

- [ ] **Task 3.3: Worker Tự động Giải ngân sau 3 ngày (`reconcileOverdueDeliveries` - UC71)**
  - **Trạng thái**: `BLOCKED` bởi Financial Safety Gate (3.0b) + Task 4.1b.
  - **Mô tả & Phạm vi**:
    * Chạy định kỳ quét các đơn hàng `DELIVERED` quá 3 ngày từ `valid_delivered_at`.
    * Điều kiện bổ sung: Không có final decision `FULL_GOODS_REFUND` hoặc `EXEMPT_GOODS_REFUND`, không trùng settlement operation, gọi lại `disburseSellerFunds()` (không có logic giải ngân thứ hai trong Worker).
    * Áp dụng cho cả đơn `return_allowed = false`.
    * Tuyệt đối không bật trên môi trường tích hợp trước khi các điều kiện blocking được thỏa mãn.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/application/ReturnReconciliationScheduler.java`

---

### Các Cổng Kiểm soát Nghiệm thu (Verification Gates G0 – G4)
- **Gate G0**: Task 3.0 (Architecture Gate - xác minh mã nguồn & DB + chốt các quyết định BD-1..BD-6, không code).
- **Gate G1**: Task 3.0b + Task 4.1a, dừng xuất diff và review trước khi code tiếp.
- **Gate G2**: Task 3.2.1 + Task 3.2.2 (Nghiệp vụ Người bán Vòng 2), dừng xuất diff và review.
- **Gate G3**: Task 3.2.3 + Task 3.2.4 (KTV Phán quyết + Worker Auto-refund 2 ngày), dừng xuất diff và review.
- **Gate G4**: Task 3.2.5 (nếu được User duyệt) + Task 3.2.6 (Kiểm thử tích hợp & Concurrency), tiếp theo là Task 4.1b, cuối cùng là Task 3.3.
*Quy tắc*: Không `git commit` hay `git push` sau bất kỳ Gate nào; mỗi Gate báo cáo file thay đổi, migration (nếu có), danh sách test đã chạy, và các điểm chưa xác minh.

---

## Checkpoint 2: Kiểm thử Tự động Backend (37 Test Cases)

### Giai đoạn Test sau Task 4.1a & Feature 3.2 (Decision + Settlement Request `REQUESTED`)
> **Chốt chặn an toàn**: CaseDecision ghi nhận thành công, Settlement Operation tạo ở state `REQUESTED`. Tuyệt đối không tự chuyển `CONFIRMED`, không đổi Order sang `REFUNDED`/`COMPLETED`.
- [ ] **Unit Tests (Mockito / In-Memory)**:
  - Chạy lệnh: `cd backend; .\mvnw.cmd test -Dtest=ReturnCaseServiceTest,RefundSettlementTest`
  - [ ] Test mở Case và Guard điều kiện: chưa `DELIVERED`, `return_allowed = false`, quá 3 ngày, đã hoàn tất sớm (TC-01..TC-05).
  - [ ] Test giới hạn bằng chứng: video > 60s hoặc > 5 ảnh (TC-07).
  - [ ] Test giữ tiền ký quỹ và chặn hoàn tất sớm khi có hồ sơ mở (TC-06, TC-08, TC-09, TC-10).
  - [ ] Test luồng Vòng 1: Seller duyệt, Seller từ chối chuyển KTV, KTV bác kèm lý do không giải ngân ngay (TC-11..TC-14).
  - [ ] Test đua quyết định Vòng 1: first-write-wins (TC-15, TC-16).
  - [ ] Test quá hạn gửi hàng trả hoặc 3 lần lấy thất bại do lỗi người mua (TC-17).
  - [ ] Test bổ sung bằng chứng Vòng 2 và Worker idempotent (TC-27).
  - [ ] Test rút yêu cầu trả hàng trước khi gửi hàng và từ chối sau khi bàn giao (TC-35, TC-36 - Task 2.4).
  - [ ] Test Vòng 2 tạo Settlement Request: Seller Agree Refund (TC-19), Worker Auto-Refund (TC-21), KTV Exemption (TC-23) tạo `settlement_operations` ở state `REQUESTED` (chưa hoàn/giải ngân ngay).
  - [ ] Test công thức hoàn tiền: Đơn 0 đồng (Ví dụ UC83-07), đơn có ship tiền mặt (voucher hàng), đơn có voucher ship, nhánh `EXEMPT` không hoàn tiền/xu (TC-24..TC-26).
  - [ ] Test kiểm tra quota Evidence theo BD-5 (TC-34).
- [ ] **PostgreSQL Integration & Concurrency Tests**:
  - **Điều kiện môi trường bắt buộc**: `OGSHOP_TEST_DB_URL=jdbc:postgresql://127.0.0.1:55432/og_shop_test` và `OGSHOP_TEST_DB_ISOLATED=true`.
  - [ ] `ReturnCaseServicePostgresTest`: Toàn bộ luồng nghiệp vụ Case Vòng 1, Vòng 2, direct handover, các trigger DB.
  - [ ] `EvidenceBatchConcurrencyPostgresTest`: Nộp bằng chứng đồng thời, kiểm tra cấp số batch theo phương án 3.0.2 được duyệt (TC-31).
  - [ ] `Round2ConcurrencyPostgresTest`: Tranh chấp đua lệnh Vòng 2, bắt ngoại lệ 40P01/55P03/40001 và retry:
    * TC-28: Seller Agree vs Worker Auto-Refund (first-write-wins).
    * TC-29: Seller Damage Exemption vs Worker Auto-Refund.
    * TC-30: Seller `agree-refund` vs KTV Final Decision (first-write-wins: một quyết định cuối thắng, không tạo hai settlement request mâu thuẫn).
    * TC-37: Seller `damage-exemption` vs KTV `EXEMPT_GOODS_REFUND` (KTV bị từ chối nếu `valid_damage_request_at` chưa có; kiểm tra lại điều kiện sau khi lấy khóa; KHÔNG áp dụng first-write-wins).
  - [ ] `SettlementRequestPostgresTest`: Atomicity tạo Settlement Operation cùng transaction với CaseDecision, rollback khi `REQUESTED` thất bại, idempotency theo `command_key` (TC-33).
  - [ ] `SellerDisbursementPostgresTest`: Financial Safety Guard invariant (TC-32: chặn giải ngân `COMPLETION` khi đã có final decision hoàn/miễn hoàn).

### Giai đoạn Test sau Task 4.1b (Settlement Confirmation & Refund Completion)
> **Nghiệm thu tài chính hoàn tất**: Xử lý chuyển `settlement_operations` sang `CONFIRMED/FAILED`, hoàn trả 100% xu vào ví xu, Order chuyển `REFUNDED`/`COMPLETED` chỉ sau khi `CONFIRMED`.
- [ ] **Unit Tests (Mockito / In-Memory)**:
  - [ ] Test chuyển `settlement_operations` từ `REQUESTED → CONFIRMED/FAILED` với mock provider (TC-19, TC-21, TC-23 giai đoạn completion).
  - [ ] Test hoàn trả 100% xu đã dùng của đơn hàng vào ví xu của Buyer khi `CONFIRMED`.
  - [ ] Test chuyển trạng thái thanh toán sang `REFUNDED` và Order sang `REFUNDED` (hoặc `COMPLETED` đối với Exemption) chỉ sau khi `CONFIRMED`.
  - [ ] Test cơ chế retry an toàn, idempotency, không giải ngân hoặc hoàn tiền trùng lặp.
- [ ] **PostgreSQL Integration Tests**:
  - [ ] `SettlementConfirmationPostgresTest`: Xác nhận `CONFIRMED`, trigger DB `trg_settlement_operation_guard` cập nhật `released_amount`/`refunded_amount`, kiểm chứng Order chuyển `REFUNDED`/`COMPLETED`.

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

- [ ] **Task 5.2: Controller cho KTV (`KtvCaseController`)** `[PENDING USER APPROVAL về danh sách endpoint]`
  - **Lưu ý**: Hiện có sự khác biệt về thiết kế endpoint KTV giữa hai tài liệu: `todo.md` dự kiến (`/api/v1/ktv/cases`, `/decide-round1`, `/decide-round2`, `/request-evidence`), còn `complaint_plan.md` dự kiến (`/work-queue`, `/assign`, `/decision`). Mục này đánh dấu `[PENDING USER APPROVAL]`, giữ nguyên hai tài liệu để User chốt phương án endpoint trước khi code.
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
