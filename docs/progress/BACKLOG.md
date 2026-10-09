# Project Backlog

## Database design report — TASK-0072

- [x] Viết danh sách 48 bảng và 48 bảng chi tiết theo mẫu Google Docs, loại Flyway; native backup, kiểm đủ nội dung và căn lề/ghim tiêu đề.
- [ ] Kiểm bố cục trang khi PDF/HTML export hoạt động trở lại; lần này Google Drive trả 403.

## Public use-case policies — TASK-0071

- [x] [TASK-0071](archive/TASK-0071-public-use-case-policies-and-coins.md): sửa đủ 83 đặc tả theo cách dùng/chính sách, điểm uy tín và xu; sao lưu native, đồng bộ tên/tổng quan, đọc lại cấu trúc và nội dung.
- [ ] Kiểm tra trang PDF khi tải tệp xuất hoạt động trở lại; chưa xác nhận bố cục toàn báo cáo.
- [ ] Khi được yêu cầu triển khai: đồng bộ OTP/Google, uy tín 100, mức trừ và hạn chế do KTV quyết định, thưởng xu theo mốc/đánh giá, xu dùng cho phí giao đến 0 đồng và hoàn xu riêng voucher; rà code/V23/diagram theo chính sách mới.

## Database consolidation — TASK-0070 / V23

- [x] Gộp 104 nguồn thành 48 bảng nghiệp vụ; preserve count/hash, backend mappings/guards, backup/restore và migrate/validate local og_shop.
- [x] Regression/Hibernate/runtime và ERD DDL verification; xuất dictionary 48 bảng và schema V22.
- [x] Đưa nội dung 48 bảng/907 trường vào chương database của báo cáo theo TASK-0072.
- [ ] Đưa ERD vào báo cáo khi người dùng chuẩn bị hoặc yêu cầu dựng sơ đồ.
- [ ] Nối API/UI/worker canonical theo 83 UC; chuyển service khỏi lớp og_compat theo từng module trước khi loại view. Không double-write money/history.

Các mục expansion V21 bên dưới ghi phần nền và công việc ứng dụng tiếp nối; tên nguồn hiện hành đối chiếu theo [V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md).

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — TASK-0069

- [x] V15–V21/G01–G12: schema, invariants/transaction primitives và mapping tương thích; backup/restore, PostgreSQL regression, áp dụng/validate local và handoff 83 UC.
- [ ] Nối API Identity/eKYC/KTV/Seller vào V15, kiểm soát legacy bypass theo migration mode.
- [ ] Nối revision/AI/duyệt/fee và public catalog; checkout group/quantity/voucher/points/zero payment.
- [ ] Nối payment IPN/group allocation, component refund/release, delivery/case deadlines/cleanup, notification/report workers và E2E sandbox. Theo [hợp đồng transaction](../architecture/DATABASE_EXPANSION_V21_20261009.md), không double-write legacy money.


## Report priority — checkpoint 2026-10-09

- [x] [TASK-0065](archive/TASK-0065-report-policy-checkpoint-20261009.md): đồng bộ chính sách báo cáo 83 UC và lưu công việc cuối ngày; đối chiếu native nội dung/định dạng, giữ tab gốc.
- [x] [TASK-0066](archive/TASK-0066-use-case-simplification-plan.md): đọc lại nguồn, tạo và kiểm tra tab sao lưu native; chuẩn bị kế hoạch tinh giản 83 đặc tả.
- [x] [TASK-0067](archive/TASK-0067-simplify-83-use-case-specifications.md): rút gọn đủ 83 đặc tả, giảm 17,3% ký tự, giữ 10 trường/mã BR/tab sao lưu; đọc lại native và kiểm bố cục PDF mẫu.
- [x] [TASK-0068](archive/TASK-0068-database-coverage-83-use-cases.md): rà cấu trúc live V1–V14 và code theo đủ 83 UC; [ma trận/thiết kế mở rộng](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), 12 nhóm dùng chung và thứ tự migration/kiểm chứng. Chỉ phân tích, chưa triển khai schema.
- [ ] Đồng bộ bản báo cáo rút gọn với diagram/database/code khi được yêu cầu; không gộp vào công việc rút gọn câu chữ.

## Completed foundation

- [x] Tạo monorepo Backend, Frontend, Database, Reference, Docs và Infra.
- [x] Thiết lập quy trình làm việc local (không còn theo dõi trên Git).
- [x] Thiết lập Maven/npm lock, local quality scripts và GitHub Actions.
- [x] Kiểm tra Backend, Frontend, documentation và Compose config.
- [x] Push baseline lên `origin/main` tại commit `40e3f8c`.

## P0 — Foundation

- [x] Đưa tài liệu yêu cầu đã duyệt vào `reference/requirements/`.
- [x] Đưa tài liệu database đã duyệt vào repository.
- [x] Tạo `V1__initial_schema.sql` từ schema đã được xác nhận (tích hợp pgvector và seller_biometrics).
- [x] Triển khai UI Batch 1: Auth (Login, Register, Forgot Password, Profile) và eKYC Seller Verification.
- [x] Tích hợp AI Microservice eKYC độc lập (services/ekyc-service: YOLOv11n + VietOCR Transformer + Gemini Flash + ArcFace 512-d).
- [ ] Hoàn thiện `TRACEABILITY_MATRIX.md` theo UC, BR và NFR.
- [x] Chốt authentication và session strategy (DP-01): access JWT + opaque refresh cookie rotation.

## P1 — Core flow (các vertical slice Backend + Frontend, bắt buộc trước tối ưu hạ tầng)

- [x] TASK-0010: Database pricing, negotiation, chat cursor và outbox baseline.
- [x] TASK-0011: Khóa thứ tự core-feature-first và acceptance gate trước Redis.
- [x] TASK-0012: MVP Seller Activation — bỏ qua eKYC thật, kích hoạt Seller trực tiếp và an toàn cả Backend và Frontend.
- [x] TASK-0013: Catalog — Seller quản lý tin đăng + Buyer duyệt/tìm kiếm/xem chi tiết.
- [ ] TASK-0018: Marketplace UI/UX — Figma design system, responsive desktop/mobile, Light/Dark, VI/EN, sample data và prototype theo role. **IN PROGRESS**.
- [ ] TASK-0014: Buy Now — UI checkout + Backend reservation/order chống bán trùng. **PAUSED BEFORE IMPLEMENTATION** đến khi TASK-0018 được duyệt.
- [ ] TASK-0015: Mock Payment — Thanh toán giữ tiền mô phỏng + màn hình đơn hàng Buyer/Seller.
- [ ] TASK-0016: Fulfillment cơ bản — Quản lý vận chuyển + UI trạng thái đơn end-to-end.
- [ ] TASK-0017: Communication REST & Offer — Chat hội thoại, cursor lịch sử và giao dịch trả giá (thực hiện sau luồng mua trực tiếp).

Thứ tự triển khai trong P1:

1. TASK-0012 — Seller activation MVP.
2. TASK-0013 — Catalog: Seller quản lý tin đăng + Buyer duyệt/tìm kiếm/xem chi tiết.
3. TASK-0018 — Thiết kế UI/UX Figma và duyệt trực quan trước khi tiếp tục code chức năng.
4. TASK-0014 — Buy Now: UI checkout + Backend reservation/order chống bán trùng.
5. TASK-0015 — Mock payment + màn hình đơn hàng Buyer/Seller.
6. TASK-0016 — Fulfillment cơ bản + UI trạng thái đơn end-to-end.
7. Chat/Offer thực hiện sau luồng mua trực tiếp.
8. Redis, WebSocket, outbox worker và external integrations tiếp tục deferred.

## P2 — Tích hợp dịch vụ bên ngoài

- [x] TASK-0005: Identity và eKYC Security Baseline.
- [ ] TASK-0006: Gmail SMTP/Mailpit và OTP reset password.
- [ ] TASK-0007: Google OAuth2/OIDC.
- [ ] TASK-0008: Cloudinary avatar.
- [ ] TASK-0009: Cloudinary product/evidence media.

Các adapter bên ngoài không được làm thay đổi quy tắc nghiệp vụ cốt lõi. Khi provider chưa sẵn sàng, core flow dùng adapter local/mock phù hợp để tiếp tục được kiểm thử.

## P3 — Realtime và tối ưu hệ thống

- [ ] WebSocket single-instance cho chat sau khi REST chat/offer đã ổn định.
- [ ] Outbox worker idempotent cho notification/realtime.
- [ ] Redis cache cho hot reads chỉ sau khi có số đo cho thấy cần cache.
- [ ] Redis Pub/Sub chỉ khi chạy nhiều backend instance hoặc có yêu cầu phân phối realtime tương đương.

Chỉ bắt đầu P3 khi:

- UC-03 đến UC-14 có happy path và negative path cơ bản đã vượt kiểm thử.
- Authorization/ownership, state transition và message idempotency đã được kiểm thử.
- Accept offer/checkout đồng thời đã có concurrency test và không bán trùng.
- Frontend hoàn thành ít nhất một luồng đăng tin → chat → offer → order → payment mock.
- PostgreSQL pagination đủ đúng và đủ nhanh cho dữ liệu kiểm thử; Redis bị tắt không làm hỏng luồng đọc/ghi cốt lõi.

## P4 — Trust and administration

- [ ] UC-15 Đánh giá và uy tín.
- [ ] UC-16 Báo cáo và kiểm duyệt.
- [ ] UC-17 Quản lý user và quyền.
- [ ] UC-18 Dashboard quản trị.
- [ ] UC-19 Thông báo và audit.

## Deferred beyond MVP

- AI định giá hoặc gợi ý sản phẩm.
- Voucher, điểm thưởng và gamification.
- Hoàn tiền từng phần.
- AI tự động thương lượng thay người dùng.
- Tích hợp KYC hoặc vận chuyển production.
