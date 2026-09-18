# Báo cáo chạy thử database

## Môi trường

- Ngày chạy: 2026-09-16
- Engine: PostgreSQL 15.4 (image local `ankane/pgvector:latest`)
- Phạm vi: DDL, seed role, constraint và invariant test
- Backend/API: chưa triển khai

## Kết quả lần chạy baseline

- DDL khởi tạo thành công.
- 22 bảng được tạo.
- PostgreSQL catalog ghi nhận 137 constraint và 88 index (bao gồm primary-key/unique index tự sinh).
- 3 role `BUYER`, `SELLER`, `ADMIN` được seed.
- 16 smoke/invariant test pass.
- Dữ liệu test được bọc trong transaction và rollback; sau test `users = 0`.

Các trường hợp đã kiểm tra:

1. Email phải lowercase.
2. Email không được trùng.
3. Một user chỉ có một default address chưa xóa.
4. Một user chỉ có một KYC pending hoặc verified.
5. Conversation không trùng buyer/seller/product.
6. Quantity MVP phải bằng 1.
7. Order total phải bằng subtotal cộng shipping fee.
8. Product reserved phải có owner và expiry.
9. Reservation owner phải chứa chính product đó trong order item.
10. Reservation ghi nhận owning order.
11. Address snapshot không đổi khi address nguồn bị sửa.
12. Một order chỉ có một payment.
13. Partial refund bị chặn trong MVP.
14. Một order chỉ có một complaint active.
15. Report luôn phải có target.
16. Product đã tham gia transaction không thể hard-delete.

## Chưa kiểm tra trong baseline

- Race condition bằng hai connection PostgreSQL độc lập.
- Timeout job đối đầu payment callback.
- Hai admin resolve complaint đồng thời.
- Hiệu năng với dataset lớn và `EXPLAIN ANALYZE`.
- Backup/restore drill.
- Runtime database role và phân quyền production.

Các mục này thuộc vòng test transaction/concurrency tiếp theo, sau khi chốt command contract cho checkout, payment và complaint.
