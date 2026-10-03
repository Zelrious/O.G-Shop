# TASK-0012 — MVP Seller Activation: Tạm bỏ qua eKYC, hoàn thiện cả Backend và Frontend

- Status: COMPLETE
- Approved: 2026-09-20
- Owner: O.G Shop
- Modules: Identity
- Requirements: UC-01, UC-02

## Mục tiêu

Triển khai vertical slice hoàn chỉnh từ Database -> Backend -> Frontend:
1. Cho phép người dùng đăng ký hoặc đăng nhập, mở trang `/seller-verification` và kích hoạt quyền Người bán (role `SELLER`) tức thì ở chế độ `MVP_BYPASS`.
2. Giao diện UI thông báo minh bạch rằng bước xác minh eKYC thật đang tạm thời được bỏ qua; không tuyên bố sai lệch rằng AI đã xác minh.
3. Backend là authority duy nhất thực hiện transaction cấp role `SELLER` một cách idempotent, không nhận `userId` từ client, không tạo dữ liệu sinh trắc học hoặc metric giả.
4. Đảm bảo toàn bộ quy chuẩn kiểm thử, migration và chất lượng mã nguồn đạt 100%.

## Phạm vi đã thực hiện

- **Database:**
  - Tạo Flyway migration `V4__mvp_seller_activation.sql`.
  - Mở rộng check constraint `ck_seller_verifications_method` chấp nhận `'MVP_BYPASS'`.
  - Điều chỉnh `ck_seller_verifications_resolution` cho phép trạng thái `'VERIFIED'` khi method là `'MVP_BYPASS'` mà không cần `verified_by`.
- **Backend:**
  - Thêm cấu hình `app.seller-verification.mode` (mặc định: `MVP_BYPASS`, hỗ trợ override qua env `SELLER_VERIFICATION_MODE`).
  - Thêm lớp cấu hình `SellerVerificationProperties.java`.
  - Thêm exception `SellerActivationDisabledException` và ánh xạ sang HTTP 400 với mã lỗi `MVP_ACTIVATION_DISABLED` trong `ApiExceptionHandler.java`.
  - Cập nhật `SellerVerificationEntity.java` hỗ trợ method `MVP_BYPASS` và phương thức `upgradeToMvpBypass`.
  - Tạo `SellerActivationService.java` xử lý cấp role `SELLER` và bản ghi verification một cách idempotent.
  - Tạo controller `SellerVerificationController.java` cho endpoint `POST /api/v1/seller-verification/activate` yêu cầu Bearer JWT hợp lệ.
  - Cập nhật `SecurityConfig.java` cho phép authenticated access tới `/api/v1/seller-verification/**`.
  - Giữ nguyên toàn bộ mã nguồn FastAPI eKYC, OCR và so khớp sinh trắc học để kích hoạt lại sau.
- **Frontend:**
  - Bổ sung `MvpActivationResult` và hàm `activateMvpSeller` trong `verificationApi.ts`.
  - Cập nhật `SellerVerificationPage.tsx`: hiển thị cảnh báo trung thực về chế độ MVP, nút kích hoạt, xử lý đầy đủ loading (disable nút), hiển thị lỗi backend, thành công thì gọi `reloadCurrentUser()` và hiển thị kết quả. Nếu đã có role `SELLER`, hiển thị trạng thái hoàn tất kèm nút về Profile thay vì nút kích hoạt.
  - Đồng bộ `ProfilePage.tsx` phù hợp với chế độ MVP.
  - Giữ nguyên các component `CccdUploader` và `CameraCapture` trong source để tái sử dụng sau.
- **Kiểm thử:**
  - Viết 6 test unit trong `SellerActivationServiceTest.java`.
  - Bổ sung test endpoint bảo mật trong `OgShopApplicationTests.java`.
  - Cập nhật `database/tests/database_tests.sql` và `database/run_database_tests.ps1` kiểm chứng V4 trên PostgreSQL.
  - Viết 4 test component/flow trong `Verification.test.tsx`.
- **Tài liệu & Backlog:**
  - Cập nhật `STATUS.md`, `BACKLOG.md` (sắp xếp lại 7 vertical slices), `identity.md`, `IDENTITY_API.md`, `TRACEABILITY_MATRIX.md`.

## Bằng chứng kiểm chứng

1. **Database Migration & Invariant Tests:**
   - Lệnh: `powershell -ExecutionPolicy Bypass -File .\database\run_database_tests.ps1 -Reset`
   - Kết quả: **PASS**. Clean migration V1 -> V2 -> V3 -> V4 thành công, toàn bộ 26 smoke/invariant assertions và 9 legacy V3 backfill assertions PASS.
2. **Backend Tests:**
   - Lệnh: `cmd /c "mvnw.cmd test"`
   - Kết quả: **BUILD SUCCESS**, **19/19 tests PASS** (gồm 6 tests mới trong `SellerActivationServiceTest`, 6 tests trong `OgShopApplicationTests`, 4 tests trong `AuthServiceTest`, 2 tests trong `EkycVerificationServiceTest`, 1 test trong `AuthControllerTest`).
3. **Frontend Tests:**
   - Lệnh: `npm.cmd test`
   - Kết quả: **PASS**, **4/4 test files, 10/10 tests PASS** (100%).
4. **Frontend Typecheck:**
   - Lệnh: `npm.cmd run typecheck`
   - Kết quả: **0 lỗi** (TypeScript `tsc -b` pass).
5. **Frontend Linting:**
   - Lệnh: `npm.cmd run lint`
   - Kết quả: **0 warning, 0 error** (ESLint pass).
6. **Frontend Production Build:**
   - Lệnh: `npm.cmd run build`
   - Kết quả: **Vite build production thành công** trong 407ms.
7. **Quality Validation Scripts:**
   - Lệnh: `powershell -ExecutionPolicy Bypass -File .\scripts\quality\validate-docs.ps1` -> **Documentation structure valid**.
   - Lệnh: `powershell -ExecutionPolicy Bypass -File .\scripts\quality\validate-agent-assets.ps1` -> **Agent assets valid**.
   - Lệnh: `git diff --check` -> **PASS** (không có whitespace lỗi).

## File thay đổi

- Database:
  - `backend/src/main/resources/db/migration/V4__mvp_seller_activation.sql` (mới)
  - `database/tests/database_tests.sql`
  - `database/run_database_tests.ps1`
- Backend:
  - `backend/src/main/resources/application.yml`
  - `backend/src/test/resources/application-test.yml`
  - `backend/src/main/java/com/oldbutgold/shop/shared/config/SellerVerificationProperties.java` (mới)
  - `backend/src/main/java/com/oldbutgold/shop/shared/config/SecurityConfig.java`
  - `backend/src/main/java/com/oldbutgold/shop/shared/api/ApiExceptionHandler.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/identity/application/SellerActivationDisabledException.java` (mới)
  - `backend/src/main/java/com/oldbutgold/shop/modules/identity/application/SellerActivationService.java` (mới)
  - `backend/src/main/java/com/oldbutgold/shop/modules/identity/api/SellerVerificationController.java` (mới)
  - `backend/src/main/java/com/oldbutgold/shop/modules/identity/infrastructure/persistence/SellerVerificationEntity.java`
  - `backend/src/main/java/com/oldbutgold/shop/modules/identity/infrastructure/persistence/SellerVerificationRepository.java`
  - `backend/src/test/java/com/oldbutgold/shop/modules/identity/application/SellerActivationServiceTest.java` (mới)
  - `backend/src/test/java/com/oldbutgold/shop/OgShopApplicationTests.java`
- Frontend:
  - `frontend/src/features/verification/verificationApi.ts`
  - `frontend/src/features/verification/Verification.test.tsx`
  - `frontend/src/pages/SellerVerificationPage.tsx`
  - `frontend/src/pages/ProfilePage.tsx`
- Documentation:
  - `docs/progress/STATUS.md`
  - `docs/progress/BACKLOG.md`
  - `docs/modules/identity.md`
  - `docs/api/IDENTITY_API.md`
  - `docs/requirements/TRACEABILITY_MATRIX.md`
  - `docs/progress/archive/TASK-0012-mvp-seller-activation.md` (mới)
