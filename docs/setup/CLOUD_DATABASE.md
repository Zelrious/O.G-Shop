# Hướng Dẫn Sử Dụng Cloud Database (Supabase PostgreSQL) & Kiểm Thử Đồng Bộ

Tài liệu này hướng dẫn cách kết nối Backend và Frontend với CSDL đám mây **Supabase PostgreSQL** đã được thiết lập đồng bộ cho toàn bộ dự án **O.G Shop**.

---

## 1. Thông Tin Cấu Hình Cloud Database

| Thuộc tính | Giá trị cấu hình |
| :--- | :--- |
| **Nền tảng** | Supabase (AWS Asia-Pacific - `ap-south-1`) |
| **Engine** | PostgreSQL 17.11 (64-bit) |
| **Sinh trắc học AI** | `pgvector` v0.8.2 (`vector(512)` + chỉ mục `ivfflat`) |
| **Connection Host** | `aws-0-ap-south-1.pooler.supabase.com` |
| **Port** | `5432` (Session Pooler mode - hỗ trợ IPv4 & DDL) |
| **Database** | `postgres` |
| **Username** | Cấu hình qua biến `CLOUD_DB_USERNAME` (ví dụ `postgres.iocotjgbafwouttufhzn`) |
| **Password** | Được bảo mật trong file `.env` (biến `CLOUD_DB_PASSWORD` hoặc `DB_PASSWORD`) |
| **JDBC URL** | `jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require` |
| **Flyway Status** | Đã hoàn thành 36/36 migrations (`V1` $\rightarrow$ `V36`) — 56 bảng CSDL |

---

## 2. Cách Khởi Chạy Backend Kết Nối Cloud Database

Cấu hình đám mây đã được tích hợp sẵn trong profile `cloud` tại file:
`backend/src/main/resources/application-cloud.yml`

### Cách 1: Chạy từ Terminal / PowerShell
```powershell
cd "backend"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=cloud
```

### Cách 2: Thiết lập biến môi trường
```powershell
$env:SPRING_PROFILES_ACTIVE = "cloud"
cd "backend"
.\mvnw.cmd spring-boot:run
```

### Cách 3: Chạy trong IDE (IntelliJ IDEA / VS Code)
- Mở Run/Debug Configurations của `OgShopApplication`.
- Tại mục **Active profiles**, điền: `cloud`.
- Bấm **Run**.

---

## 3. Danh Sách Tài Khoản Dữ Liệu Mẫu (Seed Data)

Toàn bộ tài khoản thử nghiệm đã được nạp sẵn vào Supabase Cloud DB với mật khẩu chung: **`12345678`**.

| Email | Vai trò (Role) | Trạng thái | Mục đích kiểm thử |
| :--- | :---: | :---: | :--- |
| `admin@gmail.com` | **ADMIN** | `ACTIVE` | Quản trị viên, cấu hình voucher hệ thống, giám sát giao dịch |
| `ktv@gmail.com` | **KTV** | `ACTIVE` | Kỹ thuật viên kiểm duyệt tin đăng, phê duyệt hồ sơ eKYC, phân xử khiếu nại |
| `ban@gmail.com` | **SELLER, BUYER** | `ACTIVE` | Người bán đã xác minh eKYC, sở hữu 24 sản phẩm mẫu |
| `mua@gmail.com` | **BUYER** | `ACTIVE` | Người mua, thử nghiệm giỏ hàng, đặt hàng ký quỹ Escrow, chat & trả giá |

---

## 4. Kịch Bản Kiểm Thử Các Luồng Tranh Chấp (Concurrency Testing)

Với CSDL dùng chung trên Cloud, cả nhóm có thể cùng lúc mở nhiều trình duyệt/máy tính khác nhau để kiểm thử các luồng tranh chấp đồng thời:

### Kịch bản 1: Tranh chấp Mua hàng độc bản (Pessimistic Concurrency Lock)
* **Mục tiêu**: Đảm bảo 2 người mua không thể cùng mua 1 sản phẩm second-hand (tồn kho = 1).
* **Cách thực hiện**:
  1. Người A và Người B cùng đăng nhập tài khoản Buyer (hoặc mở 2 tab ẩn danh).
  2. Cả 2 cùng vào giỏ hàng hoặc chi tiết của sản phẩm `P01` (iPhone 13 Pro Max Vintage).
  3. Cả 2 bấm nút **Xác nhận thanh toán** tại cùng một thời điểm.
* **Kết quả**: Người gửi request trước 1 mili-giây sẽ thành công; người gửi sau nhận lỗi `409 Conflict: Sản phẩm đã được người khác đặt trước`, CSDL không bị âm kho hay trùng đơn.

### Kịch bản 2: Tranh chấp Voucher giới hạn lượt (Atomic Balance Check)
* **Mục tiêu**: Kiểm tra tính nguyên tử (atomic) khi áp dụng voucher chỉ còn 1 lượt dùng.
* **Cách thực hiện**:
  1. Hai người mua cùng chọn 2 món hàng khác nhau và cùng nhập mã voucher `WELCOMEOG` (hoặc voucher test có `max_uses = 1`).
  2. Bấm áp dụng cùng lúc.
* **Kết quả**: Chỉ 1 đơn hàng được hưởng ưu đãi; đơn còn lại báo lỗi `Voucher đã hết lượt sử dụng`.

### Kịch bản 3: Hai KTV cùng thao tác duyệt 1 hồ sơ eKYC (Optimistic Locking)
* **Mục tiêu**: Tránh việc 2 KTV ra quyết định trái ngược nhau trên cùng 1 hồ sơ.
* **Cách thực hiện**:
  1. KTV 1 và KTV 2 cùng mở màn hình chi tiết duyệt eKYC của một tài khoản `PENDING`.
  2. KTV 1 bấm nút **Phê duyệt**, trong khi KTV 2 bấm **Từ chối**.
* **Kết quả**: Thao tác của KTV bấm trước được ghi nhận; KTV bấm sau nhận thông báo `Hồ sơ đã được xử lý bởi nhân sự khác, vui lòng tải lại trang`.

### Kịch bản 4: Stress-test Chat & Thông báo đồng thời
* **Mục tiêu**: Đánh giá hiệu năng của Connection Pool (HikariCP) qua kết nối Internet đường dài.
* **Cách thực hiện**: Mở 3-4 phiên trò chuyện đồng thời, gửi tin nhắn và đề xuất giá offer liên tục để kiểm tra cơ chế polling (2.5s / 5s).
* **Kết quả**: Hệ thống duy trì độ trễ ổn định < 100ms, không bị nghẽn pool kết nối.

