# Cấu hình đăng nhập và đăng ký Google

Backend dùng Google Identity Services với Web Client ID, kiểm ID token bằng Nimbus (chữ ký RS256/JWK Google, issuer, audience, expiry, email_verified và nonce). Frontend dùng SDK chính thức và lấy Client ID công khai từ `/api/v1/auth/google/config`; không cần Client Secret cho luồng popup này.

1. Mở [Google Cloud Console](https://console.cloud.google.com/) và chọn dự án.
2. Trong Google Auth Platform, cấu hình Branding (tên ứng dụng/email hỗ trợ), Audience External và Test users cho các email dùng thử nếu dự án đang Testing.
3. Vào Clients → Create client → Web application.
4. Authorized JavaScript origins: thêm `http://localhost:5173` và `http://localhost`. Thêm đúng origin nếu dùng cổng khác; origin không chứa `/login` hay `/register`. Dùng HTTPS khi triển khai thật.
5. Điền `GOOGLE_CLIENT_ID=...apps.googleusercontent.com` trong tệp `.env` backend đang nạp rồi restart backend. Frontend không cần thêm biến VITE cho Client ID.

Người dùng đã cung cấp Client ID và TASK-0086 đã thêm vào `.env` root và `backend/.env`, giữ các khóa khác. Biến môi trường/system properties vẫn ưu tiên hơn `.env`. Nút Google bị vô hiệu khi Client ID trống; tệp template không chứa giá trị tài khoản thật.

Google xác minh danh tính trước, backend quyết định:

- Google đã liên kết: dùng tài khoản cũ; nếu Buyer/Seller bật email 2FA, chỉ phát phiên sau OTP đăng nhập.
- Email đã có tài khoản chưa liên kết: OTP `GOOGLE_LINK` chứng minh quyền sở hữu; không tạo user/xu/hồ sơ thứ hai. Nếu có 2FA, tiếp tục OTP đăng nhập riêng sau liên kết.
- Khách mới: nhận ticket một lần có hạn 5 phút; chọn Buyer/Seller, bổ sung thông tin và đặt mật khẩu; xác thực OTP đăng ký rồi tạo user/liên kết/thưởng 3.000 xu trong cùng transaction. Seller vẫn PENDING_EKYC, chưa có quyền SELLER.
- Google email không tự thay email định danh O.G Shop. Khóa/hạn chế LOGIN chỉ hiển thị sau xác minh Google; KTV/Admin dùng phương thức Admin cấp.

`origin is not allowed`: kiểm Authorized JavaScript origins và cổng trình duyệt. `GOOGLE_AUTHENTICATION_FAILED`: chọn lại Google/thử lại; nonce/token cũ không dùng lại. Nếu có lỗi popup do chính sách trình duyệt, kiểm popup/CSP theo tài liệu Google; không tắt bảo vệ trình duyệt.

SMTP vẫn dùng [cấu hình OTP hiện có](EMAIL_OTP.md); Gmail app password không phải Google Client ID. Có thể thử tài khoản Google/email sở hữu, dùng Mailpit nhận OTP ở local và chuyển tiếp các recipient đã chọn. TASK-0086 kiểm Google bằng chữ ký RSA/JWK local và provider mock; chưa xác nhận đăng nhập Google/Gmail thật hay origin của Client trong Google Cloud.

Nguồn: [Google setup](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid), [verify ID tokens](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token), [JavaScript API](https://developers.google.com/identity/gsi/web/reference/js-reference).
