# Project Status

## Public use-case policies — 2026-10-09 / TASK-0071

- Completed: [TASK-0071](archive/TASK-0071-public-use-case-policies-and-coins.md) cập nhật trực tiếp 83 đặc tả, 420 ô; giảm 17,9% ký tự, giữ mẫu 10 trường và sao lưu native đầy đủ. Đồng bộ tổng quan/dẫn chiếu, uy tín 100 và các mức phạt do KTV quyết định, năm nguồn xu, voucher trước rồi xu đến 0 đồng kể cả phí giao, hoàn xu không cấp lại voucher.
- Verified: đọc lại native đủ 83 bảng; danh sách và BR đúng định dạng, nhánh/tham chiếu hợp lệ; hai tab ngoài phạm vi nguyên vẹn, nội dung bản sao khớp trước sửa.
- Incomplete/blocked: xuất PDF thành công nhưng tải tệp lỗi hostname/transport, chưa kiểm tra bố cục trang. Task chỉ sửa báo cáo; code/database/diagram chưa được đồng bộ chính sách mới trong đợt này.
- Next: chính sách hiện hành theo báo cáo và TASK-0071; triển khai bằng thay đổi tiếp nối khi được yêu cầu. Checkpoint runtime V23 bên dưới không chứng minh các quy tắc mới đã được triển khai.

## Current database checkpoint — 2026-10-09 / TASK-0070

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
