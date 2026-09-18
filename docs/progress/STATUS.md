# Project Status

- Project: Old but Gold (O.G Shop)
- Updated: 2026-09-18
- Phase: FOUNDATION
- Overall status: FOUNDATION_READY
- Active task: None
- Published baseline: `3536238` on `origin/main`

## Completed

- Chốt tên dự án và repository.
- Chốt Modular Monolith với Backend Java/Spring Boot và Frontend React/TypeScript.
- Khởi tạo Git repository cục bộ trên nhánh `main`.
- Kết nối remote `origin` tới `https://github.com/Zelrious/O.G-Shop.git`.
- Hoàn thành monorepo scaffold, module boundaries và tài liệu nền.
- Hoàn thành agent governance cho Codex, Claude Code và Gemini CLI.
- Backend đạt 3/3 test; Frontend đạt lint, typecheck, 1/1 test và production build.
- Docker Compose, tài liệu và agent assets đã qua kiểm tra cấu trúc.
- Đã hoàn thành [TASK-0002](archive/TASK-0002-register-sources-and-database-baseline.md):
  - Nhập tài liệu yêu cầu, kiến trúc eKYC và tài liệu database vào `reference/` và `database/docs/`.
  - Đăng ký 7 nguồn tài liệu trong `reference/SOURCE_REGISTER.md`.
  - Tạo Flyway migration `V1__initial_schema.sql` tích hợp 22 bảng, extension `pgvector` và bảng `seller_biometrics`.
  - Kiểm chứng Backend (3/3 test pass), Frontend (1/1 test pass), tài liệu và agent assets hợp lệ.
- Đã hoàn thành [TASK-0003](archive/TASK-0003-ui-batch-01-auth-and-ekyc.md):
  - Cài đặt `react-router-dom` và cấu hình routing URL (`/`, `/login`, `/register`, `/forgot-password`, `/profile`, `/seller-verification`).
  - Thiết lập Design Tokens chuẩn O.G Shop và UI primitives (`Button`, `Input`, `Card`, `Badge`, `Alert`).
  - Triển khai cụm tính năng Auth: mã hóa mật khẩu SHA-256, quản lý session JWT in-memory + Refresh Token Rotation an toàn.
  - Triển khai eKYC Wizard 4 bước: tải ảnh CCCD + bóc tách OCR, quét khuôn mặt thời gian thực qua Camera WebRTC, so khớp ArcFace Cosine Distance, tự động cấp quyền `SELLER`.
  - Đạt 100% Quality Gate: typecheck 0 lỗi, lint 0 warning, 5/5 unit tests passed, build production thành công.
- Đã hoàn thành [TASK-0004](archive/TASK-0004-integrate-ekyc-ai-algorithm-microservice.md):
  - Đóng gói thuật toán AI eKYC thực tế từ `D:\Khanh\Đồ án 1\Test` (YOLOv11n + VietOCR Transformer + Gemini Flash + DeepFace ArcFace 512-d + Cosine Distance <= 0.50) thành FastAPI Microservice độc lập (`services/ekyc-service`).
  - Tối ưu đường dẫn trọng số độc lập, loại bỏ hoàn toàn các thành phần thừa (GUI Tkinter cũ, thư mục testcase nặng, references ngoài).
  - Tích hợp endpoint `/api/v1/ekyc/ocr` bóc tách thông tin CCCD và trích xuất vector khuôn mặt 512-d đồng thời.
  - Tích hợp endpoint `/api/v1/ekyc/match-face` so khớp sinh trắc học thời gian thực với độ sai lệch Cosine chính xác.
  - Kết nối Frontend React `CccdUploader` và `CameraCapture` với `verificationApi.ts`, hiển thị huy hiệu AI Thật và độ tin cậy.

## In progress

- Không có task triển khai đang hoạt động. Task tiếp theo phải qua approval gate.


## Next

1. **Bước 2: Tích hợp các dịch vụ bên ngoài (External Services)**:
   - **Bước 2.1**: Tích hợp ImageKit SDK để lưu trữ ảnh đại diện (avatar) của khách hàng.
   - **Bước 2.2**: Tích hợp Cloudinary SDK để quản lý media sản phẩm (ảnh chi tiết, video kiểm tra chất lượng, video bằng chứng khiếu nại).
   - **Bước 2.3**: Cấu hình Gmail App Password (SMTP) phục vụ gửi email OTP khôi phục mật khẩu thật.
   - **Bước 2.4**: Tích hợp Google OAuth2 cho phép đăng nhập bằng Google và cấp phát token JWT.
2. Lập ma trận truy vết yêu cầu (`TRACEABILITY_MATRIX.md`) chi tiết cho các Use Case.
3. Chốt authentication/session strategy (DP-01).
4. Triển khai tầng Backend Spring Boot cho module `identity` (UC-01 Quản lý tài khoản, UC-02 Xác minh Seller) và kết nối PostgreSQL `pgvector`.


## Current risks

- Các chính sách thời hạn kiểm tra hàng, tự giải ngân và hoàn hàng còn cần xác nhận.
- Docker CLI và cấu hình Compose hợp lệ, nhưng Docker Engine trên máy đang dừng nên chưa chạy integration test PostgreSQL.
