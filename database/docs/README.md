# Database design documents

Ghi ERD, ownership bảng, invariant, index, chính sách lưu giữ và quyết định migration ở đây. Khi chủ dự án đưa schema đã duyệt vào repository, đăng ký nguồn tại `reference/SOURCE_REGISTER.md` trước khi tạo baseline.

Dữ liệu giả để test UI/logic trên V1–V8 nằm ở [database/seed](../seed/README.md), gồm bốn tài khoản và fixtures Profile/Catalog/voucher/order/payment mock, có sản phẩm thuộc nhiều danh mục. Seed tách khỏi Flyway; V8 là migration chuẩn cho quan hệ nhiều danh mục, các tài liệu thiết kế baseline cũ chưa mô tả quan hệ này.
