# TASK-0013 — Catalog Vertical Slice: Seller quản lý tin đăng + Buyer duyệt, tìm kiếm và xem chi tiết sản phẩm

- Status: COMPLETE
- Approved: 2026-09-20
- Owner: O.G Shop
- Modules: Catalog, Identity
- Requirements: UC-03, UC-04 (Implemented)

## Mục tiêu

Triển khai vertical slice hoàn chỉnh từ Database -> Backend -> Frontend cho module Catalog:
1. Seed danh mục chuẩn (8 categories) qua Flyway migration `V5__catalog_mvp_categories.sql`.
2. Backend API cho Buyer/Khách:
   - `GET /api/v1/categories` (danh mục active theo `display_order`)
   - `GET /api/v1/products` (danh sách sản phẩm `ACTIVE`, tìm kiếm theo tiêu đề, lọc category/condition/khoảng giá, sắp xếp allowlist, phân trang)
   - `GET /api/v1/products/{productId}` (chi tiết sản phẩm `ACTIVE`)
3. Backend API cho Seller (xác thực qua JWT, kiểm tra capability SELLER từ DB):
   - `GET /api/v1/seller/products`
   - `GET /api/v1/seller/products/{productId}` (cô lập quyền sở hữu, trả 404 chống ID enumeration)
   - `POST /api/v1/seller/products` (tạo `DRAFT`)
   - `PUT /api/v1/seller/products/{productId}` (sửa `DRAFT`, `ACTIVE`, `HIDDEN`; hỗ trợ JPA optimistic locking `@Version`, chặn sửa `RESERVED`/`SOLD`)
   - `POST /api/v1/seller/products/{productId}/publish` (`DRAFT`/`HIDDEN` -> `ACTIVE`)
   - `POST /api/v1/seller/products/{productId}/hide` (`ACTIVE` -> `HIDDEN`)
4. Application Facade giữa Identity và Catalog: `IdentityCatalogFacade` (`DefaultIdentityCatalogFacade`) do Identity sở hữu để Catalog kiểm tra capability người bán từ database, batch lookup thông tin hiển thị của Seller (ID, display name, trust label trung thực: "Người bán MVP") mà không phá vỡ module boundary.
5. Frontend UI:
   - Feature Marketplace: duyệt danh sách sản phẩm, thanh tìm kiếm, bộ lọc, sắp xếp, phân trang, trang chi tiết sản phẩm.
   - Feature Listing: quản lý danh sách tin đăng người bán, tạo tin, sửa tin, publish, hide.
   - Navbar: bổ sung điều hướng "Mua sắm", "Tin đăng của tôi" (nếu là Seller) hoặc CTA kích hoạt bán hàng.

## Phạm vi đã thực hiện

- **Database:**
  - Tạo `V5__catalog_mvp_categories.sql` seed idempotent 8 danh mục MVP (`electronics`, `fashion`, `home-living`, `books-stationery`, `sports-outdoors`, `collectibles`, `mother-baby`, `other`) với thứ tự hiển thị cố định 1..8.
  - Cập nhật `database/tests/database_tests.sql` và runner `run_database_tests.ps1` kiểm tra cả clean migration V1->V5 và legacy backfill V1->V4->V5.
- **Backend:**
  - Tạo contract `IdentityCatalogFacade` và `DefaultIdentityCatalogFacade` tại `modules.identity.application` để Catalog truy vấn seller capability và batch lookup public seller summary mà không truy cập trực tiếp repository hay entity của Identity.
  - Ánh xạ `ProductEntity` với `@Version` cho trường `products.version`, `listed_price`, `currency='VND'`, và các phương thức chuyển trạng thái an toàn.
  - Tạo `CategoryEntity`, `ProductMediaEntity` và các repository tương ứng.
  - Tạo các exception catalog: `ProductNotFoundException` (404), `CategoryNotFoundException` (404), `SellerRequiredException` (403), `ProductStateConflictException` (409), `ProductVersionConflictException` (409).
  - Cập nhật `ApiExceptionHandler` xử lý các exception trên và bắt `OptimisticLockingFailureException` ánh xạ sang 409 `PRODUCT_VERSION_CONFLICT`.
  - Triển khai `CatalogService` & `CatalogController`: tìm kiếm, lọc, sort allowlist, pagination, cache batch seller summary.
  - Triển khai `SellerProductService` & `SellerProductController`: quản lý tin đăng, ownership isolation trả 404 cho tin của seller khác, optimistic locking check.
  - Cập nhật `SecurityConfig.java`: mở public cho categories và products, yêu cầu authenticated cho `/api/v1/seller/products/**`, hỗ trợ HTTP `PUT` trong CORS.
- **Frontend:**
  - Tạo `frontend/src/features/marketplace/`: API client, types, `ProductCard`, `MarketplaceFilters`, `ProductDetailView`.
  - Tạo `frontend/src/features/listing/`: API client, types, `ListingForm`, `SellerListingItem`.
  - Tạo các trang: `MarketplacePage`, `ProductDetailPage`, `SellerListingsPage`, `CreateListingPage`, `EditListingPage`.
  - Cập nhật `App.tsx` với các routes public và protected.
  - Cập nhật `Navbar.tsx`: link "Mua sắm", "Tin đăng của tôi" cho seller, CTA "Đăng ký Bán hàng" cho buyer.
  - Cập nhật `Badge.tsx` và `styles.css` với các biến thể trạng thái sản phẩm (`draft`, `active`, `hidden`, `reserved`, `sold`), responsive grid, styling card, form, mobile layout.
- **Kiểm thử:**
  - 16 Backend unit tests mới (`CatalogServiceTest`, `SellerProductServiceTest`) + cập nhật mock trong `OgShopApplicationTests`.
  - 14 Frontend unit/integration tests mới (`Marketplace.test.tsx`, `Listing.test.tsx`).
  - Database tests V5 trên PostgreSQL.

## Bằng chứng kiểm chứng

1. **Database Migration & Invariant Tests:**
   - Lệnh: `powershell.exe -ExecutionPolicy Bypass -File database/run_database_tests.ps1 -Reset`
   - Kết quả: **PASS**. Clean migration V1->V5, database invariants và legacy V1->V4->V5 backfill đạt 100%.
2. **Backend Tests:**
   - Lệnh: `.\mvnw.cmd test`
   - Kết quả: **BUILD SUCCESS**, **39/39 tests PASS** (100%).
3. **Frontend Tests:**
   - Lệnh: `npm.cmd test`
   - Kết quả: **PASS**, **6/6 test files, 24/24 tests PASS** (100%).
4. **Frontend Typecheck:**
   - Lệnh: `npm.cmd run typecheck`
   - Kết quả: **0 lỗi** (TypeScript pass).
5. **Frontend Linting:**
   - Lệnh: `npm.cmd run lint`
   - Kết quả: **0 errors, 0 warnings** (`eslint . --max-warnings=0`).
6. **Frontend Production Build:**
   - Lệnh: `npm.cmd run build`
   - Kết quả: **PASS** (Vite build thành công trong 176ms).
7. **Tài liệu & Architecture:**
   - Tạo `docs/api/CATALOG_API.md`.
   - Cập nhật `docs/modules/catalog.md`, `docs/modules/identity.md`, `docs/modules/INDEX.md`, `docs/progress/STATUS.md`, `docs/progress/BACKLOG.md`, `docs/requirements/TRACEABILITY_MATRIX.md`.
