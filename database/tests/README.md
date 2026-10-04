# Database tests

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
