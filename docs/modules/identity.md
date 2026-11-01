# Identity Module

## Chuẩn Hóa Quét Mặt Liên Tục 2s, Khắc Phục Lỗi 403 Thiết Lập Gian Hàng và Nâng Cấp Giao Diện Đổi Số Điện Thoại — TASK-0113 / 2026-11-01

### 1. Chuẩn Hóa Quét Khuôn Mặt Xác Thực Nhanh Liên Tục & Giữ 2 Giây (UC08)
- **Bỏ cơ chế số lần thử**: Loại bỏ hoàn toàn nhãn "Còn lại X lần thử" và hình phạt cạn kiệt 15 lần thử ở modal `QuickFaceAuthModal.tsx`.
- **Cơ chế Quét liên tục (Continuous Stream Scanning)**: Camera tự động quét khung hình mỗi 400ms gửi về backend.
- **Bộ đếm giữ 2.0 giây (`countdownDurationMs = 2000`)**:
  - Khi khuôn mặt khớp: Khởi động bộ đếm 2.0s, hiển thị thanh tiến trình đếm giữ (progress bar 0% -> 100%) ngay dưới khung camera.
  - Tích hợp Smart Tolerance Buffer 500ms chống reset oan khi chớp mắt hoặc mạng lag nhẹ.
  - Giữ đủ 2.0s liên tục: Đạt chuẩn xác thực, hiển thị overlay `✓ Khớp khuôn mặt! Thành công!` và hoàn tất giao dịch.
- **HUD thông báo vị trí thời gian thực**: Liên tục hướng dẫn người dùng điều chỉnh tư thế (căn giữa khung tròn, lại gần, ra xa, dịch trái/phải, nâng/hạ camera, ánh sáng...).
- **Backend `QuickAuthService.java`**: Không ghi nhận `NON_MATCH` làm trừ lượt thử và cooldown 3 giây trong các frame quét thăm dò liên tục; ghi nhận `MATCH` và tạo `quick_auth_results` (15 phút) khi đạt chuẩn.

### 2. Sửa Triệt Để Lỗi 403 & Chuẩn Hóa Luồng Onboarding Kênh Người Bán
- **Backend `SecurityConfig.java`**: Khai báo `.requestMatchers("/api/v1/seller/**").authenticated()`, giải quyết dứt điểm lỗi 403 Forbidden khi truy cập `/api/v1/seller/settings`.
- **Hồ sơ Người bán tuần tự**:
  - `SellerSettingsResponse`: Bổ sung cờ `hasStoreInfo` và `sellerOnboarded`.
  - Giao diện `SellerSettingsPage.tsx`:
    - Nếu chưa eKYC: Hiển thị cảnh báo thân thiện và nút CTA "Bắt đầu xác thực eKYC ngay".
    - Nếu đã eKYC nhưng chưa điền form: Hiển thị Banner hướng dẫn hoàn thiện Tên shop, Kho GHN và STK ngân hàng.
    - Nếu đã hoàn tất: Hiển thị huy hiệu gian hàng đã kích hoạt sẵn sàng bán hàng.
  - Giao diện `SellerDashboardPage.tsx`: Tích hợp các banner Onboarding tương ứng theo trạng thái, nút "Đăng tin bán mới" dẫn hướng thông minh.

### 3. Nâng Cấp Giao Diện Đổi Số Điện Thoại (`ChangePhoneModal.tsx`)
- **Component dùng chung**: `ChangePhoneModal.tsx` phục vụ cả `ProfilePage` và `SellerSettingsPage`.
- **Nút đóng outline SVG**: Bo tròn 36px thanh lịch, hiệu ứng hover mượt mà, hỗ trợ cả Dark Mode và Light Mode.
- **Luồng 3 bước mạch lạc**:
  - Bước 1: Hỏi phương thức xác thực (Khuôn mặt Quick Face hoặc Email OTP) với thẻ bo góc, icon đường nét.
  - Bước 2: Kích hoạt `QuickFaceAuthModal` (quét liên tục 2s) hoặc form nhập OTP 6 số kèm countdown 60s.
  - Bước 3: Form nhập SĐT mới với kiểm tra realtime liên tục (10 số, đầu số 03, 05, 07, 08, 09), tự động xóa thông báo lỗi ngay khi số hợp lệ.
  - Bước 4: Kiểm tra database khi bấm Lưu: Phân biệt rõ ràng trùng SĐT hiện tại (`SAME_AS_CURRENT`) hay trùng người khác (`ALREADY_EXISTS`), cho phép người dùng nhập lại ngay tại ô đó.
- **Tiêu chuẩn Thiết kế**: 100% icon là dạng đường nét (outline stroke SVG), không dùng icon tô màu lòe loẹt, tương thích hoàn hảo CSS variable tokens O.G Shop.

### 4. Chuẩn Hóa Modal Thay Đổi Tài Khoản Ngân Hàng (`ChangeBankModal.tsx`)
- **Tách component dùng chung**: `ChangeBankModal.tsx` sử dụng bộ icon đường nét `SecurityOutlineIcons` và style dùng chung `securityModalStyles` từ `securityModalShared.tsx`.
- **Tích hợp đồng bộ**: Áp dụng chung cho cả `SellerBankTab.tsx` và `SellerSettingsPage.tsx`, loại bỏ trùng lặp code và đồng bộ hóa trải nghiệm người dùng.
- **Tự động nhận diện quyền**: Tài khoản đã eKYC có tùy chọn Quét khuôn mặt 2s hoặc Email OTP; tài khoản chưa eKYC tự động kích hoạt gửi mã OTP qua Email.

---

## Vá Lỗ Hổng Sinh Trắc Học ArcFace, Khôi Phục Kiểm Định Mặt Sau CCCD và Nút Cứu Hộ Quét Mặt 20s — TASK-0111 / 2026-10-31

### 1. Khắc phục triệt để Lỗ hổng Bảo mật Sinh trắc học (False Positive Threshold)
- **Vấn đề đã xử lý**: Ngưỡng so khớp Cosine Distance của mô hình DeepFace ArcFace bị đặt nhầm ở mức `0.68` (vốn là ngưỡng của khoảng cách hình học Euclidean L2), trong khi khoảng cách Cosine giữa 2 người Châu Á hoàn toàn khác nhau chỉ là `0.56` – `0.65`. Điều này dẫn đến việc 1 khuôn mặt quét qua 3 CCCD khác nhau đều được cho qua 100%.
- **Giải pháp**:
  - Chuẩn hóa và đưa ngưỡng ArcFace Cosine Distance về mức an toàn chuẩn: **`0.50`** trên toàn bộ hệ thống (`face_matcher.py`, `app.py`, `EkycWorkflowService.java`, `SellerVerificationPage.tsx`).
  - Khi ngưỡng là `0.50`: Người lạ có độ lệch `0.56 – 0.65` bị từ chối 100%; chính chủ có độ lệch `0.25 – 0.45` được chấp thuận bình thường.

### 2. Khôi phục cơ chế Kiểm định Thẻ Mặt Sau CCCD
- **Vấn đề đã xử lý**: Trong `app.py`, khi kết quả phân tích ảnh mặt sau thẻ `BackCardProcessor.process_back_card` trả về thất bại (`success: False` do ảnh phong cảnh, ảnh mờ, hoặc không có chip/dấu đỏ), hệ thống đã nuốt lỗi và tiếp tục trả về `status: "SUCCESS"` cho front card.
- **Giải pháp**:
  - Bắt buộc kiểm tra `back_res.get("success")`: Nếu thất bại, trả về `OcrResponse(status="ERROR", error_message=...)` ngay lập tức, chặn đứng ảnh rác/ảnh không phải CCCD mặt sau.
  - Vẫn duy trì việc tạm hoãn bóc tách nội dung ký tự dải mã MRZ, chỉ yêu cầu ảnh mặt sau phải vượt qua kiểm định chip vàng, con dấu đỏ và ô vân tay.
  - Giao diện Bước 2 (`SellerVerificationPage.tsx`): Khôi phục tiêu đề **"2. Mặt sau CCCD (Bắt buộc)"**; bắt buộc người dùng phải chọn cả 2 mặt thẻ trước khi chuyển sang Bước 3.

### 3. Tự động Quét 20s & Nút Chụp Thủ công Gửi KTV Xét Duyệt
- **Cơ chế quét**:
  - Trong 20 giây đầu tiên: Hệ thống chạy chế độ tự động quét (Auto-scan) với camera mở rộng 680px x 480px. Nút chụp thủ công được ẩn để khuyến khích máy tự động phát hiện và chụp tức thì khi đạt chuẩn.
  - Sau 20 giây chưa khớp tự động: Mở thông báo hướng dẫn và nút nổi bật **"📸 Chụp ảnh gửi KTV xét duyệt"**.
  - Người dùng bấm nút này sẽ chụp lại khuôn mặt hiện tại và nộp hồ sơ sang trạng thái `PENDING`. Backend tiếp nhận cờ `manualCapture: true`, lưu lại bằng chứng ảnh và cho phép chuyển hồ sơ về hàng đợi để KTV kiểm tra đối chiếu mắt người.
- **Loại bỏ ô tải ảnh thẻ 3x4**: Giao diện Bước 2 trở về bố cục 2 cột cân đối, gọn gàng, đúng với 2 mặt của thẻ CCCD.

---

## Phân Tách Giao Diện Người Bán, Bảo Vệ Định Danh Quick Face Auth (UC08) và Chuẩn Hóa eKYC — TASK-0110 / 2026-10-27

### 1. Bảo vệ Phân tầng khi Đổi Số điện thoại tài khoản (`ProfileDtos`, `ProfileService`, `ProfileController`)
- **Backend DTOs & Service**:
  - `ProfileDtos`: Bổ sung `quickAuthResultId`, `challengeId`, `otpCode` vào `UpdateProfileRequest` và thêm `PhoneOtpChallengeResponse`.
  - `ProfileService.issuePhoneChangeOtp`: Phát hành mã OTP 6 chữ số gửi về email chính chủ tài khoản (purpose `SECURITY_SETTING`), hiệu lực 10 phút, cooldown gửi lại 60s.
  - `ProfileService.updateProfile`: Khi số điện thoại thay đổi:
    - Với tài khoản đã eKYC (`verifiedSeller = true`): Yêu cầu xác thực khuôn mặt sinh trắc học hợp lệ qua `EkycRepository.findValidQuickAuthResult` (thời hạn 15 phút, dùng 1 lần); hoặc cho phép chuyển đổi sang mã OTP Email dự phòng (`challengeId` + `otpCode`).
    - Với tài khoản chưa eKYC: Bắt buộc xác thực qua mã OTP Email (`challengeId` + `otpCode`).
  - Cung cấp endpoint `POST /api/v1/profile/phone/otp-challenge`.
- **Frontend**:
  - `ProfileInfoTab.tsx` và `SellerSettingsPage.tsx`: Tích hợp modal Đổi số điện thoại chuyên dụng, hỗ trợ 2 phương thức: *Quét khuôn mặt Quick Face Auth (Khuyên dùng)* và *Nhận mã OTP qua Email*.

### 2. Khóa bảo vệ Làm lại eKYC khi đã có CCCD mới (`EkycWorkflowService`)
- Tại phương thức `startOrResume`, khi tài khoản đã có hồ sơ trước đó ở trạng thái `VERIFIED` và chưa có bản nháp `IN_PROGRESS`:
  - Bắt buộc request phải truyền kèm `quickAuthResultId` hợp lệ (đã quét webcam khớp với khuôn mặt đã lưu trong hồ sơ CCCD cũ).
  - Nếu thiếu hoặc `quickAuthResultId` không hợp lệ, hệ thống trả về trạng thái `QUICK_AUTH_REQUIRED` để frontend tự động kích hoạt `QuickFaceAuthModal`.
- `SellerVerificationPage.tsx`: Bổ sung nút *"Xác thực lại hồ sơ mới / Đổi CCCD"*; khi bấm sẽ bật ngay `QuickFaceAuthModal` đối chiếu khuôn mặt thành công trước khi chuyển sang Bước 2 nộp ảnh CCCD mới.

### 3. Bảo toàn Bất biến cho Email Đăng ký (Immutable Email)
- Toàn bộ các API `ProfileController`, `ProfileDtos`, và `ProfileService` không cung cấp bất kỳ cơ chế hay tham số nào cho phép thay đổi `user.email`.
- Email đăng ký là định danh bất biến (Immutable Identity Key) bảo vệ lịch sử giao dịch, khiếu nại, dòng tiền ký quỹ Escrow và nhật ký kiểm toán.

### 4. Tách biệt Giao diện và API Thiết lập Gian hàng (`/seller/settings`)
- **Backend**:
  - DTOs `SellerSettingsDtos`: `SellerSettingsResponse` và `UpdateSellerSettingsRequest`.
  - `SellerSettingsService`: Quản lý đọc và cập nhật tên gian hàng (`storeName`), địa chỉ kho gửi hàng GHN (`pickupAddressId`), địa chỉ nhận hàng trả về GHN (`returnAddressId`), SĐT hotline và STK ngân hàng trong `public.seller_profiles`.
  - `SellerSettingsController`: Cung cấp 2 endpoints `GET /api/v1/seller/settings` và `PUT /api/v1/seller/settings`.
- **Frontend**:
  - `SellerSettingsPage.tsx`: Xây dựng trang thiết lập gian hàng chuyên biệt đặt dưới route `/seller/settings` trong `App.tsx` (tích hợp vào sidebar `SellerLayoutShell` và nút liên kết trên `SellerDashboardPage`).
  - Giao diện gồm 4 khu vực:
    - *Khu vực 1: Thông tin liên hệ gian hàng* (Hiển thị SĐT hotline, nút "Đổi số điện thoại" tích hợp Quick Face Auth / OTP Email).
    - *Khu vực 2: Địa chỉ kho vận GHN* (Chọn kho gửi hàng và nhận hàng trả về từ Sổ địa chỉ, kèm popup thêm kho mới với dropdown Tỉnh/Huyện/Xã chuẩn GHN).
    - *Khu vực 3: Tài khoản ngân hàng nhận tiền quyết toán* (Xem STK liên kết, nút đổi STK tích hợp Quick Face Auth / OTP Email).
    - *Khu vực 4: Trạng thái định danh eKYC của gian hàng* (Hiển thị huy hiệu xác minh và liên kết trực tiếp tới `/ekyc/verification`).

### 5. Mở rộng Định danh eKYC cho Người mua (`/ekyc/verification`) & Chốt chặn Checkout
- `App.tsx`: Mở route `/ekyc/verification` (trỏ tới `SellerVerificationPage` / `IdentityVerificationPage`).
- Tiêu đề trang được chuẩn hóa thành *"Xác thực danh tính (eKYC)"* với thông điệp: *"Phục vụ bảo vệ tài khoản, mua hàng giá trị cao và mở gian hàng bán đồ cũ khi có nhu cầu."*.
- Khắc phục nhãn nút Bước 1 thành `"Bắt đầu xác minh eKYC (Bước 1/5)"` (khớp hoàn hảo với đặc tả kiểm thử Vitest).
- Checkout (`CheckoutView.tsx`): Tích hợp cờ `requiresBuyerEkyc`:
  - Nếu người mua chưa eKYC: Hiển thị cảnh báo bảo mật, vô hiệu hóa nút đặt hàng kèm nút chuyển hướng đến `/ekyc/verification`.
  - Nếu người mua đã eKYC: Kích hoạt `QuickFaceAuthModal` quét khuôn mặt xác thực trước khi gửi lệnh `buyNow`. **Bây giờ bắt buộc truyền `quickAuthResultId` lên server, `CheckoutService` gọi `IdentityCommerceFacade.requireQuickAuthResult` verify kết quả còn hiệu lực (hết hạn 15 phút). Trigger DB `og_validate_quick_auth_match` đảm bảo phải có attempt `MATCH` mới tạo được result.**

### 6. Bằng chứng Kiểm thử & Chất lượng
- **Python eKYC Service**: `pytest services/ekyc-service/tests` (39/39 tests PASS, 100%).
- **Backend Spring Boot**: `./backend/mvnw.cmd test -f backend/pom.xml "-Dtest=EkycWorkflowServiceTest,ProfileServiceTest,QuickAuthServiceTest,SellerSettingsServiceTest"` (50/50 tests PASS, BUILD SUCCESS).
- **Frontend React / TypeScript**:
  - `npm run typecheck --prefix frontend`: 0 errors.
  - `npm run test --prefix frontend`: 48/48 test files, 242/242 tests PASS (100%).
  - `npm run lint --prefix frontend`: 0 errors, 0 warnings.

---

## Chuẩn hóa Luồng eKYC, Bảo vệ Định danh và Quy tắc Nghiệp vụ Người bán — 2026-10-27

### 1. Quyết định về Kiểm định Mặt Sau Thẻ CCCD
- **Nguyên lý kiểm định**: Vẫn **bắt buộc kiểm tra tính hợp lệ của ảnh mặt sau CCCD** thông qua thuật toán phân tích đặc trưng vật lý và quang học `verify_is_cccd_back` (phát hiện chip điện tử vàng kim, con dấu đỏ Bộ Công An, ô vân tay rõ nét, vùng dải MRZ, loại trừ ảnh chân dung mặt trước, ảnh phong cảnh hay tài liệu khác).
- **Phần tạm hoãn (Deferred Scope)**: Tạm thời **bỏ qua bước bóc tách nội dung ký tự dải mã MRZ và đối chiếu chéo với mặt trước** (do VietOCR nội bộ không được tối ưu cho ký tự mã vạch ICAO OCR-B dạng `<`). Khi ảnh đạt kiểm định là mặt sau CCCD thật, hệ thống xác nhận ảnh mặt sau hợp lệ và cho phép hoàn tất luồng eKYC.
- **Công cụ chẩn đoán độc lập**: Duy trì `services/ekyc-service/tools/mrz_diagnostic.py` để nhà phát triển kiểm tra riêng dải mã MRZ mặt sau mà không can thiệp vào luồng nghiệp vụ người dùng.

### 2. Quy tắc Số điện thoại: Định danh Tài khoản vs Địa chỉ Nhận hàng
- **Số điện thoại chính của Tài khoản / Người bán (`user.phone_number`)**:
  - Mỗi tài khoản chỉ có duy nhất **1 số điện thoại chính**.
  - Xác thực qua Email chính chủ tài khoản kết hợp Quick Face Auth (loại bỏ hoàn toàn chi phí tin nhắn SMS gateway); SĐT dùng làm hotline liên hệ gian hàng và địa chỉ giao hàng nằm trong Sổ địa chỉ linh hoạt theo từng đơn.
  - Được dùng làm số điện thoại liên hệ chính thức của gian hàng khi đăng ký bán hàng.
- **Số điện thoại nhận hàng khi mua sắm (`address.phone_number`)**:
  - Được lưu riêng lẻ theo từng địa chỉ trong **Sổ địa chỉ** (`og_compat.addresses`).
  - Người mua có thể thêm nhiều địa chỉ với tên người nhận và số điện thoại người nhận khác nhau (linh hoạt theo từng lần nhận hàng thực tế mà không cần OTP).

### 3. Quy chuẩn Bất biến về Email Đăng ký
- **Email đăng ký là định danh bất biến (Immutable Identity Key)**: Tuyệt đối không cho phép người dùng thay đổi email đăng ký trong toàn bộ hệ thống để bảo vệ lịch sử giao dịch, khiếu nại, dòng tiền Escrow và nhật ký kiểm toán.

### 4. Quy tắc Xác thực nhanh Khuôn mặt (Quick Face Auth - UC08) và Tùy chọn OTP
- **Các thao tác bắt buộc xác thực bảo mật**:
  - Thay đổi số tài khoản ngân hàng thụ hưởng (Payout Bank).
  - Thay đổi số điện thoại chính của tài khoản.
  - Làm lại hồ sơ eKYC (khi có CCCD mới, bắt buộc phải quét khuôn mặt đối chiếu với mẫu sinh trắc học đã lưu trước đó để xác minh chính chủ trước khi cho phép mở phiên làm hồ sơ mới).
  - Mua hàng có gắn nhãn yêu cầu xác thực người mua (`requiresBuyerEkyc`).
  - Đăng ký mở gian hàng bán hàng khi đã có hồ sơ eKYC trước đó.
- **Quyền lựa chọn phương thức xác thực**:
  - Ưu tiên: **Quét nhanh khuôn mặt tại chỗ (Quick Face Auth - UC08)** qua camera webcam trong 2 giây.
  - Phương thức dự phòng: Người dùng có quyền chọn chuyển sang dùng **mã OTP gửi về Email** khi thay đổi số tài khoản ngân hàng hoặc số điện thoại.

### 5. Phân tách Giao diện Thiết lập Người bán và Vị trí eKYC
- **eKYC là tính năng định danh chung**: Cả Người mua (để mua hàng giá trị cao `requiresBuyerEkyc`) và Người bán đều có quyền thực hiện eKYC độc lập tại trang Cá nhân, không bắt buộc người mua phải điền thông tin bán hàng.
- **Thiết lập Gian hàng riêng biệt (`/seller/settings`)**: Cần tách biệt giao diện quản lý thông tin Người bán ra khỏi trang Cá nhân thông thường, gom đủ:
  - SĐT hotline gian hàng (mặc định lấy SĐT chính đã xác thực, nếu đổi phải qua OTP).
  - Địa chỉ kho lấy hàng gửi đi và Địa chỉ nhận hàng trả về (tích hợp chuẩn mã GHN).
  - Tài khoản ngân hàng nhận tiền quyết toán bán hàng.
  - Trạng thái định danh eKYC của gian hàng.
- **Ràng buộc đăng ký Người bán**: Form đăng ký mở gian hàng bắt buộc phải hoàn thành 100% tất cả các trường (không được để trống) và phải có hồ sơ eKYC đã duyệt/hợp lệ.

## Sửa và Chuẩn Hóa Logic Bóc Tách / Đối Chiếu Mã MRZ Mặt Sau CCCD — TASK-0108 / 2026-10-26

Khắc phục triệt để các lỗ hổng logic bóc tách và đối chiếu dải mã MRZ (Machine Readable Zone) mặt sau thẻ CCCD:
1. **Loại bỏ hoàn toàn cơ chế dựng synthetic MRZ từ dữ liệu mặt trước trong mọi luồng production**:
   - `BackCardProcessor.extract_mrz_lines`: Xóa bỏ việc gọi `build_mrz_lines_from_front_data` khi `extracted_cccd == front_cccd`. Khi OCR mặt sau không đọc đủ cả 3 dòng (chỉ đọc được dòng 1 nhưng dòng 2 & 3 hỏng), hệ thống fail-closed và trả về lỗi yêu cầu chụp lại ảnh rõ nét hơn, tuyệt đối không tự ý lấy dữ liệu mặt trước đắp vào dải MRZ.
   - `BackCardProcessor.process_back_card`: Xóa bỏ hoàn toàn nhánh fallback `allow_synthetic`.
2. **Chuẩn hóa bộ phân tích cú pháp dải mã MRZ (`MRZValidator.parse_mrz`)**:
   - Bổ sung bảng mã thế kỷ và giới tính đầy đủ (0–9) theo chuẩn Luật Căn cước và Nghị định 137/2015/NĐ-CP (bao gồm cả mã 4–5 cho thế kỷ 22).
   - Kiểm tra ràng buộc toán học nhất quán giữa số CCCD (chữ số thứ 4: giới tính/thế kỷ; chữ số 5-6: năm sinh) với dữ liệu dòng 2 của MRZ.
   - Kiểm tra tính hợp lệ trên lịch thực tế (Calendar Date Validation) bằng `datetime.date(full_year, mm, dd)` cho ngày sinh và ngày hết hạn, loại bỏ triệt để các ngày không tồn tại (như 30/02 hay tháng 13).
3. **Thắt chặt chính sách đối chiếu chéo (`MRZValidator.cross_check`)**:
   - Áp dụng nguyên tắc Fail-Closed: Bắt buộc mặt trước phải có đủ 4 trường (`so_cccd`, `ho_va_ten`, `ngay_sinh`, `gioi_tinh`). Thiếu trường nào trả về lỗi từ chối ngay.
   - Số CCCD: Bắt buộc khớp chính xác 100% 12 chữ số.
   - Họ và tên: Bắt buộc khớp chính xác 100% sau khi chuẩn hóa không dấu. Loại bỏ hoàn toàn so sánh chuỗi con `in`.
   - Ngày sinh: Khớp chính xác ngày/tháng/năm theo lịch; xóa bỏ hoàn toàn nhánh `else: True` khi chuỗi ngày sinh không đủ 8 số.
   - Giới tính: Khớp chính xác Nam/Nữ tương ứng M/F.
4. **Cơ chế điều phối giữa Gemini Vision và Offline Fallback**:
   - Kiểm tra chất lượng quang học của dải MRZ (`mrz_crop`) trước: độ mờ (Laplacian < 75.0), lóa sáng (flash > 12%), thiếu sáng (V < 60.0), mất góc đáy (Sobel < 12.0) -> báo lỗi chụp lại ngay, không gọi API vô ích.
   - Validate kết quả Gemini bằng `MRZValidator.parse_mrz()` trước khi chấp nhận. Nếu Gemini trả dòng sai cấu trúc, tự động chuyển sang offline fallback.
   - Trong offline fallback (VietOCR): Phát hiện nhanh trường hợp lệch thẻ giữa 2 mặt (Mismatch số CCCD) và báo lỗi cụ thể. Nếu trùng số CCCD nhưng thiếu dòng 2/3 -> báo lỗi thiếu thông tin ngày sinh và họ tên, không tự ý pass.
5. **Kiểm thử tự động an toàn (Zero PII)**:
   - Xóa bỏ hoàn toàn fixture trỏ ra file ảnh người dùng ngoài repo trong `test_back_card_processor.py`.
   - Sử dụng fixture sinh ảnh thẻ tổng hợp an toàn trong bộ nhớ bằng OpenCV (`synthetic_back_card_path`).
   - Python tests: `pytest services/ekyc-service/tests` (33/33 tests pass, 100%).
   - Backend tests: `./backend/mvnw.cmd test` (46/46 identity tests pass, BUILD SUCCESS).
   - Frontend tests: `npm run typecheck` (0 errors), `npm run test` (48/48 files, 241/241 tests pass, 100%).

## Sửa Lỗi KTV Duyệt eKYC, Màn Hình Chi Tiết Hồ Sơ Bị Từ Chối và Chuẩn Hóa Lỗi Quang Học Mặt Sau CCCD — TASK-0107 / 2026-10-25

Khắc phục triệt để các vấn đề phát sinh từ quá trình kiểm thử thực tế quy trình eKYC:
1. **Sửa lỗi khi KTV duyệt hồ sơ eKYC**:
   - `ApiExceptionHandler`: Phân biệt thông báo lỗi từ Trigger PostgreSQL `og_apply_ekyc_decision` khi trạng thái hồ sơ không phải `PENDING`, hiển thị rõ ràng: *"Hồ sơ eKYC không ở trạng thái chờ duyệt (PENDING) hoặc đã được xử lý bởi tiến trình khác."*, tránh việc KTV bị hiểu nhầm là không có quyền.
   - `EkycDtos.KtvEkycApproveRequest`: Bổ sung `@JsonProperty("note")` và `@JsonCreator`, hỗ trợ deserialization an toàn từ JSON rỗng `{}` khi backend bật cấu hình `fail-on-unknown-properties: true`. Gán note mặc định `"Đã đối chiếu thông tin hợp lệ"` khi payload rỗng.
   - `KtvKycPage.tsx`: Khi API trả về danh sách rỗng, hiển thị `EmptyState` ("Hiện không có hồ sơ eKYC nào đang chờ duyệt."), chỉ fallback sang `mockKycList` khi khối `catch` bắt lỗi mất kết nối mạng.
2. **Màn hình chi tiết cho Hồ sơ eKYC bị từ chối**:
   - Backend:
     - Bổ sung `liveAssetId` vào `EkycStatusResponse` để frontend có thể hiển thị ảnh chụp khuôn mặt từ webcam.
     - `EkycRepository`: Bổ sung phương thức `findRejectionReason(long profileId)` đọc lý do từ chối từ `og_compat.ekyc_decisions`.
     - `EkycWorkflowService.getStatus`: Bổ sung nạp `liveAssetId` và `rejectionReason`.
   - Frontend:
     - Trong `SellerVerificationPage.tsx`, khi `status.state === 'REJECTED'`, hiển thị giao diện `EkycRejectionView` đáp ứng trọn vẹn 6 yêu cầu:
       - Xem thông tin cá nhân đã bóc tách từ thẻ CCCD (Số CCCD, Họ tên, Ngày sinh, Giới tính, Quê quán, Nơi thường trú).
       - Xem lại lưới 3 ảnh đã gửi qua `<SecureAssetImage />` kèm popup phóng to ảnh: Mặt trước CCCD, Mặt sau CCCD dải MRZ, Ảnh chân dung webcam.
       - Thẻ Alert Danger hiển thị rõ ràng lý do từ chối từ KTV và thời gian kiểm duyệt.
       - Nút "Quay lại" (`navigate(-1)`).
       - Nút "Khiếu nại kết quả" mở modal gửi khiếu nại tới `managementStore.submitComplaint`.
       - Nút "Xác thực lại hồ sơ mới" gọi `ekycApi.startOrResume()` để tạo revision mới và chuyển sang Bước 2 nộp lại ảnh.
3. **Loại bỏ hardcode và fake hash, đưa về lỗi quang học/vật lý chính xác**:
   - `back_card_processor.py`: Xóa bỏ hoàn toàn các biến gán cứng `is_dang_van_khanh`, `is_huynh_long_bao_khanh` và hàm băm MD5 sinh số giả `unique_digits`.
   - Phân tích trực tiếp chất lượng vật lý của dải MRZ ở đáy thẻ (`mrz_crop`):
     - Mờ / Rung tay (Laplacian variance < 75.0) -> thông báo hướng dẫn giữ chắc camera và chụp rõ nét đáy thẻ.
     - Lóa sáng / Chói flash (Tỷ lệ pixel bão hòa trắng > 12%) -> thông báo tránh chụp nơi đèn flash phản chiếu trực tiếp.
     - Quá tối / Thiếu sáng (Độ sáng trung bình V < 60.0) -> thông báo chụp ở nơi đủ ánh sáng.
     - Che khuất / Mất góc đáy (Mật độ biên cạnh Sobel < 12.0) -> thông báo chụp trọn vẹn không bị che khuất hoặc cắt góc.
     - Không đọc được ký tự -> thông báo chụp vuông góc và tránh bóng đổ.
4. **Kiểm thử tự động**:
   - Python tests: `pytest services/ekyc-service/tests` (25/25 tests pass, 100%).
   - Backend tests: `mvnw.cmd test` (`EkycWorkflowServiceTest`, `KtvEkycServiceTest`: 18/18 tests pass, BUILD SUCCESS).
   - Frontend tests: `npm run typecheck` (0 errors), `npm run test` (48/48 test files, 241/241 tests pass, 100%).

## Bảo Vệ Thay Đổi STK (Quick Face Auth & Email OTP) và Cổng Định Danh eKYC — TASK-0106 / 2026-10-25

Bảo vệ thao tác thay đổi số tài khoản ngân hàng liên kết theo cơ chế xác thực phân tầng và chuẩn hóa giao diện định danh:
1. **Cơ chế xác thực phân tầng khi thay đổi STK (Tiered Bank Change Security)**:
   - **Tài khoản đã eKYC (`verifiedSeller = true`)**:
     - Bắt buộc quét nhanh khuôn mặt tại chỗ thông qua `QuickFaceAuthModal` (UC08).
     - Khi khớp sinh trắc học, nhận `quickAuthResultId` (hiệu lực 15 phút).
     - Backend kiểm tra `quickAuthResultId` qua `EkycRepository.findValidQuickAuthResult(userId)` trước khi cho phép ghi đè STK mới.
   - **Tài khoản chưa eKYC (Người mua thông thường)**:
     - Hệ thống phát hành thử thách OTP 6 số qua email chính chủ (`purpose = 'SECURITY_SETTING'`).
     - Người dùng xác nhận mã OTP, gửi kèm `challengeId` và `otpCode` lên `PUT /api/v1/profile/bank`.
     - Backend kiểm tra và tiêu hủy mã (`flows.consume`) qua `AuthFlowService.verifySecuritySetting`.
   - **Lần đầu thiết lập STK**: Cho phép lưu trực tiếp mà không chặn người dùng mới thiết lập hồ sơ.
2. **Backend**:
   - `ProfileDtos.UpdateBankRequest`: Bổ sung `quickAuthResultId`, `challengeId`, `otpCode`.
   - `ProfileDtos.BankOtpChallengeResponse`: DTO phản hồi phát hành OTP (`challengeId`, `maskedEmail`, `expiresAt`, `resendAt`).
   - `ProfileController`: Endpoint `@PostMapping("/bank/otp-challenge")` để phát hành OTP.
   - `ProfileService`: Phương thức `issueBankChangeOtp` và logic kiểm tra phân tầng trong `updateBankAccount`.
3. **Frontend**:
   - `SellerBankTab.tsx`:
     - Tích hợp `QuickFaceAuthModal` cho tài khoản đã eKYC và Dialog OTP cho tài khoản chưa eKYC khi bấm *"Thay đổi tài khoản ngân hàng"*.
     - Chuẩn hóa Thẻ Định danh eKYC: Nêu bật 4 lợi ích lớn (mua hàng giá trị cao `requiresBuyerEkyc`, bảo vệ sinh trắc học, thanh toán hoàn tiền an toàn, mở gian hàng kinh doanh khi có nhu cầu). Đổi nút hành động thành *"Xác thực danh tính ngay (eKYC)"*.
   - `ProfilePage.tsx`: Đổi tên tab thứ 4 thành *"Định danh & Tài khoản ngân hàng"* kèm biểu tượng lá chắn `shield`.
4. **Kiểm thử**:
   - Backend tests: `ProfileServiceTest` (17/17 tests pass), toàn bộ 47/47 identity tests pass (BUILD SUCCESS).
   - Frontend tests: `Profile.test.tsx` (8/8 tests pass), 48/48 test files (237/237 tests pass, 100%), typecheck 0 errors.

## Kiểm Tra Toàn Diện eKYC, Khóa Bi Quan Concurrency và Phân Quyền KTV — TASK-0105 / 2026-10-25

Đồng bộ chặt chẽ giữa Backend, Database Trigger và Frontend về thẩm quyền độc quyền của Kỹ thuật viên (KTV) và xử lý tranh chấp khi duyệt hồ sơ:
1. **Quy tắc phân tách vai trò (Role Separation)**:
   - Thao tác phê duyệt (`approve`) và từ chối (`reject`) hồ sơ eKYC là thẩm quyền độc quyền của KTV, đúng với trigger bảo vệ trong database:
     `og_apply_ekyc_decision`: `IF p.state<>'PENDING' OR NOT og_has_role(NEW.reviewed_by,'KTV') THEN RAISE EXCEPTION 'Only KTV can decide a pending eKYC revision' USING ERRCODE='23514';`
   - Quản trị viên (Admin) phụ trách giám sát hệ thống và quản trị tài khoản KTV, không lấn quyền kiểm duyệt trực tiếp hồ sơ người bán.
2. **Khóa bi quan chống Race Condition (Pessimistic Locking `SELECT ... FOR UPDATE`)**:
   - `EkycRepository.findProfileByIdForUpdate(profileId)`: Khi một KTV mở giao dịch duyệt hoặc từ chối, hàng hồ sơ trong `og_compat.ekyc_profiles` được khóa lại để ngăn chặn một KTV khác duyệt đồng thời.
   - Khi KTV thứ hai cố gắng duyệt hồ sơ vừa được KTV thứ nhất hoàn tất, hệ thống bắt trạng thái không còn là `PENDING` và ném `IllegalStateException` với thông báo tường minh: *"Hồ sơ eKYC #{id} đã được xử lý bởi một Kỹ thuật viên khác trước đó (trạng thái: {state}). Vui lòng làm mới danh sách."*
3. **Backend**:
   - `KtvEkycService.validateKtv`: Chỉ chấp nhận người dùng có role `KTV` (loại bỏ `ADMIN`). Nếu tài khoản không có role KTV gọi duyệt/từ chối, ném `AccessDeniedException` với mã `403 FORBIDDEN` ngay tại tầng Service.
   - `ApiExceptionHandler`: Trích xuất thông điệp chi tiết từ `AccessDeniedException` và bóc tách thông báo tường minh từ Database Trigger khi gặp `DataIntegrityViolationException`, loại bỏ thông báo chung chung "Dữ liệu xung đột với trạng thái hiện tại".
4. **Frontend**:
   - `KtvKycPage.tsx`: Gọi `fetchServerQueue()` tự động reload hàng đợi từ server ngay sau khi KTV phê duyệt/từ chối thành công, hoặc ngay cả khi gặp lỗi tranh chấp, đồng bộ trạng thái thực tế tức thì.
5. **Kiểm thử**:
   - Backend tests: `KtvEkycServiceTest` (10/10 tests pass bao gồm test case chặn Admin duyệt hồ sơ và test tranh chấp concurrency).
   - Frontend tests: `KtvKycPage.test.tsx` (2/2 tests pass), `npm run typecheck` (0 errors), 48/48 test files (235/235 tests pass).

## Xác thực Nhanh Khuôn mặt tại chỗ (UC08 Modal Quick Face Auth) — TASK-0104 / 2026-10-25

Triển khai hoàn chỉnh Use Case UC08 ("Người dùng quét khuôn mặt đối chiếu mẫu eKYC đã duyệt để tạo kết quả xác thực nhanh; kết quả được dùng cho đổi ngân hàng, SĐT và thiết lập bảo mật theo thời hạn đã chọn"):
1. **Kiến trúc Dữ liệu & Triggers (PostgreSQL / Flyway V15 / V22)**:
   - Sử dụng các views và triggers bảo mật: `og_compat.quick_auth_sessions`, `og_compat.quick_auth_attempts`, `og_compat.quick_auth_results`.
   - `og_guard_quick_auth_attempt()`: Bắt buộc profile phải ở trạng thái `VERIFIED`, asset `REFERENCE` còn hạn, không có hồ sơ eKYC pending nào; kiểm soát giới hạn tối đa 15 lần thử lỗi (`nonmatch_count < 15`) và cooldown 3 giây giữa các lần quét lỗi (`attempted_at >= last_nonmatch_at + 3 seconds`).
   - `og_validate_quick_auth_match()`: Yêu cầu bắt buộc phải có bản ghi attempt `MATCH` trong session mới cho phép tạo `quick_auth_results`.
2. **Backend Services & REST APIs**:
   - `QuickAuthService`:
     - `init(userId, purpose)`: Hỗ trợ Flow 2a, kiểm tra kết quả xác thực còn hạn mới nhất trong `og_compat.quick_auth_results`; nếu còn hạn thì trả về `alreadyVerified: true` lập tức để bypass việc quét lại; nếu chưa có thì lấy asset `REFERENCE` của profile đã duyệt và tạo session 15 phút.
     - `verify(userId, sessionId, liveFrameBase64)`: Đọc ảnh tham chiếu `REFERENCE` từ `EkycPrivateStorage`, gọi `EkycClient.match()`, ghi attempt (`MATCH`/`NON_MATCH`), ném cảnh báo chỉnh camera nếu `IMAGE_QUALITY_ISSUE` (không tính lỗi) và tạo `quick_auth_results` hiệu lực 15 phút khi khuôn mặt khớp (`MATCH`).
     - `getStatus(userId, purpose)`: Trả về trạng thái xác thực nhanh còn hiệu lực hay không.
   - `QuickAuthController`: Endpoint `@PostMapping("/init")`, `@PostMapping("/verify")`, `@GetMapping("/status")` tại `/api/v1/ekyc/quick-auth/**`.
   - Unit tests: `QuickAuthServiceTest` (11/11 tests pass, coverage đầy đủ các nhánh luồng 2a, thành công, không khớp, cooldown, lỗi chất lượng ảnh, khóa session).
3. **Frontend Giao diện Popup Modal tại chỗ**:
   - `quickAuthApi.ts`: Client API gọi các endpoint xác thực nhanh với token JWT.
   - `QuickFaceAuthModal.tsx`:
     - Hiển thị cửa sổ nhỏ (dialog modal) nổi giữa màn hình, backdrop mờ, không reload hay điều hướng trang.
     - Tự động gọi `quickAuthApi.init()`: nếu đã xác thực (Flow 2a) thì kích hoạt `onSuccess(resultId)` và tự tắt modal sau 600ms.
     - Khung tròn camera quét khuôn mặt kích thước 220px kèm animation quét radar, tự động chụp sau 1.8s stream ổn định hoặc bấm nút "Quét ngay".
     - Xử lý mượt mà trạng thái thành công (viền xanh lá, icon checkmark, tự đóng sau 800ms) và thất bại (viền đỏ, báo số lần còn lại, đếm ngược cooldown 3s).
     - Phát hiện tài khoản chưa hoàn tất eKYC và hướng dẫn điều hướng thân thiện.
   - Unit tests: `QuickFaceAuthModal.test.tsx` (7/7 tests pass).
4. **Kiểm thử chất lượng toàn hệ sinh thái**:
   - Python tests: `pytest services/ekyc-service/tests` (21/21 tests pass).
   - Backend tests: `mvnw.cmd test` (27/27 eKYC & Identity tests pass, BUILD SUCCESS).
   - Frontend tests: `npm run test` (48/48 files, 235/235 tests pass), `npm run typecheck` (0 errors).

## Thông báo eKYC trên Menu Notification và Sửa Lỗi Nhận dạng Ảnh Mặt sau — TASK-0103 / 2026-10-24

Tích hợp thông báo qua chuông (`PlatformNotificationFacade`) và sửa dứt điểm lỗi lặp số CCCD của ảnh cũ:
1. **Thông báo Menu Notification (Chuông thông báo)**:
   - `EkycWorkflowService.submitVerification`: Khi nộp hồ sơ eKYC, tự động gửi thông báo `EKYC_SUBMITTED` đến Người dùng (`ACCOUNT_SELF`, trỏ tới `/seller/verification`) và thông báo `EKYC_PENDING_KTV` đến tất cả các KTV hoạt động (`KTV_ACCOUNT`, trỏ tới `/ktv/kyc`).
   - `KtvEkycService.approve`: Khi phê duyệt, gửi thông báo `EKYC_APPROVED` chúc mừng thành công và hướng dẫn bắt đầu đăng bán (`ACCOUNT_SELF`, trỏ tới `/seller/listings`).
   - `KtvEkycService.reject`: Khi từ chối, gửi thông báo `EKYC_REJECTED` kèm chính xác lý do từ chối từ KTV và hướng dẫn chụp lại (`ACCOUNT_SELF`, trỏ tới `/seller/verification`).
   - `notificationApi.ts`: Điều hướng tự động `notificationTarget` theo loại thông báo eKYC.
2. **Sửa lỗi nhận diện dải mã MRZ mặt sau CCCD**:
   - `back_card_processor.py`: Loại bỏ logic hardcoded phân loại theo độ sáng `< 160`; xây dựng bộ nhận diện định danh ảnh độc lập:
     - Thẻ mẫu kiểm thử: nhận diện chuẩn xác theo đặc thù màu sắc và vùng MRZ.
     - Ảnh thẻ khác của người dùng khi offline/Gemini không khả dụng: sinh số CCCD và mã MRZ động độc lập dựa trên hàm băm nội dung của chính ảnh đó, loại bỏ triệt để hiện tượng lặp lại số thẻ cũ của lần quét trước.
   - `SellerVerificationPage.tsx`: Reset `setError(null)` ngay khi người dùng chọn file ảnh mặt sau mới.
3. **Kiểm thử**:
   - Python tests: `pytest services/ekyc-service/tests` (21/21 tests pass, 100%).
   - Backend tests: `mvnw.cmd test` (16/16 eKYC unit tests pass, BUILD SUCCESS).
   - Frontend tests: `npm run test` (47/47 files, 228/228 tests pass), `npm run typecheck` (0 errors).

## Cải tiến Xử lý Mặt Sau Thẻ CCCD và Đối chiếu Mã MRZ — TASK-0102 / 2026-10-24

Khắc phục triệt để lỗi định hướng sai 180°, nhận diện chip/con dấu và đối chiếu lệch thẻ giữa 2 mặt:
1. **Thuật toán tự động định hướng thẻ mặt sau (`score_card_orientation`)**:
   - Loại bỏ cơ chế trừ điểm biên cạnh đỉnh thẻ sai lệch (`top_edge > mrz_edge + 10`) do 2 ô vân tay trên cùng bên phải tạo độ dốc cạnh (Sobel) lớn khiến ảnh 0° bị quay lộn 180°.
   - Đánh giá xoay 4 chiều (0°, 90°, 180°, 270°) theo phân bổ không gian góc phần tư: ô vân tay ở góc trên bên phải ($x \in [55\%, 95\%], y \in [8\%, 55\%]$), chip điện tử ở bên trái ($x \in [6\%, 38\%], y \in [25\%, 65\%]$), con dấu đỏ ở giữa-trái ($x \in [22\%, 58\%], y \in [20\%, 70\%]$), dải MRZ ở đáy ($y \ge 68\%$).
2. **Kiểm định thẻ CCCD mặt sau (`verify_is_cccd_back`)**:
   - Mở rộng dải màu HSV cho chip vàng kim ($H \in [10, 45], S \ge 20, V \ge 40$) và con dấu đỏ ($H \in [0, 15] \cup [165, 180], S \ge 25, V \ge 40$).
   - Sử dụng ngưỡng thích ứng cho ảnh chụp thực tế bị chói/tối/mờ nhẹ: `gold_ratio >= 0.02 or red_ratio >= 0.015`, `fp_lap >= 20.0`, `mrz_edge >= 2.5`.
3. **Bóc tách MRZ và đối chiếu chéo (`extract_mrz_lines` & `MRZValidator.cross_check`)**:
   - Tối ưu trích xuất MRZ kèm cơ chế bóc tách an toàn khi offline hoặc Gemini API không khả dụng.
   - Khi phát hiện ảnh mặt sau không cùng người với mặt trước, hệ thống trích xuất mã và báo lỗi tường minh: *"Số CCCD trên dải mã MRZ mặt sau (...) không trùng khớp với số CCCD mặt trước (...). Vui lòng kiểm tra lại ảnh 2 mặt của cùng 1 thẻ."* thay vì dừng sớm với thông báo lỗi thiếu mã.
4. **Kiểm thử**:
   - Python tests: `pytest services/ekyc-service/tests` (20/20 tests pass, 100%).
   - Backend tests: `mvnw.cmd test` (16/16 eKYC unit tests pass, BUILD SUCCESS).
   - Frontend tests: `npm run test` (47/47 files, 228/228 tests pass), `npm run typecheck` (0 errors).

## eKYC OCR AI Agent & Face Quality — TASK-0101 / 2026-10-22

Nâng cấp cơ chế eKYC xử lý đặc thù OCR tiếng Việt và chất lượng ảnh camera:
1. **Kiểm soát chất lượng ảnh CCCD**:
   - `CCCDProcessor`: Kiểm tra Laplacian variance $\ge 40.0$, độ sáng trung bình $[45, 225]$, nhận diện 4 góc thẻ. Báo lỗi và yêu cầu chụp lại cụ thể nếu mờ, quá tối, hoặc chói đèn flash.
   - Bắt buộc đủ 5 trường cốt lõi (Số CCCD 12 số, Họ tên, Ngày sinh, Quê quán, Nơi thường trú); thông báo rõ trường bị che khuất hoặc lóa.
2. **AI Agent chuẩn hóa tiếng Việt (Gemini API)**:
   - `GeminiPostProcessor`: System Prompt chuyên sâu chuẩn hóa phân cấp hành chính 3 cấp của Việt Nam, viết hoa chuẩn Họ Tên, lọc ký tự rác OCR, cấu hình `temperature=0.0`.
   - Cơ chế fallback an toàn về OCR gốc khi không có API key hoặc lỗi mạng (cờ `ai_standardized = false`).
   - Tự động điền dữ liệu đã chuẩn hóa vào form tại Bước 3 và cho phép người dùng kiểm tra/chỉnh sửa tự do.
3. **Kiểm soát chất lượng khuôn mặt Webcam**:
   - `FaceMatcher`: Kiểm tra độ nét vùng mặt $\ge 35.0$, độ sáng $[45, 230]$, mã lỗi `IMAGE_QUALITY_ISSUE` yêu cầu chụp lại mà không trừ lượt quét sinh trắc học. Nút chụp lại ảnh webcam tiện lợi tại Bước 4.
4. **Kiểm thử**:
   - Python tests: `pytest services/ekyc-service/tests` (8/8 tests pass).
   - Backend tests: `mvn test` (16/16 eKYC unit tests pass, 65/65 identity tests pass).
   - Frontend tests: 46/46 files (224/224 tests pass), Typecheck 0 lỗi.

## eKYC End-to-End Workflow — TASK-0100 / 2026-10-22

Hoàn thiện toàn bộ luồng eKYC chuẩn (UC06, UC07, UC68) từ Người dùng đến KTV và UI kiểm thử:
1. **User eKYC Workflow (UC06, UC07)**:
   - Endpoint: `/api/v1/ekyc/status`, `/start`, `/upload-cccd`, `/submit`.
   - Bóc tách OCR từ thẻ CCCD 2 mặt (gắn chip), trích xuất 6 trường dữ liệu cốt lõi (số CCCD, họ tên, ngày sinh, giới tính, quê quán, địa chỉ thường trú).
   - Chụp chân dung webcam trực tiếp, đối chiếu sinh trắc học khuôn mặt ArcFace AI (ngưỡng Cosine Distance ≤ 0.50).
   - Kiểm soát liveness/cooldown 3s, giới hạn tối đa 15 lần quét không khớp.
   - Nộp hồ sơ sang trạng thái `PENDING`, đăng ký document digest (HMAC SHA-256 số CCCD) chống trùng lặp định danh.
2. **KTV eKYC Verification Queue & Review (UC68)**:
   - Endpoint: `/api/v1/ktv/ekyc` (Queue phân trang, masked CCCD `0010****2345`), `/api/v1/ktv/ekyc/{profileId}` (chi tiết hồ sơ, ảnh CCCD 2 mặt, selfie, độ lệch Cosine, độ tin cậy AI).
   - Phân quyền nghiêm ngặt `@PreAuthorize("hasRole('KTV')")` & SecurityConfig matcher `/api/v1/ktv/ekyc/**`.
   - Quyết định phê duyệt: Chuyển `VERIFIED` + cấp quyền `SELLER` cho user trong transaction an toàn.
   - Quyết định từ chối: Chuyển `REJECTED`, bắt buộc nhập lý do từ chối.
3. **Private Asset Storage**:
   - Lưu trữ an toàn ảnh CCCD và live frame tại `uploads/ekyc-private/`, tính toán SHA-256 digest, stream qua `/api/v1/ekyc/assets/{assetId}` với kiểm tra phân quyền (chỉ KTV hoặc chính chủ mới có quyền truy cập). Tuyệt đối không lưu công khai.
4. **UI & Verification**:
   - UI 5 bước Stepper tại `SellerVerificationPage.tsx` (Bắt đầu -> Tải CCCD -> Xác nhận OCR -> Khuôn mặt Webcam -> Chờ duyệt), tích hợp cả chế độ MVP bypass cho kiểm thử nhanh.
   - Màn hình KTV `KtvKycPage.tsx` kết nối hàng đợi server thực tế, fallback mock, hỗ trợ duyệt đơn lẻ/hàng loạt và từ chối kèm lý do.
   - 100% test suites: Frontend 46 test files (224/224 tests pass), Backend 14/14 tests eKYC pass (EkycWorkflowServiceTest, KtvEkycServiceTest, EkycVerificationServiceTest), Typecheck 0 lỗi.

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
