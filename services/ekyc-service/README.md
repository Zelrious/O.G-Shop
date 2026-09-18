# O.G Shop - eKYC AI Microservice

Microservice xử lý trí tuệ nhân tạo chuyên biệt cho quy trình **Xác thực Danh tính Người bán (eKYC)** trên nền tảng O.G Shop.

## 1. Kiến trúc Thuật toán Cốt lõi

Hệ thống kết hợp đa tầng các mô hình Deep Learning tiên tiến nhất cho bài toán eKYC Việt Nam:

```
[Ảnh CCCD Upload]
       │
       ▼
[1. Kiểm tra chất lượng ảnh] ──► Toán tử Laplacian: Var(∇² I) ≥ 10.0 (chống ảnh mờ)
       │
       ▼
[2. Định vị 4 góc thẻ]       ──► YOLOv11n Corner Detector + Homography Warp Perspective
       │
       ▼
[3. Định vị vùng văn bản]    ──► YOLOv11n Text Bounding Box Detector
       │
       ▼
[4. Nhận dạng ký tự tiếng Việt] ─► VietOCR (VGG Backbone + Transformer Encoder-Decoder, 151MB)
       │
       ▼
[5. Hậu xử lý & Chuẩn hóa]   ──► Rule-based 12 chữ số + Google Gemini Flash (temperature=0.1)
       │
       ▼
[6. Bóc tách khuôn mặt CCCD]  ──► RetinaFace Detector + DeepFace ArcFace ──► Vector 512-d (Card Embedding)
```

```
[Khung hình Camera WebRTC (Live Frame)]
       │
       ▼
[1. Lọc khuôn mặt Live]      ──► OpenCV Backend (< 20ms) + Kiểm tra tâm & kích thước vòng tròn
       │
       ▼
[2. Trích xuất đặc trưng]    ──► DeepFace ArcFace ──► Vector 512-d (Live Embedding)
       │
       ▼
[3. So khớp khoảng cách]     ──► Cosine Distance: d = 1 - (u · v) / (||u|| * ||v||)
       │
       ▼
[4. Đưa ra quyết định]       ──► Ngưỡng Strict Threshold: d ≤ 0.50 ──► MATCHED / NOT_MATCHED
```

---

## 2. Danh sách REST API Endpoints

Service chạy tại cổng `8001` (FastAPI + Uvicorn):

### 1. `GET /api/v1/ekyc/health`
Kiểm tra sức khỏe và thông tin các thuật toán đang nạp trong bộ nhớ.

### 2. `POST /api/v1/ekyc/ocr`
Bóc tách toàn diện thẻ CCCD:
- **Đầu vào**: `multipart/form-data` (`file: UploadFile`) hoặc `Form` (`image_base64: str`).
- **Đầu ra**:
  ```json
  {
    "status": "SUCCESS",
    "extracted_data": {
      "so_cccd": "052206008387",
      "ho_va_ten": "HUỲNH LONG BẢO KHANH",
      "ngay_sinh": "21/02/2006",
      "gioi_tinh": "Nam",
      "quoc_tich": "Việt Nam",
      "que_quan": "Tổ 17, Khu Vực 3, Lê Hồng Phong, TP. Quy Nhơn, Bình Định",
      "noi_thuong_tru": "Xã Tam Quan Nam, TX. Hoài Nhơn, Bình Định"
    },
    "image_quality_check": {
      "is_clear": true,
      "rejection_reason": ""
    },
    "card_face_embedding": [0.0123, -0.0456, ... 512 giá trị float ...]
  }
  ```

### 3. `POST /api/v1/ekyc/extract-card-face`
Trích xuất vector 512 chiều từ ảnh thẻ CCCD bằng mô hình RetinaFace và ArcFace.

### 4. `POST /api/v1/ekyc/match-face`
So khớp khuôn mặt giữa chân dung CCCD và khuôn mặt chụp từ webcam:
- **Đầu vào (JSON)**:
  ```json
  {
    "card_embedding": [ ... 512 floats ... ],
    "live_frame_base64": "data:image/jpeg;base64,...",
    "circle_center": [320, 240],
    "circle_radius": 140
  }
  ```
- **Đầu ra**:
  ```json
  {
    "is_match": true,
    "distance": 0.2458,
    "confidence_score": 0.8361,
    "threshold_used": 0.50,
    "match_status": "MATCHED",
    "user_instruction": "Giữ nguyên khuôn mặt..."
  }
  ```

---

## 3. Khởi động Microservice

Khởi động trực tiếp bằng Python:
```powershell
# Chạy từ thư mục gốc dự án:
python services/ekyc-service/start.py

# Hoặc dùng uvicorn trực tiếp:
uvicorn services.ekyc-service.app:app --port 8001
```

Tài liệu Swagger UI tương tác trực quan:
👉 `http://localhost:8001/docs`
