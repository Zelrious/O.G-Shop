# TASK-0014 — Buy Now: Checkout + Reservation/Order

- Status: PAUSED_BEFORE_IMPLEMENTATION
- Paused: 2026-09-20
- Resume condition: TASK-0018 được duyệt trực quan trên Figma
- Modules: Catalog, Order, Identity, Frontend

## Mục tiêu khi tiếp tục

Xây dựng vertical slice Backend + Frontend cho luồng mua ngay, gồm kiểm tra quyền mua, địa chỉ nhận hàng, tóm tắt chi phí, tạo reservation/order và chống bán trùng bằng transaction/concurrency test.

## Điểm tạm dừng

- Chưa triển khai code mới cho TASK-0014.
- Không thay đổi schema/API checkout cho đến khi luồng Buyer trong TASK-0018 được duyệt.
- Giữ nguyên toàn bộ kết quả TASK-0012 và TASK-0013 đang có trong working tree.

## Điều kiện tiếp tục

1. Duyệt các màn hình giỏ hàng, địa chỉ, voucher, checkout, kết quả đặt hàng và trạng thái lỗi.
2. Chốt quy tắc reservation, hết hạn giữ hàng và phản hồi khi sản phẩm vừa được người khác mua.
3. Triển khai đồng thời Backend + Frontend và kiểm thử happy path, authorization, idempotency, concurrency.
