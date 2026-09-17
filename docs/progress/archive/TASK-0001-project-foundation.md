# TASK-0001 — Project foundation

- Status: DONE
- Started: 2026-09-17
- Completed: 2026-09-17
- Approved by: Project owner
- Modules: repository-wide

## Goal

Tạo monorepo có thể build cho Old but Gold, thiết lập cấu trúc module, tài liệu, agent governance, CI và kết nối GitHub.

## Approved scope

- Tạo file trực tiếp trong `O.G Shop`.
- Không tự động sao chép tài liệu từ thư mục khác.
- Tạo Backend Spring Boot và Frontend React ở mức scaffold.
- Tạo rules, workflows, skills và tài liệu tiến trình.
- Commit và push nhánh `main` lên repository đã chỉ định.

## Completed so far

- Git repository đã được khởi tạo trên `main`.
- Remote `origin` đã được cấu hình.
- Monorepo Backend, Frontend, Database, Reference, Docs, Infra và script đã được tạo.
- 8 module boundary, requirement index, traceability skeleton và 3 ADR đã được tạo.
- Rules, workflows, skill chuẩn, Claude adapter và Gemini commands đã được tạo.
- CI Backend, Frontend, documentation và CodeQL đã được cấu hình với action pin theo commit SHA.
- Maven/npm dependency đã được khóa; TypeScript 6.0.3 được chủ dự án duyệt để tương thích `typescript-eslint`.
- Các quality gate hiện có đều đạt.

## Remaining

- Không còn việc nào trong phạm vi task đã duyệt.
- Việc nhập tài liệu nguồn và tạo database baseline thuộc task tiếp theo, cần báo cáo và phê duyệt riêng.

## Verification evidence

- `scripts/quality/verify-backend.ps1`: BUILD SUCCESS; 3 test, 0 failure/error/skipped.
- `npm ci --no-audit --no-fund`: cài 233 package thành công từ lockfile.
- `npm run lint`: pass, 0 warning.
- `npm run typecheck`: pass.
- `npm test`: 1 file/1 test pass.
- `npm run build`: pass; bundle JS 220.58 kB, gzip 69.04 kB.
- `npm audit --audit-level=high`: 0 vulnerability.
- `docker compose config --quiet`: pass.
- `validate-docs.ps1`: 9 tài liệu lõi và 8 tài liệu module hợp lệ.
- `validate-agent-assets.ps1`: 9 skill chuẩn, 9 Claude adapter và 10 workflow hợp lệ.
- `quick_validate.py`: 18/18 skill/adapter hợp lệ.
- `git push -u origin main`: thành công; baseline `40e3f8c` đã có trên `origin/main`.

## Changed files

- Root governance và local tooling: `README.md`, `AGENTS.md`, adapter, Git/env/Compose config.
- Backend: Spring Boot scaffold, security deny-by-default, module packages, test và Maven Wrapper.
- Frontend: React/Vite shell, test, lint/typecheck/build config và npm lockfile.
- Database/Reference/Infra: ownership README và vùng nhận nguồn được kiểm soát.
- Documentation: architecture, requirements, modules, ADR, security, testing và progress.
- Agent system: `.agents/`, `.claude/`, `.gemini/`.
- Automation: `.github/` và `scripts/`.

## Known environment notes

- `mvnw.cmd` của Apache không nạp được JAR bằng đường dẫn tuyệt đối chứa ký tự tiếng Việt trên JDK Windows hiện tại. `scripts/quality/verify-backend.ps1` dùng cùng Maven Wrapper qua classpath tương đối và đã kiểm tra thành công; CI Linux tiếp tục dùng `./mvnw` chuẩn.
- Docker Engine không thể khởi động trong phiên này. Cấu hình Compose đã parse thành công; chưa chạy PostgreSQL container vì baseline migration chưa tồn tại.

## Publication

- Repository: `https://github.com/Zelrious/O.G-Shop`
- Branch: `main`
- Baseline commit: `40e3f8c`
