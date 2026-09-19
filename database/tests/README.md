# Database tests

Kiểm thử constraint, transaction, tính bất biến của trạng thái và migration backfill bằng PostgreSQL/pgvector thật.

```powershell
.\database\run_database_tests.ps1
```

Script tạo container tạm `og-shop-database-tests`, chạy hai tuyến và tự xóa container khi hoàn tất:

1. V1 -> V2 -> V3 trên database rỗng, sau đó chạy `database_tests.sql`.
2. V1 -> V2 -> legacy fixture -> V3, sau đó chạy `v3_legacy_assertions.sql`.

Dùng `-Reset` chỉ khi container test cùng tên còn từ lần chạy trước. Script không gắn volume và không thao tác container/database ứng dụng.
