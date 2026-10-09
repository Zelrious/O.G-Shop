# Database tests

## Current verification — V23 / TASK-0070

252 backend tests: 251 pass, 1 Windows symlink skip; 33 DatabaseExpansionPostgresTest pass. Test thêm populated V21→V23: fake decisions/media/fees/checkout/voucher/payment/holds/shipping/case/evidence/points, fingerprints đủ 104 nguồn, checksums V1–V21 giữ nguyên và case tiếp tục hoạt động. Có negative test JSON history authority, FK sai Buyer và competing moderation optimistic lock. Các Pg Spring context xác minh Hibernate views.

Bật OGSHOP_TEST_DB_URL và OGSHOP_UPGRADE_TEST_DB_URL cho PostgreSQL regression; OGSHOP_EXPANSION_TEST_DB_URL phải trỏ DB giả riêng local `ogshop_*test*` và user có CREATE DATABASE. Không chạy test fixtures trên og_shop. Test expansion tạo và cleanup DB UUID riêng; historical moderation upgrade dừng V21 ở schema riêng để kiểm chứng đúng baseline V9/V10. V22 dùng public; fixture upgrade V21→V23 kiểm tra tuyến mới.

Private backup được restore trong chính container local; image vector thử nghiệm riêng chỉ nhận fake fixtures/credentials độc lập. ERD DDL V22 đã nhập database trống thành công/48 bảng; live Hibernate và GET health/categories/products đạt 200, scheduler tắt trong smoke test.

Logs/proofs riêng tại output/database-consolidation-2026-10-09/, bị Git ignore. [Bàn giao V22](../../docs/architecture/DATABASE_CONSOLIDATION_V22_20261009.md). Mục V21 bên dưới là kết quả lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Expansion V15–V21 — TASK-0069

`DatabaseExpansionPostgresTest` có 30 kiểm thử PostgreSQL: clean V1→V21, populated V14→V21/SOLD, identity/ownership/immutability, fee/top-up, reservation/refund/reward/voucher concurrency, source allocation, late money, cases, direct delivery, quick auth và zero checkout. Test tạo database UUID riêng, không clean DB được cung cấp; test user cần CREATE DATABASE. Chỉ dùng PostgreSQL/pgvector thử nghiệm riêng, URL local `ogshop_*test*` không có query params.

```powershell
$env:OGSHOP_EXPANSION_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:45569/ogshop_expansion_test'
$env:OGSHOP_TEST_DB_USER = 'ogshop_test'
$env:OGSHOP_TEST_DB_PASSWORD = 'ogshop_test' # fake credentials for a disposable test container
./scripts/quality/verify-backend.ps1 -MavenArguments @('--batch-mode','--no-transfer-progress','-Dtest=DatabaseExpansionPostgresTest','test')
```

Để chạy regression và Hibernate validate trên V21, đặt `OGSHOP_TEST_DB_URL` tới DB PostgreSQL riêng; `OGSHOP_UPGRADE_TEST_DB_URL` bật upgrade test cũ. Kết quả cuối TASK-0069: 249 tests, 0 failures/errors, 1 symlink skip do Windows; 30/30 expansion pass. Không có env thì Pg tests bị skip, không được báo đã kiểm chứng PostgreSQL. Backup local đã restore và migrate trên DB riêng. [Chi tiết](../../docs/architecture/DATABASE_EXPANSION_V21_20261009.md).


Kiểm thử constraint, transaction, tính bất biến của trạng thái và migration backfill bằng PostgreSQL/pgvector thật.

```powershell
.\database\run_database_tests.ps1
```

Script tạo container tạm `og-shop-database-tests`, chạy hai tuyến và tự xóa container khi hoàn tất:

1. V1 -> V2 -> V3 trên database rỗng, sau đó chạy `database_tests.sql`.
2. V1 -> V2 -> legacy fixture -> V3, sau đó chạy `v3_legacy_assertions.sql`.

Dùng `-Reset` chỉ khi container test cùng tên còn từ lần chạy trước. Script không gắn volume và không thao tác container/database ứng dụng.

## Nhiều danh mục V8 — PostgreSQL integration test

Runner baseline phía trên không thay thế kiểm chứng V8. `MultipleCategoriesPostgresTest` chạy khi có `OGSHOP_CATEGORY_TEST_DB_URL` hoặc `OGSHOP_TEST_DB_URL`; cần database PostgreSQL/pgvector riêng vì test tạo fixture và chạy toàn bộ Flyway migrations trong checkout (hiện V1–V11). Không trỏ vào database ứng dụng.

Ví dụ từ repository, sau khi tạo database test và PostgreSQL đã sẵn sàng (mật khẩu dưới đây chỉ dành cho container test):

```powershell
docker run --name og-shop-category-test -d -p 127.0.0.1:45555:5432 -e POSTGRES_DB=ogshop_category_test -e POSTGRES_USER=ogshop_category_test -e POSTGRES_PASSWORD=ogshop_category_test pgvector/pgvector:pg17
docker exec og-shop-category-test pg_isready -U ogshop_category_test -d ogshop_category_test
$env:OGSHOP_CATEGORY_TEST_DB_URL = 'jdbc:postgresql://localhost:45555/ogshop_category_test'
$env:OGSHOP_CATEGORY_TEST_DB_USER = 'ogshop_category_test'
$env:OGSHOP_CATEGORY_TEST_DB_PASSWORD = 'ogshop_category_test'
Push-Location backend
.\mvnw.cmd '-Dtest=MultipleCategoriesPostgresTest' test
Pop-Location
```

Đợi `pg_isready` báo accepting connections trước Maven. Test kiểm chứng tạo/sửa/đọc, loại trùng ID, legacy payload, lỗi validation, version sau lưu, lọc qua danh mục thứ hai và tổng/phân trang không trùng trên transaction thật. Không có biến URL thì lớp này bị skip; kết quả unit test đơn thuần không chứng minh PostgreSQL đã chạy.

Sau integration test, kiểm tra bốn constraint negative paths (tự ROLLBACK):

```powershell
docker cp .\database\tests\v8_multiple_categories_tests.sql og-shop-category-test:/tmp/v8-categories.sql
docker exec og-shop-category-test psql -X -v ON_ERROR_STOP=1 -U ogshop_category_test -d ogshop_category_test -f /tmp/v8-categories.sql
```

Chỉ dọn container test do mình vừa tạo sau khi xác minh tên/image bằng `docker inspect`; không xóa container/volume ứng dụng. Gỡ ba biến `OGSHOP_CATEGORY_TEST_DB_*` khỏi phiên shell sau khi kiểm thử. Nâng V7→V8 đã được kiểm tra trên dữ liệu cũ và seed; migration hiện có đến V11, test dùng Flyway sẽ áp dụng toàn bộ phiên bản có trong checkout.
