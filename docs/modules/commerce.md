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
- Offer được chấp nhận chỉ cập nhật giá giao dịch trong `order_items`, không ghi đè `products.listed_price`.
- `total_amount = subtotal + buyer_system_fee + shipping_fee`; `seller_proceeds = subtotal - seller_system_fee`.
- `accepted_offer_id` chỉ được dùng một lần và phải được xác minh `ACCEPTED` trong checkout transaction.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.
- [x] V3 snapshot giá niêm yết, giá thỏa thuận, phí hai phía và seller proceeds trên order/order item.

## Remaining tasks

- [ ] Chốt reservation timeout.
- [ ] Thiết kế checkout command contract.
- [ ] Triển khai row-lock và transaction.
- [ ] Kiểm thử hai Buyer checkout đồng thời.

## Expected changes

Đây là module rủi ro cao; code chỉ được thêm cùng migration và PostgreSQL concurrency test.
