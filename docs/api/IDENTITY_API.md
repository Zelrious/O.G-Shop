# Identity và eKYC API

Base path: `/api/v1`.

## Auth

| Method | Path | Auth | Hành vi |
|---|---|---|---|
| POST | `/auth/register` | Public + trusted Origin | Tạo user với role `BUYER`, trả access token và cookie có cùng thời hạn |
| POST | `/auth/login` | Public + trusted Origin | Xác minh BCrypt, bắt đầu đăng nhập có thời hạn cố định |
| POST | `/auth/session` | Access cookie + trusted Origin | Khôi phục đăng nhập và tải quyền hiện tại, giữ nguyên thời điểm hết hạn |
| POST | `/auth/logout` | Trusted Origin | Xóa cookie đăng nhập; gọi lại được khi cookie đã hết hạn |
| GET | `/auth/me` | Bearer JWT | Trả principal/role hiện tại từ backend |

Không còn refresh token, endpoint gia hạn hoặc bảng `refresh_sessions` từ V24. Response session dùng `Cache-Control: no-store`. Thời hạn mặc định 15 phút theo `AUTH_ACCESS_TTL`; hết hạn phải đăng nhập lại. Khôi phục không gửi lại cookie và không kéo dài thời hạn. Frontend giữ access token trong bộ nhớ, xóa trạng thái đăng nhập khi hết hạn hoặc nhận 401. API nghiệp vụ chỉ chấp nhận Bearer JWT, không dùng cookie để cấp quyền.

Đăng xuất xóa cookie và trạng thái trên trình duyệt. Access token đã sao chép vẫn có hiệu lực tới thời điểm hết hạn ban đầu; không còn thu hồi theo từng phiên. Đăng ký hiện chưa thực hiện OTP/Google theo chính sách báo cáo; đây vẫn là công việc tiếp nối TASK-0006/0007. `auth_challenges` giữ dữ liệu OTP và xác thực nhanh, không bị xóa trong TASK-0073.

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
og_access=<access-jwt>; HttpOnly; SameSite=Lax; Path=/api/v1/auth; Max-Age=900
```

Tên/path/Secure cấu hình bằng `AUTH_SESSION_COOKIE_NAME`, `AUTH_SESSION_COOKIE_PATH`, `AUTH_SESSION_COOKIE_SECURE`. Production dùng HTTPS và `Secure=true`. Cookie `og_refresh` với path cũ mặc định được xóa khi đăng nhập/đăng xuất. Nếu dùng prefix `__Host-`, cookie path phải là `/` và không có Domain.
