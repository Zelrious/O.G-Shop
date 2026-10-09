# Module Index

## Public policies — TASK-0071 / 2026-10-09

[TASK-0071](../progress/archive/TASK-0071-public-use-case-policies-and-coins.md) đã sửa 83 đặc tả trong báo cáo, giữ cấu trúc và sao lưu: OTP/Google dễ hiểu, uy tín bắt đầu/tối đa 100 và xử phạt do KTV quyết định, xu riêng uy tín với năm nguồn thưởng, voucher trước rồi xu trừ cả tiền hàng/phí giao đến 0 đồng, hoàn xu nhưng không cấp lại voucher. Đây là chính sách báo cáo hiện hành; không thay code/database V23 hoặc nâng trạng thái nghiệm thu module. Native readback đạt; tải PDF lỗi nên chưa kiểm bố cục trang.

## Current database — V23 / TASK-0070 / 2026-10-09

[Gộp 104 → 48 bảng nghiệp vụ](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md) đã áp dụng/validate local og_shop V23; 49 public tables gồm Flyway history, 7 report views và 104 compatibility views trong og_compat. Count/hash cả 104 nguồn khớp sau restore và live migration. Backend 252 tests (251 pass, 1 Windows symlink skip), 33 expansion Pg pass; runtime Hibernate/health/catalog đạt. [Dictionary/PK/FK](../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md) dùng cho ERD/báo cáo. Không đổi trạng thái nghiệm thu 83 UC từ việc gộp bảng; các checkpoint V21 bên dưới là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — 2026-10-09 / TASK-0069

[Schema V21 và ma trận từng UC](../architecture/DATABASE_EXPANSION_V21_20261009.md) đã triển khai G01–G12 qua V15–V21, áp dụng vào local og_shop và validate. Có 61 bảng mới/7 view; 249 tests (248 pass, 1 Windows symlink skip), gồm 30 kiểm thử expansion PostgreSQL. Schema/mapping đã có, API/UI/worker canonical còn cần triển khai; trạng thái use-case baseline bên dưới không được nâng thành VERIFIED vì chỉ có DDL.


Agent phải đọc file này và tài liệu của mọi module bị tác động trước khi lập phương án.

## Database review — 2026-10-09

[Rà soát theo 83 UC hiện hành](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md) đối chiếu live PostgreSQL 17.11/V1–V14, schema và code; đã lập ma trận từng UC, 12 nhóm mở rộng và kế hoạch chuyển đổi. Đây là phân tích, chưa triển khai schema. Bảng số UC/trạng thái bên dưới là baseline cũ, không thay bằng chứng trong bản rà soát mới.

| Module | Use case | Trạng thái | Tài liệu |
|---|---|---|---|
| Identity | UC-01, UC-02 | IMPLEMENTED | [identity.md](identity.md) |
| Catalog | UC-03, UC-04 | IMPLEMENTED | [catalog.md](catalog.md) |
| Communication | UC-05, UC-06 | PLANNED | [communication.md](communication.md) |
| Commerce | UC-07, UC-09 | PLANNED | [commerce.md](commerce.md) |
| Payment | UC-08, UC-14 | PLANNED | [payment.md](payment.md) |
| Fulfillment | UC-10, UC-11, UC-14 | PLANNED | [fulfillment.md](fulfillment.md) |
| Trust & Safety | UC-12, UC-13, UC-15–17 | PLANNED | [trust-safety.md](trust-safety.md) |
| Platform | UC-18, UC-19 | PLANNED | [platform.md](platform.md) |

## Status meanings

- `PLANNED`: boundary đã xác định, chưa có hành vi nghiệp vụ.
- `IN_PROGRESS`: có task được phê duyệt đang thực hiện.
- `IMPLEMENTED`: code đã hoàn thành nhưng chưa đủ bằng chứng nghiệm thu.
- `VERIFIED`: yêu cầu và kiểm thử liên quan đã đối chiếu.
- `BLOCKED`: không thể tiếp tục nếu thiếu quyết định hoặc dependency.
