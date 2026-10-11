# Commerce Module

## Thưởng đăng ký — TASK-0082 / V25 / 2026-10-10

`RegistrationCommerceFacade` cung cấp hai thao tác tham gia transaction do Identity mở: tạo địa chỉ đúng chủ và thưởng đăng ký đúng 3.000 xu. Không gọi repository xuyên module. Ledger CREDIT_REGISTRATION dùng command key theo user, unique một lần/user, chỉ chấp nhận account UC83 ACTIVE/email đã xác thực; không tạo policy/Admin/order giả. V25 delegate mọi validator cũ và giữ quy tắc số dư/nguồn thưởng cũ.

[Thiết kế V25](../../database/docs/EMAIL_REGISTRATION_V25.md). 12 ca đăng ký PostgreSQL đạt, gồm lỗi ghi ledger thật làm rollback user/OTP, xác nhận đồng thời, chặn duplicate/sai số xu và địa chỉ đúng chủ. V25 local giữ 47/47 fingerprints sau backup/restore/migration. Những nguồn xu khác trong UC22 và sử dụng xu khi checkout thuộc công việc tiếp nối; không nâng nghiệm thu toàn bộ Commerce.

## Diagram báo cáo — TASK-0081 / 2026-10-10

[UC01–UC18 đã có 36 ảnh kiểm nguồn/native/PDF](../requirements/use-cases-83/report-uc01-18/README.md), gồm UC13 và các điểm nối thao tác mua/giỏ. Thay 34 ảnh được duyệt và thêm hai ảnh UC11, giữ Hình 3.3–3.38 và ảnh ngoài phạm vi. 2FA chỉ dành cho Người mua/Người bán theo quyết định người dùng. Đây là cập nhật yêu cầu/diagram; không nâng trạng thái code/schema/nghiệm thu module.

## Report diagrams — TASK-0079 / 2026-10-10

[UC19–UC83 đã có đủ 130 Activity/Sequence trong báo cáo](../requirements/use-cases-83/report-from-uc19/README.md), kiểm native/PDF trọn trang. RS-02/RS-05 được chốt và đồng bộ theo xác nhận người dùng; chuẩn Activity/Sequence gọn được giữ. Đây là tài liệu, không nâng trạng thái code/database/nghiệm thu module.

## Diagram presentation — TASK-0077 / 2026-10-10

Mã Activity/Sequence hiện dùng [cách trình bày gọn](../requirements/use-cases-83/diagram-presentation/README.md): bỏ legend khỏi ảnh, rút nhãn và giữ đủ đường đi theo đặc tả. UC82 trong báo cáo đã thay bằng hai hình mới. Kiểm nguồn/1.000 đường đi và render 166 PNG đạt; ảnh UC79–UC83 đã kiểm trọn một trang trên PDF. Đây là cập nhật trình bày tài liệu.

## Report diagrams — TASK-0076 / 2026-10-10

Đã chèn Activity/Sequence UC82 theo source TASK-0075, giữ đặc tả hủy từng đơn. Mỗi hình cùng title/caption nằm trọn một trang, kiểm native/PDF bản sao cùng layout. [Bằng chứng](../requirements/use-cases-83/report-insertion/README.md). Không đổi code/schema/nghiệm thu module.

## Current use-case documents — TASK-0075 / 2026-10-10

[Bộ 83 đặc tả/166 Activity–Sequence](../requirements/use-cases-83/README.md) đã đọc lại trực tiếp báo cáo và viết theo 830 trường hiện hành; [rà soát](../requirements/use-cases-83/REVIEW.md) ghi các điểm nguồn cần làm rõ. UC10/UC20–UC24/UC29–UC31/UC61/UC82 giữ lựa chọn lúc đặt, voucher trước xu, giỏ/nhóm đơn, lịch sử và hủy theo từng đơn. Đây là yêu cầu và diagram ở mức yêu cầu; trạng thái code/schema/nghiệm thu của module không được nâng từ kết quả tài liệu. [Bàn giao](../handoff/USE_CASE_83_AGENT_HANDOFF.md).

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
