# Commerce Module

- Status: PLANNED
- Requirements: UC-07, UC-09
- Completion: 0/2 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý cart, checkout group, reservation, Order và thao tác xử lý đơn của Seller.

## Out of scope

Provider payment, shipment tracking và dispute resolution.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/commerce/`
- `frontend/src/features/checkout/` và `frontend/src/features/orders/` khi được tạo
- Flyway tables Cart, CartItem, Order, OrderItem

## Dependencies

- Identity, Catalog, Communication, Payment và Platform.

## Security invariants

- Một Order thuộc đúng một Seller.
- Reservation được tạo trong checkout transaction.
- Checkout khóa Product theo thứ tự ổn định.
- Total và offer price được xác nhận lại ở server.
- OrderItem giữ snapshot lịch sử.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.

## Remaining tasks

- [ ] Chốt reservation timeout.
- [ ] Thiết kế checkout command contract.
- [ ] Triển khai row-lock và transaction.
- [ ] Kiểm thử hai Buyer checkout đồng thời.

## Expected changes

Đây là module rủi ro cao; code chỉ được thêm cùng migration và PostgreSQL concurrency test.
