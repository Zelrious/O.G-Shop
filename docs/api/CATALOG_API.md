# Catalog API Specification

Tài liệu đặc tả toàn bộ REST API của module Catalog thuộc hệ thống O.G Shop.

- Version: 1.2.0 (V27 / TASK-0087)
- Base URL: `/api/v1`
- Module: Catalog
- Authentication: Bearer JWT trong header `Authorization: Bearer <token>` cho các endpoint Người bán (Seller). Các endpoint công khai không yêu cầu authentication.

---

## Quan hệ nhiều danh mục

- POST/PUT tin nhận `categoryIds: [1, 4]`. Cần ít nhất một ID dương, tồn tại và đang hoạt động; ID trùng được gộp. Nếu thiếu `categoryIds`, payload cũ `categoryId: 1` vẫn dùng được. Khi có cả hai, `categoryIds` quyết định lựa chọn; mảng rỗng không được fallback sang trường cũ.
- PUT thay toàn bộ tập danh mục bằng lựa chọn mới, trong cùng transaction với thông tin tin; guard quyền sở hữu/trạng thái/version vẫn áp dụng.
- Mọi response sản phẩm public, Seller và moderation trả thêm `categories: CategoryResponse[]`. Trường `category` cũ phản ánh phần tử đầu để tương thích; UI hiển thị/lưu cả mảng, không cần người dùng chọn danh mục chính.
- `GET /products?categoryId=4` tìm theo bất kỳ liên kết danh mục nào; nhiều danh mục không nhân bản kết quả hoặc tổng số sản phẩm/phân trang. Sản phẩm vẫn phải ACTIVE và chưa xóa để công khai.
- Backend cần chạy Flyway V8 trước khi dùng API này. V8 backfill danh mục cũ, không xóa sản phẩm/đơn hàng.

Ví dụ phần danh mục trong response (các trường khác giữ nguyên):

```json
{
  "category": { "categoryId": 1, "categoryName": "Điện tử", "slug": "electronics", "displayOrder": 1 },
  "categories": [
    { "categoryId": 1, "categoryName": "Điện tử", "slug": "electronics", "displayOrder": 1 },
    { "categoryId": 4, "categoryName": "Sách & văn phòng phẩm", "slug": "books-stationery", "displayOrder": 4 }
  ]
}
```

Các ví dụ response bên dưới giữ phần `category` cũ để mô tả các trường còn lại; response thực tế bổ sung `categories` theo contract này.

## 1. Public Endpoints (Dành cho Người mua và Khách)

### 1.1. Lấy danh sách danh mục sản phẩm (Categories)

- **Endpoint:** `GET /api/v1/categories`
- **Quyền truy cập:** Công khai (Public, không yêu cầu xác thực).
- **Mô tả:** Trả về danh sách tất cả các danh mục hàng hóa đang hoạt động (`is_active = true`), sắp xếp tăng dần theo thứ tự hiển thị (`display_order`).

#### Phản hồi thành công (HTTP 200 OK):

```json
[
  {
    "categoryId": 1,
    "categoryName": "Điện tử",
    "slug": "electronics",
    "description": "Điện thoại, máy tính bảng, laptop và phụ kiện công nghệ",
    "displayOrder": 1
  },
  {
    "categoryId": 2,
    "categoryName": "Thời trang",
    "slug": "fashion",
    "description": "Quần áo, giày dép, túi xách và phụ kiện thời trang",
    "displayOrder": 2
  }
]
```

---

### 1.2. Tìm kiếm và lọc danh sách sản phẩm công khai

- **Endpoint:** `GET /api/v1/products`
- **Quyền truy cập:** Công khai (Public).
- **Mô tả:** Tìm kiếm, phân trang và sắp xếp các sản phẩm đang được mở bán công khai.
- **Ràng buộc nghiệp vụ:**
  - Chỉ trả về các sản phẩm có trạng thái `ACTIVE` và chưa bị xóa mềm (`deleted_at IS NULL`).
  - Tuyệt đối không trả về sản phẩm ở trạng thái `DRAFT`, `HIDDEN`, `RESERVED`, `SOLD`, `REJECTED`.

#### Query Parameters:

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `query` | string | Không | null | Từ khóa tìm kiếm trong tiêu đề sản phẩm (không phân biệt hoa thường). |
| `categoryId` | number | Không | null | Lọc theo bất kỳ danh mục đã gắn cho sản phẩm. |
| `condition` | string | Không | null | Lọc theo tình trạng sản phẩm (`LIKE_NEW`, `GOOD`, `FAIR`, `POOR`, `FOR_PARTS`). |
| `minPrice` | number | Không | null | Giá niêm yết tối thiểu (VND). Phải `<= maxPrice`. |
| `maxPrice` | number | Không | null | Giá niêm yết tối đa (VND). Phải `>= minPrice`. |
| `page` | number | Không | 0 | Chỉ số trang (0-indexed). |
| `size` | number | Không | 12 | Số lượng sản phẩm mỗi trang (tối đa 48). |
| `sort` | string | Không | `newest` | Tiêu chí sắp xếp trong allowlist: `newest`, `price_asc`, `price_desc`. |

#### Phản hồi thành công (HTTP 200 OK):

```json
{
  "items": [
    {
      "productId": 101,
      "title": "iPhone 13 Pro Max 128GB Xanh dương",
      "listedPrice": 14500000,
      "currency": "VND",
      "condition": "LIKE_NEW",
      "location": "Quận 1, TP. Hồ Chí Minh",
      "thumbnailUrl": "https://example.com/images/iphone13.jpg",
      "category": {
        "categoryId": 1,
        "categoryName": "Điện tử",
        "slug": "electronics",
        "description": "Điện thoại, máy tính bảng, laptop...",
        "displayOrder": 1
      },
      "seller": {
        "sellerId": 42,
        "displayName": "Hoàng Nam",
        "trustLabel": "Người bán MVP"
      },
      "createdAt": "2026-09-20T10:15:30Z"
    }
  ],
  "page": 0,
  "size": 12,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

---

### 1.3. Xem chi tiết sản phẩm công khai

- **Endpoint:** `GET /api/v1/products/{productId}`
- **Quyền truy cập:** Công khai (Public).
- **Mô tả:** Trả về toàn bộ thông tin chi tiết của một sản phẩm công khai.
- **Ràng buộc nghiệp vụ:**
  - Chỉ trả về sản phẩm nếu đang ở trạng thái `ACTIVE` và chưa bị xóa mềm.
  - Nếu sản phẩm không tồn tại hoặc ở trạng thái khác (`DRAFT`, `HIDDEN`, `RESERVED`, `SOLD`), trả về HTTP 404 `PRODUCT_NOT_FOUND`.

#### Phản hồi thành công (HTTP 200 OK):

```json
{
  "productId": 101,
  "title": "iPhone 13 Pro Max 128GB Xanh dương",
  "listedPrice": 14500000,
  "currency": "VND",
  "condition": "LIKE_NEW",
  "location": "Quận 1, TP. Hồ Chí Minh",
  "thumbnailUrl": "https://example.com/images/iphone13.jpg",
  "category": {
    "categoryId": 1,
    "categoryName": "Điện tử",
    "slug": "electronics",
    "description": "Điện thoại...",
    "displayOrder": 1
  },
  "seller": {
    "sellerId": 42,
    "displayName": "Hoàng Nam",
    "trustLabel": "Người bán MVP"
  },
  "createdAt": "2026-09-20T10:15:30Z",
  "description": "Máy dùng kỹ, pin 95%, nguyên zin áp suất chưa từng qua sửa chữa.",
  "usageDuration": "10 tháng",
  "defects": "Chấm xước dăm nhỏ ở viền góc dưới",
  "repairHistory": "Chưa từng sửa chữa",
  "includedAccessories": "Hộp zin, cáp C-to-Lightning, 2 ốp lưng",
  "media": [
    {
      "mediaId": 1,
      "mediaType": "IMAGE",
      "mediaUrl": "https://example.com/images/iphone13.jpg",
      "displayOrder": 1
    }
  ]
}
```

---

## 2. Seller Endpoints (Quản lý tin đăng Người bán)

Các endpoint này yêu cầu header: `Authorization: Bearer <access_token>`.
Backend tự động trích xuất `sellerId` từ JWT Subject (`userId`). Người dùng phải có quyền Người bán (`SELLER`), được kiểm tra qua `IdentityCatalogFacade` tại tầng cơ sở dữ liệu.

### 2.1. Lấy danh sách tin đăng của chính Người bán

- **Endpoint:** `GET /api/v1/seller/products`
- **Query Parameters:** `page` (mặc định 0), `size` (mặc định 20, tối đa 50).
- **Mô tả:** Trả về danh sách tất cả sản phẩm thuộc quyền sở hữu của Seller (bao gồm cả `DRAFT`, `PENDING`, `APPROVED_AWAITING_FEE`, `ACTIVE`, `REJECTED`, `HIDDEN`, `RESERVED`, `SOLD`).

#### Phản hồi thành công (HTTP 200 OK):

```json
{
  "items": [
    {
      "productId": 101,
      "title": "iPhone 13 Pro Max 128GB Xanh dương",
      "listedPrice": 14500000,
      "currency": "VND",
      "status": "DRAFT",
      "condition": "LIKE_NEW",
      "location": "Quận 1, TP. Hồ Chí Minh",
      "thumbnailUrl": null,
      "category": {
        "categoryId": 1,
        "categoryName": "Điện tử",
        "slug": "electronics",
        "description": "Điện thoại...",
        "displayOrder": 1
      },
      "version": 0,
      "createdAt": "2026-09-20T10:15:30Z",
      "updatedAt": "2026-09-20T10:15:30Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

---

### 2.2. Lấy chi tiết tin đăng Người bán

- **Endpoint:** `GET /api/v1/seller/products/{productId}`
- **Bảo mật sở hữu (Ownership Isolation):** Nếu sản phẩm thuộc về Seller khác hoặc không tồn tại, Backend trả về HTTP 404 `PRODUCT_NOT_FOUND` để ngăn chặn việc dò quét tài nguyên (ID Enumeration).

#### Phản hồi thành công (HTTP 200 OK):

Trả về đầy đủ thông tin sản phẩm của Seller kèm trường `version` dùng cho Optimistic Locking.

---

### 2.3. Tạo tin đăng mới (Create Listing)

- **Endpoint:** `POST /api/v1/seller/products`
- **Mô tả:** Tạo tin đăng mới. Trạng thái khởi tạo luôn là `DRAFT`.
- **Ràng buộc nghiệp vụ:**
  - Client không được gửi `sellerId`, `status`, `currency`. Currency mặc định luôn là `VND`.
  - Tiêu đề bắt buộc, trim, tối đa 200 ký tự.
  - Mô tả bắt buộc, trim, tối đa 5000 ký tự.
  - `listedPrice` bắt buộc và `> 0`.
  - `categoryIds` phải có ít nhất một ID; tất cả danh mục phải tồn tại và `is_active = true`. Payload cũ chỉ có `categoryId` vẫn nhận.
  - `condition` phải thuộc danh sách cho phép: `LIKE_NEW`, `GOOD`, `FAIR`, `POOR`, `FOR_PARTS`.
  - `location` tối đa 255 ký tự.

#### Request Body:

```json
{
  "categoryIds": [1, 4],
  "title": "Bàn phím cơ Không dây Keychron K2 V2",
  "listedPrice": 1600000,
  "condition": "LIKE_NEW",
  "description": "Bàn phím gõ êm, pin trâu 2 tuần, kết nối Bluetooth mượt mà.",
  "usageDuration": "3 tháng",
  "defects": "Không có lỗi",
  "repairHistory": "Chưa sửa",
  "includedAccessories": "Hộp zin, cáp sạc Type-C, keycap puller",
  "location": "Bình Thạnh, TP.HCM"
}
```

#### Phản hồi thành công (HTTP 201 Created):

Trả về chi tiết sản phẩm vừa tạo ở trạng thái `DRAFT`.

---

### 2.4. Cập nhật tin đăng (Update Listing)

- **Endpoint:** `PUT /api/v1/seller/products/{productId}`
- **Ràng buộc nghiệp vụ:**
  - Chỉ cho phép sửa tin khi DRAFT, ACTIVE, HIDDEN hoặc REJECTED. Sửa nội dung/media ACTIVE chuyển HIDDEN, cần submit và duyệt lại; PENDING/APPROVED_AWAITING_FEE/transacting không cho sửa.
  - Nếu sản phẩm đang ở trạng thái `RESERVED` hoặc `SOLD`, từ chối với HTTP 409 `PRODUCT_STATE_CONFLICT`.
  - Nếu sản phẩm thuộc về Seller khác, trả về HTTP 404 `PRODUCT_NOT_FOUND`.
  - Hỗ trợ Optimistic Locking: Client có thể gửi trường `version`. Nếu `version` không khớp với database (bị sửa đổi đồng thời bởi phiên khác), Backend trả về HTTP 409 `PRODUCT_VERSION_CONFLICT`.

#### Request Body:

Tương tự như tạo mới, có thể bổ sung trường `"version": 0`.

#### Phản hồi thành công (HTTP 200 OK):

Trả về chi tiết sản phẩm sau khi cập nhật với `version` mới. Client dùng đúng version trong response cho lần sửa kế tiếp; không tự tính version, vì thay đổi collection cũng tham gia optimistic locking.

---

### 2.5. Đăng bán lại sản phẩm đã ẩn (Publish Listing)

- **Endpoint:** `POST /api/v1/seller/products/{productId}/publish`
- **Mô tả:** Đăng bán lại sản phẩm đang ở trạng thái `HIDDEN` sang `ACTIVE`.
- **Ràng buộc chính sách kiểm duyệt thủ công (DP-22 & UC73):**
  - **Chặn đường tắt bypass KTV:** API này **CHỈ** chấp nhận sản phẩm ở trạng thái `HIDDEN`.
  - Nếu sản phẩm ở trạng thái `DRAFT` hoặc `REJECTED`, Seller **bắt buộc** phải qua luồng gửi duyệt (`POST /api/v1/seller/products/{productId}/submit`). Nếu gọi `/publish` khi đang `DRAFT`, `PENDING` hoặc `REJECTED`, backend trả về HTTP 409 `PRODUCT_STATE_CONFLICT`.
  - Trạng thái khác HIDDEN bị từ chối; ACTIVE không phải thao tác publish idempotent.
  - Nếu sản phẩm đang ở trạng thái `RESERVED` hoặc `SOLD`, từ chối với HTTP 409 `PRODUCT_STATE_CONFLICT`.

#### Phản hồi thành công (HTTP 200 OK):

Trả về thông tin chi tiết sản phẩm với `status = "ACTIVE"`.

---

### 2.6. Gửi tin đăng để KTV kiểm duyệt thủ công (Submit Listing for Review - UC15/UC73)

- **Endpoint:** `POST /api/v1/seller/products/{productId}/submit`
- **Mô tả:** Chuyển trạng thái tin đăng từ `DRAFT`, `REJECTED` hoặc `HIDDEN` sang `PENDING` để bước vào hàng chờ xét duyệt thủ công của KTV (UC73).
- **Ràng buộc nghiệp vụ (Rule V6 & DP-22):**
  - Chỉ cho phép gửi duyệt khi sản phẩm ở trạng thái `DRAFT`, `REJECTED` hoặc `HIDDEN`.
  - Bắt buộc phải có **tối thiểu 1 ảnh** (`IMAGE`) và **đúng 1 video quay cận cảnh** (`VIDEO`). Nếu thiếu, trả về HTTP 400/500 lỗi nghiệp vụ.
  - Tự động cập nhật `version` của sản phẩm để nhận diện chính xác nội dung/media đã gửi KTV.
  - Sau khi gửi duyệt thành công, sản phẩm mang trạng thái `PENDING`. Seller không thể tự approve sản phẩm.

#### Phản hồi thành công (HTTP 200 OK):

Trả về thông tin chi tiết sản phẩm với `status = "PENDING"`.

---

### 2.7. Ẩn tin đăng (Hide Listing)

- **Endpoint:** `POST /api/v1/seller/products/{productId}/hide`
- **Mô tả:** Chuyển trạng thái tin đăng từ `ACTIVE` sang `HIDDEN`. Sau khi ẩn, tin đăng lập tức biến mất khỏi kết quả tìm kiếm và trang Marketplace công khai.
- **Ràng buộc:**
  - Nếu sản phẩm đang ở trạng thái `HIDDEN`, giữ nguyên hoặc trả về thành công.
  - Nếu sản phẩm đang ở trạng thái `RESERVED` hoặc `SOLD`, từ chối với HTTP 409 `PRODUCT_STATE_CONFLICT`.

#### Phản hồi thành công (HTTP 200 OK):

Trả về thông tin chi tiết sản phẩm với `status = "HIDDEN"`.

### 2.8. Phí đăng tin đã duyệt — UC72

- GET /api/v1/seller/products/{productId}/listing-fee: owner có SELLER active. Trả productId/title/status/version/quantity, chargeId, policyCode, listedTotal, computedFee, totalPaid, amountDue, paymentState. NOT_ASSESSED khi chưa đánh giá; CONFIRMED khi đã đủ phí. chargeId có thể null, không tạo charge số tiền 0.
- POST /api/v1/seller/products/{productId}/listing-fee/mock-pay: owner/active, body bắt buộc:

```json
{"chargeId":"11111111-1111-1111-1111-111111111111","expectedVersion":2,"commandKey":"unique-retry-key"}
```

chargeId UUID, expectedVersion ≥0, key 1–100 ký tự. Tiền lấy từ charge server. Phải là charge hiện tại ở tin APPROVED_AWAITING_FEE và version khớp; matching retry giữ receipt/reward một lần, key dùng cho charge khác trả COMMAND_KEY_CONFLICT. HTTP 200 FeeSummary mới; state ACTIVE sau receipt/guard/activation. Không gọi provider thật.

Tổng niêm yết = listedPrice × quantity được duyệt. Phí bổ sung = max(phí theo policy tại lượt duyệt − tổng receipt đã đóng cùng tin,0). Giảm giá không hoàn phí cũ. Thu đủ hoặc bổ sung bằng 0 mới mở bán; cấp 10.000 xu tin đầu một lần/Seller lúc thực sự mở bán.

## 3. Moderation Endpoints — UC73

Quyền ADMIN/KTV, reviewerId từ JWT subject; người đăng nhập vai trò khác trả 403. V27 lưu snapshot MANUAL_V27 cho đối chiếu thủ công, không khẳng định đã kiểm AI/eKYC canonical. [Chi tiết rollout/giới hạn](../architecture/MODERATION_LISTING_V27_20261012.md).

### 3.1. Hàng chờ và chi tiết

- GET /api/v1/moderation/products?page=0&size=20: PENDING, page chuẩn hóa ≥0, size 1–50; items/page/size/totalElements/totalPages/hasNext.
- GET /api/v1/moderation/products/{productId}: xem chi tiết kể cả tin đã quyết định để mở lại từ thông báo.
- DTO gồm productId/sellerId/sellerName, title/description/listedPrice/currency/condition, usageDuration/defects/repairHistory/includedAccessories/location, quantity, categories/category/media, status/requiresBuyerEkyc, version/createdAt/updatedAt, history. Giá trị thiếu giữ null; UI “Chưa cung cấp”.
- history: decisionId/reviewerId/decision/reason/contentRevision/reviewedAt. Legacy contentRevision có thể null, không suy diễn thành proof mới. version là entity row version, khác contentRevision; gửi nguyên giá trị lấy từ server, không tự tăng.

### 3.2. Duyệt

POST /api/v1/moderation/products/{productId}/approve:

```json
{"expectedVersion":1,"commandKey":"approval-retry-key"}
```

Tin PENDING, đủ media bắt buộc, active Seller, version đúng. Khóa product, kiểm key lại sau khóa; snapshot/decision/assessment/charge/status/audit/outbox cùng transaction. Phí còn thiếu >0 trả APPROVED_AWAITING_FEE; bằng 0 trả ACTIVE.

```json
{"productId":101,"status":"APPROVED_AWAITING_FEE","message":"Đã duyệt tin. Người bán cần trả 80.000 đồng để mở bán."}
```

Retry cùng actor/product/action/version/key trả kết quả gốc, kể cả sau trả phí; dùng key khác payload trả 409 COMMAND_KEY_CONFLICT. State sai hoặc Seller không còn active trả 409 PRODUCT_STATE_CONFLICT; version sai trả 409 PRODUCT_VERSION_CONFLICT. Decision append-only.

### 3.3. Từ chối

POST /api/v1/moderation/products/{productId}/reject:

```json
{"reason":"Bổ sung video cận cảnh khuyết điểm.","expectedVersion":1,"commandKey":"rejection-retry-key"}
```

reason bắt buộc sau trim, tối đa 500 ký tự; version/key bắt buộc như duyệt. Tin PENDING/version đúng; lưu REJECTED/reason/decision/audit/outbox atomically, HTTP 200 ActionResponse. Retry trả quyết định gốc sau resubmit; key khác thông tin trả 409. Seller thấy reason/reviewedAt qua owner API; public không lộ history/rejection data.

Thông báo submit/decision/open đi qua outbox và API riêng; [Notifications API](NOTIFICATIONS_API.md). Tin chờ phí không có trong public list/detail. HIDDEN đã sửa không publish được nếu proof nội dung/phí không khớp; submit lại để xét bản mới. Dữ liệu lịch sử trước V27 không bị thu phí/thưởng hồi tố khi migrate.

## 4. Mã lỗi chuẩn hóa (Error Codes)

Toàn bộ phản hồi lỗi tuân thủ cấu trúc RFC 7807 / O.G Shop API Conventions:

```json
{
  "code": "PRODUCT_NOT_FOUND",
  "message": "Không tìm thấy sản phẩm với ID 999",
  "timestamp": "2026-09-20T15:30:00Z"
}
```

| HTTP Status | Mã lỗi (`code`) | Ý nghĩa và tình huống |
|---|---|---|
| 400 Bad Request | `VALIDATION_ERROR` | Tham số hoặc body không hợp lệ (tiêu đề trống, giá <= 0, thiếu reason từ chối, v.v.). |
| 400 Bad Request | `INVALID_PRICE_RANGE` | `minPrice` lớn hơn `maxPrice`. |
| 403 Forbidden | `SELLER_REQUIRED` | Tài khoản chưa được kích hoạt quyền Người bán (`SELLER`). |
| 404 Not Found | `PRODUCT_NOT_FOUND` | Sản phẩm không tồn tại, bị ẩn, hoặc thuộc quyền sở hữu của Seller khác. |
| 404 Not Found | `CATEGORY_NOT_FOUND` | Danh mục được chỉ định không tồn tại hoặc đã bị vô hiệu hóa. |
| 409 Conflict | `PRODUCT_STATE_CONFLICT` | Trạng thái tin đăng không phù hợp với thao tác (ví dụ cố publish DRAFT/REJECTED thay vì submit, hoặc duyệt tin khi đã RESERVED/SOLD). |
| 409 Conflict | `PRODUCT_VERSION_CONFLICT` | Xung đột cập nhật đồng thời (Optimistic / Moderation Version Mismatch). Cần tải lại dữ liệu mới nhất. |
| 409 Conflict | `COMMAND_KEY_CONFLICT` | Command key (idempotency key) đã được sử dụng với payload hoặc kết quả khác trước đó. |

## 5. Media local — tên file và đường dẫn

`GET /api/v1/media/{filename}` chỉ đọc file thường trong thư mục media được cấu hình. Tên hợp lệ là UUID chữ thường do server sinh với suffix `jpg`, `jpeg`, `png`, `webp`, `mp4` hoặc `webm`; giữ `jpeg` để đọc upload cũ. Tên không hợp lệ, file không tồn tại, directory hoặc symlink trả 404. Đường dẫn tuyệt đối, dấu phân cách thư mục, `..` và tên khác định dạng này không được chấp nhận.

Khi ghi local, server chọn suffix cố định theo MIME đã được cho phép và tạo UUID mới. `originalFilename` do client gửi không tham gia tên/path lưu; file có sẵn không bị overwrite. Kiểm soát tên/path này không xác thực nội dung ảnh/video hoặc thay thế validation metadata thực tế. Local fallback của Catalog còn tồn tại trong implementation và vẫn cần xử lý theo hướng Cloudinary đã chốt; mục này không phê duyệt fallback thành chính sách.
