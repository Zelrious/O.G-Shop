# Tiêu chuẩn Bảo mật & An toàn Dữ liệu (Security Standards)

Hệ thống eKYC xử lý dữ liệu PII (Personally Identifiable Information) cấp độ cao nhất (Căn cước công dân và Sinh trắc học khuôn mặt). Mọi dòng code được viết ra phải tuân thủ nghiêm ngặt các nguyên tắc dưới đây.

## 1. Zero-Retention (Không lưu trữ dữ liệu vĩnh viễn)
- **Ảnh CCCD:** Không lưu trữ ảnh thật của CCCD xuống ổ cứng hoặc Database sau khi hoàn tất quy trình trích xuất OCR, trừ khi có yêu cầu pháp lý cụ thể được thông qua (và nếu có, phải mã hóa AES-256).
- **Video/Frame Liveness:** Frame ảnh quét từ Camera của người dùng chỉ được lưu trên RAM (bộ nhớ tạm) của Server để so khớp khuôn mặt. Ngay sau khi `match_score` được tính toán xong, frame phải bị hủy bỏ ngay lập tức.
- **Biometric Templates:** Không lưu trữ trực tiếp file ảnh khuôn mặt mà chỉ lưu các vector đặc trưng (Embeddings/Face Descriptors) nếu cần lưu lịch sử.

## 2. Masking Dữ liệu Nhạy cảm (Log Masking)
Khi in log ra Console hoặc lưu file Log (bằng Python `logging`), hệ thống không được phép in các trường nhạy cảm dạng raw:
- **Số CCCD:** Chỉ hiện 4 số cuối, ví dụ: `********8387`.
- **API Key / Secret Tokens:** Ẩn hoàn toàn trong log, ví dụ: `GEMINI_API_KEY=AIzaSy...***`.

## 3. Bảo vệ Hạ tầng API (Security Check)
- Chặn các định dạng file thực thi (`.exe`, `.bat`, `.sh`, `.php`) ngay tại lớp Frontend hoặc Controller. Chỉ cho phép xử lý `.jpg`, `.jpeg`, `.png`, `.webp`, `.jfif`.
- Kiểm tra kích thước file tối đa (Mặc định 4MB) để chống các cuộc tấn công DDoS ngốn băng thông và RAM.

## 4. Bảo mật kết nối API
- Module Web App bắt buộc chạy trên HTTPS (Bảo vệ luồng video/WebRTC không bị sniffing giữa đường).
- Các API Key của Google Gemini và các dịch vụ bên thứ 3 khác phải được nạp thông qua biến môi trường (`.env`), tuyệt đối không hardcode (ghi cứng) vào trong source code Python.
