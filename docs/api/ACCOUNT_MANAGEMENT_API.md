# Account Management API — TASK-0088

Base `/api/v1`; Bearer/cookie theo auth hiện hành. Phân quyền được kiểm lại ở service. KTV đang hoạt động quản lý customer BUYER/SELLER; từ chối cả tài khoản có vai trò nhân sự trộn với customer. ADMIN không thay KTV trong UC67.

## Endpoints

| Method / route | Quyền và kết quả |
|---|---|
| GET `/ktv/users` | KTV; `query` ≤100 ký tự, `status=ALL/ACTIVE/LOCKED/SUSPENDED/DEACTIVATED`, `role=ALL/BUYER/SELLER`, page từ 0, size 1–50 (mặc định 20) |
| GET `/ktv/users/{userId}` | KTV; customer, hạn chế, điểm/lịch sử, tối đa 50 đơn ưu tiên đang mở và 50 hồ sơ gần nhất |
| GET `/ktv/users/{userId}/history` | KTV; lịch sử quyết định phân trang, size mặc định 20/tối đa 50 |
| POST `/ktv/users/{userId}/commands` | KTV; quyết định đồng thời ghi audit và outbox |
| GET `/account` | Customer; hồ sơ của principal, không nhận userId từ client |
| GET `/account/history` | Customer; lịch sử của principal |
| GET `/account/cases/{caseId}/responses` | Customer; creator hoặc bên mua/bán của đơn liên quan |
| POST `/account/cases/{caseId}/responses` | Cùng quyền; `{text,commandKey}`, text 1–2000 ký tự, key 1–100; hồ sơ mở và còn hạn phản hồi nếu có |

Không trả entity/private evidence URL. `user.status` là trạng thái hiệu lực; `authenticationStatus` giữ trạng thái auth cũ riêng; `version` là governance_version. Client sử dụng `allowedActions`, `allowedScopes`, `allowedWeeks`, `serverTime`; backend vẫn xác minh mọi lệnh.

## Command contract

Mọi lệnh có `action`, `expectedVersion` ≥0, `commandKey` 1–100 ký tự, `reason` không rỗng ≤500. Ví dụ:

```json
{
  "action": "RESTRICT",
  "expectedVersion": 0,
  "commandKey": "client-generated-unique-key",
  "reason": "Căn cứ quyết định và lý do áp dụng",
  "scope": "BUY_ALL",
  "durationWeeks": 2
}
```

| Action | Trường bổ sung / hành vi |
|---|---|
| RESTRICT | `scope`, `durationWeeks` hoặc `expiresAt`; tối thiểu 1 tuần, tối đa 3 tháng lịch. UI chọn số tuần hợp lệ do server trả. NEW_LISTINGS chỉ áp dụng Seller; không tạo hai hạn chế active cùng scope |
| LIFT | `restrictionId` active; ghi thời điểm gỡ, giữ bản ghi; hỗ trợ legacy |
| LOCK | `expiresAt` tương lai ≤3 tháng hoặc không thời hạn; chặn hoạt động mới, giữ login và nghĩa vụ cũ |
| DEACTIVATE | Không expiresAt; ngừng mềm, không DELETE user/lịch sử |
| ACTIVATE | Gỡ trạng thái governance toàn cục; khôi phục auth legacy ACTIVE và đóng LOGIN restriction cũ; các hạn chế riêng vẫn giữ |
| COMPENSATE | `systemCaseId`, `targetCaseId`, `affectedFrom`, `affectedUntil`; phải khớp khoảng gián đoạn trong hồ sơ SYSTEM_ISSUE đã CLOSED/SYSTEM_RESOLVED, do KTV/Admin xác nhận. Đích là hồ sơ đơn cũ đủ điều kiện bù hạn Seller; cộng đúng duration vào deadline phản hồi, không kéo dài/giảm thời hạn cấm |

| Scope | Chặn |
|---|---|
| NEW_LISTINGS | Tạo/sửa/upload/submit/publish tin và trả phí để mở tin; vẫn đọc và ẩn tin sở hữu |
| CHAT | Gửi mới qua route chat/conversations; hạ tầng chặn sẵn, chat thực tế triển khai đợt 3 |
| BUY_EKYC | Mua sản phẩm yêu cầu xác thực người mua; mua tin thông thường vẫn được |
| BUY_ALL | Mua hàng nói chung; giữ riêng quyền đăng/chat/bán |
| NEW_TRANSACTIONS | Phạm vi legacy/toàn cục, chặn đăng/chat/mua và đăng ký quyền bán/eKYC mới |

Khóa toàn cục và seller unavailable đều được kiểm trong checkout; SQL guards ngăn INSERT order/checkout hoặc bypass listing dù gọi ngoài HTTP. Đơn cũ, payment/order actions, hồ sơ và thông báo không bị filter governance chặn; vẫn chịu các guard nghiệp vụ/ownership vốn có.

## Retry, lỗi và thời hạn

Lệnh khóa advisory theo key rồi row-lock customer. Cùng actor/target/payload/key trả kết quả đã lưu; key khác payload trả 409 COMMAND_KEY_CONFLICT. Version cũ trả 409 ACCOUNT_VERSION_CONFLICT; cần tải lại hồ sơ. Audit/user/outbox trong một transaction, lỗi bất kỳ rollback toàn bộ. Lệnh thành công tăng governance_version và credential_version, thu hồi token cũ; người dùng đăng nhập lại để phục vụ nghĩa vụ.

403 khi sai role/owner hoặc ACCOUNT_ACTION_RESTRICTED; 404 khi target không trong phạm vi; 400 khi scope/lý do/deadline/nguồn bù không hợp lệ. SELLER_UNAVAILABLE là 409 khi người bán không thể mở giao dịch mới. Quyền không đọc được trả 503 theo cơ chế fail closed. Không biến lỗi mạng thành thông báo thành công; retry giữ key/payload, kể cả expiresAt của khóa.

Biện pháp có expiresAt hết hiệu lực khi so với thời gian server; chưa có worker đóng record hết hạn. Lịch sử vẫn giữ nguyên. Auth legacy LOCKED/SUSPENDED vẫn chặn login đến khi KTV ACTIVATE; không tự chuyển toàn bộ tài khoản cũ. Tài khoản đã có deleted_at từ trước bị loại khỏi API quản lý này; không có chức năng khôi phục dữ liệu đã xóa cũ trong đợt 2.

## Thông báo và giới hạn

Quyết định commit sinh ACCOUNT_SELF cho owner → `/account/status` và KTV_ACCOUNT cho actor → `/ktv/users?userId=...`. KTV/Admin có route thông báo trong shell riêng. Outbox dispatcher dùng unique event/recipient và kiểm owner khi đọc; account target dùng userId, listing target cũ giữ tương thích. Xem [Notifications API](NOTIFICATIONS_API.md).

Điểm là projection đọc penalty đã xác nhận và review hợp lệ hiện có, không ghi penalty/phục hồi mới. Cảnh báo dưới 90/80 chỉ hiển thị cho KTV; chưa sinh tự động UC52 từ thay đổi điểm. UC74 xác nhận báo cáo/xử phạt, UC68 danh tính, full audit viewer/export và chat/offer chưa thuộc đợt này. [Thiết kế và rollout](../architecture/ACCOUNT_GOVERNANCE_V30_20261012.md).
