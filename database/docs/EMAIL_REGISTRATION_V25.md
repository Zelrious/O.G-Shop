# V25 — Đăng ký email OTP và thưởng đăng ký

Migration: [V25__email_otp_registration.sql](../../backend/src/main/resources/db/migration/V25__email_otp_registration.sql). Đây là bản mở rộng tiếp nối V24, không sửa V1–V24.

## Dữ liệu và tương thích

Giữ 47 bảng nghiệp vụ, 103 compatibility views và dữ liệu cũ. Không tạo lại `refresh_sessions`. Tài khoản mới có `identity_workflow=UC83`, `email_verified_at` và role `BUYER`; user cũ giữ workflow/hash/quyền hiện có. Không thưởng lại tài khoản cũ khi đăng nhập.

| Bảng public | Cột mới | Công dụng |
|---|---|---|
| auth_challenges | registration_data jsonb | Hồ sơ chờ với hash mật khẩu; NULL sau xác nhận/hủy/hết hạn |
| auth_challenges | registration_expires_at timestamptz | Hạn cuối hồ sơ chờ; gửi lại không gia hạn |
| auth_challenges | registration_requester_digest varchar(64) | HMAC IP kết nối để giới hạn gửi; không lưu IP thô |
| seller_profiles | store_name varchar(120) | Tên cửa hàng thu ở bước đăng ký |
| seller_profiles | contact_phone varchar(20) | Số liên hệ cửa hàng |
| seller_profiles | return_address_id bigint | FK (address_id,user_id), địa chỉ trả hàng đúng chủ |

Identity đọc/ghi metadata mới qua repository riêng trên public; JPA và API cũ tiếp tục dùng og_compat. Commerce sở hữu tạo địa chỉ và thưởng qua application facade, không truy cập repository xuyên module. Hai địa chỉ giống nhau dùng chung một bản ghi.

## Invariant

- Challenge chỉ có draft khi loại auth_challenges, purpose REGISTER_EMAIL và state OPEN; JSON object, hard expiry không trước OTP expiry.
- Draft/requester/hard expiry không được đổi tùy tiện. Sửa thông tin hoặc gửi lại tạo challenge mới và vô hiệu challenge trước; lịch sử vẫn giữ.
- Một email chỉ có một challenge OPEN và một user. Advisory lock theo email/requester cùng khóa bản ghi challenge tuần tự hóa gửi/nhập.
- CREDIT_REGISTRATION: đúng 3.000, một lần/user; không policy/order/group/actor giả. Nguồn hệ thống có reason cố định và command key REGISTER_EMAIL:userId.
- Thưởng chỉ cho user UC83 ACTIVE, email đã xác thực, chưa xóa. Dùng reward account/ledger hiện có, giữ nguyên quy tắc các loại thưởng cũ.
- Hàm validator cũ được sao chép thành og25_validate_existing_row rồi delegate; chỉ bổ sung loại CREDIT_REGISTRATION. Hook cập nhật balance vẫn kiểm nguồn thưởng cũ, số dư âm và quyền Admin correction.
- Xác nhận đúng tạo user, ledger, địa chỉ/hồ sơ Seller và đánh dấu OTP đã dùng trong một transaction. Lỗi bất kỳ bước lưu/commit hoàn tác tất cả. Sai mã commit số lượt rồi mới trả lỗi.
- Seller đăng ký có state PENDING_EKYC, chưa có SELLER. Cơ chế AI/MVP cũ bị chặn cho user mới; cấp quyền sau eKYC và KTV là công việc đợt 3.

## Kiểm thử và áp dụng

`EmailRegistrationPostgresTest` tự tạo database tên ngẫu nhiên từ PostgreSQL local dành riêng kiểm thử, migrate V24 với user cũ rồi áp dụng V25 qua Spring Boot. Kiểm tra JPA validate, OTP không tạo user/token sớm, nhập sai/resend/cancel, lỗi SMTP/reward rollback, xác nhận đồng thời, profile Seller, bảo toàn legacy login và số bảng.

`DatabaseExpansionPostgresTest` kiểm toàn bộ V1–V25 trên database trống để kiểm các guard cũ. `RefreshSessionRemovalPostgresTest` giữ target V24 vì nó kiểm riêng việc bỏ refresh sessions; không dùng fingerprint V24 để khẳng định các cột thêm ở V25 không đổi.

Chỉ dùng URL local có tên ogshop_*test*, không reset database ứng dụng. Trước áp dụng V25 cho database có dữ liệu cần backup và restore kiểm tra. V24 dictionary/DDL tiếp tục là checkpoint lịch sử; sáu cột trên là phần mở rộng V25, chưa tự cập nhật báo cáo Google Docs.

## Checkpoint đã xác minh — 2026-10-10

- Backend 285 tests: 259 pass/26 skip, 0 failures/errors; PostgreSQL 46 pass gồm 12 registration, 33 expansion và một V24 removal.
- Đã backup V24, khôi phục trong container riêng, migrate/validate V25 trên clone và local og_shop. Count/hash cả 47 bảng giữ nguyên qua từng bước; fingerprint chuẩn UTC và sort COLLATE C để không phụ thuộc timezone/locale của image.
- Local V25: 47 bảng nghiệp vụ/901 trường, 48 public tables gồm Flyway history; 103 compatibility views vẫn giữ. Không sửa migrations V1–V24.
- JAR mới chạy thử cổng 18083 riêng với worker/mail tắt: health/categories/products 200, đăng ký thiếu SMTP trả 503 OTP_EMAIL_UNAVAILABLE, không tạo user/challenge.
- Runtime và container kiểm thử riêng đã dừng/dọn. Backup và bằng chứng ở output/auth-registration-2026-10-10 local bị Git ignore; không chứa trong commit. Gmail thật chờ cấu hình local.
