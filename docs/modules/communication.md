# Communication Module

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
