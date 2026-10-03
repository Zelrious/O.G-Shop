# Catalog Module

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
