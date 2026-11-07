# BÁO CÁO KỸ THUẬT: HỆ THỐNG KIỂM DUYỆT TIN NHẮN VÀ CHỐNG RÒ RỈ GIAO DỊCH (CHAT MODERATION & ANTI-DISINTERMEDIATION)

*Dự án: Old but Gold (O.G Shop) — Nền tảng TMĐT Đồ Cũ C2C*  
*Ngày lập: 07/11/2026*  
*Tài liệu tham chiếu: "Kiến Trúc Hệ Thống Kiểm Duyệt Tin Nhắn Và Chống Rò Rỉ Giao Dịch Trên Nền Tảng Thương Mại Điện Tử Đồ Cũ (C2C)" (11/2026)*  
*Module sở hữu: `com.oldbutgold.shop.modules.communication` phối hợp `trustsafety` và `platform`*

---

## 1. BẢN CHẤT KINH TẾ VÀ AN NINH NỀN TẢNG C2C

### 1.1. Hiện tượng Rò rỉ Giao dịch (Platform Leakage / Disintermediation)
- Trên sàn đồ cũ C2C, sản phẩm mang tính chất đơn chiếc độc bản (single-SKU), tình trạng hao mòn đa dạng khiến nhu cầu trao đổi qua tin nhắn (in-app chat) là bắt buộc để thương lượng giá, kiểm tra ngoại quan và thỏa thuận giao nhận.
- Động cơ kinh tế: Người mua và Người bán có xu hướng trao đổi thông tin liên hệ (Số điện thoại, Zalo, Facebook) để giao dịch trực tiếp nhằm trốn phí hoa hồng sàn (take rate/commission fee).
- Thiệt hại: Không chỉ làm thất thoát doanh thu của sàn, việc rời khỏi nền tảng sẽ **vô hiệu hóa hoàn toàn cơ chế thanh toán giữ tiền đảm bảo (Escrow Payment)** của O.G Shop.

### 1.2. Ba Kịch Bản Tấn Công Khi Rời Khỏi Sàn
1. **Lừa đảo chiếm đoạt tiền cọc (Deposit Scam)**: Kẻ gian đăng bán hàng công nghệ cũ giá rẻ, dụ kết bạn Zalo rồi yêu cầu cọc tiền vận chuyển và lập tức chặn liên lạc.
2. **Tấn công Phishing và chiếm đoạt OTP**: Giả mạo tin nhắn/link ngân hàng quốc tế hoặc SMS Brandname yêu cầu người bán nhập OTP nhận tiền, rút sạch số dư tài khoản.
3. **Mạo danh chăm sóc khách hàng / Tuyển dụng hoa hồng**: Thu thập SĐT công khai trên sàn để liên hệ dẫn dụ vào bẫy lừa đảo nhiệm vụ ngoài sàn.

---

## 2. MA TRẬN KỸ THUẬT LÁCH LUẬT ĐỐI KHÁNG (TC01–TC55)

Tài liệu nghiên cứu phân loại 55 ca kiểm thử đối kháng (40 cơ bản TC01–TC40 và 15 nâng cao đặc thù Việt Nam TC41–TC55) thành 7 nhóm kỹ thuật:

1. **Biến đổi dấu câu & phân cách (TC01–TC05)**: Chèn ký tự phi chữ số (`090*123*4567`, `090.123.4567`, `090 123 4567`).
2. **Nhiễu ký tự, Homoglyph & Unicode ẩn (TC06–TC07, TC35–TC36, TC38)**:
   - Dùng chữ cái Kirin/Latinh trông giống số (chữ `O`/`o` thay số `0`, `l`/`I`/`|` thay số `1`, `S`/`$` thay `5`, `B` thay `8`).
   - Chèn ký tự vô hình Zero-Width Space (`​`), Zero-Width Non-Joiner (`‌`), Byte Order Mark (`﻿`) làm đứt gãy bộ tách từ.
3. **Phân mảnh không gian & thời gian (TC08–TC10, TC14, TC20, TC40)**:
   - **Lỗ hổng nghiêm trọng nhất**: Chia nhỏ SĐT ra 3–5 tin nhắn ngắn liên tiếp (`09` ➡️ `87` ➡️ `65` ➡️ `43` ➡️ `21`). Các bộ lọc từng tin nhắn độc lập (Stateless) hoàn toàn bị mù.
4. **Biến thể Email & URL (TC11–TC13, TC15–TC19, TC21)**: Viết dạng defanged (`chotot[.]vn`, `chotot . vn`, `gmail a còng com`).
5. **Điều hướng nền tảng & Từ lóng tiếng Việt (TC22–TC25, TC41)**: "da lô", "dép lào", "zl", "phây búc", "fb", "tê lê", "chuyển lúa", "cọc trước".
6. **Dữ liệu tài chính & PII (TC26–TC30)**: Số tài khoản ngân hàng, mã OTP, số CCCD/CMND.
7. **Đa phương thức & Tấn công nâng cao (TC31–TC34, TC42–TC55)**: Ảnh chụp SĐT, mã QR Zalo, câu đố toán học ("năm sinh Bác Hồ 1890"), ngụy trang thông số kỹ thuật máy, gửi SĐT rồi thu hồi chớp nhoáng (TC48), tiêm nhiễm chỉ thị Prompt Injection (TC54).

---

## 3. CÁC THUẬT TOÁN VÀ GIẢI PHÁP KỸ THUẬT ĐƯỢC CHỌN

Để tối ưu cho môi trường thực tế của O.G Shop: **Chi phí bằng 0, chạy mượt trên Laptop CPU (< 2ms), 0 Quota AI**, hệ thống áp dụng các thuật toán cốt lõi sau:

### 3.1. Thuật Toán Chuẩn Hóa Văn Bản Zero-Trust (Text Normalization Pipeline)
Trước khi bất kỳ biểu thức chính quy nào chạy, chuỗi đầu vào $S$ trải qua 5 bước chuyển hóa đơn ánh:
1. **Recursive Unwrapping**: Loại bỏ thẻ Markdown/HTML, giải mã chuỗi Base64/URL-encoded nếu có.
2. **Invisible Byte & Control Stripping**: Lọc sạch toàn bộ dải Unicode Zero-Width (`​` đến `‍`, `﻿`), ký tự điều hướng văn bản (`‮` RTL override), Tab, Newline và biểu tượng Emoji.
3. **Unicode NFKC & Homoglyph Canonicalization**: Chuẩn hóa Unicode Form KC (đưa chữ số Full-width `０９` về `09`), đối chiếu bảng ánh xạ Confusables chuyển chữ `O`/`o` đứng cạnh chuỗi số về số `0`, chữ `l`/`I` về `1`.
4. **Vietnamese Phonetic & Slang Transliteration**: Chuyển đổi số đếm tiếng Việt ("không", "k" ➡️ 0, "chín" ➡️ 9), quy đổi từ lóng nền tảng ("da lô", "dép lào", "zl" ➡️ "zalo", "phây búc" ➡️ "facebook").
5. **Fuzzy Matching & Regex Engine**: Khớp mẫu đầu số viễn thông Việt Nam (`(03|05|07|08|09)\d{8}`), tài khoản mạng xã hội và từ khóa giao dịch ngoại sàn.

### 3.2. Thuật Toán Cửa Sổ Trượt Có Trạng Thái (Stateful Sliding-Window Buffer)
- **Vấn đề**: Bộ lọc từng tin nhắn đơn lẻ không thể phát hiện kỹ thuật rải số qua nhiều tin (TC09).
- **Thuật toán**:
  - Khởi tạo cấu trúc dữ liệu `ConcurrentHashMap<Long, Deque<MessageSnippet>>` ngay trong bộ nhớ RAM của Spring Boot (định danh theo `conversationId`).
  - Mỗi khi có tin nhắn mới, trích xuất chuỗi chữ số và đẩy vào Deque (lưu tối đa 10 tin gần nhất trong cửa sổ trượt).
  - **Cơ chế chống dương tính giả (Anti-False-Positive Filter)**: Tự động loại trừ các con số có hậu tố đơn vị tiền tệ (`k`, `tr`, `triệu`, `đ`, `vnd`) hoặc kích thước/trọng lượng (`cm`, `mm`, `kg`, `g`, `m`) trước khi ghép chuỗi.
  - Nối toàn bộ chữ số còn lại từ các tin nhắn liên tiếp của cùng người gửi. Nếu chuỗi ghép vượt ngưỡng và khớp mẫu SĐT Việt Nam 10 chữ số, kích hoạt chặn ngay tại tin nhắn hoàn tất dãy số.
- **Write-Ahead Audit**: Do kiểm duyệt chạy đồng bộ trước khi lưu vào Database, hành vi gửi rồi bấm "Thu hồi" (Unsend/Edit - TC48) vẫn bị hệ thống bắt trọn và ghi nhận vi phạm.

### 3.3. Cơ Chế Che Dấu Dữ Liệu Tức Thời (Data Masking) & Cảnh Báo An Toàn
- **Triết lý**: Không chặn đứng hoàn toàn tin nhắn ở lần vi phạm đầu tiên để tránh gây ức chế cho người dùng chân chính lỡ gửi SĐT để người thân nhận hàng.
- **Data Masking**: Chuỗi SĐT / Link vi phạm được thay thế bằng `***` hoặc `[SĐT đã bị ẩn vì an toàn]` trước khi lưu vào database và chuyển tiếp sang người nhận.
- **Contextual Value Nudges (Cảnh báo tâm lý)**: Tin nhắn vi phạm trả về cờ `moderationStatus: "MASKED"`, giao diện Frontend lập tức hiển thị Banner màu vàng cảnh báo rủi ro mất tiền cọc khi giao dịch ngoài sàn.

### 3.4. Cỗ Máy Xử Phạt Bậc Thang 4 Cấp Độ (Tiered Penalty Engine)
Học hỏi từ hệ thống điểm Sao Quả Tạ của Shopee và cơ chế Khóa tính năng Chat của Chợ Tốt:
1. **Mức 1 (Che nội dung & Cảnh báo)**: Vi phạm lần 1 ➡️ Che `***`, cộng +1 điểm vi phạm (`chat_violation_score`), hiện banner cảnh báo an toàn.
2. **Mức 2 (Cảnh cáo nghiêm trọng & Chặn gửi)**: Vi phạm lần 2 ➡️ Chặn đứng tin nhắn không cho gửi sang đối tác; hiển thị Modal cảnh báo đỏ yêu cầu người dùng tích cam kết không giao dịch ngoài sàn mới mở lại thanh nhập.
3. **Mức 3 (Tạm khóa tính năng Chat)**: Vi phạm lần 3 ➡️ Khóa quyền gửi tin trong 24 giờ (hoặc 3 phút khi demo kiểm thử); cho phép tự mở khóa sớm nếu hoàn thành Mini-Quiz 3 câu hỏi trắc nghiệm an toàn giao dịch đạt 3/3 điểm.
4. **Mức 4 (Hạn chế gian hàng & Chuyển KTV duyệt)**: Vi phạm $\ge 4$ lần ➡️ Khóa chat, tạm ẩn toàn bộ tin đăng của người bán khỏi trang tìm kiếm, gửi thông báo nghi vấn và nhật ký bằng chứng sang hàng đợi KTV theo UC56 để KTV ra quyết định xử phạt uy tín theo UC74.

---

## 4. QUYẾT ĐỊNH KIẾN TRÚC ĐÃ ĐƯỢC THỐNG NHẤT (ARCHITECTURAL DECISIONS)

| Mã Quyết định | Tên Quyết định | Nội dung lựa chọn & Lý do |
|---|---|---|
| **ADR-MOD-01** | **Chiến lược Phân tầng: Text trước, Media sau** | Ưu tiên hoàn thiện 100% tầng Text CPU trước vì đây là kênh phát sinh 80% rủi ro rò rỉ giao dịch và có thể chạy với độ trễ < 2ms, tốn 0 quota AI. Sau đó mới nối OCR/QR trên Python eKYC Service. |
| **ADR-MOD-02** | **In-Memory Sliding Window thay vì Redis Cluster** | Sử dụng cấu trúc `ConcurrentHashMap` và `ArrayDeque` trong bộ nhớ ứng dụng Spring Boot. Không phát sinh thêm container Redis nặng nề trên laptop, đáp ứng hoàn hảo yêu cầu kiểm thử và demo đồ án. |
| **ADR-MOD-03** | **Data Masking thay vì Hard Reject ở Mức 1** | Che chuỗi vi phạm bằng `***` thay vì trả lỗi 400 khiến kẻ gian biết ngay quy tắc để thử cách lách khác. Đảm bảo trải nghiệm liền mạch cho người dùng hợp pháp. |
| **ADR-MOD-04** | **Phân định ranh giới giữa Hệ thống tự động và KTV** | Hệ thống tự động xử lý Mức 1–3 (che text, cảnh báo, tạm khóa chat có hạn). Không tự ý xóa tài khoản hay trừ điểm uy tín vĩnh viễn; việc trừ điểm uy tín (3–5 điểm) và cấm tài khoản vĩnh viễn thuộc thẩm quyền của KTV theo đúng `BR-UC18-05`, `BR-UC56-01` và `BR-UC74-07`. |
| **ADR-MOD-05** | **Thiết kế Tiến hóa Plug-and-Play (SOLID Adapter)** | Định nghĩa interface `IModerationAdapter` trong kiến trúc để sẵn sàng cắm thêm `ImageModerationAdapter` (OCR/QR), `VideoModerationAdapter` và `VoiceModerationAdapter` (ASR Whisper) trong các giai đoạn tiếp theo mà không phải sửa đổi luồng lõi. |

---

## 5. ĐỐI CHIẾU VỚI CÁC USE CASE HỆ THỐNG

- **UC18 (Trao đổi qua tin nhắn)**: Bổ sung bước kiểm duyệt nội dung, che `***` SĐT/Link và hiển thị banner cảnh báo an toàn giao dịch.
- **UC19 (Thương lượng giá)**: Giữ nguyên tính năng trả giá và mua ngay nội dòng (đây là công cụ giữ chân giao dịch chủ động quan trọng).
- **UC56 (Nhận thông báo tin nhắn có dấu hiệu vi phạm)**: Bổ sung nguồn kích hoạt tự động từ hệ thống kiểm duyệt khi tài khoản vi phạm chạm ngưỡng Mức 4.
- **UC74 (Xử lý vi phạm và áp dụng hạn chế tài khoản)**: Cung cấp bằng chứng chuỗi tin nhắn và lịch sử vi phạm từ bảng `chat_moderation_logs` cho KTV xem xét.
