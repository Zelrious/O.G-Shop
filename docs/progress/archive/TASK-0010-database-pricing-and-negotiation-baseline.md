# TASK-0010 — Database pricing and negotiation baseline

- Status: COMPLETE
- Approved: 2026-09-19
- Owner: O.G Shop
- Modules: Catalog, Communication, Commerce, Payment, Platform

## Mục tiêu

Hoàn thiện schema trước khi triển khai core flow: tách giá niêm yết, phí hệ thống, giá offer và snapshot hóa đơn hàng; đồng thời chuẩn hóa chat/offer cho realtime và Redis sau này.

## Phạm vi đã duyệt

- Tạo Flyway V3; không sửa V1/V2 đã chia sẻ.
- Giữ `carts`/`cart_items` vì chưa có quyết định bỏ checkout nhiều sản phẩm.
- Tách giá niêm yết khỏi giá hiển thị; phí hệ thống do backend tính từ policy có version.
- Offer thuộc conversation, hỗ trợ counter-offer bằng chuỗi bất biến và snapshot phí.
- Lưu read cursor theo participant thay vì update từng message; thêm idempotency key cho message.
- Sản phẩm ghim là snapshot trên conversation, không phải message hay bảng riêng.
- Order/order item snapshot giá thỏa thuận, phí buyer/seller và seller proceeds.
- Thêm transactional outbox cho WebSocket/Redis/notification sau commit.
- Không tự áp một mức phí kinh doanh chưa được chốt; seed policy 0% để bảo toàn hành vi hiện tại.

## Tương thích và forward-fix

- Migration backfill conversation cho offer V1 chưa có conversation tương ứng.
- Offer V1 được coi là do buyer đề xuất, vì schema cũ không hỗ trợ seller counter-offer.
- Dữ liệu order cũ được backfill phí 0, `agreed_price = listed_price`, seller proceeds bằng subtotal.
- Rollback production không drop column/bảng sau khi có giao dịch; nếu cần khôi phục sẽ dùng migration forward-fix và compatibility view/column.

## Kế hoạch kiểm chứng

- Chạy V1 -> V2 -> V3 trên PostgreSQL/pgvector rỗng.
- Chạy V1 -> V2, chèn dữ liệu legacy, sau đó chạy V3 và kiểm tra backfill.
- Chạy database constraint tests cho membership, duplicate message, pending offer, fee snapshot và order totals.
- Chạy backend tests/JPA schema validation và documentation validation.

## Kết quả

- Tạo Flyway `V3__pricing_negotiation_and_chat_baseline.sql` và áp dụng thành công lên database development.
- Schema có 29 bảng nghiệp vụ; database chạy Flyway có 30 bảng khi tính `flyway_schema_history`.
- Tạo `database/run_database_tests.ps1`, clean migration/invariant tests và legacy backfill tests.
- Cập nhật module docs, database design/risk/test report, traceability và decision backlog.

## Kiểm chứng

- `.\database\run_database_tests.ps1 -PostgresImage 'pgvector/pgvector:pg17'`: PASS.
  - 24 smoke/invariant assertions PASS.
  - 9 V1/V2 -> V3 legacy backfill assertions PASS.
- `.\scripts\quality\verify-backend.ps1`: PASS, 12/12 tests và Maven build thành công.
- `.\scripts\quality\validate-docs.ps1`: PASS.
- `git diff --check`: PASS.
- Flyway development database: V1/V2/V3 đều `success=true`; `DEFAULT_ZERO_V1` là policy active.
- Runtime health: Backend `UP`, eKYC `healthy`.

## Chưa hoàn thành trong task này

- Chưa chốt mức phí thực tế (DP-09); schema đang dùng compatibility policy 0%.
- Chưa chốt bỏ hay giữ cart (DP-10); V3 không xóa `carts`/`cart_items`.
- Chưa triển khai Catalog/Chat/Offer/Checkout API, WebSocket, Redis hoặc outbox worker.
- Concurrency test hai transaction accept/checkout cùng product sẽ đi cùng application command của UC-06/UC-07.
