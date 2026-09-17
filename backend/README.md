# O.G Shop Backend

Spring Boot modular monolith cho Old but Gold.

## Chạy ứng dụng

```powershell
.\mvnw.cmd spring-boot:run
```

Nếu JDK trên Windows không nạp được Maven Wrapper qua đường dẫn tuyệt đối có ký tự Unicode, chạy quality gate bằng wrapper tương đối:

```powershell
..\scripts\quality\verify-backend.ps1
```

Ứng dụng sử dụng PostgreSQL theo mặc định. Khởi động database từ repository root bằng `docker compose up -d postgres`.

## Kiểm thử

```powershell
.\mvnw.cmd verify
```

Context test dùng H2 và không thay thế cho integration test PostgreSQL của các module nghiệp vụ.

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
