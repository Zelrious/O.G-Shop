# Fulfillment Module

- Status: PLANNED
- Requirements: UC-10, UC-11, phần return của UC-14
- Completion: 0/3 use cases verified
- Last reviewed: 2026-09-17

## Purpose and scope

Quản lý shipment, tracking mock/sandbox, giao hàng, inspection deadline và return shipment.

## Out of scope

Quyết định tranh chấp và lệnh chuyển tiền cuối cùng.

## Owned paths

- `backend/src/main/java/com/oldbutgold/shop/modules/fulfillment/`
- `frontend/src/features/orders/` và `frontend/src/features/disputes/` khi được tạo
- Flyway tables Shipment và return record

## Dependencies

- Commerce, Payment, Trust & Safety và Platform.

## Security invariants

- Shipment callback idempotent.
- Inspection deadline chỉ được thiết lập một lần theo policy.
- Return có record riêng, không ghi đè outbound shipment.
- Địa chỉ giao hàng dùng snapshot của Order.

## Completed tasks

- [x] Tạo package boundary và tài liệu module.

## Remaining tasks

- [ ] Chốt inspection deadline và carrier adapter.
- [ ] Thiết kế outbound/return model.
- [ ] Triển khai UC-10, UC-11 và return flow.
- [ ] Kiểm thử callback lặp và auto-complete race.

## Expected changes

Shipping integration sẽ bắt đầu bằng deterministic fake adapter để kiểm thử state transition.
