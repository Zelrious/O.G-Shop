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
    - [x] `CaseDecisionEntity` hỗ trợ outcomes: `APPROVE_RETURN`, `REJECT_RETURN`, `SELLER_DECLINED`, `FULL_GOODS_REFUND`, `EXEMPT_GOODS_REFUND`, `SYSTEM_TIMEOUT`.
    - [x] `CaseEvidenceEntity` hỗ trợ media type `IMAGE`, `VIDEO` (<= 60s, <= 50MB) và cơ chế Append-only với `submission_no`.
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

- [ ] **Task 1.1b: Kiểm thử tích hợp PostgreSQL thật (og_compat schema & triggers)**
  - **Mô tả**: Kiểm thử tích hợp thực tế trên PostgreSQL theo mẫu `ShipmentPostgresTest` / `VouchersPostgresTest`, sử dụng `PostgresTestSafetyGuard` và `@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")`.
  - **Tiêu chí chấp nhận**:
    - [ ] Kết nối DB test an toàn biệt lập qua `PostgresTestSafetyGuard` (`OGSHOP_TEST_DB_ISOLATED=true`, `OGSHOP_TEST_DB_URL`).
    - [ ] Đọc SELECT thành công từ cả 5 view: `og_compat.cases`, `og_compat.case_rounds`, `og_compat.case_decisions`, `og_compat.case_evidences`, `og_compat.case_events`.
    - [ ] Insert đơn hàng fixture thỏa 6 điều kiện của `og_guard_return_case` (đơn `DELIVERED`, có `valid_delivered_at`, trong hạn 3 ngày, `return_allowed = true`, chưa hoàn tất sớm `early_completed_at IS NULL`, tiền chưa giải ngân cuối `escrow_state = 'HELD'`) và có bản ghi `order_fund_components`.
    - [ ] Insert hồ sơ mẫu `CaseEntity` và xác nhận trigger `trg_return_case_holds` tự động tạo bản ghi `money_holds` cho hồ sơ.
    - [ ] Ánh xạ khớp hoàn toàn database schema thật `og_compat` (chỉ tích mục này sau khi Task 1.1b pass).
    - [ ] Dọn dẹp dữ liệu fixture hoặc tự động rollback trong transaction, không để lại dữ liệu rác.
  - **Files**:
    - `backend/src/test/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseEntitiesPostgresTest.java`

- [ ] **Task 1.2: Bổ sung các trường nghiệp vụ vào `OrderEntity` & Cập nhật Repositories**
  - **Mô tả**: Ánh xạ `return_allowed`, `valid_delivered_at`, `return_due_at`, `early_completed_at`, `early_completion_warning_version` vào `OrderEntity`. Đảm bảo KHÔNG dùng `STATUS_DISPUTED` làm trạng thái đơn; đơn giữ `DELIVERED`, cờ `has_blocking_case` được tính từ hồ sơ `cases`.
  - **Tiêu chí chấp nhận**:
    - [ ] `OrderEntity` ánh xạ đủ 5 trường trên.
    - [ ] `CaseRepository` hỗ trợ `findByOrderId` và `findActiveCaseByOrderId`.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/infrastructure/persistence/OrderEntity.java`
    - `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/infrastructure/persistence/CaseRepository.java`

---

## Checkpoint 1: Domain Entities & JPA Mapping
- [ ] Biên dịch sạch sẽ: `cd backend; .\mvnw.cmd test-compile`
- [ ] Chạy smoke test H2: `cd backend; .\mvnw.cmd test "-Dtest=ReturnReasonCodeTest,CaseEntitiesSmokeTest"`
- [ ] Chạy Postgres test (khi bật DB test): `cd backend; .\mvnw.cmd test "-Dtest=CaseEntitiesPostgresTest"`

---

## Giai đoạn 2: Tách Hoàn tất sớm (UC27) & Nghiệp vụ Vòng 1 (UC62 & UC75)
- [ ] **Task 2.1: Refactor `earlyCompleteOrder` & Tách biệt nút "Đã nhận hàng" (UC27, UC71)**
  - **Mô tả**:
    * Nút "Đã nhận hàng": Chỉ ghi nhận `orders.received_at = now()`, KHÔNG đổi trạng thái giải ngân, KHÔNG đóng quyền trả hàng, KHÔNG gán lại `valid_delivered_at`.
    * Nút "Hoàn tất sớm" (`earlyCompleteOrder`): Bắt buộc Người mua đồng thuận cảnh báo từ bỏ quyền trả hàng (`warningConsent`), gọi hàm DB `og_early_complete_order`. Hàm này CHỈ ghi nhận mốc `early_completed_at` và sự kiện `EARLY_COMPLETION_ACCEPTED`, KHÔNG trực tiếp đổi trạng thái giải ngân (UC71 3a).
    * Tiến trình giải ngân cho Người bán (`disburseSellerFunds`): Là bước xử lý riêng, chỉ giải phóng tiền ký quỹ (`RELEASED`) và chuyển đơn sang `COMPLETED` khi đủ 3 điều kiện: (1) `valid_delivered_at` có giá trị; (2) Hết 3 ngày từ `valid_delivered_at` HOẶC có mốc `early_completed_at`; (3) Không có hồ sơ ngăn (`og_order_has_blocking_case = false`) và không có `money_holds` chưa đóng (BR-UC71-04).
  - **Tiêu chí chấp nhận**:
    - [ ] Chặn Hoàn tất sớm nếu đơn hàng đang có hồ sơ mở (`og_order_has_blocking_case(orderId) == true`).
    - [ ] Khi Hoàn tất sớm thành công: Chỉ ghi nhận mốc `early_completed_at`, tạo `order_events`, không giải ngân ngay trong hàm này.
    - [ ] Tiến trình giải ngân kiểm tra đủ 3 điều kiện theo BR-UC71-04 trước khi giải ngân `RELEASED` và đóng đơn `COMPLETED`.
  - **Files**:
    - `backend/src/main/java/com/oldbutgold/shop/modules/commerce/application/OrderManagementService.java`

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
  - **Tiêu chí chấp nhận**:
    - [ ] Hồ sơ `POST_RETURN` quá hạn 2 ngày không có đề nghị hợp lệ -> Tự động ra quyết định `SYSTEM_TIMEOUT` + `FULL_GOODS_REFUND` hoàn 100% tiền hàng.
    - [ ] Đơn `DELIVERED` quá hạn 3 ngày từ `valid_delivered_at`, không có hồ sơ mở -> Tự động giải ngân cho Người bán (áp dụng cho cả đơn `return_allowed = false`).
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
  - **Mô tả**: Tách biệt nút "Đã nhận hàng" và nút "Hoàn tất sớm (có popup cảnh báo)", nút "Yêu cầu trả hàng" (kiểm tra 6 điều kiện), nút "Rút yêu cầu", Modal tải bằng chứng, Timeline hồ sơ nhiều vòng.
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
