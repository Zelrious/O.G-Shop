# Architecture Overview

## Chosen architecture

O.G Shop sử dụng **Modular Monolith**: một Backend deployable nhưng code được chia thành các module nghiệp vụ có boundary rõ ràng.

## Why this option

Ưu điểm:

- Dễ chạy, debug và triển khai trong phạm vi đồ án.
- Transaction giữa các module cốt lõi đơn giản hơn microservices.
- Giữ được khả năng tách module trong tương lai nếu boundary ổn định.
- Giảm chi phí hạ tầng, observability và distributed consistency.

Trade-off:

- Một deployment có thể ảnh hưởng toàn hệ thống.
- Boundary chỉ hiệu quả khi được kiểm tra bằng review và test kiến trúc.
- Scale độc lập từng module khó hơn microservices.

Microservices chưa phù hợp vì luồng Order–Payment–Complaint cần consistency mạnh, trong khi nguồn lực và scope là đồ án môn học.

## Runtime view

```text
Browser
  -> React Frontend
  -> Spring Boot REST
  -> PostgreSQL

Spring Boot
  -> Mock/Sandbox Payment Adapter
  -> Mock/Sandbox Shipping Adapter
  -> Private Media Adapter
```

WebSocket được bổ sung sau khi REST chat/offer ổn định. Redis và outbox worker chỉ thuộc giai đoạn scale sau core acceptance gate; chúng không phải dependency của luồng nghiệp vụ cơ bản.

## Core invariants

- Một listing đại diện cho một món hàng trong MVP.
- Một Order thuộc một Seller.
- Reservation chỉ được tạo khi checkout.
- Tổng tiền được tính ở Backend.
- Payment callback, release và refund phải idempotent.
- Order, Payment và Complaint phải chuyển trạng thái nhất quán.
- Evidence và KYC không được công khai trực tiếp.
- Hành động quản trị ảnh hưởng quyền, nội dung hoặc tiền phải được audit.
