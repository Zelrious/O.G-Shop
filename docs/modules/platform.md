# Platform Module

## Database report — TASK-0072 / 2026-10-09

[TASK-0072](../progress/archive/TASK-0072-database-design-report.md) đã điền chương bảng database trong báo cáo Google Docs theo schema V23: danh sách 48 bảng và 48 bảng chi tiết/907 trường, giữ mẫu native và kiểm tra căn lề, ghim tiêu đề, căn giữa chiều dọc. Đây là cập nhật tài liệu; trạng thái triển khai module vẫn theo code/kiểm thử. PDF/HTML export bị từ chối 403, chưa kiểm bố cục trang.

## Public policies — TASK-0071 / 2026-10-09

[TASK-0071](../progress/archive/TASK-0071-public-use-case-policies-and-coins.md) rút gọn UC34–UC60/UC77–UC81: thông báo phản ánh kết quả, không thay quyết định; tiền và xu hiển thị riêng, đơn 0 đồng không bị nhắc trả tiền. UC52 cảnh báo uy tín dưới 90 và dưới 80 cho KTV xem xét. Native structure/readback đạt; PDF đã xuất nhưng tải lỗi, chưa kiểm bố cục trang. Chưa thay worker/API hoặc database V23 theo chính sách mới.

## Database consolidation — TASK-0070 / V23

notifications/audit_logs/outbox_events/interaction_events giữ core; delivery/consumer progress và dispatch/reminder/export/status histories gộp theo loại/chủ thể. 7 reporting views trong public; 104 writable adapter views trong og_compat không lưu bản sao dữ liệu.

Local og_shop đã migrate/validate V23: 48 bảng nghiệp vụ, 49 gồm Flyway history; 104 nguồn khớp fingerprint trước/sau. Backend entity/join-table dùng schema og_compat. 252 tests: 251 pass, 1 Windows symlink skip; 33 expansion PostgreSQL pass. [Bàn giao V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md), [dictionary 48 bảng](../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md). API/UI/worker canonical còn cần nối; checkpoint V21/baseline dưới đây là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — TASK-0069 / V21

V20: append-only audit/status history, Admin dispatch/export authority, notification delivery/reminder/consumer dedup, interaction metrics và revenue/case/reputation views. AuditLogEntity @Immutable tương thích ràng buộc audit. [Bàn giao và ma trận 83 UC](../architecture/DATABASE_EXPANSION_V21_20261009.md). Local og_shop V1–V21 đã migrate/validate; 249 tests, 0 failures/errors, 1 Windows symlink skip; 30 expansion PostgreSQL tests đạt. Đây là nền database và mapping tương thích, chưa nghiệm thu các API/UI mới. Phần Database review và trạng thái baseline cũ bên dưới ghi tình trạng trước TASK-0069.


## Database review — 2026-10-09

Theo [bản rà soát 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), UC34–UC60/UC77–UC81 cần G10/G11/G12: audit chỉ thêm, notification event/recipient dedup và sender/reference version, interaction metrics riêng. Doanh thu lấy confirmed listing fee ledger; số UC/states bên dưới thuộc baseline cũ. Chưa triển khai schema/worker hoặc nghiệm thu báo cáo.

- Status: PLANNED
- Requirements: UC-18, UC-19
- Completion: 0/2 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Cung cấp notification, audit log và read model cho dashboard quản trị.

## Out of scope

Sở hữu hoặc sửa trực tiếp trạng thái nghiệp vụ của module khác.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/platform/`
- `frontend/src/features/admin/` khi được tạo
- Flyway tables Notification, AuditLog và projection nếu được phê duyệt

## Dependencies

Đọc domain event hoặc query đã công bố từ các module nghiệp vụ.

## Security invariants

- Audit là append-only qua API nghiệp vụ.
- Audit không chứa secret hoặc raw evidence.
- Dashboard chỉ đọc aggregate/projection.
- Notification không phải nguồn sự thật cho trạng thái giao dịch.
- Domain event cần phát ra ngoài transaction phải được ghi cùng transaction vào `outbox_events`.
- Outbox payload không chứa secret, raw KYC, token hoặc bằng chứng nhạy cảm.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.
- [x] V3 tạo durable `outbox_events` cho Redis/WebSocket/notification worker sau này.

## Remaining tasks

- [ ] Chốt event publication pattern sau khi command/event của core module ổn định.
- [ ] Triển khai outbox worker idempotent sau core acceptance gate.
- [ ] Thiết kế audit schema và retention.
- [ ] Triển khai notification và dashboard read model.
- [ ] Kiểm thử permission và dữ liệu nhạy cảm.

## Expected changes

Platform sẽ được triển khai sau khi event/command của các core module ổn định. Không thêm Redis/cache để bù cho query hoặc transaction nghiệp vụ chưa đúng; core flow phải tiếp tục hoạt động khi Redis không khả dụng.
