# Catalog Module

## Database report — TASK-0072 / 2026-10-09

[TASK-0072](../progress/archive/TASK-0072-database-design-report.md) đã điền chương bảng database trong báo cáo Google Docs theo schema V23: danh sách 48 bảng và 48 bảng chi tiết/907 trường, giữ mẫu native và kiểm tra căn lề, ghim tiêu đề, căn giữa chiều dọc. Đây là cập nhật tài liệu; trạng thái triển khai module vẫn theo code/kiểm thử. PDF/HTML export bị từ chối 403, chưa kiểm bố cục trang.

## Public policies — TASK-0071 / 2026-10-09

[TASK-0071](../progress/archive/TASK-0071-public-use-case-policies-and-coins.md) rút gọn UC12–UC17/UC72–UC73 theo nội dung cần khai, ảnh/video, KTV duyệt và phí trước mở bán; giữ biểu phí/cộng dồn đã chốt. Bài đăng đầu được duyệt và mở bán nhận 10.000 xu một lần; vi phạm nội dung có mức trừ/hạn đăng do KTV quyết định. Chỉ sửa báo cáo, chưa thay code/database V23.

## Database consolidation — TASK-0070 / V23

7 bảng: categories/products/product_categories/product_revisions/product_media/listing_fee_charges/business_policies. Media, revision/AI/moderation history và policy được gộp theo chủ thể/loại dòng; fee receipt nằm payment_confirmations. Concurrent moderation và optimistic locking qua adapter views đã kiểm chứng.

Local og_shop đã migrate/validate V23: 48 bảng nghiệp vụ, 49 gồm Flyway history; 104 nguồn khớp fingerprint trước/sau. Backend entity/join-table dùng schema og_compat. 252 tests: 251 pass, 1 Windows symlink skip; 33 expansion PostgreSQL pass. [Bàn giao V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md), [dictionary 48 bảng](../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md). API/UI/worker canonical còn cần nối; checkpoint V21/baseline dưới đây là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — TASK-0069 / V21

V16: revision/media/checklist/AI-run, lượng khả dụng và moderation proof; phí tin policy/assessment/charge/receipt cộng dồn. ACTIVE UC83 cần duyệt/đủ phí/Seller, legacy giữ provenance. [Bàn giao và ma trận 83 UC](../architecture/DATABASE_EXPANSION_V21_20261009.md). Local og_shop V1–V21 đã migrate/validate; 249 tests, 0 failures/errors, 1 Windows symlink skip; 30 expansion PostgreSQL tests đạt. Đây là nền database và mapping tương thích, chưa nghiệm thu các API/UI mới. Phần Database review và trạng thái baseline cũ bên dưới ghi tình trạng trước TASK-0069.


## Database review — 2026-10-09

Theo [bản rà soát 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), UC12–UC17/UC72–UC73 cần G03/G04/G05: bản tin/media/checklist bất biến, lượng, phí tin cộng dồn và APPROVED_AWAITING_FEE. Media hiện giới hạn một video; service duyệt còn chuyển thẳng ACTIVE. Chưa triển khai các thay đổi này; trạng thái bên dưới là baseline trước rà soát.

- Status: IMPLEMENTED
- Requirements: UC-03, UC-04
- Completion: 2 use cases implemented (UC-03, UC-04), 0/2 verified
- Last reviewed: 2026-09-20

## Purpose and scope

Quản lý category, tin đăng một món hàng, media, tìm kiếm, bộ lọc và trang chi tiết sản phẩm. Cung cấp chức năng quản lý tin đăng cho Người bán và duyệt hàng cho Người mua.

## Out of scope

Reservation, giá offer được chấp nhận, thanh toán, upload Cloudinary và moderation case.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/catalog/`
- `frontend/src/features/marketplace/`
- `frontend/src/features/listing/`
- Flyway tables: `categories`, `products`, `product_media`

## Dependencies

- Identity: thông qua `IdentityCatalogFacade` để kiểm tra quyền Người bán từ database và lấy tóm tắt Người bán công khai (`sellerId`, `displayName`, `trustLabel`). Tuyệt đối không truy cập trực tiếp Identity repository hoặc map `UserEntity` vào Catalog entity.
- Trust & Safety: để ẩn nội dung vi phạm (giai đoạn sau).

## Security invariants

- Chỉ owner hợp lệ sửa tin đăng; nếu truy cập tài nguyên của Seller khác, trả về HTTP 404 để chống ID enumeration.
- Product đang tham gia giao dịch (`RESERVED`, `SOLD`) không bị sửa đổi hay chuyển trạng thái tùy tiện.
- `products.listed_price` là giá Seller niêm yết; hiển thị rõ "Giá niêm yết" trên UI.
- Chống ghi đè đồng thời (Concurrent updates) thông qua JPA Optimistic Locking (`products.version` mapping sang HTTP 409 `PRODUCT_VERSION_CONFLICT`).
- Chợ công khai chỉ hiển thị sản phẩm `ACTIVE` và chưa xóa mềm (`deleted_at IS NULL`).

## Completed tasks

- [x] Tạo package boundary và tài liệu module.
- [x] V3 tách `listed_price` khỏi giá hiển thị và snapshot giá sản phẩm khi mở conversation.
- [x] V5 seed 8 danh mục MVP chuẩn hóa (`electronics`, `fashion`, `home-living`, `books-stationery`, `sports-outdoors`, `collectibles`, `mother-baby`, `other`) với display order ổn định.
- [x] Triển khai `CatalogService` & `CatalogController`: tìm kiếm, lọc danh mục/tình trạng/khoảng giá, sắp xếp theo allowlist, phân trang, và xem chi tiết sản phẩm `ACTIVE`.
- [x] Triển khai `SellerProductService` & `SellerProductController`: quản lý tin đăng, tạo `DRAFT`, sửa tin (`DRAFT`/`ACTIVE`/`HIDDEN`), publish (`DRAFT`/`HIDDEN` -> `ACTIVE`), hide (`ACTIVE` -> `HIDDEN`), khóa không cho sửa khi `RESERVED`/`SOLD`.
- [x] Triển khai `IdentityCatalogFacade` liên module để kiểm tra seller capability và batch lookup tóm tắt người bán.
- [x] Triển khai Frontend Marketplace (`/marketplace`, `/products/:productId`) và Seller Listings (`/seller/listings`, `/seller/listings/new`, `/seller/listings/:productId/edit`).
- [x] Kiểm thử toàn diện Backend (39/39 tests pass), Frontend (24/24 tests pass) và Database tests V1->V5 pass.

## Remaining tasks

- [ ] Tích hợp Cloudinary tải ảnh sản phẩm thật (TASK-0009).
- [ ] Tích hợp Buy Now và reservation chống bán trùng (TASK-0014).
- [ ] Nghiệm thu trọn vẹn UC-03/UC-04 sau khi tích hợp flow mua hàng.

## Expected changes

- Bổ sung adapter upload media sau khi Cloudinary được tích hợp.
- Tích hợp trạng thái `RESERVED` khi kích hoạt luồng Buy Now (TASK-0014).
