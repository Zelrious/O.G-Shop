# Database workspace

Thư mục này chứa công cụ, dữ liệu mẫu và tài liệu hỗ trợ PostgreSQL. Migration chạy cùng ứng dụng nằm tại `backend/src/main/resources/db/migration/` để phiên bản ứng dụng và schema luôn đi cùng nhau.

- `docs/`: mô hình dữ liệu, invariant và quyết định thiết kế.
- `scripts/`: script quản trị có thể chạy lặp lại.
- `seed/`: dữ liệu giả dành riêng cho development/test.
- `tests/`: kiểm thử integration, constraint và concurrency.

Không đặt dữ liệu thật, thông tin định danh thật hoặc secret trong repository.
