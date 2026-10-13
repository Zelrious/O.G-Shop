# Quản lý user KTV — TASK-0088 / V28–V30

Được triển khai theo “tiếp tục triển khai” sau kế hoạch đợt 2. [API](../api/ACCOUNT_MANAGEMENT_API.md), [bằng chứng UI và kiểm thử](../../output/phase2-runtime/README.md).

## Trước và sau

KtvUsersPage trước dùng danh sách demo/localStorage; thay bằng API KTV-only/customer-only, lọc/tìm/phân trang, hồ sơ/điểm/lịch sử/nghĩa vụ thật. Quyết định có lý do/phạm vi/thời hạn, key/version/row lock, ghi cùng audit/outbox. Ngừng mềm giữ dữ liệu và nghĩa vụ. Người dùng xem lý do, hạn và phản hồi hồ sơ của mình từ `/account/status`; KTV nhận xác nhận và mở đúng tài khoản.

## Use case được thực hiện

| UC | Phần nối trong đợt 2 | Giới hạn |
|---|---|---|
| UC67 | Tra cứu customer, biện pháp từng chức năng, khóa/ngừng mềm, gỡ/kích hoạt, lịch sử/nghĩa vụ cũ và bù đúng thời gian lỗi đã xác nhận | Không xóa cứng; UC68 và xác nhận/xử phạt từ UC74 tiếp nối |
| UC11 | Đọc điểm và lịch sử từ penalty/review hợp lệ có sẵn | Không viết pipeline phạt/phục hồi điểm mới |
| UC51, UC53 | Xác nhận cho KTV actor sau commit và thông báo owner về quyết định | UC66/Admin và các nguồn thông báo khác ngoài scope |
| UC52 | Cảnh báo dưới 90/80 trên hồ sơ, không tự khóa | Worker phát thông báo khi vượt ngưỡng điểm chưa triển khai |
| UC59–UC60 | Read/owner/allowlist và điều hướng ACCOUNT_SELF/KTV_ACCOUNT; route KTV/Admin notification riêng | Chat/order targets mới tiếp nối |
| Nền UC77 | Audit actor/reason/before/after/key atomic | Chưa có viewer/export audit toàn hệ thống |

UC74 chỉ cung cấp nguồn penalty đã xác nhận để đọc; không coi database có bảng/case guard là UI/API xử phạt đã hoàn tất. CHAT là scope/hạ tầng kiểm quyền, chưa có nhắn tin UC18. UC10 chặn giữa hai người thuộc đợt chat, không phải khóa tài khoản KTV.

## State và ranh giới module

Identity sở hữu account governance và access facade/filter. Commerce facade đọc đơn cũ; TrustSafety facade đọc uy tín/hồ sơ và append phản hồi/bù deadline; Platform facade ghi audit và notice. Catalog/checkout đi qua Identity facade để kiểm hành động. Không truy cập repository của module khác từ module nghiệp vụ.

`governance_state` riêng với auth status: khóa/ngừng hoạt động mới không tước login và quyền xử lý nghĩa vụ. Tăng credential_version thu hồi token đã cấp trước quyết định. Auth status LOCKED/SUSPENDED cũ vẫn chặn credential đúng; KTV ACTIVATE khôi phục auth và đóng LOGIN cũ, giữ hạn chế scope khác. User đã deleted_at bị loại khỏi API này, chưa có luồng khôi phục dữ liệu xóa cũ. Không tự migrate policy cho user cũ. Hết hạn khóa/scoped restriction so theo thời gian, không xóa lịch sử. Không auto ban theo điểm, hủy đơn, refund hoặc release.

BUY_ALL tách khỏi NEW_TRANSACTIONS: cấm mua không cấm đăng/chat/bán. SELLER_UNAVAILABLE chặn mua từ seller đang bị chặn hoạt động mới. NEW_LISTINGS kiểm cả application và SQL chuyển sang PENDING/ACTIVE; owner vẫn đọc/ẩn tin. Guard SQL khóa user trước kiểm để quyết định KTV và tạo hoạt động mới có thứ tự rõ ràng.

## Database và nguồn bù

- V28 thêm governance state/version/reason/deadline/JSON restrictions vào users; command_request vào audit_logs và unique key cho ACCOUNT_GOVERNANCE. Dùng lại command_key đã có. Không thêm bảng nghiệp vụ: 47 bảng.
- Audit proof KTV active trước update: target customer, version tăng đúng một, before/after khớp, reason/scope/time hợp lệ; unauthorized SQL bị từ chối. Timestamp Java truncate microseconds khớp PostgreSQL.
- Guard order/checkout và eKYC item; guard listing; response owner/open/deadline; compensation từ CLOSED SYSTEM_ISSUE có SYSTEM_RESOLVED và khoảng COMPENSATION do KTV/Admin xác nhận.
- V29 khóa seller/chặn gửi tin DRAFT/HIDDEN/REJECTED→PENDING; access function VOLATILE để đọc quyền sau row lock.
- V30 thêm BUY_ALL và tách native buyer purchase guard; NEW_TRANSACTIONS vẫn toàn cục/legacy.

Bù cho `seller_response_due_at` của hồ sơ đơn cũ đủ điều kiện: append DEADLINE_EXTENSION với nguồn hệ thống/khoảng thời gian/actor/reason/key. Effective deadline = base + tổng duration nguồn xác nhận. Chặn trùng source/target case. Không dùng ngày mở hồ sơ để tự kéo dài hạn; không bù bằng việc tăng thời hạn cấm. Kiểm bằng luồng UC83 thật từ listing→checkout→payment→giao→return→hai bên xác nhận, không bỏ guard canonical để tạo fixture.

## UI và thông báo

Giữ Inter, nền kem, xanh/gold, button/alert/modal đã có; kế thừa cấu trúc danh sách–chi tiết nghiên cứu ở [đợt 1](MODERATION_LISTING_V27_20261012.md). Tách identity/điểm, hạn chế, nghĩa vụ và timeline để KTV đối chiếu trước quyết định. Form lấy choices từ server, có loading/empty/error/retry; lựa chọn tài khoản mới không hiển thị hồ sơ cũ. Modal reason/confirm, chống nhấp đôi, stable retry, focus trap/Escape/restore và mobile xếp nút dọc. Người dùng có banner và trang giải thích lý do/thời hạn/hỗ trợ.

Notice bền vững commit cùng quyết định; worker sau commit delivery. Giữ field JSON outbox `productId` cũ trong Notice để đọc payload listing đã tồn tại, dùng làm reference ID theo destination; account notification API trả userId/reference_type USER và productId null. Actor KTV không vào customer AppLayout: bell mở `/ktv/notifications`, Admin `/admin/notifications`. Không gửi email/provider thật. Dead-letter tự động vẫn chưa có.

## Rollout môi trường đang dùng

Chưa apply V26–V30 vào app DB trong task này. Chỉ chạy database/container QA riêng với fake accounts/providers.

1. Ghi phiên bản/checksum DB đang dùng; backup và kiểm restore vào DB riêng, giữ artefact cũ và ledger/audit.
2. Dừng backend cũ; deploy backend chứa callback và V1–V30 cùng frontend mới. Flyway áp tuần tự migration chưa có và validate; không sửa V1–V27, không reset/clean.
3. Kiểm count/history/old accounts/old orders không đổi và 47 bảng nghiệp vụ; thử KTV bằng customer fixture, reject ADMIN/staff/customer gọi API KTV.
4. Kiểm scope BUY_ALL/BUY_EKYC/NEW_LISTINGS, thu hồi token và login lại; old order actions/case response; actor/owner notice/read/targets, retry/stale/rollback. Theo dõi outbox và query latency.
5. Nếu lỗi, dừng phục vụ và điều tra; sửa tiến tới hoặc restore backup đã kiểm cùng artefact. Không downgrade bằng DELETE audit/ledger hoặc sửa migration đã chia sẻ.

## Giới hạn tiếp nối

Tóm tắt tối đa 50 đơn ưu tiên mở/50 hồ sơ gần nhất; history phân trang. Banner polling 30 giây hiện đọc full detail, có thể tối ưu endpoint access nhẹ khi có nhu cầu vận hành. Governance expiry lazy, không có worker đóng record. Full UC74/UC68, notification ngưỡng điểm, chat/block/offer, canonical UI end-to-end và provider thật tiếp nối. Số tests và ảnh local chứng minh phạm vi đã nối, không thay nghiệm thu môi trường thực.
