# Core feature development baseline

Ngày đối chiếu: 2026-10-04. Phạm vi hiện tại là ba luồng Profile, mua hàng và đăng/quản lý tin. Code đã có chức năng cơ bản; đây là mốc phát triển, chưa nghiệm thu toàn bộ yêu cầu hoặc sẵn sàng triển khai production.

## Chức năng đã triển khai

| Luồng | Hành vi hiện có |
|---|---|
| Profile | Xem/sửa hồ sơ, đổi mật khẩu, địa chỉ/mặc định, ba trường ngân hàng Seller và che số tài khoản; avatar upload Cloudinary khi Lưu, preview/Bỏ chọn và đồng bộ session/header |
| Catalog/listing | Tìm/lọc/xem sản phẩm; một sản phẩm thuộc nhiều danh mục; Seller tạo/sửa nháp, upload media, submit PENDING; màn duyệt/từ chối thủ công, không chạy AI |
| Mua hàng | Mua ngay mở checkout địa chỉ/phương thức/voucher; tạo đơn và giữ hàng một giờ; QR/bill và payment mô phỏng; thông báo đơn chưa thanh toán khi trở về Home theo đường thoát trong UI |
| Đơn hàng | Buyer/Seller xem danh sách/chi tiết; lọc trạng thái, phân trang 10 đơn mặc định, cap 50 và thứ tự createdAt DESC/id DESC; hủy/xác nhận theo trạng thái |
| Dữ liệu test | ADMIN, BUYER, BUYER+SELLER, KTV; sản phẩm/media/nhiều danh mục, voucher, đơn và payment fixture, helpers xác minh Buyer/hết hạn |

Profile avatar nhận multipart `PUT /api/v1/profile/with-avatar`; xem [Identity API](../api/IDENTITY_API.md). Listing/moderation và nhiều danh mục tại [Catalog API](../api/CATALOG_API.md).

Checkout: `GET /api/v1/commerce/checkout/preview`, `POST /api/v1/commerce/checkout/buy-now`. Đơn Buyer: `/api/v1/commerce/orders`; đơn Seller: `/api/v1/commerce/seller/orders`. Payment mock: `GET /api/v1/payments/orders/{orderId}`, `POST /api/v1/payments/mock-process`. Các endpoint này yêu cầu đăng nhập; đọc DTO/controller tương ứng để biết payload.

## Phần còn thiếu

- Avatar: sau khi URL cũ tải lỗi, ảnh mới/preview có thể vẫn bị ẩn. Chưa có bằng chứng HTTP multipart/rollback PostgreSQL/upload Cloudinary thật.
- Orders: Seller đổi tab/trang trong lúc xác nhận đơn, kết quả thao tác về muộn có thể tải query cũ vào view hiện tại. Chưa kiểm chứng query PostgreSQL/tie-break thật hoặc checkout đồng thời/expiry end-to-end.
- Moderation: API mới kiểm tra đăng nhập, chưa giới hạn ADMIN/KTV. Proof cũ V9→V10, command cũ qua reject/resubmit và bảo toàn reason lịch sử còn lỗi. SQL DELETE/concurrency tests còn khoảng trống; test pass không chứng minh các lỗi này đã được sửa.
- Media Catalog còn fallback local khi Cloudinary lỗi và có duration giả định cho video thiếu metadata; cần xử lý để khớp hướng Cloudinary và validation thời lượng thực tế. Avatar không fallback local.
- Checkout mới kiểm tra eKYC khi tạo đơn; preview chưa có cùng guard. Idempotency/concurrency, đồng bộ báo giá và các contract phương thức/voucher cần hoàn thiện.
- Phí sàn hiện theo baseline tạm, ship code 30.000đ chưa là policy cuối cùng. Payment/QR chỉ phục vụ mô phỏng, chưa có provider tiền thật/payout/vận đơn.
- Schema code đến V11, DB local từng kiểm tra history mới đến V8. Kiểm chứng nâng cấp trên DB riêng/bản sao trước dùng; role KTV hiện diện không chứng minh history đã đồng bộ.
- Cart/chat/offer, AI kiểm duyệt, eKYC/KTV xét duyệt đầy đủ và chức năng hậu giao dịch thuộc các đợt tiếp theo.

## Cấu hình và dữ liệu

Điền biến môi trường backend theo [.env.example](../../.env.example); không đưa secret vào frontend hoặc Git. [Migrations](../../backend/src/main/resources/db/migration/README.md) là nguồn schema, [seed local/test](../../database/seed/README.md) tách khỏi Flyway. Tài khoản fixture dùng mật khẩu 12345678, chỉ phục vụ development/test.

## Kiểm tra

Backend không thuộc hai PostgreSQL suites:

```powershell
cd backend
.\mvnw.cmd -o '-Dtest=!*PostgresTest' test
```

Frontend:

```powershell
cd frontend
npm test
npm run typecheck
npm run lint
npm run build
```

Đợt đóng gói đã chạy trên snapshot lấy từ Git: backend tại `52af08c` đạt 114 tests, 0 failures/errors/skipped; frontend tại `01689fe` đạt 59 tests/12 suites, typecheck/lint/build pass. Hai snapshot có cùng backend tree. Đây gồm mock/context/H2; không chứng minh PostgreSQL, Cloudinary thật hoặc browser E2E. Kiểm tra PostgreSQL theo [hướng dẫn](../../database/tests/README.md) trên DB riêng, không trỏ test vào DB ứng dụng. Build có cảnh báo chunk lớn hơn 500 kB.

Whitespace kiểm tra từng đợt đạt, riêng hai khoảng trắng cuối dòng có sẵn trong migration V6 được giữ nguyên để không đổi checksum của migration đã áp dụng. Không có thay đổi SQL hoặc database ứng dụng trong đợt đóng gói.
