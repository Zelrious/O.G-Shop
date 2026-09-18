# Thuật Toán Trọng Điểm

Tài liệu này đánh dấu vị trí các đoạn mã lõi (Core algorithms) đóng vai trò sống còn trong việc vận hành quy trình eKYC, giúp lập trình viên tra cứu và chỉnh sửa nhanh chóng.

## 1. So khớp Sinh Trắc Học Khuôn Mặt (Biometric Matching)
- **Tệp nguồn:** `biometric_module/face_matcher.py`
- **Class/Hàm:** `FaceMatcher.process_live_frame()`
- **Chi tiết Thuật toán:**
  - Ứng dụng mô hình **ArcFace** thông qua thư viện `DeepFace`.
  - **Nhận diện khuôn mặt (Detector):** Dùng `retinaface` cực kỳ khắt khe để quét khuôn mặt trên ảnh CCCD (Chống nhận diện nhầm). Dùng `opencv` backend cực kỳ nhẹ và nhanh để quét khung hình camera (Tối ưu tốc độ thời gian thực).
  - **Khoảng cách và Góc độ:** Có hệ thống kiểm tra logic vị trí (Tọa độ khuôn mặt phải nằm đúng vị trí trong bán kính $r \times 0.8$ của vùng quét UI). Kích thước khuôn mặt được kiểm tra `fw < circle_radius * 0.6` (Phải đưa gần) và `fw > circle_radius * 1.7` (Phải đưa xa).
  - **So khớp Cosine Distance:** Cắt ảnh mặt (`cropped_face`), bypass quá trình detect lại, đẩy thẳng vào model `DeepFace.represent()` để lấy mảng Float. Tính góc lệch (Cosine Distance) giữa 2 mảng $512$ chiều:
  $$ \text{Distance} = 1.0 - \frac{A \cdot B}{||A|| \times ||B||} $$
  - **Ngưỡng chặn (Threshold):** `strict_threshold = 0.50`. Nhỏ hơn hoặc bằng 0.50 là đậu.

## 2. Bóc tách dữ liệu CCCD (OCR + Object Detection)
- **Tệp nguồn:** `cccd_module/cccd_extractor.py` (Điều phối), `cccd_module/ocr_core/serve_model.py` (Xử lý Model YOLO)
- **Chi tiết Thuật toán:**
  - Đầu tiên, YOLO (`detector.py`) sẽ quét bức ảnh để nhận dạng các tọa độ chữ nhật (Bounding box) chứa 4 góc thẻ và các trường thông tin (Tên, CCCD, Quê quán...).
  - Hàm Warp Perspective sẽ kéo dãn 4 góc để làm phẳng thẻ nếu người dùng chụp nghiêng.
  - Từng mảnh chữ nhật được cắt ra và đưa vào mạng nơ-ron Transformer của thư viện `vietocr` để chuyển thành Text (Chuỗi văn bản).

## 3. Chống dị dạng cấu trúc bằng AI Ngôn ngữ
- **Tệp nguồn:** `cccd_module/ai_post_processing.py`
- **Class/Hàm:** `GeminiPostProcessor.process()`
- **Chi tiết Thuật toán:**
  - Kết quả OCR dạng JSON đôi khi thiếu một vài dấu phẩy, chữ in hoa, hay mắc một số lỗi ngữ pháp tiếng Việt đặc trưng.
  - Prompt chuyên biệt được thiết kế để đẩy JSON lên API Gemini với cờ `temperature=0.1` (Tuyệt đối không sáng tạo dữ liệu ảo). Gemini sẽ trả về JSON chuẩn, không dư thừa, sạch hoàn toàn lỗi chính tả.

## 4. Bảo đảm toàn vẹn dữ liệu
- **Tệp nguồn:** `database_manager.py`
- **Class/Hàm:** `DatabaseManager.save_profile()`
- **Chi tiết thuật toán:**
  - `numpy.ndarray` chứa Vector khuôn mặt 512 chiều được tự động ép thành List nguyên bản của Python (`embedding.tolist()`) để Extension `pgvector` không văng lỗi Parsing `TypeError`. Quá trình giao tiếp với DB là một luồng kín. Lỗi kết nối sẽ ném (Raise) Exception để vô hiệu hóa việc mở Camera, thay vì lẳng lặng bỏ qua.
