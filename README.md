# Old but Gold (O.G Shop)

O.G Shop là nền tảng C2C hỗ trợ mua bán đồ cũ theo hướng đáng tin cậy. Phạm vi đồ án tập trung vào đăng bán, trao đổi, đặt hàng, thanh toán giữ tiền mô phỏng, giao nhận, khiếu nại, hoàn trả, đánh giá và quản trị.

## Trạng thái

Dự án đã hoàn thành **Identity/eKYC Security Baseline** và **Database Pricing/Negotiation Baseline V3**. Ưu tiên hiện tại là hoàn thiện core flow trên PostgreSQL: catalog → chat/offer → checkout/order → payment/fulfillment mô phỏng. Email/OTP, Google OIDC, Cloudinary, WebSocket, outbox worker và Redis được triển khai sau core acceptance gate hoặc khi thật sự cần cho một luồng cơ bản.

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

Dùng Java 21. Cấu hình secret và VNPAY theo [hướng dẫn backend](backend/README.md): `.env` dùng cho Docker Compose, Spring Boot không tự nạp file này.

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

Frontend mặc định: `http://localhost:5173`.

### 4. Chạy eKYC AI Microservice

```powershell
python services/ekyc-service/start.py
```

Microservice mặc định: `http://localhost:8001`.
Swagger API Docs: `http://localhost:8001/docs`.

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
