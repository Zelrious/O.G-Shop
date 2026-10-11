# Auth flows V26

V26 giữ V1–V25 và 47 bảng nghiệp vụ (48 nếu tính Flyway history). Không tạo lại refresh_sessions hoặc thêm bảng cho từng luồng.

- `public.users.credential_version`: bigint mặc định 0; trigger tăng khi hash mật khẩu đổi, cùng transaction. JWT mới có `cv`; token cũ thiếu `cv` chỉ tương thích khi version còn 0. Decoder kiểm database trên mọi Bearer request và cookie restore, không dùng cache. Phiên vẫn hết hạn cố định V24.
- `public.users.google_subject`: canonical identity Google, unique cho user; backfill từ external_identities history. Ghi cùng projection cũ khi liên kết, provider subject ổn định, không dựa riêng vào email.
- `public.auth_challenges.auth_data`: metadata user/version/Google/ticket; không có OTP hoặc mật khẩu plaintext. Dùng purpose riêng LOGIN_EMAIL_2FA, PASSWORD_RESET, GOOGLE_LINK, GOOGLE_NONCE, GOOGLE_REGISTER, PASSWORD_RESET_COMPLETE. Nonce/ticket/grant một lần; xác minh OTP reset tiêu thụ mã và phát grant mới có hạn 5 phút.
- V22 view users writer được mở rộng phép so sánh OLD với credential_version, không bỏ guard chống ghi đè. Validator cũ vẫn được gọi; chỉ thêm các purpose handoff và decoy reset không có user.
- Verify sai commit bộ đếm; consume tăng bộ đếm và trạng thái trong một câu lệnh theo guard lịch sử. Cooldown/hourly limits và advisory locks theo requester → email; row lock user trước kiểm credential, OTP challenge khóa trước hiệu ứng.
- Cleanup mỗi phút xóa auth_data của OPEN đã hết hạn theo batch 100 SKIP LOCKED; lịch sử digest/deadline/bộ đếm được giữ. Password reset gửi email sau commit qua queue giới hạn 100, hai worker; mất kết nối/đầy queue/restart thì không đổi mật khẩu, người dùng gửi lại sau cooldown. Queue chưa phải outbox bền vững.

Migration đặt lock_timeout 10s, statement_timeout 120s và advisory lock triển khai; thêm cột/index và quét identity Google hiện có. Trước áp dụng vào database có dữ liệu cần backup có kiểm restore. Phục hồi bằng bản backup hoặc migration forward phù hợp, không sửa checksum V1–V25. Chưa áp V26 vào database local đang dùng của người dùng trong TASK-0086.

Kiểm thử `AuthFlowsPostgresTest`: V25 → V26 giữ fixture; clean V1 → V26; JPA validate; OTP replay/attempts/cooldown/expiry/concurrency; reset generic/SMTP delay/rollback; bearer/cookie và token cũ thiếu cv bị thu hồi; Google link/new Buyer/Seller và staff boundary. Chỉ chạy trên database UUID của container pgvector riêng; không dùng dữ liệu người dùng. [Bàn giao](../../output/auth-fixes-2026-10-11/README.md).
