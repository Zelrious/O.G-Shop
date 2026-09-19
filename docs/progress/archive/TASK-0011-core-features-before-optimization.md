# TASK-0011 — Core features before infrastructure optimization

- Status: COMPLETE
- Approved: 2026-09-19
- Owner: O.G Shop
- Modules: Catalog, Communication, Commerce, Payment, Platform

## Mục tiêu

Khóa thứ tự triển khai: hoàn thiện và kiểm chứng các luồng nghiệp vụ cơ bản trên PostgreSQL trước khi thêm Redis, multi-instance realtime, outbox worker hoặc các tối ưu hạ tầng.

## Phạm vi

- Sắp xếp lại STATUS/BACKLOG theo core-flow-first.
- Ghi rõ acceptance gate trước giai đoạn tối ưu.
- Communication triển khai REST history/send và offer transaction trước WebSocket/Redis.
- Platform/outbox schema được giữ sẵn nhưng worker bị defer cho đến khi core command ổn định.

## Kiểm chứng

- Documentation validation.
- `git diff --check`.

## Kết quả

- Backlog và status ưu tiên UC-01 đến UC-14 trước external integration/realtime/optimization.
- Communication bắt buộc hoàn thiện REST chat/history và offer transaction trước WebSocket/Redis.
- Redis chỉ được đưa vào khi có số đo hiệu năng hoặc yêu cầu multi-instance; PostgreSQL vẫn là source of truth.
- Outbox schema được giữ để tránh migration lại, nhưng worker không phải dependency của core flow.
- Acceptance gate yêu cầu authorization, idempotency, concurrency và frontend end-to-end trước tối ưu.
