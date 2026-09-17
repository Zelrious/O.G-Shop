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

## Completed tasks

- [x] Tạo package boundary và tài liệu module.

## Remaining tasks

- [ ] Chốt WebSocket authentication và reconnect.
- [ ] Thiết kế offer state machine.
- [ ] Triển khai UC-05 và UC-06.
- [ ] Kiểm thử participant access và duplicate delivery.

## Expected changes

Sẽ bổ sung REST history, WebSocket gateway, application services và frontend conversation state.
