# Decisions Pending

| ID | Quyết định cần chốt | Mặc định tạm thời | Tác động |
|---|---|---|---|
| DP-01 | Access token và refresh token | JWT ngắn hạn + refresh token có thể thu hồi | Identity, security, database |
| DP-02 | Thời hạn reservation khi checkout | Cấu hình, chưa chốt giá trị | Commerce, payment, scheduled job |
| DP-03 | Thời hạn Buyer kiểm tra hàng | Cấu hình, chưa chốt giá trị | Fulfillment, complaint, payment |
| DP-04 | Payment sandbox | Adapter nội bộ trước, provider chọn sau | Payment |
| DP-05 | Shipping sandbox | Adapter nội bộ trước, provider chọn sau | Fulfillment |
| DP-06 | Media provider | Interface chung, chọn Cloudinary/ImageKit/S3 sau | Catalog, evidence |
| DP-07 | Chính sách hoàn hàng | Full-order refund trong MVP | Complaint, fulfillment, payment |
| DP-08 | Giấy phép repository | Chưa tạo LICENSE | Repository governance |
