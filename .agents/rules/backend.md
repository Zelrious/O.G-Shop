# Backend rules

- Giữ modular monolith và boundary trong `docs/architecture/MODULE_BOUNDARIES.md`.
- Controller chỉ chuyển đổi HTTP; application service điều phối use case và transaction.
- Domain không phụ thuộc Spring, HTTP hoặc persistence khi có thể tránh.
- Module không truy cập repository của module khác; giao tiếp qua application contract hoặc event nội bộ.
- DTO API không để lộ entity persistence.
- Kiểm tra quyền ở server theo resource và hành động, không tin role/owner từ client.
- Endpoint thay đổi trạng thái nhạy cảm phải xét idempotency, concurrency và audit.
