# O.G Shop Backend

Spring Boot modular monolith cho Old but Gold. Dự án dùng Maven Wrapper 3.3.4 loại `only-script` để mọi máy và CI chạy Maven 3.9.11 mà không cần commit bootstrap JAR.

## Chạy ứng dụng

Thiết lập tối thiểu `JWT_SIGNING_KEY` (32 byte trở lên), `EKYC_INTERNAL_TOKEN`, `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` trước khi chạy. Các secret chỉ được đọc từ biến môi trường. Spring Boot không tự nạp `.env`; file ở repository root là mẫu tên biến và dùng cho Docker Compose.

Ví dụ trong PowerShell 7, cùng cửa sổ sẽ chạy backend:

```powershell
$env:JWT_SIGNING_KEY = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:EKYC_INTERNAL_TOKEN = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:VNPAY_TMN_CODE = 'replace-with-sandbox-tmn-code'
$env:VNPAY_HASH_SECRET = 'replace-with-sandbox-hash-secret'
$env:VNPAY_RETURN_URL = 'http://localhost:5173/payment/vnpay-return'
$env:VNPAY_IPN_URL = 'http://localhost:8080/api/v1/payments/vnpay-ipn'
```

Các giá trị VNPAY ở trên chỉ là placeholder để phát triển chức năng khác; thanh toán thật qua sandbox cần thay bằng thông tin merchant do VNPAY cấp. eKYC cần cùng internal token ở hai service khi nối FastAPI. URL IPN là địa chỉ VNPAY gọi về backend để thông báo kết quả: `localhost` chỉ dùng local, nghiệm thu provider cần địa chỉ HTTPS công khai được cấu hình với merchant. URL return đưa trình duyệt về frontend; trang return đọc trạng thái do server xác nhận.

```powershell
.\mvnw.cmd spring-boot:run
```

Từ repository root có thể chạy quality gate chuẩn; script tự phát hiện `JAVA_HOME` khi biến này chưa được đặt và vẫn gọi chính Maven Wrapper:

```powershell
..\scripts\quality\verify-backend.ps1
```

Ứng dụng sử dụng PostgreSQL theo mặc định. Khởi động database từ repository root bằng `docker compose up -d postgres`.

Database phải có extension `pgvector`; Compose dùng `pgvector/pgvector:pg17` tương ứng PostgreSQL 17. Backend tự chạy Flyway khi khởi động, vì vậy hãy sao lưu và kiểm chứng tuyến V9–V13 trên bản sao trước khi dùng database có dữ liệu. Migrations V1–V13 đã chia sẻ phải giữ nguyên; sửa tiếp bằng migration mới. Callback bảo toàn lý do kiểm duyệt trước V10 chỉ cứu được dữ liệu còn tồn tại; lịch sử đã mất cần backup gốc.

Identity API dùng access JWT 15 phút và refresh token opaque 30 ngày trong cookie HttpOnly. Browser chỉ gọi eKYC qua `/api/v1/ekyc/**`; backend gọi FastAPI bằng internal credential.

## Kiểm thử

```powershell
.\mvnw.cmd verify
```

Context test dùng H2 và không thay thế cho integration test PostgreSQL của các module nghiệp vụ.

Maven Wrapper lưu bản Maven đã tải trong Maven user home mặc định. Không trỏ `MAVEN_USER_HOME` vào đường dẫn dự án có ký tự Unicode trên JDK Windows hiện tại.

## Module boundaries

- `identity`: tài khoản, phiên và Seller verification.
- `catalog`: category, product và media.
- `communication`: chat và offer.
- `commerce`: cart, checkout và order.
- `payment`: payment, hold, release và refund.
- `fulfillment`: shipment, inspection và return.
- `trustsafety`: complaint, dispute, review, report và moderation.
- `platform`: notification, admin và audit.

Đọc tài liệu tương ứng trong `../docs/modules/` trước khi sửa một module.

## Bản tích hợp sơ bộ

- Đã tích hợp lịch sử VNPAY từ main và console Admin/KTV, cùng các bản sửa đã review: backend xác định chủ đơn/số tiền/hạn thanh toán; IPN lưu kết quả trong transaction và xử lý thông báo lặp; frontend return dùng trạng thái server. Ví dụ IPN thành công đến hai lần chỉ chuyển trạng thái một lần.
- Đã bỏ đăng nhập offline tự cấp ADMIN/KTV; tài khoản kiểm duyệt cần role và trạng thái ACTIVE thực tế trên backend. Không còn dùng email hoặc localStorage để giả quyền truy cập.
- Có regression tests cho kiểm duyệt hàng loạt, avatar, phản hồi đơn hàng đến sai thứ tự, UUID và state guards của demo voucher/dispute.
- Console quản trị ngoài luồng kiểm duyệt sản phẩm còn nhiều thao tác localStorage/fixture/alert; thành viên tiếp theo cần nối API, phân quyền, audit và persistence cho từng chức năng.
- Còn mở: thử VNPAY sandbox E2E với IPN công khai, đối soát/hoàn tiền qua provider, triển khai migration trên database ứng dụng và chính sách nghiệp vụ chưa chốt. Tiền đến sau hủy/hết hạn được đánh dấu cần hoàn, không tự gửi lệnh hoàn tới provider.
- Frontend build còn cảnh báo chunk lớn hơn 500 kB; tối ưu chia bundle ở lượt sau. Một test symlink bị skip trên Windows do quyền filesystem; CI Linux có thể kiểm chứng đường này.
