# Rủi ro, yêu cầu chức năng và kế hoạch thử nghiệm database

Phạm vi hiện tại chỉ gồm PostgreSQL và nghiệp vụ dữ liệu. Chưa tạo Spring Boot entity, repository, API hoặc giao diện.

## 1. Yêu cầu chức năng ở tầng dữ liệu

| Mã | Yêu cầu | Cơ chế chính |
|---|---|---|
| DB-FR-01 | Một user có thể mua và bán | `users`, `roles`, `user_roles` |
| DB-FR-02 | Chỉ seller đã xác minh mới được đăng bán | Lịch sử `seller_verifications`; service transaction kiểm tra KYC + role khi publish |
| DB-FR-03 | Listing mô tả rõ tình trạng đồ cũ | Condition, thời gian sử dụng, lỗi, sửa chữa, phụ kiện và media |
| DB-FR-04 | Mỗi listing MVP đại diện một món | `quantity = 1`; product đi qua `ACTIVE/RESERVED/SOLD` |
| DB-FR-05 | Chat và offer theo từng sản phẩm | Unique conversation và một pending offer/buyer/product |
| DB-FR-06 | Cart có thể chứa nhiều seller | Khi checkout nhóm theo seller; `checkout_group_id` nối các order sinh từ cùng checkout |
| DB-FR-07 | Một order chỉ có một seller | `orders.seller_id`; service kiểm tra seller của mọi order item |
| DB-FR-08 | Lịch sử đơn không đổi theo dữ liệu hiện tại | Snapshot địa chỉ và snapshot title/price trong order item |
| DB-FR-09 | Mô phỏng giữ, giải ngân và hoàn tiền | Một `payments`/order, state fields và timestamp bắt buộc |
| DB-FR-10 | Theo dõi vận chuyển | Một `shipments`/order, ràng buộc timestamp theo trạng thái |
| DB-FR-11 | Review sau giao dịch | Unique theo order/reviewer/reviewee; rating 1–5; service kiểm tra order completed |
| DB-FR-12 | Khiếu nại đóng băng payment | Một complaint active/order; resolution và refund fields nhất quán |
| DB-FR-13 | Report tách khỏi tranh chấp order | Report luôn có user hoặc product target |
| DB-FR-14 | Không mất lịch sử | Soft delete hoặc state transition; FK mặc định chặn xóa dữ liệu đã tham chiếu |
| DB-FR-15 | Theo vết thao tác nhạy cảm | `audit_logs`; quyền DB production phải chặn update/delete log |

## 2. Risk register

| Mức | Rủi ro | Hậu quả | Phòng ngừa/khắc phục | Test bắt buộc |
|---|---|---|---|---|
| P0 | Hai buyer mua cùng một product | Bán trùng một món | `SELECT ... FOR UPDATE`, khóa theo thứ tự product ID, update có điều kiện và kiểm tra row count | Hai connection checkout đồng thời; đúng một transaction thắng |
| P0 | Reservation không biết thuộc order nào | Job timeout trả nhầm hàng về `ACTIVE` | `products.reserved_order_id` + composite FK tới order item; khi release phải lọc cả product ID và owning order ID | Không thể tạo `RESERVED` thiếu owner/expiry hoặc owner không chứa product; job A không release reservation của order B |
| P0 | Callback payment gửi lặp hoặc đảo thứ tự | Giải ngân/hoàn tiền hai lần | Unique transaction code, idempotency key ở service, row lock payment, state machine một chiều | Gửi cùng callback hai lần; số dư logic và trạng thái chỉ đổi một lần |
| P0 | Order và payment lệch trạng thái | Giao hàng khi chưa giữ tiền hoặc completed khi chưa release | Cập nhật trong cùng transaction; lock order + payment; bảng cặp trạng thái hợp lệ | Inject lỗi giữa hai update và xác nhận rollback toàn bộ |
| P0 | Payment timeout và callback thành công chạy cùng lúc | Hủy order đã trả tiền hoặc giữ hàng vô hạn | Cùng lock order trước; callback/job chỉ transition từ expected state | Chạy timeout và callback song song |
| P0 | Admin resolve complaint hai lần | Refund và release cùng xảy ra | Lock complaint/payment/order; chỉ resolve từ `OPEN/REVIEWING`; external action có idempotency | Hai admin resolve ngược nhau đồng thời |
| P1 | Multi-seller order vô tình trộn item | Sai người nhận tiền | Group trước khi tạo order; kiểm tra mọi product seller bằng order seller trong transaction | Cart 2 seller tạo 2 order cùng `checkout_group_id` |
| P1 | Giá hoặc địa chỉ lịch sử bị thay đổi | Sai hóa đơn và bằng chứng tranh chấp | Snapshot address, title, price, line total | Sửa address/product sau checkout, order vẫn giữ giá trị cũ |
| P1 | Partial refund lọt vào MVP | Order/payment không có trạng thái biểu diễn đúng | Enforce `refund_amount = amount`; chỉ mở rộng cùng trạng thái `PARTIALLY_REFUNDED` sau này | Refund nhỏ hơn amount bị từ chối |
| P1 | KYC/CCCD bị lộ | Rủi ro quyền riêng tư nghiêm trọng | Private object storage, encryption, signed URL ngắn hạn, không log số giấy tờ, retention policy | Kiểm tra role DB và log không chứa PII |
| P1 | User sửa/xóa dữ liệu transaction | Mất audit và lịch sử | API không cấp hard delete; FK RESTRICT; DB role runtime không có `DELETE` trên bảng lịch sử | Runtime role bị từ chối delete/update audit |
| P1 | Lost update do hai command | Ghi đè trạng thái mới hơn | JPA `@Version`; pessimistic lock cho checkout/payment/dispute | Hai version update, transaction thứ hai thất bại |
| P1 | DDL và entity lệch nhau | Lỗi runtime hoặc schema âm thầm thay đổi | Flyway là nguồn chuẩn; Hibernate `ddl-auto=validate` | Boot integration test validate schema |
| P1 | Backup tồn tại nhưng restore lỗi | Mất toàn bộ dữ liệu đồ án | Backup định kỳ, kiểm tra restore vào DB mới | Restore drill và so sánh row count/checksum |
| P2 | JSONB evidence/document không có schema | Payload quá lớn hoặc sai cấu trúc | Validate JSON Schema ở service, giới hạn request/file, chỉ lưu metadata/object key | Payload sai kiểu/oversize bị từ chối |
| P2 | Search tiếng Việt kém | Không tìm được listing có dấu/không dấu | Sau MVP đánh giá `unaccent` + `pg_trgm` hoặc search engine; chưa tối ưu mù quáng | Bộ query tiếng Việt có/không dấu và đo latency |
| P2 | Quá nhiều index | Insert/update chậm và tốn dung lượng | Đo `pg_stat_user_indexes`; xóa index không dùng sau workload thật | Benchmark write và kiểm tra index scan |
| P2 | Phone không được chuẩn hóa | Trùng account với `0...` và `+84...` | Chuẩn hóa E.164 trước khi lưu; chỉ unique giá trị canonical | Hai biểu diễn cùng số phải bị coi là một |
| P2 | Category tạo chu trình | Cây category không duyệt được | Service kiểm tra ancestor trong transaction; có thể thêm trigger recursive nếu admin edit trực tiếp | A→B→C rồi đặt A parent C phải thất bại |
| P2 | Notification reference bị dangling | Link thông báo mở sai | Đây là polymorphic reference; service kiểm tra target và UI chịu target đã ẩn | Target bị ẩn/xóa mềm vẫn render an toàn |

## 3. Điểm database chưa thể tự đảm bảo bằng CHECK/FK

Các quy tắc sau liên quan nhiều bảng hoặc trạng thái cũ/mới nên được thực hiện trong transaction command, không sửa entity tự do:

- Seller của product phải trùng seller của order/conversation/offer.
- Sender phải thuộc conversation.
- Address nguồn phải thuộc buyer.
- Subtotal phải bằng tổng line item.
- Payment amount phải bằng order total.
- Review/complaint actor phải thuộc order và có đúng vai trò.
- Admin duyệt KYC/resolve complaint phải thực sự có role `ADMIN`.
- State transition phải đi theo cạnh hợp lệ, không chỉ thuộc danh sách status.

Nếu muốn cho DBA hoặc SQL client cập nhật trực tiếp trong production, cần stored procedure/trigger và thu hồi quyền `UPDATE` trực tiếp. Với Spring Boot modular monolith, khuyến nghị để domain service sở hữu các transaction này và DB giữ constraint cấu trúc.

## 4. Các lớp kiểm thử

1. **Schema smoke test:** DDL chạy được trên PostgreSQL 16, seed đủ role.
2. **Constraint test:** unique, FK, check, snapshot, soft-delete/hard-delete protection.
3. **Transaction test:** rollback khi một bước trong checkout/payment/complaint thất bại.
4. **Concurrency test:** hai session tranh cùng product, callback đối đầu timeout, hai admin resolve complaint.
5. **Performance test:** explain/analyze cho listing search, inbox, order history, admin queues; dùng dataset đủ lớn.
6. **Recovery test:** dump, restore sang database rỗng, kiểm tra constraint và dữ liệu.

`tests/database_tests.sql` hiện bao phủ lớp 1–2. Lớp 3–6 sẽ được bổ sung sau khi baseline chạy ổn định vì cần nhiều connection, workload và quy ước transaction command cụ thể.

## 5. Cách chạy database độc lập

Yêu cầu Docker Desktop đang chạy. Từ thư mục `Database`:

```powershell
.\run_database_tests.ps1 -Reset
```

Lệnh trên tạo PostgreSQL 16 tại `127.0.0.1:5433`, chạy schema và toàn bộ smoke test. `-Reset` xóa **volume database thử nghiệm của compose project này** rồi khởi tạo lại; không dùng khi có dữ liệu cần giữ.

Các lần sau, khi schema không đổi:

```powershell
.\run_database_tests.ps1
```

Có thể dùng một image PostgreSQL 15+ khác đã có sẵn trên máy:

```powershell
.\run_database_tests.ps1 -Reset -PostgresImage 'ankane/pgvector:latest'
```

Kết nối thủ công:

```text
Host: 127.0.0.1
Port: 5433
Database: marketplace
User: marketplace
Password: marketplace_dev_only
```

Thông tin trên chỉ dùng local development và không được tái sử dụng ở production.
