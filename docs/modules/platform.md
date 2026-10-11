# Platform Module

## Auth version và provider — TASK-0086 / 2026-10-11

V26 giữ 47 bảng nghiệp vụ, thêm credential_version/google_subject và auth_data, backfill Google identity, mở rộng users view writer và giữ validator cũ. Password hash đổi tăng version nguyên tử; JWT decoder đối chiếu trên mỗi Bearer/restore, chặn version cũ/status/LOGIN restriction và vẫn giữ deadline V24. Google dùng JWK RS256/issuer/audience/expiry/nonce, bounded network timeouts; Client ID công khai lấy qua backend config. Reset mail after-commit queue hai worker/100 việc, không durable; retry bằng resend nếu mất thư.

Clean V1→V26 và upgrade V25→V26/JPA/security/transaction đã kiểm trên PostgreSQL riêng; 64 backend liên quan (29 PostgreSQL) và 135 frontend đạt, package/build/typecheck/scoped lint đạt. [Schema](../../database/docs/AUTH_FLOWS_V26.md), [Google setup](../setup/GOOGLE_AUTH.md), [bằng chứng](../../output/auth-fixes-2026-10-11/README.md). Chưa áp V26 vào database đang dùng, chưa test provider Google/Gmail thật. Services/container task đã dọn; container người dùng giữ nguyên. Các checkpoint dưới đây giữ lịch sử.

## CORS Retry-After — TASK-0085 / 2026-10-11

SecurityConfig thêm expose chính xác Retry-After; giữ danh sách origin/request headers và allowCredentials hiện hành. HTTP regression qua controller/advice/CorsFilter thật kiểm register/resend 429 có header cần đọc; origin ngoài cấu hình vẫn bị chặn trước application service. Nhóm backend 19 tests và package đạt. [Bằng chứng](../../output/auth-retry-fix-2026-10-11/README.md). Không đổi SMTP, database hoặc migration.

## Mailpit local và Gmail relay — TASK-0083 / 2026-10-11

SMTP local dùng IPv4 `127.0.0.1:1025`; Mailpit chỉ publish localhost, relay tự động/thủ công giới hạn email được chọn. Đã kiểm UI 200, một thư example.test được giữ local và Gmail STARTTLS/AUTH 235, không gửi thư thật. Config private đã nạp vào container mới; container cũ được giữ nguyên để bảo toàn thư. Backend cần restart để đọc lại `.env`; nhận OTP Gmail và đăng ký qua UI còn chờ kiểm. [Hướng dẫn](../setup/EMAIL_OTP.md), [bàn giao](../progress/archive/TASK-0083-mailpit-local-smtp.md).

## Nền SMTP và migration auth — TASK-0082 / V25 / 2026-10-10

Boot mail starter và cấu hình SMTP có timeout 5 giây; không có fallback trả OTP qua API/log. Mail chưa bật trả 503 và không tạo tài khoản; mail health check chỉ bật khi OTP_MAIL_ENABLED=true. [Cấu hình local/Gmail](../setup/EMAIL_OTP.md). Worker Identity xóa payload chờ quá hard expiry, giữ lịch sử auth challenge; không thay notification/audit/outbox.

V25 thêm sáu cột trên hai bảng hiện có và loại thưởng đăng ký, giữ V1–V24. [Chi tiết schema/guard](../../database/docs/EMAIL_REGISTRATION_V25.md). 46 kiểm thử PostgreSQL clean/upgrade/OTP đạt; V25 local đã backup/restore/migrate/validate với 47/47 fingerprints giữ nguyên, 47 bảng nghiệp vụ/901 trường. SMTP transport localhost đạt; runtime mới health/catalog 200, thiếu SMTP trả 503 không tạo user/challenge. Không tự sửa báo cáo Google Docs hoặc DDL ERD V24.

## Report diagrams — TASK-0079 / 2026-10-10

[UC19–UC83 đã có đủ 130 Activity/Sequence trong báo cáo](../requirements/use-cases-83/report-from-uc19/README.md), kiểm native/PDF trọn trang. RS-02/RS-05 được chốt và đồng bộ theo xác nhận người dùng; chuẩn Activity/Sequence gọn được giữ. Đây là tài liệu, không nâng trạng thái code/database/nghiệm thu module.

## Diagram presentation — TASK-0077 / 2026-10-10

Mã Activity/Sequence hiện dùng [cách trình bày gọn](../requirements/use-cases-83/diagram-presentation/README.md): bỏ legend khỏi ảnh, rút nhãn và giữ đủ đường đi theo đặc tả. UC79–UC81 trong báo cáo đã thay bằng sáu hình mới. Kiểm nguồn/1.000 đường đi và render 166 PNG đạt; ảnh UC79–UC83 đã kiểm trọn một trang trên PDF. Đây là cập nhật trình bày tài liệu.

## Report diagrams — TASK-0076 / 2026-10-10

Đã chèn Activity/Sequence UC79–UC81 theo source TASK-0075, giữ đặc tả/chính sách thống kê. Mỗi hình cùng title/caption nằm trọn một trang, kiểm PDF bản sao cùng layout. [Bằng chứng](../requirements/use-cases-83/report-insertion/README.md). Không đổi code/schema/nghiệm thu module.

## Current use-case documents — TASK-0075 / 2026-10-10

[Bộ 83 đặc tả/166 Activity–Sequence](../requirements/use-cases-83/README.md) đã đọc lại trực tiếp báo cáo và viết theo 830 trường hiện hành; [rà soát](../requirements/use-cases-83/REVIEW.md) ghi các điểm nguồn cần làm rõ. UC66/UC69/UC70/UC72/UC77–UC81 giữ quyền Admin/KTV, lịch sử, chương trình ưu đãi và thống kê doanh thu từ phí đăng đã xác nhận. Đây là yêu cầu và diagram ở mức yêu cầu; trạng thái code/schema/nghiệm thu của module không được nâng từ kết quả tài liệu. [Bàn giao](../handoff/USE_CASE_83_AGENT_HANDOFF.md).

## Report terminology — TASK-0074 / 2026-10-10

[TASK-0074](../progress/archive/TASK-0074-report-plain-language-glossary-and-actors.md) viết lại mô tả notification/audit/outbox/interaction theo công dụng, điền từ viết tắt và phạm vi actor từ báo cáo. Giữ nguyên chính sách và 83 đặc tả; không thay worker, code hay database V24. Native content/styles đạt; chưa kiểm trang vì PDF export 403.

## Current database — V24 / TASK-0073

[Bàn giao V24](../architecture/DATABASE_AUTH_SIMPLIFICATION_V24_20261009.md): bỏ riêng refresh sessions, giữ mọi bảng Platform và lịch sử; local og_shop còn 47 bảng nghiệp vụ, 103 compatibility views, 7 report views. Graph validator bỏ nhánh refresh, giữ các ràng buộc khác. Fingerprints cả 47 bảng, clean/upgrade PostgreSQL, Flyway/Hibernate và runtime smoke đạt. Báo cáo được cập nhật 47 bảng/895 trường và đối chiếu định dạng native; chưa kiểm trang PDF do export 403. Không nâng trạng thái nghiệm thu worker/API từ thay đổi này; các checkpoint V23 bên dưới là lịch sử.

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
