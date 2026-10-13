# Notifications API — đợt 1–2

Base `/api/v1`; xác thực qua cơ chế JWT/Bearer hoặc cookie hiện hành. Danh tính lấy từ principal, client không chọn userId. [Luồng/schema](../architecture/MODERATION_LISTING_V27_20261012.md).

## Danh sách

`GET /notifications?page=0&size=20` trả HTTP 200:

```json
{
  "items": [{
    "id": 12, "type": "LISTING_REVIEWED", "title": "Kết quả kiểm duyệt tin đăng",
    "content": "Tin đã được duyệt. Phí còn phải trả: 80.000 đồng. Thanh toán để mở bán.",
    "read": false, "createdAt": "2026-10-12T04:15:40Z", "readAt": null,
    "destination": "SELLER_LISTING", "productId": 1
  }],
  "page": 0, "size": 20, "totalElements": 1, "unreadCount": 1
}
```

page âm được đưa về 0; size giới hạn 1–50. Sắp createdAt/id giảm dần. Chỉ trả recipient hiện tại, unreadCount tính toàn bộ thông báo người đó. Timestamp ISO UTC. UI poll bell 15 giây khi tab hiển thị; delivery outbox có độ trễ riêng.

## Đã đọc

`POST /notifications/{id}/read`, không cần body; HTTP 204. Gọi lại giữ readAt đầu tiên. ID người khác/không tồn tại trả 404; chưa xác thực trả 401. Không có API client tự tạo/gửi thông báo ở đợt này.

## Sự kiện và đích

| Trigger | Type / người nhận | Đích |
|---|---|---|
| Gửi duyệt | LISTING_SUBMITTED / Seller; LISTING_PENDING / KTV active | SELLER_LISTING hoặc KTV_MODERATION |
| Duyệt/từ chối | LISTING_REVIEWED / Seller; MODERATION_CONFIRMED / reviewer | SELLER_LISTING hoặc KTV_MODERATION |
| Mock fee được ghi nhận và mở bán | LISTING_OPENED / Seller; nội dung chứa thưởng nếu cấp lần đầu | SELLER_LISTING |
| Quyết định account commit | ACCOUNT_REVIEWED / owner; ACCOUNT_CONFIRMED / KTV actor | ACCOUNT_SELF hoặc KTV_ACCOUNT |

Đích là enum/data, không nhận URL tự do: Seller có role SELLER mở `/seller/listings/{productId}/review`; KTV/ADMIN mở `/ktv/moderation?productId={id}`. API đích vẫn kiểm owner/role sau khi click. Thông báo cũ/đích không hỗ trợ vẫn xem/đánh dấu được, UI không tự đoán URL.

Account targets có `userId`, `productId=null`: ACCOUNT_SELF mở `/account/status`; KTV_ACCOUNT chỉ KTV mở `/ktv/users?userId={id}`. Bell customer mở `/notifications`, KTV `/ktv/notifications`, Admin `/admin/notifications` để giữ đúng shell. Owner/role vẫn kiểm tại API đích. ACCOUNT_REVIEWED chứa lý do/quyền nghĩa vụ cũ; ACCOUNT_CONFIRMED xác nhận cho KTV. [Account API](ACCOUNT_MANAGEMENT_API.md).

Business event và outbox cùng transaction; dispatcher transaction riêng ghi delivery và published_at, unique event/recipient chống trùng. Đợt 2 đọc cả ACCOUNT_NOTIFICATION và LISTING_NOTIFICATION, giữ payload listing cũ. Chat/offer/order reminders và thông báo tự động ngưỡng uy tín chưa nối. Tham chiếu UC44, phần UC51–UC53, UC59–UC60; chưa có dead-letter tự động.
