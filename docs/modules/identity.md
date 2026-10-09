# Identity Module

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

- [x] Tạo package boundary và tài liệu module.
- [x] Triển khai giao diện React và Typed Mock Adapter cho Auth (UC-01) và eKYC Seller Verification (UC-02) theo Batch 1 ([TASK-0003](../progress/archive/TASK-0003-ui-batch-01-auth-and-ekyc.md)).
- [x] Đóng gói Microservice AI eKYC độc lập ([services/ekyc-service/](../../services/ekyc-service/)) với FastAPI, tích hợp YOLOv11n + VietOCR Transformer (144.8MB) + Gemini Flash + RetinaFace + DeepFace ArcFace 512-d (Cosine Distance <= 0.50) ([TASK-0004](../progress/archive/TASK-0004-integrate-ekyc-ai-algorithm-microservice.md)).
- [x] TASK-0005 thay luồng mock bằng Spring Boot Identity authority; TASK-0073 thay refresh rotation bằng access JWT và cookie có cùng thời hạn cố định.
- [x] TASK-0005 đưa eKYC sau Spring Boot gateway; FastAPI dùng internal token, request-local embedding và fail-closed.
- [x] TASK-0005 dùng Flyway V2 để loại dữ liệu CCCD/face embedding thật khỏi schema runtime, chỉ giữ metric và metadata model.
- [x] TASK-0012 triển khai MVP Seller Activation: Flyway V4 hỗ trợ method `MVP_BYPASS`; endpoint `POST /api/v1/seller-verification/activate` cấp role `SELLER` idempotent; UI `/seller-verification` minh bạch thông báo bước eKYC thật đang tạm thời được bỏ qua.
- [x] TASK-0013 cung cấp application facade `IdentityCatalogFacade` (`DefaultIdentityCatalogFacade`) làm hợp đồng liên module cho Catalog kiểm tra seller capability từ database và batch lookup thông tin Người bán công khai (`sellerId`, `displayName`, `trustLabel`) mà không vi phạm ranh giới module.

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
