# TASK-0003 — UI Batch 1: Auth & eKYC Seller Verification

- Status: DONE
- Started: 2026-09-18
- Completed: 2026-09-18
- Approved by: Project Owner
- Scope: Frontend Batch 1 (UC-01, UC-02)

## Goal

Thiết kế và triển khai toàn bộ giao diện, routing, component primitives và mock security service cho cụm tính năng Auth (Đăng nhập, Đăng ký, Quên mật khẩu, Profile) và eKYC Seller Verification (Upload CCCD + Quét Camera WebRTC sinh trắc học).

## Approved Scope & Decisions

- Đã được chủ dự án phê duyệt cài đặt `react-router-dom` (`^7.18.4`).
- Bảo mật thông tin nhạy cảm: cấu hình `.env` / `.env.example`, mã hóa băm mật khẩu một chiều SHA-256 qua Web Crypto API.
- Quản lý phiên: mô phỏng Spring Security với JWT Access Token in-memory + Refresh Token Rotation an toàn.
- Tuân thủ Design Tokens ngữ nghĩa đạt chuẩn WCAG 2.2 AA.

## Completed

- **Cấu hình & Dependencies**:
  - Cài đặt `react-router-dom` hỗ trợ routing chuẩn URL.
  - Tạo `frontend/.env` và `frontend/.env.example`.
- **Design Tokens & Primitives** (`shared/`):
  - [styles.css](file:///d:/Khanh/Đồ án 1/Source Code/O.G Shop/frontend/src/app/styles.css): Tokens màu sắc, typography, spacing, shadows, responsive layout.
  - UI Primitives: `Button`, `Input`, `Card`, `Badge`, `Alert`.
- **Nghiệp vụ Auth (UC-01)** (`features/auth/`):
  - `hashUtils.ts`: Mã hóa mật khẩu bằng SHA-256.
  - `mockAuthService.ts`: Đăng nhập, đăng ký, quên mật khẩu (OTP), logout, và Token Rotation.
  - `AuthContext.tsx` & `useAuth.ts`: Quản lý session in-memory, phục hồi phiên khi refresh trang.
  - `LoginForm.tsx`, `RegisterForm.tsx`, `ForgotPasswordForm.tsx`.
- **Nghiệp vụ eKYC (UC-02)** (`features/verification/`):
  - `useWebcam.ts`: Hook mở/tắt webcam WebRTC, chụp frame selfie lật gương.
  - `CccdUploader.tsx`: Upload ảnh CCCD, xem trước, mô phỏng OCR trích xuất thông tin thẻ.
  - `CameraCapture.tsx`: Khung tròn quét khuôn mặt, tính khoảng cách Cosine sinh trắc học ArcFace.
  - `VerificationStatusCard.tsx`: Hiển thị trạng thái duyệt hồ sơ và huy hiệu Seller.
- **Màn hình & Routing** (`pages/` & `app/`):
  - `HomePage.tsx`: Giới thiệu nền tảng và các cam kết tin cậy.
  - `LoginPage.tsx`, `RegisterPage.tsx`, `ForgotPasswordPage.tsx`.
  - `ProfilePage.tsx`: Xem hồ sơ, vai trò, nút đăng xuất, nút đăng ký bán hàng eKYC.
  - `SellerVerificationPage.tsx`: Wizard 4 bước eKYC hoàn chỉnh.
  - `Navbar.tsx` & `ProtectedRoute.tsx`.
- **Kiểm thử**:
  - 3 test suites: `App.test.tsx`, `Auth.test.tsx`, `Verification.test.tsx` (5/5 tests passed).
  - TypeScript typecheck: 0 lỗi.
  - ESLint: 0 lỗi, 0 warning.
  - Production build: thành công (bundle gzip 92.97 kB).

## Verification Evidence

- `npm run typecheck`: exit code 0.
- `npm run lint`: exit code 0 (0 warnings).
- `npm test`: 3 test files passed, 5/5 tests passed.
- `npm run build`: built in 369ms, output tại `frontend/dist/`.
- `validate-docs.ps1`: pass.
- `validate-agent-assets.ps1`: pass.
