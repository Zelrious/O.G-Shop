# Catalog API Specification

Tài liệu đặc tả toàn bộ REST API của module Catalog thuộc hệ thống O.G Shop.

- Version: 1.1.0
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
- **Mô tả:** Trả về danh sách tất cả sản phẩm thuộc quyền sở hữu của Seller (bao gồm cả `DRAFT`, `ACTIVE`, `HIDDEN`, `RESERVED`, `SOLD`).

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
  - Chỉ cho phép sửa tin khi ở trạng thái `DRAFT`, `ACTIVE`, hoặc `HIDDEN`.
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
- **Ràng buộc chính sách kiểm duyệt thủ công (DP-22 & UC70):**
  - **Chặn đường tắt bypass KTV:** API này **CHỈ** chấp nhận sản phẩm ở trạng thái `HIDDEN`.
  - Nếu sản phẩm ở trạng thái `DRAFT` hoặc `REJECTED`, Seller **bắt buộc** phải qua luồng gửi duyệt (`POST /api/v1/seller/products/{productId}/submit`). Nếu gọi `/publish` khi đang `DRAFT`, `PENDING` hoặc `REJECTED`, backend trả về HTTP 409 `PRODUCT_STATE_CONFLICT`.
  - Nếu sản phẩm đang ở trạng thái `ACTIVE`, trả về thành công (idempotent).
  - Nếu sản phẩm đang ở trạng thái `RESERVED` hoặc `SOLD`, từ chối với HTTP 409 `PRODUCT_STATE_CONFLICT`.

#### Phản hồi thành công (HTTP 200 OK):

Trả về thông tin chi tiết sản phẩm với `status = "ACTIVE"`.

---

### 2.6. Gửi tin đăng để KTV kiểm duyệt thủ công (Submit Listing for Review - UC15/UC70)

- **Endpoint:** `POST /api/v1/seller/products/{productId}/submit`
- **Mô tả:** Chuyển trạng thái tin đăng từ `DRAFT` hoặc `REJECTED` sang `PENDING` để bước vào hàng chờ xét duyệt thủ công của KTV (UC70).
- **Ràng buộc nghiệp vụ (Rule V6 & DP-22):**
  - Chỉ cho phép gửi duyệt khi sản phẩm ở trạng thái `DRAFT` hoặc `REJECTED`.
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

### 2.8. Đăng bán lại tin đăng đã ẩn (Publish Listing)

- **Endpoint:** `POST /api/v1/seller/products/{productId}/publish`
- **Mô tả:** Đăng bán lại tin đăng đang ở trạng thái `HIDDEN`.
- **Ràng buộc bảo mật & kiểm duyệt:**
  - Sản phẩm bắt buộc phải đang ở trạng thái `HIDDEN`. Tin ở trạng thái `DRAFT` hoặc `REJECTED` không được publish trực tiếp mà phải gửi duyệt (`POST /api/v1/seller/products/{id}/submit`) để KTV kiểm định.
  - Phải có bằng chứng quyết định `APPROVED` gần nhất từ KTV trong `product_moderation_decisions`. Các tin `HIDDEN` cũ (legacy data) chưa từng có quyết định duyệt sẽ bị chặn với HTTP 409 `PRODUCT_STATE_CONFLICT`, yêu cầu gửi duyệt.
  - Quyết định `APPROVED` phải có `product_version` khớp chính xác với `content_revision` hiện tại của tin đăng. Nếu Seller đã thay đổi tiêu đề, mô tả, giá, tình trạng, danh mục, hoặc thêm/xóa ảnh/video sau khi được duyệt, hệ thống từ chối với HTTP 409 `PRODUCT_STATE_CONFLICT` ("Nội dung hoặc media của tin đăng đã thay đổi so với phiên bản được KTV phê duyệt...").

#### Phản hồi thành công (HTTP 200 OK):

Trả về thông tin chi tiết sản phẩm với `status = "ACTIVE"`.

---

## 3. Moderation Endpoints (Dành cho KTV kiểm duyệt tin - UC70)

**Trạng thái triển khai và giới hạn kiểm chứng:**

Kiểm tra độc lập còn ghi nhận hai giới hạn lifecycle: decision V9 có entity version cũ bị dùng như proof content revision mới sau V10; expectedVersion hiện chỉ là content revision, không nhận diện round reject/resubmit không sửa. V10 còn sửa reason lịch sử; SQL DELETE case có false positive. Các vấn đề này chờ sửa, chưa nghiệm thu toàn bộ UC70.

- Đã tách `content_revision` độc lập với `@Version` entity để kiểm soát chính xác phiên bản nội dung KTV duyệt qua Flyway V10.
- `expectedVersion` và `commandKey` là **BẮT BUỘC** trên cả Approve và Reject (`@Valid @RequestBody`). Request thiếu body hoặc sai định dạng trả về HTTP 400 Bad Request ngay lập tức.
- Replay hỗ trợ trả về kết quả nguyên gốc đã ghi (`ACTIVE` hoặc `REJECTED`), không phụ thuộc vào trạng thái hiện tại của sản phẩm sau resubmit.
- Bảng `product_moderation_decisions` được bảo vệ toàn vẹn bằng database trigger append-only (chặn mọi `UPDATE` và `DELETE`).
- Xác thực: Bearer JWT. Role KTV đã có trong migration V11; tài khoản test `ktv@gmail.com` dùng KTV. API hiện mới kiểm tra đăng nhập, chưa giới hạn ADMIN/KTV. reviewerId được trích xuất trực tiếp từ principal JWT và lưu vào bản ghi quyết định cùng Clock backend.

### 3.1. Lấy danh sách hàng chờ tin cần kiểm duyệt

- **Endpoint:** `GET /api/v1/moderation/products`
- **Quyền:** Authenticated.
- **Query Parameters:**
  - `page`: số trang (mặc định 0).
  - `size`: kích thước trang (1 đến 50, mặc định 20).
- **Mô tả:** Trả về danh sách phân trang các tin đăng mang trạng thái `PENDING`, kèm toàn bộ media (ảnh/video), danh mục đã gắn, cờ eKYC Người mua và phiên bản nội dung `version` (`content_revision`) phục vụ kiểm soát xung đột đồng thời.

#### Phản hồi thành công (HTTP 200 OK):

```json
{
  "items": [
    {
      "productId": 101,
      "title": "Máy ảnh cơ Canon AE-1",
      "description": "Máy chụp tốt, cơ êm, kèm lens 50mm f/1.8",
      "listedPrice": 3500000,
      "condition": "GOOD",
      "status": "PENDING",
      "sellerId": 10,
      "category": { "categoryId": 1, "categoryName": "Máy ảnh", "slug": "cameras", "displayOrder": 1 },
      "categories": [
        { "categoryId": 1, "categoryName": "Máy ảnh", "slug": "cameras", "displayOrder": 1 }
      ],
      "media": [
        { "mediaId": 1, "mediaType": "IMAGE", "mediaUrl": "https://example.com/img1.jpg", "displayOrder": 1 },
        { "mediaId": 2, "mediaType": "VIDEO", "mediaUrl": "https://example.com/vid1.mp4", "displayOrder": 2 }
      ],
      "requiresBuyerEkyc": true,
      "createdAt": "2026-10-01T10:00:00Z",
      "version": 1,
      "updatedAt": "2026-10-01T10:05:00Z"
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

### 3.2. Lấy chi tiết tin đăng chờ duyệt

- **Endpoint:** `GET /api/v1/moderation/products/{productId}`
- **Quyền:** Authenticated.
- **Mô tả:** Lấy toàn bộ chi tiết kiểm định của tin đăng (thời gian đã dùng, khuyết điểm, lịch sử sửa chữa, phụ kiện kèm theo, media, version).

---

### 3.3. Phê duyệt tin đăng (Approve - UC70)

- **Endpoint:** `POST /api/v1/moderation/products/{productId}/approve`
- **Quyền:** Authenticated (lấy reviewerId từ JWT subject).
- **Request Body (bắt buộc):**
  ```json
  {
    "expectedVersion": 1,
    "commandKey": "ck-app-101-1-1696320000"
  }
  ```
- **Ràng buộc & Xử lý đồng thời:**
  - `expectedVersion` (@NotNull, @Min(0)) và `commandKey` (@NotBlank, @Size(max=100)) là bắt buộc. Thiếu body, JSON null hoặc tham số sai trả về HTTP 400 Bad Request.
  - Tin phải ở trạng thái `PENDING`. Nếu trạng thái khác (ACTIVE, REJECTED, RESERVED, SOLD), trả về HTTP 409 `PRODUCT_STATE_CONFLICT`.
  - Phải có đủ tối thiểu 1 ảnh và 1 video (Rule V6).
  - `expectedVersion` phải khớp chính xác với `content_revision` hiện tại của tin trong DB; nếu không khớp (tin đã bị sửa), trả về HTTP 409 `PRODUCT_VERSION_CONFLICT`.
  - Hỗ trợ idempotent replay: nếu gọi lại với cùng key và thông tin khớp, trả về kết quả gốc (`ACTIVE`) không tạo bản ghi mới; nếu cùng key nhưng khác actor, product, action hoặc version, trả về HTTP 409 `COMMAND_KEY_CONFLICT`.
  - Đổi trạng thái sang `ACTIVE`, lưu bản ghi quyết định `APPROVED` vào bảng `product_moderation_decisions` (với reason = NULL), và ghi `audit_logs` diễn ra trong cùng một transaction nguyên tử (atomic).

#### Phản hồi thành công (HTTP 200 OK):

```json
{
  "productId": 101,
  "status": "ACTIVE",
  "message": "Phê duyệt tin đăng thành công."
}
```

---

### 3.4. Từ chối tin đăng (Reject - UC70)

- **Endpoint:** `POST /api/v1/moderation/products/{productId}/reject`
- **Quyền:** Authenticated (lấy reviewerId từ JWT subject).
- **Request Body (bắt buộc):**
  ```json
  {
    "reason": "Video quay cận cảnh bị mờ nhòe, không nhìn rõ vết xước màn hình.",
    "expectedVersion": 1,
    "commandKey": "ck-rej-101-1-1696320000"
  }
  ```
- **Ràng buộc:**
  - `reason` là trường **BẮT BUỘC**, không được null, rỗng hoặc toàn khoảng trắng, độ dài tối đa 500 ký tự. `expectedVersion` (@NotNull, @Min(0)) và `commandKey` (@NotBlank, @Size(max=100)) là bắt buộc. Request thiếu body, JSON null hoặc lý do không hợp lệ trả về HTTP 400 Bad Request ngay lập tức.
  - Tin phải ở trạng thái `PENDING`. Trạng thái khác trả về HTTP 409 `PRODUCT_STATE_CONFLICT`.
  - Nếu `expectedVersion` không khớp với `content_revision`, trả về HTTP 409 `PRODUCT_VERSION_CONFLICT`.
  - Hỗ trợ idempotent replay: cùng command key trả về kết quả `REJECTED` gốc kể cả sau khi Seller đã resubmit lên `PENDING`. Khác thông tin trả về HTTP 409 `COMMAND_KEY_CONFLICT`.
  - Đổi trạng thái sang `REJECTED`, lưu lý do và quyết định vào bảng `product_moderation_decisions`, và ghi `audit_logs` atomically.
  - Seller có thể xem lại lý do từ chối thực tế này khi tra cứu danh sách hoặc chi tiết tin của mình (`GET /api/v1/seller/products/{id}`). Public API tuyệt đối không để lộ dữ liệu kiểm duyệt nội bộ.

#### Phản hồi thành công (HTTP 200 OK):

```json
{
  "productId": 101,
  "status": "REJECTED",
  "message": "Đã từ chối tin đăng: Video quay cận cảnh bị mờ nhòe, không nhìn rõ vết xước màn hình."
}
```

---

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
