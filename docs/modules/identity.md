# Identity Module

- Status: PLANNED
- Requirements: UC-01, UC-02
- Completion: 0/2 use cases verified
- Last reviewed: 2026-09-17

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
- Password/token không xuất hiện trong log hoặc response.
- Khóa tài khoản không xóa lịch sử giao dịch.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.
- [x] Triển khai giao diện React và Typed Mock Adapter cho Auth (UC-01) và eKYC Seller Verification (UC-02) theo Batch 1 ([TASK-0003](file:///d:/Khanh/Đồ án 1/Source Code/O.G Shop/docs/progress/archive/TASK-0003-ui-batch-01-auth-and-ekyc.md)).
- [x] Đóng gói Microservice AI eKYC độc lập ([services/ekyc-service/](file:///d:/Khanh/Đồ án 1/Source Code/O.G Shop/services/ekyc-service/)) với FastAPI, tích hợp YOLOv11n + VietOCR Transformer (144.8MB) + Gemini Flash + RetinaFace + DeepFace ArcFace 512-d (Cosine Distance <= 0.50) ([TASK-0004](file:///d:/Khanh/Đồ án 1/Source Code/O.G Shop/docs/progress/archive/TASK-0004-integrate-ekyc-ai-algorithm-microservice.md)).
- [x] Kết nối Frontend React (CccdUploader, CameraCapture) trực tiếp với Microservice qua [verificationApi.ts](file:///d:/Khanh/Đồ án 1/Source Code/O.G Shop/frontend/src/features/verification/verificationApi.ts) (kèm cơ chế fallback mô phỏng an toàn).

## Remaining tasks (Lộ trình Bước 2 & Backend)

- [ ] **Bước 2.1**: Tích hợp ImageKit SDK để lưu trữ và nén ảnh Avatar đại diện người dùng.
- [ ] **Bước 2.2**: Tích hợp Cloudinary SDK để lưu trữ media sản phẩm (hình ảnh đa góc độ, video kiểm thử tình trạng, video khiếu nại bằng chứng).
- [ ] **Bước 2.3**: Tích hợp Google App Password (Gmail SMTP) để gửi email chứa mã OTP khôi phục mật khẩu thật.
- [ ] **Bước 2.4**: Tích hợp Google OAuth2 để đăng nhập nhanh bằng tài khoản Google và cấp phát token JWT chuẩn.
- [ ] Triển khai tầng Backend Java/Spring Boot Identity: Controller, Service, Spring Security, JWT rotation filters.
- [ ] Tích hợp API Backend Spring Boot lưu trữ hồ sơ eKYC vào bảng `seller_verifications` và `seller_biometrics` (PostgreSQL `pgvector`).
- [ ] Kiểm thử bảo mật (security invariants), quyền hạn (RBAC), thu hồi phiên (session revocation) và quyền riêng tư sinh trắc học (biometric privacy).

## Expected changes

- Bổ sung cấu hình Secret Key cho ImageKit, Cloudinary, Gmail SMTP và Google OAuth2 vào file môi trường.
- Xây dựng Media Upload Service trên Backend/Frontend hỗ trợ direct upload lên Cloudinary / ImageKit.
- Xây dựng Email Service gửi OTP reset mật khẩu và notification qua Gmail SMTP.
- DTO, controller, application service, security principal, repository, migration sẽ được bổ sung theo từng task được phê duyệt.

