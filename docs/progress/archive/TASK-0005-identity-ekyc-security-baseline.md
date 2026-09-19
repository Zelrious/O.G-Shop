# TASK-0005 — Identity và eKYC Security Baseline

- Status: COMPLETE
- Approved: 2026-09-19
- Owner: O.G Shop
- Modules: Identity, frontend Auth/Verification, eKYC service

## Mục tiêu

Thay luồng mock bằng backend authority tối thiểu, bảo vệ refresh session, và đưa eKYC sau Spring Boot gateway mà không trả hoặc lưu face embedding thật.

## Phạm vi đã duyệt

- BCrypt qua `DelegatingPasswordEncoder`.
- Access token 15 phút; refresh token opaque 30 ngày trong cookie HttpOnly, rotate/revoke và chỉ lưu digest.
- Register, login, current user, refresh và logout do Spring Boot xử lý.
- Role do backend sở hữu; public registration chỉ cấp `BUYER`.
- Frontend không gọi FastAPI trực tiếp, không nhận embedding và không fail-open sang mock.
- FastAPI yêu cầu internal token, giới hạn input và không dùng embedding mutable dùng chung.
- Gemini post-processing tắt mặc định; file tạm bị xóa sau request.
- Flyway V2 lưu session và metadata eKYC, không lưu CCCD/embedding/ảnh sinh trắc học thật.
- Bỏ theo dõi cấu hình agent cá nhân khỏi Git trong khi giữ file local.

## Điều kiện chấp nhận

- Client không thể tự cấp `SELLER` hoặc `ADMIN`.
- Password không được hash trong frontend và không bao giờ được lưu dạng rõ.
- Refresh token không xuất hiện trong response body/localStorage; token cũ bị thu hồi khi rotate.
- FastAPI lỗi không thể chuyển hồ sơ sang verified bằng mock.
- FastAPI từ chối caller thiếu/sai internal token.
- Hai request face matching không chia sẻ card embedding.
- API/UI không trả face embedding.
- Backend, frontend, FastAPI và migration có bằng chứng kiểm thử tương ứng.

## Bằng chứng ban đầu

- `SecurityConfig` đang `permitAll`.
- Auth frontend dùng mock và SHA-256 phía client.
- Seller Verification tự thêm role `SELLER` phía client.
- Frontend gọi thẳng `VITE_EKYC_API_URL` và fallback sang mock khi lỗi.
- FastAPI cho CORS `*`, trả embedding và gán `matcher.id_embedding` trên singleton.
- V1 có `seller_biometrics.face_embedding` và thông tin CCCD.

## File thay đổi

- Backend: cấu hình Spring Security/JWT/CORS, auth/session application services, persistence, API/error contract và Flyway V2.
- Frontend: thay mock auth/eKYC bằng backend API, giữ access token trong memory và dùng refresh cookie.
- eKYC service: internal-token authentication, input limits, fail-closed processing và loại mutable embedding dùng chung.
- Documentation/config: identity API, security baseline, traceability, env examples và implementation plan.
- Repository hygiene: bỏ theo dõi các file agent/skill/workflow cá nhân và thêm chúng vào `.gitignore`, nhưng vẫn giữ bản local.

## Kiểm chứng

- `scripts/quality/verify-backend.ps1`: PASS, 12/12 tests.
- `npm run lint`, `npm run typecheck`: PASS.
- `npm test -- --run`: PASS, 6/6 tests.
- `npm run build`: PASS, Vite production build.
- `python -m unittest discover -s tests -v`: PASS, 4/4 tests.
- `python -m py_compile ...`: PASS.
- `scripts/quality/validate-docs.ps1`: PASS.
- `docker compose config --quiet`: PASS.
- PostgreSQL/pgvector smoke test: V1 -> V2 migration, JPA schema validation và HTTP auth flow đều PASS.
- HTTP smoke: role injection trả `400 INVALID_INPUT`; đăng ký chỉ cấp `BUYER`; cookie `HttpOnly`, `SameSite=Lax`, scoped path; refresh rotation/reuse revokes family.
- Database smoke: password lưu dạng `{bcrypt}` và không bằng plaintext; schema không còn CCCD, ảnh hay face embedding.
- `git diff --check HEAD`: PASS.

## Chưa hoàn thành / bị chặn

- Không có blocker trong phạm vi TASK-0005.
- Password reset email/OTP, Google OIDC và Cloudinary tiếp tục ở TASK-0006 đến TASK-0009.
