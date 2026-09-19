# Source Register

| ID | File | Nguồn | Ngày thêm | Trạng thái | Phạm vi sử dụng |
|---|---|---|---|---|---|
| SRC-01 | `requirements/dac_ta_yeu_cau_luong_thuat_toan_va_use_case.docx` | Chủ dự án | 2026-09-18 | REFERENCE_ONLY | Bản tham khảo ban đầu; không phải requirement cuối cùng và không tự động tạo phạm vi bắt buộc |
| SRC-02 | `requirements/ekyc/ARCHITECTURE.md` | PoC eKYC (`Test`) | 2026-09-18 | APPROVED | Kiến trúc xác thực sinh trắc học và OCR CCCD |
| SRC-03 | `requirements/ekyc/ALGORITHMS.md` | PoC eKYC (`Test`) | 2026-09-18 | APPROVED | Thuật toán YOLO/VietOCR, Gemini post-processing và ArcFace matching |
| SRC-04 | `requirements/ekyc/DATABASE_DESIGN.md` | PoC eKYC (`Test`) | 2026-09-18 | APPROVED | Thiết kế lưu trữ PGVector và mô hình sinh trắc học |
| SRC-05 | `database/docs/second_hand_marketplace_database_design.md` | Database Workspace | 2026-09-18 | APPROVED | Thiết kế logic và physical database C2C marketplace |
| SRC-06 | `database/docs/second_hand_marketplace_database_handoff.md` | Database Workspace | 2026-09-18 | APPROVED | Bối cảnh nghiệp vụ, invariants và luồng giao dịch |
| SRC-07 | `database/docs/database_test_report.md` | Database Workspace | 2026-09-18 | APPROVED | Báo cáo kiểm thử 16 invariant trên PostgreSQL |

## Status values

- `PENDING`: đã nhận nhưng chưa xác nhận nội dung.
- `APPROVED`: được dùng làm nguồn yêu cầu hoặc thiết kế.
- `SUPERSEDED`: đã có bản mới thay thế.
- `REFERENCE_ONLY`: dùng tham khảo, không phải yêu cầu bắt buộc.
