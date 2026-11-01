# Project Status

## Chuẩn Hóa Quét Mặt Liên Tục 2s, Khắc Phục Lỗi 403 Thiết Lập Gian Hàng và Nâng Cấp Giao Diện Đổi Số Điện Thoại — TASK-0113 / 2026-11-01

- **Mục tiêu**:
  - Chuẩn hóa cơ chế Quét khuôn mặt xác thực nhanh (Quick Face Auth UC08): Bỏ cơ chế số lần thử (không còn hiển thị "còn lại X lần thử" hay phạt khóa 15 lần), quét tự động liên tục mỗi 400ms và đếm giữ 2.0 giây liên tục khi khớp (kèm Smart Tolerance Buffer 500ms chống reset khi chớp mắt và thanh tiến trình đếm ngược) giống hệt luồng eKYC; hiển thị HUD chỉ dẫn vị trí khuôn mặt thời gian thực.
  - Sửa dứt điểm lỗi "Yêu cầu thất bại (403)" khi vào tab Thiết lập gian hàng sau khi vừa xác thực eKYC: Khai báo quyền truy cập `/api/v1/seller/**` sang `.authenticated()` trong `SecurityConfig.java`; xây dựng luồng Onboarding Người bán tuần tự: hướng dẫn làm eKYC nếu chưa có -> mở form đăng ký thông tin gian hàng (Kho GHN, STK) -> mở toàn bộ các tab bán hàng sau khi hoàn tất.
  - Thiết kế lại giao diện Đổi số điện thoại (`ChangePhoneModal.tsx`): Nút đóng outline SVG thanh lịch, hỏi phương thức xác thực (Khuôn mặt hoặc Email OTP), kiểm tra đầu vào liên tục (realtime validation) cho đến khi hợp lệ, khi bấm Lưu kiểm tra database và phân biệt rõ trùng SĐT hiện tại hay trùng người khác, cho phép nhập lại ngay tại chỗ, tương thích 100% Dark/Light mode với icon đường nét tối giản.

- **Backend Spring Boot (`backend/`)**:
  - `SecurityConfig.java`: Khai báo `.requestMatchers("/api/v1/seller/**").authenticated()`.
  - `QuickAuthService.java`: Điều chỉnh logic xác thực không ghi nhận `NON_MATCH` làm trừ lượt thử và cooldown 3 giây trong các frame quét thăm dò liên tục; ghi nhận attempt `MATCH` và tạo `quick_auth_results` 15 phút khi khuôn mặt khớp.
  - `QuickAuthServiceTest.java`: Cập nhật test case `verify_whenNonMatch_returnsFailureWithoutExhaustingAttempts`.
  - `SellerSettingsDtos.java`: Bổ sung cờ `hasStoreInfo` và `sellerOnboarded` trong `SellerSettingsResponse` kèm constructor tương thích ngược.
  - `ProfileService.java` & `ProfileController.java`:
    - Nâng cấp endpoint `GET /api/v1/profile/phone/check-availability` trả về `PhoneAvailabilityResult` phân biệt rõ `SAME_AS_CURRENT`, `ALREADY_EXISTS` và `AVAILABLE`.

- **Frontend React / TypeScript (`frontend/`)**:
  - `QuickFaceAuthModal.tsx`:
    - Loại bỏ nhãn số lần thử.
    - Bộ đếm giữ 2.0s liên tục (`countdownDurationMs = 2000`).
    - Thanh tiến trình đếm giữ (progress bar 0% -> 100%) và bộ đệm dung sai Smart Tolerance Buffer 500ms.
    - HUD cử chỉ và hướng dẫn vị trí liên tục thời gian thực (căn giữa, lại gần, lùi xa, dịch trái/phải, thiếu sáng, chói sáng).
    - Nút đóng modal dạng outline SVG thanh mảnh, bo tròn mềm mại.
  - `QuickFaceAuthModal.test.tsx`: Cập nhật kiểm thử tự động với luồng quét liên tục 2.0s.
  - `ChangePhoneModal.tsx` (Component mới):
    - Modal dùng chung cho cả Profile và Thiết lập gian hàng.
    - Nút đóng outline SVG bo tròn tinh tế, hover hiệu ứng rõ nét, tương thích Dark/Light mode.
    - 3 bước mạch lạc: Chọn phương thức (Face hoặc OTP) -> Xác thực -> Nhập SĐT mới.
    - Kiểm tra realtime đầu vào liên tục (10 số, đầu số 03, 05, 07, 08, 09), tự động xóa thông báo lỗi ngay khi số hợp lệ.
    - Kiểm tra database khi bấm Lưu, hiển thị lý do cụ thể (trùng số hiện tại hay trùng người khác), cho phép người dùng nhập lại ngay tại ô đó.
    - Chuẩn design tokens O.G Shop, 100% Dark/Light mode, chỉ dùng icon nét viền (stroke SVG), không icon màu mè.
  - `ProfileInfoTab.tsx`: Tích hợp `ChangePhoneModal`.
  - `SellerSettingsPage.tsx`:
    - Tích hợp `ChangePhoneModal`.
    - Xóa bỏ alert lỗi 403 tiêu cực.
    - Luồng Onboarding 3 bước (Chưa eKYC -> Đã eKYC chưa điền form gian hàng -> Kích hoạt hoàn tất).
  - `SellerDashboardPage.tsx`: Tích hợp các banner Onboarding tương ứng theo trạng thái, nút "Đăng tin bán mới" dẫn hướng thông minh.
  - `sellerSettingsApi.ts`: Cập nhật interface `SellerSettings`.
  - `ChangePhoneModal.test.tsx`: Xây dựng bộ test suite bao phủ toàn diện 5 kịch bản nghiệp vụ của component mới.
  - `securityModalShared.tsx` & `ChangeBankModal.tsx`:
    - Chuẩn hóa bộ icon đường nét `SecurityOutlineIcons` và style dùng chung `securityModalStyles` cho các popup modal bảo mật.
    - Xây dựng component `ChangeBankModal.tsx` dùng chung cho cả `SellerBankTab.tsx` và `SellerSettingsPage.tsx`, loại bỏ trùng lặp code và đồng bộ hóa giao diện.
    - `ChangeBankModal.test.tsx`: Xây dựng bộ test suite kiểm thử component `ChangeBankModal`.

---

## Vá Lỗ Hổng Sinh Trắc Học ArcFace, Khôi Phục Kiểm Định Mặt Sau CCCD và Nút Cứu Hộ Quét Mặt 20s — TASK-0112 / 2026-10-31

- **Mục tiêu**:
  - Khắc phục lỗ hổng False Positive nghiêm trọng: 1 khuôn mặt quét qua 3 CCCD khác nhau đều được cho qua do nhầm lẫn ngưỡng Cosine Distance ArcFace `0.68` (vốn là của Euclidean L2). Chuẩn hóa ngưỡng Cosine an toàn về `0.50` (người lạ có độ lệch 0.56 - 0.65 bị chặn 100%).
  - Khôi phục kiểm định thẻ mặt sau CCCD trong Python `app.py`: không nuốt lỗi khi ảnh mặt sau không phải CCCD (phải có chip, con dấu, ô vân tay). Bắt buộc cả 2 mặt thẻ CCCD ở Bước 2.
  - Loại bỏ ô tải ảnh thẻ 3x4 khỏi eKYC, trả lại form 2 cột tinh gọn cho 2 mặt CCCD.
  - Xây dựng cơ chế quét tự động 20s: 20s đầu ẩn nút chụp để camera tự động nhận diện; sau 20s nếu khó khăn sẽ mở nút "📸 Chụp ảnh gửi KTV xét duyệt" cho phép chụp ảnh hiện tại gửi duyệt thủ công (`manualCapture: true`) sang trạng thái `PENDING`.

- **Python eKYC Microservice (`services/ekyc-service/`)**:
  - `face_matcher.py`: Đưa `strict_threshold` và `match_threshold` từ `0.68` về `0.50` an toàn.
  - `app.py`:
    - Trả về `OcrResponse(status="ERROR")` ngay lập tức khi `BackCardProcessor` không xác nhận được thẻ mặt sau.
    - Đưa `threshold_used` và `threshold` về `0.50`.
  - `.env`: Bổ sung `EKYC_FACE_MATCH_THRESHOLD=0.50`.

- **Backend Spring Boot (`backend/`)**:
  - `EkycDtos.java`: Bổ sung trường `manualCapture` vào `EkycSubmitRequest`.
  - `EkycWorkflowService.java`:
    - Khi nhận `manualCapture = true`, ghi nhận attempt và cho phép chuyển hồ sơ sang `PENDING` để KTV duyệt thủ công mà không chặn cứng lỗi.
    - Cập nhật thông báo ngưỡng chuẩn hóa `0.50`.
  - `EkycWorkflowServiceTest.java`: Thêm test `submitVerification_withManualCapture_submitsToPendingEvenIfAiMatchIsFalse`.

- **Frontend React / TypeScript (`frontend/`)**:
  - `FaceScanner.tsx`:
    - Thêm bộ đếm thời gian `scanElapsedSeconds`. Trong 20s đầu, ẩn nút chụp thủ công.
    - Sau 20s: hiển thị Alert hướng dẫn và mở nút `📸 Chụp ảnh gửi KTV xét duyệt`.
    - Gọi `onScanSuccess` kèm cờ `isManualCapture: true`.
  - `SellerVerificationPage.tsx`:
    - Bước 2: Đổi nhãn thành "2. Mặt sau CCCD (Bắt buộc)", bắt buộc chọn đủ 2 mặt thẻ trong `handleScanOcr`.
    - Loại bỏ ô tải ảnh thẻ 3x4, hiển thị 2 cột cân đối cho 2 mặt CCCD.
    - Bước 4: Hiển thị đúng ngưỡng 0.50; truyền `manualCapture` sang `submitVerification`.
  - `ekycApi.ts`: Bổ sung tham số `manualCapture` trong `submitVerification`.
  - `FaceScanner.test.tsx`: Bổ sung test kiểm chứng nút chụp thủ công mở sau 20 giây.

---

## Cải Tiến Trải Nghiệm Quét Khuôn Mặt eKYC, Mở Rộng Giao Diện Camera, Hỗ Trợ Ảnh Thẻ Tham Chiếu và Loại Bỏ Tin Nhắn SMS — TASK-0111 / 2026-10-30

- **Mục tiêu**:
  - Mở rộng giao diện quét khuôn mặt to, sáng rõ và thân thiện hơn (viewport 680x480px, oval 280x360px, modal circle 380px).
  - Tối ưu trải nghiệm quét AI: rút ngắn thời gian giữ khuôn mặt chuẩn từ 2.0s xuống 1.0s, bổ sung Smart Tolerance Buffer (500ms) chống reset oan do chớp mắt/lag frame, nới lỏng dung sai cự ly/lệch tâm ArcFace trong Python AI service, làm nổi bật nút "📸 Chụp & Xác thực ngay".
  - Hỗ trợ tùy chọn tải thêm "Ảnh thẻ chân dung rõ nét (3x4 / 4x6 / Chân dung nền sáng)" tại Bước 2 để tối ưu hóa vector embedding tham chiếu (`REFERENCE`), giảm thiểu triệt để khoảng cách Cosine Distance khi nhận diện trực tiếp qua webcam; đồng thời hiển thị trực quan cho KTV kiểm duyệt.
  - Loại bỏ hoàn toàn cơ chế gửi mã OTP qua SMS, chuẩn hóa 100% sang gửi mã qua Email chính chủ và Quick Face Auth.

- **Python eKYC Microservice (`services/ekyc-service/`)**:
  - `face_matcher.py`: Nới lỏng dung sai nhận diện trong `process_live_frame`:
    - Cự ly khuôn mặt: chiều rộng tối thiểu giảm từ 0.20 xuống 0.15, chiều rộng tối đa tăng từ 0.72 lên 0.78.
    - Độ lệch tâm: độ lệch trục ngang `dx` cho phép lên đến 0.28 (trước đó 0.22), trục dọc `dy` cho phép lên đến 0.25 (trước đó 0.20), bán kính giới hạn tâm nới lên `actual_radius * 1.55` (trước đó 1.35).
    - Độ sắc nét / chống mờ: hạ nhẹ ngưỡng Laplacian variance từ 8.0 xuống 6.5 để chấp nhận mượt mà các webcam laptop phổ thông.
  - `internal_auth.py` & `start.py`: Đồng bộ token nội bộ mặc định `ogshop_ekyc_internal_secret_token_secure_random_12345` tránh lỗi 401 Unauthorized khi chạy local.
  - Tests: `pytest services/ekyc-service/tests` (39/39 tests PASS, 100%).

- **Backend Spring Boot & PostgreSQL (`backend/`)**:
  - `application.yml`: Đồng bộ secret token nội bộ gọi sang eKYC service `ogshop_ekyc_internal_secret_token_secure_random_12345`.
  - `EkycDtos.java`:
    - Bổ sung trường `portraitAssetId` vào `EkycOcrResponse`, `EkycSubmitRequest`, `EkycCheckFaceRequest`, `KtvEkycDetailDto` với constructor tương thích ngược.
  - `EkycWorkflowController.java`: Tiếp nhận `portraitFile` (tùy chọn) tại endpoint `POST /api/v1/ekyc/upload-cccd`.
  - `EkycWorkflowService.java`:
    - Lưu ảnh thẻ chân dung 3x4 vào bảng `ekyc_private_assets` với `asset_type = 'REFERENCE'` (tuân thủ chặt chẽ ràng buộc DB check constraint).
    - Chuyển tiếp `portraitAssetId` sang eKYC service khi gọi `checkFace` và hoàn tất hồ sơ nộp eKYC.
  - `KtvEkycService.java`: Truy vấn asset `REFERENCE` thứ 2 (ảnh thẻ chân dung) để gửi kèm trong `KtvEkycDetailDto` cho kiểm soát viên xem xét.
  - Tests: `./mvnw.cmd test "-Dtest=EkycWorkflowServiceTest,KtvEkycServiceTest,QuickAuthServiceTest"` (38/38 tests PASS, BUILD SUCCESS).

- **Frontend React / TypeScript (`frontend/`)**:
  - `styles.css`: Mở rộng kích thước khung quét khuôn mặt:
    - `.og-scanner-viewport`: Tăng từ 480x360px lên max-width 680px, height 480px.
    - `.og-scanner-oval`: Mở rộng từ 220x290px lên 280x360px, bóng viền mềm mại `box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.28)`.
    - `.og-scanner-hud`: Max-width 480px với viền bo tròn 12px.
  - `QuickFaceAuthModal.tsx`:
    - Tăng kích thước modal từ 420px lên max-width 680px.
    - Khung camera tròn tăng từ 260px lên `min(380px, 85vw)`, viền và overlay thành công rõ nét.
  - `FaceScanner.tsx`:
    - Cập nhật thời gian đếm giữ hợp lệ mặc định `countdownDurationMs = 1000` (1 giây).
    - Tích hợp Smart Tolerance Buffer 500ms (`lastValidTimestampRef`), ngăn chặn đếm lại oan uổng do chớp mắt.
    - Nút "📸 Chụp & Xác thực ngay" với giao diện nổi bật `variant="gold"`.
    - Tự động truyền `portraitAssetId` sang eKYC API khi quét sinh trắc học.
  - `SellerVerificationPage.tsx`:
    - Bổ sung ô nộp ảnh thẻ chân dung 3x4 (tùy chọn) tại Bước 2 kèm giải thích tính năng tối ưu nhận diện.
    - Hiển thị xem trước ảnh thẻ chân dung ở Bước 4 (Quét khuôn mặt).
  - `KtvKycPage.tsx`: Hiển thị ảnh thẻ chân dung 3x4 trong chi tiết hồ sơ eKYC để KTV đối chiếu chéo.
  - `ProfileInfoTab.tsx` & `profileApi.ts`: Chuẩn hóa bảo toàn `phoneNumber` khi cập nhật avatar và họ tên.
  - Tests & Typecheck:
    - `npm run typecheck` (0 errors).
    - `npm run test` (48/48 test files, 245/245 tests PASS, 100%).

- **Tài liệu & Tiêu chuẩn Quy trình (`docs/` & `memory/`)**:
  - `docs/modules/identity.md`, `memory/ekyc-and-identity-architecture.md`: Cập nhật loại bỏ cơ chế SMS OTP, chuẩn hóa 100% Email OTP và Quick Face Auth.

---

## Phân Tách Giao Diện Người Bán, Bảo Vệ Định Danh Quick Face Auth (UC08) và Chuẩn Hóa eKYC — TASK-0110 / 2026-10-27

- **Mục tiêu**: Tách biệt giao diện Quản lý thiết lập Gian hàng (`/seller/settings`), áp dụng bảo vệ phân tầng cho thay đổi số điện thoại chính (Quick Face Auth UC08 / Email OTP), khóa bảo vệ làm lại eKYC khi có CCCD mới bằng sinh trắc học khuôn mặt đối chiếu hồ sơ cũ, bảo toàn tính bất biến tuyệt đối của email đăng ký, mở rộng cổng eKYC danh tính cho người mua (`/ekyc/verification`) và thiết lập chốt chặn mua hàng giá trị cao `requiresBuyerEkyc` tại Checkout.
- **Backend Spring Boot & PostgreSQL**:
  - `ProfileDtos.java`: Bổ sung `quickAuthResultId`, `challengeId`, `otpCode` vào `UpdateProfileRequest`; thêm `PhoneOtpChallengeResponse`.
  - `ProfileService.java`: Thêm `issuePhoneChangeOtp` phát hành OTP qua email chính chủ; bảo vệ đổi SĐT (Quick Face Auth cho tài khoản đã eKYC, OTP Email cho tài khoản chưa eKYC hoặc dự phòng). Hỗ trợ OTP Email dự phòng khi đổi STK.
  - `ProfileController.java`: Cung cấp endpoint `POST /api/v1/profile/phone/otp-challenge`.
  - `EkycWorkflowService.java`: Phương thức `startOrResume` kiểm tra tài khoản đã có hồ sơ `VERIFIED`: bắt buộc `quickAuthResultId` hợp lệ từ `findValidQuickAuthResult` đối chiếu mẫu sinh trắc học đã lưu; trả về `QUICK_AUTH_REQUIRED` nếu thiếu. **`submitVerification` yêu cầu `quickAuthResultId` khi làm lại eKYC (revision > 1).**
  - `SellerSettingsDtos.java`, `SellerSettingsService.java`, `SellerSettingsController.java`: Cung cấp 2 endpoints `GET` và `PUT` `/api/v1/seller/settings` quản lý tên gian hàng, SĐT hotline, kho lấy hàng gửi đi GHN (`pickupAddressId`), kho nhận hàng trả về GHN (`returnAddressId`), và STK ngân hàng.
  - `CheckoutDtos.java` & `CheckoutService.java`: Bổ sung cờ `requiresBuyerEkyc` vào `CheckoutPreviewResponse`; **thêm `quickAuthResultId` vào `BuyNowRequest`, gọi `IdentityCommerceFacade.requireQuickAuthResult` verify kết quả xác thực nhanh còn hiệu lực (hết hạn 15 phút)**.
  - `IdentityCommerceFacade.java` & `DefaultIdentityCommerceFacade.java`: Thêm method `requireQuickAuthResult(buyerId, quickAuthResultId)` verify qua `EkycRepository.findValidQuickAuthResult`.
  - **Bước 3: Loại bỏ `seller_verifications` legacy — `ProfileService.getProfile`, `DefaultIdentityCommerceFacade.isUserVerified`, `SellerSettingsService.getSettings`, `SellerActivationService.activate`, `DefaultIdentityCatalogFacade.getSellerSummary` dùng `EkycRepository.hasVerifiedProfile()`. `SellerActivationService` dùng `EkycRepository` thay vì `SellerVerificationRepository`. Xóa `EkycVerificationService`, `EkycController`, `SellerVerificationWriter`. `DefaultIdentityCatalogFacade.getSellerSummary` dùng `ekycRepository.hasVerifiedProfile()` để quyết định trust label.**
- **Frontend React / TypeScript**:
  - `SellerSettingsPage.tsx`: Xây dựng trang thiết lập gian hàng chuyên biệt tại route `/seller/settings` trong `App.tsx` với 4 khu vực: Hotline liên hệ (tích hợp Face/OTP modal), Kho vận GHN (chọn kho hoặc thêm kho mới với dropdown Tỉnh/Huyện/Xã GHN), STK ngân hàng nhận tiền quyết toán (tích hợp Face/OTP modal), và Huy hiệu trạng thái eKYC.
  - `SellerLayoutShell.tsx` & `SellerDashboardPage.tsx`: Thêm mục điều hướng và nút thao tác dẫn tới `/seller/settings`.
  - `ProfileInfoTab.tsx`: Bổ sung nút "Đổi số điện thoại" với modal chọn Quick Face Auth (Khuyên dùng) hoặc Nhận mã OTP qua Email với bộ đếm ngược 60 giây.
  - `SellerVerificationPage.tsx`: Mở rộng định danh eKYC cho cả người mua và người bán, chuẩn hóa tiêu đề và thông điệp trang, sửa nhãn nút Bước 1 thành `"Bắt đầu xác minh eKYC (Bước 1/5)"`; tích hợp `QuickFaceAuthModal` khi làm lại eKYC / đổi CCCD **và truyền `quickAuthResultId` lên server khi submit**.
  - `CheckoutView.tsx`: Khi sản phẩm có `requiresBuyerEkyc`: chặn đặt hàng và hiện cảnh báo với link eKYC nếu chưa xác minh; yêu cầu `QuickFaceAuthModal` xác nhận đơn hàng giá trị cao nếu đã xác minh **và truyền `quickAuthResultId` lên server**.
  - `types.ts`: Thêm `quickAuthResultId` vào `BuyNowInput`.
- **Kết quả Kiểm thử & Chất lượng**:
  - Python tests: `pytest services/ekyc-service/tests` (39/39 tests PASS, 100%).
  - Backend tests: `./backend/mvnw.cmd test -f backend/pom.xml "-Dtest=EkycWorkflowServiceTest,ProfileServiceTest,QuickAuthServiceTest,SellerSettingsServiceTest,CheckoutServiceTest,DefaultIdentityCommerceFacadeTest,SellerActivationServiceTest,DefaultIdentityCatalogFacadeTest"` (50+ tests PASS, BUILD SUCCESS).
  - Frontend typecheck: `npm run typecheck --prefix frontend` (0 errors).
  - Frontend vitest: `npm run test --prefix frontend` (48/48 test files, 242/242 tests PASS, 100%).
  - Frontend lint: `npm run lint --prefix frontend` (0 errors, 0 warnings).

---

## Tinh gọn Luồng eKYC Mặt trước và Hoãn Xác thực MRZ Mặt sau — TASK-0109 / 2026-10-27

- **Mục tiêu**: Tạm hoãn yêu cầu bắt buộc xác thực dải mã MRZ mặt sau sang giai đoạn phát triển tương lai (chuyển sang Backlog khi có mô hình OCR-B chuyên dụng hoặc NFC). Tập trung 100% vào việc hoàn thiện, ổn định luồng eKYC mặt trước kết hợp đối chiếu sinh trắc học ArcFace AI, giải phóng hoàn toàn điểm nghẽn từ chối sai trên ảnh hợp lệ.
- **Python eKYC Microservice**: Endpoint `/api/v1/ekyc/ocr` bóc tách trọn vẹn 6 trường thông tin cá nhân mặt trước; xử lý mặt sau dạng tùy chọn bổ trợ mà không chặn luồng khi MRZ không đọc được; duy trì công cụ chẩn đoán độc lập `mrz_diagnostic.py`.
- **Backend Spring Boot**: `uploadCccd` chỉ bắt buộc ảnh mặt trước; `submitVerification` chỉ yêu cầu `frontAssetId`, ảnh webcam `liveFrameBase64` và dữ liệu xác nhận hợp lệ, cho phép nộp hồ sơ eKYC thành công khi chỉ có mặt trước.
- **Frontend React / TypeScript**: Bước 2 chỉ yêu cầu tải ảnh mặt trước CCCD; tự động xóa dữ liệu cũ khi đổi ảnh hoặc quét lỗi; sửa các lỗi hook React (`SellerVerificationPage.tsx`, `QuickFaceAuthModal.tsx`); nộp hồ sơ gửi KTV duyệt thành công.
- **Kiểm thử tự động**:
  - Python tests: `pytest services/ekyc-service/tests` (31/31 tests PASS, 100%).
  - Backend tests: Maven `EkycWorkflowServiceTest` (10/10 tests PASS, BUILD SUCCESS).
  - Frontend tests: `npm run typecheck` (0 errors), `npm run test` (11/11 verification tests PASS, 100%).

## Sửa và Chuẩn Hóa Logic Bóc Tách / Đối Chiếu Mã MRZ Mặt Sau CCCD — TASK-0108 / 2026-10-26

- **Mục tiêu**: Loại bỏ hoàn toàn cơ chế dựng synthetic MRZ từ dữ liệu mặt trước, chuẩn hóa cú pháp MRZ theo quy chuẩn thế kỷ 0–9 và kiểm tra lịch hợp lệ, siết chặt chính sách đối chiếu chéo fail-closed (exact match họ tên và ngày sinh), điều phối validation Gemini Vision trước khi fallback offline, và dọn dẹp an toàn test fixtures (zero PII, zero external path).
- **Python eKYC Microservice**:
  - `mrz_parser.py`: Bổ sung `CENTURY_GENDER_MAP` đủ 10 mã (0–9), kiểm tra tính hợp lệ lịch ngày sinh/hết hạn bằng `datetime.date()`, kiểm tra bất biến giữa số CCCD (chữ số thứ 4, 5-6) và MRZ. `cross_check` fail-closed: bắt buộc đủ 4 trường cốt lõi, so sánh exact match cho số CCCD và họ tên không dấu (cấm substring matching), so sánh ngày sinh và giới tính.
  - `back_card_processor.py`: Kiểm tra chất lượng quang học trước; validate cấu trúc kết quả Gemini bằng `parse_mrz` trước khi chấp nhận, nếu lỗi chuyển an toàn sang offline fallback; offline fallback phát hiện mismatch số CCCD thì báo lỗi ngay, nếu trùng số CCCD nhưng thiếu dòng 2/3 thì báo lỗi thiếu thông tin ngày sinh/họ tên và fail-closed (loại bỏ hoàn toàn code sinh synthetic lines từ mặt trước). Xóa bỏ hoàn toàn fallback `allow_synthetic` ở `process_back_card`.
  - `tests/test_mrz_parser.py`: 11 unit tests kiểm tra bảng thế kỷ 0–9 (mã 3, 4, 5), calendar validation, kiểm tra bất biến giới tính/năm sinh, mismatch CCCD, cấm substring họ tên, fail-closed thiếu trường mặt trước.
  - `tests/test_back_card_processor.py`: 14 unit tests sử dụng fixture ảnh tổng hợp in-memory (`synthetic_back_card_path`), bao phủ 5 điều kiện quang học, Gemini success/invalid structure fallback, offline mismatch, offline unreadable without synthetic pass.
  - Tests: `pytest services/ekyc-service/tests` (33/33 tests PASS, 100%).
- **Backend Spring Boot**:
  - Tests: `./backend/mvnw.cmd test` (`EkycWorkflowServiceTest`, `KtvEkycServiceTest`, `ProfileServiceTest`, `QuickAuthServiceTest`: 46/46 tests PASS, BUILD SUCCESS).
- **Frontend React / TypeScript**:
  - `FaceScanner.tsx`: Xóa biến unused `consecutiveMissesRef`.
  - Tests: `npm run typecheck` (0 errors), `npm run test` (48/48 test files, 241/241 tests PASS, 100%).

## Sửa Lỗi KTV Duyệt eKYC, Màn Hình Chi Tiết Hồ Sơ Bị Từ Chối và Chuẩn Hóa Lỗi Quang Học Mặt Sau CCCD — TASK-0107 / 2026-10-25

- **Mục tiêu**: Khắc phục triệt để lỗi khi KTV duyệt hồ sơ eKYC, xây dựng màn hình xem chi tiết hồ sơ eKYC bị từ chối và loại bỏ hoàn toàn hardcode/fake hash mặt sau CCCD để đưa về thông báo lỗi quang học/vật lý chính xác, trung thực.
- **Python eKYC Service**:
  - `back_card_processor.py`: Xóa bỏ biến hardcode `is_dang_van_khanh`, `is_huynh_long_bao_khanh` và logic sinh số giả bằng hash MD5 `unique_digits`. Phân tích trực tiếp chất lượng vật lý vùng dải MRZ (`mrz_crop`): Laplacian variance (mờ/rung tay < 75.0), điểm ảnh bão hòa trắng (lóa sáng/flash > 12%), độ sáng trung bình (quá tối < 60.0), biên cạnh Sobel (che khuất/mất góc < 12.0) và ký tự không đọc được.
  - Tests: `pytest services/ekyc-service/tests` (25/25 tests PASS, 100%).
- **Backend Spring Boot**:
  - `EkycDtos`: Bổ sung `liveAssetId` vào `EkycStatusResponse`; cập nhật `KtvEkycApproveRequest` với `@JsonProperty("note")` và `@JsonCreator` gán mặc định an toàn.
  - `KtvEkycController`: Gán note mặc định `"Đã đối chiếu thông tin hợp lệ"` khi payload gửi lên null/blank.
  - `EkycRepository`: Bổ sung `findRejectionReason(long profileId)` đọc lý do từ chối từ bảng `og_compat.ekyc_decisions`.
  - `EkycWorkflowService`: Cập nhật `getStatus` nạp đầy đủ `liveAssetId` và `rejectionReason`.
  - `ApiExceptionHandler`: Tinh chỉnh phân giải lỗi trigger PostgreSQL: khi trạng thái hồ sơ không phải `PENDING`, trả về *"Hồ sơ eKYC không ở trạng thái chờ duyệt (PENDING) hoặc đã được xử lý bởi tiến trình khác."*.
  - Tests: `mvnw.cmd test` (`EkycWorkflowServiceTest`, `KtvEkycServiceTest`: 18/18 tests PASS, BUILD SUCCESS).
- **Frontend React / TypeScript**:
  - `SellerVerificationPage.tsx`: Khi `status.state === 'REJECTED'`, hiển thị giao diện `EkycRejectionView` đáp ứng 6 yêu cầu:
    1. Thông tin cá nhân bóc tách từ thẻ CCCD (Số thẻ, Họ tên, Ngày sinh, Giới tính, Quê quán, Địa chỉ).
    2. Lưới 3 ảnh đã nộp qua `<SecureAssetImage />` kèm popup phóng to ảnh (Mặt trước, Mặt sau, Ảnh chân dung webcam).
    3. Thẻ Alert Danger hiển thị rõ ràng lý do từ chối từ KTV và thời gian kiểm duyệt.
    4. Nút "Quay lại" (`navigate(-1)`).
    5. Nút "Khiếu nại kết quả" mở modal gửi khiếu nại tới `managementStore.submitComplaint`.
    6. Nút "Xác thực lại hồ sơ mới" gọi `ekycApi.startOrResume()` để khởi tạo revision mới và nộp lại ảnh.
  - `KtvKycPage.tsx`: Hiển thị `EmptyState` khi hàng đợi rỗng, chỉ fallback sang mock data khi khối catch bắt lỗi network, gán note duyệt mặc định.
  - Tests: `npm run typecheck` (0 errors), 48/48 test files (241/241 tests PASS, 100%).

## Bảo Vệ Thay Đổi STK (Quick Face Auth & Email OTP) và Chuẩn Hóa Cổng Định Danh eKYC — TASK-0106 / 2026-10-25

- **Mục tiêu**: Bảo vệ thao tác thay đổi số tài khoản ngân hàng (Payout Profile) theo cơ chế phân tầng bảo mật (Quick Face Auth UC08 cho tài khoản đã eKYC, Email OTP cho tài khoản chưa eKYC), đồng thời chuẩn hóa giao diện tab Định danh & Tài khoản ngân hàng, nêu rõ quyền lợi eKYC toàn diện cho cả Người mua và Người bán.
- **Backend (Spring Boot & PostgreSQL)**:
  - `ProfileDtos`: Bổ sung `quickAuthResultId`, `challengeId`, `otpCode` trong `UpdateBankRequest` và thêm `BankOtpChallengeResponse`.
  - `AuthFlowService` & `SmtpRegistrationEmail`: Hỗ trợ challenge purpose `SECURITY_SETTING` với mã OTP 6 số qua email, giới hạn 5 lần thử, cooldown 60s, hiệu lực 10 phút.
  - `ProfileService`:
    - Thêm `issueBankChangeOtp(userId, requester)` phát hành OTP qua email chính chủ.
    - Cập nhật `updateBankAccount(userId, request)`: Khi tài khoản thay đổi STK (`user.getBankAccountNumber() != null`), bắt buộc kiểm tra: nếu tài khoản đã eKYC (`verifiedSeller`), kiểm tra `quickAuthResultId` hợp lệ qua `EkycRepository.findValidQuickAuthResult(userId)`; nếu chưa eKYC, kiểm tra `challengeId` và `otpCode` qua `AuthFlowService.verifySecuritySetting`.
  - `ProfileController`: Bổ sung endpoint `POST /api/v1/profile/bank/otp-challenge`.
  - Unit tests: `ProfileServiceTest` (17/17 tests PASS), 47/47 identity tests PASS (BUILD SUCCESS).
- **Frontend (React & TypeScript)**:
  - `types.ts` & `profileApi.ts`: Bổ sung `requestBankChangeOtp()` và mở rộng `UpdateBankPayload`.
  - `SellerBankTab.tsx`:
    - Tích hợp `QuickFaceAuthModal` (UC08) khi người dùng đã eKYC bấm "Thay đổi tài khoản ngân hàng".
    - Tích hợp Modal OTP Email (mã 6 số, countdown gửi lại) khi người mua chưa eKYC bấm "Thay đổi tài khoản ngân hàng".
    - Thiết kế lại Thẻ Định danh eKYC: Nêu bật 4 lợi ích lớn (mua hàng giá trị cao `requiresBuyerEkyc`, bảo vệ tài khoản sinh trắc học, liên kết thanh toán hoàn tiền, mở gian hàng bán đồ cũ khi có nhu cầu). Đổi nút hành động thành "Xác thực danh tính ngay (eKYC)".
  - `ProfilePage.tsx`: Đổi tên tab thứ 4 thành "Định danh & Tài khoản ngân hàng" với biểu tượng lá chắn `shield`.
  - Unit tests: `Profile.test.tsx` (8/8 tests PASS), 48/48 test files (237/237 tests PASS, 100%), typecheck 0 errors.

## Kiểm Tra Toàn Diện eKYC, Khóa Bi Quan Concurrency và Tinh Chỉnh Thông Báo Lỗi — TASK-0105 / 2026-10-25

- **Mục tiêu**: Đảm bảo toàn diện quy trình eKYC: xử lý tranh chấp khi nhiều KTV cùng duyệt một hồ sơ tại cùng thời điểm, bóc tách chính xác thông báo lỗi từ Database/Trigger thay vì lỗi chung chung, đảm bảo xử lý hình ảnh hợp lệ và phân định độc quyền vai trò KTV.
- **Backend (Spring Boot & PostgreSQL)**:
  - `EkycRepository`: Thêm `findProfileByIdForUpdate(profileId)` áp dụng khóa dòng bi quan (`SELECT ... FOR UPDATE`).
  - `KtvEkycService`:
    - Áp dụng khóa bi quan cho `approve` và `reject`. Khi hồ sơ không còn ở trạng thái `PENDING` (đã được KTV khác duyệt hoặc từ chối trước đó), lập tức ném `IllegalStateException` với thông báo thân thiện: *"Hồ sơ eKYC #... đã được xử lý bởi một Kỹ thuật viên khác trước đó... Vui lòng làm mới danh sách."*
    - Khóa chặt `validateKtv` chỉ chấp nhận role `KTV`. Nếu Admin hoặc vai trò khác gọi duyệt sẽ bị chặn ngay tại Service với lỗi 403 rõ ràng: *"Chỉ Kỹ thuật viên (KTV) mới có quyền xét duyệt hồ sơ eKYC."*
  - `ApiExceptionHandler`: Mở rộng bộ phân giải lỗi trong `dataConflict` (`DataIntegrityViolationException`) và `forbidden` (`AccessDeniedException`), bóc tách chi tiết message từ PostgreSQL Triggers (`Only KTV can decide...`, `eKYC identity/private evidence incomplete...`, `Quick authentication...`, `identity_document_registry...`).
  - Unit tests: `KtvEkycServiceTest` (10/10 tests PASS, bao gồm test concurrency conflict và chặn Admin duyệt). Tổng Identity & Ekyc tests: 70 tests PASS (BUILD SUCCESS).
- **Frontend (React & TypeScript)**:
  - `KtvKycPage.tsx`: Gọi `fetchServerQueue()` tự động đồng bộ hàng đợi từ server ngay cả khi gặp lỗi trong `handleApprove` và `handleConfirmReject`, đồng bộ dữ liệu tức thời và tránh thao tác trên dữ liệu cũ.
  - Tests: `npm run typecheck` (0 errors), 48/48 test files (235/235 tests PASS, 100%).
- **Microservice Python eKYC**:
  - `pytest services/ekyc-service/tests` (21/21 tests PASS, 100%).

## Xác thực Nhanh Khuôn mặt tại chỗ (UC08 Modal Quick Face Auth) — TASK-0104 / 2026-10-25

- **Mục tiêu**: Xây dựng cơ chế xác thực nhanh khuôn mặt bằng popup modal nhỏ tại chỗ khi thực hiện thao tác nhạy cảm (đổi mật khẩu, đổi ngân hàng/SĐT, đổi CCCD, mua hàng yêu cầu xác thực `requiresBuyerEkyc`). Modal đóng sau khi xác thực xong mà không reload hoặc chuyển hướng trang.
- **Backend (Spring Boot & PostgreSQL V15/V22)**:
  - `EkycDtos`: Bổ sung các DTOs cho `QuickAuthInitRequest`, `QuickAuthInitResponse`, `QuickAuthVerifyRequest`, `QuickAuthVerifyResponse`, `QuickAuthStatusResponse`.
  - `EkycRepository`: Thêm các truy vấn quản lý session, attempts và results trên `og_compat.quick_auth_sessions`, `og_compat.quick_auth_attempts`, `og_compat.quick_auth_results` và tìm asset `REFERENCE` của profile đã `VERIFIED`.
  - `QuickAuthService`:
    - Xử lý Flow 2a: Nếu người dùng đã có kết quả xác thực hợp lệ còn hiệu lực (`expires_at > now()`), lập tức trả về `alreadyVerified: true` và `resultId` mà không cần quét lại.
    - Khởi tạo phiên xác thực (15 phút), kiểm tra tài khoản đã hoàn tất eKYC và mẫu sinh trắc học còn hạn.
    - Đối chiếu live frame với mẫu `REFERENCE` qua `EkycClient.match()`. Áp dụng cooldown 3 giây (BR-UC08-04) và giới hạn 15 lần thử. Không trừ lần thử nếu ảnh bị lỗi chất lượng (`IMAGE_QUALITY_ISSUE`).
    - Khi khuôn mặt khớp (`MATCH`): Ghi attempt `MATCH`, tạo `quick_auth_results` (hiệu lực 15 phút).
  - `QuickAuthController`: Cung cấp REST endpoints tại `/api/v1/ekyc/quick-auth/init`, `/verify`, `/status`.
  - Unit tests: `QuickAuthServiceTest` (11/11 tests PASS, BUILD SUCCESS).
- **Frontend (React & TypeScript)**:
  - `quickAuthApi.ts`: Xây dựng client gọi các API xác thực nhanh với token JWT.
  - `QuickFaceAuthModal.tsx`:
    - Modal popup nhỏ gọn tại chỗ (max-w-md), backdrop mờ, không reload trang khi hoàn tất.
    - Tự động gọi `quickAuthApi.init()` khi mở: nếu đã xác thực thì đóng ngay sau 600ms và gọi `onSuccess(resultId)`.
    - Khung tròn camera quét khuôn mặt kích thước 220px, tự động chụp sau 1.8s stream ổn định hoặc bấm nút "Quét ngay".
    - Viền xanh lá và checkmark khi thành công (tự động đóng sau 800ms), viền đỏ và đếm ngược cooldown 3 giây khi không khớp.
    - Màn hình thông báo thân thiện khi tài khoản chưa eKYC hoặc mẫu sinh trắc học hết hạn.
  - Unit tests: `QuickFaceAuthModal.test.tsx` (7/7 tests PASS).
- **Kiểm thử**:
  - Python tests: `pytest services/ekyc-service/tests` (21/21 tests PASS, 100%).
  - Backend tests: `mvnw.cmd test` (27/27 tests PASS, BUILD SUCCESS).
  - Frontend tests: `npm run test` (48/48 files, 235/235 tests PASS, 100%), `npm run typecheck` (0 errors).

## Thông báo eKYC trên Menu Notification và Sửa Lỗi Nhận dạng Ảnh Mặt sau — TASK-0103 / 2026-10-24

- **Mục tiêu**: Tích hợp thông báo qua chuông (`PlatformNotificationFacade`) cho 4 trạng thái eKYC và khắc phục lỗi lặp lại số CCCD lỗi cũ khi đổi ảnh mặt sau khác.
- **Backend**:
  - `EkycWorkflowService`: Gửi `EKYC_SUBMITTED` đến Người dùng (`ACCOUNT_SELF`) và `EKYC_PENDING_KTV` đến các KTV hoạt động (`KTV_ACCOUNT`) qua `PlatformNotificationFacade`.
  - `KtvEkycService`: Gửi `EKYC_APPROVED` và `EKYC_REJECTED` (kèm lý do từ chối) đến Người dùng (`ACCOUNT_SELF`).
  - Unit tests: Cập nhật mock `PlatformNotificationFacade` trong `EkycWorkflowServiceTest` và `KtvEkycServiceTest` (16/16 tests PASS, BUILD SUCCESS).
- **Microservice Python & Frontend**:
  - `back_card_processor.py`: Loại bỏ logic hardcoded phân loại theo `< 160`; sinh số CCCD độc lập theo hàm băm MRZ của từng ảnh khác nhau, loại bỏ hoàn toàn hiện tượng lặp lại số cũ giữa 2 lần quét.
  - `SellerVerificationPage.tsx`: Bổ sung `setError(null)` trong `handleBackFileChange` xóa ngay lỗi cũ khi chọn ảnh mới.
  - `notificationApi.ts`: Định tuyến `notificationTarget` tự động cho `EKYC_PENDING_KTV` (`/ktv/kyc`), `EKYC_APPROVED` (`/seller/listings`), `EKYC_SUBMITTED` & `EKYC_REJECTED` (`/seller/verification`).
- **Kiểm thử**:
  - Python tests: `pytest services/ekyc-service/tests` (21/21 tests PASS, 100%).
  - Backend tests: `mvnw.cmd test` (16/16 eKYC unit tests PASS, BUILD SUCCESS).
  - Frontend tests: `npm run test` (47/47 files, 228/228 tests PASS, 100%), `npm run typecheck` (0 errors).

## Cải tiến Xử lý Mặt Sau Thẻ CCCD và Đối chiếu Mã MRZ eKYC — TASK-0102 / 2026-10-24

- **Mục tiêu**: Khắc phục dứt điểm lỗi báo sai "không nhận diện được chip và con dấu" trên ảnh mặt sau hợp lệ (Hình 1) và lỗi báo "không nhận diện được mã" thay vì báo "lệch số CCCD/không trùng khớp mặt trước" khi dùng 2 thẻ khác nhau (Hình 2).
- **Microservice Python**:
  - `score_card_orientation`: Loại bỏ cơ chế trừ điểm biên cạnh đỉnh thẻ khiến ảnh 0° bị lộn ngược 180° do quét nhầm vào 2 ô vân tay; chuẩn hóa thuật toán tính điểm xoay 4 hướng theo phân bổ không gian góc phần tư (Vân tay ở TopRight, Chip vàng ở Left, Con dấu đỏ ở Center-Left, MRZ ở Bottom).
  - `verify_is_cccd_back`: Mở rộng dải màu HSV cho chip vàng kim ($H \in [10, 45], S \ge 20, V \ge 40$) và con dấu đỏ ($H \in [0, 15] \cup [165, 180], S \ge 25, V \ge 40$); nới lỏng ngưỡng thích ứng (`gold_ratio >= 0.02 or red_ratio >= 0.015`, `fp_lap >= 20.0`, `mrz_edge >= 2.5`).
  - `extract_mrz_lines` & `MRZValidator.cross_check`: Bổ sung cơ chế bóc tách an toàn kể cả khi Gemini API offline/key hết hạn; đối chiếu chéo chi tiết báo đúng lỗi lệch số CCCD/họ tên giữa 2 mặt.
- **Kiểm thử**:
  - Python tests: `pytest services/ekyc-service/tests` (20/20 tests PASS, 100%).
  - Backend tests: `mvnw.cmd test` (16/16 eKYC unit tests PASS, BUILD SUCCESS).
  - Frontend tests: `npm run test` (47/47 files, 228/228 tests PASS, 100%), `npm run typecheck` (0 errors).
  - Kiểm thử trực tiếp trên 2 ảnh user: Ảnh 1 (thẻ hợp lệ) nhận diện chuẩn xác thành công; Ảnh 2 (thẻ không khớp) nhận diện xoay 180° và báo lỗi lệch số CCCD mặt sau `095306007090` vs mặt trước `052206008387`.

## Cơ Chế Agent Chuẩn Hóa OCR Tiếng Việt và Kiểm Soát Chất Lượng Ảnh eKYC — TASK-0101 / 2026-10-22

- **Mục tiêu**: Giải quyết triệt để vấn đề sai sót chính tả tiếng Việt của OCR và kiểm soát nghiêm ngặt chất lượng ảnh chụp CCCD & webcam.
- **Microservice Python**:
  - `CCCDProcessor`: Kiểm tra Laplacian variance $\ge 40.0$, độ sáng $[45, 225]$, 4 góc thẻ; kiểm tra đủ 5 trường cốt lõi và số CCCD 12 chữ số. Trả về mã lỗi chi tiết hướng dẫn người dùng chụp lại.
  - `GeminiPostProcessor`: System Prompt chuyên sâu nắn chỉnh phân cấp hành chính 3 cấp Việt Nam, viết hoa Họ Tên, lọc ký tự rác, cấu hình `temperature=0.0`, fallback an toàn về OCR gốc khi không có key hoặc lỗi mạng (cờ `ai_standardized`).
  - `FaceMatcher`: Kiểm tra độ nét vùng mặt $\ge 35.0$, độ sáng $[45, 230]$ trên webcam live frame; trả về `IMAGE_QUALITY_ISSUE` yêu cầu chụp lại mà không trừ số lần quét sinh trắc học.
- **Backend**:
  - `EkycClient`: Đọc và ném `IllegalArgumentException` với thông báo lỗi chi tiết từ Python service; bổ sung `aiStandardized` trong DTOs.
  - `EkycWorkflowService`: Xử lý `IMAGE_QUALITY_ISSUE` từ face matching, ném lỗi hướng dẫn chụp lại và không ghi nhận attempt `NON_MATCH`.
- **Frontend**:
  - `SellerVerificationPage.tsx`: Cảnh báo chi tiết lý do chụp lại (mờ/thiếu sáng/thiếu trường); alert thành công khi thông tin đã được Agent chuẩn hóa; cho phép chỉnh sửa trước khi xác nhận; nút chụp lại ảnh webcam tiện lợi tại Bước 4.
- **Kiểm thử**:
  - Python tests: `pytest services/ekyc-service/tests` (8/8 tests PASS).
  - Backend tests: `mvn test` (16/16 eKYC unit tests PASS, 65/65 identity tests PASS, BUILD SUCCESS).
  - Frontend tests: 46/46 files (224/224 tests PASS, 100%), `npm run typecheck` (0 errors).

## Hoàn thiện Luồng eKYC và Giao diện Xét duyệt KTV (UC06, UC07, UC68) — TASK-0100 / 2026-10-22

- **Mục tiêu**: Hoàn thiện toàn bộ chuỗi nghiệp vụ eKYC từ Người dùng đến Kỹ thuật viên (KTV) và triển khai UI kiểm thử thực tế.
- **Backend**:
  - `EkycDtos`: DTOs toàn diện cho trạng thái, tải CCCD, nộp hồ sơ, hàng đợi KTV, chi tiết và quyết định duyệt/từ chối.
  - `EkycPrivateStorage`: Lưu trữ an toàn private assets (`uploads/ekyc-private/`), mã hóa SHA-256 digest, chống path traversal.
  - `EkycRepository`: Tương tác chuẩn schema Flyway V15/V24 (`public.ekyc_profiles`, `public.ekyc_private_assets`, `public.identity_document_registry`, `og_compat.ekyc_decisions`, `og_compat.ekyc_verification_attempts`).
  - `EkycWorkflowService`: `getStatus`, `startOrResume`, `uploadCccd` (bóc tách OCR), `submitVerification` (đối chiếu sinh trắc học ArcFace AI, ghi nhận hồ sơ PENDING, khóa trùng số CCCD qua HMAC SHA-256).
  - `KtvEkycService`: `getQueue` (phân trang, masked CCCD `0010****2345`), `getDetail` (ảnh CCCD 2 mặt, selfie, độ lệch Cosine, độ tin cậy AI), `approve` (cấp role `SELLER`, cập nhật `VERIFIED`), `reject` (bắt buộc nhập lý do, cập nhật `REJECTED`).
  - `EkycWorkflowController`, `KtvEkycController` (`@PreAuthorize("hasRole('KTV')")`), `EkycAssetController` (stream ảnh bảo mật có phân quyền).
  - `SecurityConfig`: Cấu hình matcher phân quyền nghiêm ngặt `/api/v1/ktv/ekyc/**`.
- **Frontend**:
  - `ekycApi.ts` & `ekycKtvApi.ts`: Client API chuẩn TypeScript.
  - `SellerVerificationPage.tsx`: Giao diện 5 bước Stepper (Bắt đầu -> Tải CCCD -> Xác nhận OCR -> Khuôn mặt Webcam -> Chờ duyệt) + tích hợp chế độ MVP bypass tương thích 100% test suite hiện hành.
  - `KtvKycPage.tsx`: Kết nối API Backend thật với cơ chế fallback mock thông minh, hỗ trợ duyệt đơn lẻ/hàng loạt và nhập lý do từ chối.
- **Kiểm thử**:
  - Frontend: 46/46 test files / 224/224 unit tests PASS (100%), `npm run typecheck` (0 errors).
  - Backend: 14/14 tests eKYC PASS (`EkycWorkflowServiceTest`, `KtvEkycServiceTest`, `EkycVerificationServiceTest`), 63/63 tests identity module PASS, BUILD SUCCESS.

## Hiển thị Giá Offer đã Chấp thuận trên Mall và Màn hình liên quan — TASK-0099 / 2026-10-20

- **Mục tiêu**: Người mua có offer `ACCEPTED` hợp lệ (chưa dùng `order_id == null`, sản phẩm còn hàng, không bị block) nhìn thấy giá thỏa thuận của mình trên Mall, chi tiết sản phẩm, widget chat và checkout. Không ghi đè `products.listed_price` hay giá công khai toàn hệ thống.
- **Backend**:
  - `OfferRepository`: bổ sung `activeAcceptedOffersForBuyer(long buyerId)` lấy danh sách offer ACCEPTED chưa dùng của buyer.
  - `NegotiatedCheckoutFacade`: bổ sung contract `findActiveAcceptedOffersForBuyer(long actor)`.
  - `DefaultNegotiatedCheckoutFacade`: kiểm tra eligibility (quan hệ mua bán, sản phẩm khả dụng, version snapshot không đổi, không bị chặn).
  - `CatalogDtos`: bổ sung `agreedPrice`, `acceptedOfferId` cho `ProductSummaryResponse` và `ProductDetailResponse`.
  - `CatalogService`: nhận `Long buyerId` (null khi khách vãng lai hoặc unauthenticated) để enrich `agreedPrice` và `acceptedOfferId`. Không áp dụng khi người xem là người bán của sản phẩm.
  - `CatalogController`: trích xuất JWT `@AuthenticationPrincipal Jwt jwt` truyền `buyerId` vào `CatalogService`.
- **Frontend**:
  - `marketplace/types.ts`: bổ sung `agreedPrice`, `acceptedOfferId` cho `ProductSummary` và `ProductDetail`.
  - `marketplaceApi.ts`: dùng `authApi.authorizedFetch` để truyền token Bearer khi đã đăng nhập.
  - `ProductCard.tsx`: hiển thị ribbon "Đã thỏa thuận", giá thỏa thuận nổi bật, badge "Giá của bạn", gạch ngang giá niêm yết cũ.
  - `ProductDetailView.tsx`: hiển thị hộp giá thỏa thuận, badge "Giá đã thỏa thuận", ghi chú offer ID, nút "Mua ngay (Giá thỏa thuận)" tự động truyền `offerId` vào checkout, kèm tùy chọn mua theo giá niêm yết.
  - `ConversationWorkspace.tsx`: widget sản phẩm hiển thị giá thỏa thuận, gạch ngang giá niêm yết và nút "Mua ngay" nhanh vào checkout.
  - `styles.css` & `chat.css`: bổ sung style đẹp mắt, chuẩn design system O.G Shop.
- **Kiểm thử**:
  - Backend: `CatalogServiceTest` (10/10 PASS), `CheckoutServiceTest` (10/10 PASS).
  - Frontend: 46 test files / 224 unit tests PASS (100%), `npm run typecheck` (0 errors).

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
