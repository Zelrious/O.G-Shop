# Kiến trúc Hệ thống eKYC - Chợ Đồ Cũ Uy Tín

Hệ thống eKYC (Electronic Know Your Customer) được thiết kế theo kiến trúc module hóa (Modular Architecture), tách biệt rõ ràng giữa giao diện người dùng (GUI), xử lý AI (Core) và lưu trữ dữ liệu (Database).

## Sơ đồ Tổng quan
Hệ thống bao gồm 3 khối chính tương tác chặt chẽ với nhau:

1. **GUI Layer (`gui_app.py`)**: 
   - Sử dụng Tkinter, quản lý các khung hình điều hướng: Trang chủ, Upload CCCD, Xác nhận thông tin (Form), và Quét Camera Liveness.
   - Luồng Đăng ký & Đăng nhập được xử lý hoàn toàn bất đồng bộ (Multi-threading) để không làm đơ giao diện khi chạy Model AI.

2. **Core AI Layer**:
   - `cccd_extractor.py` & `vietocr`: Nhận diện và bóc tách ký tự từ ảnh thẻ CCCD.
   - `ai_post_processing.py`: Gửi dữ liệu OCR lỗi qua API của Google Gemini để chuẩn hóa lỗi chính tả tiếng Việt.
   - `face_matcher.py`: Trích xuất Vector đặc trưng (512 chiều) từ khuôn mặt và so sánh độ tương đồng (Cosine Distance).

3. **Data Layer (`database_manager.py`)**:
   - Quản lý tương tác với PostgreSQL qua thư viện `psycopg2`.
   - Lưu trữ dữ liệu hồ sơ và Vector khuôn mặt người dùng bằng extension `pgvector`.

## Luồng Hoạt động (Workflow)

### Luồng 1: Đăng ký (Register)
1. User tải ảnh mặt trước CCCD lên GUI.
2. GUI gọi `CCCDProcessor` và `FaceMatcher` chạy song song để vừa bóc OCR, vừa kiểm tra xem thẻ có khuôn mặt không.
3. Dữ liệu OCR được làm sạch bằng Gemini API.
4. GUI gọi `DatabaseManager.check_cccd_exists()` để từ chối ngay nếu CCCD đã đăng ký.
5. Nếu hợp lệ, chuyển sang quét Camera.
6. Khi có khuôn mặt thật nằm đúng vị trí trong vòng tròn và đạt khoảng cách chuẩn, `FaceMatcher` tiến hành trích xuất live vector và so khớp (Cosine <= 0.50).
7. Nếu thành công, toàn bộ dữ liệu (Bao gồm Vector) được lưu vĩnh viễn vào Database thông qua `save_profile()`.

### Luồng 2: Đăng nhập (Login)
1. User nhập mã số CCCD vào khung đăng nhập.
2. GUI truy vấn CSDL qua lệnh `get_face_embedding()`. 
3. Nếu tìm thấy, nạp Vector 512 chiều đó vào bộ nhớ của `FaceMatcher` (caching).
4. Khởi động Camera và yêu cầu quét khuôn mặt. 
5. Khuôn mặt trên camera được trích xuất thành 1 vector mới và tiến hành so sánh với vector lấy từ CSDL. 
6. Chỉ khi mức độ tương đồng vượt ngưỡng 0.50 (Cosine), đăng nhập mới thành công.

## Về bảo mật
- Hệ thống áp dụng nguyên tắc .env (Environment Variables) để bảo mật API key của Gemini cũng như toàn bộ thông tin đăng nhập PostgreSQL (`DB_USER`, `DB_PASSWORD`).
- Dữ liệu lưu trong DB chỉ lưu Vector đặc trưng (Array), không lưu ảnh gốc của người dùng, đảm bảo tuân thủ tính ẩn danh khi CSDL bị lộ cũng không thể phục dựng thành mặt người.
