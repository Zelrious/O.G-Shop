# Security Baseline

## Authentication and authorization

- Password phải được băm bằng thuật toán được Spring Security hỗ trợ; không tự viết crypto.
- Mọi API ghi dữ liệu kiểm tra role, ownership và trạng thái ở Backend.
- Không cấp ADMIN qua đăng ký hoặc profile API.
- Khóa tài khoản không xóa dữ liệu giao dịch hoặc bằng chứng liên quan.

## Sensitive data

- Không commit secret hoặc credential.
- KYC và evidence dùng dữ liệu mẫu trong đồ án.
- Media nhạy cảm lưu private; chỉ cấp signed URL ngắn hạn sau kiểm tra quyền.
- Không ghi password, token, giấy tờ, signed URL hoặc nội dung evidence vào log/audit.

## Transaction safety

- Checkout khóa/reserve hàng theo thứ tự ổn định để tránh bán trùng.
- Total, fee và offer price được tính lại ở Backend.
- Callback và scheduled job phải idempotent.
- Không release khi tồn tại Complaint hoặc return đang mở.

## Input and output

- Validate kích thước, định dạng, enum và loại tệp.
- Dùng parameter binding/JPA; không ghép SQL từ input.
- Chỉ trả trường DTO đã chủ động chọn.
- Nội dung do người dùng tạo phải được escape khi hiển thị.

## Audit

Các hành động KYC approval, role change, account restriction, moderation, release, refund và dispute resolution phải ghi actor, action, entity, reason, time và request ID.
