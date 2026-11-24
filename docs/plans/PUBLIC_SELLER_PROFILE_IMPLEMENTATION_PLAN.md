# Kế hoạch triển khai: Hồ sơ người bán công khai (Public Seller Profile)

> **Trạng thái tài liệu:** `IMPLEMENTED — REBASED ON origin/main (9e4a610), MIGRATION BUMPED TO V60`  
> **Ngày hoàn thiện:** 2026-11-24 (cập nhật 2026-11-25: sau khi rebase lên `origin/main` PR #23 đã dùng `V59__seller_buyer_block_scopes.sql`, migration của tính năng này được đổi sang **`V60__add_public_avatar_consent_and_seller_profile_indexes.sql`**)  
> **Phạm vi nhiệm vụ:** Read-only investigation & plan finalization (không thay đổi mã nguồn ứng dụng, migration hay dữ liệu DB).  
> **Ký hiệu trạng thái bằng chứng:**
> - `[VERIFIED]` — Đã xác minh trực tiếp từ source code, Flyway migration, PostgreSQL schema hoặc tài liệu UC trong repository.
> - `[PROPOSED]` — Đề xuất kỹ thuật chưa triển khai.
> - `[UNVERIFIED]` — Chưa có bằng chứng xác minh trực tiếp.
> - `[OPEN]` — Cần quyết định nghiệp vụ từ Product Owner (PO) trước khi triển khai.

---

## 1. Executive Summary

Tính năng **Hồ sơ người bán công khai (Public Seller Profile)** là một tính năng bổ sung đề xuất (không có Use Case riêng trong bộ `UC01`–`UC83` `[VERIFIED]`) nhằm cung cấp trang xem công khai (`/sellers/:sellerId`) cho Khách và Người mua khi đánh giá độ tin cậy của Người bán.

Rà soát thực tế codebase và PostgreSQL schema đã phát hiện **6 điểm quan trọng cần hiệu chỉnh so với kế hoạch sơ bộ**:
1. **Database Schema `[VERIFIED]`**: Không tồn tại bảng `seller_settings`. Cấu hình người bán thực tế được lưu trong bảng `public.seller_profiles` (view tương thích `og_compat.seller_profiles`). Migration mới nhất đã áp dụng là **`V58`** → phiên bản tiếp theo là **`V59`**.
2. **Rò rỉ PII & sai lệch logic thống kê ở `OrderReviewService` hiện có `[VERIFIED]`**: `GET /api/v1/commerce/orders/users/{userId}/reviews` hiện trả `orderId`, `reviewerId`, tên thật `reviewerName`, không lọc `deleted_at IS NULL`, không tách đánh giá nhận với tư cách Người bán vs Người mua, và mặc định điểm trung bình là `5.0` khi chưa có đánh giá. Public API mới **bắt buộc dùng DTO độc lập**, ẩn danh ở Backend và tính tổng hợp chính xác.
3. **Trạng thái tài khoản phức tạp hơn một cờ `active` `[VERIFIED]`**: Hệ thống có đồng thời `users.status`, `users.governance_state` (`ACTIVE`, `LOCKED`, `DEACTIVATED`), `users.governance_until`, `users.deleted_at`, và `users.governance_restrictions`. Cần PO chốt chính sách hiển thị khi Seller ở trạng thái `LOCKED` tạm thời hoặc bị hạn chế (`RESTRICTED`).
4. **Định nghĩa `Completed Transactions Count` `[OPEN]`**: Hệ thống có cả `orders` và `order_items` (1 đơn hàng có thể chứa nhiều `order_items` cùng một Người bán). Cần PO chốt đếm theo `orders` (`COMPLETED`) hay theo `order_items`/số lượng.
5. **Ranh giới module & tránh circular dependency `[VERIFIED]`**: `catalog` đang phụ thuộc `identity`, `trustsafety` phụ thuộc `commerce`. Do đó không đặt Public Seller Profile aggregation trong `identity` mà đề xuất đặt Read-Model/Facade tại module `platform` (theo [MODULE_BOUNDARIES.md](../../docs/architecture/MODULE_BOUNDARIES.md)).
6. **Hardcode mock stats trên Product Detail `[VERIFIED]`**: [ProductDetailView.tsx](../../frontend/src/features/marketplace/components/ProductDetailView.tsx) (dòng 525–531) hiện hardcode `5.0 (18 đánh giá) • 12 sản phẩm đang bán • 8 đã giao dịch` và chưa gắn link sang trang hồ sơ người bán.

---

## 2. Verified Repository Baseline

| Hạng mục | Giá trị xác minh | Trạng thái |
|---|---|---|
| **Git Branch** | `integration/main-runtime-remediation` | `[VERIFIED]` |
| **Git HEAD** | `02c95ac` (`feat(return-shipment): implement 3-stage GHN return lifecycle, seller visibility, timeline & tabs`) | `[VERIFIED]` |
| **Working Tree trước nhiệm vụ** | Sạch (không có file modified/untracked) | `[VERIFIED]` |
| **Migration mới nhất trong repo** | `V58__allow_points_on_shipping_and_voucher_priority.sql` | `[VERIFIED]` |
| **Migration mới nhất trên DB `og_shop`** | `version = 58`, `success = t` (bảng `flyway_schema_history`) | `[VERIFIED]` |
| **Tài liệu kế hoạch sẵn có trong `docs/plans/`** | Chỉ có `STEP-2-EXTERNAL-INTEGRATIONS-IMPLEMENTATION-PLAN.md` (chưa có plan cho Public Seller Profile) | `[VERIFIED]` |
| **Context path & API prefix** | `/api/v1` (theo `API_CONVENTIONS.md` và `SecurityConfig.java`) | `[VERIFIED]` |

---

## 3. Use Case Mapping

Qua rà soát toàn bộ 83 đặc tả `docs/report/use-cases/UC01.md` – `UC83.md`, **không có UC riêng** cho Hồ sơ người bán công khai `[VERIFIED]`. Các UC giao cắt trực tiếp:

| UC | Tên Use Case | Vai trò liên quan & Ràng buộc bắt buộc | Trạng thái |
|---|---|---|---|
| **UC05** | Quản lý hồ sơ | Nguồn `full_name` và `avatar_url` trong `public.users`. Không lộ CCCD, ngân hàng, địa chỉ riêng. | `[VERIFIED]` |
| **UC13** | Xem chi tiết sản phẩm | Điểm vào chính (`/products/:productId`). `BR-UC13-02`: chỉ hiển thị thông tin công khai; không hiển thị CCCD, ngân hàng, địa chỉ riêng. Xem không cần đăng nhập. | `[VERIFIED]` |
| **UC17** | Quản lý tin đăng | `BR-UC17-05`: tin ẩn, khóa, hết hàng không mở bán. `BR-UC17-08`: đơn cũ giữ snapshot riêng; lịch sử bán tách khỏi vòng đời bài đăng. | `[VERIFIED]` |
| **UC61** | Đánh giá đối tác sau giao dịch | Nguồn đánh giá 1–5 sao & nhận xét. `BR-UC61-03`: nhận xét công khai không chứa địa chỉ riêng, ngân hàng, bằng chứng riêng tư. `BR-UC61-04`: số sao trung bình hiển thị riêng với điểm uy tín. | `[VERIFIED]` |
| **UC11** | Xem điểm uy tín và lịch sử xử phạt | `BR-UC11-01`: chỉ chủ tài khoản và KTV xem lịch sử xử phạt. **Tuyệt đối không công khai** lịch sử xử phạt/điểm phạt trên trang hồ sơ người bán. | `[VERIFIED]` |

---

## 4. Confirmed Business Rules

Các quyết định nghiệp vụ đã được PO thống nhất (bắt buộc giữ nguyên):

1. **Thông tin công khai**:
   - Tên hiển thị hoặc tên gian hàng (`store_name`, fallback `full_name`).
   - Trạng thái xác minh eKYC dưới dạng badge (`Đã xác minh eKYC` / `Chưa xác minh eKYC`).
   - Ảnh đại diện **chỉ khi** Người bán đã opt-in (`allow_public_avatar = true`).
   - Điểm sao trung bình & tổng số đánh giá hợp lệ.
   - Danh sách nhận xét hợp lệ, **đã ẩn danh người mua**.
   - Tổng số giao dịch hoàn tất.
   - Danh sách sản phẩm đang mở bán theo `UC17`.
2. **Thông tin tuyệt đối không công khai**: Email, SĐT, địa chỉ nhà riêng/kho, thông tin ngân hàng, CCCD, lịch sử xử phạt, chi tiết đơn hàng, mọi PII/định danh của Người mua.
3. **Consent**:
   - Ảnh đại diện là **opt-in, mặc định OFF (`false`)**.
   - Không bổ sung công tắc tắt toàn bộ hồ sơ công khai trong phạm vi hiện tại.
4. **Lịch sử bán & vòng đời sản phẩm**:
   - Chỉ hiển thị chỉ số tổng hợp số giao dịch hoàn tất; không liệt kê danh sách sản phẩm đã bán.
   - Không lưu bản sao ảnh/nội dung bài đăng cũ để hiển thị trên hồ sơ công khai.
   - Tin ẩn, hết hàng/đã bán, bị gỡ hoặc không còn trạng thái công khai phải biến mất khỏi hồ sơ công khai theo đúng `UC17`.

---

## 5. Current Architecture Analysis

### 5.1. Backend Modules & Dependency Boundaries `[VERIFIED]`
Theo [MODULE_BOUNDARIES.md](../../docs/architecture/MODULE_BOUNDARIES.md):
- `identity`: Sở hữu `UserEntity`, `RoleEntity`, `seller_profiles`, `ekyc_profiles`, `AccountManagementService`, `SellerSettingsService`.
- `catalog`: Sở hữu `ProductEntity`, `ProductMediaEntity`, `CategoryEntity`, `CatalogController` (`GET /api/v1/products/**`). Đã phụ thuộc `identity` qua `IdentityCatalogFacade`.
- `commerce`: Sở hữu `OrderEntity`, `OrderItemEntity`.
- `trustsafety`: Sở hữu `reviews` (`OrderReviewService`, `DisputeRepository`). Đã phụ thuộc `commerce`.
- `platform`: Sở hữu Notification, Admin read model, AuditLog (*"chỉ đọc projection/aggregate; không sửa business state trực tiếp"*).

### 5.2. Module Ownership đề xuất để tránh Circular Dependency `[PROPOSED]`
- **Vấn đề**: Nếu đặt `PublicSellerProfileService` trong `identity` và gọi sang `catalog`/`commerce`/`trustsafety` sẽ tạo vòng phụ thuộc (`catalog -> identity -> catalog`).
- **Giải pháp đề xuất**:
  - **`identity`** giữ quyền sở hữu `SellerSettingsService` (quản lý `allow_public_avatar` và `store_name`) + cung cấp `IdentityPublicSellerFacade` (read-only: trả thông tin công khai đã lọc PII và kiểm tra điều kiện tài khoản).
  - **`catalog`** cung cấp `CatalogPublicSellerFacade` (read-only: đếm & phân trang sản phẩm đang mở bán của `sellerId`).
  - **`commerce`** cung cấp `CommercePublicSellerFacade` (read-only: đếm giao dịch hoàn tất của `sellerId`).
  - **`trustsafety`** cung cấp `TrustSafetyPublicSellerFacade` (read-only: tính điểm sao trung bình, phân bố 1–5 sao, phân trang review hợp lệ đã ẩn danh).
  - **`platform`** (hoặc package read-model `modules/platform/publicseller`) sở hữu `PublicSellerProfileController` & `PublicSellerProfileService` để tổng hợp 4 read-only facade trên mà **không tạo circular dependency** và không sửa bất kỳ business state nào.

### 5.3. SecurityConfig `[VERIFIED]`
- `SecurityConfig.java` áp dụng cơ chế allow-list tường minh + `.anyRequest().denyAll()`.
- Cần bổ sung đúng `HttpMethod.GET` cho `"/api/v1/public/sellers/**"` vào nhóm `.permitAll()`.
- `GET/PUT /api/v1/seller/settings` đã nằm trong nhóm `/api/v1/seller/**` yêu cầu `.authenticated()` và lấy `userId` trực tiếp từ `jwt.getSubject()`.

---

## 6. Database and Migration Analysis

### 6.1. Xác minh Schema thực tế `[VERIFIED]`
- **Bảng `seller_settings` KHÔNG tồn tại.**
- Bảng thực tế lưu cấu hình người bán là **`public.seller_profiles`** (kèm view tương thích `og_compat.seller_profiles`):
  - Cột hiện có: `seller_profile_id` (PK), `user_id` (FK `users.user_id`), `revision_no` (default `1`), `state` (default `'PENDING_EKYC'`), `ekyc_profile_id`, `quick_auth_result_id`, `pickup_address_id`, `return_address_id`, `bank_snapshot` (jsonb), `updated_at`, `record_type` (`CHECK (record_type = 'seller_profiles')`), `seller_profile_decisions_history` (jsonb), `store_name` (`varchar(120)`), `contact_phone` (`varchar(20)`).
  - **Lưu ý quan trọng**: `public.seller_profiles` **không có ràng buộc UNIQUE trên `user_id`** (`SellerSettingsService` truy vấn bản ghi mới nhất qua `ORDER BY seller_profile_id DESC LIMIT 1`), và một Seller chưa từng lưu trang `Thiết lập gian hàng` có thể có **0 dòng** trong `public.seller_profiles`.
  - **Triggers OG-70**: `public.seller_profiles` có trigger `og70_before` và `og70_after`; view `og_compat.seller_profiles` cần được cập nhật đồng bộ khi thêm cột mới nếu có truy vấn qua `og_compat`.

### 6.2. Đề xuất Migration `V59` `[PROPOSED]`
- **Tên file dự kiến**: `V59__add_public_avatar_consent_and_seller_profile_indexes.sql` (chỉ tạo sau khi có Implementation Authorization và kiểm tra lại không có nhánh nào chiếm `V59`).
- **Nội dung schema đề xuất**:
  1. `ALTER TABLE public.seller_profiles ADD COLUMN IF NOT EXISTS allow_public_avatar BOOLEAN NOT NULL DEFAULT FALSE;`
  2. Cập nhật lại view `og_compat.seller_profiles` để bổ sung cột `allow_public_avatar` (nếu cần đồng bộ `og_compat`).
  3. Bổ sung composite indexes phục vụ truy vấn công khai (xem chi tiết mục 13).
- **Đánh giá cột `allow_public_avatar BOOLEAN NOT NULL DEFAULT FALSE`**:
  - Phù hợp 100% với `public.seller_profiles`, an toàn backfill (mọi bản ghi cũ mặc định `FALSE` = opt-in).
  - Khi Seller chưa có dòng trong `public.seller_profiles`: Backend mặc định `allowPublicAvatar = false`.
  - **Không cần index riêng** cho cột boolean `allow_public_avatar` (cardinality thấp, luôn truy vấn theo `user_id`).
  - **Audit thay đổi consent `[OPEN / PROPOSED]`**: Hiện `seller_profiles` chỉ có `updated_at`. Nếu PO yêu cầu lưu lịch sử bật/tắt consent (thời điểm, giá trị cũ/mới), có thể ghi sự kiện vào `audit_logs` hiện có khi `allowPublicAvatar` thay đổi (đánh dấu cần PO xác nhận có nằm trong phạm vi hiện tại không).

---

## 7. Privacy and Security Design

### 7.1. Nguyên tắc Zero PII Leakage ở Backend `[PROPOSED]`
- Toàn bộ Public API trả về **Public DTO độc lập**, không serialize Entity (`UserEntity`, `ProductEntity`, `OrderEntity`, `ReviewResponse`).
- **Whitelist tường minh** trường trả về; tuyệt đối loại bỏ: `email`, `phoneNumber`, `contactPhone`, `bankName`, `bankAccountNumber`, `bankAccountHolder`, `pickupAddressId`, `returnAddressId`, địa chỉ kho/nhà, CCCD, `penaltyPoints`, lịch sử xử phạt, `buyerId`, `reviewerId`, `orderId`.
- Error response tuân thủ `API_CONVENTIONS.md` (`code`, `message`, `requestId`, `fieldErrors`), không lộ stack trace, SQL hay phân biệt tài khoản bị khóa vs không tồn tại.

### 7.2. Quy tắc Avatar & Cache Invalidation `[PROPOSED]`
- Nguồn ảnh duy nhất được phép đọc: `public.users.avatar_url` (từ `UC05`). **Tuyệt đối không** truy cập bảng `ekyc_private_assets`.
- Khi `allow_public_avatar = false` (hoặc chưa có bản ghi `seller_profiles`): API **bắt buộc trả `avatarUrl: null`**. Frontend chỉ hiển thị monogram chữ cái đầu; không gửi URL ảnh thật xuống client.
- **Cache & hiệu lực tức thì khi thu hồi consent**:
  - Hiện codebase không dùng Redis hay `@Cacheable` cho profile/settings `[VERIFIED]`.
  - Public endpoint `GET /api/v1/public/sellers/{sellerId}` trả header `Cache-Control: no-store, max-age=0` (hoặc `no-cache`) để trình duyệt/proxy không giữ bản sao chứa `avatarUrl` cũ sau khi Seller tắt consent.

### 7.3. Điều kiện tài khoản được phép hiển thị công khai `[VERIFIED + OPEN]`
Dựa trên constraint thực tế của `public.users` (`governance_state IN ('ACTIVE', 'LOCKED', 'DEACTIVATED')`, `status`, `deleted_at`, `governance_until`, `governance_restrictions`) và `AccountManagementService.java`:

| Trạng thái tài khoản | Điều kiện thực tế trong DB | Hành vi đề xuất | Trạng thái |
|---|---|---|---|
| **Active Seller** | Có role `SELLER`, `status = 'ACTIVE'`, `governance_state = 'ACTIVE'` (hoặc `LOCKED` đã hết hạn `governance_until <= now`), `deleted_at IS NULL` | `200 OK` — Hiển thị hồ sơ công khai | `[PROPOSED]` |
| **Buyer-only (không có role `SELLER`)** | Không có role `SELLER` trong `user_roles` | `404 NOT_FOUND` — Không công khai hồ sơ người mua | `[PROPOSED]` |
| **Permanently Disabled / Deactivated** | `governance_state = 'DEACTIVATED'` hoặc `LOCKED` với `governance_until IS NULL` | `404 NOT_FOUND` — Tránh tiết lộ trạng thái nội bộ | `[PROPOSED]` |
| **Soft Deleted** | `deleted_at IS NOT NULL` | `404 NOT_FOUND` | `[PROPOSED]` |
| **Temporarily Locked / Suspended (`LOCKED` có `governance_until > now`) hoặc `RESTRICTED (NEW_LISTINGS)`** | Tài khoản đang bị khóa tạm thời (tối đa 3 tháng) hoặc chỉ bị hạn chế đăng tin mới | **Chưa có quy định trong UC/BR** — Cần PO chọn: (A) Trả `404` trong suốt thời gian khóa tạm, hay (B) Vẫn hiển thị hồ sơ nhưng ẩn nút mua/tin mới | `[OPEN — PO Decision #2]` |

---

## 8. Review Lifecycle

### 8.1. Hiện trạng xác minh từ `UC61.md`, `public.reviews` và `OrderReviewService.java` `[VERIFIED]`
- Bảng `public.reviews` có cột: `review_id`, `order_id`, `reviewer_id`, `reviewee_id`, `rating` (1–5), `comment` (`varchar(2000)`), `created_at`, `updated_at`, `deleted_at`, `record_type`, `edit_history`.
- **Chưa có cột `moderation_status` / `is_hidden`** trong `public.reviews`; cơ chế ẩn/gỡ duy nhất ở schema hiện tại là `deleted_at IS NOT NULL`.
- Điều kiện tạo review hợp lệ hiện tại (`OrderReviewService.submitReview`): chỉ thành viên của đơn (`buyerId` hoặc `sellerId`), đơn ở trạng thái `DELIVERED` hoặc `COMPLETED`, điểm 1–5 sao, 1 đánh giá/bên/đơn (được sửa qua `UPDATE`).
- **Mâu thuẫn/khiếm khuyết phát hiện ở code hiện tại**:
  1. `DisputeRepository.findReviewsForUser` **chưa lọc `r.deleted_at IS NULL`**.
  2. `findReviewsForUser` lấy mọi review có `reviewee_id = userId` mà **không phân biệt review nhận với tư cách Người bán** (`JOIN orders o ON o.order_id = r.order_id WHERE o.seller_id = :sellerId AND r.reviewer_id = o.buyer_id`).
  3. `OrderReviewService.getUserReviews` tính `avg` trên danh sách bị cắt `limit` (tối đa 50) và `.orElse(5.0)` khi không có review.
  4. Chưa có cơ chế tự động lọc/che PII (SĐT, địa chỉ, số tài khoản) trong `comment` tự do theo `BR-UC61-03`.
  5. Chưa có quy định khi **Buyer bị khóa/vô hiệu hóa** thì review cũ của Buyer đó có tiếp tục hiển thị (ở dạng ẩn danh) hay bị ẩn theo Buyer.

### 8.2. Thiết kế bắt buộc cho Public Reviews API `[PROPOSED]`
- **Điều kiện hiển thị**: `r.reviewee_id = :sellerId AND r.deleted_at IS NULL AND o.seller_id = :sellerId AND r.reviewer_id = o.buyer_id AND r.rating BETWEEN 1 AND 5`.
- **Ẩn danh người mua ở Backend**: Không trả `reviewerId`, `orderId`, tên thật. Backend tạo nhãn ẩn danh deterministic không thể suy ngược danh tính (ví dụ: `"Người mua đã xác thực"` hoặc che ký tự đầu/cuối kiểu `"N***a"` khi tên đủ dài, fallback `"Người mua đã xác thực"`).
- Không tự thay đổi review lifecycle hoặc quy tắc thưởng xu `UC61`.
- Ghi nhận **PO Decision #3** về việc ẩn/giữ review khi Buyer bị vô hiệu hóa, và **PO Decision #4** về cách xử lý PII trong `comment` tự do.

---

## 9. Statistics Definitions

### 9.1. Average Rating (`averageRating`, `totalReviews`, `ratingDistribution`) `[PROPOSED]`
- **Tập dữ liệu**: Toàn bộ review thỏa điều kiện mục 8.2 (không bị giới hạn theo `page`/`size`).
- **Công thức**: `ROUND(AVG(r.rating)::numeric, 1)` (làm tròn 1 chữ số thập phân).
- **Khi `totalReviews = 0`**: Trả `averageRating: null` (tuyệt đối không trả `5.0` giả), `ratingDistribution: { "1": 0, "2": 0, "3": 0, "4": 0, "5": 0 }`. Frontend hiển thị `"Chưa có đánh giá"`.
- Tách biệt hoàn toàn với điểm uy tín (`reputationScore` theo `BR-UC61-04` và `UC11`).

### 9.2. Active Products Count (`activeProductsCount`) `[VERIFIED + OPEN]`
- Theo `ProductEntity.java` và `CatalogService.java`:
  - Điều kiện cơ bản: `p.seller_id = :sellerId AND p.status = 'ACTIVE' AND p.deleted_at IS NULL`.
  - Hiện bảng `public.products` **không có cột `expires_at`** `[VERIFIED]`.
  - Bảng `public.products` có cột tính sẵn `quantity_available` (`quantity_total - quantity_held - quantity_sold - quantity_unavailable`). Hiện `CatalogService` cho phép xem tin `ACTIVE` kể cả hết hàng kèm nhãn `"Hết hàng"` theo `UC13`, nhưng `UC17` yêu cầu chỉ hiển thị sản phẩm đang bán.
  - **Cần PO xác nhận (`PO Decision #5`)**: `activeProductsCount` và Tab *Sản phẩm đang bán* có lọc thêm `quantity_available > 0` hay giữ đồng bộ với `CatalogService` (`status = 'ACTIVE' AND deleted_at IS NULL`)?

### 9.3. Completed Transactions Count (`completedTransactionsCount`) `[OPEN — PO Decision #1]`
- **Bằng chứng schema `[VERIFIED]`**: `orders` (1 đơn gắn 1 `seller_id`, `status = 'COMPLETED'`, `completed_at`) có quan hệ `1-N` với `order_items` (1 đơn có thể gồm nhiều sản phẩm của cùng Người bán khi mua qua giỏ hàng).
- **Hai phương án**:
  - **Phương án A (Đề xuất)**: Đếm số đơn hoàn tất — `COUNT(*) FROM public.orders WHERE seller_id = :sellerId AND status = 'COMPLETED'` (1 đơn hoàn tất = 1 giao dịch, nhất quán với `UC61` mỗi đơn đánh giá 1 lần).
  - **Phương án B**: Đếm theo `order_items` hoặc `SUM(quantity)` của các đơn `COMPLETED`.
- **Trạng thái**: `[OPEN]` — Cần PO phê duyệt trước khi implement. Không tạo bảng bản sao lịch sử sản phẩm đã bán.

---

## 10. Backend API Contract Proposal

Tuân thủ `docs/api/API_CONVENTIONS.md` (`/api/v1`, error contract chuẩn, server-side `size` cap).

### 10.1. `GET /api/v1/public/sellers/{sellerId}` (Public — `permitAll`)
- **Headers**: `Cache-Control: no-store, max-age=0`
- **Response `200 OK`**:
```json
{
  "sellerId": 12,
  "displayName": "Tiệm Đồ Cổ Sài Gòn",
  "avatarUrl": null,
  "ekycVerified": true,
  "joinedAt": "2026-09-01T08:00:00Z",
  "stats": {
    "averageRating": 4.8,
    "totalReviews": 15,
    "activeProductsCount": 6,
    "completedTransactionsCount": 18,
    "ratingDistribution": {
      "1": 0,
      "2": 0,
      "3": 1,
      "4": 2,
      "5": 12
    }
  }
}
```
- **Response `404 Not Found`** (không tồn tại, không phải Seller, hoặc tài khoản không đủ điều kiện công khai):
```json
{
  "code": "SELLER_PROFILE_NOT_FOUND",
  "message": "Hồ sơ người bán không tồn tại hoặc hiện không khả dụng.",
  "requestId": "...",
  "fieldErrors": []
}
```

### 10.2. `GET /api/v1/public/sellers/{sellerId}/products` (Public — `permitAll`)
- **Query params**: `page` (default `0`, min `0`), `size` (default `12`, min `1`, max `24`), `sort` (`newest` | `price_asc` | `price_desc`, tie-breaker luôn có `product_id DESC`).
- **Response `200 OK`**: Tái sử dụng chuẩn `PageResponse<PublicSellerProductSummary>` (chỉ gồm `productId`, `title`, `listedPrice`, `currency`, `condition`, `thumbnailUrl`, `quantityAvailable`, `createdAt`; không chứa thông tin riêng tư). Trả `404` nếu `sellerId` không đủ điều kiện công khai.

### 10.3. `GET /api/v1/public/sellers/{sellerId}/reviews` (Public — `permitAll`)
- **Query params**: `page` (default `0`, min `0`), `size` (default `10`, min `1`, max `20`), `rating` (optional `1..5`). Sort deterministic: `created_at DESC, review_id DESC`.
- **Response `200 OK`**:
```json
{
  "content": [
    {
      "reviewId": 101,
      "reviewerDisplayName": "Người mua đã xác thực",
      "rating": 5,
      "comment": "Đóng gói rất cẩn thận, hàng đúng mô tả.",
      "createdAt": "2026-11-20T10:15:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 15,
  "totalPages": 2
}
```
- **Khẳng định bảo mật**: Tuyệt đối không có `orderId`, `reviewerId`, `buyerId`, `email`, `phone`, `address`.

### 10.4. Cập nhật Private API `GET/PUT /api/v1/seller/settings` (Authenticated — Owner only)
- Bổ sung trường `allowPublicAvatar: boolean` vào `SellerSettingsResponse` và `UpdateSellerSettingsRequest` trong `SellerSettingsDtos.java`.
- Ownership luôn lấy từ `jwt.getSubject()`, không nhận `sellerId` từ client.

### 10.5. Cập nhật Seller Summary trên `GET /api/v1/products/{productId}` (`UC13`)
- Mở rộng `IdentityCatalogFacade.SellerPublicSummary` để trả thêm `averageRating`, `totalReviews`, `activeProductsCount`, `completedTransactionsCount`, và `avatarUrl` (tuân thủ `allowPublicAvatar`) nhằm loại bỏ 3 chỉ số hardcode trong `ProductDetailView.tsx`.

---

## 11. Frontend Architecture Proposal

- **Route mới** trong [App.tsx](../../frontend/src/app/App.tsx) (bên trong `AppLayout` — công khai cho Guest, Buyer, Seller):
  - `<Route path="/sellers/:sellerId" element={<PublicSellerProfilePage />} />`
- **Cấu trúc file đề xuất** (nhất quán với `frontend/src/features/seller/` và `frontend/src/pages/`):
  - `frontend/src/features/seller/publicSellerApi.ts` — gọi 3 Public API (dùng `fetch` công khai, không bắt buộc token).
  - `frontend/src/features/seller/types.ts` — TypeScript interfaces cho `PublicSellerProfile`, `PublicSellerReview`, `PageResponse`.
  - `frontend/src/features/seller/components/PublicSellerHeader.tsx` — Avatar/Monogram, Tên gian hàng, Badge eKYC, 3 chỉ số thật.
  - `frontend/src/features/seller/components/PublicSellerProductsTab.tsx` — Lưới sản phẩm (tái sử dụng `ProductCard`) + phân trang + empty/error state.
  - `frontend/src/features/seller/components/PublicSellerReviewsTab.tsx` — Phân bố sao + danh sách review ẩn danh + lọc sao + phân trang.
  - `frontend/src/pages/PublicSellerProfilePage.tsx` — Quản lý state 2 tab (`products` | `reviews`), đồng bộ URL query `?tab=...&page=...`, xử lý `404` thân thiện.
- **Tích hợp vào các trang hiện có**:
  - [SellerSettingsPage.tsx](../../frontend/src/pages/seller/SellerSettingsPage.tsx) (tab `Thiết lập gian hàng` trong `SellerLayoutShell`): Thêm thẻ **"Quyền riêng tư Hồ sơ công khai"** gồm toggle `Cho phép hiển thị ảnh đại diện công khai` và nút `"Xem trước Hồ sơ công khai của tôi"` mở `/sellers/${user.id}`.
  - [ProductDetailView.tsx](../../frontend/src/features/marketplace/components/ProductDetailView.tsx): Gắn `<Link to="/sellers/...">` cho khối Người bán và thay 3 chỉ số hardcode bằng dữ liệu thật.
  - [BuyerOrderDetailView.tsx](../../frontend/src/features/orders/components/BuyerOrderDetailView.tsx): Gắn link tên Người bán dẫn tới `/sellers/${order.sellerId}`.

---

## 12. Regression Test Strategy

### 12.1. Backend Security & Functional Tests `[PROPOSED]`
- **Public Access & AuthZ (MockMvc)**:
  - Guest (không JWT) gọi `GET /api/v1/public/sellers/{id}`, `/products`, `/reviews` → `200 OK`.
  - Guest gọi `GET/PUT /api/v1/seller/settings` → `401 Unauthorized`.
  - Seller A không thể đọc hoặc bật `allowPublicAvatar` thay Seller B (IDOR test: `userId` luôn lấy từ JWT).
- **Privacy & PII Leakage**:
  - `allowPublicAvatar = false` → JSON response có `avatarUrl == null`, không chứa bất kỳ URL ảnh nào; đổi `true -> false` có hiệu lực ngay ở request tiếp theo + header `Cache-Control: no-store`.
  - Assert raw JSON string của cả 3 Public API **không chứa** `email`, `phoneNumber`, `bankAccount`, `cccd`, `buyerId`, `reviewerId`, `orderId`, `penaltyPoints`.
  - `avatarUrl` không bao giờ lấy từ `ekyc_private_assets`.
- **Visibility & Lifecycle**:
  - Buyer-only user, `DEACTIVATED` seller, `deleted_at IS NOT NULL` seller → `404 SELLER_PROFILE_NOT_FOUND` (không lộ thông tin nội bộ).
  - Tin `DRAFT`, `PENDING`, `HIDDEN`, `SOLD`, `REJECTED`, `deleted_at IS NOT NULL` không xuất hiện ở `/products` và không tính vào `activeProductsCount`.
  - Review có `deleted_at IS NOT NULL` hoặc review nhận với tư cách Buyer (không phải Seller) không xuất hiện ở `/reviews` và không tính vào `averageRating`.
  - Seller có 0 review → `averageRating == null`, `totalReviews == 0` (không trả `5.0`).
  - Pagination: `size > max` bị kẹp (clamp) ở server; `page` ngoài biên trả `content: []`.

### 12.2. Frontend Tests (Vitest + Testing Library) `[PROPOSED]`
- Guest truy cập `/sellers/:sellerId` render đủ Header + 2 Tab.
- `avatarUrl = null` hiển thị monogram chữ cái đầu; `avatarUrl` hợp lệ hiển thị `<img>`.
- Chuyển đổi qua lại giữa Tab *Sản phẩm đang bán* và Tab *Đánh giá từ người mua*, phân trang hoạt động đúng.
- Empty state (0 sản phẩm, 0 đánh giá `"Chưa có đánh giá"`) và Error / 404 state hiển thị đúng.
- `SellerSettingsPage`: bật/tắt `allowPublicAvatar` gọi `PUT /api/v1/seller/settings` đúng payload; nút xem trước trỏ đúng `/sellers/{userId}`.
- `ProductDetailView`: không còn số liệu hardcode `5.0 (18 đánh giá)` / `12 sản phẩm đang bán` / `8 đã giao dịch`; link dẫn đúng `/sellers/:sellerId`.

---

## 13. Performance Considerations

- **Chống N+1 query**:
  - Products tab: Tận dụng `@BatchSize(size = 50)` của `ProductEntity` và batch-fetch thumbnail từ `product_media` bằng `IN (:productIds)` trong 1 query.
  - Reviews tab: 1 query SQL phân trang (`reviews JOIN orders LEFT JOIN users`) + 1 query aggregate (`COUNT`, `AVG`, `GROUP BY rating`) thay vì tải toàn bộ review lên JVM.
- **Deterministic Pagination & Server Caps**:
  - Products: `ORDER BY created_at DESC, product_id DESC`, max `size = 24`.
  - Reviews: `ORDER BY created_at DESC, review_id DESC`, max `size = 20`.
- **Đề xuất Indexes trong `V59`** (chỉ tạo partial/composite index có mục đích rõ ràng):
  - `idx_seller_profiles_user_latest ON public.seller_profiles (user_id, seller_profile_id DESC)` — tối ưu tìm bản ghi mới nhất theo `user_id`.
  - `idx_products_seller_active ON public.products (seller_id, created_at DESC, product_id DESC) WHERE status = 'ACTIVE' AND deleted_at IS NULL` — tối ưu đếm & phân trang sản phẩm đang bán.
  - `idx_reviews_reviewee_active ON public.reviews (reviewee_id, created_at DESC, review_id DESC) WHERE deleted_at IS NULL` — tối ưu đếm/tính sao & phân trang review.
  - `idx_orders_seller_completed ON public.orders (seller_id) WHERE status = 'COMPLETED'` — tối ưu đếm giao dịch hoàn tất.
- **Caching**: Không thêm server-side cache ở giai đoạn đầu để đảm bảo thu hồi consent (`allowPublicAvatar`) và ẩn review có hiệu lực tức thì. Chỉ cân nhắc cache sau khi có số liệu tải thực tế.

---

## 14. Implementation Phases

| Phase | Nội dung chính | Điều kiện tiên quyết |
|---|---|---|
| **Phase 1 — Database (`V59`)** | Tạo migration `V59` thêm `allow_public_avatar BOOLEAN NOT NULL DEFAULT FALSE` trên `public.seller_profiles`, cập nhật view `og_compat.seller_profiles`, thêm 4 index mục 13. Kiểm tra fresh & upgrade migration trên DB cô lập. | Có Implementation Authorization + PO chốt các mục OPEN |
| **Phase 2 — Backend API & Facades** | Cập nhật `SellerSettingsService/Dtos`, triển khai 4 read-only facades (`identity`, `catalog`, `commerce`, `trustsafety`), tạo `PublicSellerProfileController/Service` tại `platform`, mở `GET /api/v1/public/sellers/**` trong `SecurityConfig`, nối stats thật cho `UC13`. | Phase 1 hoàn tất |
| **Phase 3 — Frontend UI** | Tạo `/sellers/:sellerId` (`PublicSellerProfilePage` + 2 tab), cập nhật `SellerSettingsPage` (toggle consent + nút xem trước), cập nhật `ProductDetailView` & `BuyerOrderDetailView`. | Phase 2 hoàn tất |
| **Phase 4 — Verification & Regression** | Viết và chạy toàn bộ Backend Security/Unit/MockMvc tests, Frontend Vitest, `npm run typecheck`, `npm run build`, kiểm tra regression cho `UC05`, `UC13`, `UC17`, `UC61`. | Phase 3 hoàn tất |

---

## 15. Acceptance Criteria / Definition of Done

| ID | Nhóm | Nội dung Acceptance Criteria | Cách kiểm chứng / Evidence dự kiến | Trạng thái |
|---|---|---|---|---|
| **AC-DB-01** | Database | Migration `V59` áp dụng thành công (fresh + upgrade), không sửa migration `V1`–`V58`, cột `allow_public_avatar` có `NOT NULL DEFAULT FALSE` trên `public.seller_profiles`. | Chạy Flyway validate/migrate trên DB test cô lập + kiểm tra `\d+ public.seller_profiles`. | `NOT STARTED` |
| **AC-BE-01** | Backend | 3 Public API trả đúng schema mục 10, `Cache-Control: no-store`, dùng DTO độc lập không serialize Entity. | MockMvc tests cho cả 3 endpoint. | `NOT STARTED` |
| **AC-BE-02** | Backend | `allowPublicAvatar` chỉ thay đổi được bởi chủ sở hữu qua JWT; khi `false`, Public API trả `avatarUrl = null` ngay lập tức. | Integration test bật/tắt consent + cross-seller IDOR test. | `NOT STARTED` |
| **AC-BE-03** | Backend | Thống kê `averageRating`, `activeProductsCount`, `completedTransactionsCount` tính đúng theo định nghĩa PO đã chốt; 0 review trả `averageRating = null`. | JUnit/Repository tests với nhiều kịch bản (0 review, review xóa mềm, đơn nhiều item, tin ẩn). | `NOT STARTED` |
| **AC-SEC-01** | Security | Không rò rỉ PII (`email`, SĐT, địa chỉ, ngân hàng, CCCD, `buyerId`, `orderId`, lịch sử xử phạt) trong response, nested object và error payload. | Security assertion tests inspect raw JSON body & error responses. | `NOT STARTED` |
| **AC-SEC-02** | Security | Guest truy cập được Public API; Guest bị `401` khi gọi `/api/v1/seller/settings`; tài khoản không hợp lệ trả `404`. | Spring Security MockMvc tests. | `NOT STARTED` |
| **AC-FE-01** | Frontend | Guest xem được `/sellers/:sellerId` với 2 tab, phân trang, empty/error/404 state; monogram hiện khi `avatarUrl = null`. | Vitest + Testing Library cho `PublicSellerProfilePage`. | `NOT STARTED` |
| **AC-FE-02** | Frontend | Seller bật/tắt `allowPublicAvatar` và bấm xem trước được từ `/seller/settings`; `ProductDetailView` hiển thị số liệu thật và gắn link đúng `/sellers/:sellerId`. | Vitest cho `SellerSettingsPage` & `ProductDetailView`. | `NOT STARTED` |
| **AC-QA-01** | Testing | Toàn bộ test mới + regression suites liên quan (`Catalog*`, `SellerSettings*`, `OrderReview*`, Frontend Vitest + `tsc` + `vite build`) PASS 100%, không có regression. | Báo cáo kết quả lệnh chạy `mvnw test`, `npm run typecheck`, `npm run test -- --run`, `npm run build`. | `NOT STARTED` |

---

## 16. Risk Register

| ID | Risk | Severity | Evidence | Mitigation | Status |
|---|---|---|---|---|---|
| **R-01** | Rò rỉ PII người mua hoặc thông tin riêng tư qua DTO cũ (`ReviewResponse` chứa `orderId`, `reviewerId`, `reviewerName`) | **CRITICAL** | `OrderReviewService.java` & `DisputeRepository.java` dòng 455–485 `[VERIFIED]` | Tạo Public DTO độc lập hoàn toàn, ẩn danh tên người mua ở Backend, bổ sung security test inspect raw JSON. | `OPEN` |
| **R-02** | Sai lệch bảng/view OG-70 khi thêm cột vào `public.seller_profiles` mà quên đồng bộ `og_compat.seller_profiles` hoặc trường hợp Seller có 0 dòng `seller_profiles` | **HIGH** | `\d+ public.seller_profiles` & `SellerSettingsService.java` `[VERIFIED]` | Cập nhật cả bảng và view trong `V59`; xử lý fallback an toàn `allowPublicAvatar = false` khi chưa có bản ghi. | `OPEN` |
| **R-03** | Circular dependency giữa `identity`, `catalog`, `commerce`, `trustsafety` | **HIGH** | `CatalogService` đang gọi `IdentityCatalogFacade` `[VERIFIED]` | Đặt Public Seller Profile read-model aggregator ở module `platform`, chỉ gọi read-only facades một chiều. | `PROPOSED` |
| **R-04** | Thống kê `averageRating` sai (trả `5.0` khi 0 review, tính cả review xóa mềm hoặc review nhận với tư cách Buyer) | **HIGH** | `OrderReviewService.java` dòng 96–101 `[VERIFIED]` | Viết query aggregate SQL riêng lọc `deleted_at IS NULL` + `o.seller_id = :sellerId AND r.reviewer_id = o.buyer_id`, trả `null` khi `count = 0`. | `OPEN` |
| **R-05** | Nội dung `comment` tự do trong `reviews` chứa PII do người mua tự gõ (`BR-UC61-03`) | **MEDIUM** | `public.reviews` không có cột moderation/PII filter `[VERIFIED]` | Đưa vào PO Decision #4 để quyết định có áp dụng regex/masking SĐT/email tối thiểu khi hiển thị công khai không. | `OPEN` |
| **R-06** | Hiệu năng suy giảm (N+1 query) khi trang `/sellers/:sellerId` tải danh sách sản phẩm + thumbnail + thống kê | **MEDIUM** | `ProductEntity` + `product_media` tách bảng `[VERIFIED]` | Dùng batch-fetch media, giới hạn `size`, và 4 composite/partial indexes ở `V59`. | `PROPOSED` |

---

## 17. PO Decisions Required

Các quyết định nghiệp vụ cần PO phê duyệt trước khi cấp Implementation Authorization:

| # | Vấn đề nghiệp vụ cần PO quyết định | Các phương án | Đề xuất kỹ thuật | Trạng thái |
|---|---|---|---|---|
| **PO-01** | **Cách đếm `Completed Transactions Count`** (1 đơn có thể chứa nhiều `order_items` cùng 1 Seller) | (A) Đếm số đơn `orders` có `status = 'COMPLETED'`; (B) Đếm số `order_items` / tổng `quantity` của đơn `COMPLETED`. | **Phương án A** (1 đơn hoàn tất = 1 giao dịch, khớp `UC61` 1 review/đơn). | `[OPEN]` |
| **PO-02** | **Hiển thị hồ sơ khi Seller đang `LOCKED` tạm thời (`governance_until > now`) hoặc `RESTRICTED (NEW_LISTINGS)`** | (A) `LOCKED` tạm thời trả `404`, còn `RESTRICTED (NEW_LISTINGS)` vẫn hiện hồ sơ; (B) Cả hai đều trả `404`; (C) Vẫn hiện hồ sơ kèm nhãn tạm ngưng. | **Phương án A** (khóa tài khoản tạm thời không hiển thị công khai; hạn chế chỉ đăng tin mới không ẩn hồ sơ). | `[OPEN]` |
| **PO-03** | **Hiển thị review cũ khi tài khoản Buyer (người viết review) bị `DEACTIVATED` / xóa mềm** | (A) Vẫn giữ hiển thị dưới tên ẩn danh `"Người mua đã xác thực"` (giao dịch đã hoàn tất là có thật); (B) Ẩn review của Buyer đã vô hiệu hóa. | **Phương án A** (đảm bảo tính toàn vẹn điểm sao và không lộ trạng thái Buyer). | `[OPEN]` |
| **PO-04** | **Kiểm soát PII trong `comment` tự do của review (`BR-UC61-03`)** | (A) Giữ nguyên nội dung đã lưu (dựa vào kiểm duyệt/xóa mềm `deleted_at` nếu bị báo cáo); (B) Tự động che (mask) chuỗi SĐT/email trong Public Reviews API trước khi trả về. | **Phương án B** (phòng thủ chiều sâu ở tầng read-only, không sửa dữ liệu gốc). | `[OPEN]` |
| **PO-05** | **Điều kiện `quantity_available` cho `activeProductsCount` và Tab Sản phẩm đang bán** | (A) Chỉ tính & hiển thị tin `ACTIVE` có `quantity_available > 0`; (B) Tính cả tin `ACTIVE` hết hàng (`quantity_available = 0`, gắn nhãn `"Hết hàng"` giống `UC13`). | **Phương án A** (phù hợp tên gọi *"Sản phẩm đang bán"* và `UC17`). | `[OPEN]` |
| **PO-06** | **Ghi Audit Log khi Seller bật/tắt `allowPublicAvatar`** | (A) Không cần audit riêng (chỉ cập nhật `updated_at`); (B) Ghi thêm sự kiện vào `audit_logs` khi consent thay đổi. | **Phương án B** (chi phí thấp, hữu ích cho truy vết quyền riêng tư). | `[OPEN]` |

---

## 18. Out of Scope

- Không xây dựng công tắc tắt toàn bộ Hồ sơ người bán công khai.
- Không hiển thị danh sách sản phẩm đã bán, ảnh/nội dung tin đăng đã ẩn hoặc hết hạn.
- Không công khai điểm uy tín nội bộ hoặc lịch sử xử phạt (`UC11`).
- Không thay đổi luồng tạo/sửa đánh giá, thưởng xu hay quy tắc kiểm duyệt của `UC61`.
- Không thêm tính năng theo dõi (Follow shop), chat mới ngoài `UC18`, hay tìm kiếm gian hàng toàn sàn.
- Không thay đổi quy trình eKYC (`UC06`–`UC08`) hoặc cấu trúc lưu trữ `ekyc_private_assets`.

---

## 19. Final Readiness Assessment

- **Mức độ hoàn thiện kỹ thuật**: Đã xác minh đầy đủ codebase, 83 UC, PostgreSQL schema, Flyway history (`V58`), `SecurityConfig`, `OrderReviewService`, `SellerSettingsService`, `AccountManagementService`, `ProductEntity` và Frontend routes/components.
- **Đánh giá sẵn sàng**: **`NEEDS PO DECISIONS`** — Cần PO chốt 6 quyết định nghiệp vụ trong mục 17 (**PO-01** đến **PO-06**, đặc biệt **PO-01**, **PO-02**, **PO-03**, **PO-05**) và cấp **Implementation Authorization** riêng trước khi bắt đầu Phase 1.

