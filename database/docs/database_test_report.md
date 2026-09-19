# Báo cáo chạy thử database

## Môi trường

- Ngày chạy: 2026-09-19
- Engine: PostgreSQL 17 + pgvector (`pgvector/pgvector:pg17`)
- Phạm vi: V1 -> V2 -> V3, constraint/invariant và legacy backfill
- Cách ly: container tạm không gắn volume, không dùng database ứng dụng

## Kết quả schema V3

- Clean V1 -> V2 -> V3: PASS.
- 29 bảng nghiệp vụ được tạo; khi chạy qua Flyway sẽ có thêm `flyway_schema_history`.
- PostgreSQL catalog ghi nhận 203 constraint và 115 index, bao gồm primary/unique index tự sinh.
- 24 smoke/invariant assertions: PASS; dữ liệu test được rollback.
- V1/V2 legacy fixture -> V3: PASS.
- 9 legacy migration/backfill assertions: PASS.

## Các invariant V3 đã kiểm tra

1. Email lowercase/unique và mỗi user chỉ có một default address.
2. Mỗi user chỉ có một KYC `PENDING` hoặc `VERIFIED`.
3. Chỉ một system-fee policy ở trạng thái `ACTIVE`.
4. Conversation unique theo buyer/seller/product.
5. Message retry với cùng `client_message_id` bị từ chối.
6. User ngoài conversation không thể insert message hoặc offer.
7. Read cursor được lưu theo participant.
8. Chỉ một offer `PENDING` trên conversation; counter-offer giữ parent chain.
9. Order total bao gồm buyer fee + shipping; seller proceeds trừ seller fee.
10. Order item snapshot listed/agreed price, phí, accepted offer và pricing source.
11. Product reservation có owner/expiry và owning order phải chứa product.
12. Address order là snapshot, không đổi theo address nguồn.
13. Payment unique/order; partial refund bị chặn trong MVP.
14. Complaint active unique/order; report bắt buộc có target.
15. Product đã tham gia giao dịch không hard-delete được.
16. Outbox payload bắt buộc là JSON object.

## Legacy backfill đã kiểm tra

- `products.price` được giữ nguyên giá trị trong `listed_price`.
- Conversation cũ có product snapshot, `last_message_id` và `last_activity_at`.
- Offer cũ chưa có conversation được tạo conversation tương ứng.
- Offer cũ được coi là Buyer proposal và snapshot phí 0.
- Buyer/Seller state được backfill cho mọi conversation.
- Message cũ được sinh `client_message_id`.
- Order/order item cũ giữ nguyên total với phí 0 và `pricing_source = LIST_PRICE`.
- Các cột legacy đã thay thế không còn trong schema V3.

## Chưa kiểm tra

- Hai connection cùng accept offer/checkout một product.
- Worker outbox retry/crash recovery vì application worker chưa triển khai.
- Payment callback đối đầu timeout job.
- Hiệu năng với dataset lớn và `EXPLAIN ANALYZE`.
- Backup/restore drill và runtime database role production.

Các mục này cần application command, nhiều connection hoặc workload thật và sẽ được thêm khi triển khai UC-05–UC-08.
