# Catalog Module

- Status: PLANNED
- Requirements: UC-03, UC-04
- Completion: 0/2 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý category, tin đăng một món hàng, media, tìm kiếm, bộ lọc và trang chi tiết.

## Out of scope

Reservation, giá offer được chấp nhận, thanh toán và moderation case.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/catalog/`
- `frontend/src/features/marketplace/` và `frontend/src/features/listing/` khi được tạo
- Flyway tables Category, Product, ProductMedia

## Dependencies

- Identity để xác định Seller và trạng thái verification.
- Trust & Safety để ẩn nội dung vi phạm.

## Security invariants

- Chỉ owner hợp lệ sửa tin đăng.
- Product đang tham gia giao dịch không bị hard delete.
- Media được validate loại, kích thước và quyền truy cập.
- `products.listed_price` là giá Seller niêm yết; giá hiển thị có phí phải do Backend tính từ system-fee policy active.
- Chấp nhận offer không thay đổi giá niêm yết công khai.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.
- [x] V3 tách `listed_price` khỏi giá hiển thị và snapshot giá sản phẩm khi mở conversation.

## Remaining tasks

- [ ] Chốt trạng thái Product và transition guard.
- [ ] Triển khai tạo/sửa/đăng/ẩn tin.
- [ ] Triển khai search/filter/detail.
- [ ] Kiểm thử ownership và visibility.

## Expected changes

Sẽ bổ sung catalog API, query model, media adapter và index database sau khi schema được nhập.
