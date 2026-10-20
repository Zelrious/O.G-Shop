# Catalog Module

## Hiển thị Giá Offer đã Chấp thuận trên Mall — TASK-0099 / 2026-10-20

- `CatalogDtos`: Bổ sung `agreedPrice` và `acceptedOfferId` vào `ProductSummaryResponse` và `ProductDetailResponse`.
- `CatalogService`: Tiêm `NegotiatedCheckoutFacade`, nhận `Long buyerId` từ JWT; khi `buyerId` khác `sellerId`, làm giàu `agreedPrice` và `acceptedOfferId` từ offer `ACCEPTED` còn hiệu lực. Giữ nguyên `listed_price` và thuật toán tìm kiếm/sắp xếp theo giá niêm yết.
- `CatalogController`: Nhận `@AuthenticationPrincipal Jwt jwt` để chuyển tiếp `buyerId` xuống service.
- UI Mall/Detail: Hiển thị giá thỏa thuận, huy hiệu "Giá của bạn", gạch ngang giá niêm yết và link trực tiếp sang checkout kèm `offerId`.

## KTV UI system — TASK-0094 / 2026-10-13

Thêm shared Select/Pagination/PageHeader; token surface/popover/disabled và semantic badge/button, sửa contrast card eKYC/khiếu nại, custom voucher/filter/page-size dropdown, phân trang 0 kết quả. Giữ sidebar và tất cả API/handler nghiệp vụ. 195 full-suite pass + 2 eKYC tests mới; final focused26, lint/TypeScript/build đạt. Browser48 geometry +8 contrast +2 dropdown checks, không overflow/JS error; sampled contrast light4,65/dark5,76. [Báo cáo và giới hạn](../../output/ui-0094/README.md).

## Hero/product contrast — TASK-0092 / 2026-10-13

Hero chia intro/search desktop, thu gọn386,9→234,4px ở1440 (39,4%), mobile highlight2×2. Product title/price/seller/location/condition/hover thay màu cố định bằng theme tokens; dark title/price tương phản12,83:1. Giữ catalog/search API và nội dung listing. [Bằng chứng và giới hạn](../../output/ui-0092/README.md).

## Marketplace/listing UI — TASK-0091 / 2026-10-13

Home/marketplace/product detail và Seller listing/form dùng outline icon/surface sáng-tối. Liên hệ người bán mở chat nhanh desktop hoặc hội thoại mobile; permission/price/media/workflow hiện hành giữ từ API. Không đổi schema/service. [Frontend183/39, full lint/build và ảnh](../../output/ui-0091/README.md).

## Chat/offer V31 — TASK-0090 / 2026-10-13

ConversationCatalogFacade đọc/lock product context, quantity_available, version/public revision. Snapshot offer thay đổi tin sẽ không được accept/checkout; public price giữ nguyên. Adapter trong infrastructure, contract application; không truy cập repository chéo.

[API](../api/CHAT_OFFER_API.md), [thiết kế/rollout](../architecture/CHAT_OFFER_V31_20261013.md), [test guide/ảnh](../../output/phase34-runtime/README.md). Năm task marketplace hoàn tất trong phạm vi hiện hành; app DB V31/47 bảng, backup/preservation150 nguồn đạt, runtime5174/8080 sẵn sàng. Các checkpoint dưới giữ lịch sử.

## Quản lý user KTV — TASK-0088 / 2026-10-12

SellerProductService/ListingWorkflowService gọi Identity facade trước create/edit/media/submit/publish/pay-to-open; quyền đọc/ẩn tin cũ giữ. V28/V29 kiểm thêm ở SQL và serialize với quyết định KTV. Không thêm biện pháp tự ẩn listing đang bán; checkout chặn seller không khả dụng. Hồi quy moderation/category/fee và negative SQL giữ lịch sử đợt 1. [Thiết kế/rollout](../architecture/ACCOUNT_GOVERNANCE_V30_20261012.md), [API](../api/ACCOUNT_MANAGEMENT_API.md), [Kiểm chứng](../../output/phase2-runtime/README.md). Các checkpoint dưới đây giữ lịch sử.

## Kiểm duyệt và phí đăng — TASK-0087 / 2026-10-12

Đã nối DTO đầy đủ và UI KTV danh sách–chi tiết, history, role/version/key/locking; duyệt có fee assessment theo immutable MANUAL_V27, chuyển chờ phí rồi mở bán. Sửa nội dung/media đang bán ẩn tin và cần xét lại; phí bổ sung bảo toàn receipt cũ. Seller có trang kết quả và modal mock fee, owner API. [Thiết kế/rollout](../architecture/MODERATION_LISTING_V27_20261012.md), [API](../api/CATALOG_API.md), [Ảnh và kiểm chứng](../../output/phase1-runtime/README.md). PostgreSQL gồm stale/retry/rollback/race/tổng giá×quantity và tăng/giảm/ngưỡng; UI không suy diễn dữ liệu thiếu. UC73/phần UC15/UC17 đã kiểm local; AI UC16/full eKYC/quantity checkout không thuộc đợt 1, V27 chưa apply app DB. Các checkpoint dưới đây là lịch sử.

## Diagram báo cáo — TASK-0081 / 2026-10-10

[UC01–UC18 đã có 36 ảnh kiểm nguồn/native/PDF](../requirements/use-cases-83/report-uc01-18/README.md), gồm UC12–UC17, công khai/media, AI hỗ trợ và duyệt/phí. Thay 34 ảnh được duyệt và thêm hai ảnh UC11, giữ Hình 3.3–3.38 và ảnh ngoài phạm vi. 2FA chỉ dành cho Người mua/Người bán theo quyết định người dùng. Đây là cập nhật yêu cầu/diagram; không nâng trạng thái code/schema/nghiệm thu module.

## Report diagrams — TASK-0079 / 2026-10-10

[UC19–UC83 đã có đủ 130 Activity/Sequence trong báo cáo](../requirements/use-cases-83/report-from-uc19/README.md), kiểm native/PDF trọn trang. RS-02/RS-05 được chốt và đồng bộ theo xác nhận người dùng; chuẩn Activity/Sequence gọn được giữ. Đây là tài liệu, không nâng trạng thái code/database/nghiệm thu module.

## Diagram presentation — TASK-0077 / 2026-10-10

Mã Activity/Sequence hiện dùng [cách trình bày gọn](../requirements/use-cases-83/diagram-presentation/README.md): bỏ legend khỏi ảnh, rút nhãn và giữ đủ đường đi theo đặc tả. Kiểm nguồn/1.000 đường đi và render 166 PNG đạt; ảnh UC79–UC83 đã kiểm trọn một trang trên PDF. Đây là cập nhật trình bày tài liệu.

## Current use-case documents — TASK-0075 / 2026-10-10

[Bộ 83 đặc tả/166 Activity–Sequence](../requirements/use-cases-83/README.md) đã đọc lại trực tiếp báo cáo và viết theo 830 trường hiện hành; [rà soát](../requirements/use-cases-83/REVIEW.md) ghi các điểm nguồn cần làm rõ. UC12–UC17/UC73 giữ điều kiện công khai/media, cảnh báo AI, duyệt trước và đủ phí trước mở bán; phí tăng tính phần còn thiếu. Đây là yêu cầu và diagram ở mức yêu cầu; trạng thái code/schema/nghiệm thu của module không được nâng từ kết quả tài liệu. [Bàn giao](../handoff/USE_CASE_83_AGENT_HANDOFF.md).

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
