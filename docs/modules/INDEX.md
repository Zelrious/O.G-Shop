# Module Index

## Sửa F1–F5/F7 auth — TASK-0086 / 2026-10-11

[Identity](identity.md) đã nối login email 2FA, reset OTP, Google/link/registration, lỗi khóa có cấu trúc và Back Seller; [Platform](platform.md) dùng V26 credential version thu hồi mọi phiên. 64 backend/135 frontend tests và quality gates đạt; 29 PostgreSQL tests chạy riêng, browser dùng HTTP fixture. [Bằng chứng trước/sau](../../output/auth-fixes-2026-10-11/README.md). Client ID đã cấu hình private, cần restart/backend migration sau backup; Google/Gmail thật, UC09 face, eKYC/KTV/full UC67 còn chờ. Các checkpoint dưới đây giữ lịch sử, không thay nghiệm thu provider thật.

## Sửa F6 auth — TASK-0085 / 2026-10-11

[Identity](identity.md) hiển thị countdown đăng ký Buyer/Seller, giữ dữ liệu và cho retry sau hết hạn; [Platform](platform.md) expose Retry-After qua CORS. 34 frontend/19 backend tests và quality gates đạt; browser kiểm hai vai trò với HTTP fixture riêng. [Bằng chứng](../../output/auth-retry-fix-2026-10-11/README.md). Các mục audit phía dưới mô tả kết quả trước sửa; F1–F5/F7 vẫn cần triển khai trong phạm vi tiếp nối.

## Audit auth — TASK-0084 / 2026-10-11

[Identity](identity.md) đã đối chiếu UC01–UC04 với UI/code/API và kiểm OTP Buyer/Seller trên môi trường riêng; 29 frontend/42 backend tests đạt. [Báo cáo](../../output/auth-audit-2026-10-11/AUTH_FUNCTIONAL_AUDIT.md) ghi thiếu Google/reset/2FA, chưa thu hồi phiên sau đổi mật khẩu, lỗi quay lại Seller và Retry-After/CORS. Không đổi source/schema hoặc nâng nghiệm thu toàn bộ các UC.

## Hộp thư OTP local — TASK-0083 / 2026-10-11

[Platform](platform.md) đã kiểm Mailpit SMTP/UI và Gmail STARTTLS/AUTH 235; config private dùng IPv4 và relay giới hạn recipient. [Identity](identity.md) còn cần xác nhận OTP qua UI sau restart backend. Không thay code/schema hoặc nghiệm thu gửi Gmail thật. [Hướng dẫn](../setup/EMAIL_OTP.md).

## Cải tổ auth đợt 1 — TASK-0082 / V25 / 2026-10-10

[Identity](identity.md) đã nối đăng ký email OTP, Buyer/Seller draft, mật khẩu mới thống nhất và chặn quyền bán tức thì cho user mới. [Commerce](commerce.md) cung cấp thưởng 3.000 xu/địa chỉ trong cùng transaction; [Platform](platform.md) ghi SMTP và schema guard. [API](../api/IDENTITY_API.md), [Gmail/local mail](../setup/EMAIL_OTP.md), [schema V25](../../database/docs/EMAIL_REGISTRATION_V25.md). Backend 259 pass/26 skip, gồm 46 PostgreSQL; frontend 113 pass, typecheck/lint/build đạt. V25 local có 47 bảng/901 trường, backup/restore/47 fingerprints và runtime đạt; Gmail thật cần cấu hình local. Phiên giữ deadline cố định. Các checkpoint dưới đây là lịch sử, không nâng nghiệm thu các UC Google/2FA/eKYC/KTV chưa nối.

## Diagram UC01–UC18 — TASK-0081 / 2026-10-10

[36 ảnh đã kiểm và chèn](../requirements/use-cases-83/report-uc01-18/README.md): thay 34 ảnh được duyệt, thêm cặp UC11 và sửa đúng loại Sequence UC07; giữ Hình 3.3–3.38. RS-03 đã chốt 2FA chỉ dành cho Người mua/Người bán, đồng bộ UC03/UC09 native/local/diagram. Native/PDF xác nhận trọn trang, giữ nội dung/caption/ảnh ngoài phạm vi và ba tab khác. Báo cáo hiện đủ 166 ảnh UC cùng hai hình tổng quan. Các checkpoint dưới đây giữ lịch sử.

## Caption báo cáo — TASK-0080 / 2026-10-10

[Tên/số 322 caption theo chương](../requirements/use-cases-83/chapter-captions/README.md) đã cập nhật và kiểm native/PDF; giữ nội dung đặc tả/bảng, 166 ảnh thực tế, ba tab khác. UC11 thiếu hai ảnh, vị trí Sequence UC07 dùng ảnh Activity cũ, danh mục đầu báo cáo còn template. Đây là metadata/trình bày báo cáo dùng chung, không đổi nghiệp vụ hoặc nghiệm thu tám module.

## Report diagrams — TASK-0079 / 2026-10-10

[UC19–UC83 đã có đủ 130 Activity/Sequence trong báo cáo](../requirements/use-cases-83/report-from-uc19/README.md), kiểm native/PDF trọn trang. RS-02/RS-05 được chốt và đồng bộ theo xác nhận người dùng; chuẩn Activity/Sequence gọn được giữ. Đây là tài liệu, không nâng trạng thái code/database/nghiệm thu module.

## Diagram workflow — TASK-0078 / 2026-10-10

Các lượt diagram tiếp theo áp dụng [quy trình kiểm đặc tả → kiểm mã → xuất/chèn ảnh](../requirements/use-cases-83/SPECIFICATION_GUIDE.md#quy-trình-thực-hiện-do-người-dùng-quy-định--2026-10-10). Hỏi người dùng khi luồng đặc tả không hợp lý và trước khi thay ảnh đã có; giữ mẫu Activity/Sequence hiện hành. Đây là quy trình tài liệu, không thay trạng thái nghiệp vụ/nghiệm thu module.

## Diagram presentation — TASK-0077 / 2026-10-10

Mã Activity/Sequence hiện dùng [cách trình bày gọn](../requirements/use-cases-83/diagram-presentation/README.md): bỏ legend khỏi ảnh, rút nhãn và giữ đủ đường đi theo đặc tả. Kiểm nguồn/1.000 đường đi và render 166 PNG đạt; ảnh UC79–UC83 đã kiểm trọn một trang trên PDF. Đây là cập nhật trình bày tài liệu.

## Use-case documents and diagrams — TASK-0075 / 2026-10-10

[Bộ 83 hiện hành](../requirements/use-cases-83/README.md) thay nguồn mã/nội dung bộ 78/80 khi soạn tài liệu và diagram. Đọc mới đủ 83 bảng/830 trường; có 83 đặc tả, 166 source Activity–Sequence, mapping và 8 ghi chú nguồn. Render 166 hình/kiểm từng nhánh đạt; [bằng chứng](../requirements/use-cases-83/VALIDATION.md). Đây là tài liệu mức yêu cầu, không đổi trạng thái nghiệm thu module; chưa đưa ảnh vào Google Docs/Drive.

## Report language — TASK-0074 / 2026-10-10

[TASK-0074](../progress/archive/TASK-0074-report-plain-language-glossary-and-actors.md) làm rõ 165 trường/47 mô tả bảng/6 mô tả yêu cầu, điền 52 từ viết tắt và 7 actor trong báo cáo. Mã AT01–AT07 và 83 đặc tả được giữ; KTV dùng tên Kỹ thuật viên theo phần tổng quan. Native content/styles đạt, PDF export 403 nên chưa kiểm trang. Đây là chỉnh tài liệu; schema V24, code và trạng thái nghiệm thu module không đổi.

## Current database and authentication — V24 / TASK-0073

[TASK-0073](../progress/archive/TASK-0073-remove-refresh-session-table.md) đã bỏ refresh sessions khỏi backend/frontend/PostgreSQL và báo cáo. Local og_shop còn **47 bảng nghiệp vụ/895 trường**, 48 public tables gồm Flyway history, 103 compatibility views, 7 report views. Đăng nhập có thời hạn cố định 15 phút mặc định, khôi phục không gia hạn; `auth_challenges`/eKYC giữ nguyên. [Dictionary V24](../../database/docs/DATABASE_TABLE_DICTIONARY_V24.md), [DDL ERD](../../database/schema/og_shop_v24_tables_for_erd.sql), [bàn giao](../architecture/DATABASE_AUTH_SIMPLIFICATION_V24_20261009.md).

47 fingerprints, backup/restore, V1–V24 validate và runtime smoke đạt; backend 263 tests (237 pass/26 skip), 34 Pg tests đạt, frontend 104 tests và nhóm auth cuối 40 tests đạt. Native báo cáo có một bảng tổng hợp và 47 bảng chi tiết, 48 headers ghim và 4.854 ô đúng căn lề. Global lint còn warning CartContext ngoài phạm vi; PDF export 403 nên chưa kiểm trang. Các checkpoint dưới đây là lịch sử, không thay trạng thái nghiệm thu 83 UC.

## Database report — TASK-0072 / 2026-10-09

[TASK-0072](../progress/archive/TASK-0072-database-design-report.md): đã viết danh sách 48 bảng nghiệp vụ và 48 bảng chi tiết/907 trường trong báo cáo native, bỏ Flyway; xác minh 4.922 ô và định dạng 49 bảng, tiêu đề đúng tên schema. Chỉ cập nhật báo cáo, không đổi trạng thái nghiệm thu module. Xuất PDF/HTML gặp 403; bố cục trang chưa được xem.

## Public policies — TASK-0071 / 2026-10-09

[TASK-0071](../progress/archive/TASK-0071-public-use-case-policies-and-coins.md) đã sửa 83 đặc tả trong báo cáo, giữ cấu trúc và sao lưu: OTP/Google dễ hiểu, uy tín bắt đầu/tối đa 100 và xử phạt do KTV quyết định, xu riêng uy tín với năm nguồn thưởng, voucher trước rồi xu trừ cả tiền hàng/phí giao đến 0 đồng, hoàn xu nhưng không cấp lại voucher. Đây là chính sách báo cáo hiện hành; không thay code/database V23 hoặc nâng trạng thái nghiệm thu module. Native readback đạt; tải PDF lỗi nên chưa kiểm bố cục trang.

## Historical database — V23 / TASK-0070 / 2026-10-09

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
