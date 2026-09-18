# Luồng hoạt động hệ thống eKYC (Workflow)

Tài liệu này mô tả chi tiết luồng xử lý của hệ thống eKYC, được chia làm 2 giai đoạn chính.

## Giai đoạn 1: Bóc tách & Đánh giá chất lượng CCCD (Document OCR & Quality Check)

Giai đoạn này đảm bảo dữ liệu đầu vào trên giấy tờ là chính xác, hình ảnh rõ nét, không bị che khuất trước khi đi tiếp.

```mermaid
sequenceDiagram
    participant User as Người dùng (Web App)
    participant Backend as Python Backend
    participant YOLO_VietOCR as YOLO + VietOCR (Local)
    participant Gemini as Gemini API

    User->>Backend: Upload ảnh CCCD (Mặt trước & Mặt sau)
    Backend->>Backend: Kiểm tra định dạng (Validate file)
    Backend->>Gemini: Gửi ảnh yêu cầu đánh giá chất lượng (Quality Check)
    Gemini-->>Backend: Trả về cờ Image Quality (Mờ, Lóa, Thiếu góc)
    
    alt Ảnh mờ, lóa, nghiêng hoặc thiếu góc
        Backend-->>User: Hiển thị lỗi rõ ràng ("Ảnh bị mờ/nghiêng/chói sáng"). Yêu cầu chụp lại.
    else Dữ liệu bị thiếu (VD: Không thấy mặt sau)
        Backend-->>User: Hiển thị lỗi ("Vui lòng cung cấp đúng và đủ 2 mặt CCCD").
    else Ảnh đẹp (Pass Quality Check)
        Backend->>YOLO_VietOCR: Gửi ảnh để trích xuất dữ liệu (OCR)
        YOLO_VietOCR-->>Backend: Trả về dữ liệu trích xuất (JSON)
        Backend->>Backend: Chạy thuật toán Validator (Kiểm tra logic ngày sinh, hạn cấp...)
        Backend-->>User: Hiển thị Form điền sẵn toàn bộ thông tin (Thành công Bước 1)
    end
```

## Giai đoạn 2: Xác thực khuôn mặt thời gian thực (Liveness & Face Matching)

Giai đoạn này đảm bảo người đang thao tác trên thiết bị chính là chủ nhân của giấy tờ CCCD. Ứng dụng Web sẽ yêu cầu cấp quyền truy cập Camera.

```mermaid
sequenceDiagram
    participant User as Người dùng (Web App / Camera)
    participant WebClient as Frontend (Trình duyệt)
    participant Backend as Python Backend (OpenCV/Face Matching)
    
    User->>WebClient: Cho phép truy cập Camera
    WebClient->>Backend: Bắt đầu gửi luồng Video/Frame liên tục
    Backend->>Backend: Phân tích Liveness (Kiểm tra người thật hay ảnh giả)
    
    alt Không phải người thật (Spoofing) hoặc lỗi góc máy
        Backend-->>WebClient: Yêu cầu điều chỉnh: "Đưa mặt lại gần", "Giữ thẳng khuôn mặt", "Ánh sáng yếu"...
        WebClient-->>User: Hiện cảnh báo trên màn hình Camera
    else Vượt qua Liveness Check (Người thật)
        Backend->>Backend: Cắt ảnh khuôn mặt thật (Live Face)
        Backend->>Backend: So khớp (Face Matching) với ảnh khuôn mặt trên CCCD (ID Face)
        
        alt Khuôn mặt KHÔNG khớp
            Backend-->>WebClient: Báo lỗi "Khuôn mặt không khớp với giấy tờ"
        else Khuôn mặt KHỚP (Match Score > Threshold)
            Backend-->>WebClient: Xác thực sinh trắc học THÀNH CÔNG!
            WebClient-->>User: Hoàn tất quy trình eKYC.
        end
    end
```

## Cơ chế chống tấn công (DDoS / Lạm dụng) - Dự kiến tương lai
- Ở Bước 1: Nếu một IP hoặc tài khoản liên tiếp gửi ảnh sai/chụp lỗi quá N lần -> Khóa tạm thời 15 phút.
- Ở Bước 2: Nếu xác thực khuôn mặt sai quá N lần -> Khóa tài khoản, yêu cầu liên hệ CSKH.
