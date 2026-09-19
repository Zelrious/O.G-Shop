# Flyway migrations

Flyway trong thư mục này là nguồn DDL chuẩn của ứng dụng. Không sửa migration đã được chia sẻ hoặc đã chạy; mọi forward-fix phải có version mới.

| Version | Phạm vi |
|---|---|
| `V1__initial_schema.sql` | Core marketplace baseline |
| `V2__identity_and_ekyc_security_baseline.sql` | Session/OAuth identity và eKYC không lưu sinh trắc học thô |
| `V3__pricing_negotiation_and_chat_baseline.sql` | Phí có version, chat cursor/idempotency, offer chain, pricing snapshot và outbox |

V3 seed `DEFAULT_ZERO_V1` là policy tương thích 0%, không phải mức phí kinh doanh. Khi chốt mức phí, retire policy cũ và tạo version mới; không update policy đã được offer/order tham chiếu.
