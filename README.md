# Old but Gold (O.G Shop)

O.G Shop là nền tảng C2C hỗ trợ mua bán đồ cũ theo hướng đáng tin cậy. Phạm vi đồ án tập trung vào đăng bán, trao đổi, đặt hàng, thanh toán giữ tiền mô phỏng, giao nhận, khiếu nại, hoàn trả, đánh giá và quản trị.

## Trạng thái

Dự án đang ở giai đoạn **Foundation**: hoàn thiện cấu trúc monorepo, quy tắc làm việc, nền Backend/Frontend, database tooling và CI trước khi triển khai nghiệp vụ.

Theo dõi trạng thái tại [docs/progress/STATUS.md](docs/progress/STATUS.md).

## Công nghệ

- Backend: Java 21, Spring Boot 3.5, Maven, Spring Security, Spring Data JPA, Flyway.
- Frontend: React 19, TypeScript, Vite, npm.
- Database: PostgreSQL.
- Local infrastructure: Docker Compose.
- CI: GitHub Actions.

## Cấu trúc chính

```text
backend/      Spring Boot modular monolith
frontend/     React application theo feature
database/     SQL tests, seed, scripts và tài liệu database
reference/    Tài liệu nguồn do chủ dự án chủ động đưa vào repository
docs/         Kiến trúc, module, yêu cầu, tiến trình, ADR và kiểm thử
.agents/      Quy tắc, workflow và skill chuẩn dùng chung
.claude/      Adapter dành cho Claude Code
.gemini/      Adapter dành cho Gemini CLI
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

### 2. Chạy Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Health check: `http://localhost:8080/actuator/health`.

### 3. Chạy Frontend

```powershell
cd frontend
npm install
npm run dev
```

Frontend mặc định: `http://localhost:5173`.

## Quy trình làm việc với AI agent

Mọi agent phải đọc [AGENTS.md](AGENTS.md), trạng thái tổng quát và tài liệu module liên quan trước khi đề xuất thay đổi. Mọi thay đổi code hoặc kiến trúc phải có báo cáo trước và được chủ dự án phê duyệt.

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
