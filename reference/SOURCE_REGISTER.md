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
| SRC-08 | [Báo cáo đồ án — Bản sao của Thẻ 1](https://docs.google.com/document/d/1mbj6vIkLgNM0mzdy-xRukPfz2r0vN_kk5jvKx_QToKQ/edit?tab=t.goqi7hu9cf5) / [snapshot](../docs/requirements/use-cases-83/SOURCE_SNAPSHOT.json) | Chủ dự án chỉ định nguồn trong TASK-0075 | 2026-10-10 | APPROVED | Căn cứ 83 đặc tả/diagram theo bản đọc hiện hành; ghi chú mâu thuẫn/thiếu rõ trong REVIEW, không tự nghiệm thu code/database |

TASK-0079 đọc lại SRC-08 trước khi chèn UC19–UC83, ghi quyết định RS-02/RS-05 đã được người dùng duyệt trong [checkpoint nguồn và diagram](../docs/requirements/use-cases-83/report-from-uc19/README.md). Snapshot TASK-0075 được giữ lịch sử.

TASK-0080 đọc mới chương/caption và tên UC của SRC-08, chuẩn hóa 322 caption theo chương và mẫu tiếng Việt; [mapping/bằng chứng hiện tại](../docs/requirements/use-cases-83/chapter-captions/README.md). Giữ nội dung yêu cầu/source diagram/ảnh; danh mục đầu báo cáo còn template. Snapshot TASK-0075 và bằng chứng chèn TASK-0079 không thay mapping caption mới.

TASK-0081 đọc mới SRC-08 trước khi kiểm/chèn UC01–UC18, ghi quyết định RS-03 (2FA chỉ Người mua/Người bán) và ba sửa đổi nguồn đã duyệt; [nguồn hiện hành và bằng chứng](../docs/requirements/use-cases-83/report-uc01-18/README.md). Thay 34 ảnh/thêm hai UC11 được duyệt, giữ caption theo TASK-0080. Snapshot ban đầu giữ lịch sử.

## Status values

- `PENDING`: đã nhận nhưng chưa xác nhận nội dung.
- `APPROVED`: được dùng làm nguồn yêu cầu hoặc thiết kế.
- `SUPERSEDED`: đã có bản mới thay thế.
- `REFERENCE_ONLY`: dùng tham khảo, không phải yêu cầu bắt buộc.
