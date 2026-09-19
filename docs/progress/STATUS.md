# Project Status

- Project: Old but Gold (O.G Shop)
- Updated: 2026-09-19
- Phase: CORE_FEATURES_FIRST
- Overall status: READY_FOR_NEXT_TASK
- Active task: None
- Published branch: `origin/main`

## Completed

- Chốt tên dự án và repository.
- Chốt Modular Monolith với Backend Java/Spring Boot và Frontend React/TypeScript.
- Khởi tạo Git repository cục bộ trên nhánh `main`.
- Kết nối remote `origin` tới `https://github.com/Zelrious/O.G-Shop.git`.
- Hoàn thành monorepo scaffold, module boundaries và tài liệu nền.
- Backend/Frontend foundation, database tooling và CI đã được khởi tạo.
- Cấu hình AI/agent cá nhân được giữ local và không còn theo dõi trên Git.
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

1. Chốt DP-09 (mức phí) và DP-10 (cart hay direct checkout); trong lúc chưa chốt tiếp tục dùng fee policy 0% và không xóa cart.
2. Hoàn thiện traceability và kiểm thử các phần còn lại của UC-01/UC-02.
3. Triển khai UC-03/UC-04: product listing, danh sách, tìm kiếm và chi tiết sản phẩm.
4. Triển khai UC-05/UC-06: REST chat/history trước, sau đó offer/counter-offer transaction.
5. Triển khai UC-07 đến UC-14: accept offer, checkout, payment mock và vòng đời đơn hàng; bổ sung concurrency test.
6. Kết nối frontend và xác minh luồng end-to-end Buyer ↔ Seller.
7. Sau khi core acceptance gate đạt, mới thực hiện external integrations, WebSocket, outbox worker và Redis theo nhu cầu đo được.


## Current risks

- Các chính sách thời hạn kiểm tra hàng, tự giải ngân và hoàn hàng còn cần xác nhận.
- Các tích hợp ở TASK-0006 đến TASK-0009 chưa được triển khai nhưng không được chặn việc kiểm thử core flow bằng adapter local/mock.
- Luồng hiện tại là eKYC mô phỏng kỹ thuật, không phải KYC production.
- Mức phí hệ thống chưa được chốt; policy active hiện tại là 0% để không thu nhầm.
- Accept offer/checkout đồng thời chưa có application-level concurrency test; đây là điều kiện bắt buộc trước giai đoạn tối ưu.
- Outbox worker, WebSocket và Redis được chủ động defer; schema outbox hiện tại chỉ là nền tảng, chưa phải dependency của core flow.
