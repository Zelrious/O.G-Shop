# Ngữ cảnh Dự án (Project Context)

## 1. Tổng quan
Dự án này là một hệ thống **eKYC (Electronic Know Your Customer)** chuyên biệt để xác thực danh tính người dùng thông qua Căn cước công dân (CCCD) Việt Nam. Hệ thống hướng tới việc kết hợp trích xuất thông tin giấy tờ (OCR) và xác thực sinh trắc học khuôn mặt (Liveness & Face Matching) một cách tự động, chính xác và bảo mật.

## 2. Mục tiêu cốt lõi
- Xây dựng một quy trình xác thực 2 bước an toàn:
  - **Bước 1 (Document OCR):** Bóc tách chính xác 100% dữ liệu từ ảnh chụp mặt trước & mặt sau của CCCD. Phát hiện sớm các lỗi chụp ảnh (mờ, lóa, nghiêng, thiếu nét) để bắt người dùng chụp lại ngay lập tức.
  - **Bước 2 (Biometric Verification):** Sử dụng camera trên thiết bị của người dùng (Web App) để quay video khuôn mặt thực tế, so khớp (Face Matching) với khuôn mặt trên CCCD và chống giả mạo (Liveness Detection).

## 3. Kiến trúc Công nghệ (Tech Stack)
- **Backend / Xử lý Core:** Python 3.11+.
- **Module OCR & Quality Check (Bước 1):** Trích xuất thông tin chữ (OCR) sử dụng mô hình tự chủ YOLO + VietOCR cục bộ. Sử dụng API của Google Gemini (Model: `gemini-3.5-flash`) chuyên biệt để đánh giá chất lượng ảnh (Quality Check: độ mờ, lóa, thiếu góc).
- **Module Face Matching (Bước 2):** Đã triển khai thuật toán Face Matching tự chủ nâng cao thay vì phụ thuộc hoàn toàn vào các thư viện có sẵn (như dlib hay OpenCV cơ bản), hiện đang trong giai đoạn kiểm thử và tích hợp Liveness Detection.
- **Frontend / Client:** Web App (Sử dụng HTML5, JavaScript để tương tác với Camera của máy tính/điện thoại người dùng) -> Gửi frame/video về Backend xử lý.
- **Tương lai (Future scope):** Sẽ tự phát triển một mô hình AI đánh giá chất lượng ảnh nội bộ (On-premise) để hoàn thiện tự chủ công nghệ 100%, không phụ thuộc hoàn toàn vào Gemini.

## 4. Ràng buộc & Cần lưu ý
- **Bảo mật dữ liệu (PII):** Đây là hệ thống xử lý dữ liệu định danh cá nhân nhạy cảm, tuyệt đối tuân thủ quy tắc Không lưu trữ (Zero-retention) ảnh và thông tin của người dùng xuống ổ cứng sau khi hoàn thành phiên làm việc.
- **Tối ưu chi phí & Rate Limit:** Việc gọi Gemini API (bản miễn phí) bị giới hạn số lượng request mỗi phút, cần thiết kế hệ thống xử lý lỗi 429 và 503 mượt mà. Kế hoạch tương lai cần xây dựng cơ chế chống DDoS/Spam request.

## 5. Mục đích của Thư mục "docs/"
Thư mục này đóng vai trò như **"Bộ não"** của dự án. Bất cứ khi nào bắt đầu một phiên làm việc mới, hoặc có một kỹ sư mới / AI Assistant mới tham gia, hãy đọc các file trong thư mục này (đặc biệt là `ROADMAP.md` và `WORKFLOW.md`) để lấy lại toàn bộ bối cảnh và định hướng công việc.
