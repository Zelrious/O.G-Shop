# Handoff Summary – Trust-oriented Second-hand C2C Marketplace

## 1. Bối cảnh project

Tên đề tài:

> **Nền tảng mua bán đồ Second-hand đáng tin cậy**

Đây là đồ án môn học, core system là một **C2C second-hand marketplace**, trong đó người dùng cá nhân có thể vừa mua vừa bán sản phẩm đã qua sử dụng.

Điểm khác biệt chính của hệ thống không phải chỉ là “website mua bán đồ cũ”, mà là:

> **Trust-oriented marketplace** — tập trung vào minh bạch thông tin sản phẩm, xác thực người bán, bảo vệ giao dịch, đánh giá uy tín và giải quyết tranh chấp.

AI **chưa phải core requirement**. Các chức năng AI chỉ được xem là hướng mở rộng sau khi hệ thống cơ bản hoạt động ổn định.

### Core flow

```text
User
 ↓
Seller Verification
 ↓
Product Listing
 ↓
Search / View Product
 ↓
Chat / Offer
 ↓
Checkout
 ↓
Payment Held
 ↓
Seller Confirm
 ↓
Shipment
 ↓
Buyer Receives
 ↓
Inspection
 ├── Confirm OK → Release Payment → Completed → Review
 └── Report Problem → Complaint → Freeze Payment
                                  ↓
                             Admin Review
                             ↙          ↘
                        Refund         Release
```

---

## 2. Các role trong hệ thống

Có 3 role chính:

```text
BUYER
SELLER
ADMIN
```

### User model

Không tạo riêng `BuyerAccount` và `SellerAccount`.

Một `User` có thể:

```text
User
 ├── BUYER
 └── SELLER
```

Người dùng bình thường có thể mua hàng.

Muốn bán hàng:

```text
User
 ↓
Seller Verification
 ↓
VERIFIED
 ↓
SELLER permission
```

Admin không đăng ký như user bình thường; tài khoản Admin do hệ thống tạo/cấp quyền.

---

## 3. Các quyết định nghiệp vụ đã chốt

### Seller Verification

Seller phải xác minh trước khi đăng sản phẩm.

MVP:

```text
PENDING
VERIFIED
REJECTED
```

KYC được **Admin duyệt thủ công**.

OCR CCCD / Face Recognition chỉ là AI extension.

### Product

Sản phẩm là đồ second-hand, vì vậy thông tin condition rất quan trọng.

Product cần lưu:

```text
Condition
UsageDuration
Defects
RepairHistory
IncludedAccessories
```

Mặc định một listing đại diện cho **một món hàng cụ thể**.

Vì vậy:

```text
quantity ≈ 1
```

cho phần lớn sản phẩm.

### Order

Để giảm độ phức tạp:

> **Một Order chỉ thuộc một Seller.**

Không triển khai multi-seller order trong phiên bản đầu.

Nếu Cart chứa sản phẩm từ nhiều Seller thì khi checkout:

```text
Cart
 ↓
group by Seller
 ↓
Order Seller A
Order Seller B
Order Seller C
```

hoặc restrict Cart về một Seller.

**Khuyến nghị:** split thành nhiều Order theo Seller.

### Payment + Escrow + Refund

Không tạo ba bảng:

```text
Payment
Escrow
Refund
```

Mà gộp thành:

```text
Payment
```

Payment Status:

```text
PENDING
PAID
HELD
RELEASED
REFUND_PENDING
REFUNDED
FAILED
```

Escrow trong project là **mô phỏng logic giữ tiền**, không phải hệ thống tài chính giữ tiền thật.

Flow:

```text
Buyer pays
 ↓
HELD
 ↓
Shipment
 ↓
Buyer confirms
 ↓
RELEASED
```

Có tranh chấp:

```text
HELD
 ↓
Complaint
 ↓
payment frozen
 ↓
Admin
 ├── REFUNDED
 └── RELEASED
```

### Shipment

Không tự xây logistics.

Dùng:

```text
mock shipment
```

hoặc API bên thứ ba.

Không cần `ShipmentStatusHistory` trong MVP.

### Complaint

Đã gộp:

```text
Complaint
+
ComplaintEvidence
+
DisputeResolution
```

thành:

```text
Complaint
```

Evidence có thể dùng PostgreSQL `JSONB`.

Ví dụ:

```json
{
  "images": [
    "https://..."
  ],
  "videos": [
    "https://..."
  ]
}
```

Complaint chỉ dùng cho **tranh chấp liên quan đến Order**.

### Report

`Report` khác `Complaint`.

```text
Complaint
→ vấn đề trong giao dịch

Report
→ user/product/content vi phạm
```

Ví dụ Report:

```text
Fake listing
Spam
Harassment
Suspicious account
Prohibited content
```

### Reputation

Không tạo bảng `Reputation`.

Reputation được suy ra từ:

```text
Review
+
Completed Order
+
Complaint
```

Ví dụ:

```text
AverageRating
CompletedTransactions
TotalReviews
```

có thể query hoặc cache sau.

### Product media

Đã gộp:

```text
ProductImage
ProductVideo
```

thành:

```text
ProductMedia
```

với:

```text
MediaType = IMAGE | VIDEO
```

---

## 4. Database hiện tại: 22 bảng

```text
1.  User
2.  Role
3.  UserRole
4.  Address
5.  SellerVerification

6.  Category
7.  Product
8.  ProductMedia

9.  Conversation
10. Message
11. Offer

12. Cart
13. CartItem

14. Order
15. OrderItem
16. Payment
17. Shipment

18. Review
19. Complaint
20. Report

21. Notification
22. AuditLog
```

Đây là **logical model hiện tại**, có thể tiếp tục chỉnh trước khi tạo migration thật.

---

## 5. Chi tiết database

### `User`

Lưu tài khoản và hồ sơ người dùng.

```text
UserId             PK
Email              UNIQUE NOT NULL
PasswordHash       NOT NULL
FullName           NOT NULL
PhoneNumber        UNIQUE
AvatarUrl          NULL
Status             NOT NULL
CreatedAt          NOT NULL
UpdatedAt          NULL
```

Không lưu password plaintext.

---

### `Role`

```text
RoleId             PK
RoleName           UNIQUE NOT NULL
Description        NULL
```

Dữ liệu ban đầu:

```text
BUYER
SELLER
ADMIN
```

---

### `UserRole`

Many-to-many User ↔ Role.

```text
UserId             PK, FK
RoleId             PK, FK
```

Foreign keys:

```text
UserRole.UserId
→ User.UserId

UserRole.RoleId
→ Role.RoleId
```

Có thể simplification sau này nếu project quyết định mỗi user chỉ có một role, nhưng **hiện tại giữ bảng này** vì một user có thể vừa Buyer vừa Seller.

---

### `Address`

Cho phép User có nhiều địa chỉ.

```text
AddressId          PK
UserId             FK
RecipientName      NOT NULL
PhoneNumber        NOT NULL
Province           NOT NULL
District           NOT NULL
Ward               NOT NULL
DetailAddress      NOT NULL
IsDefault          NOT NULL DEFAULT false
```

FK:

```text
Address.UserId
→ User.UserId
```

---

### `SellerVerification`

```text
VerificationId     PK
UserId             FK
VerificationMethod
Status
SubmittedAt
VerifiedAt
VerifiedBy         FK → User
```

Foreign keys:

```text
SellerVerification.UserId
→ User.UserId

SellerVerification.VerifiedBy
→ User.UserId
```

`VerifiedBy` là Admin.

Status:

```text
PENDING
VERIFIED
REJECTED
```

---

### `Category`

Hỗ trợ category tree.

```text
CategoryId         PK
ParentCategoryId   FK NULL
CategoryName       NOT NULL
Description        NULL
```

FK self-reference:

```text
Category.ParentCategoryId
→ Category.CategoryId
```

---

### `Product`

Entity trung tâm của marketplace.

```text
ProductId              PK
SellerId               FK
CategoryId             FK

Title                   NOT NULL
Description             NOT NULL
Price                   NOT NULL

Condition               NOT NULL
UsageDuration           NULL
Defects                 NULL
RepairHistory           NULL
IncludedAccessories     NULL

Location                NULL
Status                  NOT NULL

CreatedAt               NOT NULL
UpdatedAt               NULL
```

FK:

```text
Product.SellerId
→ User.UserId

Product.CategoryId
→ Category.CategoryId
```

Suggested statuses:

```text
DRAFT
PENDING
ACTIVE
RESERVED
SOLD
HIDDEN
REJECTED
```

Suggested condition:

```text
LIKE_NEW
GOOD
FAIR
POOR
FOR_PARTS
```

Có thể điều chỉnh enum sau.

---

### `ProductMedia`

Ảnh và video dùng chung một bảng.

```text
MediaId            PK
ProductId          FK
MediaType          NOT NULL
MediaUrl           NOT NULL
DisplayOrder
CreatedAt
```

FK:

```text
ProductMedia.ProductId
→ Product.ProductId
```

MediaType:

```text
IMAGE
VIDEO
```

---

### `Conversation`

Một conversation gắn với một sản phẩm.

```text
ConversationId     PK
BuyerId            FK
SellerId           FK
ProductId          FK
CreatedAt
```

FK:

```text
BuyerId
→ User.UserId

SellerId
→ User.UserId

ProductId
→ Product.ProductId
```

Nên cân nhắc constraint:

```text
UNIQUE(BuyerId, SellerId, ProductId)
```

để tránh tạo nhiều conversation trùng cho cùng một sản phẩm.

---

### `Message`

```text
MessageId          PK
ConversationId     FK
SenderId           FK
Content
MessageType
CreatedAt
ReadAt
```

FK:

```text
Message.ConversationId
→ Conversation.ConversationId

Message.SenderId
→ User.UserId
```

MessageType có thể:

```text
TEXT
IMAGE
SYSTEM
```

---

### `Offer`

Lưu việc trả giá.

```text
OfferId            PK
ProductId          FK
BuyerId            FK
SellerId           FK
OfferPrice         NOT NULL
Status             NOT NULL
CreatedAt
RespondedAt
```

FK:

```text
ProductId
→ Product.ProductId

BuyerId
→ User.UserId

SellerId
→ User.UserId
```

Status:

```text
PENDING
ACCEPTED
REJECTED
COUNTERED
EXPIRED
```

Có thể thêm:

```text
ParentOfferId
```

sau này nếu muốn theo dõi counter-offer dạng chain.

MVP chưa cần.

---

### `Cart`

```text
CartId             PK
UserId             FK UNIQUE
UpdatedAt
```

FK:

```text
Cart.UserId
→ User.UserId
```

Một User có một Cart active.

---

### `CartItem`

```text
CartId             PK, FK
ProductId          PK, FK
Quantity
```

FK:

```text
CartItem.CartId
→ Cart.CartId

CartItem.ProductId
→ Product.ProductId
```

#### Lưu ý quan trọng

Vì hàng second-hand thường unique:

```text
Quantity = 1
```

Có thể:

- bỏ `Quantity`, hoặc
- giữ nhưng CHECK `Quantity = 1`.

Nếu project có danh mục hàng second-hand có nhiều quantity thì giữ.

---

### `Order`

```text
OrderId             PK
BuyerId             FK
SellerId            FK
ShippingAddressId   FK

TotalAmount
ShippingFee

Status
CreatedAt
UpdatedAt
```

FK:

```text
Order.BuyerId
→ User.UserId

Order.SellerId
→ User.UserId

Order.ShippingAddressId
→ Address.AddressId
```

Suggested states:

```text
PAYMENT_PENDING
PAID_HELD
SELLER_CONFIRMED
SHIPPED
DELIVERED
COMPLETED

CANCELLED
DISPUTED
REFUNDED
```

---

### `OrderItem`

```text
OrderItemId         PK
OrderId             FK
ProductId           FK
Quantity
UnitPrice
```

FK:

```text
OrderItem.OrderId
→ Order.OrderId

OrderItem.ProductId
→ Product.ProductId
```

`UnitPrice` phải lưu snapshot giá tại thời điểm mua.

Không lấy lại `Product.Price` khi xem lịch sử Order.

---

### `Payment`

Đã gộp:

```text
Payment
+
Escrow
+
Refund
```

Schema:

```text
PaymentId           PK
OrderId             FK

Amount              NOT NULL
PaymentMethod       NOT NULL
TransactionCode     UNIQUE

Status              NOT NULL

HeldAt
ReleasedAt

RefundAmount
RefundReason
RefundedAt

CreatedAt
UpdatedAt
```

FK:

```text
Payment.OrderId
→ Order.OrderId
```

Payment status:

```text
PENDING
PAID
HELD
RELEASED
REFUND_PENDING
REFUNDED
FAILED
```

#### Quyết định MVP

Nên dùng:

```text
Order 1 ── 1 Payment
```

→ `Payment.OrderId UNIQUE`

Nếu sau này cần payment retries/history:

```text
Order 1 ── N PaymentAttempt
```

nhưng chưa cần trong MVP.

---

### `Shipment`

```text
ShipmentId          PK
OrderId             FK UNIQUE

Carrier
TrackingNumber      UNIQUE
Status

ShippedAt
DeliveredAt
UpdatedAt
```

FK:

```text
Shipment.OrderId
→ Order.OrderId
```

Suggested states:

```text
PENDING
PICKED_UP
IN_TRANSIT
DELIVERED
RETURNED
FAILED
```

---

### `Review`

```text
ReviewId            PK
OrderId             FK
ReviewerId          FK
RevieweeId          FK

Rating
Comment
CreatedAt
```

FK:

```text
OrderId
→ Order.OrderId

ReviewerId
→ User.UserId

RevieweeId
→ User.UserId
```

Constraint:

```text
CHECK Rating BETWEEN 1 AND 5
```

và:

```text
UNIQUE(
  OrderId,
  ReviewerId,
  RevieweeId
)
```

Chỉ cho phép review khi:

```text
Order.Status = COMPLETED
```

---

### `Complaint`

Gộp cả complaint, evidence và resolution.

```text
ComplaintId         PK
OrderId             FK
CreatedBy           FK

Reason
Description

Evidence            JSONB

Status
Resolution

ResolvedBy          FK
RefundAmount

CreatedAt
ResolvedAt
```

FK:

```text
Complaint.OrderId
→ Order.OrderId

Complaint.CreatedBy
→ User.UserId

Complaint.ResolvedBy
→ User.UserId
```

Status:

```text
OPEN
REVIEWING
RESOLVED
REJECTED
```

Resolution:

```text
REFUND_BUYER
RELEASE_TO_SELLER
REJECT_COMPLAINT
```

Optional later:

```text
PARTIAL_REFUND
```

Evidence JSONB example:

```json
{
  "images": [
    "url1",
    "url2"
  ],
  "videos": [
    "url3"
  ]
}
```

---

### `Report`

Dùng cho vi phạm ngoài transaction dispute.

```text
ReportId             PK
ReporterId           FK

ReportedUserId       FK NULL
ProductId            FK NULL

Reason
Description
Status

CreatedAt
ResolvedAt
```

FK:

```text
ReporterId
→ User.UserId

ReportedUserId
→ User.UserId

ProductId
→ Product.ProductId
```

Cần đảm bảo ít nhất một trong:

```text
ReportedUserId
ProductId
```

không NULL.

Sau này có thể đổi thành generic:

```text
TargetType
TargetId
```

nếu cần report message/review.

---

### `Notification`

```text
NotificationId      PK
UserId              FK

Type
Title
Content

IsRead
CreatedAt
```

FK:

```text
Notification.UserId
→ User.UserId
```

---

### `AuditLog`

Ghi những thao tác quan trọng.

```text
LogId               PK
UserId              FK NULL

Action
EntityType
EntityId

IpAddress
CreatedAt
```

FK:

```text
AuditLog.UserId
→ User.UserId
```

Nên log:

```text
LOGIN
ACCOUNT_LOCK
KYC_APPROVE
KYC_REJECT

PRODUCT_HIDE

PAYMENT_HELD
PAYMENT_RELEASED
REFUND

COMPLAINT_RESOLVED
```

---

## 6. Quan hệ high-level

```text
User
 ├── UserRole ───────── Role
 ├── Address
 ├── SellerVerification
 ├── Product
 │     └── ProductMedia
 │
 ├── Conversation
 │     └── Message
 │
 ├── Offer
 │
 ├── Cart
 │     └── CartItem ─── Product
 │
 ├── Order
 │     ├── OrderItem ── Product
 │     ├── Payment
 │     ├── Shipment
 │     ├── Review
 │     └── Complaint
 │
 ├── Report
 ├── Notification
 └── AuditLog
```

---

## 7. Các quan hệ cardinality chính

```text
User 1 ─── N Address

User 1 ─── N Product

Category 1 ─── N Product

Product 1 ─── N ProductMedia

Product 1 ─── N Conversation

Conversation 1 ─── N Message

Product 1 ─── N Offer

User 1 ─── 1 Cart
Cart 1 ─── N CartItem

Order 1 ─── N OrderItem

Order 1 ─── 1 Payment

Order 1 ─── 0..1 Shipment

Order 1 ─── 0..N Review

Order 1 ─── 0..N Complaint
```

Có thể giới hạn:

```text
Order 1 ─── 0..1 active Complaint
```

---

## 8. Điểm cần Codex đặc biệt kiểm tra trước khi generate database

Đây là những điểm **chưa nên hard-code mù quáng**.

### A. Shipping Address history

Hiện tại:

```text
Order.ShippingAddressId
→ Address
```

Có vấn đề nếu User sửa/xóa Address sau khi Order hoàn thành.

Ví dụ:

```text
Order #123
→ Address A
```

sau đó User sửa Address A thành địa chỉ mới.

Order lịch sử sẽ hiển thị sai địa chỉ giao hàng cũ.

#### Khuyến nghị

Khi tạo Order, lưu snapshot:

```text
ShippingRecipientName
ShippingPhoneNumber
ShippingProvince
ShippingDistrict
ShippingWard
ShippingDetailAddress
```

trong `Order`.

Có thể vẫn giữ:

```text
ShippingAddressId
```

để biết nguồn ban đầu.

Đây là thay đổi **nên làm trước khi tạo schema final**.

---

### B. Cart và multi-seller

Một Order chỉ có một Seller.

Nhưng Cart có thể chứa:

```text
Product Seller A
Product Seller B
```

Khi checkout phải:

```text
group CartItems by SellerId
```

và tạo:

```text
Order A
Order B
```

Không tạo một Order chứa nhiều Seller.

---

### C. Quantity

Đồ second-hand thường unique.

Nên cân nhắc bỏ:

```text
CartItem.Quantity
OrderItem.Quantity
```

hoặc enforce:

```text
Quantity = 1
```

Khuyến nghị hiện tại: **giữ Quantity**, vì schema linh hoạt hơn nếu sau này Seller có nhiều món tương tự.

---

### D. Product reservation concurrency

Khi hai Buyer cùng checkout một Product:

```text
Buyer A → Checkout
Buyer B → Checkout
```

không được tạo hai giao dịch thành công.

Cần thiết kế concurrency control.

Ví dụ:

```text
Product.Status:
ACTIVE → RESERVED → SOLD
```

Khi tạo Order:

- lock Product row;
- kiểm tra `ACTIVE`;
- đổi thành `RESERVED`.

Nếu payment fail/timeout:

```text
RESERVED → ACTIVE
```

Nếu transaction thành công:

```text
RESERVED → SOLD
```

Đây là một trong những business rules quan trọng nhất.

---

### E. Order + Payment consistency

Không được xảy ra:

```text
Payment = RELEASED
Order = PAYMENT_PENDING
```

hoặc:

```text
Payment = REFUNDED
Order = COMPLETED
```

mà không có nghiệp vụ hợp lệ.

Các transition quan trọng phải chạy trong database transaction.

---

### F. Delete strategy

Không hard-delete các entity có lịch sử giao dịch.

Không xóa thật:

```text
User
Product
Order
Payment
Review
Complaint
```

Nên dùng:

```text
Status
DeletedAt
```

hoặc soft delete khi phù hợp.

Đặc biệt Order/Payment không được mất lịch sử.

---

## 9. Những thứ chưa cần triển khai

Không đưa vào database core lúc này:

```text
OCRResult
FaceRecognition
FraudScore
SemanticEmbedding
Recommendation
PricePrediction
SentimentAnalysis
RewardWallet
Voucher
Promotion
```

Nếu AI được duyệt sau, thiết kế module riêng.

---

## 10. Tech stack dự kiến

### Backend

```text
Java
Spring Boot
Spring Security
Spring Data JPA / Hibernate
Maven
```

### Database

```text
PostgreSQL
```

### Frontend

```text
React
TypeScript
```

### Realtime

```text
WebSocket
```

### Media

```text
Cloudinary / ImageKit / S3 compatible
```

### Development

```text
Docker
Docker Compose
Git
GitHub
```

---

## 11. Kiến trúc backend đề xuất

Không dùng microservices ngay từ đầu.

Dùng:

> **Modular Monolith**

Suggested modules:

```text
auth
user
verification

category
product

chat
offer

cart
order
payment
shipment

review
complaint
report

notification
admin
audit
```

Package flow:

```text
Controller
   ↓
Service
   ↓
Repository
   ↓
PostgreSQL
```

Không để Controller gọi Repository trực tiếp.

---

## 12. Prompt ngắn để giao cho Codex

```text
Tôi đang xây dựng backend/database cho một đồ án Spring Boot tên Trust-oriented Second-hand C2C Marketplace.

Core system gồm User/Role, Seller Verification, Product Listing, Product Condition, Product Media, Search, Chat, Offer, Cart, Order, Payment với simulated escrow/refund, Shipment, Review, Complaint/Dispute, Report, Notification và Audit Log.

Database sử dụng PostgreSQL và hiện có 22 logical tables:

User, Role, UserRole, Address, SellerVerification, Category, Product, ProductMedia, Conversation, Message, Offer, Cart, CartItem, Order, OrderItem, Payment, Shipment, Review, Complaint, Report, Notification, AuditLog.

Các quyết định quan trọng:

- Một User có thể vừa Buyer vừa Seller thông qua Role/UserRole.
- Seller phải VERIFIED trước khi đăng bán.
- Một Product listing đại diện chủ yếu cho một món second-hand riêng biệt.
- Một Order chỉ thuộc một Seller.
- Nếu Cart chứa nhiều Seller, checkout phải split thành nhiều Order.
- ProductImage và ProductVideo đã gộp thành ProductMedia.
- Payment, Escrow và Refund đã gộp thành Payment.
- Payment states: PENDING, PAID, HELD, RELEASED, REFUND_PENDING, REFUNDED, FAILED.
- ComplaintEvidence và DisputeResolution đã gộp vào Complaint; evidence dùng JSONB.
- Không có bảng Reputation; reputation được tính từ Review + completed transactions.
- Shipment history chưa cần trong MVP.
- AI chưa nằm trong core database.

Trước khi generate Entity/Migration, hãy review schema và business invariants. Đặc biệt cần xử lý:

1. Snapshot shipping address trong Order để lịch sử không thay đổi khi User sửa Address.
2. Concurrency khi hai Buyer cùng mua một Product (ACTIVE → RESERVED → SOLD).
3. Transaction consistency giữa Order và Payment.
4. Không hard-delete transaction history.
5. Index cho các FK và query phổ biến.
6. Kiểm tra constraint/unique/check phù hợp.

Không tự ý thêm microservices, blockchain, AI table hoặc các bảng không phục vụ core nghiệp vụ. Khi thấy schema cần thay đổi, trước tiên giải thích vấn đề, đề xuất phương án và ảnh hưởng đến các entity liên quan rồi mới sửa.
```
