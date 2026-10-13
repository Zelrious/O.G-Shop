# Kiểm duyệt, phí đăng tin và thông báo — V27

TASK-0087, ngày 2026-10-12. Đợt 1 đã được chủ dự án duyệt. [API Catalog](../api/CATALOG_API.md), [API thông báo](../api/NOTIFICATIONS_API.md), [ảnh và bằng chứng](../../output/phase1-runtime/README.md).

## Trước và sau

Trước: KTV nhận DTO thiếu khuyết điểm/lịch sử sửa chữa/phụ kiện; UI gán kết luận mặc định, duyệt chuyển thẳng ACTIVE, chưa nối ledger phí/xu và thông báo.

Sau: UI danh sách–chi tiết hiển thị dữ liệu thực, thiếu thì “Chưa cung cấp”; KTV/ADMIN quyết định trên phiên bản được tải, ghi lịch sử. Duyệt tạo đánh giá phí theo bản nội dung bất biến. Tin cần thu phí chuyển APPROVED_AWAITING_FEE; chỉ mở bán sau receipt hoặc khi phần bổ sung bằng 0. Sửa nội dung/media của tin đang bán làm tin HIDDEN và cần duyệt lại.

## Nghiệp vụ và transaction

- Tổng niêm yết = giá mỗi sản phẩm × số lượng được duyệt. Biểu LISTING_INITIAL_V1 hiện có: dưới 100.000 đồng thu 10.000 đồng; từ 100.000 đồng thu 10%. Dùng policy đang hiệu lực và snapshot tại lượt duyệt, không tính tiền từ client.
- Còn thiếu = max(phí tại lượt duyệt − tổng receipt đã đóng của cùng tin, 0). Giảm giá không hoàn phí cũ; tăng giá chỉ thu phần thiếu, kể cả biểu phí giữ nguyên.
- Decision, snapshot, assessment/charge, thay đổi trạng thái, audit và outbox cùng transaction. Khóa product khi duyệt/trả phí; kiểm expectedVersion và commandKey; retry không tạo decision/receipt/reward trùng. Replay duyệt trả kết quả gốc, dù tin đã trả phí sau đó.
- Khi thực sự mở bán, Commerce cấp CREDIT_FIRST_LISTING 10.000 xu đúng một lần cho mỗi Seller. Key FIRST_LISTING:<sellerId>; guard DB yêu cầu tin đã mở bán có proof duyệt. Không tạo thưởng ngược cho tin cũ khi migrate.
- Payment facade xác nhận LISTING_MOCK; không tích hợp VNPay thật cho phí đăng tin. Catalog dùng facade Identity/Payment/Commerce/Platform, không truy cập repository của module khác.

## Schema và tính tương thích

V27 thêm manual_listing_fee_required vào products và các guard/index; tái sử dụng ledger, outbox và notifications đã có, không thêm bảng nghiệp vụ. V1–V26 SQL giữ nguyên.

MANUAL_V27 là provenance riêng của bản nội dung đối chiếu thủ công, gồm thông tin/media đang có. Nó không chứng minh AI, digest nội dung media hay eKYC canonical UC83. Các guard canonical UC83 được giữ cho nhánh đó; checkout/inventory đa số lượng và full account restriction tiếp tục là việc riêng.

Cờ mặc định false giữ nguyên hành vi dữ liệu cũ. Tin gửi duyệt hoặc xét mới được bật cờ vĩnh viễn; activation guard kiểm quyết định, bản hiện tại/public revision, giá/số lượng/content revision, phí và quyền SELLER đang hoạt động. Không backfill quyết định, khoản thu hoặc xu cho tin lịch sử.

Phát hiện upgrade có Google identity: V26 UPDATE user rồi CREATE INDEX gặp pending deferred trigger events. Callback Flyway đặt SET CONSTRAINTS ALL IMMEDIATE trước V26 trong transaction migration; SQL/checksum V26 không thay. Kiểm upgrade V21→V27 xác nhận dữ liệu gốc, Google subject và counter mới đúng.

## Thông báo và UI

Submit gửi Seller xác nhận và các KTV đang hoạt động lời nhắc. Quyết định gửi Seller kết quả/lý do/phí, KTV xác nhận. Mở bán gửi Seller receipt mô phỏng và thưởng nếu có. Outbox ghi trong business transaction; dispatcher mặc định 5 giây, khóa SKIP LOCKED tối đa 50 sự kiện/batch, delivery unique event/recipient. Worker lỗi không đảo ngược quyết định đã commit; cả batch delivery rollback và retry. Chưa có dead-letter tự động: payload lỗi kéo dài cần điều tra/vận hành, không xóa sự kiện để bỏ qua.

UI dùng Inter, nền kem, xanh/gold và component sẵn có. Desktop dùng danh sách–chi tiết; mobile xếp cột, checklist và nút rõ ràng. Modal phí có breakdown, trạng thái tải/lỗi/retry, chặn nhấp đôi, focus trap/Escape. Bell poll 15 giây khi tab hiển thị; trang thông báo có phân trang, đã đọc và đích allowlist theo role. Header mobile được sửa tràn ngang khi có bell và tên dài.

## Kiểm chứng và giới hạn

- Frontend: 148 tests/31 files đạt; lint và build gồm TypeScript đạt. Vite vẫn cảnh báo chunk >500 kB.
- Backend unit/API: 222 chạy, 220 đạt/2 skip, 0 lỗi. Package cuối đạt; lần đóng gói trước bị Windows giữ JAR trong runtime QA, đã kết thúc runtime và đóng gói lại.
- PostgreSQL riêng: 65 tests đạt qua lượt cuối 32 tests moderation/category/workflow/migration và 33 expansion kiểm lại; gồm rollback, retry, stale version, cạnh tranh KTV/thanh toán, phí tăng/giảm/ngưỡng/số lượng, reward một lần, notification privacy/delivery và clean/populated upgrade.
- Browser với tài khoản/dữ liệu giả: KTV duyệt và từ chối; Seller xem lý do, trả phí 80.000 đồng, tin ACTIVE và thông báo 10.000 xu; đánh dấu đã đọc/điều hướng; mobile 390×844 không tràn ngang ở màn hình đã kiểm. Ảnh/video fixture không chứng minh provider/media thật: ảnh dùng SVG local, video cố ý lỗi URL.

Chưa migrate database ứng dụng; chưa commit/push. AI UC16, full eKYC/KTV, quản lý user, chat/offer và provider thật nằm ngoài đợt 1. Không nâng toàn bộ UC15/UC22/UC52/UC53/UC72/UC77 thành VERIFIED chỉ từ phần đã nối này.

## Đưa vào môi trường đang dùng

1. Backup DB hiện tại và kiểm restore vào DB riêng; ghi Flyway version/checksum. Giữ artefact/source trước nâng cấp.
2. Dừng backend đang dùng trước nâng cấp, tránh hai phiên bản cùng xử lý listing. Dùng bản backend có callback và V27; Flyway chạy tuần tự các migration chưa áp, validate V1–V27.
3. Kiểm product cũ không đổi, quyền KTV/Seller, submit mới → duyệt → chờ phí → mock-pay → ACTIVE và notification. Không dùng dữ liệu thật để thử phí mock.
4. Deploy frontend cùng hợp đồng, restart backend/worker; kiểm queue outbox, lỗi delivery, quyền/owner và stale version. Thực hiện kiểm provider thật riêng khi được yêu cầu.
5. Khi có lỗi migration transaction, điều tra và sửa tiến tới; không sửa migration đã chia sẻ. Nếu cần khôi phục, dùng backup đã kiểm và đồng bộ artefact/frontend, bảo toàn ledger/audit. Không chạy downgrade bằng DELETE lịch sử.

## Các đợt tiếp theo từ năm task ban đầu

| Đợt | Phạm vi và use case kéo theo | UI và điều kiện nghiệm thu |
|---|---|---|
| 2 | UC67 quản lý user; UC11 uy tín/lịch sử xử phạt; UC51 thông báo khóa/mở; UC10 chặn; audit nền UC77 | Thay demo localStorage bằng API; bảng lọc/tìm/phân trang, hồ sơ chi tiết, lý do/thời hạn/phạm vi hạn chế. Không xóa lịch sử giao dịch; kiểm thu hồi phiên, nghĩa vụ cũ và quyền KTV/ADMIN. |
| 3 | UC18 nhắn tin; UC47 thông báo; UC10 chặn; UC59–UC60 đọc/điều hướng; gắn sản phẩm/đơn liên quan | Inbox hai cột desktop, một cột mobile; context sản phẩm, gửi/đang gửi/lỗi/thử lại, unread, attachments/quota theo đặc tả. Kiểm participant/owner, chặn, chống gửi trùng, phân trang và reconnect. |
| 4 | UC19 offer; UC23–UC25 giỏ/đặt/thanh toán; thông báo UC47/UC59–UC60; revision/stock/expiry | Offer là card có trạng thái trong chat, nhập giá và xác nhận, chấp nhận/từ chối/hết hạn; không coi accept là thanh toán. Kiểm Seller accept race, bản giá chốt, stock và expiry/idempotency. |
| Xuyên suốt | Hoàn thiện kiểm thử tính năng đã làm | Mỗi đợt có unit/API/PG, negative/rollback/concurrency và browser desktop/mobile; hồi quy auth/checkout/payment phù hợp phạm vi. Không đợi đến cuối mới kiểm. |

Các đợt 2–4 là kế hoạch tiếp nối, chưa triển khai trong TASK-0087. [Material canonical layouts](https://m3.material.io/foundations/layout/canonical-examples/overview) hỗ trợ cấu trúc danh sách–chi tiết; [Carousell buying/chat/offer](https://support.carousell.com/hc/en-us/articles/115006305247-How-do-I-buy-on-Carousell) là tham khảo cho offer trong hội thoại. Cách áp dụng vào O.G Shop là quyết định thiết kế của đợt này, giữ token/component hiện có. Shopee Webchat cũng được xem trong lượt rà soát ban đầu.
