# Định chuẩn Đầu ra Hệ thống eKYC (Output Standards)

Tài liệu này quy định cấu trúc dữ liệu JSON giao tiếp giữa các module của hệ thống (giữa AI, Backend và Frontend).

## 1. Đầu ra của Module CCCD OCR (Gemini)

Cần nâng cấp Prompt hiện tại để AI trả về thêm một object `image_quality_check` bên cạnh dữ liệu truyền thống.

```json
{
  "status": "SUCCESS | ERROR | RETRY_REQUIRED",
  "error_message": "Chỉ có nội dung nếu status = ERROR",
  
  "image_quality_check": {
    "is_clear": true,
    "is_full_document_visible": true,
    "is_glare_free": false,
    "rejection_reason": "Ảnh bị lóa sáng mạnh ở phần nơi thường trú. Vui lòng chụp lại."
  },

  "extracted_data": {
    "so_cccd": "052206008387",
    "ho_va_ten": "HUỲNH LONG BẢO KHANH",
    "ngay_sinh": "21/02/2006",
    "gioi_tinh": "Nam",
    "quoc_tich": "Việt Nam",
    "que_quan": "Tam Quan Nam, TX. Hoài Nhơn, Bình Định",
    "noi_thuong_tru": "Tổ 17 Khu Vực 3 Lê Hồng Phong, TP. Quy Nhơn, Bình Định",
    "ngay_het_han": "21/02/2031",
    "dac_diem_nhan_dang": "sẹo chấm C 1,2 cm trên sau cánh mũi phải",
    "ngay_cap": "13/05/2021",
    "co_quan_cap": "CỤC TRƯỞNG CỤC CẢNH SÁT QUẢN LÝ HÀNH CHÍNH VỀ TRẬT TỰ XÃ HỘI"
  }
}
```

## 2. Đầu ra của Module Face Matching

Khi luồng video từ Client gửi lên Backend, Backend sẽ trả về trạng thái của từng Frame (Realtime) để Frontend hướng dẫn người dùng.

```json
{
  "frame_status": "FACE_NOT_FOUND | TOO_FAR | TOO_CLOSE | NOT_LOOKING_STRAIGHT | PROCESSING",
  "user_instruction": "Vui lòng đưa mặt lại gần camera hơn"
}
```

Khi đạt đủ điều kiện ảnh tốt (Face Liveness pass), Backend sẽ tiến hành Match và trả về kết quả cuối cùng:

```json
{
  "match_status": "MATCHED | NOT_MATCHED",
  "confidence_score": 0.98,
  "threshold_used": 0.85,
  "liveness_passed": true,
  "final_decision": "Xác thực danh tính thành công."
}
```
