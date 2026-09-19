# Identity Module

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
- Password và refresh token không xuất hiện trong log hoặc response body.
- Browser không nhận face embedding và không gọi FastAPI trực tiếp.
- Khóa tài khoản không xóa lịch sử giao dịch.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.
- [x] Triển khai giao diện React và Typed Mock Adapter cho Auth (UC-01) và eKYC Seller Verification (UC-02) theo Batch 1 ([TASK-0003](file:///d:/Khanh/Đồ án 1/Source Code/O.G Shop/docs/progress/archive/TASK-0003-ui-batch-01-auth-and-ekyc.md)).
- [x] Đóng gói Microservice AI eKYC độc lập ([services/ekyc-service/](file:///d:/Khanh/Đồ án 1/Source Code/O.G Shop/services/ekyc-service/)) với FastAPI, tích hợp YOLOv11n + VietOCR Transformer (144.8MB) + Gemini Flash + RetinaFace + DeepFace ArcFace 512-d (Cosine Distance <= 0.50) ([TASK-0004](file:///d:/Khanh/Đồ án 1/Source Code/O.G Shop/docs/progress/archive/TASK-0004-integrate-ekyc-ai-algorithm-microservice.md)).
- [x] TASK-0005 thay luồng mock bằng Spring Boot Identity authority: BCrypt, access JWT, opaque refresh rotation/revocation và HttpOnly cookie.
- [x] TASK-0005 đưa eKYC sau Spring Boot gateway; FastAPI dùng internal token, request-local embedding và fail-closed.
- [x] TASK-0005 dùng Flyway V2 để loại dữ liệu CCCD/face embedding thật khỏi schema runtime, chỉ giữ metric và metadata model.

## Remaining tasks (Lộ trình Bước 2 & Backend)

- [ ] TASK-0006: Gmail/Mailpit và OTP reset password.
- [ ] TASK-0007: Google OAuth2/OIDC và account linking.
- [ ] TASK-0008: Cloudinary avatar qua `AvatarStoragePort`.
- [ ] Hoàn thiện profile/address và audit role transition để xác minh trọn vẹn UC-01/UC-02.

## Expected changes

- Bổ sung cấu hình Cloudinary, Gmail SMTP và Google OAuth2 theo từng task.
- Xây dựng Media Upload Service sau outbound port do Identity sở hữu.
- Xây dựng Email Service gửi OTP reset mật khẩu và notification qua Gmail SMTP.
- DTO, controller, application service, security principal, repository, migration sẽ được bổ sung theo từng task được phê duyệt.
