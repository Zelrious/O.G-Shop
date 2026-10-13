# Chat, offer và quan hệ chặn

Base `/api/v1/chat`; Bearer/cookie của Buyer/Seller. Server lấy actor từ JWT, kiểm role thực và ownership; KTV/Admin không vào chat khách hàng. ID ngoài phạm vi trả404. API không trả entity hoặc URL media riêng tư.

| Method / path | Hành vi |
|---|---|
| GET `/conversations?query=&page=0&size=20` | Hộp thư của actor, tìm theo tên món đồ, phân trang và unread |
| POST `/conversations` | `{productId,orderId?}`; một conversation/product/buyer/seller, xác minh đơn thuộc actor nếu có |
| GET `/conversations/{id}?before=&size=50` | Product context hiện tại, messages cursor, offers, quota, quyền canSend/canOffer và lý do |
| POST `/conversations/{id}/messages` | `{clientId:UUID,text,replyTo?}`; tối đa2000 ký tự, retry cùng nội dung trả cùng message |
| POST `/conversations/{id}/read` | `{messageId}`; chỉ message trong thread, cursor không giảm, trả204 |
| POST `/conversations/{id}/media` | Multipart clientId/file/caption; JPEG/PNG/WebP hoặc MP4; recheck quyền/quota sau provider I/O |
| GET `/messages/{id}/media` | Bytes xác thực participant, no-store/nosniff;404 ngoài phạm vi,410 hết hạn,503 provider chưa sẵn sàng |
| POST `/conversations/{id}/offers` | `{clientId,price,expiresAt?}`; Buyer gửi đầu tiên, giá nguyên dương cho1 món |
| POST `/offers/{id}/decision` | `{commandKey,action,expectedVersion,price?,expiresAt?}`; ACCEPT/REJECT/COUNTER/WITHDRAW |
| GET `/blocks` | Danh sách người mua đang bị seller hiện tại chặn |
| GET `/blocks/search?query=` | Tên/mã customer, ít nhất2 ký tự; không trả email/staff |
| POST `/blocks` | `{buyerId,blocked,reason?}`; seller-only, reason≤500, ghi audit khi thay đổi |

Offer trả trạng thái, chain parentId, snapshot/version, fee, orderId, allowedActions và canCheckout. UI chỉ hiển thị hành động server cho phép. Một PENDING/thread; counter đóng row cũ và tạo row con trong cùng transaction. Pending có thể hết hạn; để trống expiresAt không áp hạn tự đặt. Hạn này là hạn phản hồi, không tự hủy giá đã ACCEPTED. Accept không giữ hàng hoặc đổi giá niêm yết. Snapshot mới đúng buyer/product/version/public revision; một offer dùng cho một order. Giá legacy chưa có snapshot được giữ để đọc, cần đề nghị mới để checkout.

Checkout hiện hành: GET `/api/v1/commerce/checkout/preview?productId=&offerId=&voucherCode=` và POST `/api/v1/commerce/checkout/buy-now` thêm `offerId?`. Preview giữ listedPrice và trả agreedPrice/acceptedOfferId. Retry cùng payload sau thành công trả cùng order; khác payload hoặc order đã CANCELLED/REFUNDED trả409. Không nhận giá/phí do client gửi. Voucher tính trên giá thỏa thuận; lưu listed/agreed/fee snapshot và liên kết conversation-order. Offer/order/outbox/inventory cùng transaction.

SELLER_BUYER_BLOCKED ngăn tương tác và mua mới, vẫn cho chat khi có đơn/hồ sơ còn mở của món đồ đó. Hạn chế CHAT của KTV luôn chặn gửi trực tiếp; giữ login, lịch sử, cursor và quyền xử lý hồ sơ chính thức. BUY_ALL không cấm chat. CHAT đơn thuần không vô hiệu hóa giá đã accepted để mua nếu quyền mua còn hợp lệ.

Media: mỗi participant/thread/ngày Asia/Saigon tối đa5 ảnh≤5MB,1 video≤20MB/30s, tổng45MB. Lưu7 ngày; giữ khi có nghĩa vụ/hồ sơ còn mở, grace7 ngày sau kết thúc. Cleanup3AM chỉ quản lý assets CHAT_V31. Legacy/unmanaged không lộ URL cũ và không bị worker tự xóa. Provider I/O ngoài transaction; quota và permission kiểm lại dưới locks. Blob phía UI được thu hồi khi bỏ attachment hoặc đổi actor. Cloudinary dùng authenticated; chưa cấu hình thì local private storage.

Lỗi tiêu biểu: OFFER_COMMAND_CONFLICT/OFFER_VERSION_CONFLICT/OFFER_STATE_CONFLICT/OFFER_LISTING_CHANGED/OFFER_ALREADY_USED/OFFER_CHECKOUT_CONFLICT (409); CHAT_CLIENT_ID_CONFLICT (409); CHAT_MEDIA_QUOTA (429); ACCOUNT_ACTION_RESTRICTED/SELLER_BUYER_BLOCKED (403). UI giữ draft và command key cho retry, đọc lại state khi conflict. Poll list5s/detail2.5s khi tab hiển thị, reconnect khi online; PostgreSQL/outbox là nguồn lịch sử.

[Thiết kế và rollout](../architecture/CHAT_OFFER_V31_20261013.md) · [Kiểm chứng và hướng dẫn test](../../output/phase34-runtime/README.md).
