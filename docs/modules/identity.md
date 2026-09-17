# Identity Module

- Status: PLANNED
- Requirements: UC-01, UC-02
- Completion: 0/2 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý tài khoản, đăng nhập, phiên, role, địa chỉ hồ sơ và quy trình xác minh Seller.

## Out of scope

Product ownership, moderation decision, order authorization và KYC production.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/identity/`
- `frontend/src/features/auth/` khi được tạo
- Flyway tables liên quan User, Role, UserRole, Address, SellerVerification

## Dependencies

- Platform cho audit và notification.
- Trust & Safety khi Admin hạn chế tài khoản.

## Security invariants

- Không cấp ADMIN qua public API.
- Seller capability chỉ có hiệu lực sau verification hợp lệ.
- Password/token không xuất hiện trong log hoặc response.
- Khóa tài khoản không xóa lịch sử giao dịch.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.

## Remaining tasks

- [ ] Chốt authentication/session strategy.
- [ ] Thiết kế API và persistence.
- [ ] Triển khai UC-01 và UC-02.
- [ ] Kiểm thử role, ownership, session revocation và KYC privacy.

## Expected changes

DTO, controller, application service, security principal, repository, migration và giao diện auth sẽ được bổ sung theo từng task được phê duyệt.
