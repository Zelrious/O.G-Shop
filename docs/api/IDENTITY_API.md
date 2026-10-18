# Identity và eKYC API

Base path: `/api/v1`.

## Auth

| Method | Path | Auth | Hành vi |
|---|---|---|---|
| POST | `/auth/register` | Public + trusted Origin | `202 OTP_REQUIRED`; gửi OTP, lưu draft hash mật khẩu, chưa tạo user/token/cookie |
| POST | `/auth/register/verify` | Public + trusted Origin | Xác nhận mã; tạo user `BUYER`, thưởng 3.000 xu một lần và bắt đầu phiên |
| POST | `/auth/register/resend` | Public + trusted Origin | Gửi mã mới sau cooldown; `202`, challengeId mới, mã cũ vô hiệu |
| POST | `/auth/register/cancel` | Public + trusted Origin | `204`; hủy draft, không tạo tài khoản; gọi lại được |
| POST | `/auth/login` | Public + trusted Origin | Xác minh BCrypt; `200` phiên hoặc `202 OTP_REQUIRED` nếu Buyer/Seller bật email 2FA |
| POST | `/auth/login/verify` | Public + trusted Origin | OTP `LOGIN_EMAIL_2FA` đúng mới phát token/cookie |
| POST | `/auth/password-reset` | Public + trusted Origin | `202` chờ OTP với phản hồi giống nhau cho email đủ điều kiện và email không có tài khoản |
| POST | `/auth/password-reset/verify` | Public + trusted Origin | `200 RESET_READY`, trả grant mới dùng một lần để đặt mật khẩu |
| POST | `/auth/password-reset/complete` | Public + trusted Origin | Lưu mật khẩu và tiêu thụ grant trong cùng transaction; `204`, xóa cookie, phải đăng nhập lại |
| POST | `/auth/challenge/resend` | Public + trusted Origin | `202`, challenge OTP mới sau cooldown, mã cũ vô hiệu |
| POST | `/auth/challenge/cancel` | Public + trusted Origin | Hủy OTP hoặc reset grant; `204`, giữ lịch sử |
| GET | `/auth/google/config` | Public | Client ID công khai và cờ enabled; không trả secret |
| POST | `/auth/google/nonce` | Public + trusted Origin | Nonce một lần, có hạn và giới hạn theo requester |
| POST | `/auth/google` | Public + trusted Origin | Xác minh ID token; trả phiên, OTP login/link hoặc ticket đăng ký |
| POST | `/auth/google/link/verify` | Public + trusted Origin | OTP liên kết tài khoản cũ; nếu bật 2FA tiếp tục OTP đăng nhập riêng |
| POST | `/auth/session` | Access cookie + trusted Origin | Khôi phục đăng nhập và tải quyền hiện tại, giữ nguyên thời điểm hết hạn |
| POST | `/auth/logout` | Trusted Origin | Xóa cookie đăng nhập; gọi lại được khi cookie đã hết hạn |
| GET | `/auth/me` | Bearer JWT | Trả principal/role hiện tại từ backend |

Không còn refresh token, endpoint gia hạn hoặc bảng `refresh_sessions` từ V24. Response session dùng `Cache-Control: no-store`. Thời hạn mặc định 15 phút theo `AUTH_ACCESS_TOKEN_TTL`; hết hạn phải đăng nhập lại. Khôi phục không gửi lại cookie và không kéo dài thời hạn. Frontend giữ access token trong bộ nhớ, xóa trạng thái đăng nhập khi hết hạn hoặc nhận 401. API nghiệp vụ chỉ chấp nhận Bearer JWT, không dùng cookie để cấp quyền.

Đăng xuất xóa cookie và trạng thái trên trình duyệt; không thu hồi riêng một token đã sao chép. Từ TASK-0086/V26, JWT có claim `cv` bằng `users.credential_version`; mọi Bearer request và cookie restore đối chiếu database. Đổi/reset password hash tăng version nguyên tử, vô hiệu mọi token/cookie cũ. Token trước V26 thiếu `cv` chỉ hợp lệ khi version còn 0; khóa/ngừng tài khoản hoặc hạn chế LOGIN cũng chặn phiên. Deadline cố định V24 vẫn giữ, không thêm refresh session.

### Hợp đồng đăng ký email

`POST /auth/register` nhận `fullName`, `email`, `password`, `phoneNumber` tùy chọn và `accountType` (`BUYER` hoặc `SELLER`); `googleTicket` tùy chọn cho hồ sơ đã được Google xác minh. Mật khẩu mới ít nhất 8 ký tự, tối đa 72 byte UTF-8; đăng nhập vẫn cho mật khẩu cũ ngắn hơn 8 ký tự. Seller phải có `seller`: `storeName`, `contactPhone`, `bankName`, `accountNumber`, `accountHolder`, `pickupAddress` và `returnAddress`; mỗi địa chỉ gồm `province`, `district`, `ward`, `detailAddress`. Buyer không gửi hồ sơ Seller.

Kết quả chờ gồm `status=OTP_REQUIRED`, `challengeId`, `email`, `expiresAt`, `resendAt`, `attemptsRemaining`; không có token/user/cookie. Xác nhận nhận `challengeId` + `code` 6 số; gửi lại/hủy chỉ nhận `challengeId`. Xác nhận thành công trả principal/token/`expiresIn`, `awardedCoins=3000`, `nextStep=PROFILE|SELLER_VERIFICATION`. Principal có `sellerApprovalRequired` cho tài khoản cần quy trình phê duyệt Seller.

Đăng ký Seller chỉ lưu `PENDING_EKYC`, hai địa chỉ thuộc đúng user và bank snapshot; mọi tài khoản mới chỉ có `BUYER`. Mã đã dùng/hủy/vô hiệu hóa không được dùng lại; xác nhận đồng thời được tuần tự hóa theo email. Nếu lưu user/thưởng/hồ sơ thất bại, transaction hoàn tác cả tiêu thụ mã. Lượt nhập sai được commit trước khi trả lỗi.

[Cấu hình SMTP, giới hạn gửi/nhập và mã lỗi](../setup/EMAIL_OTP.md). Tất cả response đăng ký dùng `Cache-Control: no-store`.

Phản hồi `429` của giới hạn gửi trả `Retry-After` dưới dạng số giây chờ. CORS expose chính xác header này qua `Access-Control-Expose-Headers: Retry-After` cho frontend origin được cấu hình; không thêm trường JSON. Frontend hiển thị countdown khi bắt đầu đăng ký Buyer/Seller và gửi lại OTP, giữ dữ liệu và cho gửi lại khi hết hạn. Backend tiếp tục là nơi thực thi giới hạn.

### Hợp đồng OTP đăng nhập và đặt lại mật khẩu — V26

Challenge mới có `status=OTP_REQUIRED`, `challengeId`, `email`, `expiresAt`, `resendAt`, `attemptsRemaining`, `purpose`. Purpose OTP gồm `LOGIN_EMAIL_2FA`, `PASSWORD_RESET`, `GOOGLE_LINK`; các endpoint verify chỉ nhận đúng purpose, `challengeId` và `code` sáu số. Chờ OTP không có access token, user hoặc cookie; frontend giữ trạng thái chưa đăng nhập. Email 2FA chỉ áp dụng Buyer/Seller đã bật cờ, không áp dụng Admin/KTV; màn bật/tắt 2FA và face verification UC09 chưa thuộc lượt sửa này.

Reset bắt đầu chỉ nhận `email`. Tài khoản đủ điều kiện phải có email đã xác minh, ACTIVE, Buyer/Seller và không bị hạn chế LOGIN. Email khác nhận decoy có cùng status/shape/cooldown nhưng không gửi thư và không thể cấp grant. Mail reset được xếp hàng sau commit, nên SMTP chậm/lỗi không làm response phụ thuộc tài khoản có tồn tại. Hàng đợi giới hạn và nằm trong bộ nhớ; restart/đầy hàng đợi có thể mất thư, người dùng gửi lại sau cooldown. Đây chưa phải durable outbox.

OTP reset đúng trả `status=RESET_READY` với `challengeId` **mới**, hết hạn theo `expiresAt`; purpose trong response vẫn là `PASSWORD_RESET`. Server lưu grant riêng `PASSWORD_RESET_COMPLETE`, dùng một lần. Complete nhận `challengeId`, `password`, `confirmPassword`; sai xác nhận/chính sách mật khẩu hoặc lỗi lưu giữ mật khẩu cũ. Thành công giữ email/cờ 2FA, thu hồi mọi phiên và yêu cầu đăng nhập lại. Không thể complete bằng challenge gửi email hoặc OTP đã dùng.

Mặc định OTP/grant có hạn 5 phút, cooldown 60 giây, 5 lượt nhập, 5 lần gửi/email/giờ và 20 lần/requester/giờ; dùng cấu hình OTP hiện có. Rate tính chung các challenge auth có subject/requester tương ứng. Resend/cancel nhận `challengeId`; resend trả ID mới và code cũ không dùng lại. Lượt nhập sai được commit; xác nhận đồng thời chỉ một lượt thành công. Mã lỗi gồm `OTP_INVALID`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXHAUSTED`, `OTP_UNAVAILABLE`, `OTP_RESEND_TOO_SOON`, `OTP_RATE_LIMIT`, `PASSWORD_CONFIRMATION_MISMATCH`.

`403 ACCOUNT_RESTRICTED` sau credential hợp lệ trả `message` (lý do), `accountStatus`, `supportInstruction`, `allowedFunctions=[CONTACT_SUPPORT]`; password sai vẫn trả lỗi đăng nhập chung. Chỉ thực thi trạng thái tài khoản/hạn chế LOGIN tại auth; quyền hạn chế từng hành động và nghĩa vụ đơn cũ UC67 chưa nghiệm thu trong phạm vi này.

### Hợp đồng Google — V26

Frontend lấy config và nonce rồi SDK Google trả ID token; `POST /auth/google` nhận `nonceId` và `credential`. Backend kiểm chữ ký RS256/JWK Google, issuer, audience, expiry, email_verified, nonce và tiêu thụ nonce một lần. Không tin profile client tự giải mã. [Cấu hình và cách thử](../setup/GOOGLE_AUTH.md).

- Google đã liên kết: đăng nhập đúng user, hoặc chờ OTP `LOGIN_EMAIL_2FA` nếu có cờ.
- Email có sẵn chưa liên kết: OTP `GOOGLE_LINK`; xác nhận link không tạo user/xu/hồ sơ lần hai. Nếu bật 2FA, cần OTP đăng nhập riêng.
- Email mới: `202 REGISTRATION_REQUIRED`, `registrationTicket`, `email`, `fullName`. Ticket có hạn 5 phút và dùng một lần; `/auth/register` phải gửi đúng email/ticket, password và thông tin Buyer/Seller. OTP đăng ký đúng mới tạo user/link/thưởng trong cùng transaction. Seller giữ PENDING_EKYC/BUYER.

Google không dành cho tài khoản Admin/KTV do Admin cấp. Thiếu Client ID trả `GOOGLE_NOT_CONFIGURED`; token/nonce không hợp lệ trả `GOOGLE_AUTHENTICATION_FAILED` hoặc `OTP_UNAVAILABLE`. Client ID đã cấu hình local, nhưng chưa kiểm Google/Gmail thật hoặc xác nhận origin trong Google Cloud. [Migration](../../database/docs/AUTH_FLOWS_V26.md), [bằng chứng local](../../output/auth-fixes-2026-10-11/README.md).

## eKYC gateway

| Method | Path | Auth | Hành vi |
|---|---|---|---|
| POST | `/ekyc/ocr` | Bearer JWT | Validate file và proxy OCR qua internal FastAPI |
| POST | `/ekyc/verify` | Bearer JWT | OCR + request-local face match; chỉ backend có thể cấp `SELLER` |

`/ekyc/verify` nhận multipart `cardFile` và `liveFrame`. Khi provider lỗi, API trả `503 EKYC_UNAVAILABLE`; không fallback sang mock và không tạo verification. Response không bao gồm face embedding.

Từ V25, tài khoản mới theo quy trình phê duyệt Seller bị chặn ở bước ghi kết quả cấp quyền qua AI/MVP. Wizard eKYC và KTV duyệt hồ sơ mới chưa được nối trong đợt 1; hành vi legacy vẫn giữ để tương thích dữ liệu cũ.

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
