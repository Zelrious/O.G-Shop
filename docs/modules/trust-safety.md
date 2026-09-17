# Trust & Safety Module

- Status: PLANNED
- Requirements: UC-12, UC-13, UC-15, UC-16, UC-17
- Completion: 0/5 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý Complaint, phản hồi hai bên, evidence, dispute resolution, Review, Report, moderation và hạn chế tài khoản.

## Out of scope

Authentication implementation, payment provider và shipment provider.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/trustsafety/`
- `frontend/src/features/disputes/`, `reputation/`, `admin/` khi được tạo
- Flyway tables Complaint, Review, Report và evidence metadata

## Dependencies

- Identity, Catalog, Commerce, Payment, Fulfillment và Platform.

## Security invariants

- Complaint khác Report.
- Một Order chỉ có một Complaint active.
- Evidence gốc không bị Admin sửa.
- Resolution luôn có actor, reason và audit.
- Restrict account không tự hủy/refund/release giao dịch đang mở.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.

## Remaining tasks

- [ ] Chốt evidence model và private storage.
- [ ] Chốt resolution command contract.
- [ ] Triển khai Complaint/dispute/review/report/moderation.
- [ ] Kiểm thử IDOR, double resolution và evidence privacy.

## Expected changes

Các command ảnh hưởng tiền sẽ phối hợp Payment qua facade có version/state guard.
