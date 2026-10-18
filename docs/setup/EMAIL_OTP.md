# Gửi email OTP đăng ký

Luồng UC01 yêu cầu xác thực email trước khi tạo tài khoản. Khi chưa bật SMTP, API trả `503 OTP_EMAIL_UNAVAILABLE`; không tạo user, không trả mã qua API hoặc log. Đăng nhập tài khoản đã có vẫn hoạt động.

## Gmail

1. Bật Xác minh 2 bước cho tài khoản gửi, rồi tạo mật khẩu ứng dụng tại [Google App Passwords](https://myaccount.google.com/apppasswords).
2. Thêm cấu hình dưới đây vào `.env` local đang dùng. Thay email và mật khẩu ứng dụng bằng giá trị thật; không gửi mật khẩu trong chat hoặc commit vào Git.
3. Khởi động lại backend. Đăng ký bằng email bạn sở hữu, nhận mã và xác nhận; chỉ sau bước này mới có user và 3.000 xu.

```dotenv
OTP_MAIL_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_USERNAME=your-sender@gmail.com
SMTP_PASSWORD=your-app-password
SMTP_FROM=your-sender@gmail.com
```

Gmail yêu cầu xác minh 2 bước để tạo mật khẩu ứng dụng; một số tài khoản tổ chức hoặc Advanced Protection không có tùy chọn này. Đổi mật khẩu Google có thể thu hồi mật khẩu ứng dụng. [Hướng dẫn chính thức](https://support.google.com/accounts/answer/185833). Cấu hình SMTP Gmail dùng `smtp.gmail.com`, STARTTLS cổng 587 và xác thực: [Google SMTP](https://support.google.com/mail/answer/7104828).

`OgShopApplication` đọc tệp tồn tại đầu tiên theo thứ tự `.env`, `../.env`, `backend/.env`, tính từ thư mục chạy. Khi chạy Maven trong `backend`, `.env` ở root repository được đọc nếu không có `backend/.env`. Biến môi trường hoặc Java system properties có ưu tiên cao hơn. Tránh tạo nhiều tệp `.env` chồng nhau; cấu hình một tệp đang dùng. `.env` bị Git ignore.

## Hộp thư local

Dùng Mailpit hoặc SMTP thử nghiệm khác trên localhost; không gửi tới email thật:

```dotenv
OTP_MAIL_ENABLED=true
SMTP_HOST=127.0.0.1
SMTP_PORT=1025
SMTP_AUTH=false
SMTP_STARTTLS=false
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=no-reply@ogshop.test
```

Trên Windows, dùng `127.0.0.1` khi Docker chỉ publish cổng trên IPv4; `localhost` có thể được phân giải thành IPv6 và không kết nối được. Mở hộp thư ở `http://127.0.0.1:8025`. Backend phải khởi động lại sau khi đổi `.env`.

### Email giả và email thật cùng lúc

Backend dùng khối SMTP local phía trên, nhưng đặt `SMTP_FROM` bằng Gmail của tài khoản gửi. Mailpit giữ thư local và chỉ relay những người nhận khớp danh sách. Tạo `.env.mailpit.local` tại root repository; tệp này được `.gitignore` loại khỏi Git:

```dotenv
MP_SMTP_RELAY_HOST=smtp.gmail.com
MP_SMTP_RELAY_PORT=587
MP_SMTP_RELAY_STARTTLS=true
MP_SMTP_RELAY_AUTH=login
MP_SMTP_RELAY_USERNAME=your-sender@gmail.com
MP_SMTP_RELAY_PASSWORD=your-app-password-without-grouping-spaces
MP_SMTP_RELAY_MATCHING=^your-recipient@gmail\.com$
MP_SMTP_RELAY_ALLOWED_RECIPIENTS=^your-recipient@gmail\.com$
MP_SMTP_RELAY_ALL=false
```

Thay sender, mật khẩu ứng dụng và recipient bằng thông tin của bạn. `MATCHING` giới hạn relay tự động; `ALLOWED_RECIPIENTS` giới hạn thao tác release thủ công. Dùng cùng danh sách email sở hữu ở cả hai dòng. Docker `--env-file` nhận regex trực tiếp: dùng một dấu `\` trước dấu chấm, không bọc giá trị bằng dấu nháy. [Mailpit SMTP relay](https://mailpit.axllent.org/docs/configuration/smtp-relay/).

Chạy từ root repository sau khi điền xong tệp cấu hình:

```powershell
docker run -d --name ogshop-mailpit-relay --restart unless-stopped --env-file .env.mailpit.local -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 axllent/mailpit
```

Container chỉ đọc env-file lúc tạo; sửa tệp rồi restart container chưa nạp giá trị mới. Khi tạo lại, bảo toàn thư cũ bằng volume hoặc giữ container trước đó, tránh xóa dữ liệu hộp thư. Nếu đã có container dùng hai cổng, kiểm tra tên/cấu hình rồi dừng container đó trước. [Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).

Kiểm tra email `buyer01@example.test` trong Mailpit và email thật trong Gmail. SMTP local thành công hoặc Gmail AUTH `235` chỉ xác nhận kết nối/xác thực; vẫn cần xác nhận nhận OTP thật và hoàn tất đăng ký qua giao diện.

SMTP có giới hạn connect/read/write 5 giây theo [Spring Boot Email](https://docs.spring.io/spring-boot/3.5/reference/io/email.html). Không bật mail debug ở môi trường chứa OTP thật.

Health check SMTP chỉ bật cùng OTP_MAIL_ENABLED; backend vẫn kiểm sức khỏe database khi chưa cấu hình gửi mail.

## Thời hạn và lỗi

- OTP: 6 số, hiệu lực mặc định 5 phút, tối đa 5 lượt nhập; gửi lại sau 60 giây.
- Tối đa 5 lần gửi/email/giờ và 20 lần/IP/giờ; lấy IP kết nối trực tiếp, không tự tin `X-Forwarded-For`. Khi dùng reverse proxy cần cấu hình hạ tầng tin cậy phù hợp.
- Hồ sơ chờ tồn tại tối đa 30 phút từ lần gửi đầu; gửi lại không kéo dài thời hạn này. Mã cũ bị vô hiệu hóa khi mã mới được gửi thành công.
- Payload chỉ giữ hash mật khẩu và thông tin đăng ký. Xác nhận thành công hoặc hủy xóa payload ngay; worker dọn hồ sơ hết hạn mỗi phút, tối đa 100 bản ghi/lượt, giữ lịch sử challenge.
- `429 OTP_RESEND_TOO_SOON` / `OTP_RATE_LIMIT` có `Retry-After`. Các lỗi nhập mã gồm `OTP_INVALID`, `OTP_ATTEMPTS_EXHAUSTED`, `OTP_EXPIRED`, `OTP_UNAVAILABLE`.
- Lỗi SMTP hoàn tác draft mới; gửi lại thất bại giữ nguyên mã trước. Việc tạo user, thưởng xu, địa chỉ/hồ sơ Người bán và tiêu thụ OTP nằm trong một transaction.

## Migration

Code mới cần Flyway V25. V1–V24 không sửa; V25 bổ sung metadata vào `auth_challenges`, thông tin cửa hàng/địa chỉ trả hàng vào `seller_profiles`, và loại thưởng đăng ký. Không thêm bảng nghiệp vụ hoặc khôi phục refresh sessions.

V25 đã kiểm trên PostgreSQL riêng và áp dụng/validate vào local og_shop ngày 10/10/2026 sau backup/restore; count/hash 47 bảng giữ nguyên. Các môi trường khác vẫn cần kiểm riêng, sao lưu và kiểm tra bản khôi phục trước khi cập nhật. [Bằng chứng/schema](../../database/docs/EMAIL_REGISTRATION_V25.md).
