# Flyway migrations

Flyway trong thư mục này là nguồn DDL chuẩn của ứng dụng. Không sửa migration đã được chia sẻ hoặc đã chạy; mọi forward-fix phải có version mới.

| Version | Phạm vi |
|---|---|
| `V1__initial_schema.sql` | Core marketplace baseline |
| `V2__identity_and_ekyc_security_baseline.sql` | Session/OAuth identity và eKYC không lưu sinh trắc học thô |
| `V3__pricing_negotiation_and_chat_baseline.sql` | Phí có version, chat cursor/idempotency, offer chain, pricing snapshot và outbox |
| `V4__mvp_seller_activation.sql` | Seller activation MVP_BYPASS |
| `V5__catalog_mvp_categories.sql` | Danh mục cho luồng Catalog cơ bản |
| `V6__voucher_and_media_lifecycle_baseline.sql` | Voucher, snapshot giảm giá, media limits và lifecycle |
| `V7__buyer_ekyc_and_profile_baseline.sql` | Profile/ngân hàng và cờ yêu cầu eKYC Người mua |
| `V8__multiple_product_categories.sql` | Quan hệ sản phẩm–danh mục nhiều-nhiều, backfill từ danh mục cũ, PK/FK/index và bảo đảm ít nhất một liên kết |
| `V9__product_moderation_decisions.sql` | Lịch sử quyết định kiểm duyệt thủ công |
| `V10__product_moderation_enforcement.sql` | Content revision, command/replay và triggers kiểm duyệt |
| `V11__add_ktv_role.sql` | Role KTV và chuyển fixture KTV khỏi BUYER |

V3 seed `DEFAULT_ZERO_V1` là policy tương thích 0%, không phải mức phí kinh doanh. Khi chốt mức phí, retire policy cũ và tạo version mới; không update policy đã được offer/order tham chiếu.

V8 giữ `products.category_id` làm projection tương thích của danh mục đầu tiên. FK `(product_id, category_id)` tới `product_categories` được deferred để Hibernate thêm/thay collection trong cùng transaction. Dữ liệu cũ được backfill trước khi thêm FK; không sửa V1–V7. Không rollback bằng cách xóa liên kết mới; dùng migration tiếp theo để forward-fix. Xem [kiểm chứng PostgreSQL](../../../../../../database/tests/README.md).

Database local vừa kiểm tra có KTV nhưng Flyway history chỉ đến V8. Trước khi chạy backend trên database hiện có, kiểm chứng tuyến nâng cấp V9–V11 trên bản sao/DB test riêng; không tự repair/reset history. V10 còn vấn đề tương thích proof kiểm duyệt cũ và thay reason lịch sử; các vấn đề này chưa được khép. Việc commit SQL không áp dụng migration vào database.
