# Task: TASK-0004 - Tích hợp Thuật toán Lõi eKYC Microservice (Bước 1)

## Trạng thái
- Tình trạng: COMPLETED
- Bắt đầu: 2026-09-18
- Hoàn thành: 2026-09-18
- Mục tiêu: Đóng gói và tích hợp thuật toán AI eKYC thực tế từ `D:\Khanh\Đồ án 1\Test` thành FastAPI microservice độc lập (`services/ekyc-service`), kết nối trực tiếp với Frontend WebRTC.

## Phạm vi thực hiện
1. **Thuật toán OCR CCCD**:
   - Laplacian variance check ($\ge 10.0$) phát hiện ảnh mờ.
   - YOLOv11n Corner Detection + Homography Warp Perspective nắn thẳng thẻ.
   - YOLOv11n Text Detection + VietOCR Transformer (151MB) nhận dạng tiếng Việt có dấu.
   - Google Gemini Flash hậu xử lý chính tả địa danh và dấu câu.
   - Trích xuất đồng thời chân dung thẻ CCCD qua RetinaFace $\to$ ArcFace vector 512-d.
2. **Thuật toán Sinh trắc học Biometrics**:
   - Phát hiện mặt thời gian thực qua OpenCV backend (< 20ms) trên khung hình camera live, tự động chuyển đổi giữa crop mặt và full-frame.
   - DeepFace ArcFace sinh vector đặc trưng 512 chiều chuẩn hóa $L_2$.
   - Tính khoảng cách Cosine $d = 1 - \frac{\mathbf{u} \cdot \mathbf{v}}{\|\mathbf{u}\|_2 \|\mathbf{v}\|_2}$.
   - Áp dụng ngưỡng Strict Threshold $\le 0.50$ đưa ra quyết định MATCHED / NOT_MATCHED (kiểm thử thực tế đạt $d = 0.0029$, confidence $99.61\%$).
3. **Loại bỏ các thành phần không bổ trợ**:
   - Bỏ qua GUI Tkinter cũ `gui_app.py`, `camera_test.py`.
   - Bỏ qua thư mục `Testcase/` tĩnh nặng hàng trăm MB.
   - Bỏ qua thư mục `references/` (chỉ sao chép 2 tệp trọng số `.pt` 5.2MB vào `cccd_module/ocr_core/weights/`).
4. **Kết nối Frontend**:
   - `verificationApi.ts` gọi FastAPI endpoints (`/api/v1/ekyc/ocr`, `/api/v1/ekyc/match-face`).
   - `CccdUploader.tsx` và `CameraCapture.tsx` kết nối trực tiếp, hiển thị huy hiệu AI Thật và độ tin cậy.

## Kết quả kiểm chứng thực tế
- [x] Typecheck Frontend (`npm run typecheck` - 0 lỗi)
- [x] Lint Frontend (`npm run lint` - 0 warning, 0 error)
- [x] Unit test Frontend (`npm test` - 5/5 passed)
- [x] Production build Frontend (`npm run build` - 0 error)
- [x] Test model OCR + ArcFace đầu cuối trên ảnh thật (`2.jpg`) thành công: OCR bóc tách đầy đủ họ tên `HUỲNH LONG BẢO KHANH`, số CCCD `052206008387`, trích xuất ArcFace vector 512-d.
- [x] Test so khớp khuôn mặt đầu cuối: $d = 0.0029 \le 0.50$, Confidence $= 99.61\%$, Trạng thái MATCHED.
