# Identity và eKYC API

Base path: `/api/v1`.

## Auth

| Method | Path | Auth | Hành vi |
|---|---|---|---|
| POST | `/auth/register` | Public + trusted Origin | Tạo user với role `BUYER`, trả access token và set refresh cookie |
| POST | `/auth/login` | Public + trusted Origin | Xác minh BCrypt, trả access token và set refresh cookie |
| POST | `/auth/refresh` | Refresh cookie + trusted Origin | Rotate refresh token single-use và trả access token mới |
| POST | `/auth/refresh/logout` | Refresh cookie + trusted Origin | Revoke token family và xóa cookie |
| GET | `/auth/me` | Bearer JWT | Trả principal/role hiện tại từ backend |

Auth response không chứa refresh token. Response session dùng `Cache-Control: no-store`.

## eKYC gateway

| Method | Path | Auth | Hành vi |
|---|---|---|---|
| POST | `/ekyc/ocr` | Bearer JWT | Validate file và proxy OCR qua internal FastAPI |
| POST | `/ekyc/verify` | Bearer JWT | OCR + request-local face match; chỉ backend có thể cấp `SELLER` |

`/ekyc/verify` nhận multipart `cardFile` và `liveFrame`. Khi provider lỗi, API trả `503 EKYC_UNAVAILABLE`; không fallback sang mock và không tạo verification. Response không bao gồm face embedding.

## Seller Verification (MVP Bypass)

| Method | Path | Auth | Hành vi |
|---|---|---|---|
| POST | `/seller-verification/activate` | Bearer JWT | Kích hoạt quyền `SELLER` cho tài khoản hiện tại qua chế độ `MVP_BYPASS`. Idempotent, không tạo dữ liệu CCCD/sinh trắc học giả. Trả lỗi `400 MVP_ACTIVATION_DISABLED` khi mode bị tắt. |

Response body:
```json
{
  "verificationId": 1,
  "status": "VERIFIED",
  "verificationMethod": "MVP_BYPASS"
}
```

## Profile avatar

Base path `/api/v1/profile`, Bearer JWT của tài khoản hiện tại. `GET /profile` và `PUT /profile` JSON cũ vẫn giữ tương thích; upload mới dùng `PUT /api/v1/profile/with-avatar`, Content-Type multipart/form-data do client/browser đặt boundary.

| Part | Content-Type | Dữ liệu |
|---|---|---|
| profile | application/json | fullName bắt buộc, tối đa 120 ký tự; phoneNumber tùy chọn, định dạng Việt Nam hoặc chuỗi rỗng |
| avatar | image file | JPEG/PNG/WebP được backend kiểm tra nội dung; tối đa 5 MiB và 16 triệu pixels; WebP phải có một frame tĩnh |

Response là ProfileResponse giống GET Profile (id/email/fullName/phoneNumber/avatarUrl/roles/bank fields đã che/verifiedSeller/createdAt). Server lấy owner từ JWT, kiểm tra ACTIVE và dùng URL/publicId do Cloudinary trả về; không nhận userId/publicId trong profile part. JSON có unknown properties bị từ chối theo cấu hình Jackson hiện tại.

- 400 INVALID_AVATAR: file rỗng/sai định dạng/quá hạn mức/không đọc được; lỗi DTO dùng 400 INVALID_INPUT.
- 503 AVATAR_STORAGE_UNAVAILABLE: cấu hình/provider/metadata upload không hợp lệ; không fallback local.
- Endpoint mới được security config bảo vệ đăng nhập. Kiểm tra ACTIVE thất bại dùng 403 ACCESS_DENIED.
- Chọn file frontend chỉ preview; upload khi Lưu. Lưu fullName/phone/avatar trong transaction riêng sau upload; lỗi lưu/commit kích hoạt bù trừ chỉ asset mới, không xóa avatar cũ/demo. Chưa có cleanup/retention ảnh cũ.
- Giới hạn kiểm chứng: suite mock/context pass; chưa có bằng chứng test HTTP multipart/transaction PostgreSQL hoặc upload thật. Ảnh mới bị ẩn sau ảnh cũ lỗi còn chờ sửa.

## Cookie

Local default:

```http
og_refresh=<opaque>; HttpOnly; SameSite=Lax; Path=/api/v1/auth/refresh
```

Production phải dùng HTTPS, `Secure=true`; nếu dùng prefix `__Host-` thì cookie path phải là `/` và không có Domain.
