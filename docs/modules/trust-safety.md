# Trust & Safety Module

## Database report — TASK-0072 / 2026-10-09

[TASK-0072](../progress/archive/TASK-0072-database-design-report.md) đã điền chương bảng database trong báo cáo Google Docs theo schema V23: danh sách 48 bảng và 48 bảng chi tiết/907 trường, giữ mẫu native và kiểm tra căn lề, ghim tiêu đề, căn giữa chiều dọc. Đây là cập nhật tài liệu; trạng thái triển khai module vẫn theo code/kiểm thử. PDF/HTML export bị từ chối 403, chưa kiểm bố cục trang.

## Public policies — TASK-0071 / 2026-10-09

[TASK-0071](../progress/archive/TASK-0071-public-use-case-policies-and-coins.md) sửa UC61–UC67/UC74–UC76: nhận 3–5 sao phục hồi uy tín đến 100; KTV xác nhận mới trừ 10–20/7–10/3–5 theo hành vi. Dưới 90 và dưới 80 báo KTV, không tự khóa/xóa; công khai hạn đăng/chat/mua, giữ quyền phản hồi đơn cũ. Đánh giá có xu riêng, hoàn tiền và xu theo UC83. Đây là báo cáo được chốt, chưa triển khai chính sách mới vào code/V23.

## Database consolidation — TASK-0070 / V23

Case core còn 3 bảng: cases/case_actions/case_evidence; round/decision/event/hold gom case_actions. reviews chứa edit_history; seller_buyer_blocks và penalty_ledger giữ riêng, policies nằm business_policies. Quyền quyết định, source evidence và hold chặn giải ngân sau upgrade được kiểm chứng.

Local og_shop đã migrate/validate V23: 48 bảng nghiệp vụ, 49 gồm Flyway history; 104 nguồn khớp fingerprint trước/sau. Backend entity/join-table dùng schema og_compat. 252 tests: 251 pass, 1 Windows symlink skip; 33 expansion PostgreSQL pass. [Bàn giao V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md), [dictionary 48 bảng](../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md). API/UI/worker canonical còn cần nối; checkpoint V21/baseline dưới đây là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — TASK-0069 / V21

V19/V20: return/system cases, nhiều round/evidence/decision/event, final-authority/holds, penalties/review history/restrictions. Cleanup view dựa nghĩa vụ hoàn tất; legacy tự xóa +3 ngày đã tắt. [Bàn giao và ma trận 83 UC](../architecture/DATABASE_EXPANSION_V21_20261009.md). Local og_shop V1–V21 đã migrate/validate; 249 tests, 0 failures/errors, 1 Windows symlink skip; 30 expansion PostgreSQL tests đạt. Đây là nền database và mapping tương thích, chưa nghiệm thu các API/UI mới. Phần Database review và trạng thái baseline cũ bên dưới ghi tình trạng trước TASK-0069.


## Database review — 2026-10-09

Theo [bản rà soát 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), UC61–UC65/UC74–UC76 cần G09/G10: một return case nhiều vòng, lỗi hệ thống riêng, evidence revisions, KTV-only miễn hoàn và penalty ledger. complaints hiện order-bound/một resolution, cleanup +3 ngày chưa xét nghĩa vụ còn mở. Chưa triển khai; trạng thái bên dưới là baseline trước rà soát.

- Status: PLANNED
- Requirements: UC-12, UC-13, UC-15, UC-16, UC-17
- Completion: 0/5 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý Complaint, phản hồi hai bên, evidence, dispute resolution, Review, Report, moderation và hạn chế tài khoản.

## Out of scope

Authentication implementation, payment provider và shipment provider.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/`
- `frontend/src/features/disputes/`, `reputation/`, `admin/` khi được tạo
- Flyway tables Complaint, Review, Report và evidence metadata

## Dependencies

- Identity, Catalog, Commerce, Payment, Fulfillment và Platform.

## Security invariants

- Complaint khác Report.
- Một Order chỉ có một Complaint active.
- Evidence gốc không bị Admin sửa.
- Resolution luôn có actor, reason và audit.
- Restrict account không tự hủy/refund/release giao dịch đang mở.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.

## Remaining tasks

- [ ] Chốt evidence model và private storage.
- [ ] Chốt resolution command contract.
- [ ] Triển khai Complaint/dispute/review/report/moderation.
- [ ] Kiểm thử IDOR, double resolution và evidence privacy.

## Expected changes

Các command ảnh hưởng tiền sẽ phối hợp Payment qua facade có version/state guard.
