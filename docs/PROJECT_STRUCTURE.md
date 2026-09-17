# Project Structure

## Principles

1. Một repository chứa Backend, Frontend, database tooling và tài liệu liên quan.
2. Backend là Modular Monolith, chia theo bounded context.
3. Frontend chia theo user-facing feature.
4. Tài liệu nguồn chỉ được thêm bởi chủ dự án vào `reference/`.
5. Tài liệu module là bản đồ để agent xác định phạm vi trước khi sửa.
6. Quy tắc chuẩn nằm trong `.agents/`; file riêng cho Claude/Gemini chỉ là adapter.

## Root directories

| Đường dẫn | Trách nhiệm |
|---|---|
| `backend/` | Spring Boot application và Flyway migrations |
| `frontend/` | React application |
| `database/` | Database test, seed, script và tài liệu hỗ trợ |
| `reference/` | Nguồn đầu vào do chủ dự án chọn |
| `docs/` | Kiến trúc, module, yêu cầu, tiến trình và ADR |
| `.agents/` | Rule, workflow và skill chuẩn |
| `.claude/` | Adapter native cho Claude Code |
| `.gemini/` | Command adapter cho Gemini CLI |
| `.github/` | CI và pull request governance |
| `scripts/` | Automation có kết quả xác định |
| `infra/` | Cấu hình hạ tầng ngoài ứng dụng |

## Backend package layout

```text
modules/<module>/
├── api/             HTTP DTO, controller, API mapping
├── application/     use case, transaction, authorization orchestration
├── domain/          entity, value object, domain rule
└── infrastructure/  JPA, external adapter, messaging implementation
```

Không bắt buộc tạo đủ bốn thư mục nếu module chưa có trách nhiệm tương ứng.

## Frontend layout

```text
src/
├── app/       bootstrap và application-wide providers
├── pages/     route composition
├── features/  nghiệp vụ theo hành trình người dùng
└── shared/    thành phần không phụ thuộc feature
```

## Database ownership

- Flyway migration chạy cùng ứng dụng: `backend/src/main/resources/db/migration/`.
- Database integration tests: `database/tests/`.
- Seed chỉ dùng cho development/test: `database/seed/`.
- Thiết kế và invariant: `database/docs/`.
- JPA không tự thay đổi schema; `ddl-auto` phải là `validate` ngoài test cô lập.

## Documentation ownership

- Thay đổi boundary: cập nhật `docs/architecture/` và ADR.
- Thay đổi module: cập nhật `docs/modules/<module>.md`.
- Thay đổi yêu cầu: cập nhật `docs/requirements/` và traceability.
- Bắt đầu/kết thúc task: cập nhật `docs/progress/`.
- Thay đổi API: cập nhật OpenAPI và `docs/api/`.
