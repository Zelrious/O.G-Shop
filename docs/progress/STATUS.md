# Project Status

## Hoàn thiện Marketplace và Tích hợp Vận chuyển GHN — TASK-0098 / 2026-10-20

- **Nghiệp vụ hoàn thành (7/7 mục)**:
  1. *Kiểm duyệt bài đăng (UC73)*: Modal xác nhận duyệt hàng loạt, hiển thị danh sách & gỡ tin, theo dõi tin đã xem xét media (`inspectedIds`), cảnh báo trực quan tin chưa xem, bảo toàn optimistic locking và báo kết quả một phần khi conflict.
  2. *Quản lý tài khoản KTV (UC67)*: Phân định rõ ràng `RESTRICT` vs `LOCK` vs `DEACTIVATE`; thông báo rõ tài khoản bị khóa vẫn đăng nhập và xử lý nghĩa vụ cũ được; tính thời hạn theo `serverTime` từ API và cố định khi retry.
  3. *Chat trực tiếp (UC18)*: Nhãn tự động cập nhật định kỳ (2.5 giây/lần), bảo đảm quyền riêng tư, draft, chống gửi lặp bằng `client_message_id`, hiển thị rõ trạng thái gửi/lỗi/ngoại tuyến.
  4. *Offer trả giá (UC19)*: Giữ nguyên giá niêm yết trên Mall/Marketplace; bổ sung lựa chọn rõ ràng giữa Giá niêm yết và Giá thương lượng đã chấp nhận khi vào checkout từ luồng thường (chỉ server xác thực offer hợp lệ); format tiền tệ `xxx.xxx.xxx ₫`, chặn chọn ngày quá khứ, báo lỗi tại từng trường nhập.
  5. *Nhận & áp dụng voucher*: Trả về lý do từ chối cụ thể từ server (chưa đủ min order kèm số tiền, hết hạn, không áp dụng sản phẩm, chưa đến giờ, chưa có trong ví); chọn voucher sở hữu trong checkout; giữ ngữ cảnh sản phẩm + offer + voucherCode khi mở ví từ checkout.
  6. *Tạo & phát voucher (UC69/UC70)*: Form Admin format tiền VND dễ đọc (`50.000 ₫`, `200.000 ₫`), kiểm tra ràng buộc ngày (`startTime < endTime`), % giảm (1-100%, max discount bắt buộc), scope sản phẩm; giữ luồng KTV phát 1-100 người; **giữ nguyên phân quyền hiện tại** (Admin tạo chương trình, KTV phát voucher).
  7. *Tích hợp GHN*: Cước vận chuyển thực tế tính động qua `FulfillmentShippingFacade.calculateShippingFee()` gọi GHN `/v2/shipping-order/fee`, tự động fallback 30k khi timeout/offline, snapshot vào `OrderEntity.shippingFee`; giao diện Người bán tạo vận đơn modal, in mã A5 link token; giao diện Người mua/Người bán xem tracking GHN; cập nhật tài liệu `docs/modules/fulfillment.md`.
- **Kiểm thử & Chất lượng**:
  - Backend: `CheckoutServiceTest` (10/10 PASS), `Fulfillment*Test` (71 PASS), `mvn test-compile` BUILD SUCCESS.
  - Frontend: 46 test files / 218 unit tests PASS (100%), `npm run typecheck` PASS (0 errors), `npm run lint` PASS (0 warnings).
  - Không thay đổi phân quyền; không tác động schema database ngoài kế hoạch; không push/commit/deploy production.

## Commit delivery — TASK-0097 / 2026-10-13


Đóng gói260 file thay đổi ban đầu thành14 commit trên `feat/marketplace-console-refactor-20261013`, từ HEAD `2061aa8` của nhánh auth (kế thừa14 commit so với main). Backend snapshots của commits2–7 biên dịch main/test đạt; frontend snapshots8–13 typecheck đạt. Hồi quy bản đã commit:204 frontend tests/43 files, lint và TypeScript/Vite build đạt; backend387 discovered,226 passed,161 skipped theo điều kiện môi trường,0 failure/error. Không chạy migration trên database ứng dụng, không push/merge. Logic giữ nguyên; chỉ chuẩn hóa EOF/EOL và thêm metadata bàn giao. Các nhật ký/ảnh QA và liên kết tới output/task trong tài liệu là bằng chứng local, không được force-add vào Git.

## Console selects and eKYC — TASK-0096 / 2026-10-13

KTV/Admin:18 native selects chuyển sang shared Select; upward popover neo sát trigger6px, theo dõi vị trí khi sidebar chuyển động; sidebar chờ320ms, transition360ms. Cột queue360–400px; eKYC list/detail và masked list; voucher tự chọn campaign đầu API, bỏ option rỗng. API/handler/rules giữ nguyên.204 tests/43 files, lint/TypeScript/build đạt;82 browser checks không overflow/JS error, menu lệch ngang0px/gap6px. [Ảnh và giới hạn](../../output/ui-0096/README.md).

## Console reports and navigation — TASK-0095 / 2026-10-13

Admin: thẻ doanh thu lớn bên trái gồm biểu đồ/so tháng, ba KPI phụ xếp dọc và bằng đáy0px. KTV/Admin: utilities chung hàng heading, hover tách focus để sidebar không thu khi đổi trang; màu surface/selected theo theme, câu chữ tranh chấp đơn giản. Dữ liệu minh họa và API/handler/export giữ nguyên.201 frontend tests, final5 regression, full lint/TypeScript/build đạt;60 browser checks hai theme1366/1440/1920/390 không overflow/JS error. [Ảnh và giới hạn](../../output/ui-0095/README.md).

## KTV UI system — TASK-0094 / 2026-10-13

Thêm shared Select/Pagination/PageHeader; token surface/popover/disabled và semantic badge/button, sửa contrast card eKYC/khiếu nại, custom voucher/filter/page-size dropdown, phân trang 0 kết quả. Giữ sidebar và tất cả API/handler nghiệp vụ. 195 full-suite pass + 2 eKYC tests mới; final focused26, lint/TypeScript/build đạt. Browser48 geometry +8 contrast +2 dropdown checks, không overflow/JS error; sampled contrast light4,65/dark5,76. [Báo cáo và giới hạn](../../output/ui-0094/README.md).

## Voucher thật và thao tác hàng loạt — TASK-0093 / 2026-10-13

- Completed: customer ví/nhận/owned checkout; Admin campaigns/version/revisions; KTV filter real audience/select1–100/preview/reason/confirm/per-item result/revalidation/retry; private outbox/audit/reminders. Thay voucher localStorage pages bằng API. Light/dark/outline/mobile theo UI hiện có.
- Verified:191 frontend pass;303 distinct executed backend pass qua full+corrective V32 assertion rerun,84 backend skipped. Final20 UI regression/full lint/TypeScript/Vite/package pass; genuine QA Admin→KTV grant2/revoke2→customer claim,390/320/768 no overflow,9 signed HTTP. [Evidence](../../output/vouchers-0093/README.md), [API](../api/VOUCHERS_API.md).
- Runtime: frontend5174 retained; backend8080 V32 healthUP;47 tables. Final private backup restore, clone upgrade và app migration giữ150/150 sources. Không grant/order trên app để QA/reset/commit/push. QA services/tab/container đã dọn khi bàn giao; PG/Mailpit app giữ.
- Incomplete ngoài hai voucher tasks: full xu spending/refund/history pagination, canonical multi-seller checkout, central audit viewer/providers thật. Blocked:none. Next: Admin tạo/bật chương trình, KTV cấp theo bộ lọc; customer mở Voucher và xu. [Task](archive/TASK-0093-vouchers-and-bulk-distribution.md). Checkpoints dưới giữ lịch sử.

## Sửa tương phản và khoảng trống — TASK-0092 / 2026-10-13

- Completed: bốn vùng ảnh người dùng: hero/trust, product dark text, footer highlights, đầu hộp thư; thêm header/search và guest320px. Hero1440 giảm386,9→234,4px (39,4%); dark title/price12,83:1.
- Verified:21 regression/4 files, full lint, TypeScript/Vite build đạt. Browser light/dark, product/footer, hero390/320 và inbox desktop/mobile; geometry/contrast/ảnh có trong [bằng chứng](../../output/ui-0092/README.md). Inbox dùng fixture5192, không gửi chat/offer/order thật.
- Incomplete ngoài phạm vi: providers/backlog nghiệp vụ trước task. Blocked:none. Next: tải lại5174 để xem. Runtime hiện có giữ chạy; QA tab/fixture và viewport đã dọn. Không reset data/commit/push. [Task](archive/TASK-0092-ui-contrast-and-density.md).

## UX/UI được duyệt — TASK-0091 / 2026-10-13

- Completed: React hai theme sáng/charcoal, SVG outline, message icon và chuông riêng/popover, chat desktop nổi/mobile inbox, draft/tệp/retry/pending-send chung và privacy theo owner, entrypoint sản phẩm/đơn/notification, scroll/focus/motion và console theo theme.
- Verified: frontend183/39 files, full frontend lint, TypeScript/Vite build đạt. Browser fixture desktop/light/dark/read/offer modal/draft/send/handoff, mobile320/390 không tràn ngang, Seller/KTV shell. [Before/after, ảnh và giới hạn](../../output/ui-0091/README.md).
- Runtime: mở lại frontend5174, container PG/Mailpit local hiện có và artifact backendV31/private config; healthUP, validate31/no migration necessary. Không reset/seed app DB hoặc gửi chat/offer/order thật; không commit/push.
- Incomplete ngoài phạm vi: provider thật, showcase prototype và backlog nghiệp vụ trước task. Blocked:none. Next: tải lại5174, đăng nhập và thử theme/chat. [Task](archive/TASK-0091-approved-ui-quick-chat-themes.md). Checkpoint dưới giữ lịch sử.

## Hoàn tất năm task marketplace — TASK-0090 / 2026-10-13

- Completed: kiểm duyệt manual KTV (UC73), quản lý account KTV core (UC67), kiểm thử, chat UC18/quan hệ chặn UC10 và offer UC19 nối checkout. Thông báo chat/media/offer/kết quả/đơn từ offer qua outbox, đọc và điều hướng UC47/UC59–UC60. UI desktop/mobile theo design system hiện có.
- Verified: frontend168/36 files; backend regression373 (372pass/1 Windows skip), cuối39/39 gồm27 PG chat/offer và package; tổng374 tests khác nhau/373pass/1skip qua các lượt. Typecheck/scoped lint/build đạt. Browser Buyer/Seller/CHAT ban + 8 signed HTTP checks; mobile chat/blocks/checkout không tràn ngang. [API](../api/CHAT_OFFER_API.md), [thiết kế](../architecture/CHAT_OFFER_V31_20261013.md), [test guide/ảnh](../../output/phase34-runtime/README.md).
- Rollout: app DB V31/47 bảng; backup cuối restore và migration QA giữ150/150 nguồn, app migration cũng giữ150/150. Backend8080/frontend5174 chạy bản mới; origin5174 và providers/private settings giữ. Không seed staff vào app DB, không reset/commit/push.
- Incomplete ngoài năm task: full UC68/UC74/AI, audit viewer, canonical multi-seller checkout và provider thật; không coi local QA là nghiệm thu Cloudinary/VNPay/Google/Gmail thực. Blocked: none. Next: người dùng thử tài khoản hiện có tại5174 theo test guide. [Task](archive/TASK-0090-chat-offer-completion.md). Các checkpoint phía dưới giữ lịch sử.

## Sửa đăng nhập local — TASK-0089 / 2026-10-12

- Completed: sửa CORS origin5173→5174 trong private config, cố định Vite5174/strictPort, template/README đồng bộ; restart đúng backend8080. [Before/after và ảnh](../../output/login-origin-fix/README.md).
- Verified: Google config/preflight login5174 từ403→200, credentials/ACAO đúng; origin5199 vẫn403. Browser Google button tải được, fake login nhận lỗi xác thực backend thay Failed to fetch. Typecheck/scoped lint/build đạt.
- Incomplete: người dùng thử lại credentials/Google thật; không nghiệm thu login thật từ tài khoản giả. App DB đãV30 trước chẩn đoán (runtime người dùng đã chạy sau bàn giao0088), restart validate30/no migration necessary; không chạy migration mới trong0089.
- Blocked:none. Next: tải lại trang5174/login. Backend8080 chạy background cùng classpath/private settings, frontend5174 giữ chạy; dừng đúng backend cũ khi chạy lại trong IntelliJ. Không commit/push. [Task](archive/TASK-0089-local-login-origin.md). Checkpoint dưới là lịch sử.

## Quản lý user KTV — TASK-0088 / 2026-10-12

- Completed: Đợt 2 UC67 core: API KTV/customer-only, tra cứu/hồ sơ/uy tín/nghĩa vụ, scope/thời hạn, khóa/ngừng mềm/gỡ/kích hoạt; giữ login/nghĩa vụ cũ, phản hồi hồ sơ, bù deadline đúng lỗi xác nhận; audit/version/key/locks/outbox và UI desktop/mobile. [Thiết kế/rollout](../architecture/ACCOUNT_GOVERNANCE_V30_20261012.md), [API](../api/ACCOUNT_MANAGEMENT_API.md), [Bằng chứng](../../output/phase2-runtime/README.md).
- Verified: 157 frontend/34 files pass; 9 account UI kiểm lại sau thay đổi cuối; tsc/lint/build pass. Backend 222 chạy, 220 pass/2 skip và package pass. 83 PostgreSQL tests khác nhau đạt qua các lượt V30: 17 governance, 34 expansion và 32 listing/category/moderation/migration; service compensation kiểm lại trong test expansion. Browser KTV + chủ tài khoản bị khóa: notice/read/target, login lại, old-order detail, official response; modal/status/response mobile390×844 không tràn ngang. Docs/manifest có bằng chứng và giới hạn.
- Incomplete: V26–V30 chưa áp app DB; full UC74/UC68, notification ngưỡng điểm, chat/block/offer, audit viewer/dead-letter và provider thật tiếp nối. Legacy auth LOCKED/SUSPENDED cần ACTIVATE; deleted_at cũ không có restore UI. Không commit/push.
- Blocked: none trong phạm vi code đợt 2. Next: đợt 3 chat UC18/UC47/UC10 theo kế hoạch; rollout DB riêng sau backup/restore theo tài liệu. QA runtime/container/tab đã dọn, app PostgreSQL/Mailpit giữ nguyên. [Task](archive/TASK-0088-ktv-user-management.md). Các checkpoint dưới đây giữ lịch sử.

## Kiểm duyệt và phí đăng — TASK-0087 / 2026-10-12

- Completed: Đợt 1 đã được duyệt: DTO/UI đối chiếu thật, history, chờ phí server, mock-pay mở bán, phí cộng dồn, thưởng tin đầu một lần, thông báo bền vững/read/điều hướng. [Thiết kế/rollout](../architecture/MODERATION_LISTING_V27_20261012.md), [Ảnh và kiểm chứng](../../output/phase1-runtime/README.md).
- Verified: frontend 148/31 files; backend 222 chạy, 220 pass/2 skip, 0 lỗi; 65 PostgreSQL tests khác nhau đạt sau kiểm lại 33 expansion. Lint/tsc/Vite/package đạt, cảnh báo bundle lớn còn có sẵn. Browser KTV/Seller và mobile 390×844 trên fixture riêng đạt; video/provider thật chưa kiểm.
- Incomplete: V26/V27 chưa apply database ứng dụng; user/chat/offer, AI/full eKYC/canonical checkout và provider thật tiếp nối. Notification chưa có dead-letter tự động. Không commit/push.
- Blocked: none trong phạm vi code đợt 1. Next: backup/restore rồi rollout theo tài liệu; triển khai đợt 2 quản lý user khi được duyệt. [Task](archive/TASK-0087-moderation-fees-and-notifications.md). Checkpoint bên dưới giữ lịch sử.

## Sửa F1–F5/F7 auth — TASK-0086 / 2026-10-11

- Completed: OTP login theo cờ Buyer/Seller, reset OTP/grant/new password, credential version thu hồi mọi phiên, Google token/nonce/link/registration, lỗi khóa sau credential đúng, Back Seller và race restore; Web Client ID đã cấu hình private. [Trước/sau và bằng chứng](../../output/auth-fixes-2026-10-11/README.md).
- Verified: 64 backend đạt, 0 lỗi/skip (29 PostgreSQL, 3 SMTP transport, 3 Google RSA/JWK local); 135 frontend đạt; typecheck/scoped lint/build/package đạt. V26 clean/upgrade giữ fixture; OTP concurrent chỉ một winner, reset rollback/replay và old Bearer/cookie bị chặn. Browser HTTP fixture kiểm Back Seller click/Enter, Buyer OTP click, login OTP và reset ba bước. Docs 9 core/8 module, 13 tài liệu/238 liên kết local và whitespace phạm vi auth đạt.
- Incomplete: V26 chưa áp vào database đang dùng; Google/Gmail và origin thực chưa nghiệm thu. UC09 bật/tắt bằng face, eKYC/KTV và full UC67 tiếp nối. Reset mail queue chưa durable; Vite giữ cảnh báo chunk lớn.
- Blocked: none cho code/kiểm local. Next: backup có kiểm restore, restart backend để áp V26/nạp Client ID, tải lại frontend và thử Google/email sở hữu theo [setup](../setup/GOOGLE_AUTH.md). Dịch vụ/container/tab test đã dọn, dữ liệu/container người dùng giữ nguyên. [Task](archive/TASK-0086-auth-audit-remaining-findings.md). Các checkpoint dưới đây giữ lịch sử.

## Sửa thời gian chờ đăng ký — TASK-0085 / 2026-10-11

- Completed: sửa F6 bằng expose Retry-After và countdown ở form tài khoản/Seller; đồng bộ thời điểm nhận lỗi, giữ dữ liệu và chặn submit khi đang chờ. [Bằng chứng](../../output/auth-retry-fix-2026-10-11/README.md).
- Verified: 34 frontend/19 backend tests đạt; typecheck/scoped lint/build/package đạt. Browser Buyer/Seller nhận 429, hiện 5 giây, disable rồi enable và chuyển OTP sau retry; mốc 60 giây được kiểm bằng fake clock/MockMvc.
- Incomplete: chưa chạy lại PostgreSQL/SMTP E2E hoặc gửi Gmail trong lượt sửa F6; các F1–F5/F7 của TASK-0084 vẫn còn. Vite giữ cảnh báo chunk lớn có sẵn.
- Blocked: none đối với F6. Next: restart backend bản mới/tải lại frontend; tiếp nối các phát hiện khác theo phạm vi được duyệt. [Task](archive/TASK-0085-registration-retry-countdown.md).

## Kiểm tra ba luồng auth — TASK-0084 / 2026-10-11

- Completed: đọc mới đặc tả SRC-08 UC01–UC04 và phụ thuộc UC09/UC67; review code, browser desktop/mobile và API/SMTP/database riêng; [báo cáo bảy phát hiện](../../output/auth-audit-2026-10-11/AUTH_FUNCTIONAL_AUDIT.md).
- Verified: 29 frontend/42 backend tests đạt, gồm 12 PostgreSQL và 3 SMTP; typecheck/lint/build/package đạt. Buyer/Seller OTP tạo UC83/BUYER, một khoản 3.000 xu; Seller PENDING_EKYC với hai địa chỉ riêng. Đã dọn đúng dịch vụ/container test.
- Incomplete: chức năng Google, reset OTP/thu hồi mọi thiết bị và login 2FA; phản hồi khóa, chờ 429/CORS và nút quay lại Seller cần sửa. Một lần OTP Buyer click reload chưa xác định nguyên nhân; Enter và Seller OTP click đạt. Không có bằng chứng Gmail thật hay eKYC/KTV hoàn chỉnh.
- Blocked: none cho audit. Source nghiệp vụ/migration và dữ liệu đang dùng được giữ; số tests xanh không thay nghiệm thu UC.
- Next: duyệt triển khai theo thứ tự sửa và tiêu chí trong báo cáo. [Task](archive/TASK-0084-auth-functional-audit.md).

## SMTP local Mailpit — TASK-0083 / 2026-10-11

- Completed: cấu hình private backend dùng 127.0.0.1; mật khẩu ứng dụng bỏ khoảng trắng phân nhóm; Mailpit mới nạp Gmail relay chỉ recipient được chọn, container cũ giữ nguyên dữ liệu.
- Verified: SMTP nhận một thư example.test vào hộp thư local, UI 200, Gmail STARTTLS/AUTH 235. Không gửi email thật hoặc tạo tài khoản trong lượt chẩn đoán.
- Incomplete: nhận OTP Gmail và đăng ký qua UI sau khi backend đọc lại cấu hình.
- Blocked: none đối với kết nối SMTP; backend cần restart từ môi trường đang dùng backend/.env.
- Next: restart backend, đăng ký bằng email sở hữu và email example.test. [Bàn giao](archive/TASK-0083-mailpit-local-smtp.md).

## Cải tổ đăng ký/đăng nhập đợt 1 — TASK-0082 / 2026-10-10

- Completed: code đăng ký hai bước OTP, Buyer/Seller draft, thưởng 3.000 xu một lần; mật khẩu mới thống nhất và login tài khoản cũ; SMTP adapter/form resend/cancel, V25 và [hướng dẫn Gmail](../setup/EMAIL_OTP.md). Giữ deadline phiên V24.
- Verified: backend 285 tests: 259 pass/26 skip, 0 failures/errors, gồm 46 PostgreSQL; frontend 113 pass, auth cuối 22 pass, typecheck/lint/build đạt. SMTP transport localhost đạt. V25 local đã backup/restore/migrate/validate; 47/47 fingerprints giữ nguyên, 47 bảng nghiệp vụ/901 trường. JAR mới runtime health/categories/products 200; chưa bật SMTP trả 503, không tạo user/challenge.
- Incomplete: Gmail thật chờ thông tin gửi/mật khẩu ứng dụng trong local .env; Google, reset OTP, 2FA và eKYC/KTV ngoài đợt 1. Báo cáo Google Docs/dictionary/DDL ERD V24 chưa tự chuyển thành V25.
- Blocked: none đối với code/kiểm thử/migration đợt 1; Docker đã hoạt động lại sau khi người dùng khởi động lại.
- Next: cấu hình SMTP theo hướng dẫn và restart backend để dùng bản mới; tiếp tục đợt 2–3 theo phạm vi được duyệt. [Bàn giao](archive/TASK-0082-email-otp-registration.md). Container và runtime kiểm thử riêng đã dọn, backup local được giữ.

## Diagram UC01–UC18 — TASK-0081 / 2026-10-10

- Completed: đọc mới đặc tả, hỏi/chốt RS-03 thành 2FA chỉ Người mua/Người bán; đồng bộ UC03/UC09 và sửa 36 source. Thay 34 ảnh được duyệt, thêm hai ảnh UC11, sửa Sequence UC07; giữ Hình 3.3–3.38. [Bằng chứng/PNG](../requirements/use-cases-83/report-uc01-18/README.md).
- Verified: 830 trường local/native, 36 luồng chính/236 lượt nhánh/25 kiểm nhân quả; 36 PNG, native giữ 132 ảnh ngoài phạm vi/154 bảng/322 caption/ba tab. PDF 36 ảnh/heading/caption trọn trang và đúng cỡ, kiểm trực quan ảnh dài; backup bốn tab và section QA giữ nguyên.
- Incomplete: none trong UC01–UC18. Blocked: none. Next: dùng source/caption hiện hành; danh mục đầu báo cáo còn template ngoài lượt này. Không đổi nghiệm thu phần mềm. [Task](archive/TASK-0081-diagrams-uc01-18.md).

## Caption theo chương — TASK-0080 / 2026-10-10

- Completed: tên diagram dùng “Sơ đồ hoạt động/tuần tự [tên UC]”; 322 caption được đánh số riêng cho bảng/hình theo chương, 321 sửa đổi tối thiểu. [Mapping và bằng chứng](../requirements/use-cases-83/chapter-captions/README.md).
- Verified: native giữ 154 bảng/166 ảnh/ba tab khác, style caption và các TOC; PDF đủ 322 caption và 166 ảnh cùng caption/đúng cỡ. Sáu paragraph UC01 chỉnh leading/keep sau khi PDF phát hiện caption rời ảnh; không thay ảnh.
- Incomplete: none trong lượt caption; UC11 còn hai vị trí thiếu ảnh, Sequence UC07 dùng ảnh Activity cũ, danh mục hình/bảng đầu báo cáo còn template là các phần ngoài lượt caption. Kiểm 9 core/8 module, 109 Markdown/1.013 liên kết-anchor và whitespace đạt. Blocked: none. Next: dùng mapping caption mới; các lượt ảnh tiếp theo đọc nguồn và hỏi trước thay ảnh theo quy trình. [Task](archive/TASK-0080-chapter-table-figure-captions.md).

## Report diagrams UC19–UC83 — TASK-0079 / 2026-10-10

- Completed: đọc mới 83 bảng/830 trường, kiểm/sửa mã theo đặc tả và giữ mẫu, chèn 118 ảnh mới/thay 12 ảnh được duyệt. [PNG và bằng chứng](../requirements/use-cases-83/report-from-uc19/README.md).
- Verified: 166 đường chính + 834 đường chọn nhánh, 130 PNG; native giữ nội dung ngoài 5 sửa đổi văn bản được duyệt/caption và ba tab khác. PDF kiểm đủ 130 heading/ảnh/caption cùng trang, cỡ và tỷ lệ đúng.
- Incomplete: none trong UC19–UC83. Blocked: none. Next: UC01–UC18 là phạm vi tiếp nối nếu được yêu cầu; đọc lại nguồn/vị trí và hỏi trước khi thay ảnh có sẵn. Không đổi nghiệm thu phần mềm. [Task](archive/TASK-0079-diagrams-from-uc19.md).

## Diagram workflow — TASK-0078 / 2026-10-10

- Completed: ghi [quy trình người dùng yêu cầu](../requirements/use-cases-83/SPECIFICATION_GUIDE.md#quy-trình-thực-hiện-do-người-dùng-quy-định--2026-10-10) và dẫn chiếu trong bàn giao/module index: kiểm đặc tả trước, hỏi khi luồng không hợp lý, kiểm/sửa mã giữ mẫu rồi xuất/chèn; hỏi trước khi thay ảnh đã có.
- Verified: cấu trúc 9 core/8 module, 75 liên kết/anchor local và whitespace đạt. Không đọc lại đặc tả native, render hoặc chèn ảnh trong lượt ghi quy trình này.
- Incomplete: none trong phạm vi ghi quy trình; [TASK-0078](archive/TASK-0078-diagram-workflow.md) hoàn tất. Blocked: none. Next: áp dụng quy trình cho phạm vi UC tiếp theo được yêu cầu; giữ quy tắc thu ảnh lớn vừa trang và giữ ảnh nhỏ.

## Compact diagram presentation — TASK-0077 / 2026-10-10

- Completed: rút nhãn và bỏ legend/ghi chú dài khỏi 166 source Activity–Sequence của 83 UC theo mẫu; thay mười hình UC83→UC79 trong báo cáo. [Bằng chứng/PNG hiện hành](../requirements/use-cases-83/diagram-presentation/README.md).
- Verified: 830 trường và 1.000 đường đi kiểm lại đạt, render đủ 166 PNG. Native giữ văn bản/cấu trúc ba tab và 38 ảnh cũ; PDF bản sao có cùng body/layout/cỡ ảnh kiểm mười hình/title/caption trọn trang, giữ tỷ lệ, không phóng lớn.
- Incomplete: UC01–UC78 chưa chèn vào báo cáo. Blocked: none. Next: dùng source gọn khi chèn các UC còn lại theo yêu cầu tiếp nối. Trạng thái ứng dụng/schema/nghiệm thu giữ theo các checkpoint riêng.

## Report diagram insertion — TASK-0076 / 2026-10-10

- Completed: chèn 10 hình PlantUML Activity/Sequence UC83→UC79 đúng chỗ báo cáo. [Bằng chứng/PNG gốc](../requirements/use-cases-83/report-insertion/README.md).
- Verified: giữ 83 bảng/830 trường, 38 ảnh cũ và ba tab. Đủ 10 hình cùng title/caption trọn một trang, kiểm native và PDF bản sao cùng body/layout/cỡ ảnh. Sequence tối đa 590 pt; không phóng lớn.
- Incomplete: UC01–UC78 chưa chèn. Blocked: none cho năm UC; export toàn bộ bản cuối lỗi nhưng đã kiểm bản sao một tab. Next: xem năm mẫu và tiếp tục khi được yêu cầu. Không đổi code/schema/nghiệm thu module.

## Use-case documents and diagrams — TASK-0075 / 2026-10-10

- Completed: đọc lại 83 bảng/830 trường trong tab làm việc Báo cáo đồ án và viết [83 đặc tả/166 Activity–Sequence](../requirements/use-cases-83/README.md), mapping, hướng dẫn và bàn giao. Nguồn cuối vẫn giữ 830/830 trường. Bộ 78/80 được giữ lịch sử.
- Verified: khớp nội dung, 166 đường chính + 834 đường chọn nhánh, 11 tình huống trọng yếu; PlantUML render 166 PNG thành công, overview đủ bộ và kiểm làn/nhánh/legend. [Bằng chứng](../requirements/use-cases-83/VALIDATION.md).
- Incomplete: [8 ghi chú câu chữ/diễn giải nguồn](../requirements/use-cases-83/REVIEW.md), trong đó dấu câu UC17 đã sửa local; các điểm còn lại chưa sửa báo cáo. Chưa chèn ảnh Google Docs/tải Drive, chưa viết sequence lớp/API hoặc nghiệm thu phần mềm.
- Blocked: none đối với bước tài liệu/diagram. Next: xem các ghi chú nguồn, chọn cỡ ảnh và thư mục Drive khi thực hiện bước đưa hình. Không sửa ứng dụng/schema trong TASK-0075; các thay đổi đồng thời được giữ.

## Report language, abbreviations and actors — TASK-0074 / 2026-10-10

- Completed: [TASK-0074](archive/TASK-0074-report-plain-language-glossary-and-actors.md) sửa 165 mô tả trường, 47 mô tả bảng và 6 mô tả yêu cầu theo công dụng; điền 52 từ viết tắt và làm rõ 7 actor AT01–AT07 trong báo cáo hiện hành. KTV thống nhất là Kỹ thuật viên theo phần tổng quan báo cáo.
- Verified: native backup đủ ba tab; 7.783 ô đối chiếu đạt, 401 ô kiểm kiểu chữ/căn lề đạt, widths giữ nguyên và hai bảng danh mục có header ghim. Giữ nguyên 83 đặc tả, 47 bảng/895 trường, các mã actor, cấu trúc và các tab khác. Không còn hai câu mô tả chung bị yêu cầu sửa.
- Incomplete: PDF export vẫn 403, chưa kiểm trang; full trusted-read bridge thất bại nên không khẳng định đã kiểm toàn bộ protected controls. Narrow native reads và backup đầy đủ hỗ trợ sửa các ô văn bản trong phạm vi.
- Blocked: none đối với nội dung/định dạng native. Next: kiểm trang khi export khả dụng. Không đổi code, database hoặc chính sách nghiệp vụ trong TASK-0074; checkpoint V24 dưới đây vẫn hiện hành.

## Current database and authentication — V24 / TASK-0073

- Completed: [TASK-0073](archive/TASK-0073-remove-refresh-session-table.md) bỏ refresh sessions khỏi code, local PostgreSQL và báo cáo. Còn 47 bảng nghiệp vụ/895 trường, 48 public tables gồm Flyway history; 103 compatibility views và 7 report views. Đăng nhập có thời hạn cố định, mặc định 15 phút, restore giữ deadline. `auth_challenges` và các bảng eKYC được giữ. [Bàn giao V24](../architecture/DATABASE_AUTH_SIMPLIFICATION_V24_20261009.md).
- Verified: private backup/restore trước live, 47/47 fingerprints khớp, V1–V24 validate; backend 263 tests (237 pass/26 skip, 0 failures/errors), 34 PostgreSQL tests đạt, runtime health/catalog 200. Frontend 104 tests đạt và nhóm auth chạy lại cuối 40 tests đạt; scoped lint/build đạt. Báo cáo có 47 bảng chi tiết, một tổng hợp, 48 headers ghim và 4.854 ô đúng định dạng native; native backup riêng được giữ.
- Incomplete: global lint còn warning có sẵn ở CartContext ngoài phạm vi; chưa kiểm trang PDF vì export 403. OTP/Google và canonical API/worker theo 83 UC vẫn còn công việc tiếp nối; không coi schema đủ là đã nghiệm thu use case.
- Blocked: none đối với việc bỏ refresh sessions. Next: dùng [DDL ERD V24](../../database/schema/og_shop_v24_tables_for_erd.sql)/[dictionary V24](../../database/docs/DATABASE_TABLE_DICTIONARY_V24.md), forward-fix bằng V25+. Kiểm trang khi PDF export khả dụng. Các checkpoint V23 bên dưới là lịch sử; trạng thái task thiết kế khác giữ nguyên.

## Database design report — 2026-10-09 / TASK-0072

- Completed: [TASK-0072](archive/TASK-0072-database-design-report.md) điền trực tiếp bảng tổng hợp 48 bảng và 48 bảng chi tiết/907 trường theo PostgreSQL V23; native backup đầy đủ, không có bảng Flyway.
- Verified: 4.922 ô khớp nội dung; 49 tiêu đề đúng, căn giữa; 49 hàng tiêu đề ghim, STT căn giữa, cột nội dung justified, mọi ô MIDDLE, Times New Roman 13 pt. Ba tab giữ tên/thứ tự. Các chỉnh sửa đồng thời ở ngoài chương database được giữ.
- Incomplete: PDF/HTML export bị Google Drive từ chối 403 nên chưa kiểm bố cục trang. ERD vẫn là phần riêng người dùng chuẩn bị. Không đổi schema/code hay trạng thái nghiệm thu use case.
- Next: dùng chương bảng đã cập nhật và dictionary V23 để đối chiếu ERD; kiểm trang khi xuất PDF khả dụng.

## Public use-case policies — 2026-10-09 / TASK-0071

- Completed: [TASK-0071](archive/TASK-0071-public-use-case-policies-and-coins.md) cập nhật trực tiếp 83 đặc tả, 420 ô; giảm 17,9% ký tự, giữ mẫu 10 trường và sao lưu native đầy đủ. Đồng bộ tổng quan/dẫn chiếu, uy tín 100 và các mức phạt do KTV quyết định, năm nguồn xu, voucher trước rồi xu đến 0 đồng kể cả phí giao, hoàn xu không cấp lại voucher.
- Verified: đọc lại native đủ 83 bảng; danh sách và BR đúng định dạng, nhánh/tham chiếu hợp lệ; hai tab ngoài phạm vi nguyên vẹn, nội dung bản sao khớp trước sửa.
- Incomplete/blocked: xuất PDF thành công nhưng tải tệp lỗi hostname/transport, chưa kiểm tra bố cục trang. Task chỉ sửa báo cáo; code/database/diagram chưa được đồng bộ chính sách mới trong đợt này.
- Next: chính sách hiện hành theo báo cáo và TASK-0071; triển khai bằng thay đổi tiếp nối khi được yêu cầu. Checkpoint runtime V23 bên dưới không chứng minh các quy tắc mới đã được triển khai.

## Historical database checkpoint — 2026-10-09 / TASK-0070

- Completed: [TASK-0070](archive/TASK-0070-consolidate-database-48-tables.md), [bàn giao V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md): local og_shop V21→V23, 48 business tables/49 public tables gồm history, 23 success/0 failed; 7 public report views, 104 adapter views ở og_compat, không còn schema chuyển đổi cũ.
- Verified: private backup + restore trong container local, fingerprints 104/104 nguồn khớp trên restored clone và live; 252 backend tests (251 pass, 1 Windows symlink skip), 33 expansion Pg pass; Hibernate validate và HTTP health/categories/products đều 200. ERD DDL nhập database trống thành công với đúng 48 bảng.
- Incomplete: API/UI/worker canonical còn cần nối theo use case; Google Docs report chưa tự thay chương database. Dictionary/DDL hiện hành đã chuẩn bị tại database/docs và database/schema.
- Blocked: none. Next: dùng schema public/48 bảng cho ERD và dictionary V22 cho báo cáo; giữ og_compat cho code đang chuyển tiếp, forward-fix bằng V24+. Các task UI/design và checkpoint V21 bên dưới giữ lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database checkpoint — 2026-10-09 / TASK-0069

- Completed: [TASK-0069](archive/TASK-0069-expand-database-83-use-cases.md) và [bàn giao V21/ma trận 83 UC](../architecture/DATABASE_EXPANSION_V21_20261009.md). V15–V21 cho 12 nhóm dữ liệu đã migrate vào local og_shop; 21 success/0 failed, 105 public tables/7 views, tăng 61 bảng nghiệp vụ.
- Verified: clean/upgrade/backup restore, giữ dữ liệu legacy, Hibernate validate; 249 tests, 0 failures/errors, 1 Windows symlink skip; 30 expansion Pg tests pass. Có backup riêng bị Git ignore, không có reset hay fake approval/payment.
- Incomplete: API/UI/worker chưa nối đầy đủ workflow canonical. Next: Identity/Seller → Catalog/fee → checkout/voucher/points → payment/component settlement → shipping/cases → notifications/reports. Không đánh dấu 83 UC hoàn thành từ số bảng.
- Blocked: none. Active/paused design tasks và các trạng thái baseline phía dưới thuộc luồng công việc khác, được giữ nguyên.


- Project: Old but Gold (O.G Shop)
- Updated: 2026-09-20
- Phase: UI_UX_DESIGN_FIRST
- Overall status: IN_PROGRESS
- Active task: TASK-0018 — Marketplace UI/UX design system & clickable prototype in Figma
- Paused task: TASK-0014 — Buy Now (paused before implementation; resume after design approval)
- Published branch: `origin/main`

## Report checkpoint — 2026-10-09

- [TASK-0068](archive/TASK-0068-database-coverage-83-use-cases.md): hoàn thành [rà database theo từng UC trong báo cáo 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md). Live PostgreSQL 17.11/og_shop có V1–V14 success và 43 bảng ngoài Flyway history; kết quả 53 một phần, 18 mâu thuẫn, 10 thiếu, 2 đủ nền. Đã đề xuất 12 nhóm mở rộng, quan hệ/ràng buộc, chuyển đổi và acceptance plan; không triển khai schema/code.
- [TASK-0067](archive/TASK-0067-simplify-83-use-case-specifications.md): đã rút gọn đủ 83 đặc tả, giảm từ 212.140 xuống 175.369 ký tự (17,3%); giữ 10 trường, mã BR và tab sao lưu; đọc lại native và xem PDF mẫu.
- [TASK-0066](archive/TASK-0066-use-case-simplification-plan.md): đã tạo tab “Sao lưu trước tinh giản — 09-10-2026” và chuẩn bị kế hoạch; bản sao tiếp tục được bảo toàn.
- [TASK-0065](archive/TASK-0065-report-policy-checkpoint-20261009.md): đã chỉnh và đọc lại tab “Bản sao của Thẻ 1” trong báo cáo 83 UC; chốt phí tin cộng dồn, gom thanh toán, trả/hoàn và phạm vi sandbox; giữ bảng/kiểu chữ và tab gốc.
- Hướng tiếp tục: dùng TASK-0068 làm cơ sở thiết kế schema/migration theo từng đợt khi phạm vi triển khai được yêu cầu; không dùng mức “Đủ nền” hoặc số UC làm bằng chứng nghiệm thu phần mềm.
- Nguồn hiện hành cho công việc báo cáo là Google Docs được dẫn trong task. Các baseline 78/80 UC và trạng thái triển khai bên dưới chưa được đồng bộ theo toàn bộ quyết định mới; không dùng chúng để mở lại câu hỏi đã chốt.

## Completed

- Chốt tên dự án và repository.
- Chốt Modular Monolith với Backend Java/Spring Boot và Frontend React/TypeScript.
- Khởi tạo Git repository cục bộ trên nhánh `main`.
- Kết nối remote `origin` tới `https://github.com/Zelrious/O.G-Shop.git`.
- Hoàn thành monorepo scaffold, module boundaries và tài liệu nền.
- Backend/Frontend foundation, database tooling và CI đã được khởi tạo.
- Cấu hình AI/agent cá nhân được giữ local và không còn theo dõi trên Git.
- Đã hoàn thành [TASK-0013](archive/TASK-0013-catalog-vertical-slice.md):
  - Flyway V5 seed 8 danh mục MVP chuẩn hóa (`electronics`, `fashion`, `home-living`, `books-stationery`, `sports-outdoors`, `collectibles`, `mother-baby`, `other`) với display order ổn định 1..8.
  - Entity `ProductEntity` ánh xạ bảng `products` với `@Version` hỗ trợ JPA Optimistic Locking và các phương thức chuyển trạng thái nghiệp vụ an toàn.
  - Cung cấp `IdentityCatalogFacade` (`DefaultIdentityCatalogFacade`) do Identity sở hữu để Catalog kiểm tra quyền bán từ database và batch lookup public seller summary (`sellerId`, `displayName`, `trustLabel: "Người bán MVP"`).
  - Backend Public Catalog API (`GET /api/v1/categories`, `GET /api/v1/products`, `GET /api/v1/products/{productId}`) tìm kiếm tiêu đề, lọc danh mục/tình trạng/giá, sort allowlist, phân trang; chỉ trả sản phẩm `ACTIVE` và chưa xóa mềm.
  - Backend Seller Catalog API (`/api/v1/seller/products/**`) cho phép xem tin của mình, tạo `DRAFT`, sửa tin (`DRAFT`/`ACTIVE`/`HIDDEN`), publish, hide, chặn sửa khi `RESERVED`/`SOLD`, cô lập quyền sở hữu trả 404 chống ID enumeration, và ánh xạ optimistic locking thành HTTP 409 `PRODUCT_VERSION_CONFLICT`.
  - Frontend Marketplace (`/marketplace`, `/products/:productId`) và Seller Listings (`/seller/listings`, `/seller/listings/new`, `/seller/listings/:productId/edit`) với UI chuẩn O.G Shop tokens, không dùng mock data, responsive trên mobile, đầy đủ loading/empty/error/retry.
  - Cập nhật Navbar: link "Mua sắm", "Tin đăng của tôi" cho Seller, CTA kích hoạt cho Buyer, không horizontal overflow trên mobile.
  - Đạt 100% Quality Gate: Backend 39/39 tests pass, Frontend 24/24 tests pass, typecheck 0 lỗi, lint 0 warning, build production pass, Database runner pass 100% (cả clean V1->V5 và legacy V1->V4->V5 backfill).
- Đã hoàn thành [TASK-0012](archive/TASK-0012-mvp-seller-activation.md):
  - Flyway V4 mở rộng method `MVP_BYPASS` và điều chỉnh resolution check constraint cho phép kích hoạt nhanh.
  - Endpoint `POST /api/v1/seller-verification/activate` cấp role `SELLER` idempotent từ Backend authority.
  - Cấu hình `app.seller-verification.mode=MVP_BYPASS` (env: `SELLER_VERIFICATION_MODE`), từ chối bằng lỗi `MVP_ACTIVATION_DISABLED` khi tắt.
  - Không tạo dữ liệu CCCD/sinh trắc học hay metric giả, giữ nguyên eKYC FastAPI microservice để bật lại sau.
  - Frontend `/seller-verification` hiển thị cảnh báo trung thực về chế độ MVP, xử lý đầy đủ các trạng thái và tự động tải lại quyền người dùng.
  - Đạt 100% Quality Gate: Backend 19/19 tests pass, Database migration/invariant tests pass, Frontend 10/10 tests pass, typecheck 0 lỗi, lint 0 warning, build production thành công.
- Đã hoàn thành [TASK-0005](archive/TASK-0005-identity-ekyc-security-baseline.md):
  - Backend là authority cho register/login/current-user/refresh/logout và role assignment.
  - Access JWT ngắn hạn kết hợp opaque refresh cookie rotation/reuse detection; database chỉ lưu digest.
  - Frontend không còn mock auth, client-side password hashing, role grant hoặc direct FastAPI call.
  - eKYC chỉ đi qua backend gateway, yêu cầu internal token, fail closed và không trả/lưu face embedding.
  - Flyway V2 loại dữ liệu CCCD/sinh trắc học nhạy cảm khỏi schema hiện tại.
- Đã hoàn thành [TASK-0002](archive/TASK-0002-register-sources-and-database-baseline.md):
  - Nhập tài liệu yêu cầu, kiến trúc eKYC và tài liệu database vào `reference/` và `database/docs/`.
  - Đăng ký 7 nguồn tài liệu trong `reference/SOURCE_REGISTER.md`.
  - Tạo Flyway migration `V1__initial_schema.sql` tích hợp 22 bảng, extension `pgvector` và bảng `seller_biometrics`.
  - Kiểm chứng Backend, Frontend và tài liệu của baseline tại thời điểm hoàn thành.
- Đã hoàn thành [TASK-0010](archive/TASK-0010-database-pricing-and-negotiation-baseline.md):
  - Flyway V3 tách giá Seller niêm yết, system-fee policy có version và snapshot phí trên offer/order.
  - Chat có product snapshot, participant read cursor, message idempotency và offer/counter-offer chain.
  - Accepted offer snapshot sang order item; giá niêm yết công khai không bị ghi đè.
  - Thêm transactional outbox cho Redis/WebSocket/notification và test runner PostgreSQL clean + legacy migration.
  - Database development đã migrate thành công đến V3; Backend và eKYC đang health UP.
- Đã hoàn thành [TASK-0011](archive/TASK-0011-core-features-before-optimization.md):
  - Chốt thứ tự triển khai nghiệp vụ cốt lõi trên PostgreSQL trước tối ưu hạ tầng.
  - REST chat/history và offer transaction phải ổn định trước WebSocket/Redis.
  - Đặt acceptance gate cho concurrency, authorization, idempotency và frontend end-to-end trước giai đoạn tối ưu.
- Đã hoàn thành [TASK-0003](archive/TASK-0003-ui-batch-01-auth-and-ekyc.md):
  - Cài đặt `react-router-dom` và cấu hình routing URL (`/`, `/login`, `/register`, `/forgot-password`, `/profile`, `/seller-verification`).
  - Thiết lập Design Tokens chuẩn O.G Shop và UI primitives (`Button`, `Input`, `Card`, `Badge`, `Alert`).
  - Triển khai Auth/eKYC UI mock; cơ chế SHA-256, client role grant và token localStorage này đã bị TASK-0005 thay thế.
  - Đạt 100% Quality Gate: typecheck 0 lỗi, lint 0 warning, 5/5 unit tests passed, build production thành công.
- Đã hoàn thành [TASK-0004](archive/TASK-0004-integrate-ekyc-ai-algorithm-microservice.md):
  - Đóng gói thuật toán AI eKYC thực tế từ `D:\Khanh\Đồ án 1\Test` (YOLOv11n + VietOCR Transformer + Gemini Flash + DeepFace ArcFace 512-d + Cosine Distance <= 0.50) thành FastAPI Microservice độc lập (`services/ekyc-service`).
  - Tối ưu đường dẫn trọng số độc lập, loại bỏ hoàn toàn các thành phần thừa (GUI Tkinter cũ, thư mục testcase nặng, references ngoài).
  - Tích hợp endpoint `/api/v1/ekyc/ocr`; embedding trong response và direct frontend call đã bị TASK-0005 loại bỏ.
  - Tích hợp endpoint `/api/v1/ekyc/match-face` so khớp sinh trắc học thời gian thực với độ sai lệch Cosine chính xác.
  - Kết nối Frontend React `CccdUploader` và `CameraCapture` với `verificationApi.ts`, hiển thị huy hiệu AI Thật và độ tin cậy.

## Next

1. Hoàn thành TASK-0018: thiết kế Figma responsive desktop/mobile, Light/Dark, VI/EN, sample data và prototype theo role.
2. Người dùng duyệt trực quan bộ UI/UX và chốt các điều chỉnh trước khi sửa Frontend.
3. Tiếp tục TASK-0014: Buy Now — UI checkout + Backend reservation/order chống bán trùng theo thiết kế đã duyệt.
4. Tiếp tục TASK-0015, TASK-0016 và TASK-0017 theo thứ tự core flow đã chốt.


## Current risks

- Các chính sách thời hạn kiểm tra hàng, tự giải ngân và hoàn hàng còn cần xác nhận.
- Các tích hợp ở TASK-0006 đến TASK-0009 chưa được triển khai nhưng không được chặn việc kiểm thử core flow bằng adapter local/mock.
- Luồng hiện tại là eKYC mô phỏng kỹ thuật, không phải KYC production.
- Mức phí hệ thống chưa được chốt; policy active hiện tại là 0% để không thu nhầm.
- Accept offer/checkout đồng thời chưa có application-level concurrency test; đây là điều kiện bắt buộc trước giai đoạn tối ưu.
- Outbox worker, WebSocket và Redis được chủ động defer; schema outbox hiện tại chỉ là nền tảng, chưa phải dependency của core flow.
