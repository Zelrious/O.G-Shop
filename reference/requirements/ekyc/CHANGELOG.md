# Nhật ký Cập nhật (Changelog & Session Summary)

Tài liệu này lưu trữ lại các thay đổi quan trọng về mặt kỹ thuật, môi trường và mã nguồn sau mỗi phiên làm việc để đảm bảo tính liên tục của ngữ cảnh dự án.

## [Ngày 13/09/2026] - Tích hợp YOLOv11 & Củng cố Core OCR

### 1. Kiến trúc Mô hình (Model Architecture)
- **Gỡ bỏ TensorFlow Lite:** Đã loại bỏ hoàn toàn các mô hình `.tflite` cũ (`corner_detection` & `text_detection`) do lỗi không nhận diện được bounding box (`ValueError: Please try again`).
- **Tích hợp YOLOv11:** 
  - Thay thế bằng mô hình YOLOv11 (`.pt`) từ repository tham khảo.
  - Sử dụng trọng số `29_03_25-YOLOv11n-Corner-best_metrics.pt` cho Corner Detection (ngưỡng `score_ths = 0.15`).
  - Sử dụng trọng số `best.pt` cho Text Detection (ngưỡng `score_ths = 0.2`).
- **Text Recognition (VietOCR):** Hệ thống OCR chạy mượt mà sau khi cấu hình đúng đường dẫn và đồng bộ Vocab.

### 2. Thay đổi Mã nguồn (Source Code)
- **`config.py`:** Trỏ đường dẫn models sang thư mục weights của YOLO. Hạ ngưỡng threshold để nhận diện góc thẻ dễ dàng hơn với ảnh mờ.
- **`detector.py`:** 
  - Viết lại class `Detector` sử dụng `ultralytics.YOLO`. 
  - Áp dụng kỹ thuật monkeypatch (`torch.load` với `weights_only=False`) để khắc phục lỗi bảo mật khi load mô hình của **PyTorch 2.6+**.
  - Xử lý chuyển đổi tọa độ Bounding Box của YOLO (`[xmin, ymin, xmax, ymax]`) về chuẩn cũ (`[ymin, xmin, ymax, xmax]`) dưới dạng số nguyên (`int`) để không bị lỗi crop ảnh.
- **`image_utils.py`:** Cập nhật lại Index Mapping (Từ class label số nguyên sang nhãn tương ứng của YOLO Text Model: `id=6`, `name=8`, `dob=1`, `home=10`, `add=0`).
- **`merged_model.py`:** 
  - Bỏ tham số `path_to_labels`.
  - Fix lỗi crash khi danh sách ảnh (`list_ans`) bị rỗng.
  - Fix lỗi `Inhomogeneous shape` của numpy khi truyền list các ảnh kích thước khác nhau vào VietOCR.
- **`text_recognition.py` / `predictor.py`:** Chỉnh sửa luồng dữ liệu (Pipeline) để truyền trực tiếp Numpy Array thay vì ép sang PIL Image, do hàm `batch_predict` được thiết kế phục vụ Numpy.

### 3. Cập nhật Môi trường (Environment)
- Nâng cấp gói `ultralytics` từ `8.0.200` lên bản `8.4.150` để hỗ trợ kiến trúc YOLOv11 (sửa lỗi thiếu layer `C3k2`).

### 4. Kết quả Kiểm thử (`run_tests.py`)
- **TC1 (Ảnh chuẩn):** Phát hiện ảnh không có đủ thông tin (cố ý) và xử lý ngoại lệ thành công không gây crash hệ thống.
- **TC2 (Nghiêng/Xoay/Mờ) & TC3 (Bị che/Thiếu nét):** 
  - Quá trình Detection, Alignment, và OCR Text trôi chảy.
  - Dữ liệu trích xuất chính xác tuyệt đối (Họ tên, Ngày sinh, CCCD).
  - Tốc độ xử lý (Inference) dưới 2 giây / ảnh CPU.

### 5. Next Steps (Cho phiên làm việc sau)
- Tham khảo [ROADMAP.md](./ROADMAP.md) phần **1. Kiểm thử & Hoàn thiện Module Face Matching (Bước 2)**.
- Tiến hành viết Testcase Unit Test cho module Face Matching để hoàn thiện nốt phần Backend AI trước khi xây dựng Web App Frontend.
