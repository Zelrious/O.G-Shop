# Database rules

- Schema production thay đổi bằng migration Flyway tăng dần.
- Không sửa migration đã chia sẻ; dùng migration mới để forward-fix.
- Tên bảng/cột/index/constraint phải rõ nghĩa và nhất quán.
- Dùng foreign key, unique/check constraint cho invariant có thể bảo vệ tại database.
- Checkout, payment, refund và dispute phải có chiến lược idempotency và concurrency.
- Mọi migration nặng cần nêu lock, thời gian chạy và phương án phục hồi.
- Dữ liệu seed chỉ dùng cho development/test và không chứa PII thật.
