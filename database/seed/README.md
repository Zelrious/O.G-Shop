# Dữ liệu test O.G Shop

Bộ seed dành cho PostgreSQL local/test có schema danh mục tới **V8** và **role KTV**. Code đã có migration V11 bổ sung KTV; không suy ra đã chạy V11 chỉ từ việc role hiện diện. Seed chỉ thêm dữ liệu, không tự thay đổi schema; chỉ dùng dữ liệu giả, không có API key/KYC thật/payout thật. Role trên DB local đã được kiểm tra, chưa chạy lại seed sau thay đổi role.

## Nạp dữ liệu

Từ thư mục repository `O.G Shop`, khi Docker/PostgreSQL đã chạy:

```powershell
.\database\seed\load_test_data.ps1
```

Mặc định nạp vào container `og-shop-postgres-1`, database/user `og_shop` (database local đã kiểm tra, cổng host 55432). Script sử dụng `docker exec psql`, không cần cài psql trên Windows và không đọc/in `.env`. Nếu database/container khác, chỉ định rõ:

```powershell
.\database\seed\load_test_data.ps1 -ContainerName '<container-test>' -DatabaseName '<database-test>' -DatabaseUser '<user>'
```

Nếu dùng pgAdmin/DBeaver, mở kết nối đúng database test rồi chạy **toàn bộ** [development_test_data.sql](development_test_data.sql). Không chạy từng đoạn: file có transaction và manifest đánh dấu lần nạp. Sau đó chạy [verify_test_data.sql](verify_test_data.sql), hoặc:

```powershell
.\database\seed\load_test_data.ps1 -VerifyOnly
```

TASK-0046 không tự nạp lại database ứng dụng. DB vừa kiểm tra có đủ KTV nhưng history Flyway chỉ đến V8; việc đồng bộ history/migrations đang để đợt sửa sau theo yêu cầu owner. Database test mới cần chuẩn bị schema/role tương thích bằng migrations đã kiểm chứng trên DB riêng; V1 cần extension `vector`. Không dùng restart backend/Flyway repair/reset trên DB đang dùng để tự xử lý chênh lệch này.

Nếu đã nạp bộ seed V7, V8 tự backfill danh mục cũ. Để thêm bốn ví dụ nhiều danh mục mà giữ đơn/Profile/media đã test:

```powershell
.\database\seed\load_test_data.ps1 -MultipleCategories
```

Helper [add_test_product_categories.sql](add_test_product_categories.sql) chỉ thêm cho P01/P02/P03/P05 còn thuộc Seller fixture, chưa xóa và ở trạng thái có thể sửa. Chạy lại không ghi đè các lựa chọn đã chỉnh sau lần bổ sung đầu tiên. Bộ seed nạp mới đã có sẵn các ví dụ này.

## Tài khoản

Mật khẩu chung: **`12345678`**. Database lưu hash `{bcrypt}`, tương thích DelegatingPasswordEncoder hiện tại.

| Email | Role thực tế | Trạng thái ban đầu |
|---|---|---|
| admin@gmail.com | ADMIN | ACTIVE |
| mua@gmail.com | BUYER | ACTIVE, chưa VERIFIED |
| ban@gmail.com | BUYER, SELLER | ACTIVE; seller activation giả lập MVP_BYPASS/VERIFIED |
| ktv@gmail.com | KTV | ACTIVE; tài khoản dành test thao tác KTV |

**KTV đã có role riêng.** DP-26 thay lựa chọn tạm BUYER trước đây; TASK-0046 kiểm tra READ ONLY cho đúng các role trong bảng. API `/api/v1/moderation/**` vẫn chỉ kiểm tra đăng nhập, chưa giới hạn ADMIN/KTV; thao tác duyệt thành công chưa chứng minh authorization đúng. Guard xử lý sau theo DP-27; không cấp ADMIN ngầm hoặc hạ KTV về BUYER.

Seed SQL đã đổi wanted_roles sang KTV, nhưng prerequisite còn kiểm tra ba role cũ và NOTICE cuối file còn nói KTV dùng BUYER. Chưa sửa SQL/loader hoặc kiểm chứng lại seed trong TASK-0046; lưu phần này cho đợt sửa sau. Khi thử nạp mới, phải xác nhận role KTV có sẵn và kết quả role sau nạp, không coi NOTICE cũ là hướng hiện hành.

Số điện thoại, địa chỉ, ngân hàng đều giả. Seller có ba trường ngân hàng; số tài khoản `000000001234` để thử hiển thị `****1234`, không phải tài khoản nhận tiền. Seller VERIFIED là **fixture MVP**, không phải kết quả eKYC/AI.

## Dữ liệu ban đầu

- **6 địa chỉ:** Buyer có A1 mặc định, A3 rồi A2 theo thứ tự `is_default DESC, created_at DESC` hiện tại; Seller có hai địa chỉ, KTV có một. Xóa A1 phải chọn địa chỉ đầu tiên còn lại trong danh sách (A3). Snapshot giao hàng của đơn vẫn giữ địa chỉ ban đầu khi Profile thay đổi.
- **24 sản phẩm**, **28 liên kết danh mục** thuộc tám category V5: 13 ACTIVE, 2 PENDING, 2 DRAFT, 1 REJECTED, 1 HIDDEN, 2 RESERVED, 3 SOLD. P01/P02/P03/P05 thuộc hai danh mục; không thêm/sửa danh mục thật.
- **68 media:** 46 ảnh, 22 video. P13 có đủ 5 ảnh; P12/P14 có tối thiểu 1 ảnh + 1 video. P15 chỉ có ảnh, P16 chưa có media để thử chặn gửi duyệt.
- **8 voucher TEST** bên cạnh WELCOMEOG/OGFREESHIP có sẵn từ migration. Không thay đổi các voucher hiện có hoặc policy phí.
- **6 đơn + 6 payment mock**, 3 snapshot voucher và 2 notification chờ thanh toán. Notification/dashboard/shipments chưa được nối đầy đủ trong ứng dụng, seed không làm các tính năng đó tự xuất hiện.

Ảnh/video dùng Cloudinary **demo public**, không thuộc cloud của chủ dự án; một số ảnh là placeholder hoa, ảnh lặp để thử gallery nhiều ảnh. Video chó mẫu cắt 10 giây phục vụ player/validation, không mô tả tình trạng hàng. `cloudinary_public_id` để NULL để không coi asset demo là tài sản có thể xóa khỏi cloud của dự án. Chưa kiểm chứng upload vào cloud đã cấu hình; thử upload ảnh/video thật bằng form Người bán để kiểm chứng riêng.

Phí hệ thống snapshot test bằng 0 theo policy compatibility V3, ship test 30.000đ theo code hiện tại; đây **không phải quyết định phí kinh doanh cuối cùng**. Không sử dụng QR/bank của màn payment để chuyển tiền thật.

## Các tình huống nên test

| Mã | Dữ liệu và thao tác |
|---|---|
| P01–P08 | Tìm kiếm tiếng Việt, lọc category/condition/giá; phân trang với size nhỏ; xem chi tiết và Mua ngay |
| P01/P02/P03/P05 | Lọc bằng danh mục thứ hai (collectibles/fashion/books-stationery/other); xem cả hai danh mục, chỉnh checkbox và lưu lại; bỏ hết lựa chọn phải bị chặn |
| P09 | Tin yêu cầu eKYC: mua@gmail.com chưa VERIFIED phải bị chặn; xem sản phẩm vẫn được |
| P10/P11 | Giá 50 triệu và giá 65.000đ: định dạng tiền, tổng tiền, voucher không đủ ngưỡng |
| P13/P14 | ktv@gmail.com mở `/moderation`, duyệt một tin, từ chối tin còn lại; kiểm tra trạng thái public và danh sách Seller |
| P15/P16 | ban@gmail.com chỉnh nháp; thiếu video/media phải không gửi duyệt được; thêm media qua Cloudinary rồi gửi PENDING |
| P17/P18 | Seller sửa/gửi lại tin bị từ chối; ẩn/hiện tin theo hành vi hiện tại. Lý do từ chối chưa có cột lưu trong schema, seed không giả lập lý do đã lưu |
| O01 / P19 | PAYMENT_PENDING + payment PENDING, tổng 530.000đ; mở QR/bill, thoát về trang chủ, xem lại đơn |
| O02 / P20 | PAYMENT_PENDING + payment FAILED, tổng 780.000đ; thử thanh toán mock lại trước hạn |
| O03 / P21 | PAID_HELD + HELD, seller voucher 20.000đ; Buyer trả 910.000đ, Seller proceeds 880.000đ |
| O04 / P22 | SHIPPED + HELD, voucher platform 50.000đ; tổng 1.180.000đ |
| O05 / P23 | COMPLETED + RELEASED, voucher đã hết lượt 40.000đ; tổng 790.000đ |
| O06 / P24 | CANCELLED + FAILED, tổng 630.000đ; sản phẩm ACTIVE đã được nhả |

ID database được tự sinh; tìm bằng title `[TEST Pxx]` hoặc đọc manifest audit, không hardcode ID trong UI. O01/O02 có **đúng một giờ tính từ lúc nạp lần đầu**; scheduler có thể hủy khi hết hạn. Nạp lại không gia hạn hoặc đổi trạng thái.

| Voucher | Tình huống |
|---|---|
| TEST50K | Platform giảm 50.000đ, đơn từ 200.000đ |
| TESTSHIP30 | Giảm ship tối đa 30.000đ, đơn từ 100.000đ |
| TEST10PC | Giảm 10%, trần 100.000đ, đơn từ 100.000đ |
| TESTSELLER20 | Seller ban@gmail.com tài trợ 20.000đ, đơn từ 200.000đ |
| TESTEXPIRED | Đã hết hạn |
| TESTFUTURE | Chưa đến ngày hiệu lực |
| TESTUSEDUP | Hết lượt sử dụng; một lượt đã snapshot vào O05 |
| TESTINACTIVE | Bị tắt |

### Nhánh Buyer VERIFIED

Sau khi đã thử P09 với Buyer chưa xác minh, chạy [set_test_buyer_verified.sql](set_test_buyer_verified.sql) trong database test hoặc:

```powershell
.\database\seed\load_test_data.ps1 -BuyerVerified
```

Script thêm fixture `MVP_BYPASS/VERIFIED` cho đúng Buyer của bộ seed; không thêm role SELLER, không chạy AI và không ghi đè hồ sơ đang PENDING. Sau đó test mua P09 thành công; dùng KTV (BUYER chưa VERIFIED) để kiểm tra nhánh bị chặn và quyền sở hữu đơn/địa chỉ của Buyer khác.

### Hết hạn mà không phải đợi một giờ

Chạy [expire_test_pending_orders.sql](expire_test_pending_orders.sql) trong database test. Script chỉ lùi mốc của O01/O02 **còn PAYMENT_PENDING** và reservation tương ứng; vẫn giữ khoảng cách created_at → due_at đúng một giờ. Script không hủy/nhả hàng hộ scheduler. Với backend đang chạy, chờ lần quét scheduler rồi kiểm tra đơn CANCELLED và sản phẩm ACTIVE. Không dùng helper này cho đơn người dùng tạo ngoài fixture.

## Nạp lại và khôi phục

- Một transaction cho seed chính; lỗi sẽ rollback toàn bộ bản ghi. Identity sequence có thể tăng dù rollback; ID không liền nhau là bình thường.
- Advisory lock ngăn hai lần nạp seed chạy đồng thời. Manifest `audit_logs.action = DEV_SEED_CREATED`, `request_id = og-shop-development-test-v1` chống nạp trùng.
- Khi manifest đã tồn tại, lần nạp sau giữ nguyên mật khẩu/Profile/media/voucher/trạng thái và deadline đã test; **không reset**.
- Nếu bốn email hoặc mã TEST voucher đã tồn tại ngoài seed, script dừng và giữ dữ liệu cũ. Nếu thiếu category V5, cột V6/V7 hoặc bảng liên kết V8, script dừng.
- Muốn trở về trạng thái ban đầu, dùng database test mới hoặc khôi phục backup đã tạo trước lúc nạp. Không xóa manifest rồi nạp lại vào database đang chứa fixture; không TRUNCATE bảng người dùng/đơn/payment.

## Kiểm chứng

[verify_test_data.sql](verify_test_data.sql) kiểm tra bản ghi manifest, ownership, media của tin công khai, deadline giữ hàng, giá/discount/seller proceeds và payment mock; chỉ đọc dữ liệu. [seed_constraint_tests.sql](seed_constraint_tests.sql) dành cho database kiểm thử riêng ngay sau nạp mới, kiểm tra email/default trùng, nháp thiếu video, ảnh thứ sáu và reservation thiếu order; tất cả thử ghi được rollback.

Bộ seed đã được kiểm tra integrity/idempotency/constraint trên PostgreSQL riêng khi chuẩn bị. Chưa kiểm chứng lại loader/login sau đổi role KTV; seed không thay thế kiểm thử UI hoặc Cloudinary thật.

Nâng V7→V8 đã được kiểm tra giữ nguyên 24 sản phẩm/6 đơn; seed V8 mới và helper bổ sung giữ 28 liên kết, chạy lại không reset. Kiểm tra này chưa chứng minh an toàn toàn bộ tuyến migration V9–V11 trên database đang dùng.
