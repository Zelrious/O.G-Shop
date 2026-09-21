# TASK-0018 — Marketplace UI/UX Design System & Figma Prototype

- Status: IN_PROGRESS
- Started: 2026-09-20
- Owner: O.G Shop
- Deliverable: `O.G Shop — Marketplace UI UX v1` trên Figma
- Scope: Web desktop/mobile, Light/Dark, Vietnamese/English

## Mục tiêu

Thiết kế lại trải nghiệm O.G Shop thành marketplace đồ cũ đáng tin cậy, dễ kiểm thử trực quan trước khi tiếp tục phát triển Frontend. Figma là nguồn duyệt UI/UX cho các task chức năng tiếp theo.

## Phạm vi

- Design foundations: color, typography, spacing, radius, elevation, grid, responsive rules, accessibility.
- Component library cho marketplace, auth, form, chat, order, shipping, dispute và admin.
- Sitemap và screen flow theo Guest, Buyer, Seller và Admin.
- Màn hình có mã định danh riêng; gồm desktop/mobile và các trạng thái loading, empty, error, dialog/drawer/toast liên quan.
- Theme Light/Dark không chói; nội dung Vietnamese/English; dữ liệu mẫu hư cấu để kiểm thử.
- Clickable prototype cho các luồng cốt lõi Buyer, Seller, Admin và Auth/KYC.

## Nguyên tắc phạm vi

- Thiết kế toàn bộ màn hình canonical ở Light + Vietnamese cho desktop/mobile.
- Kiểm chứng Dark + English trên các màn hình đại diện và dùng variable modes để toàn bộ hệ thống có thể chuyển theme/locale.
- eKYC production vẫn deferred; thiết kế đầy đủ trạng thái để sử dụng sau, còn hành vi MVP hiện tại tiếp tục activation bypass trung thực.
- Không sửa Frontend trong task này; chỉ bắt đầu implement sau khi thiết kế được duyệt.

## Acceptance gate

1. Có inventory màn hình và flow map theo role.
2. Foundations và component library dùng variable/token, không hardcode rời rạc.
3. Có prototype desktop/mobile với dữ liệu mẫu.
4. Có kiểm tra Light/Dark, VI/EN, contrast, focus và touch target.
5. Người dùng duyệt trực quan trước khi TASK-0014 được tiếp tục.
