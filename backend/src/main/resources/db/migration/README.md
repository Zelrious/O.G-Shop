# Flyway migrations

## Current schema — V24 / TASK-0073

V24 đã áp dụng/validate local og_shop: bỏ view và bảng `refresh_sessions`, còn 47 bảng nghiệp vụ (48 gồm Flyway history), 103 compatibility views và 7 report views. Migration loại đúng nhánh refresh trong graph validator trước DROP, không dùng CASCADE và giữ các kiểm tra còn lại. Backend/frontend chuyển sang đăng nhập có thời hạn cố định, restore không gia hạn. `auth_challenges` và dữ liệu eKYC giữ nguyên.

V1–V24 immutable sau rollout; thay đổi tiếp theo dùng V25+. Backup/restore, upgrade, 47 fingerprints, PostgreSQL tests và runtime smoke được ghi trong [bàn giao V24](../../../../../../docs/architecture/DATABASE_AUTH_SIMPLIFICATION_V24_20261009.md). Không chỉnh V22 để giảm số bảng.

## Historical consolidation — V23 / TASK-0070

V22 đã migrate/validate local og_shop: 48 bảng nghiệp vụ, 49 public tables gồm Flyway history. Migration chuyển dữ liệu nguyên tử và đối chiếu đủ 104 nguồn trước khi bỏ bảng cũ. Entity/join table dùng view og_compat; schema public giữ 48 physical aggregates và report views. [Handoff](../../../../../../docs/architecture/DATABASE_CONSOLIDATION_V22_20261009.md), [dictionary](../../../../../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md).

V1–V23 immutable; thay đổi tiếp theo dùng V24+. Hai generator và metadata V21 chỉ hỗ trợ tái tạo/đối chiếu artifact, không chỉnh migration đã áp dụng. Checkpoint V21 phía dưới là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Historical expansion — V21 / TASK-0069

Đã áp dụng và validate local og_shop V15–V21: 105 public tables gồm history, 7 view. [Handoff/ma trận 83 UC](../../../../../../docs/architecture/DATABASE_EXPANSION_V21_20261009.md), [test guide](../../../../../../database/tests/README.md), [migration runner](../../../../../../database/scripts/migrate-schema.ps1). V1–V14 giữ checksum; V15–V20 đã rollout, V21 forward-fix checkout 0đ; thay đổi tiếp theo phải dùng V22+.

| Version | Phạm vi mới |
|---|---|
| V15 | OTP/lớp hai, eKYC/private reference/registry, quick-auth, KTV identity và seller approval |
| V16 | Product/media/checklist/AI revisions, quantity, fee policy/assessment/charge/receipt |
| V17 | Checkout/reservations/snapshots, voucher grants/revocations/allocations và reward ledger |
| V18 | Payment intent purpose, confirmed source/allocation, money components/holds/settlement |
| V19 | Shipment legs/handover, case rounds/decisions/evidence, money blockers/cleanup eligibility |
| V20 | Restrictions/penalties/review/audit history, notifications, metrics/export/views |
| V21 | Checkout zero total confirmation riêng, không tạo provider receipt giả |

Dữ liệu legacy mặc định LEGACY_V14; snapshots migration không tự thành KTV/payment proof. API/service mới phải đặt UC83, xác thực caller/provider và commit các aggregate trong một transaction. DDL không tự hoàn thiện 83 API/worker. Runtime notes bên dưới là lịch sử trước rollout này.


Flyway trong thư mục này là nguồn DDL chuẩn của ứng dụng. Không sửa migration đã được chia sẻ hoặc đã chạy; mọi forward-fix phải có version mới.

## Runtime review — 2026-10-09

TASK-0068 đã đọc metadata/schema của DB local og_shop trên PostgreSQL 17.11: Flyway V1–V14 success, 44 bảng public gồm history. [Bản rà soát 83 UC](../../../../../../docs/architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md) ghi các phần đủ/thiếu, thiết kế mở rộng và giới hạn kiểm chứng. Không chạy migration hoặc Flyway validate trong lần rà soát này. Các ghi chú runtime chỉ tới V8/V13 phía dưới là lịch sử tại thời điểm task cũ, không phải trạng thái local hiện tại.

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
| `V12__explicit_moderation_proof_and_legacy_history.sql` | Bổ sung proof kiểm duyệt và lưu lịch sử cũ |
| `V13__vnpay_payment_attempts.sql` | VNPAY attempt và trạng thái hoàn tiền |
| `V14__add_vnpay_ipn_receipts.sql` | IPN receipt, event, đối soát và audit |

V3 seed `DEFAULT_ZERO_V1` là policy tương thích 0%, không phải mức phí kinh doanh. Khi chốt mức phí, retire policy cũ và tạo version mới; không update policy đã được offer/order tham chiếu.

V8 giữ `products.category_id` làm projection tương thích của danh mục đầu tiên. FK `(product_id, category_id)` tới `product_categories` được deferred để Hibernate thêm/thay collection trong cùng transaction. Dữ liệu cũ được backfill trước khi thêm FK; không sửa V1–V7. Không rollback bằng cách xóa liên kết mới; dùng migration tiếp theo để forward-fix. Xem [kiểm chứng PostgreSQL](../../../../../../database/tests/README.md).

Database local vừa kiểm tra có KTV nhưng Flyway history chỉ đến V8. Trước khi chạy backend trên database hiện có, kiểm chứng tuyến nâng cấp V9–V11 trên bản sao/DB test riêng; không tự repair/reset history. V10 còn vấn đề tương thích proof kiểm duyệt cũ và thay reason lịch sử; các vấn đề này chưa được khép. Việc commit SQL không áp dụng migration vào database.

## Forward fixes TASK-0059

V1–V11 giữ nguyên checksum. V12 thêm decision_content_revision nullable cho proof rõ ràng và archive append-only; callback LegacyModerationHistoryCallback bảo toàn original_record trước khi V10 normalize reason. Legacy proof NULL phải gửi duyệt lại. Dữ liệu đã mất ở installation chạy V10 trước đây cần backup.

V13 thêm vnpay_payment_attempts, method VNPAY và ràng buộc refund cho tiền đến sau hủy/hết hạn. Giao dịch mock vẫn yêu cầu escrow trước hoàn tiền. Đã kiểm chứng 25 PostgreSQL tests, gồm V9→V13, trên container riêng; không áp dụng SQL vào DB ứng dụng. Snapshot rủi ro V10 phía trên được thay bằng forward fix này; chưa nghiệm thu provider E2E.
