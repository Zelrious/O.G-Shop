# Communication Module

## Diagram báo cáo — TASK-0081 / 2026-10-10

[UC01–UC18 đã có 36 ảnh kiểm nguồn/native/PDF](../requirements/use-cases-83/report-uc01-18/README.md), gồm UC18, quyền gửi và nhánh lỗi giữ bản soạn. Thay 34 ảnh được duyệt và thêm hai ảnh UC11, giữ Hình 3.3–3.38 và ảnh ngoài phạm vi. 2FA chỉ dành cho Người mua/Người bán theo quyết định người dùng. Đây là cập nhật yêu cầu/diagram; không nâng trạng thái code/schema/nghiệm thu module.

## Report diagrams — TASK-0079 / 2026-10-10

[UC19–UC83 đã có đủ 130 Activity/Sequence trong báo cáo](../requirements/use-cases-83/report-from-uc19/README.md), kiểm native/PDF trọn trang. RS-02/RS-05 được chốt và đồng bộ theo xác nhận người dùng; chuẩn Activity/Sequence gọn được giữ. Đây là tài liệu, không nâng trạng thái code/database/nghiệm thu module.

## Diagram presentation — TASK-0077 / 2026-10-10

Mã Activity/Sequence hiện dùng [cách trình bày gọn](../requirements/use-cases-83/diagram-presentation/README.md): bỏ legend khỏi ảnh, rút nhãn và giữ đủ đường đi theo đặc tả. Kiểm nguồn/1.000 đường đi và render 166 PNG đạt; ảnh UC79–UC83 đã kiểm trọn một trang trên PDF. Đây là cập nhật trình bày tài liệu.

## Current use-case documents — TASK-0075 / 2026-10-10

[Bộ 83 đặc tả/166 Activity–Sequence](../requirements/use-cases-83/README.md) đã đọc lại trực tiếp báo cáo và viết theo 830 trường hiện hành; [rà soát](../requirements/use-cases-83/REVIEW.md) ghi các điểm nguồn cần làm rõ. UC18/UC19 và UC34–UC60 giữ trao đổi, thương lượng, thông báo theo sự kiện/quyền nhận; UC57/UC58 đã có nội dung trong nguồn mới. Đây là yêu cầu và diagram ở mức yêu cầu; trạng thái code/schema/nghiệm thu của module không được nâng từ kết quả tài liệu. [Bàn giao](../handoff/USE_CASE_83_AGENT_HANDOFF.md).

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
