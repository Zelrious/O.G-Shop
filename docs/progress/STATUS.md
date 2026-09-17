# Project Status

- Project: Old but Gold (O.G Shop)
- Updated: 2026-09-17
- Phase: FOUNDATION
- Overall status: FOUNDATION_READY
- Active task: None
- Published baseline: `40e3f8c` on `origin/main`

## Completed

- Chốt tên dự án và repository.
- Chốt Modular Monolith với Backend Java/Spring Boot và Frontend React/TypeScript.
- Khởi tạo Git repository cục bộ trên nhánh `main`.
- Kết nối remote `origin` tới `https://github.com/Zelrious/O.G-Shop.git`.
- Hoàn thành monorepo scaffold, module boundaries và tài liệu nền.
- Hoàn thành agent governance cho Codex, Claude Code và Gemini CLI.
- Backend đạt 3/3 test; Frontend đạt lint, typecheck, 1/1 test và production build.
- Docker Compose, tài liệu và agent assets đã qua kiểm tra cấu trúc.
- Công bố scaffold lên `https://github.com/Zelrious/O.G-Shop` tại commit `40e3f8c`.

## In progress

- Không có task triển khai đang hoạt động. Task tiếp theo phải qua approval gate.

## Next

1. Đưa tài liệu nguồn vào `reference/` theo quyết định của chủ dự án.
2. Đăng ký tài liệu nguồn trong `reference/SOURCE_REGISTER.md`.
3. Chốt baseline database và tạo Flyway migration đầu tiên.
4. Lập ma trận truy vết yêu cầu trước khi triển khai module `identity`.
5. Chốt authentication/session strategy.

## Current risks

- Tài liệu yêu cầu và database chính thức chưa nằm trong repository mới.
- Chưa có Flyway baseline nên Backend chưa đại diện cho database nghiệp vụ.
- Các chính sách thời hạn kiểm tra hàng, tự giải ngân và hoàn hàng còn cần xác nhận.
- Docker CLI và cấu hình Compose hợp lệ, nhưng Docker Engine trên máy đang dừng nên chưa chạy integration test PostgreSQL.
