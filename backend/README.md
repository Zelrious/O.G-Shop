# O.G Shop Backend

Spring Boot modular monolith cho Old but Gold. Dự án dùng Maven Wrapper 3.3.4 loại `only-script` để mọi máy và CI chạy Maven 3.9.11 mà không cần commit bootstrap JAR.

## Chạy ứng dụng

Thiết lập tối thiểu `JWT_SIGNING_KEY` (32 byte trở lên) và `EKYC_INTERNAL_TOKEN` trước khi chạy. Các secret chỉ được đọc từ biến môi trường.

```powershell
.\mvnw.cmd spring-boot:run
```

Từ repository root có thể chạy quality gate chuẩn; script tự phát hiện `JAVA_HOME` khi biến này chưa được đặt và vẫn gọi chính Maven Wrapper:

```powershell
..\scripts\quality\verify-backend.ps1
```

Ứng dụng sử dụng PostgreSQL theo mặc định. Khởi động database từ repository root bằng `docker compose up -d postgres`.

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
