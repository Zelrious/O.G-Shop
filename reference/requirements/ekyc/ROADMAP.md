# Kế hoạch Phát triển Dự án (Roadmap & Backlog)

Tài liệu này theo dõi tiến độ các tính năng đã, đang và sẽ được phát triển cho hệ thống eKYC.

## 🟢 Đã hoàn thành (Done)
- [x] Khởi tạo kiến trúc Module Python theo chuẩn OOP (`cccd_module`).
- [x] **Xây dựng bộ trích xuất dữ liệu CCCD (OCR):** Tự chủ công nghệ, tích hợp thành công mô hình YOLO + VietOCR cục bộ thay thế hoàn toàn cho API.
- [x] **Nâng cấp AI (YOLOv11):** Chuyển đổi thành công Corner Detection & Text Detection từ TFLite cũ sang YOLOv11, giúp tăng tốc độ quét và đạt độ chính xác >99% với ảnh chuẩn. Khắc phục hoàn toàn lỗi `ValueError: Please try again`.
- [x] Tối ưu hóa UI để xử lý tập trung vào 1 ảnh mặt trước CCCD.
- [x] Tích hợp cơ chế Validate dữ liệu (kiểm tra độ dài, ngày tháng, giới tính, hạn sử dụng logic).
- [x] Xây dựng script Automation Test (`run_tests.py`) và test thử với ảnh mờ, ảnh che, ảnh chuẩn (đã PASS).
- [x] Thiết lập Bộ tài liệu "Bộ não Dự án" (Documentation/Knowledge Base).
- [x] **Nâng cấp Bước 1 (Quality Check):** Chỉnh sửa Prompt của Gemini để AI đánh giá chất lượng ảnh.
- [x] **Tự chủ Công nghệ (Face Matching):** Đã code xong thuật toán Face Matching nâng cao bằng OpenCV & Deep Learning thay vì phụ thuộc API.

## 🟡 Đang thực hiện (In Progress / Next Tasks)

**1. Kiểm thử & Hoàn thiện Module Face Matching (Bước 2)**
- [ ] Tích hợp Face Matching vào `run_tests.py` để test riêng (Unit Test).
- [ ] Chạy kiểm thử Face Matching với các cặp ảnh cùng một người (chụp thẳng, góc nghiêng, ánh sáng kém).
- [ ] Chạy kiểm thử Face Matching với các cặp ảnh hai người khác nhau để đo FAR (False Acceptance Rate).
- [ ] Căn chỉnh (Face Alignment) khuôn mặt từ camera để tối ưu hóa việc so khớp với ảnh trên CCCD.
- [ ] Tích hợp tính năng Liveness Detection cơ bản (Yêu cầu chớp mắt / Quay đầu).

**2. Phát triển API (Backend Core)**
- [ ] Viết API `/upload_cccd` (Upload ảnh -> Quality Check -> OCR -> Trả về JSON).
- [ ] Viết API `/face_match` (Nhận 1 frame camera + Tọa độ face box trên CCCD -> Face Match).
- [ ] Viết API `/liveness_check` (WebSocket hoặc SSE để stream frame từ client và nhận diện chuyển động).
- [ ] Tích hợp Logging, xử lý ngoại lệ trung tâm cho các API trên.

**3. Phát triển Frontend (Web App)**
- [ ] Setup Framework (React/Next.js/Vue) hoặc Streamlit cho Prototype nhanh.
- [ ] Xây dựng màn hình Step 1: Upload CCCD mặt trước, mặt sau (kèm loading skeleton & error handling UI).
- [ ] Tích hợp module WebRTC / HTML5 Canvas xin quyền Camera và capture ảnh liên tục (Step 2).
- [ ] Xây dựng màn hình Step 2: Hướng dẫn người dùng quay mặt, chớp mắt và hiển thị real-time box (overlay).
- [ ] Xây dựng màn hình Step 3: Tổng hợp kết quả eKYC (Tick xanh báo thành công hoặc Red alert báo xịt).

## 🔴 Kế hoạch Tương lai (Future / Backlog)
*Ghi chú: Các mục này không thực hiện ngay trong giai đoạn thử nghiệm.*

- **Rate Limiting (Chống DDoS & Hao tổn Token):** Theo dõi IP/User ID để phòng chống giả mạo liên tục.
- **Tối ưu Pipeline On-Premise 100%:** Dần thay thế hoàn toàn phần Quality Check (hiện dùng Gemini) bằng các mô hình AI YOLO/CNN chạy cục bộ.
- **Sửa lỗi chính tả tự động:** Áp dụng mô hình `vietnamese-correction-v2` để xử lý nhiễu text từ OCR.
