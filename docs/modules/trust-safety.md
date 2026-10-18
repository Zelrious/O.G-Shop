# Trust & Safety Module

## Console selects and eKYC — TASK-0096 / 2026-10-13

KTV/Admin:18 native selects chuyển sang shared Select; upward popover neo sát trigger6px, theo dõi vị trí khi sidebar chuyển động; sidebar chờ320ms, transition360ms. Cột queue360–400px; eKYC list/detail và masked list; voucher tự chọn campaign đầu API, bỏ option rỗng. API/handler/rules giữ nguyên.204 tests/43 files, lint/TypeScript/build đạt;82 browser checks không overflow/JS error, menu lệch ngang0px/gap6px. [Ảnh và giới hạn](../../output/ui-0096/README.md).

## Console reports and navigation — TASK-0095 / 2026-10-13

Admin: thẻ doanh thu lớn bên trái gồm biểu đồ/so tháng, ba KPI phụ xếp dọc và bằng đáy0px. KTV/Admin: utilities chung hàng heading, hover tách focus để sidebar không thu khi đổi trang; màu surface/selected theo theme, câu chữ tranh chấp đơn giản. Dữ liệu minh họa và API/handler/export giữ nguyên.201 frontend tests, final5 regression, full lint/TypeScript/build đạt;60 browser checks hai theme1366/1440/1920/390 không overflow/JS error. [Ảnh và giới hạn](../../output/ui-0095/README.md).

## KTV UI system — TASK-0094 / 2026-10-13

Thêm shared Select/Pagination/PageHeader; token surface/popover/disabled và semantic badge/button, sửa contrast card eKYC/khiếu nại, custom voucher/filter/page-size dropdown, phân trang 0 kết quả. Giữ sidebar và tất cả API/handler nghiệp vụ. 195 full-suite pass + 2 eKYC tests mới; final focused26, lint/TypeScript/build đạt. Browser48 geometry +8 contrast +2 dropdown checks, không overflow/JS error; sampled contrast light4,65/dark5,76. [Báo cáo và giới hạn](../../output/ui-0094/README.md).

## Console hai theme — TASK-0091 / 2026-10-13

KTV/Admin shell và trang quản lý dùng token light/charcoal, chuông/theme riêng và sidebar mở khi focus bàn phím. Không đổi nghiệp vụ moderation/KYC/complaint/dispute/evidence. Browser KTV fixture chỉ xác nhận shell/error UI và không có chat trigger; endpoint moderation nằm ngoài fixture. [Bằng chứng](../../output/ui-0091/README.md).

## Chat/offer V31 — TASK-0090 / 2026-10-13

ConversationEvidenceFacade giữ media cho case/report còn mở/recent terminal, native trigger hold trước cleanup. Legacy report không có resolved timestamp được giữ thận trọng. KTV CHAT ban không tước kênh phản hồi hồ sơ chính thức; full pipeline UC74 ngoài phạm vi.

[API](../api/CHAT_OFFER_API.md), [thiết kế/rollout](../architecture/CHAT_OFFER_V31_20261013.md), [test guide/ảnh](../../output/phase34-runtime/README.md). Năm task marketplace hoàn tất trong phạm vi hiện hành; app DB V31/47 bảng, backup/preservation150 nguồn đạt, runtime5174/8080 sẵn sàng. Các checkpoint dưới giữ lịch sử.

## Quản lý user KTV — TASK-0088 / 2026-10-12

AccountTrustFacade đọc penalty đã xác nhận/review hợp lệ để tính điểm 0–100 và lịch sử; cảnh báo <90/<80 không autoban. Creator hoặc order-party đọc/append phản hồi chính thức trong hồ sơ mở/còn hạn, không lộ private evidence. Bù deadline Seller từ lỗi SYSTEM_ISSUE CLOSED/SYSTEM_RESOLVED với khoảng gián đoạn KTV/Admin xác nhận; exact duration/idempotent/native guard. Đây là phần đọc UC11 và phục vụ nghĩa vụ UC67; không triển khai API xác nhận báo cáo/xử phạt UC74 hay full tranh chấp. [Thiết kế/rollout](../architecture/ACCOUNT_GOVERNANCE_V30_20261012.md), [API](../api/ACCOUNT_MANAGEMENT_API.md), [Kiểm chứng](../../output/phase2-runtime/README.md). Các checkpoint dưới đây giữ lịch sử.

## Diagram báo cáo — TASK-0081 / 2026-10-10

[UC01–UC18 đã có 36 ảnh kiểm nguồn/native/PDF](../requirements/use-cases-83/report-uc01-18/README.md), gồm UC10–UC11, quyền chọn bản ghi/chặn và uy tín. Thay 34 ảnh được duyệt và thêm hai ảnh UC11, giữ Hình 3.3–3.38 và ảnh ngoài phạm vi. 2FA chỉ dành cho Người mua/Người bán theo quyết định người dùng. Đây là cập nhật yêu cầu/diagram; không nâng trạng thái code/schema/nghiệm thu module.

## Report diagrams — TASK-0079 / 2026-10-10

[UC19–UC83 đã có đủ 130 Activity/Sequence trong báo cáo](../requirements/use-cases-83/report-from-uc19/README.md), kiểm native/PDF trọn trang. RS-02/RS-05 được chốt và đồng bộ theo xác nhận người dùng; chuẩn Activity/Sequence gọn được giữ. Đây là tài liệu, không nâng trạng thái code/database/nghiệm thu module.

## Diagram presentation — TASK-0077 / 2026-10-10

Mã Activity/Sequence hiện dùng [cách trình bày gọn](../requirements/use-cases-83/diagram-presentation/README.md): bỏ legend khỏi ảnh, rút nhãn và giữ đủ đường đi theo đặc tả. Kiểm nguồn/1.000 đường đi và render 166 PNG đạt; ảnh UC79–UC83 đã kiểm trọn một trang trên PDF. Đây là cập nhật trình bày tài liệu.

## Current use-case documents — TASK-0075 / 2026-10-10

[Bộ 83 đặc tả/166 Activity–Sequence](../requirements/use-cases-83/README.md) đã đọc lại trực tiếp báo cáo và viết theo 830 trường hiện hành; [rà soát](../requirements/use-cases-83/REVIEW.md) ghi các điểm nguồn cần làm rõ. UC11/UC61–UC68/UC74–UC76 giữ uy tín 100, cảnh báo không tự khóa/xóa, xét bằng chứng theo giai đoạn và chỉ KTV duyệt không hoàn khi đủ căn cứ nghiêm trọng. Đây là yêu cầu và diagram ở mức yêu cầu; trạng thái code/schema/nghiệm thu của module không được nâng từ kết quả tài liệu. [Bàn giao](../handoff/USE_CASE_83_AGENT_HANDOFF.md).

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
