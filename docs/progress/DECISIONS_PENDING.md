# Decisions Pending

| ID | Quyết định cần chốt | Mặc định tạm thời | Tác động |
|---|---|---|---|
| DP-02 | Thời hạn reservation khi checkout | Cấu hình, chưa chốt giá trị | Commerce, payment, scheduled job |
| DP-03 | Thời hạn Buyer kiểm tra hàng | Cấu hình, chưa chốt giá trị | Fulfillment, complaint, payment |
| DP-04 | Payment sandbox | Adapter nội bộ trước, provider chọn sau | Payment |
| DP-05 | Shipping sandbox | Adapter nội bộ trước, provider chọn sau | Fulfillment |
| DP-07 | Chính sách hoàn hàng | Full-order refund trong MVP | Complaint, fulfillment, payment |
| DP-08 | Giấy phép repository | Chưa tạo LICENSE | Repository governance |
| DP-09 | Mức phí hệ thống, bên chịu phí và min/max | Policy V3 0% để không thu nhầm; Backend hỗ trợ phí Buyer/Seller | Catalog, offer, checkout, payment |
| DP-10 | Giữ cart nhiều sản phẩm hay chỉ mua trực tiếp từ listing/offer | Tạm giữ `carts`/`cart_items`; accepted offer vẫn có thể checkout trực tiếp | Commerce, frontend checkout |

## Resolved

| ID | Quyết định | Ngày |
|---|---|---|
| DP-01 | Access JWT 15 phút + opaque refresh token 30 ngày, HttpOnly, rotate/revoke | 2026-09-19 |
| DP-06 | Cloudinary là media provider duy nhất cho MVP, ẩn sau outbound port | 2026-09-19 |
| DP-11 | Hoàn thiện và kiểm thử core flow trên PostgreSQL trước WebSocket, outbox worker, Redis hoặc tối ưu multi-instance | 2026-09-19 |
