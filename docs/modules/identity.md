# Identity Module

## Console selects and eKYC — TASK-0096 / 2026-10-13

KTV/Admin:18 native selects chuyển sang shared Select; upward popover neo sát trigger6px, theo dõi vị trí khi sidebar chuyển động; sidebar chờ320ms, transition360ms. Cột queue360–400px; eKYC list/detail và masked list; voucher tự chọn campaign đầu API, bỏ option rỗng. API/handler/rules giữ nguyên.204 tests/43 files, lint/TypeScript/build đạt;82 browser checks không overflow/JS error, menu lệch ngang0px/gap6px. [Ảnh và giới hạn](../../output/ui-0096/README.md).

## KTV UI system — TASK-0094 / 2026-10-13

Thêm shared Select/Pagination/PageHeader; token surface/popover/disabled và semantic badge/button, sửa contrast card eKYC/khiếu nại, custom voucher/filter/page-size dropdown, phân trang 0 kết quả. Giữ sidebar và tất cả API/handler nghiệp vụ. 195 full-suite pass + 2 eKYC tests mới; final focused26, lint/TypeScript/build đạt. Browser48 geometry +8 contrast +2 dropdown checks, không overflow/JS error; sampled contrast light4,65/dark5,76. [Báo cáo và giới hạn](../../output/ui-0094/README.md).

## Voucher audience — TASK-0093 / 2026-10-13

VoucherIdentityFacade checks database staff role/active; customer BUYER/SELLER excludes staff/deleted, effective status; paginated query/role/status/include/exclude and ordered numeric locks. Commerce uses facade. Inactive customers keep wallet history; only ACTIVE receive new grants. Admin authors, KTV issues/revokes. PG and signed HTTP negatives pass. [API](../api/VOUCHERS_API.md), [evidence](../../output/vouchers-0093/README.md).

## Giao diện và owner chat — TASK-0091 / 2026-10-13

Auth/profile dùng outline icon/token theme. Chat runtime key theo user/customer eligibility, đổi owner/role loại bỏ cache/draft/tệp/pending UI; staff không có chat trigger. Backend auth/governance không đổi. Theme/owner/staff tests nằm trong183 frontend tests đạt; [bằng chứng](../../output/ui-0091/README.md).

## Chat/offer V31 — TASK-0090 / 2026-10-13

ConversationIdentityFacade kiểm customer/role/governance và user-pair lock; CHAT giữ read/history nhưng chặn send, BUY_ALL giữ chat. Guards đọc permission noRollbackFor denial được xử lý trong projection, command vẫn rollback khi lỗi đi ra. Signed QA KTV CHAT thu hồi phiên/login lại và giữ owned order đã kiểm.

[API](../api/CHAT_OFFER_API.md), [thiết kế/rollout](../architecture/CHAT_OFFER_V31_20261013.md), [test guide/ảnh](../../output/phase34-runtime/README.md). Năm task marketplace hoàn tất trong phạm vi hiện hành; app DB V31/47 bảng, backup/preservation150 nguồn đạt, runtime5174/8080 sẵn sàng. Các checkpoint dưới giữ lịch sử.

## Đăng nhập local — TASK-0089 / 2026-10-12

Failed to fetch từ CORS origin mismatch5173/5174, không từ phép kiểm credentials. Đã đồng bộ private origin5174 và Vite/strictPort, restart backend. Config/preflight200, fake login nhận AUTHENTICATION_FAILED, Google button tải được; không thử password thật/Google popup. [Bằng chứng](../../output/login-origin-fix/README.md). Không đổi auth logic/password/role.

## Quản lý user KTV — TASK-0088 / 2026-10-12

Identity sở hữu API KTV customer-only, governance state/version riêng với auth, biện pháp scope/deadline, gỡ/kích hoạt/ngừng mềm và thu hồi token. KtvUsersPage thay demo bằng API list–detail; /account/status, banner và phản hồi hồ sơ owner. Facade chặn hoạt động mới, vẫn giữ login và nghĩa vụ cũ; status auth legacy cần ACTIVATE riêng. UC67 core và phần đọc UC11; UC68/UC74/UC10 đầy đủ tiếp nối. [Thiết kế/rollout](../architecture/ACCOUNT_GOVERNANCE_V30_20261012.md), [API](../api/ACCOUNT_MANAGEMENT_API.md), [Kiểm chứng](../../output/phase2-runtime/README.md). Các checkpoint dưới đây giữ lịch sử.

## Kiểm duyệt và phí đăng — TASK-0087 / 2026-10-12

IdentityCatalogFacade bổ sung truy vấn KTV active cho notification audience. Quyền Seller active và role KTV/ADMIN được dùng trong listing workflow; auth hiện hành được hồi quy. [Thiết kế/rollout](../architecture/MODERATION_LISTING_V27_20261012.md), [Ảnh và kiểm chứng](../../output/phase1-runtime/README.md). Không xây API quản lý user/khóa scope trong đợt này; UC67/full eKYC/KTV và provider Google/Gmail thật tiếp nối.

## Sửa auth còn lại — TASK-0086 / 2026-10-11

F1–F5/F7 đã sửa trong code: OTP login Buyer/Seller trước phát phiên khi bật cờ; reset email/OTP/grant/new password/đăng nhập lại; cv thu hồi mọi token/cookie sau đổi hash; Google verify/link/registration và 2FA; phản hồi khóa sau credential đúng; Back Seller chặn default/key riêng. Google mới vẫn đặt mật khẩu/OTP, Seller giữ PENDING_EKYC/BUYER, thưởng 3.000 xu một lần. Admin/KTV dùng login do Admin cấp, không dùng reset/Google/2FA khách hàng.

64 backend tests đạt (29 PostgreSQL, 3 SMTP transport, 3 Google RSA/JWK local); 135 frontend tests, typecheck/scoped lint/build/package đạt. Browser fixture kiểm Back click/Enter, Buyer OTP click, login OTP và reset ba bước; không thay nghiệm thu provider thật. [Bằng chứng và trước/sau](../../output/auth-fixes-2026-10-11/README.md), [API](../api/IDENTITY_API.md), [Google setup](../setup/GOOGLE_AUTH.md), [schema V26](../../database/docs/AUTH_FLOWS_V26.md).

Client ID đã nạp vào private .env root/backend; cần restart backend và áp V26 sau backup/restore. V26 chỉ chạy ở database test riêng; Google/Gmail thật, UC09 bật/tắt bằng face, eKYC/KTV và full UC67 còn tiếp nối. Reset mail queue giới hạn nằm trong bộ nhớ, chưa có durable outbox. Các checkpoint dưới đây giữ kết quả lịch sử trước sửa.

## Retry-After và countdown đăng ký — TASK-0085 / 2026-10-11

F6 đã sửa: frontend đọc được Retry-After qua CORS, nhận lỗi thì tính hạn chờ từ thời điểm hiện tại; form tài khoản/Seller hiển thị số giây, giữ dữ liệu và chặn submit trong thời gian chờ. Hết hạn cho gửi lại; xác nhận OTP vẫn dùng được trong cooldown. Giữ JSON, giới hạn backend và SMTP/database. Auth/RegisterPage 34 tests và backend controller/service/CORS 19 tests đạt, typecheck/lint/build/package đạt. Browser hai vai trò đã kiểm 429 bằng fixture 5 giây, chuyển OTP sau retry; 60 giây được kiểm bằng fake clock và HTTP MockMvc. [Bằng chứng](../../output/auth-retry-fix-2026-10-11/README.md), [task](../progress/archive/TASK-0085-registration-retry-countdown.md). Các phát hiện F1–F5/F7 của audit vẫn là việc tiếp nối; chưa nghiệm thu PostgreSQL/SMTP E2E trong lượt sửa này.

## Audit đăng ký/đăng nhập/đặt lại mật khẩu — TASK-0084 / 2026-10-11

Đã đọc mới SRC-08 UC01–UC04/UC09/UC67 và kiểm code/UI/API trên môi trường riêng. Đăng ký Buyer/Seller email OTP chạy được: UC83/email verified, BUYER, một khoản 3.000 xu; Seller PENDING_EKYC, hai địa chỉ/ngân hàng và chuyển bước xác minh. Frontend auth 29 tests và backend liên quan 42 tests đạt (12 PostgreSQL, 3 SMTP), typecheck/lint/build/package đạt; không nghiệm thu toàn UC từ số tests.

Còn thiếu UC02/Google, UC04/reset và OTP login 2FA. Fixture có cờ 2FA vẫn nhận full token; đổi mật khẩu chưa thu hồi token/cookie thiết bị khác; khóa trả lỗi phiên chung. CORS chưa expose Retry-After; nút quay lại Seller tái hiện không về bước tài khoản, stepper là workaround. [Báo cáo và bằng chứng](../../output/auth-audit-2026-10-11/AUTH_FUNCTIONAL_AUDIT.md), [task](../progress/archive/TASK-0084-auth-functional-audit.md). Không sửa source/migration, không gửi Gmail thật; đã dọn dịch vụ test riêng. eKYC/KTV và quyền hạn chế theo từng hành động chưa nghiệm thu.

## Kết nối hộp thư OTP — TASK-0083 / 2026-10-11

Đã sửa cấu hình SMTP local và nạp Mailpit relay riêng email thật. UI Mailpit/SMTP local và Gmail STARTTLS/AUTH đạt; chưa gửi OTP Gmail hoặc xác nhận đăng ký qua UI sau restart backend. Quy tắc tạo user, OTP và thưởng xu giữ như đợt 1. [Hướng dẫn](../setup/EMAIL_OTP.md), [bàn giao](../progress/archive/TASK-0083-mailpit-local-smtp.md).

## Đăng ký email OTP — TASK-0082 / V25 / 2026-10-10

Đợt 1 đã nối đăng ký hai bước: lưu draft chứa hash, gửi mã; chỉ tạo user UC83/email đã xác thực sau OTP đúng. Tài khoản mới có BUYER, nhận 3.000 xu một lần qua Commerce facade trong cùng transaction. Seller lưu PENDING_EKYC với cửa hàng, địa chỉ lấy/trả hàng và ngân hàng; AI/MVP bị chặn cho user mới. [API](../api/IDENTITY_API.md), [SMTP/Gmail](../setup/EMAIL_OTP.md), [migration V25](../../database/docs/EMAIL_REGISTRATION_V25.md).

Mật khẩu mới thống nhất tối thiểu 8 ký tự/tối đa 72 byte UTF-8 ở đăng ký và đổi mật khẩu; login cho mật khẩu cũ ngắn hơn 8 ký tự. Giữ phiên cố định V24; đăng ký chờ chưa có token/cookie. Có giới hạn gửi/nhập, resend/cancel, hard expiry 30 phút và cleanup payload; nhập sai commit lượt thử, lỗi lưu/thưởng hoàn tác tiêu thụ OTP.

Đã kiểm 12 ca đăng ký trên PostgreSQL thật và 34 ca guard/upgrade; toàn backend 285 tests: 259 pass/26 skip, 0 failures/errors. Frontend 113 tests, typecheck/lint/build đạt. V25 local đã backup/restore/migrate/validate, 47 fingerprints giữ nguyên; runtime health/catalog 200. Gmail thật chờ cấu hình gửi; Google, reset OTP/thu hồi mọi thiết bị và 2FA/eKYC/KTV là đợt tiếp nối. Không nâng nghiệm thu toàn bộ UC01–UC11. Các checkpoint V24 trở về trước dưới đây là lịch sử.

## Diagram báo cáo — TASK-0081 / 2026-10-10

[UC01–UC18 đã có 36 ảnh kiểm nguồn/native/PDF](../requirements/use-cases-83/report-uc01-18/README.md), gồm UC01–UC09 và phạm vi 2FA được chốt. Thay 34 ảnh được duyệt và thêm hai ảnh UC11, giữ Hình 3.3–3.38 và ảnh ngoài phạm vi. 2FA chỉ dành cho Người mua/Người bán theo quyết định người dùng. Đây là cập nhật yêu cầu/diagram; không nâng trạng thái code/schema/nghiệm thu module.

## Report diagrams — TASK-0079 / 2026-10-10

[UC19–UC83 đã có đủ 130 Activity/Sequence trong báo cáo](../requirements/use-cases-83/report-from-uc19/README.md), kiểm native/PDF trọn trang. RS-02/RS-05 được chốt và đồng bộ theo xác nhận người dùng; chuẩn Activity/Sequence gọn được giữ. Đây là tài liệu, không nâng trạng thái code/database/nghiệm thu module.

## Diagram presentation — TASK-0077 / 2026-10-10

Mã Activity/Sequence hiện dùng [cách trình bày gọn](../requirements/use-cases-83/diagram-presentation/README.md): bỏ legend khỏi ảnh, rút nhãn và giữ đủ đường đi theo đặc tả. Kiểm nguồn/1.000 đường đi và render 166 PNG đạt; ảnh UC79–UC83 đã kiểm trọn một trang trên PDF. Đây là cập nhật trình bày tài liệu.

## Current use-case documents — TASK-0075 / 2026-10-10

[Bộ 83 đặc tả/166 Activity–Sequence](../requirements/use-cases-83/README.md) đã đọc lại trực tiếp báo cáo và viết theo 830 trường hiện hành; [rà soát](../requirements/use-cases-83/REVIEW.md) ghi các điểm nguồn cần làm rõ. UC01–UC09 và UC68 giữ OTP/Google, 2FA tùy chọn, kết quả xác thực nhanh còn hiệu lực, KTV quyết định eKYC và quyền bán riêng. Đây là yêu cầu và diagram ở mức yêu cầu; trạng thái code/schema/nghiệm thu của module không được nâng từ kết quả tài liệu. [Bàn giao](../handoff/USE_CASE_83_AGENT_HANDOFF.md).

## Report terminology — TASK-0074 / 2026-10-10

[TASK-0074](../progress/archive/TASK-0074-report-plain-language-glossary-and-actors.md) giải thích OTP/eKYC/2FA, công dụng các trường auth và danh sách actor bằng lời dễ hiểu; KTV thống nhất là Kỹ thuật viên theo báo cáo. Giữ nguyên 83 đặc tả và 47 bảng/895 trường; không triển khai thêm chức năng OTP/Google hay thay schema. Nội dung và định dạng native được kiểm; PDF export vẫn 403.

## Current authentication — V24 / TASK-0073

[TASK-0073](../progress/archive/TASK-0073-remove-refresh-session-table.md) bỏ `refresh_sessions` và luồng gia hạn. Đăng nhập mặc định 15 phút; HttpOnly cookie khôi phục trong thời hạn gốc, `/auth/session` tải lại quyền mà không gia hạn, `/auth/logout` xóa cookie. API nghiệp vụ tiếp tục dùng Bearer JWT. Frontend tự xóa trạng thái khi hết hạn/401, không còn tùy chọn ghi nhớ đăng nhập. [API hiện hành](../api/IDENTITY_API.md).

Identity còn 10 bảng: users, roles, user_roles, addresses, auth_challenges, ekyc_profiles, ekyc_private_assets, identity_document_registry, verification_attempts, seller_profiles. `auth_challenges` lưu OTP/xác thực nhanh; Gmail chỉ gửi mã, không thay việc giữ hạn dùng và trạng thái đã sử dụng. Chưa gộp ba bảng eKYC được đề xuất trước đó và chưa triển khai OTP/Google theo chính sách báo cáo.

Local database V24 có 47 bảng nghiệp vụ; 47 fingerprints giữ nguyên, backup/restore và Flyway validate đạt. Backend 263 tests, 237 pass/26 skip, 0 failures/errors; 34 PostgreSQL expansion/upgrade tests đạt. Frontend full suite 104 tests, nhóm auth chạy lại cuối cùng 40 tests đạt; scoped lint và build đạt. Báo cáo đã cập nhật 47 bảng/895 trường theo mẫu; native content/style readback đạt, PDF export 403 nên chưa kiểm trang. [Bàn giao V24](../architecture/DATABASE_AUTH_SIMPLIFICATION_V24_20261009.md). Các checkpoint dưới đây là lịch sử.

## Database report — TASK-0072 / 2026-10-09

[TASK-0072](../progress/archive/TASK-0072-database-design-report.md) đã điền chương bảng database trong báo cáo Google Docs theo schema V23: danh sách 48 bảng và 48 bảng chi tiết/907 trường, giữ mẫu native và kiểm tra căn lề, ghim tiêu đề, căn giữa chiều dọc. Đây là cập nhật tài liệu; trạng thái triển khai module vẫn theo code/kiểm thử. PDF/HTML export bị từ chối 403, chưa kiểm bố cục trang.

## Public policies — TASK-0071 / 2026-10-09

[TASK-0071](../progress/archive/TASK-0071-public-use-case-policies-and-coins.md) sửa UC01–UC11/UC66–UC68: đăng ký xác thực email bằng OTP một lần; Google liên kết đúng tài khoản, không tạo trùng; lớp hai tùy chọn riêng khi đăng nhập. Uy tín bắt đầu/tối đa 100, các hạn chế do KTV quyết định, giữ nghĩa vụ đơn cũ. Báo cáo đã đọc lại native; chưa thay code/database V23 theo các chính sách mới.

## Database consolidation — TASK-0070 / V23

11 bảng: users/roles/user_roles/addresses/refresh_sessions, auth_challenges, ekyc_profiles, ekyc_private_assets, identity_document_registry, verification_attempts, seller_profiles. Security/points/external identities/restrictions/cart header lưu trên users; eKYC/Seller decisions lưu trên hồ sơ. Không cấp SELLER từ identity decision.

Local og_shop đã migrate/validate V23: 48 bảng nghiệp vụ, 49 gồm Flyway history; 104 nguồn khớp fingerprint trước/sau. Backend entity/join-table dùng schema og_compat. 252 tests: 251 pass, 1 Windows symlink skip; 33 expansion PostgreSQL pass. [Bàn giao V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md), [dictionary 48 bảng](../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md). API/UI/worker canonical còn cần nối; checkpoint V21/baseline dưới đây là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — TASK-0069 / V21

V15: OTP/security settings, eKYC revisions/private assets/registry/attempts/KTV decisions, quick auth và seller approval riêng. Canonical role grant cần approval; AI/eKYC không tự cấp SELLER. [Bàn giao và ma trận 83 UC](../architecture/DATABASE_EXPANSION_V21_20261009.md). Local og_shop V1–V21 đã migrate/validate; 249 tests, 0 failures/errors, 1 Windows symlink skip; 30 expansion PostgreSQL tests đạt. Đây là nền database và mapping tương thích, chưa nghiệm thu các API/UI mới. Phần Database review và trạng thái baseline cũ bên dưới ghi tình trạng trước TASK-0069.


## Database review — 2026-10-09

Theo [bản rà soát 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), UC01–UC11/UC66–UC68 cần G01/G02/G10: email OTP/lớp hai, eKYC phiên bản do KTV quyết cuối, reference riêng tư có hạn, seller profile và quyền nghĩa vụ cũ. Schema/code còn AI/MVP tự VERIFIED/SELLER. Chưa triển khai các thay đổi này; trạng thái bên dưới là baseline trước rà soát.

- Status: IMPLEMENTED
- Requirements: UC-01, UC-02
- Completion: 0/2 use cases verified
- Last reviewed: 2026-09-19

## Purpose and scope

Quản lý tài khoản, đăng nhập, phiên, role, địa chỉ hồ sơ và quy trình xác minh Seller.

## Out of scope

Product ownership, moderation decision, order authorization và KYC production.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/identity/`
- `frontend/src/features/auth/` khi được tạo
- Flyway tables liên quan User, Role, UserRole, Address, SellerVerification

## Dependencies

- Platform cho audit và notification.
- Trust & Safety khi Admin hạn chế tài khoản.

## Security invariants

- Không cấp ADMIN qua public API.
- Seller capability chỉ có hiệu lực sau verification hợp lệ.
- Password và credential không xuất hiện trong log; API chỉ trả access token cho browser theo hợp đồng đăng nhập, không có refresh token.
- Browser không nhận face embedding và không gọi FastAPI trực tiếp.
- Khóa tài khoản không xóa lịch sử giao dịch.

## Completed tasks

- [x] TASK-0086: F1–F5/F7 auth, Google/link/OTP reset, thu hồi mọi phiên bằng credential version và Back Seller; kiểm local có bằng chứng, provider thật còn chờ.
- [x] Tạo package boundary và tài liệu module.
- [x] Triển khai giao diện React và Typed Mock Adapter cho Auth (UC-01) và eKYC Seller Verification (UC-02) theo Batch 1 ([TASK-0003](../progress/archive/TASK-0003-ui-batch-01-auth-and-ekyc.md)).
- [x] Đóng gói Microservice AI eKYC độc lập ([services/ekyc-service/](../../services/ekyc-service/)) với FastAPI, tích hợp YOLOv11n + VietOCR Transformer (144.8MB) + Gemini Flash + RetinaFace + DeepFace ArcFace 512-d (Cosine Distance <= 0.50) ([TASK-0004](../progress/archive/TASK-0004-integrate-ekyc-ai-algorithm-microservice.md)).
- [x] TASK-0005 thay luồng mock bằng Spring Boot Identity authority; TASK-0073 thay refresh rotation bằng access JWT và cookie có cùng thời hạn cố định.
- [x] TASK-0005 đưa eKYC sau Spring Boot gateway; FastAPI dùng internal token, request-local embedding và fail-closed.
- [x] TASK-0005 dùng Flyway V2 để loại dữ liệu CCCD/face embedding thật khỏi schema runtime, chỉ giữ metric và metadata model.
- [x] TASK-0012 triển khai MVP Seller Activation: Flyway V4 hỗ trợ method `MVP_BYPASS`; endpoint `POST /api/v1/seller-verification/activate` cấp role `SELLER` idempotent; UI `/seller-verification` minh bạch thông báo bước eKYC thật đang tạm thời được bỏ qua.
- [x] TASK-0013 cung cấp application facade `IdentityCatalogFacade` (`DefaultIdentityCatalogFacade`) làm hợp đồng liên module cho Catalog kiểm tra seller capability từ database và batch lookup thông tin Người bán công khai (`sellerId`, `displayName`, `trustLabel`) mà không vi phạm ranh giới module.

## Remaining tasks (Lộ trình Bước 2 & Backend)

- [x] TASK-0006 implementation: SMTP/Mailpit và OTP reset password trong TASK-0082/0086; Gmail thật còn cần kiểm.
- [x] TASK-0007 implementation: Google ID token/nonce, registration và account linking trong TASK-0086; provider E2E thật còn cần kiểm.
- [ ] Kiểm Google/Gmail thật sau cấu hình origin/restart và áp V26 vào môi trường đang dùng sau backup/restore.
- [ ] UC09 bật/tắt 2FA bằng face, eKYC/KTV và quyền hạn chế từng hành động UC67 theo phạm vi tiếp nối.
- [ ] TASK-0008: Cloudinary avatar qua `AvatarStoragePort`.
- [ ] Hoàn thiện profile/address và audit role transition để xác minh trọn vẹn UC-01/UC-02.

## Expected changes

- Bổ sung cấu hình Cloudinary, Gmail SMTP và Google OAuth2 theo từng task.
- Xây dựng Media Upload Service sau outbound port do Identity sở hữu.
- Xây dựng Email Service gửi OTP reset mật khẩu và notification qua Gmail SMTP.
- DTO, controller, application service, security principal, repository, migration sẽ được bổ sung theo từng task được phê duyệt.
