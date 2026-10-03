# Project Status

- Project: Old but Gold (O.G Shop)
- Updated: 2026-09-20
- Phase: UI_UX_DESIGN_FIRST
- Overall status: IN_PROGRESS
- Active task: TASK-0018 — Marketplace UI/UX design system & clickable prototype in Figma
- Paused task: TASK-0014 — Buy Now (paused before implementation; resume after design approval)
- Published branch: `origin/main`

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
