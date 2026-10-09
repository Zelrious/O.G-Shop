# Database workspace

## Current schema — V23 / 09-10-2026

Local og_shop đã gộp 104 nguồn V21 thành **48 bảng nghiệp vụ** (49 nếu tính flyway_schema_history), giữ toàn bộ dữ liệu qua proof 104/104. [Handoff V22](../docs/architecture/DATABASE_CONSOLIDATION_V22_20261009.md), [dictionary 48 bảng](docs/DATABASE_TABLE_DICTIONARY_V22.md), [DDL để dựng ERD](schema/og_shop_v22_tables_for_erd.sql), [full runtime schema, không có dữ liệu](schema/og_shop_v22_runtime_schema.sql).

ERD chọn schema public và bỏ Flyway history. og_compat chứa 104 view có thể ghi cho code/guard hiện tại, không phải 104 bảng hay bản sao. Full schema gồm view/trigger để tham khảo; ERD snapshot chỉ dành database trống, runtime dùng Flyway V1–V23. V22 đã áp dụng, không regenerate để sửa checksum; forward-fix dùng V24+. Checkpoint V21 phía dưới là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Current schema — V21 / 09-10-2026

Local og_shop đã áp dụng V15–V21 theo [bàn giao 83 UC](../docs/architecture/DATABASE_EXPANSION_V21_20261009.md). Migration mới, 61 bảng bổ sung và 7 view, giữ legacy provenance/history; chưa nối toàn bộ API/worker. Runner baseline bên dưới chỉ kiểm tra baseline cũ, không thay bộ expansion PostgreSQL.

`database/scripts/migrate-schema.ps1` mặc định validate; migrate cần env `OGSHOP_MIGRATION_DB_URL`, `OGSHOP_MIGRATION_DB_USER`, `OGSHOP_MIGRATION_DB_PASSWORD` và `-BackupPath` trỏ custom pg_dump đã restore thử. CLI không chạy Spring web/scheduler; không repair history hoặc reset database.


Thư mục này chứa công cụ, dữ liệu mẫu và tài liệu hỗ trợ PostgreSQL. Migration chạy cùng ứng dụng nằm tại `backend/src/main/resources/db/migration/` để phiên bản ứng dụng và schema luôn đi cùng nhau.

- `docs/`: mô hình dữ liệu, invariant và quyết định thiết kế.
- `scripts/`: script quản trị có thể chạy lặp lại.
- `seed/`: dữ liệu giả dành riêng cho development/test.
- `tests/`: kiểm thử integration, constraint và concurrency.

Chạy clean migration, invariant tests và V1/V2 -> V3 legacy backfill test:

```powershell
.\database\run_database_tests.ps1
```

Không đặt dữ liệu thật, thông tin định danh thật hoặc secret trong repository.
