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

## Cookie

Local default:

```http
og_refresh=<opaque>; HttpOnly; SameSite=Lax; Path=/api/v1/auth/refresh
```

Production phải dùng HTTPS, `Secure=true`; nếu dùng prefix `__Host-` thì cookie path phải là `/` và không có Domain.
