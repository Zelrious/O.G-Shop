# Decisions Pending

## F1–F5/F7 đã được duyệt và sửa — TASK-0086 / 2026-10-11

Người dùng duyệt “tiến hành sửa các F khác” và cung cấp Google Web Client ID. Đã triển khai phạm vi audit bằng purpose-bound OTP/grant, Google ID token/nonce/link, V26 credential version và sửa Back Seller; kiểm local/tests đạt. Không còn quyết định code chờ trong phạm vi auth này. [Bàn giao](../../output/auth-fixes-2026-10-11/README.md), [Google setup](../setup/GOOGLE_AUTH.md). Cấu hình origin, Google/Gmail E2E thật và rollout V26 sau backup còn cần thực hiện; đây là việc kiểm/triển khai, không mở lại chính sách đã chốt. UC09 face/eKYC/KTV/full UC67 ngoài lượt này. Các checkpoint dưới đây giữ lịch sử.

## F6 đã được duyệt và sửa — TASK-0085 / 2026-10-11

Người dùng yêu cầu chỉnh sửa sau khi được giải thích Retry-After/CORS. Đã chọn expose header hiện hành và hiển thị countdown, không đổi hợp đồng JSON; kiểm thử/browser fixture đạt. Không còn quyết định chờ cho F6. [Bàn giao](../../output/auth-retry-fix-2026-10-11/README.md); các F1–F5/F7 của audit tiếp tục theo phạm vi được duyệt, không coi lượt này đã triển khai toàn bộ auth.

## Audit auth — TASK-0084 / 2026-10-11

Không mở lại các chính sách đã chốt của UC01–UC04/UC09/UC67. [Audit](../../output/auth-audit-2026-10-11/AUTH_FUNCTIONAL_AUDIT.md) đã hoàn tất; bảy khoảng trống/lỗi và thứ tự sửa được ghi để duyệt triển khai tiếp. Lượt này chỉ kiểm tra, không tự thêm Google/reset/2FA hoặc sửa source UI. Cấu hình SMTP local đã hoàn tất ở TASK-0083; nhận Gmail thật vẫn chưa nghiệm thu. Checkpoint TASK-0082 bên dưới giữ trạng thái lịch sử.

## Auth đợt 1 — TASK-0082 / 2026-10-10

Không còn chờ chọn nghiệp vụ đợt 1: người dùng duyệt email OTP, Buyer/Seller draft và 3.000 xu. Code/kiểm thử/V25 local đã hoàn tất. Local .env chưa có SMTP host/username/password; Gmail thật cần địa chỉ gửi/mật khẩu ứng dụng theo [hướng dẫn](../setup/EMAIL_OTP.md), không gửi secret trong chat. Google/reset/2FA/eKYC/KTV sẽ theo phạm vi đợt 2–3 đã nêu, không coi schema có sẵn là đã triển khai.

## RS-03 đã chốt — TASK-0081 / 2026-10-10

Người dùng chọn 2FA chỉ dành cho Người mua/Người bán; UC09 BR-04 và UC03 bước 3/BR-06 đã đồng bộ native/local/diagram. [Bằng chứng và scope](../requirements/use-cases-83/report-uc01-18/README.md). Không còn chờ quyết định này; các ghi chú đọc ban đầu dưới đây giữ lịch sử.

## Caption báo cáo — TASK-0080 / 2026-10-10

Tên và cách đánh số bảng/hình đã được người dùng chốt theo chương, không còn quyết định chờ trong lượt caption. [Rà hiện trạng](../requirements/use-cases-83/chapter-captions/README.md) ghi UC11 thiếu hai ảnh, Sequence UC07 đang chứa ảnh Activity và danh mục hình/bảng đầu báo cáo còn mẫu. Chưa có yêu cầu thay ảnh UC07; nếu làm lượt thay ảnh phải hỏi ý kiến trước theo quy trình đã chốt. Không dùng lượt sửa caption để suy diễn quyền thay ảnh hoặc sửa nghiệp vụ.

## Câu chữ đặc tả hiện hành — TASK-0075 / 2026-10-10

[REVIEW bộ 83](../requirements/use-cases-83/REVIEW.md) ghi 8 ghi chú đọc mới: trùng mã nhánh UC15, khóa/xóa dưới 80, actor UC09 so với rule 2FA, dấu câu UC17, phản ứng thiếu ở UC63, hai thời điểm UC22, chi tiết chỉ tiêu/định dạng UC78–UC81 và điểm nối cảnh báo AI UC15/UC16. RS-04 đã sửa dấu câu ở bản local. TASK-0079 đã chốt RS-02 thành xem xét khóa tài khoản dưới 80 và bổ sung phản ứng RS-05 của UC63 theo xác nhận người dùng; native/local/diagram đã đồng bộ. Những ghi chú còn lại giữ giải thích theo nguồn. Không mở lại các chính sách đã được nêu rõ; không lấy các mặc định triển khai lịch sử bên dưới thay cho đặc tả hiện hành.

## Report checkpoint — 2026-10-09

- Quyết định báo cáo hiện hành và nguồn tiếp tục được ghi trong [TASK-0065](archive/TASK-0065-report-policy-checkpoint-20261009.md). Phí tin do Người bán trả sau duyệt, phí bổ sung cộng dồn kể cả Admin giữ nguyên biểu phí; điều kiện nhóm thanh toán, trả/hoàn 100% hoặc miễn hoàn do KTV, cửa sổ lấy trả và phạm vi sandbox đã được đồng bộ trong Google Docs.
- [TASK-0067](archive/TASK-0067-simplify-83-use-case-specifications.md) đã rút gọn 83 UC, giảm 17,3% ký tự và bảo toàn tab sao lưu TASK-0066. Đây là mức giảm quan sát được, không phải mục tiêu trang/tỷ lệ bắt buộc.
- [TASK-0068](archive/TASK-0068-database-coverage-83-use-cases.md) đã hoàn thành [phân tích database theo 83 UC](../architecture/DATABASE_COVERAGE_83_USE_CASES_20261009.md), xác nhận live V1–V14 và chỉ rõ các thiếu hụt/12 nhóm mở rộng. Người dùng đã cho phép triển khai; TASK-0069 đã áp dụng V15–V21 với legacy provenance/backup/restore. Không mở lại các chính sách đã chốt; còn cần nối các API/worker theo [bàn giao V21](../architecture/DATABASE_EXPANSION_V21_20261009.md).
- Bảng quyết định dưới đây là baseline triển khai cũ, chưa đối chiếu toàn bộ với báo cáo 83 UC. Không xem trạng thái cũ của DP-03/DP-07/DP-09/DP-10 là bằng chứng người dùng chưa trả lời cho báo cáo; xem checkpoint mới trước khi tiếp tục.

| ID | Quyết định cần chốt | Mặc định tạm thời | Tác động |
|---|---|---|---|
| DP-02 | Thời hạn reservation khi checkout | Cấu hình, chưa chốt giá trị | Commerce, payment, scheduled job |
| DP-03 | Thời hạn Buyer kiểm tra hàng | Cấu hình, chưa chốt giá trị | Fulfillment, complaint, payment |
| DP-04 | Payment sandbox | Adapter nội bộ trước, provider chọn sau | Payment |
| DP-05 | Shipping sandbox | Adapter nội bộ trước, provider chọn sau | Fulfillment |
| DP-07 | Chính sách hoàn hàng | Full-order refund trong MVP | Complaint, fulfillment, payment |
| DP-08 | Giấy phép repository | Chưa tạo LICENSE | Repository governance |
| DP-09 | Mức phí hệ thống, bên chịu phí và min/max | Policy V3 0% để không thu nhầm; Backend hỗ trợ phí Buyer/Seller | Catalog, offer, checkout, payment |
| DP-10 | Giữ cart nhiều sản phẩm hay chỉ mua trực tiếp từ listing/offer | Tạm giữ `carts`/`cart_items`; accepted offer vẫn có thể checkout trực tiếp | Commerce, frontend checkout |

## Resolved

| ID | Quyết định | Ngày |
|---|---|---|
| DP-01 | Access JWT 15 phút + opaque refresh token 30 ngày, HttpOnly, rotate/revoke | 2026-09-19 |
| DP-06 | Cloudinary là media provider duy nhất cho MVP, ẩn sau outbound port | 2026-09-19 |
| DP-11 | Hoàn thiện và kiểm thử core flow trên PostgreSQL trước WebSocket, outbox worker, Redis hoặc tối ưu multi-instance | 2026-09-19 |
