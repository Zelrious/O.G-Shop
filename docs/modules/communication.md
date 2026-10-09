# Communication Module

## Database report — TASK-0072 / 2026-10-09

[TASK-0072](../progress/archive/TASK-0072-database-design-report.md) đã điền chương bảng database trong báo cáo Google Docs theo schema V23: danh sách 48 bảng và 48 bảng chi tiết/907 trường, giữ mẫu native và kiểm tra căn lề, ghim tiêu đề, căn giữa chiều dọc. Đây là cập nhật tài liệu; trạng thái triển khai module vẫn theo code/kiểm thử. PDF/HTML export bị từ chối 403, chưa kiểm bố cục trang.

## Public policies — TASK-0071 / 2026-10-09

[TASK-0071](../progress/archive/TASK-0071-public-use-case-policies-and-coins.md) sửa UC18–UC19/UC47/UC56: chat, thương lượng và quyền đối tác được viết theo thao tác sử dụng; dấu hiệu chat chưa phải vi phạm. KTV xác nhận ngôn từ không phù hợp trừ 3–5 uy tín, cấm chat 1 tuần–3 tháng theo mốc tuần hoặc cấm tài khoản nếu nặng. Giữ lịch sử và phản hồi hồ sơ chính thức của đơn cũ. Báo cáo đã đọc lại, chưa triển khai chính sách mới vào code/V23.

## Database consolidation — TASK-0070 / V23

3 bảng: conversations/messages/offers; participant state và media quota lưu trên conversations. Order conversation links lưu trên orders và conversation_id của order_items. Không bỏ lịch sử thương lượng hay quyền của hai bên.

Local og_shop đã migrate/validate V23: 48 bảng nghiệp vụ, 49 gồm Flyway history; 104 nguồn khớp fingerprint trước/sau. Backend entity/join-table dùng schema og_compat. 252 tests: 251 pass, 1 Windows symlink skip; 33 expansion PostgreSQL pass. [Bàn giao V22](../architecture/DATABASE_CONSOLIDATION_V22_20261009.md), [dictionary 48 bảng](../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md). API/UI/worker canonical còn cần nối; checkpoint V21/baseline dưới đây là lịch sử.

V22 tạo cấu trúc 48 bảng; V23 đã căn bộ đếm ID qua mọi loại dòng, kiểm thử tạo mới profile/legacy verification/voucher/payment sau populated upgrade đạt. Trạng thái runtime hiện hành là V23.

## Database expansion — TASK-0069 / V21

V17/V20 tái dùng conversations/messages/offers, thêm conversation_order_links, kiểm tra accepted offer đúng parties/product và blocks theo tài khoản. API chat và lifecycle media cho nghĩa vụ cũ còn cần nối service. [Bàn giao và ma trận 83 UC](../architecture/DATABASE_EXPANSION_V21_20261009.md). Local og_shop V1–V21 đã migrate/validate; 249 tests, 0 failures/errors, 1 Windows symlink skip; 30 expansion PostgreSQL tests đạt. Đây là nền database và mapping tương thích, chưa nghiệm thu các API/UI mới. Phần Database review và trạng thái baseline cũ bên dưới ghi tình trạng trước TASK-0069.


## Database review — 2026-10-09

Theo [bản rà soát 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), UC18–UC19/UC47/UC56 tái dùng participant/message dedup/offer chain; cần link đơn, snapshot lượng, kiểm tra offer đúng buyer/product và quyền trao đổi nghĩa vụ cũ khi chặn. Media hold chỉ từ hồ sơ chính thức, chat không tự giữ tiền. Chưa triển khai; trạng thái bên dưới là baseline trước rà soát.

- Status: PLANNED
- Requirements: UC-05, UC-06
- Completion: 0/2 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý conversation giữa Buyer/Seller, message realtime và offer cho một Product.

## Out of scope

Reservation, checkout, payment và quyết định khiếu nại.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/communication/`
- `frontend/src/features/chat-offer/` khi được tạo
- Flyway tables Conversation, Message, Offer

## Dependencies

- Identity cho participant.
- Catalog cho Product và giá niêm yết.
- Platform cho notification.

## Security invariants

- Chỉ participant được xem conversation.
- Offer không giữ hàng.
- Message/evidence riêng tư không dùng public URL dài hạn.
- Message sender và offer proposer/responder phải có row `(conversation_id, user_id)` trong `conversation_user_state`.
- `client_message_id` idempotent trong một conversation; retry không được sinh message thứ hai.
- Mỗi conversation chỉ có một offer `PENDING`; counter-offer tạo row mới và trỏ `parent_offer_id`.
- Redis/PubSub không phải nguồn lịch sử chat; PostgreSQL và outbox là biên bền vững.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.
- [x] V3 chuẩn hóa conversation product snapshot, read cursor, message idempotency và multi-round offer chain.

## Remaining tasks

- [x] Thiết kế offer state machine ở database (`PENDING/ACCEPTED/REJECTED/COUNTERED/WITHDRAWN/EXPIRED`).
- [ ] Triển khai REST tạo/lấy conversation, lịch sử cursor, gửi message và read cursor.
- [ ] Triển khai offer/counter-offer/reject/withdraw/accept trong transaction.
- [ ] Kết nối frontend chat/offer cơ bản bằng REST hoặc polling.
- [ ] Kiểm thử participant access, idempotency, transition và accept đồng thời.
- [ ] Chỉ sau khi các mục trên đạt: bổ sung WebSocket single-instance và chốt authentication/reconnect.
- [ ] Chỉ bổ sung Redis cache/Pub/Sub khi có số đo hoặc yêu cầu multi-instance.

## Expected changes

Ưu tiên REST history, application services và frontend conversation state để hoàn thiện hành vi nghiệp vụ. WebSocket là lớp realtime tiếp theo; Redis là lớp tối ưu sau cùng và không phải nguồn dữ liệu hay điều kiện để chat hoạt động.
