# Payment Module

## Diagram báo cáo — TASK-0081 / 2026-10-10

[UC01–UC18 đã có 36 ảnh kiểm nguồn/native/PDF](../requirements/use-cases-83/report-uc01-18/README.md), gồm UC17, thanh toán phí còn nợ và không thu trùng. Thay 34 ảnh được duyệt và thêm hai ảnh UC11, giữ Hình 3.3–3.38 và ảnh ngoài phạm vi. 2FA chỉ dành cho Người mua/Người bán theo quyết định người dùng. Đây là cập nhật yêu cầu/diagram; không nâng trạng thái code/schema/nghiệm thu module.

## Report diagrams — TASK-0079 / 2026-10-10

[UC19–UC83 đã có đủ 130 Activity/Sequence trong báo cáo](../requirements/use-cases-83/report-from-uc19/README.md), kiểm native/PDF trọn trang. RS-02/RS-05 được chốt và đồng bộ theo xác nhận người dùng; chuẩn Activity/Sequence gọn được giữ. Đây là tài liệu, không nâng trạng thái code/database/nghiệm thu module.

## Diagram presentation — TASK-0077 / 2026-10-10

Mã Activity/Sequence hiện dùng [cách trình bày gọn](../requirements/use-cases-83/diagram-presentation/README.md): bỏ legend khỏi ảnh, rút nhãn và giữ đủ đường đi theo đặc tả. UC83 trong báo cáo đã thay bằng hai hình mới. Kiểm nguồn/1.000 đường đi và render 166 PNG đạt; ảnh UC79–UC83 đã kiểm trọn một trang trên PDF. Đây là cập nhật trình bày tài liệu.

## Report diagrams — TASK-0076 / 2026-10-10

Đã chèn Activity/Sequence UC83 theo source TASK-0075, giữ đặc tả hoàn tiền/xu từng phần. Mỗi hình cùng title/caption nằm trọn một trang, kiểm native/PDF bản sao cùng layout. [Bằng chứng](../requirements/use-cases-83/report-insertion/README.md). Không đổi code/schema/nghiệm thu module.

## Current use-case documents — TASK-0075 / 2026-10-10

[Bộ 83 đặc tả/166 Activity–Sequence](../requirements/use-cases-83/README.md) đã đọc lại trực tiếp báo cáo và viết theo 830 trường hiện hành; [rà soát](../requirements/use-cases-83/REVIEW.md) ghi các điểm nguồn cần làm rõ. UC25/UC71/UC72/UC83 tách phí đăng, tiền thực trả và xu; tránh giao dịch 0 đồng, xử lý từng phần/từng đơn và chỉ báo chuyển xong khi xác nhận. Đây là yêu cầu và diagram ở mức yêu cầu; trạng thái code/schema/nghiệm thu của module không được nâng từ kết quả tài liệu. [Bàn giao](../handoff/USE_CASE_83_AGENT_HANDOFF.md).

## Database report — TASK-0072 / 2026-10-09

[TASK-0072](../progress/archive/TASK-0072-database-design-report.md) đã điền chương bảng database trong báo cáo Google Docs theo schema V23: danh sách 48 bảng và 48 bảng chi tiết/907 trường, giữ mẫu native và kiểm tra căn lề, ghim tiêu đề, căn giữa chiều dọc. Đây là cập nhật tài liệu; trạng thái triển khai module vẫn theo code/kiểm thử. PDF/HTML export bị từ chối 403, chưa kiểm bố cục trang.

## Public policies — TASK-0071 / 2026-10-09

[TASK-0071](../progress/archive/TASK-0071-public-use-case-policies-and-coins.md) sửa UC25/UC71/UC83: đơn 0 đồng do xu không có giao dịch tiền 0 đồng; hoàn tiền thực trả và xu riêng từng đơn. Hủy/hoàn hợp lệ trả toàn bộ xu đã dùng kể cả phí giao, không cấp lại voucher; trả không hoàn phí giao chiều mua đã trả bằng tiền. Giữ điều kiện KTV-only không hoàn vì hỏng nghiêm trọng, không chuyển trùng. Báo cáo đã đọc lại native; code/database V23 chưa đồng bộ quy tắc mới trong task này.

## Database consolidation — TASK-0070 / V23

6 bảng: payments/payment_attempts/payment_events/payment_confirmations/order_fund_components/settlement_operations. record_type phân biệt intent/legacy payment, raw receipt/parsed event và allocation/component; holds nằm case_actions, reconciliation nằm cases. Money source/refund concurrency/callback dedup vẫn được bảo vệ.

Local og_shop đã migrate/validate V23: 48 bảng nghiệp vụ, 49 gồm Flyway history; 104 nguồn khớp fingerprint trước/sau. Backend entity/join-table dùng schema og_compat. 252 tests: 251 pass, 1 Windows symlink skip; 33 expansion PostgreSQL pass. [Bàn giao V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md), [dictionary 48 bảng](../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md). API/UI/worker canonical còn cần nối; checkpoint V21/baseline dưới đây là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — TASK-0069 / V21

V18/V21: intent theo purpose, exact verified confirmations, phân bổ từng đơn, money components/holds/settlement operations. Refund/release không vượt confirmed source; late charge đi reconciliation; checkout 0đ không tạo cash receipt. [Bàn giao và ma trận 83 UC](../architecture/DATABASE_EXPANSION_V21_20261009.md). Local og_shop V1–V21 đã migrate/validate; 249 tests, 0 failures/errors, 1 Windows symlink skip; 30 expansion PostgreSQL tests đạt. Đây là nền database và mapping tương thích, chưa nghiệm thu các API/UI mới. Phần Database review và trạng thái baseline cũ bên dưới ghi tình trạng trước TASK-0069.


## Database review — 2026-10-09

Theo [bản rà soát 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), UC25/UC71/UC83 cần G07 và kết nối phí tin G04: intent theo purpose, allocation từng đơn, fund components/holds và settlement operation. V13/V14 đã có receipt/attempt/reconciliation nhưng payments vẫn một/đơn và refund_amount=amount. Hoàn 100% tiền hàng thực trả cần tách ship; các phần hoàn/giải ngân khác nhau được xử lý riêng. Chưa triển khai; trạng thái/invariant bên dưới là baseline cũ.

- Status: PLANNED
- Requirements: UC-08, phần refund của UC-14
- Completion: 0/2 requirement groups verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý payment intent mô phỏng/sandbox, trạng thái giữ tiền, callback, release và full-order refund.

## Out of scope

Ví tiền thật, partial refund và đối soát production.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/payment/`
- Flyway tables/fields thuộc Payment
- Payment provider adapter

## Dependencies

- Commerce, Fulfillment, Trust & Safety và Platform.

## Security invariants

- Callback được xác minh và idempotent.
- Không đồng thời release và refund.
- Không release khi Complaint/return đang mở.
- Không tin total hoặc trạng thái do client gửi.
- `payments.amount` phải bằng `orders.total_amount`, trong đó total đã bao gồm buyer system fee và shipping fee.
- Policy/phí đã snapshot trên offer/order item không được tính lại khi payment callback đến.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.

## Remaining tasks

- [ ] Chọn sandbox hoặc mock contract.
- [ ] Chốt Payment state machine.
- [ ] Triển khai callback/release/refund.
- [ ] Kiểm thử callback lặp, đến muộn và race condition.

## Expected changes

Sẽ ưu tiên interface provider và fake adapter xác định trước khi tích hợp sandbox ngoài.
