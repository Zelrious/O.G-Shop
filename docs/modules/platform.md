# Platform Module

- Status: PLANNED
- Requirements: UC-18, UC-19
- Completion: 0/2 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Cung cấp notification, audit log và read model cho dashboard quản trị.

## Out of scope

Sở hữu hoặc sửa trực tiếp trạng thái nghiệp vụ của module khác.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/platform/`
- `frontend/src/features/admin/` khi được tạo
- Flyway tables Notification, AuditLog và projection nếu được phê duyệt

## Dependencies

Đọc domain event hoặc query đã công bố từ các module nghiệp vụ.

## Security invariants

- Audit là append-only qua API nghiệp vụ.
- Audit không chứa secret hoặc raw evidence.
- Dashboard chỉ đọc aggregate/projection.
- Notification không phải nguồn sự thật cho trạng thái giao dịch.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.

## Remaining tasks

- [ ] Chốt event publication pattern.
- [ ] Thiết kế audit schema và retention.
- [ ] Triển khai notification và dashboard read model.
- [ ] Kiểm thử permission và dữ liệu nhạy cảm.

## Expected changes

Platform sẽ được triển khai sau khi event/command của các core module ổn định.
