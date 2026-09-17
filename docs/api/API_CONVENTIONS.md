# API Conventions

## Base path

REST API sử dụng `/api/v1`. Health check sử dụng `/actuator/health`.

## Resource conventions

- Dùng danh từ số nhiều: `/products`, `/orders`, `/complaints`.
- Hành động nghiệp vụ dùng sub-resource hoặc command rõ nghĩa: `/orders/{id}/cancel`.
- Không cho client gửi role, owner ID, total hoặc trạng thái do server sở hữu.
- ID trong URL luôn được kiểm tra ownership và visibility ở Backend.

## Error contract

```json
{
  "code": "ORDER_STATE_CONFLICT",
  "message": "Không thể thực hiện thao tác ở trạng thái hiện tại.",
  "requestId": "generated-request-id",
  "fieldErrors": []
}
```

Không trả stack trace, SQL, token hoặc thông tin nội bộ.

## Pagination

- Dùng `page`, `size`, `sort` cho màn hình quản trị đơn giản.
- Giới hạn `size` phía server.
- Cursor pagination chỉ được thêm khi có yêu cầu và số liệu chứng minh.

## Idempotency

Checkout, payment callback, release và refund phải có idempotency key hoặc unique transaction reference và trả kết quả ổn định khi nhận request lặp.
