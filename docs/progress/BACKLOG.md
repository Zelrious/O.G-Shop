# Project Backlog

## Completed foundation

- [x] Tạo monorepo Backend, Frontend, Database, Reference, Docs và Infra.
- [x] Thiết lập rules, workflows và skills cho Codex, Claude Code và Gemini CLI.
- [x] Thiết lập Maven/npm lock, local quality scripts và GitHub Actions.
- [x] Kiểm tra Backend, Frontend, documentation, agent assets và Compose config.
- [x] Push baseline lên `origin/main` tại commit `40e3f8c`.

## P0 — Foundation

- [ ] Đưa tài liệu yêu cầu đã duyệt vào `reference/requirements/`.
- [ ] Đưa tài liệu database đã duyệt vào repository.
- [ ] Tạo `V1__initial_schema.sql` từ schema đã được xác nhận.
- [ ] Hoàn thiện `TRACEABILITY_MATRIX.md` theo UC, BR và NFR.
- [ ] Chốt authentication và session strategy.

## P1 — Core flow

- [ ] UC-01 Quản lý tài khoản và phiên.
- [ ] UC-02 Xác minh Seller.
- [ ] UC-03 Quản lý tin đăng.
- [ ] UC-04 Tìm kiếm và xem chi tiết.
- [ ] UC-05 Chat.
- [ ] UC-06 Trả giá.
- [ ] UC-07 Checkout và chống bán trùng.
- [ ] UC-08 Thanh toán giữ tiền mô phỏng.
- [ ] UC-09 đến UC-14: xử lý đơn, giao hàng, khiếu nại và hoàn tiền.

## P2 — Trust and administration

- [ ] UC-15 Đánh giá và uy tín.
- [ ] UC-16 Báo cáo và kiểm duyệt.
- [ ] UC-17 Quản lý user và quyền.
- [ ] UC-18 Dashboard quản trị.
- [ ] UC-19 Thông báo và audit.

## Deferred beyond MVP

- AI định giá hoặc gợi ý sản phẩm.
- Voucher, điểm thưởng và gamification.
- Hoàn tiền từng phần.
- Multi-round counteroffer phức tạp.
- Tích hợp KYC hoặc vận chuyển production.
