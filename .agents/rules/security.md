# Security rules

- Mặc định từ chối truy cập cho đến khi endpoint có chính sách rõ.
- Không ghi log password, token, secret, hồ sơ KYC hoặc bằng chứng tranh chấp nhạy cảm.
- Secret đi qua biến môi trường hoặc secret manager; chỉ commit `.env.example`.
- Validate đầu vào ở biên và encode đầu ra theo ngữ cảnh.
- Dùng truy vấn tham số/JPA; không ghép chuỗi SQL từ input.
- Webhook/payment callback phải xác minh nguồn, chống replay và xử lý idempotent.
- Chỉ dùng sandbox hoặc mô phỏng cho payment, KYC và shipping trong phạm vi đồ án.
- Dependency mới phải có lý do, phiên bản cố định và kiểm tra lỗ hổng phù hợp.
