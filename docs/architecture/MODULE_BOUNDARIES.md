# Module Boundaries

| Module | Sở hữu | Không sở hữu |
|---|---|---|
| Identity | User, role, session, seller verification | Product, Order, moderation case |
| Catalog | Category, Product, ProductMedia | Cart, Order, payment |
| Communication | Conversation, Message, Offer | Reservation, payment decision |
| Commerce | Cart, checkout, reservation, Order | Payment provider, Shipment tracking |
| Payment | Payment, release, refund command | Order item, shipment lifecycle |
| Fulfillment | Shipment, inspection deadline, return | Payment resolution policy |
| Trust & Safety | Complaint, dispute, Review, Report | Authentication/session |
| Platform | Notification, Admin read model, AuditLog | Business state ownership |

## Dependency rules

- `shared` không tham chiếu module nghiệp vụ.
- Module gọi application facade của module khác; không gọi repository trực tiếp.
- Cross-module write quan trọng phải nằm trong một application transaction hoặc domain-event workflow có idempotency.
- Platform dashboard chỉ đọc projection/aggregate; không sửa business state trực tiếp.
- Adapter bên ngoài được đặt sau interface do module sở hữu.
