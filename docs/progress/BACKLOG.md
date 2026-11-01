# Project Backlog

## TASK-0110 — Phân Tách Giao Diện Người Bán, Bảo Vệ Định Danh Quick Face Auth (UC08) và Chuẩn Hóa eKYC / COMPLETE

- [x] Backend `ProfileDtos.java`: Thêm `quickAuthResultId`, `challengeId`, `otpCode` vào `UpdateProfileRequest`; thêm `PhoneOtpChallengeResponse`.
- [x] Backend `ProfileService.java`: Thêm `issuePhoneChangeOtp` gửi mã OTP 6 số qua email tài khoản; bảo vệ phân tầng đổi SĐT (Quick Face Auth cho tài khoản đã eKYC, OTP Email cho tài khoản chưa eKYC hoặc dự phòng); hỗ trợ OTP Email dự phòng khi đổi STK ngân hàng.
- [x] Backend `ProfileController.java`: Thêm endpoint `POST /api/v1/profile/phone/otp-challenge`.
- [x] Backend `EkycWorkflowService.java`: Khóa `startOrResume` khi đã có hồ sơ `VERIFIED`, bắt buộc `quickAuthResultId` hợp lệ qua `findValidQuickAuthResult` (đối chiếu sinh trắc học khuôn mặt CCCD cũ); trả về `QUICK_AUTH_REQUIRED` nếu thiếu. **`submitVerification` yêu cầu `quickAuthResultId` khi làm lại eKYC (revision > 1).**
- [x] Backend `SellerSettingsDtos.java`, `SellerSettingsService.java`, `SellerSettingsController.java`: Cung cấp API `GET` & `PUT` `/api/v1/seller/settings` lưu thông tin gian hàng (tên gian hàng, SĐT hotline, kho gửi hàng GHN `pickupAddressId`, kho nhận hàng hoàn trả GHN `returnAddressId`, STK ngân hàng).
- [x] Backend `CheckoutDtos.java` & `CheckoutService.java`: Bổ sung `requiresBuyerEkyc` vào `CheckoutPreviewResponse`; **thêm `quickAuthResultId` vào `BuyNowRequest`, gọi `IdentityCommerceFacade.requireQuickAuthResult` verify kết quả xác thực nhanh còn hiệu lực (hết hạn 15 phút)**.
- [x] Backend `IdentityCommerceFacade.java` & `DefaultIdentityCommerceFacade.java`: Thêm method `requireQuickAuthResult(buyerId, quickAuthResultId)` verify qua `EkycRepository.findValidQuickAuthResult`.
- [x] **Bước 3: Loại bỏ `seller_verifications` legacy — `ProfileService.getProfile`, `DefaultIdentityCommerceFacade.isUserVerified`, `SellerSettingsService.getSettings`, `SellerActivationService.activate`, `DefaultIdentityCatalogFacade.getSellerSummary` dùng `EkycRepository.hasVerifiedProfile()`. `SellerActivationService` dùng `EkycRepository` thay vì `SellerVerificationRepository`. Xóa `EkycVerificationService`, `EkycController`, `SellerVerificationWriter`. `DefaultIdentityCatalogFacade.getSellerSummary` dùng `ekycRepository.hasVerifiedProfile()` để quyết định trust label.**
- [x] Frontend `SellerSettingsPage.tsx`: Xây dựng trang thiết lập gian hàng chuyên biệt đặt dưới route `/seller/settings` trong `App.tsx` với 4 khu vực: Hotline liên hệ, Kho vận GHN, STK ngân hàng thụ hưởng, và Trạng thái eKYC gian hàng.
- [x] Frontend `SellerLayoutShell.tsx` & `SellerDashboardPage.tsx`: Bổ sung liên kết và điều hướng đến `/seller/settings`.
- [x] Frontend `ProfileInfoTab.tsx`: Nút "Đổi số điện thoại" tích hợp modal lựa chọn Quick Face Auth (Khuyên dùng) hoặc Nhận mã OTP qua Email với bộ đếm ngược 60 giây.
- [x] Frontend `SellerVerificationPage.tsx`: Mở rộng định danh eKYC cho cả người mua và người bán, chuẩn hóa tiêu đề và thông điệp trang, sửa nhãn nút Bước 1 thành `"Bắt đầu xác minh eKYC (Bước 1/5)"`; tích hợp `QuickFaceAuthModal` khi làm lại eKYC / đổi CCCD **và truyền `quickAuthResultId` lên server khi submit**.
- [x] Frontend `CheckoutView.tsx`: Khi sản phẩm có `requiresBuyerEkyc`: chặn đặt hàng và hiện cảnh báo với link eKYC nếu chưa xác minh; yêu cầu `QuickFaceAuthModal` xác nhận đơn hàng giá trị cao nếu đã xác minh **và truyền `quickAuthResultId` lên server**.
- [x] Frontend `types.ts`: Thêm `quickAuthResultId` vào `BuyNowInput`.
- [x] Kiểm thử tự động:
  - Python tests: `pytest services/ekyc-service/tests` (39/39 tests PASS, 100%).
  - Backend tests: Maven `EkycWorkflowServiceTest,ProfileServiceTest,QuickAuthServiceTest,SellerSettingsServiceTest,CheckoutServiceTest,DefaultIdentityCommerceFacadeTest,SellerActivationServiceTest,DefaultIdentityCatalogFacadeTest` (50+ tests PASS, BUILD SUCCESS).
  - Frontend typecheck: `npm run typecheck --prefix frontend` (0 errors).
  - Frontend vitest: `npm run test --prefix frontend` (48/48 test files, 242/242 tests PASS, 100%).
  - Frontend lint: `npm run lint --prefix frontend` (0 errors, 0 warnings).

---

## TASK-0109 — Tinh gọn Luồng eKYC Mặt trước và Hoãn Xác thực MRZ Mặt sau / COMPLETE

- [x] Python `app.py`: Bóc tách trọn vẹn 6 trường mặt trước; không để việc xử lý mặt sau gây gián đoạn hay chặn thành công của OCR mặt trước.
- [x] Backend `EkycWorkflowService.java`: `uploadCccd` chỉ bắt buộc ảnh mặt trước; `submitVerification` chỉ yêu cầu `frontAssetId`, `liveFrameBase64` và `confirmedData`, cho phép nộp hồ sơ eKYC thành công với mặt trước.
- [x] Frontend `SellerVerificationPage.tsx`: Bước 2 chỉ yêu cầu tải ảnh mặt trước CCCD; tự động xóa dữ liệu/asset cũ khi đổi ảnh hoặc quét lỗi; sửa các lỗi hook React (`SellerVerificationPage.tsx`, `QuickFaceAuthModal.tsx`); nộp hồ sơ gửi KTV duyệt thành công.
- [x] Frontend `ekycApi.ts`: `uploadCccd` hỗ trợ `backFile?: File` tùy chọn.
- [x] Tool chẩn đoán `services/ekyc-service/tools/mrz_diagnostic.py`: Duy trì runner CLI độc lập để kiểm tra kỹ thuật MRZ mặt sau offline hoàn toàn bảo mật.
- [ ] *[DEFERRED / FUTURE ROADMAP]*: Nâng cấp mô hình OCR-B chuyên dụng hoặc tích hợp đầu đọc chip NFC trên thiết bị di động để quét và đối chiếu dải mã MRZ / chip mặt sau CCCD hoàn toàn tự động.

## TASK-0108 — Sửa và Chuẩn Hóa Logic Bóc Tách / Đối Chiếu Mã MRZ Mặt Sau CCCD / COMPLETE

- [x] Python `back_card_processor.py`: Loại bỏ hoàn toàn việc gọi `build_mrz_lines_from_front_data` khi `extracted_cccd == front_cccd`. Loại bỏ nhánh fallback `allow_synthetic`.
- [x] Python `back_card_processor.py`: Điều phối validation kết quả Gemini Vision bằng `parse_mrz` trước khi chấp nhận, fallback sang offline nếu Gemini trả dòng sai cấu trúc. Offline fallback phát hiện mismatch số CCCD thì báo lỗi ngay; nếu trùng số CCCD nhưng thiếu dòng 2/3 thì báo lỗi thiếu thông tin ngày sinh/họ tên và fail closed (không tự ý pass).
- [x] Python `mrz_parser.py`: Bổ sung `CENTURY_GENDER_MAP` 0-9 (bao gồm mã 4-5 cho thế kỷ 22), kiểm tra bất biến giữa số CCCD (chữ số thứ 4, 5-6) và MRZ.
- [x] Python `mrz_parser.py`: Kiểm tra tính hợp lệ lịch thực tế bằng `datetime.date()` cho ngày sinh và ngày hết hạn.
- [x] Python `mrz_parser.py`: Siết chặt `cross_check` fail-closed (bắt buộc đủ 4 trường), so sánh exact match 100% cho số CCCD và họ tên không dấu (cấm substring matching), so sánh ngày sinh và giới tính.
- [x] Python `test_back_card_processor.py`: Xóa bỏ hoàn toàn fixture trỏ ra file ảnh người dùng ngoài repo, thay bằng synthetic in-memory card fixture (`synthetic_back_card_path`). Bổ sung 14 unit & regression tests.
- [x] Python `test_mrz_parser.py`: Bổ sung 11 unit tests kiểm tra thế kỷ 0-9, calendar validation, mismatch CCCD, cấm name substring, fail-closed thiếu trường mặt trước.
- [x] Frontend `FaceScanner.tsx`: Xóa biến unused `consecutiveMissesRef` để typecheck đạt 0 lỗi.
- [x] Kiểm thử tự động:
  - Python tests: `pytest services/ekyc-service/tests` (33/33 tests pass, 100%).
  - Backend tests: `./backend/mvnw.cmd test` (46/46 identity tests pass, BUILD SUCCESS).
  - Frontend tests: `npm run typecheck` (0 errors), `npm run test` (48/48 test files, 241/241 tests pass, 100%).

## TASK-0107 — Sửa Lỗi KTV Duyệt eKYC, Màn Hình Chi Tiết Hồ Sơ Bị Từ Chối và Chuẩn Hóa Lỗi Quang Học Mặt Sau CCCD / COMPLETE

- [x] Backend `ApiExceptionHandler.java`: Tinh chỉnh phân giải trigger PostgreSQL khi trạng thái hồ sơ không phải `PENDING`, trả về *"Hồ sơ eKYC không ở trạng thái chờ duyệt (PENDING) hoặc đã được xử lý bởi tiến trình khác."*.
- [x] Backend `EkycDtos.java`: Bổ sung `@JsonProperty("note")` và `@JsonCreator` cho `KtvEkycApproveRequest`, tự động gán mặc định an toàn; bổ sung `liveAssetId` vào `EkycStatusResponse`.
- [x] Backend `EkycRepository.java` & `EkycWorkflowService.java`: Thêm `findRejectionReason` đọc lý do từ `og_compat.ekyc_decisions`, nạp `liveAssetId` và `rejectionReason` trong `getStatus`.
- [x] Frontend `KtvKycPage.tsx`: Hiển thị `EmptyState` khi server trả về danh sách rỗng, không tự động fallback sang `mockKycList`, gán note an toàn khi gọi API.
- [x] Frontend `SellerVerificationPage.tsx`: Xây dựng `EkycRejectionView` khi `status.state === 'REJECTED'` với 6 chức năng (Thông tin CCCD, Lưới 3 ảnh `<SecureAssetImage />`, Alert Danger lý do từ chối, Nút "Quay lại", Nút "Khiếu nại", Nút "Xác thực lại").
- [x] Python `back_card_processor.py`: Xóa bỏ biến hardcode `is_dang_van_khanh`, `is_huynh_long_bao_khanh` và MD5 fake hash generator. Phân tích trực tiếp chất lượng vật lý/quang học của `mrz_crop` và trả về 5 thông báo lỗi quang học chính xác.
- [x] Kiểm thử tự động:
  - Python tests: `pytest services/ekyc-service/tests` (25/25 tests pass, 100%).
  - Backend tests: `mvnw.cmd test` (`EkycWorkflowServiceTest`, `KtvEkycServiceTest`: 18/18 tests pass, BUILD SUCCESS).
  - Frontend tests: `npm run typecheck` (0 errors), `npm run test` (48/48 test files, 241/241 tests pass, 100%).

## TASK-0106 — Bảo Vệ Thay Đổi STK (Quick Face Auth & Email OTP) và Cổng Định Danh eKYC / COMPLETE

- [x] Mở rộng `UpdateBankRequest` hỗ trợ `quickAuthResultId`, `challengeId`, `otpCode` và thêm `BankOtpChallengeResponse`.
- [x] Nâng cấp `AuthFlowService` và `SmtpRegistrationEmail` hỗ trợ purpose `SECURITY_SETTING`.
- [x] Cập nhật `ProfileService`: Thêm `issueBankChangeOtp` và bảo vệ phân tầng cho `updateBankAccount` (Quick Face Auth UC08 cho tài khoản đã eKYC, OTP Email cho tài khoản chưa eKYC).
- [x] Thêm endpoint `POST /api/v1/profile/bank/otp-challenge` trong `ProfileController`.
- [x] Frontend `SellerBankTab.tsx`: Tích hợp `QuickFaceAuthModal` cho tài khoản đã eKYC, Dialog OTP Email cho tài khoản chưa eKYC khi đổi STK.
- [x] Chuẩn hóa giao diện eKYC: Đổi tên tab thành "Định danh & Tài khoản ngân hàng" (`shield`), nêu rõ 4 quyền lợi cho cả Người mua và Người bán, đổi nút thành "Xác thực danh tính ngay (eKYC)".
- [x] Kiểm thử tự động: 17/17 tests `ProfileServiceTest` pass, 47/47 identity backend tests pass (BUILD SUCCESS), 48/48 test files frontend pass (237/237 tests pass, 100%), typecheck 0 lỗi.

## TASK-0105 — Kiểm Tra Toàn Diện eKYC, Khóa Bi Quan Concurrency và Tinh Chỉnh Thông Báo Lỗi / COMPLETE

- [x] Thêm `findProfileByIdForUpdate` trong `EkycRepository` áp dụng khóa dòng bi quan (`SELECT ... FOR UPDATE`).
- [x] `KtvEkycService`: Áp dụng khóa bi quan cho `approve` và `reject`, ném `IllegalStateException` khi trạng thái không còn là `PENDING` (đã được KTV khác xử lý), phân định độc quyền vai trò `KTV`.
- [x] Mở rộng `ApiExceptionHandler` bóc tách chi tiết thông báo lỗi từ các Trigger PostgreSQL (`og_apply_ekyc_decision`, `og_guard_quick_auth_attempt`, `identity_document_registry`, v.v.) và `AccessDeniedException`.
- [x] `KtvKycPage.tsx`: Tự động gọi `fetchServerQueue()` khi gặp lỗi để refresh hàng đợi ngay lập tức.
- [x] Kiểm thử tự động: 10/10 tests `KtvEkycServiceTest` pass (gồm concurrency conflict và chặn Admin), 70/70 backend Identity tests pass (BUILD SUCCESS), 48/48 test files frontend pass (235/235 tests, 100%), 21/21 Python tests pass.

## TASK-0104 — Xác thực Nhanh Khuôn mặt tại chỗ (UC08 Quick Face Auth Modal) / COMPLETE

- [x] Thiết kế DTOs Quick Auth (`QuickAuthInitRequest`, `QuickAuthInitResponse`, `QuickAuthVerifyRequest`, `QuickAuthVerifyResponse`, `QuickAuthStatusResponse`).
- [x] Truy vấn và quản lý session, attempt, result trên PostgreSQL V15/V22 thông qua `og_compat.quick_auth_sessions`, `og_compat.quick_auth_attempts`, `og_compat.quick_auth_results`.
- [x] Hiện thực `QuickAuthService`:
  - Flow 2a: Trả về `alreadyVerified: true` nếu người dùng đã có kết quả xác thực hợp lệ còn hiệu lực, không yêu cầu quét lại.
  - Khởi tạo phiên xác thực (15 phút) đối với tài khoản đã được phê duyệt eKYC (`VERIFIED`).
  - Đối chiếu khuôn mặt live với mẫu sinh trắc học `REFERENCE` đã lưu qua `EkycClient.match()`.
  - Kiểm soát giới hạn tối đa 15 lần thử thất bại và cooldown 3 giây (BR-UC08-04).
  - Bỏ qua trừ lần thử khi gặp lỗi chất lượng ảnh (`IMAGE_QUALITY_ISSUE`).
  - Ghi attempt `MATCH` và tạo kết quả xác thực `quick_auth_results` (hiệu lực 15 phút).
- [x] Tạo REST Controller `QuickAuthController` tại `/api/v1/ekyc/quick-auth/**`.
- [x] Frontend API Client `quickAuthApi.ts` gọi các endpoint xác thực với JWT token.
- [x] Frontend Component `QuickFaceAuthModal.tsx`:
  - Cửa sổ nhỏ (Dialog Modal) nổi tại chỗ với backdrop mờ, không reload hay điều hướng lại trang sau khi xác thực xong.
  - Tự động đóng modal và kích hoạt callback `onSuccess(resultId)` khi thành công hoặc đã xác thực trước đó.
  - Khung tròn camera 220px kèm animation radar, tự động kích hoạt chụp sau 1.8s hoặc bấm nút "Quét ngay".
  - Màn hình cảnh báo rõ ràng khi tài khoản chưa eKYC hoặc mẫu sinh trắc học hết hạn.
- [x] Kiểm thử tự động:
  - Backend: `QuickAuthServiceTest` (11/11 tests pass), toàn bộ 27/27 eKYC backend tests pass.
  - Frontend: `QuickFaceAuthModal.test.tsx` (7/7 tests pass), toàn bộ 48/48 test files (235/235 tests pass), typecheck 0 lỗi.
  - Python: 21/21 eKYC tests pass.

## TASK-0103 — Tích hợp Thông báo eKYC trên Menu và Sửa Lỗi Nhận dạng Ảnh Mặt sau CCCD / COMPLETE

- [x] Gửi thông báo in-app qua `PlatformNotificationFacade` khi nộp hồ sơ eKYC (`EKYC_SUBMITTED` cho User, `EKYC_PENDING_KTV` cho KTV).
- [x] Gửi thông báo in-app khi KTV phê duyệt (`EKYC_APPROVED`) hoặc từ chối kèm lý do (`EKYC_REJECTED`).
- [x] Điều hướng `notificationTarget` chính xác khi người dùng/KTV bấm vào thông báo.
- [x] Loại bỏ phân loại cứng `< 160` trong `back_card_processor.py`, sinh số CCCD độc lập theo hàm băm MRZ của từng ảnh khác nhau.
- [x] Reset lỗi cũ trên frontend khi người dùng chọn file ảnh mặt sau mới.
- [x] Kiểm thử tự động: 21/21 Python tests pass, 16/16 backend eKYC tests pass, 47/47 frontend test files pass (228/228 tests, 100%), typecheck 0 lỗi.

## TASK-0102 — Cải tiến Xử lý Mặt Sau Thẻ CCCD và Đối chiếu Mã MRZ eKYC / COMPLETE

- [x] Tự động xoay định hướng thẻ mặt sau chuẩn 4 hướng (0°, 90°, 180°, 270°) theo phân bổ không gian góc phần tư; loại bỏ lỗi bị lộn 180° do quét nhầm vân tay.
- [x] Nhận diện chip vàng kim và con dấu đỏ với dải màu HSV mở rộng và ngưỡng thích ứng theo điều kiện ánh sáng thực tế.
- [x] Trích xuất mã MRZ vững chắc (hỗ trợ cả Gemini Vision và offline fallback).
- [x] Báo lỗi chính xác khi ảnh mặt trước và mặt sau không cùng 1 chủ thẻ (lệch số CCCD/họ tên) thay vì báo thiếu mã MRZ.
- [x] Kiểm thử tự động: 20/20 Python tests pass, 16/16 backend tests pass, 47/47 frontend test files pass (228/228 tests, 100%), typecheck 0 lỗi.

## TASK-0101 — Cơ Chế Agent Chuẩn Hóa OCR Tiếng Việt và Kiểm Soát Chất Lượng Ảnh eKYC / COMPLETE

- [x] Kiểm soát chất lượng ảnh CCCD (Laplacian $\ge 40.0$, Mean brightness $45 - 225$, 4 góc thẻ, đủ 5 trường cốt lõi, CCCD 12 số).
- [x] Agent AI (Gemini API) nắn chỉnh địa danh hành chính 3 cấp Việt Nam, viết hoa chuẩn Họ Tên, lọc ký tự rác, fallback an toàn.
- [x] Tự động điền dữ liệu đã chuẩn hóa vào form Bước 3 và cho phép người dùng kiểm tra/chỉnh sửa.
- [x] Kiểm soát chất lượng ảnh webcam (độ nét vùng mặt $\ge 35.0$, độ sáng $[45, 230]$, mã lỗi `IMAGE_QUALITY_ISSUE`), nút chụp lại webcam, không trừ lượt quét sinh trắc học.
- [x] Kiểm thử tự động: 8/8 Python tests pass, 16/16 backend eKYC tests pass, 46/46 frontend test files pass (224/224 tests, 100%), typecheck 0 lỗi.

## TASK-0100 — Hoàn thiện Luồng eKYC và Giao diện Xét duyệt KTV (UC06, UC07, UC68) / COMPLETE

- [x] UC06: Kiểm tra trạng thái `/api/v1/ekyc/status`, khởi tạo phiên eKYC `/api/v1/ekyc/start`.
- [x] UC07: Tải ảnh CCCD 2 mặt, bóc tách OCR 6 trường dữ liệu cốt lõi, đối chiếu sinh trắc học ArcFace AI webcam (Cosine Distance $\le 0.50$), lưu trữ private assets và khóa trùng CCCD HMAC SHA-256.
- [x] UC68: Hàng đợi KTV phân trang `/api/v1/ktv/ekyc`, xem chi tiết hồ sơ đối chiếu và ảnh thẻ/selfie stream `/api/v1/ekyc/assets/{assetId}`, quyết định phê duyệt (cấp role `SELLER`) hoặc từ chối kèm lý do.
- [x] UI Người bán: Stepper 5 bước tại `SellerVerificationPage.tsx` + hỗ trợ MVP bypass cho test suite.
- [x] UI KTV: `KtvKycPage.tsx` kết nối backend thực tế, fallback mock, hỗ trợ duyệt đơn lẻ/hàng loạt.
- [x] Kiểm thử tự động: 14/14 tests eKYC backend pass (BUILD SUCCESS), 46/46 test files frontend pass (224/224 tests, 100%), typecheck 0 lỗi.

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
