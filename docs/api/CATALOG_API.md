# Catalog API Specification

Tài liệu đặc tả toàn bộ REST API của module Catalog thuộc hệ thống O.G Shop.

- Version: 1.0.0
- Base URL: `/api/v1`
- Module: Catalog
- Authentication: Bearer JWT trong header `Authorization: Bearer <token>` cho các endpoint Người bán (Seller). Các endpoint công khai không yêu cầu authentication.

---

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
| `categoryId` | number | Không | null | Lọc theo ID danh mục. |
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
  - `categoryId` phải tồn tại và `is_active = true`.
  - `condition` phải thuộc danh sách cho phép: `LIKE_NEW`, `GOOD`, `FAIR`, `POOR`, `FOR_PARTS`.
  - `location` tối đa 255 ký tự.

#### Request Body:

```json
{
  "categoryId": 1,
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

Trả về chi tiết sản phẩm sau khi cập nhật với `version` mới tăng thêm 1.

---

### 2.5. Đăng bán sản phẩm (Publish Listing)

- **Endpoint:** `POST /api/v1/seller/products/{productId}/publish`
- **Mô tả:** Chuyển trạng thái tin đăng từ `DRAFT` hoặc `HIDDEN` sang `ACTIVE`.
- **Ràng buộc:**
  - Nếu sản phẩm đang ở trạng thái `ACTIVE`, giữ nguyên hoặc trả về thành công.
  - Nếu sản phẩm đang ở trạng thái `RESERVED` hoặc `SOLD`, từ chối với HTTP 409 `PRODUCT_STATE_CONFLICT`.

#### Phản hồi thành công (HTTP 200 OK):

Trả về thông tin chi tiết sản phẩm với `status = "ACTIVE"`.

---

### 2.6. Ẩn tin đăng (Hide Listing)

- **Endpoint:** `POST /api/v1/seller/products/{productId}/hide`
- **Mô tả:** Chuyển trạng thái tin đăng từ `ACTIVE` sang `HIDDEN`. Sau khi ẩn, tin đăng lập tức biến mất khỏi kết quả tìm kiếm và trang Marketplace công khai.
- **Ràng buộc:**
  - Nếu sản phẩm đang ở trạng thái `HIDDEN`, giữ nguyên hoặc trả về thành công.
  - Nếu sản phẩm đang ở trạng thái `RESERVED` hoặc `SOLD`, từ chối với HTTP 409 `PRODUCT_STATE_CONFLICT`.

#### Phản hồi thành công (HTTP 200 OK):

Trả về thông tin chi tiết sản phẩm với `status = "HIDDEN"`.

---

## 3. Mã lỗi chuẩn hóa (Error Codes)

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
| 400 Bad Request | `VALIDATION_ERROR` | Tham số hoặc body không hợp lệ (tiêu đề trống, giá <= 0, v.v.). |
| 400 Bad Request | `INVALID_PRICE_RANGE` | `minPrice` lớn hơn `maxPrice`. |
| 403 Forbidden | `SELLER_REQUIRED` | Tài khoản chưa được kích hoạt quyền Người bán (`SELLER`). |
| 404 Not Found | `PRODUCT_NOT_FOUND` | Sản phẩm không tồn tại, bị ẩn, hoặc thuộc quyền sở hữu của Seller khác. |
| 404 Not Found | `CATEGORY_NOT_FOUND` | Danh mục được chỉ định không tồn tại hoặc đã bị vô hiệu hóa. |
| 409 Conflict | `PRODUCT_STATE_CONFLICT` | Không thể sửa hoặc đổi trạng thái khi sản phẩm đang `RESERVED` hoặc `SOLD`. |
| 409 Conflict | `PRODUCT_VERSION_CONFLICT` | Xung đột cập nhật đồng thời (Optimistic Locking Failure). Cần tải lại dữ liệu mới nhất. |
