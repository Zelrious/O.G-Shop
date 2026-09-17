# Payment Module

- Status: PLANNED
- Requirements: UC-08, phần refund của UC-14
- Completion: 0/2 requirement groups verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý payment intent mô phỏng/sandbox, trạng thái giữ tiền, callback, release và full-order refund.

## Out of scope

Ví tiền thật, partial refund và đối soát production.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/payment/`
- Flyway tables/fields thuộc Payment
- Payment provider adapter

## Dependencies

- Commerce, Fulfillment, Trust & Safety và Platform.

## Security invariants

- Callback được xác minh và idempotent.
- Không đồng thời release và refund.
- Không release khi Complaint/return đang mở.
- Không tin total hoặc trạng thái do client gửi.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.

## Remaining tasks

- [ ] Chọn sandbox hoặc mock contract.
- [ ] Chốt Payment state machine.
- [ ] Triển khai callback/release/refund.
- [ ] Kiểm thử callback lặp, đến muộn và race condition.

## Expected changes

Sẽ ưu tiên interface provider và fake adapter xác định trước khi tích hợp sandbox ngoài.
