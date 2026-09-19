# Data Flow

## HTTP command

```text
Client request
  -> Controller validates shape
  -> Application service authorizes role + ownership + state
  -> Domain rule evaluates transition
  -> Repository persists business state and OutboxEvent in one transaction
  -> Commit
  -> DTO response
```

Trong giai đoạn core flow, PostgreSQL transaction và DTO response là đường chạy bắt buộc. `OutboxEvent` có thể được ghi cùng transaction để giữ khả năng mở rộng, nhưng chưa yêu cầu worker, WebSocket hoặc Redis để request thành công.

## Accepted offer and pricing

```text
Buyer/Seller creates offer
  -> Backend calculates fee breakdown from one immutable policy version
  -> Offer stores item price + fee/proceeds snapshot
  -> Seller/recipient accepts while locking Offer + Product
  -> OrderItem snapshots listed price, agreed price, fees and accepted offer
  -> Product becomes RESERVED; public listed price is unchanged
  -> OutboxEvent is committed for delivery ở giai đoạn sau
```

## Delivery phases

1. Core: REST + PostgreSQL cho catalog, chat history/message, offer, checkout và order.
2. Realtime: WebSocket single-instance sau khi core command và authorization đã được kiểm thử.
3. Scale: outbox worker và Redis cache/Pub/Sub sau khi có số đo hiệu năng hoặc yêu cầu multi-instance.

Redis chỉ được cache hot reads hoặc phân phối realtime event. PostgreSQL luôn là durable source của chat, offer, order và outbox; Redis không khả dụng không được làm hỏng luồng đọc/ghi cốt lõi.

## Payment callback

```text
Provider/mock callback
  -> Verify source/signature or sandbox token
  -> Resolve idempotency key
  -> Lock current Payment/Order state
  -> Apply allowed transition once
  -> Record audit and notification
  -> Return stable acknowledgement
```

## Read flow

```text
Client query
  -> Controller parses filter/page
  -> Query service applies visibility rules
  -> Repository/projection returns allowed fields
  -> DTO response
```

Sensitive media is represented by private object keys. A short-lived signed URL is issued only after access checks.
