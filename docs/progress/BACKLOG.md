# Project Backlog

## TASK-0097 — Commit delivery / COMPLETE

260 original changed/new files grouped into14 commits on the separate marketplace/console branch. Exact-index intermediate builds and final frontend/backend regression passed; integration skips and existing provider/bundle backlog retained. Local QA artifacts stay ignored. Push/merge is outside this delivery.

## TASK-0096 — Console selects/queues/eKYC / COMPLETE

Unified18 controls, corrected popover anchor, delayed sidebar, first voucher default and two-column eKYC verified. Existing eKYC preview/backend/provider/bundle limits unchanged. [Evidence](../../output/ui-0096/README.md).

## TASK-0095 — Console reports/navigation / COMPLETE

Revenue hierarchy, integrated utilities, persistent hover and plain dispute copy verified. Existing report fixture data and backend/provider/bundle backlog retained. [Evidence](../../output/ui-0095/README.md).

## TASK-0094 — KTV UI refactor / COMPLETE

UI scope verified: Select/Pagination/PageHeader, themed eKYC/complaint cards, voucher controls and shared semantic actions. No business/API/sidebar changes. Existing backend/provider and large-bundle backlog unchanged. [Evidence](../../output/ui-0094/README.md).

## Voucher — TASK-0093 / 2026-10-13

- [x] Own wallet/receivable/self-claim and owned voucher application in current buy-now.
- [x] Admin real campaigns/create/update/version/policy revisions/pause; code/benefit/date/scope/limit validation.
- [x] KTV real audience quick filters, explicit1–100 selections, preview/reason/confirmation, per-item results/recheck/stable retry receipts.
- [x] Only unused/unexpired receipts revocable; spent coupons never restored, orders retain discount history.
- [x] Private notices/audit/reminders, role/owner/negative/race/rollback verification.
- [x]191 frontend/303 executed backend across full+corrective runs;84 skipped. Final20/lint/build/package, genuine UI/9 signed HTTP, appV32/47 and150/150 preserved.
- [ ] Separate follow-up: full xu spending/refund/history pagination, multi-seller checkout, central audit viewer/providers.

[Evidence](../../output/vouchers-0093/README.md), [task](archive/TASK-0093-vouchers-and-bulk-distribution.md). Other backlog unchanged.

## Sửa UI theo bốn ảnh — TASK-0092 / 2026-10-13

- [x] Hero gọn và highlight tách nền ở hai theme.
- [x] Product title/price/meta/condition/hover dark dùng token đúng.
- [x] Footer highlights và header/search rõ màu/boundary.
- [x] Hộp thư toolbar40px, workspace/composer theo viewport.
- [x] Guest header320px hết tràn;21 regression/full lint/typecheck/build và browser/ảnh đạt.

[Bằng chứng](../../output/ui-0092/README.md), [task](archive/TASK-0092-ui-contrast-and-density.md). Backlog nghiệp vụ trước task giữ nguyên.

## UX/UI được duyệt — TASK-0091 / 2026-10-13

- [x] Hai theme sáng/charcoal, đổi ngay và lưu lựa chọn.
- [x] Outline icon/chrome và token các luồng chính/console.
- [x] Message icon/bell riêng, popover và chat nhanh desktop/mobile inbox.
- [x] Draft/tệp/retry/pending-send chung, owner reset và permission từ server.
- [x] Sản phẩm/đơn/notification entrypoint, quay lại giữ ngữ cảnh, focus/reduced-motion.
- [x] Frontend183/39, full lint/typecheck/build, browser fixture/mobile và runtimehealthUP.
- [ ] Provider thật, showcase prototype và backlog nghiệp vụ trước task tiếp nối.

[Bằng chứng](../../output/ui-0091/README.md), [task](archive/TASK-0091-approved-ui-quick-chat-themes.md).

## Năm task marketplace — TASK-0090 / 2026-10-13

- [x] Kiểm duyệt manual bài đăng và quản lý user KTV core (đợt1/2).
- [x] Chat/private media/read cursor/unread/search/retry và quan hệ chặn, giữ nghĩa vụ cũ.
- [x] Offer/counter/accept/reject/withdraw/expiry, checkout giá snapshot một lần, thông báo kết quả và đơn.
- [x] Frontend168, backend full regression +39 scoped, signed HTTP/browser desktop/mobile và quality gates.
- [x] Backup đã kiểm restore, V31/47 bảng,150 nguồn giữ; runtime5174/8080 cho người dùng thử.

[Test guide](../../output/phase34-runtime/README.md). Full UC68/UC74/AI/provider thật, audit viewer/dead-letter và canonical multi-seller checkout là backlog riêng. Các checklist chưa rollout/chat ở checkpoint cũ dưới đây mô tả trạng thái lúc bàn giao từng đợt.

## Kết nối đăng nhập local — TASK-0089

- [x] Origin/cổng frontend5174 đồng bộ, strictPort và restart backend; HTTP allow/deny, browser config/fake login, typecheck/lint/build đạt.
- [ ] Người dùng thử lại mật khẩu/Google thật sau tải lại trang. [Bằng chứng](../../output/login-origin-fix/README.md).

DB hiệnV30 khi kiểm0089; không chạy migration mới trong lượt sửa kết nối. Rollout/backup ở các checkpoint dưới mô tả phạm vi và trạng thái lịch sử, không coi restart là đã nghiệm thu dữ liệu/provider.

## Quản lý user KTV — TASK-0088 / 2026-10-12

- [x] Đợt 2 UC67 core, đọc UC11, thông báo account UC51/phần UC53/UC59–UC60 và audit nền UC77; API thật, scope/deadline/soft-state, nghĩa vụ/phản hồi cũ, bù hạn từ lỗi xác nhận.
- [x] Frontend157, backend220 pass/2skip, PostgreSQL83 khác nhau và browser KTV/customer/mobile theo [bằng chứng](../../output/phase2-runtime/README.md).
- [ ] Backup/restore và rollout V26–V30 vào database đang dùng cùng backend/frontend mới.
- [ ] Đợt 3: UC18 chat, UC47 unread/notification, UC10 chặn; participant/privacy, quota/media, retry/reconnect.
- [ ] Đợt 4: UC19 offer, giá/số lượng, expiry/revision/stock, accept race và UC23–UC25.
- [ ] Full UC74 xác nhận/xử phạt và pipeline uy tín/notification UC52 ngưỡng điểm; UC68/eKYC, audit viewer, dead-letter và tối ưu polling khi được triển khai.

[Thiết kế/rollout](../architecture/ACCOUNT_GOVERNANCE_V30_20261012.md). Các mục đợt 2 ở checkpoint TASK-0087 bên dưới là kế hoạch lịch sử đã tiếp nối; chat/offer vẫn chưa triển khai.

## Kiểm duyệt và phí đăng — TASK-0087 / 2026-10-12

- [x] DTO/UI KTV và Seller result/fee, dữ liệu thiếu trung thực, history/version/key.
- [x] V27 MANUAL_V27, mock receipt, cumulative fee, activation proof, reward tin đầu và notification outbox/read/target.
- [x] 148 frontend, 220 backend thường/2 skip, 65 PostgreSQL; lint/build/package và browser desktop/mobile riêng.
- [ ] Backup/restore và rollout V26/V27 vào DB đang dùng; kiểm provider/media thật theo phạm vi riêng.
- [ ] Đợt 2: UC67 user, UC11 lịch sử xử phạt/uy tín, UC51 thông báo, auth/restriction/old obligations và audit.
- [ ] Đợt 3: UC18 chat, UC47 unread/notification, UC10 chặn, participant/privacy/media/quota và reconnect.
- [ ] Đợt 4: UC19 offer, state/expiry/accept race và chốt price/quantity vào UC23–UC25.
- [ ] Kiểm thử xuyên suốt mỗi đợt; vận hành notification payload lỗi/dead-letter và Vite chunking khi có phạm vi tối ưu.

[Thiết kế/rollout](../architecture/MODERATION_LISTING_V27_20261012.md), [Ảnh và kiểm chứng](../../output/phase1-runtime/README.md). Các mục đợt 2–4 là kế hoạch, chưa triển khai.

## Kết quả audit auth — TASK-0084

- [x] Đọc mới yêu cầu UC01–UC04/UC09/UC67; kiểm UI/API/SMTP trên môi trường riêng; 29 frontend/42 backend tests, typecheck/lint/build/package đạt.
- [x] Buyer/Seller OTP qua UI và PostgreSQL xác nhận quyền/trạng thái/xu; lưu [báo cáo và bằng chứng](../../output/auth-audit-2026-10-11/AUTH_FUNCTIONAL_AUDIT.md), dọn môi trường test.
- [x] F1: email 2FA trước phát phiên khi cờ Buyer/Seller bật; password/Google, staff boundary đã kiểm local trong TASK-0086.
- [x] F2/F3: email/OTP/grant/new password và thu hồi mọi Bearer/cookie bằng cv; rollback/replay/concurrency/old-token có PostgreSQL tests.
- [x] F4 implementation: Google token/nonce, OTP link email trùng và registration Buyer/Seller không trùng user/xu/hồ sơ; cần provider E2E thật sau config/restart.
- [x] F6: expose Retry-After/CORS và countdown form tài khoản/Seller; 34 frontend/19 backend tests, quality gates và browser fixture hai vai trò đạt. [TASK-0085](archive/TASK-0085-registration-retry-countdown.md).
- [x] F5/F7 auth: structured restriction sau credential đúng, Back Seller click/Enter về bước tài khoản và giữ fields; Buyer OTP click qua Profile trên browser fixture.
- [x] TASK-0086: 64 backend/135 frontend tests, typecheck/scoped lint/build/package; [bằng chứng](../../output/auth-fixes-2026-10-11/README.md), dọn đúng dịch vụ test.
- [ ] Backup/restore rồi áp V26 vào database đang dùng; thử Google/Gmail thật và xác nhận authorized origin.
- [ ] UC09 bật/tắt bằng face và các phụ thuộc eKYC/KTV/quyền hạn chế từng hành động UC67 trong phạm vi tiếp nối được duyệt.

## SMTP local và relay — TASK-0083

- [x] Xác định lỗi localhost:1025, nạp Mailpit và dùng IPv4 trong config backend private.
- [x] Chuẩn hóa mật khẩu ứng dụng; kiểm recipient regex và nạp relay config vào container mới, giữ container cũ.
- [x] SMTP local/UI và Gmail STARTTLS/AUTH 235; không gửi email thật.
- [ ] Restart backend rồi xác nhận OTP Gmail và đăng ký qua UI; thư example.test nhận ở Mailpit.

## Auth đợt 1 — TASK-0082

- [x] Đọc code/đặc tả hiện hành, báo cáo phương án và nhận duyệt.
- [x] Code OTP email hai bước, giới hạn gửi/nhập, hủy/gửi lại và cleanup draft.
- [x] Buyer/Seller draft và thưởng 3.000 xu một lần trong transaction; mật khẩu mới thống nhất.
- [x] Cấu hình SMTP và [hướng dẫn Gmail](../setup/EMAIL_OTP.md), V25 giữ migrations/dữ liệu cũ.
- [x] PostgreSQL V25 clean/upgrade/negative/concurrency: 46 pass; backend 259 pass/26 skip và frontend 113 pass, lint/typecheck/build.
- [x] V25 local sau backup/restore, 47/47 fingerprints giữ nguyên, runtime health/catalog 200.
- [ ] Cấu hình SMTP local .env và xác nhận gửi Gmail thật khi có mật khẩu ứng dụng.
- [ ] Cập nhật báo cáo Google Docs/dictionary/DDL ERD từ checkpoint V24 sang V25 khi có phạm vi tài liệu tương ứng.
- [x] Code đợt 2: Google và quên mật khẩu OTP/thu hồi mọi thiết bị trong TASK-0086; provider thật và rollout V26 còn cần kiểm.
- [x] Đợt 3 phần login: thực thi cờ email 2FA Buyer/Seller trong TASK-0086.
- [ ] Đợt 3 tiếp nối: bật/tắt 2FA bằng face, eKYC và KTV duyệt hồ sơ Người bán.

## Diagram UC01–UC18 — TASK-0081

- [x] Đọc mới và kiểm đặc tả, chốt 2FA chỉ Người mua/Người bán; sửa source theo thứ tự thao tác và giữ mẫu.
- [x] Thay 34 ảnh được duyệt, thêm hai ảnh UC11, sửa đúng loại Sequence UC07; giữ Hình 3.3–3.38.
- [x] Kiểm native/backup/PDF và trực quan đủ 36 hình, đồng bộ [checkpoint](../requirements/use-cases-83/report-uc01-18/README.md) và [bàn giao task](archive/TASK-0081-diagrams-uc01-18.md).

## Tên và số caption theo chương — TASK-0080

- [x] Đọc mới chương, mẫu UC01–UC18 và tên UC; chuẩn hóa 322 caption, bảng/hình có bộ đếm riêng theo chương.
- [x] Backup/readback giữ bảng/ảnh/style/tab; kiểm PDF 322 caption, 166 ảnh cùng caption và chỉnh paragraph UC01 giữ ảnh/cỡ.
- [x] Kiểm cuối tài liệu và bàn giao [mapping caption hiện tại](../requirements/use-cases-83/chapter-captions/README.md); [task đã archive](archive/TASK-0080-chapter-table-figure-captions.md).
- Ngoài lượt caption: UC11 thiếu hai ảnh, Sequence UC07 đang dùng ảnh Activity; danh mục đầu báo cáo còn template. Thay ảnh phải hỏi người dùng theo quy trình.

## Report diagrams from UC19 — TASK-0079

- [x] Đọc mới nguồn; chốt RS-02 thành xem xét khóa dưới 80 và bổ sung EXC UC63 2b; đồng bộ native/local/diagram.
- [x] Kiểm/sửa mã giữ mẫu, render 130 PNG; chèn 118 ảnh mới và thay 12 ảnh được duyệt.
- [x] Kiểm native giữ nội dung/tab ngoài phạm vi và PDF trọn trang cho cả 130 hình. [Bằng chứng](../requirements/use-cases-83/report-from-uc19/README.md).
- [x] UC01–UC18 đã hoàn tất trong TASK-0081 sau khi đọc nguồn và được duyệt thay 34 ảnh/thêm UC11.

## Diagram workflow — TASK-0078

- [x] Ghi [quy trình hiện hành](../requirements/use-cases-83/SPECIFICATION_GUIDE.md#quy-trình-thực-hiện-do-người-dùng-quy-định--2026-10-10): kiểm đặc tả trước; hỏi khi luồng không hợp lý; sửa mã theo đặc tả hợp lý và giữ mẫu; xuất/chèn sau kiểm; hỏi trước khi thay ảnh có sẵn.
- [x] Kiểm cấu trúc tài liệu, 75 liên kết/anchor local và whitespace; đóng [task ghi quy trình](archive/TASK-0078-diagram-workflow.md).
- Các mục chèn tiếp UC01–UC78 bên dưới tuân thủ quy trình này, kể cả khi vị trí đã có hình.

## Compact diagram presentation — TASK-0077

- [x] Áp dụng mẫu trình bày gọn cho 166 source; giữ văn bản đầy đủ trong comment/tài liệu, kiểm lại nguồn/1.000 đường đi và render toàn bộ.
- [x] Sao lưu báo cáo và thay mười ảnh UC79–UC83 từ dưới lên, giữ kích thước tự nhiên nếu vừa trang.
- [x] Kiểm native và PDF cùng layout: mười hình/title/caption trọn một trang, giữ tỷ lệ và các nội dung khác.
- [ ] Dùng source gọn để chèn UC01–UC78 khi được yêu cầu; đọc lại vị trí/nguồn và kiểm PDF.

## Report diagram insertion — TASK-0076

- [x] Render lại/chèn 10 Activity–Sequence UC83→UC79 và sao lưu native trước ghi.
- [x] Giữ đặc tả/tab khác/ảnh cũ; kiểm title/image/caption cùng trang trên PDF có cùng layout.
- [ ] Chèn UC01–UC78 khi được yêu cầu; đọc vị trí mới, giữ tỷ lệ, không phóng ảnh nhỏ, kiểm PDF.

## Use-case documents and diagrams — TASK-0075

- [x] Đọc lại trực tiếp nguồn hiện hành, đủ 83 đặc tả/830 trường và xác nhận nguồn không đổi ở lần đọc cuối.
- [x] Viết 83 tài liệu, 83 Activity và 83 Sequence ở mức yêu cầu; mapping 78 → 83, hướng dẫn, rà soát và bàn giao.
- [x] Kiểm nội dung/nhánh/liên kết và render 166 PNG local; giữ bộ 78 làm lịch sử.
- [ ] Thống nhất các ghi chú nguồn còn mở RS-01/02/03/05/06/07/08 khi hoàn thiện báo cáo; không coi RS-06/07 là chính sách mới cần tự quyết.
- [ ] TASK-0076 đã chèn UC79–UC83; UC01–UC78 chưa chèn. TASK-0075 chỉ chuẩn bị tài liệu/source.
- [ ] Khi chuyển sang triển khai: viết sequence chi tiết thiết kế và đối chiếu code/API/database hiện hành; tài liệu không thay bằng chứng nghiệm thu.

## Report language and directories — TASK-0074

- [x] Thay mô tả loại dữ liệu/mã nguồn khó hiểu bằng công dụng; làm rõ 165 trường, 47 bảng và 6 mô tả tổng quan.
- [x] Điền 52 từ viết tắt và chuẩn hóa phạm vi 7 actor theo 83 đặc tả; giữ mã AT01–AT07, KTV là Kỹ thuật viên theo báo cáo.
- [x] Sao lưu native đầy đủ, giữ bảng có sẵn và widths; kiểm nội dung/kiểu chữ/căn lề, ghim headers hai bảng danh mục. Không sửa code/schema hoặc chính sách.
- [ ] Kiểm trang PDF khi Google Drive cho phép export; lần này vẫn 403.

## Authentication simplification — TASK-0073 / V24

- [x] Bỏ refresh sessions khỏi code/database/report, chuyển login sang thời hạn cố định, restore không gia hạn; giữ OTP/quick-auth và mọi bảng còn lại.
- [x] Backup/restore trước rollout; giữ 47 fingerprints/checksums, PostgreSQL negative/upgrade tests, runtime và frontend regression; xuất dictionary/ERD V24.
- [x] Cập nhật báo cáo còn một tổng hợp + 47 bảng chi tiết/895 trường; 48 headers ghim, native content/styles khớp, giữ bản sao đầy đủ.
- [ ] Kiểm trang khi PDF export khả dụng; hiện 403.
- [ ] Quyết định thay nơi lưu OTP/quick-auth trước khi bỏ `auth_challenges`; Gmail gửi mã không thay dữ liệu trạng thái. Đề xuất gộp eKYC trước đây chưa triển khai.

Nguồn hiện hành là [V24](../architecture/DATABASE_AUTH_SIMPLIFICATION_V24_20261009.md). Các số liệu V23 dưới đây giữ lịch sử.

## Database design report — TASK-0072

- [x] Viết danh sách 48 bảng và 48 bảng chi tiết theo mẫu Google Docs, loại Flyway; native backup, kiểm đủ nội dung và căn lề/ghim tiêu đề.
- [ ] Kiểm bố cục trang khi PDF/HTML export hoạt động trở lại; lần này Google Drive trả 403.

## Public use-case policies — TASK-0071

- [x] [TASK-0071](archive/TASK-0071-public-use-case-policies-and-coins.md): sửa đủ 83 đặc tả theo cách dùng/chính sách, điểm uy tín và xu; sao lưu native, đồng bộ tên/tổng quan, đọc lại cấu trúc và nội dung.
- [ ] Kiểm tra trang PDF khi tải tệp xuất hoạt động trở lại; chưa xác nhận bố cục toàn báo cáo.
- [ ] Khi được yêu cầu triển khai: đồng bộ OTP/Google, uy tín 100, mức trừ và hạn chế do KTV quyết định, thưởng xu theo mốc/đánh giá, xu dùng cho phí giao đến 0 đồng và hoàn xu riêng voucher; rà code/V23/diagram theo chính sách mới.

## Database consolidation — TASK-0070 / V23

- [x] Gộp 104 nguồn thành 48 bảng nghiệp vụ; preserve count/hash, backend mappings/guards, backup/restore và migrate/validate local og_shop.
- [x] Regression/Hibernate/runtime và ERD DDL verification; xuất dictionary 48 bảng và schema V22.
- [x] Đưa nội dung 48 bảng/907 trường vào chương database của báo cáo theo TASK-0072.
- [ ] Đưa ERD vào báo cáo khi người dùng chuẩn bị hoặc yêu cầu dựng sơ đồ.
- [ ] Nối API/UI/worker canonical theo 83 UC; chuyển service khỏi lớp og_compat theo từng module trước khi loại view. Không double-write money/history.

Các mục expansion V21 bên dưới ghi phần nền và công việc ứng dụng tiếp nối; tên nguồn hiện hành đối chiếu theo [V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md).

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — TASK-0069

- [x] V15–V21/G01–G12: schema, invariants/transaction primitives và mapping tương thích; backup/restore, PostgreSQL regression, áp dụng/validate local và handoff 83 UC.
- [ ] Nối API Identity/eKYC/KTV/Seller vào V15, kiểm soát legacy bypass theo migration mode.
- [ ] Nối revision/AI/duyệt/fee và public catalog; checkout group/quantity/voucher/points/zero payment.
- [ ] Nối payment IPN/group allocation, component refund/release, delivery/case deadlines/cleanup, notification/report workers và E2E sandbox. Theo [hợp đồng transaction](../architecture/DATABASE_EXPANSION_V21_20261009.md), không double-write legacy money.


## Report priority — checkpoint 2026-10-09

- [x] [TASK-0065](archive/TASK-0065-report-policy-checkpoint-20261009.md): đồng bộ chính sách báo cáo 83 UC và lưu công việc cuối ngày; đối chiếu native nội dung/định dạng, giữ tab gốc.
- [x] [TASK-0066](archive/TASK-0066-use-case-simplification-plan.md): đọc lại nguồn, tạo và kiểm tra tab sao lưu native; chuẩn bị kế hoạch tinh giản 83 đặc tả.
- [x] [TASK-0067](archive/TASK-0067-simplify-83-use-case-specifications.md): rút gọn đủ 83 đặc tả, giảm 17,3% ký tự, giữ 10 trường/mã BR/tab sao lưu; đọc lại native và kiểm bố cục PDF mẫu.
- [x] [TASK-0068](archive/TASK-0068-database-coverage-83-use-cases.md): rà cấu trúc live V1–V14 và code theo đủ 83 UC; [ma trận/thiết kế mở rộng](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), 12 nhóm dùng chung và thứ tự migration/kiểm chứng. Chỉ phân tích, chưa triển khai schema.
- [ ] Đồng bộ bản báo cáo rút gọn với diagram/database/code khi được yêu cầu; không gộp vào công việc rút gọn câu chữ.

## Completed foundation

- [x] Tạo monorepo Backend, Frontend, Database, Reference, Docs và Infra.
- [x] Thiết lập quy trình làm việc local (không còn theo dõi trên Git).
- [x] Thiết lập Maven/npm lock, local quality scripts và GitHub Actions.
- [x] Kiểm tra Backend, Frontend, documentation và Compose config.
- [x] Push baseline lên `origin/main` tại commit `40e3f8c`.

## P0 — Foundation

- [x] Đưa tài liệu yêu cầu đã duyệt vào `reference/requirements/`.
- [x] Đưa tài liệu database đã duyệt vào repository.
- [x] Tạo `V1__initial_schema.sql` từ schema đã được xác nhận (tích hợp pgvector và seller_biometrics).
- [x] Triển khai UI Batch 1: Auth (Login, Register, Forgot Password, Profile) và eKYC Seller Verification.
- [x] Tích hợp AI Microservice eKYC độc lập (services/ekyc-service: YOLOv11n + VietOCR Transformer + Gemini Flash + ArcFace 512-d).
- [ ] Hoàn thiện `TRACEABILITY_MATRIX.md` theo UC, BR và NFR.
- [x] Chốt authentication: DP-01 từng dùng refresh rotation; TASK-0073 thay bằng access JWT/cookie có cùng thời hạn cố định, không gia hạn.

## P1 — Core flow (các vertical slice Backend + Frontend, bắt buộc trước tối ưu hạ tầng)

- [x] TASK-0010: Database pricing, negotiation, chat cursor và outbox baseline.
- [x] TASK-0011: Khóa thứ tự core-feature-first và acceptance gate trước Redis.
- [x] TASK-0012: MVP Seller Activation — bỏ qua eKYC thật, kích hoạt Seller trực tiếp và an toàn cả Backend và Frontend.
- [x] TASK-0013: Catalog — Seller quản lý tin đăng + Buyer duyệt/tìm kiếm/xem chi tiết.
- [ ] TASK-0018: Marketplace UI/UX — Figma design system, responsive desktop/mobile, Light/Dark, VI/EN, sample data và prototype theo role. **IN PROGRESS**.
- [ ] TASK-0014: Buy Now — UI checkout + Backend reservation/order chống bán trùng. **PAUSED BEFORE IMPLEMENTATION** đến khi TASK-0018 được duyệt.
- [ ] TASK-0015: Mock Payment — Thanh toán giữ tiền mô phỏng + màn hình đơn hàng Buyer/Seller.
- [ ] TASK-0016: Fulfillment cơ bản — Quản lý vận chuyển + UI trạng thái đơn end-to-end.
- [ ] TASK-0017: Communication REST & Offer — Chat hội thoại, cursor lịch sử và giao dịch trả giá (thực hiện sau luồng mua trực tiếp).

Thứ tự triển khai trong P1:

1. TASK-0012 — Seller activation MVP.
2. TASK-0013 — Catalog: Seller quản lý tin đăng + Buyer duyệt/tìm kiếm/xem chi tiết.
3. TASK-0018 — Thiết kế UI/UX Figma và duyệt trực quan trước khi tiếp tục code chức năng.
4. TASK-0014 — Buy Now: UI checkout + Backend reservation/order chống bán trùng.
5. TASK-0015 — Mock payment + màn hình đơn hàng Buyer/Seller.
6. TASK-0016 — Fulfillment cơ bản + UI trạng thái đơn end-to-end.
7. Chat/Offer thực hiện sau luồng mua trực tiếp.
8. Redis, WebSocket, outbox worker và external integrations tiếp tục deferred.

## P2 — Tích hợp dịch vụ bên ngoài

- [x] TASK-0005: Identity và eKYC Security Baseline.
- [ ] TASK-0006: Gmail SMTP/Mailpit và OTP reset password.
- [ ] TASK-0007: Google OAuth2/OIDC.
- [ ] TASK-0008: Cloudinary avatar.
- [ ] TASK-0009: Cloudinary product/evidence media.

Các adapter bên ngoài không được làm thay đổi quy tắc nghiệp vụ cốt lõi. Khi provider chưa sẵn sàng, core flow dùng adapter local/mock phù hợp để tiếp tục được kiểm thử.

## P3 — Realtime và tối ưu hệ thống

- [ ] WebSocket single-instance cho chat sau khi REST chat/offer đã ổn định.
- [ ] Outbox worker idempotent cho notification/realtime.
- [ ] Redis cache cho hot reads chỉ sau khi có số đo cho thấy cần cache.
- [ ] Redis Pub/Sub chỉ khi chạy nhiều backend instance hoặc có yêu cầu phân phối realtime tương đương.

Chỉ bắt đầu P3 khi:

- UC-03 đến UC-14 có happy path và negative path cơ bản đã vượt kiểm thử.
- Authorization/ownership, state transition và message idempotency đã được kiểm thử.
- Accept offer/checkout đồng thời đã có concurrency test và không bán trùng.
- Frontend hoàn thành ít nhất một luồng đăng tin → chat → offer → order → payment mock.
- PostgreSQL pagination đủ đúng và đủ nhanh cho dữ liệu kiểm thử; Redis bị tắt không làm hỏng luồng đọc/ghi cốt lõi.

## P4 — Trust and administration

- [ ] UC-15 Đánh giá và uy tín.
- [ ] UC-16 Báo cáo và kiểm duyệt.
- [ ] UC-17 Quản lý user và quyền.
- [ ] UC-18 Dashboard quản trị.
- [ ] UC-19 Thông báo và audit.

## Deferred beyond MVP

- AI định giá hoặc gợi ý sản phẩm.
- Voucher, điểm thưởng và gamification.
- Hoàn tiền từng phần.
- AI tự động thương lượng thay người dùng.
- Tích hợp KYC hoặc vận chuyển production.
