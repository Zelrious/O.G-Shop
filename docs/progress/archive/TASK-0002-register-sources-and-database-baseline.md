# TASK-0002 — Register Sources and Database Baseline

- Status: DONE
- Started: 2026-09-18
- Completed: 2026-09-18
- Approved by: Project owner
- Modules: repository-wide, database, identity

## Goal

Đưa tài liệu yêu cầu, tài liệu eKYC và tài liệu thiết kế database vào repository; đăng ký nguồn chính thức; và thiết lập Flyway migration ban đầu `V1__initial_schema.sql` tích hợp 22 bảng cốt lõi của marketplace, extension `pgvector` cùng bảng sinh trắc học `seller_biometrics`.

## Approved scope

- Nhận tài liệu từ `Requirements/`, `D:\Khanh\Đồ án 1\Test\docs\` và `Database/`.
- Cập nhật `reference/SOURCE_REGISTER.md`.
- Đặt tài liệu thiết kế database vào `database/docs/`.
- Tạo `backend/src/main/resources/db/migration/V1__initial_schema.sql`.
- Đảm bảo kiểm thử Backend và chất lượng tài liệu đạt quality gate.

## Completed

- Đã sao chép và lưu trữ:
  - `reference/requirements/dac_ta_yeu_cau_luong_thuat_toan_va_use_case.docx`
  - `reference/requirements/ekyc/ARCHITECTURE.md`, `ALGORITHMS.md`, `DATABASE_DESIGN.md`
  - `database/docs/second_hand_marketplace_database_design.md`, `second_hand_marketplace_database_handoff.md`, `database_risk_and_test_plan.md`, `database_test_report.md`, `second_hand_marketplace_schema.sql`
  - `database/tests/database_tests.sql`
- Đã đăng ký đầy đủ 7 nguồn tài liệu chính thức trong `reference/SOURCE_REGISTER.md` (SRC-01 đến SRC-07).
- Đã tạo Flyway migration `V1__initial_schema.sql`:
  - Kích hoạt extension `vector` (`pgvector`).
  - 22 bảng nghiệp vụ cốt lõi từ `users`, `roles`, `categories`, `products`, `orders`, `payments`, `shipments`, `complaints`...
  - Bảng chuyên biệt `seller_biometrics` lưu `cccd_number` (UNIQUE), thông tin OCR và `face_embedding vector(512)` phục vụ eKYC chống gian lận đa tài khoản.
  - Hỗ trợ phương thức xác thực `AI_EKYC` trong bảng `seller_verifications`.
  - Seed dữ liệu 3 roles mặc định: `BUYER`, `SELLER`, `ADMIN`.
- Cập nhật tài liệu tiến trình: `STATUS.md` và `BACKLOG.md`.

## Verification evidence

- `scripts/quality/validate-docs.ps1`: pass (9 core documents, 8 module documents).
- `scripts/quality/validate-agent-assets.ps1`: pass (10 canonical skills, 10 Claude adapters, 10 workflows).
- `backend`: `mvnw.cmd test`: BUILD SUCCESS; 3 test, 0 failure/error/skipped.
- `frontend`: `npm test`: 1 file/1 test pass (App.test.tsx).

## Changed files

- `reference/requirements/*`
- `reference/SOURCE_REGISTER.md`
- `database/docs/*`
- `database/tests/database_tests.sql`
- `backend/src/main/resources/db/migration/V1__initial_schema.sql`
- `docs/progress/active/task-p0-01-register-sources-and-database-baseline.md` (được lưu trữ tại đây)
- `docs/progress/STATUS.md`
- `docs/progress/BACKLOG.md`
