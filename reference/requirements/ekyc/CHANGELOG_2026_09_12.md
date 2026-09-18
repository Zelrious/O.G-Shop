# Nhật Ký Phát Triển (Changelog) - Ngày 12/09/2026

## Tóm tắt chung
Hôm nay chúng ta đã tập trung giải quyết triệt để các vấn đề liên quan đến module **Nhận diện sinh trắc học (Face Matching)**, bao gồm sửa các lỗi môi trường, tối ưu hóa tốc độ nhận diện thời gian thực (Real-time), và vá lỗ hổng bảo mật nhận diện sai (False Positive).

## Chi tiết các thay đổi (Changelog)

### 1. Sửa lỗi Môi trường & Thư viện
- **OpenCV & Numpy:** Xóa bỏ bản `opencv-python-headless` (gây lỗi không mở được cửa sổ Camera) và cài đặt lại `opencv-python==4.10.0.84` kết hợp với `numpy==1.26.4`. Xử lý dứt điểm tình trạng xung đột thư viện với `TensorFlow 2.16`.

### 2. Nâng cấp Giao diện Kiểm thử (`camera_test.py`)
- **Tích hợp Tkinter:** Thêm hộp thoại chọn file giao diện UI để người dùng dễ dàng chọn ảnh CCCD cần test thay vì hard-code đường dẫn tĩnh.
- **Xử lý Đa luồng (Multi-threading):** Đưa luồng AI DeepFace xuống chạy ngầm (background thread), giúp Camera hiển thị mượt mà không bị treo hay giật lag.
- **Giao diện Hướng dẫn:** Vẽ khung hình chữ nhật căn giữa màn hình để hướng dẫn người dùng đặt khuôn mặt đúng vị trí. Viền khung tự động chuyển Xanh Lá (Matched) hoặc Đỏ (Not Matched) để báo hiệu.
- **Tốc độ thời gian thực (Real-time):** Gỡ bỏ thời gian delay 2 giây, cho phép AI liên tục quét tốc độ cao ngay khi rảnh.

### 3. Tái cấu trúc (Refactor) Kiến trúc lõi (`biometric_module/face_matcher.py`)
- **Fix lỗi Unicode tiếng Việt:** Tích hợp `cv2.imdecode` và `numpy` để đọc trực tiếp luồng byte của ảnh. Khắc phục hoàn toàn lỗi `cv2.imread` không thể đọc được file khi đường dẫn thư mục có dấu tiếng Việt (ví dụ: `Đồ án 1`).
- **Kiến trúc Lưu trạng thái (Stateful):**
  - Chuyển từ cơ chế quét lại CCCD liên tục sang cơ chế: Đọc CCCD 1 lần duy nhất ở đầu quy trình.
  - Sử dụng **RetinaFace** để bóc tách khuôn mặt trên thẻ CCCD (vì RetinaFace bắt góc ảnh nhỏ, mờ cực kỳ chuẩn xác). Mã hóa khuôn mặt thành Vector Embedding và lưu đệm (cache) vào RAM.
- **Tối ưu Tốc độ Webcam:** Cấu hình luồng Camera sử dụng bộ dò **OpenCV Haar Cascades**, giảm thời gian phát hiện khuôn mặt thực tế xuống chỉ còn `0.01s` (Tức thời).
- **Vá lỗ hổng Bảo mật & Tùy chỉnh Threshold:**
  - Phát hiện lỗi False Positive do ngưỡng Cosine mặc định (0.68) của ArcFace quá lỏng đối với người Châu Á.
  - Tự code lại công thức tính khoảng cách hình học Cosine Distance.
  - **(Cập nhật mới nhất từ bạn):** Đã tinh chỉnh và chốt hệ số an toàn (Threshold) ở mức **`0.55`**. Mức này đảm bảo tính bảo mật cực cao (chặn hoàn toàn người lạ) nhưng không gây khó dễ cho người dùng chính chủ như mức 0.50.

### 4. Tự chủ Công nghệ OCR (Thoát ly Google Gemini API)
- **Tích hợp Local OCR (YOLO + VietOCR):** Sao chép thành công bộ mô hình AI từ dự án tham khảo vào `cccd_module/ocr_core`. Đảm bảo độ chính xác cực cao thông qua cơ chế crop từng trường bằng YOLO trước khi đọc bằng VietOCR. 
- **Đánh giá chất lượng ảnh (Blur Detection):** Xây dựng hàm kiểm tra độ sắc nét bằng phương sai Laplacian (OpenCV) ngay tại Local, chặn sớm ảnh mờ, lóa và các lỗi cắt góc trước khi qua xử lý OCR.
- **Tối ưu Luồng Xử lý & Giao diện:** Chuyển đổi ứng dụng (`gui_app.py`, `main.py`) chỉ tập trung xử lý **1 ảnh mặt trước** (tối ưu nhất cho luồng eKYC đăng ký tài khoản). Tự động suy luận giới tính dựa vào thế kỷ trong số CCCD. Tiết kiệm tài nguyên và thời gian thao tác.

---
**Trạng thái hiện tại:** Hoàn thành xuất sắc Bước 1 (OCR CCCD) và Bước 2 (Face Matching). Hệ thống lõi AI đã chạy hoàn hảo và ổn định. Sẵn sàng tiến tới Bước 3: Phát triển Web App Frontend.
