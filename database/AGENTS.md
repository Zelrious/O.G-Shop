# Database scope

Đọc `../AGENTS.md`, `../.agents/rules/database.md` và `../.agents/workflows/database-change.md` trước khi thay đổi schema.

- Mọi thay đổi schema phải có migration Flyway mới và tài liệu rollback/forward-fix.
- Migration đã được chia sẻ không được sửa lại.
- Constraint dữ liệu quan trọng phải được bảo vệ ở database và có kiểm thử.
- Chỉ dùng dữ liệu giả trong seed và test.
