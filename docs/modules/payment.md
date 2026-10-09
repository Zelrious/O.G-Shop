# Payment Module

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
