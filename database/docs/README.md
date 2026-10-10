# Database design documents

## Current database — V24 / 47 business tables

[Dictionary V24](DATABASE_TABLE_DICTIONARY_V24.md) là nguồn hiện hành cho 47 bảng/895 trường, PK/FK và mapping 103 nguồn còn giữ. [Bàn giao V24](../../docs/architecture/DATABASE_AUTH_SIMPLIFICATION_V24_20261009.md) ghi việc bỏ refresh sessions, kiểm chứng dữ liệu và cập nhật báo cáo. `auth_challenges` chưa bị bỏ; các đề xuất gộp eKYC chưa triển khai trong đợt này.

## Historical database — V23 / 48 business tables

[Dictionary V22](DATABASE_TABLE_DICTIONARY_V22.md) liệt kê đủ 48 bảng, thuộc tính, kiểu, PK/FK, loại dòng và mapping 104 nguồn. [Bàn giao V22](../../docs/architecture/DATABASE_CONSOLIDATION_V22_20261009.md) mô tả việc gộp, kiểm chứng, backup và giới hạn lớp chuyển tiếp tại checkpoint V23. Tài liệu V21 và các baseline phía dưới giữ lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion V21

[Bàn giao và ma trận 83 UC](../../docs/architecture/DATABASE_EXPANSION_V21_20261009.md) là mô tả hiện hành cho V15–V21. Các SQL/design/handoff baseline trong thư mục này là tài liệu lịch sử; DDL chuẩn vẫn nằm ở backend Flyway, không chạy schema snapshot cũ đè runtime.


Ghi ERD, ownership bảng, invariant, index, chính sách lưu giữ và quyết định migration ở đây. Khi chủ dự án đưa schema đã duyệt vào repository, đăng ký nguồn tại `reference/SOURCE_REGISTER.md` trước khi tạo baseline.

Dữ liệu giả để test UI/logic trên V1–V8 nằm ở [database/seed](../seed/README.md), gồm bốn tài khoản và fixtures Profile/Catalog/voucher/order/payment mock, có sản phẩm thuộc nhiều danh mục. Seed tách khỏi Flyway; V8 là migration chuẩn cho quan hệ nhiều danh mục, các tài liệu thiết kế baseline cũ chưa mô tả quan hệ này.
