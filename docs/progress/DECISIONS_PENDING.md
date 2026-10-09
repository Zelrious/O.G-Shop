# Decisions Pending

## Report checkpoint — 2026-10-09

- Quyết định báo cáo hiện hành và nguồn tiếp tục được ghi trong [TASK-0065](archive/TASK-0065-report-policy-checkpoint-20261009.md). Phí tin do Người bán trả sau duyệt, phí bổ sung cộng dồn kể cả Admin giữ nguyên biểu phí; điều kiện nhóm thanh toán, trả/hoàn 100% hoặc miễn hoàn do KTV, cửa sổ lấy trả và phạm vi sandbox đã được đồng bộ trong Google Docs.
- [TASK-0067](archive/TASK-0067-simplify-83-use-case-specifications.md) đã rút gọn 83 UC, giảm 17,3% ký tự và bảo toàn tab sao lưu TASK-0066. Đây là mức giảm quan sát được, không phải mục tiêu trang/tỷ lệ bắt buộc.
- [TASK-0068](archive/TASK-0068-database-coverage-83-use-cases.md) đã hoàn thành [phân tích database theo 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), xác nhận live V1–V14 và chỉ rõ các thiếu hụt/12 nhóm mở rộng. Người dùng đã cho phép triển khai; TASK-0069 đã áp dụng V15–V21 với legacy provenance/backup/restore. Không mở lại các chính sách đã chốt; còn cần nối các API/worker theo [bàn giao V21](../architecture/DATABASE_EXPANSION_V21_20261009.md).
- Bảng quyết định dưới đây là baseline triển khai cũ, chưa đối chiếu toàn bộ với báo cáo 83 UC. Không xem trạng thái cũ của DP-03/DP-07/DP-09/DP-10 là bằng chứng người dùng chưa trả lời cho báo cáo; xem checkpoint mới trước khi tiếp tục.

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
