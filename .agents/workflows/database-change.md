# Database change

1. Xác định owner của dữ liệu và invariant.
2. Đánh giá tương thích, lock, dữ liệu cũ, rollback hoặc forward-fix.
3. Báo cáo migration, code/query bị tác động và kế hoạch test; chờ duyệt.
4. Tạo migration Flyway mới, không sửa migration đã chia sẻ.
5. Cập nhật mapping/repository và dữ liệu seed nếu cần.
6. Test trên PostgreSQL với cả trạng thái trước và sau khi migrate khi có dữ liệu chuyển đổi.
7. Cập nhật database docs, module docs và tiến trình.
