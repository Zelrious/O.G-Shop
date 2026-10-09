# Commerce Module

## Database report — TASK-0072 / 2026-10-09

[TASK-0072](../progress/archive/TASK-0072-database-design-report.md) đã điền chương bảng database trong báo cáo Google Docs theo schema V23: danh sách 48 bảng và 48 bảng chi tiết/907 trường, giữ mẫu native và kiểm tra căn lề, ghim tiêu đề, căn giữa chiều dọc. Đây là cập nhật tài liệu; trạng thái triển khai module vẫn theo code/kiểm thử. PDF/HTML export bị từ chối 403, chưa kiểm bố cục trang.

## Public policies — TASK-0071 / 2026-10-09

[TASK-0071](../progress/archive/TASK-0071-public-use-case-policies-and-coins.md) sửa UC20–UC25/UC82: 3.000 xu đăng ký, 10.000 xu bài đầu mở bán, 3.000 xu mua đầu hoàn tất, 500 xu gửi đánh giá, 400/500 xu nhận 4/5 sao; tính một lần mỗi mốc/đánh giá. Voucher trước, xu sau, trừ cả hàng/phí giao đến 0 đồng; hủy hoặc hoàn hợp lệ trả xu, không cấp lại voucher. Uy tín tách riêng. Chỉ cập nhật báo cáo, chưa sửa code/database V23.

## Database consolidation — TASK-0070 / V23

9 bảng: cart_items/checkout_groups/orders/order_items/inventory_reservations/vouchers/voucher_products/voucher_grants/reward_ledger. Cart header/point balance lưu users; voucher versions, redemptions, allocations và grant history gộp vào chủ thể. Stock, voucher và point races cùng checkout 0đ đã kiểm chứng.

Local og_shop đã migrate/validate V23: 48 bảng nghiệp vụ, 49 gồm Flyway history; 104 nguồn khớp fingerprint trước/sau. Backend entity/join-table dùng schema og_compat. 252 tests: 251 pass, 1 Windows symlink skip; 33 expansion PostgreSQL pass. [Bàn giao V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md), [dictionary 48 bảng](../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md). API/UI/worker canonical còn cần nối; checkpoint V21/baseline dưới đây là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — TASK-0069 / V21

V17/V21: quantity integer, group/order/item snapshots, giữ lượng, grant/voucher/points allocations và ledger; checkout toàn trợ giá 0đ có confirmation riêng. Deferred constraints yêu cầu một transaction cho toàn checkout. [Bàn giao và ma trận 83 UC](../architecture/DATABASE_EXPANSION_V21_20261009.md). Local og_shop V1–V21 đã migrate/validate; 249 tests, 0 failures/errors, 1 Windows symlink skip; 30 expansion PostgreSQL tests đạt. Đây là nền database và mapping tương thích, chưa nghiệm thu các API/UI mới. Phần Database review và trạng thái baseline cũ bên dưới ghi tình trạng trước TASK-0069.


## Database review — 2026-10-09

Theo [bản rà soát 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), UC20–UC24/UC29–UC31/UC82 cần G05/G06: lượng >1, nhiều giữ hàng, checkout group, snapshot cờ trả/ưu đãi, grant/points ledger và hủy theo giai đoạn. Live schema vẫn CHECK quantity=1; code checkout một món. Chưa triển khai các thay đổi này; trạng thái bên dưới là baseline trước rà soát.

- Status: PLANNED
- Requirements: UC-07, UC-09
- Completion: 0/2 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý cart, checkout group, reservation, Order và thao tác xử lý đơn của Seller.

## Out of scope

Provider payment, shipment tracking và dispute resolution.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/commerce/`
- `frontend/src/features/checkout/` và `frontend/src/features/orders/` khi được tạo
- Flyway tables Cart, CartItem, Order, OrderItem

## Dependencies

- Identity, Catalog, Communication, Payment và Platform.

## Security invariants

- Một Order thuộc đúng một Seller.
- Reservation được tạo trong checkout transaction.
- Checkout khóa Product theo thứ tự ổn định.
- Total và offer price được xác nhận lại ở server.
- OrderItem giữ snapshot lịch sử.
- Offer được chấp nhận chỉ cập nhật giá giao dịch trong `order_items`, không ghi đè `products.listed_price`.
- `total_amount = subtotal + buyer_system_fee + shipping_fee`; `seller_proceeds = subtotal - seller_system_fee`.
- `accepted_offer_id` chỉ được dùng một lần và phải được xác minh `ACCEPTED` trong checkout transaction.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.
- [x] V3 snapshot giá niêm yết, giá thỏa thuận, phí hai phía và seller proceeds trên order/order item.

## Remaining tasks

- [ ] Chốt reservation timeout.
- [ ] Thiết kế checkout command contract.
- [ ] Triển khai row-lock và transaction.
- [ ] Kiểm thử hai Buyer checkout đồng thời.

## Expected changes

Đây là module rủi ro cao; code chỉ được thêm cùng migration và PostgreSQL concurrency test.
