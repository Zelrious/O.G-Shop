# Fulfillment Module

## Database report — TASK-0072 / 2026-10-09

[TASK-0072](../progress/archive/TASK-0072-database-design-report.md) đã điền chương bảng database trong báo cáo Google Docs theo schema V23: danh sách 48 bảng và 48 bảng chi tiết/907 trường, giữ mẫu native và kiểm tra căn lề, ghim tiêu đề, căn giữa chiều dọc. Đây là cập nhật tài liệu; trạng thái triển khai module vẫn theo code/kiểm thử. PDF/HTML export bị từ chối 403, chưa kiểm bố cục trang.

## Public policies — TASK-0071 / 2026-10-09

[TASK-0071](../progress/archive/TASK-0071-public-use-case-policies-and-coins.md) rút gọn UC26–UC33, giữ mốc giao hợp lệ, OTP/hai bên, nhận khác hoàn tất sớm, 3 ngày yêu cầu trả và 2 ngày phản hồi sau nhận trả. Phân biệt phí giao đã trả bằng tiền không hoàn khi trả với xu cho phí giao được trả lại trong trường hợp hợp lệ theo UC83. Đây là chính sách báo cáo, không sửa code/database V23.

## Database consolidation — TASK-0070 / V23

2 bảng: shipments/shipment_events; events, pickup attempts và handover confirmations chung sổ có record_type. OTP/hạn giao/trả giữ nguyên; direct handover vẫn cần chứng cứ hai bên, không tự valid-deliver từ một xác nhận.

Local og_shop đã migrate/validate V23: 48 bảng nghiệp vụ, 49 gồm Flyway history; 104 nguồn khớp fingerprint trước/sau. Backend entity/join-table dùng schema og_compat. 252 tests: 251 pass, 1 Windows symlink skip; 33 expansion PostgreSQL pass. [Bàn giao V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md), [dictionary 48 bảng](../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md). API/UI/worker canonical còn cần nối; checkpoint V21/baseline dưới đây là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — TASK-0069 / V21

V19: shipment leg, events/pickup attempts, OTP/two-party handover, valid delivery và early consent. Deadline trả/pickup tách riêng; attention view hỗ trợ xử lý conflict/im lặng, chưa có worker/API mới. [Bàn giao và ma trận 83 UC](../architecture/DATABASE_EXPANSION_V21_20261009.md). Local og_shop V1–V21 đã migrate/validate; 249 tests, 0 failures/errors, 1 Windows symlink skip; 30 expansion PostgreSQL tests đạt. Đây là nền database và mapping tương thích, chưa nghiệm thu các API/UI mới. Phần Database review và trạng thái baseline cũ bên dưới ghi tình trạng trước TASK-0069.


## Database review — 2026-10-09

Theo [bản rà soát 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), UC26–UC28/UC32–UC33 cần G08: outbound/return leg, events/attempts, OTP/hai bên, mốc nhận hợp lệ và hạn riêng. shipments hiện UNIQUE order_id, chưa đủ giao trả và pickup window; early completion khác receipt. Chưa triển khai; trạng thái bên dưới là baseline trước rà soát.

- Status: PLANNED
- Requirements: UC-10, UC-11, phần return của UC-14
- Completion: 0/3 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý shipment, tracking mock/sandbox, giao hàng, inspection deadline và return shipment.

## Out of scope

Quyết định tranh chấp và lệnh chuyển tiền cuối cùng.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/fulfillment/`
- `frontend/src/features/orders/` và `frontend/src/features/disputes/` khi được tạo
- Flyway tables Shipment và return record

## Dependencies

- Commerce, Payment, Trust & Safety và Platform.

## Security invariants

- Shipment callback idempotent.
- Inspection deadline chỉ được thiết lập một lần theo policy.
- Return có record riêng, không ghi đè outbound shipment.
- Địa chỉ giao hàng dùng snapshot của Order.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.

## Remaining tasks

- [ ] Chốt inspection deadline và carrier adapter.
- [ ] Thiết kế outbound/return model.
- [ ] Triển khai UC-10, UC-11 và return flow.
- [ ] Kiểm thử callback lặp và auto-complete race.

## Expected changes

Shipping integration sẽ bắt đầu bằng deterministic fake adapter để kiểm thử state transition.
