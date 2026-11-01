# Old but Gold (O.G Shop)

O.G Shop là nền tảng C2C hỗ trợ mua bán đồ cũ theo hướng đáng tin cậy. Phạm vi đồ án tập trung vào đăng bán, trao đổi, đặt hàng, thanh toán giữ tiền mô phỏng, giao nhận, khiếu nại, hoàn trả, đánh giá và quản trị.

## Trạng thái

Checkpoint auth hiện hành là **V25**: đăng ký email hai bước OTP, Buyer/Seller draft và thưởng 3.000 xu một lần sau xác nhận; mật khẩu mới thống nhất, đăng nhập giữ thời hạn cố định. [Cấu hình SMTP/Gmail](docs/setup/EMAIL_OTP.md), [hợp đồng API](docs/api/IDENTITY_API.md), [schema V25](database/docs/EMAIL_REGISTRATION_V25.md). Google, reset OTP, 2FA và eKYC/KTV là các đợt tiếp nối. Các baseline core flow bên dưới được giữ để đối chiếu, không đồng nghĩa nghiệm thu toàn bộ 83 UC.

Theo dõi trạng thái tại [docs/progress/STATUS.md](docs/progress/STATUS.md).

## Công nghệ

- Backend: Java 21, Spring Boot 3.5, Maven, Spring Security, Spring Data JPA, Flyway.
- Frontend: React 19, TypeScript, Vite, npm.
- AI Service: Python 3.11, FastAPI, YOLOv11n, VietOCR Transformer, DeepFace ArcFace (512-d); Gemini tùy chọn và tắt mặc định.
- Database: PostgreSQL (hỗ trợ extension `pgvector`).
- Local infrastructure: Docker Compose.
- CI: GitHub Actions.

## Cấu trúc chính

```text
backend/      Spring Boot modular monolith
frontend/     React application theo feature
services/     AI microservices (ekyc-service: OCR CCCD & Sinh trắc học khuôn mặt)
database/     SQL tests, seed, scripts và tài liệu database
reference/    Tài liệu nguồn do chủ dự án chủ động đưa vào repository
docs/         Kiến trúc, module, yêu cầu, tiến trình, ADR và kiểm thử
.github/      Pull request template và CI workflows
scripts/      Script bootstrap và quality gate
infra/        Cấu hình hạ tầng hỗ trợ
```

Xem chi tiết tại [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md).

## Chạy local

### 1. Khởi động PostgreSQL

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
```

Image PostgreSQL có `pgvector` vì migration V1 tạo extension `vector`. Với database có dữ liệu, sao lưu và thử nâng cấp V9–V13 trên bản sao trước khi chạy backend; Flyway tự áp dụng migration còn thiếu khi ứng dụng khởi động. Không xóa volume hoặc sửa migration đã chia sẻ để bỏ qua lỗi.

### 2. Chạy Backend

Dùng Java 21. Cấu hình secret và VNPAY theo [hướng dẫn backend](backend/README.md). `OgShopApplication` nạp tệp `.env` local đầu tiên tìm thấy; biến môi trường/system properties có ưu tiên cao hơn. [Thứ tự nạp và cấu hình SMTP](docs/setup/EMAIL_OTP.md).

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Health check: `http://localhost:8080/actuator/health`.

### 3. Chạy Frontend

```powershell
cd frontend
npm ci
npm run dev
```

Dùng Node.js 24 trở lên và npm 11 trở lên, cùng phiên bản với quality gate. Console Admin/KTV đã có giao diện sơ bộ; nhiều chức năng vẫn dùng dữ liệu demo. Xem phạm vi bản tích hợp và việc còn mở trong [hướng dẫn backend](backend/README.md#bản-tích-hợp-sơ-bộ).

Frontend mặc định: `http://localhost:5174`. Vite dùng strictPort; backend cần `APP_FRONTEND_ORIGIN=http://localhost:5174` trong `.env` đang nạp rồi restart để đồng bộ CORS. Nếu cổng bận, dừng đúng phiên frontend cũ trước khi chạy lại.

### 4. Chạy eKYC AI Microservice

```powershell
python services/ekyc-service/start.py
```

Microservice mặc định: `http://localhost:8001`.
Swagger API Docs: `http://localhost:8001/docs`.

### 5. Chạy Kiểm Thử Tích Hợp PostgreSQL Thật (`FulfillmentPostgresTest`)

Để kiểm tra đồng thời (concurrency), khóa bi quan (`PESSIMISTIC_WRITE`), và đối soát webhook/giao vận trên PostgreSQL thật:

```powershell
# Bước 1: Tạo database kiểm thử riêng biệt (không ảnh hưởng dữ liệu dev)
docker exec -it og-shop-postgres psql -U <DB_USER> -c "CREATE DATABASE og_shop_test;"

# Bước 2: Thiết lập biến môi trường trỏ vào database test (thay thế bằng thông tin của bạn, không commit credential)
$env:OGSHOP_TEST_DB_URL = "jdbc:postgresql://localhost:55432/og_shop_test"
$env:OGSHOP_TEST_DB_USER = "<DB_USER>"
$env:OGSHOP_TEST_DB_PASSWORD = "<DB_PASSWORD>"
$env:OGSHOP_TEST_DB_ISOLATED = "true"

# Bước 3: Chạy bộ test tích hợp Fulfillment trên PostgreSQL thật
cd backend
.\mvnw.cmd test -Dtest=FulfillmentPostgresTest
```
*(Ghi chú: Flyway sẽ tự động chạy 36 migrations và Hibernate `validate` schema trên database test này khi chạy)*.


## Tài liệu quan trọng

- [Tiến trình tổng quát](docs/progress/STATUS.md)
- [Danh mục module](docs/modules/INDEX.md)
- [Kiến trúc tổng quan](docs/architecture/OVERVIEW.md)
- [Quy ước API](docs/api/API_CONVENTIONS.md)
- [Security baseline](docs/security/SECURITY_BASELINE.md)
- [Chiến lược kiểm thử](docs/testing/TEST_STRATEGY.md)
- [Danh mục tài liệu nguồn](reference/SOURCE_REGISTER.md)

## Phạm vi repository

Chỉ các file được tạo hoặc chủ dự án chủ động đặt trong thư mục repository này mới được theo dõi và đưa lên GitHub. Không tự động sao chép tài liệu từ thư mục khác.
