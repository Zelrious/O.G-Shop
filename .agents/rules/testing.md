# Testing rules

- Viết test cho hành vi có rủi ro, invariant và regression; tránh test lặp lại implementation.
- Unit test cho domain rule; integration test cho persistence, security và transaction.
- Luồng thanh toán/đơn hàng cần test lỗi, lặp callback, retry và cạnh tranh.
- Frontend test hành vi người dùng và trạng thái, không bám chi tiết DOM không cần thiết.
- Chạy tập kiểm tra nhỏ nhất đủ chứng minh thay đổi, sau đó chạy quality gate của module.
- Ghi lệnh và kết quả thực tế vào task; không ghi `pass` nếu chưa chạy.
