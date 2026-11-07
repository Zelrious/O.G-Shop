# TASK-0115: Triển Khai Hệ Thống Kiểm Duyệt Tin Nhắn Văn Bản Tự Động và Chống Rò Rỉ Giao Dịch Ngoại Sàn (Giai Đoạn 1)

- **Mã Task**: TASK-0115
- **Ngày bắt đầu**: 2026-11-07
- **Trạng thái**: COMPLETED
- **Người thực hiện**: Zelrious <huynhlongbaokhanh@gmail.com>
- **Mục tiêu**:
  1. Triển khai Giai đoạn 1 — Hệ thống kiểm duyệt văn bản tự động và chống rò rỉ giao dịch ngoại sàn (Text Moderation & Anti-Disintermediation) trong module `communication`.
  2. Tạo Flyway migration `V37__chat_moderation.sql` mở rộng `public.users` (violation score, restriction timestamp) và tạo bảng lưu vết `public.chat_moderation_logs`.
  3. Xây dựng bộ kiểm duyệt `TextModerator.java` đạt chuẩn Zero-Trust CPU tốc độ cao (< 2ms, 0 quota AI) với các cơ chế:
     - Chuẩn hóa Zero-Width, ký tự điều hướng văn bản, Unicode NFKC, emoji & keycaps.
     - Ánh xạ Homoglyphs (O/o->0, l/I/|->1, S/s->5, B/b->8).
     - Từ điển tiếng lóng nền tảng ("da lô", "dép lào", "phây búc", "tê lê", defanged URLs) và số đếm tiếng Việt ("không"->0, "chín"->9, "k"->0).
     - Bộ đệm cửa sổ trượt In-Memory RAM (`ConcurrentHashMap<Long, Deque<MessageSnippet>>`) 10 tin nhắn gần nhất phát hiện rải SĐT qua nhiều tin liên tiếp (TC08–TC10).
     - Bộ lọc chống dương tính giả (Anti-False-Positive Filter) triệt tiêu việc bắt nhầm tin nhắn mặc cả giá ("fix 500k", "bớt 200k") và thông số kích thước ("120cm", "15kg").
  4. Tích hợp trực tiếp vào `ChatService.java` khi gửi tin nhắn: lưu bản masked, ghi nhận `chat_moderation_logs`, tăng `chat_violation_score` và trả metadata `moderationStatus: "MASKED"`.
  5. Nâng cấp giao diện Frontend (`ConversationWorkspace.tsx`, `chatApi.ts`, `chat.css`): hiển thị Banner cảnh báo an toàn màu vàng với icon khiên bảo vệ (outline SVG) và nhãn `[Đã ẩn thông tin liên hệ]` bên cạnh tin nhắn bị che.

## Các Hạng Mục Đã Hoàn Thành

### 1. Cơ sở Dữ liệu Flyway Migration (`backend/src/main/resources/db/migration/`)
- [x] Tạo file migration `V37__chat_moderation.sql`:
  - Thêm cột `chat_violation_score INT NOT NULL DEFAULT 0` và `chat_restricted_until TIMESTAMPTZ NULL` vào bảng `public.users`.
  - Tạo bảng nhật ký kiểm duyệt `public.chat_moderation_logs` (log_id, conversation_id, sender_id, original_content, masked_content, violation_type, triggered_rule, created_at).
  - Tạo index `idx_chat_moderation_logs_sender` và `idx_chat_moderation_logs_conversation`.

### 2. Backend Logic Kiểm Duyệt Văn Bản (`TextModerator.java`)
- [x] Tạo class `TextModerator.java` trong `com.oldbutgold.shop.modules.communication.application`:
  - Pipeline chuẩn hóa Zero-Trust: Bóc tách invisible bytes (`\u200B` - `\u200D`, `\uFEFF`, RTL override `\u202E`), keycap combiners (`\uFE0F\u20E3`), Unicode NFKC và Cyrillic homoglyphs.
  - Phân tích ngữ cảnh số để ánh xạ ký tự đồng hình (O/o->0, l/I/|->1, S/s->5, B/b->8).
  - Chuyển đổi từ điển số đếm tiếng Việt ("không" -> 0, "chín" -> 9, tiền tố "k" -> 0) và tiếng lóng nền tảng ("da lô", "dép lào", "phây búc", "tê lê", external URLs, defanged links).
  - Thuật toán cửa sổ trượt In-Memory (`ConcurrentHashMap<Long, Deque<MessageSnippet>>`): Nối các chữ số liên tiếp từ cùng người gửi trong cửa sổ 10 tin gần nhất; phát hiện rải SĐT qua nhiều tin nhắn ngắn liên tiếp (TC08, TC09, TC10).
  - Bộ lọc chống dương tính giả (Anti-False-Positive Filter): Loại bỏ hoàn toàn giá cả (`500k`, `200k`, `2.500.000 đ`), kích thước (`120cm`, `15kg`, `50 x 30 x 20 cm`) và số lượng đếm trước khi xét duyệt SĐT.
  - Cơ chế Data Masking: Che giấu chuỗi vi phạm bằng `***` trước khi lưu trữ vào CSDL.

### 3. Tích Hợp Backend (`ChatService.java`, `ChatRepository.java`, `ChatDtos.java`)
- [x] `ChatDtos.java`: Bổ sung `moderationStatus` và `violationType` vào `ChatDtos.Message`.
- [x] `ChatRepository.java`: Thêm `logModeration` và `incrementViolationScore`.
- [x] `ChatService.java`:
  - Inject `TextModerator` vào `ChatService`.
  - Trong phương thức `send()`: kiểm duyệt trước khi lưu, lưu bản masked, ghi nhận log vào `chat_moderation_logs`, tăng `chat_violation_score` cho người gửi vi phạm.
  - Cập nhật phương thức `message()` đọc và mapping `moderationStatus` cùng `violationType` từ `media_metadata`.
  - Hỗ trợ replay an toàn khi tin nhắn bị che.

### 4. Kiểm Thử Backend Đầy Đủ (`TextModeratorTest.java`)
- [x] Tạo file `TextModeratorTest.java` bao phủ 100% các ca kiểm thử:
  - TC01–TC05: SĐT có dấu chấm, gạch ngang, khoảng trắng, dấu hoa thị, gạch dưới.
  - TC06–TC07: SĐT xen chữ/homoglyph (O9O, 090l), xen emoji keycaps, xen Zero-width space.
  - TC08–TC10: Kịch bản rải số qua 3–5 tin nhắn ngắn liên tiếp (`09` -> `87` -> `65` -> `43` -> `21`) và kịch bản xen kẽ tin nhắn đối tác.
  - TC41: Tiếng lóng Việt Nam ("kết bạn da lô", "nhắn qua dép lào", "phây búc", "tê lê", "không chín không...", "k chín không...").
  - Anti-False-Positive: Tin nhắn mặc cả ("fix 500k", "bớt 200k"), hỏi kích thước ("120cm", "75cm", "15kg", "220v", "65w") tuyệt đối không bị bắt nhầm.
  - Off-platform payment: "cọc trước 100k", "chuyển lúa ngoài", "chuyển khoản riêng".
  - External links & defanged URLs: `chotot[.]vn`, `zalo.me`.
  - Ca kết hợp: Mặc cả tiền kèm SĐT ("Fix 500k gọi mình 0901234567 nha") giữ nguyên "Fix 500k" và che đúng phần SĐT thành `***`.
- [x] Chạy `mvn test -Dtest=TextModeratorTest`: 9/9 tests passed (0 failures).
- [x] Chạy toàn bộ backend test: 712 tests run, 0 failures.

### 5. Nâng Cấp Giao Diện Frontend (`frontend/src/features/chat-offer/`)
- [x] `chatApi.ts`: Mở rộng interface `ChatMessage` với các trường `moderationStatus?: string; violationType?: string;`.
- [x] `ConversationWorkspace.tsx`:
  - Thêm `hasMaskedMessage`: Tự động nhận diện khi cuộc trò chuyện có tin nhắn bị che `MASKED`.
  - Hiển thị **Banner Cảnh Báo An Toàn** ở đầu khung chat với icon khiên bảo vệ `shield` (outline stroke SVG) và thông điệp:
    *"Cảnh báo an toàn: Nhằm phòng tránh rủi ro lừa đảo mất tiền cọc, vui lòng không chia sẻ số điện thoại hoặc giao dịch qua Zalo/Facebook ngoài sàn O.G Shop."*
  - Hiển thị nhãn nhỏ tinh tế `[Đã ẩn thông tin liên hệ]` bên cạnh bong bóng chat của tin nhắn bị che.
- [x] `chat.css`: Định dạng banner cảnh báo và nhãn tin nhắn bằng CSS tokens hệ thống (`var(--og-color-surface)`, `var(--og-color-warning)`, `var(--og-color-text-primary)`), tương thích 100% Dark Mode và Light Mode.
- [x] `chat.test.tsx`: Bổ sung ca kiểm thử tự động xác minh hiển thị Banner cảnh báo an toàn và nhãn `[Đã ẩn thông tin liên hệ]`.
- [x] Chạy kiểm thử Frontend: 51 test files, 266 tests passed (100%).
- [x] Chạy `npm run build`: Build thành công không có cảnh báo/lỗi TypeScript.
