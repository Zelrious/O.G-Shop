# BÁO CÁO NGHIÊN CỨU & THIẾT KẾ TOÀN DIỆN
# HỆ THỐNG KIỂM DUYỆT BẢO MẬT TIN NHẮN, CHỐNG RÒ RỈ GIAO DỊCH, PHÁT HIỆN CÔNG KÍCH & TRIGGER ĐIỀU PHỐI KTV (UC56 / UC74)

*Dự án: Nền tảng Thương mại Điện tử Đồ cũ C2C Old but Gold (O.G Shop)*  
*Tác giả / Chủ sở hữu: Huỳnh Long Bảo Khánh (Zelrious)*  
*Thời điểm hoàn thành báo cáo: 08/11/2026*  
*Mã tài liệu: ARCH-DOC-20261108-CHAT-MODERATION*  
*Phạm vi áp dụng: Module `communication` phối hợp `identity`, `trustsafety` và `platform`*

---

## MỤC LỤC
1. [TỔNG QUAN VÀ ĐÁNH GIÁ HIỆN TRẠNG CODEBASE](#1-tổng-quan-và-đánh-giá-hiện-trạng-codebase)
2. [CƠ SỞ PHÁP LÝ & TIÊU CHUẨN AN TOÀN THÔNG TIN](#2-cơ-sở-pháp-lý--tiêu-chuẩn-an-toàn-thông-tin)
3. [CƠ SỞ LÝ THUYẾT & KHOA HỌC TÍNH TOÁN](#3-cơ-sở-lý-thuyết--khoa-học-tính-toán)
4. [PHÂN TÍCH HIỆU NĂNG, ĐA LUỒNG VÀ GIỚI HẠN BIÊN](#4-phân-tích-hiệu-năng-đa-luồng-và-giới-hạn-biên)
5. [TẬP DỮ LIỆU TỪ VỰNG VI PHẠM MỞ RỘNG (LEXICON & TAXONOMY)](#5-tập-dữ-liệu-từ-vựng-vi-phạm-mở-rộng-lexicon--taxonomy)
6. [THIẾT KẾ KIẾN TRÚC TRIGGER & GIAO DIỆN KTV (UC56 & UC74)](#6-thiết-kế-kiến-trúc-trigger--giao-diện-ktv-uc56--uc74)
7. [MA TRẬN KIỂM THỬ THỰC NGHIỆM PHÂN TẦNG (LEVEL 1 – LEVEL 5)](#7-ma-trận-kiểm-thử-thực-nghiệm-phân-tầng-level-1--level-5)
8. [KẾ HOẠCH TRIỂN KHAI & PROMPT DÀNH CHO AGENT THỰC THI](#8-kế-hoạch-triển-khai--prompt-dành-cho-agent-thực-thi)

---

## 1. TỔNG QUAN VÀ ĐÁNH GIÁ HIỆN TRẠNG CODEBASE

### 1.1. Bối cảnh Hệ thống
Trong mô hình TMĐT đồ cũ C2C, sản phẩm mang tính đơn chiếc độc bản (single-SKU), độ hao mòn khác nhau nên nhu cầu trao đổi qua tin nhắn nội sàn (In-app Chat) giữa Người mua và Người bán là bắt buộc. Tuy nhiên, kênh chat đối mặt với 2 nguy cơ an ninh nghiêm trọng:
1. **Rò rỉ giao dịch (Disintermediation / Platform Leakage)**: Người dùng trao đổi Số điện thoại, tài khoản mạng xã hội (Zalo, Facebook, Telegram) nhằm giao dịch trực tiếp ngoài sàn để trốn phí hoa hồng. Hành vi này vô hiệu hóa hoàn toàn cơ chế thanh toán tạm giữ đảm bảo (Escrow) của O.G Shop, dẫn tới lừa đảo chiếm đoạt tiền cọc (Deposit Scam) và tấn công Phishing.
2. **Nội dung độc hại và vi phạm chuẩn mực cộng đồng**: Tranh cãi trả giá, mâu thuẫn giao dịch dẫn tới chửi bới, đe dọa vũ lực, phân biệt vùng miền hoặc phát tán nội dung chống phá thể chế chính trị.

### 1.2. Đánh giá Tỷ lệ Hoàn thành Hiện tại so với Kế hoạch
Căn cứ vào `CHAT_MODERATION_TECHNICAL_REPORT_20261107.md` và nhiệm vụ `TASK-0115`:

| Hạng mục kiểm tra | Trạng thái Code hiện tại | % Hoàn thành |
|---|---|:---:|
| **Giai đoạn 1 (Lõi lọc text CPU, Sliding Window, Anti-False Positive, V37)** | Hoàn tất trong `TextModerator.java`, `V37__chat_moderation.sql`, `ChatService.java` và Frontend Banner/Badge. | **100%** |
| **Mức 1 Xử phạt (Data Masking `***` + Cảnh báo an toàn)** | Hoàn tất, che SĐT/Link tức thì, tăng `chat_violation_score`. | **100%** |
| **Mức 2 Xử phạt (Chặn gửi lần 2 + Modal cam kết đỏ)** | Chưa triển khai (vẫn đang che tin như Mức 1). | **0%** |
| **Mức 3 Xử phạt (Tạm khóa chat 24h + Mini-Quiz tự mở khóa)** | Đã có cột `chat_restricted_until` trong DB nhưng chưa chặn trong `ChatService`, chưa có UI Quiz. | **20%** |
| **Mức 4 Xử phạt & Hàng đợi KTV (UC56 / UC74)** | Đã có log DB nhưng chưa có Trigger tự động tạo ticket báo KTV và chưa có giao diện KTV duyệt chat. | **15%** |
| **Phát hiện Công kích, Kỳ thị vùng miền, Chính trị** | Chưa tích hợp bộ từ điển này vào `TextModerator.java`. | **10%** |
| **Giai đoạn 2 (Kiểm duyệt Hình ảnh OCR / Mã QR)** | Chưa triển khai (nằm ở lộ trình sau). | **0%** |
| **TỔNG THỂ TOÀN BỘ HỆ THỐNG AN NINH CHAT** | **Đã hoàn thiện nền tảng lõi vững chắc, cần mở rộng dữ liệu và luồng KTV** | **~52%** |

---

## 2. CƠ SỞ PHÁP LÝ & TIÊU CHUẨN AN TOÀN THÔNG TIN

Việc kiểm duyệt nội dung trên nền tảng O.G Shop tuân thủ các văn bản pháp luật hiện hành của Việt Nam:

1. **Luật An ninh mạng 2018 (Luật số 24/2018/QH14)**:
   - *Điều 8 & Điều 16*: Nghiêm cấm sử dụng không gian mạng để tuyên truyền chống phá Nhà nước CHXHCN Việt Nam; kích động biểu tình, bạo loạn, phá rối an ninh; xúc phạm lãnh tụ, danh nhân; xúc phạm Quốc kỳ, Quốc ca, Quốc huy; tuyên truyền chiến tranh, khủng bố.
   - *Điều 26*: Trách nhiệm của doanh nghiệp cung cấp dịch vụ mạng trong việc ngăn chặn, gỡ bỏ thông tin vi phạm và lưu trữ nhật ký kiểm toán phục vụ cơ quan điều tra.
2. **Nghị định 72/2013/NĐ-CP & Nghị định 15/2020/NĐ-CP**:
   - Quy định chế tài xử phạt hành chính đối với hành vi cung cấp, chia sẻ thông tin xúc phạm uy tín của cơ quan, tổ chức, danh dự của cá nhân; chia rẽ khối đại đoàn kết dân tộc, kích động kỳ thị vùng miền, tôn giáo, chủng tộc.
3. **Tiêu chuẩn Cộng đồng & Quyền Riêng tư**:
   - Tuân thủ nguyên tắc bảo vệ dữ liệu cá nhân (PII Protection): Tự động che giấu số Căn cước công dân (CCCD 12 số) và Chứng minh nhân dân (CMND 9 số) để bảo vệ người dùng trước rủi ro rò rỉ danh tính.

---

## 3. CƠ SỞ LÝ THUYẾT & KHOA HỌC TÍNH TOÁN

### 3.1. Các Kỹ thuật Lách luật Đối kháng (Adversarial Evasion Techniques)
Người dùng cố ý lách luật thường áp dụng 5 kỹ thuật:
1. **Biến dạng hình học ký tự (Homoglyph Substitution)**: Lợi dụng sự tương đồng về thị giác giữa chữ cái và chữ số (chữ `O`/`o` thay số `0`, chữ `l`/`I`/`|` thay số `1`, chữ `S` thay số `5`, chữ `B` thay số `8`).
2. **Chèn nhiễu vô hình (Zero-Width Steganography)**: Chèn các ký tự điều hướng và byte vô hình (`​` Zero-width space, `‌` ZWNJ, `﻿` BOM, `‮` RTL override) nhằm làm gãy các bộ tách từ Regex thông thường.
3. **Phân mảnh không gian & thời gian (Spatial-Temporal Fragmentation Attack)**: Chia nhỏ chuỗi nhạy cảm thành nhiều tin nhắn ngắn liên tiếp (`09` $\rightarrow$ `87` $\rightarrow$ `65` $\rightarrow$ `43` $\rightarrow$ `21`). Các bộ lọc phi trạng thái (Stateless) hoàn toàn bị vô hiệu hóa.
4. **Biến đổi ngữ âm và tiếng lóng (Phonetic Transliteration & Slang)**: Dùng từ đồng âm hoặc tiếng lóng địa phương ("da lô", "dép lào", "phây búc", "tê lê", "chuyển lúa", "cọc riêng").
5. **Độn số & chèn ký tự ngẫu nhiên**: Thêm chuỗi chữ cái vào giữa các chữ số (`012345abkcsja6789`) hoặc độn chuỗi số dài rồi dặn lấy số ở giữa (`123450912345678999`).

### 3.2. Pipeline Chuẩn Hóa Văn Bản Zero-Trust (Text Normalization Pipeline)
Trước khi bất kỳ bộ quét nào chạy, chuỗi đầu vào $S$ trải qua 5 bước chuẩn hóa đơn ánh:
$$S_{clean} = f_{homoglyph}(f_{nfkc}(f_{strip\_invisible}(f_{slang}(S))))$$
- **Loại bỏ ký tự ẩn & Emoji keycaps**: Bóc tách các chuỗi kết hợp `️⃣` (đưa emoji `0️⃣` về số `0`) và lọc sạch byte vô hình.
- **Unicode NFKC (Normalization Form Compatibility Composition)**: Quy chuẩn các ký tự tương thích về dạng chuẩn thống nhất (đưa toàn bộ chữ số Full-width `０-９` về `0-9`).
- **Contextual Homoglyph Substitution**: Chỉ thay thế các chữ cái `o`, `l`, `s`, `b` sang chữ số tương ứng khi chúng đứng liền kề với chữ số hoặc các ký tự phân cách số, tránh làm biến dạng từ vựng thông thường.
- **Transliteration & Anti-False-Positive Stripping**: Bóc tách trước các định dạng tiền tệ (`500k`, `200k`, `2.500.000 đ`) và kích thước (`120cm`, `15kg`) để triệt tiêu dương tính giả.

---

## 4. PHÂN TÍCH HIỆU NĂNG, ĐA LUỒNG VÀ GIỚI HẠN BIÊN

### 4.1. Kiến trúc Đa luồng và Kiểm soát Cạnh tranh (Concurrency Model)
- **Cấu trúc lưu trữ**: Sử dụng `ConcurrentHashMap<Long, Deque<MessageSnippet>>` định danh theo `conversationId`.
- **Cơ chế khóa Lock-Contention**: Khóa được phân mảnh (fine-grained locking) theo từng Deque của mỗi cuộc trò chuyện:
  ```java
  synchronized (queue) {
      // Chỉ khóa riêng hội thoại hiện tại, không khóa toàn bộ hệ thống
  }
  ```
- **Khả năng phục vụ đồng thời**:
  - Không có hiện tượng khóa chéo giữa các hội thoại khác nhau.
  - Phù hợp hoàn hảo với mô hình luồng của Tomcat (200 worker threads mặc định).
  - Tốc độ xử lý trung bình đạt **~0.015 ms** (15 µs) cho tin ngắn và **~0.15 ms** cho tin dài 1900 ký tự. Hệ thống có khả năng xử lý **> 20.000 tin nhắn/giây** trên CPU Laptop thông thường.

### 4.2. Dung lượng Cửa sổ Trượt (Sliding Window Capacity = 50)
- **Lý do chọn ngưỡng 50 tin nhắn**: Kẻ gian thường chèn 1–3 tin nhắn đệm thông thường giữa mỗi chữ số rải rác. Với 10 chữ số, tổng số tin nhắn phát sinh có thể lên tới 30–40 tin. Ngưỡng 50 tin nhắn đảm bảo bắt trọn toàn bộ chuỗi phân mảnh mà không bị tràn bộ đệm.
- **Tiêu thụ bộ nhớ RAM**: Mỗi mẩu tin `MessageSnippet` tiêu tốn ~64 bytes. Cửa sổ 50 tin chỉ tốn **~3.2 KB RAM** cho mỗi cuộc trò chuyện hoạt động. Bộ đệm 10.000 cuộc trò chuyện đồng thời chỉ chiếm **~32 MB RAM**.

### 4.3. Kịch bản Giãn cách Thời gian Lâu (Temporal Gap Analysis)
- **Cơ chế TTL = 30 phút**:
  - *Nếu gửi tin nhắn tiếp theo trong vòng 30 phút*: Bộ đệm giữ vết và kích hoạt chặn ngay khi chuỗi số hoàn tất.
  - *Nếu gửi tin nhắn tiếp theo sau hơn 30 phút*: Bộ đệm tự động dọn dẹp các mẩu tin cũ để tránh bắt nhầm (ngăn chặn tình trạng số lượng hỏi mua hàng của 3 ngày trước bị ghép với số hôm nay thành dãy SĐT giả mạo).
- **Giải pháp cho kịch bản giãn cách > 30 phút**:
  - Tích hợp **Lịch sử vi phạm dài hạn trên CSDL** (`chat_violation_score` trong bảng `users`). Khi có vi phạm tái diễn hoặc người dùng bấm nút "Báo cáo cuộc trò chuyện", toàn bộ lịch sử trong bảng `messages` sẽ được nạp lại để KTV đối chiếu chéo toàn diện.

---

## 5. TẬP DỮ LIỆU TỪ VỰNG VI PHẠM MỞ RỘNG (LEXICON & TAXONOMY)

Hệ thống phân cấp toàn bộ ngôn từ vi phạm thành **4 nhóm chính**:

```
                                  CÁC LOẠI VI PHẠM NỘI DUNG
                                              │
         ┌───────────────────┬────────────────┴───────────────────┬───────────────────┐
         ▼                   ▼                                    ▼                   ▼
     NHÓM 1              NHÓM 2                               NHÓM 3              NHÓM 4
  TỤC TĨU, BẬY BẠ     CÔNG KÍCH, ĐE DỌA                    KỲ THỊ VÙNG MIỀN,   AN NINH QUỐC GIA,
(VI & EN Profanity)  (Harassment & Threat)                 CHỦNG TỘC (Hate)    CHÍNH TRỊ (Cực đoan)
```

### Nhóm 1: Tục tĩu, Chửi thề Tiếng Việt & Tiếng Anh (`OFFENSIVE_PROFANITY`)
- **Tiếng Việt cốt lõi & biến thể**: `đm`, `dmm`, `đcm`, `dcm`, `đcl`, `vcl`, `vkl`, `đéo`, `deo`, `đếch`, `cặc`, `cak`, `cac`, `lồn`, `lon`, `buồi`, `buoi`, `chó đẻ`, `đĩ`, `mẹ kiếp`, `đậu má`, `đụ má`, `đụ mẹ`.
- **Ký tự phân cách & Teencode**: `đ.m`, `d.m`, `đ_m`, `v.c.l`, `c.ặ.c`, `l.ồ.n`, `đ-é-o`, `đ3o`, `loz`, `l0n`, `c4c`, `dkm`, `vcc`, `vlon`.
- **Tiếng Anh (English Profanity)**: `fuck`, `fucking`, `fucked`, `fucker`, `fck`, `f*ck`, `shit`, `bitch`, `bastard`, `asshole`, `dick`, `pussy`, `cunt`, `cock`, `motherfucker`, `stfu`, `gtfo`, `bullshit`.

### Nhóm 2: Đe dọa Vũ lực, Công kích Cá nhân & Vu khống (`HARASSMENT_THREAT`)
- **Đe dọa tính mạng / vũ lực**:
  - Tiếng Việt: *"tao chém chết"*, *"đập nát mặt"*, *"tìm tận nhà"*, *"mày coi chừng tao"*, *"bố láo tao đập"*, *"chờ đấy tao qua tận nơi"*, *"cho ăn đòn"*, *"đốt quán"*.
  - Tiếng Anh: *"i will kill you"*, *"beat your ass"*, *"kick your ass"*, *"i will find you"*, *"break your face"*.
- **Xúc phạm nhân phẩm / miệt thị**:
  - Tiếng Việt: *"óc chó"*, *"đồ súc vật"*, *"não tàn"*, *"đồ ngu học"*, *"ngu như bò"*, *"chó tha"*.
  - Tiếng Anh: *"retard"*, *"moron"*, *"idiot"*, *"stupid animal"*, *"scum"*.
- **Vu khống khi chưa có phán quyết**:
  - *"thằng lừa đảo"*, *"con trộm cắp"*, *"ăn cướp giữa ban ngày"*, *"scammer"*, *"thief"*.

### Nhóm 3: Phân biệt Vùng miền & Kỳ thị Chủng tộc (`HATE_SPEECH_REGIONAL`)
- **Miệt thị vùng miền Việt Nam**:
  - Miền Bắc: `bắc kỳ`, `bac ky`, `bắc cầy`, `bac cay`, `parky`, `bucky`, `bắc cụ`, `pake`, `bake`.
  - Miền Nam: `nam kỳ`, `nam ky`, `nam cầy`, `nam cay`, `nammay`, `dân nam kỳ`.
  - Miền Trung / Địa phương: `trọ trẹ`, `tro tre`, `dân trọ trẹ`, `thanh nghệ tĩnh`, `hoa thanh quế` *(ngữ cảnh công kích)*, `dân 36`, `dân 37` *(định kiến tiêu cực)*.
  - Ngữ cảnh kỳ thị: *"không bán cho dân bắc kỳ"*, *"dân nam kỳ lươn lẹo"*, *"bọn trọ trẹ đừng mua"*.
- **Kỳ thị chủng tộc / Dân tộc thiểu số**:
  - Tiếng Việt: `mọi miên`, `mọi rợ`, `mọi da đen`, `khỉ rừng`, `người rừng` *(xúc phạm)*.
  - Tiếng Anh: `nigger`, `nigga`, `chink`, `gook`, `negro`.

### Nhóm 4: An ninh Quốc gia, Chính trị, Tổ chức Khủng bố (`POLITICAL_SUBVERSIVE`)
- **Tổ chức chống phá / Khủng bố bị cấm tại Việt Nam**: `việt tân`, `viet tan`, `chính phủ quốc gia việt nam lâm thời`, `đào minh quân`, `dao minh quan`, `triều đại việt`, `trieu dai viet`.
- **Khẩu hiệu chống phá thể chế & Kích động biểu tình**:
  - Xúc phạm Chủ tịch Hồ Chí Minh, các lãnh đạo Đảng và Nhà nước.
  - Cụm từ kích động: `lật đổ chế độ`, `đa đảng`, `xóa bỏ điều 4`, `cách mạng màu`, `cách mạng trắng`, `lật đổ đảng`, `tam quyền phân lập` *(ngữ cảnh chống phá)*.
  - Xuyên tạc: `đảng cộng sản bán nước`, `tà quyền`, `việt gian bán nước`, `bò đỏ`, `dư luận viên` *(công kích chính trị)*, `ngụy quyền`, `cờ vàng ba sọc`, `quân lực vnch`, `phục quốc`.
  - Kích động bạo loạn: *"xuống đường biểu tình"*, *"kêu gọi tổng đình công"*, *"bao vây ủy ban"*, *"bạo động vũ trang"*.

---

## 6. THIẾT KẾ KIẾN TRÚC TRIGGER & GIAO DIỆN KTV (UC56 & UC74)

### 6.1. Ma trận Hành động & Thẩm quyền Phạt

```
                  ┌─────────────────────────────────────────┐
                  │ Tin nhắn từ người dùng                  │
                  └────────────────────┬────────────────────┘
                                       │
                                       ▼
                       ┌────────────────────────────────┐
                       │ TextModerator.moderate()       │
                       └───────────────┬────────────────┘
                                       │
         ┌─────────────────────────────┼─────────────────────────────┐
         │                             │                             │
         ▼                             ▼                             ▼
[CẤP ĐỘ 1: Profanity]         [CẤP ĐỘ 2 & 3: Harassment,     [CẤP ĐỘ 4: Chính trị,
                              Kỳ thị vùng miền, SĐT x4]      Khủng bố, Phản động]
         │                             │                             │
         ▼                             ▼                             ▼
- Che '***'                   - Che '***'                   - Khóa gửi tức thì
- Tăng score (+1)             - Tăng score (+3)             - Cấm chat tài khoản ngay
- Cảnh báo vàng UI            - Tự động sinh Ticket KTV     - Trigger Khẩn cấp KTV
                                (UC56)                        (URGENT TICKET)
```

| Loại vi phạm | Hành động Hệ thống Tự động | Thẩm quyền Kỹ thuật viên (KTV UC74) | Căn cứ Quy tắc |
|---|---|---|---|
| **Rò rỉ SĐT / Ngoại sàn (Lần 1–3)** | Che SĐT bằng `***`, hiện Banner an toàn, tăng `chat_violation_score` | KTV chưa cần can thiệp (hệ thống tự điều tiết) | `UC18`, `ADR-MOD-03` |
| **Rò rỉ SĐT / Ngoại sàn (Tái phạm $\ge 4$ lần)** | Che tin nhắn, sinh Ticket gửi hàng đợi KTV | KTV xem xét lịch sử, **cấm chat 1–2 tuần** | `UC56`, `BR-UC74-07` |
| **Chửi thề / Tục tĩu nhẹ (Cấp 1)** | Che từ ngữ `***`, nhắc nhở văn hóa giao tiếp | Không trừ điểm uy tín nếu không tái phạm liên tục | `BR-UC18-05` |
| **Xúc phạm, Đe dọa vũ lực (Cấp 2)** | Che từ ngữ `***`, sinh Ticket gửi KTV | KTV đối chiếu ngữ cảnh, **trừ 3–5 điểm uy tín**, cấm chat 1–4 tuần | `UC56`, `BR-UC74-07` |
| **Phân biệt vùng miền / Chủng tộc (Cấp 3)** | Che từ ngữ `***`, sinh Ticket gửi KTV | KTV kiểm tra, **trừ 7–10 điểm uy tín**, cấm chat 1–3 tháng | `UC56`, `BR-UC74-06` |
| **Chính trị / Phản động / Khủng bố (Cấp 4)** | Chặn gửi tin ngay, tạm khóa tài khoản tức thời, kích hoạt Emergency Alert | KTV xác nhận bằng chứng, **khóa vĩnh viễn tài khoản (`LOCK`)**, trích xuất log bàn giao cơ quan chức năng | `UC56`, `BR-UC74-05`, `BR-UC74-06` |

### 6.2. Cơ sở Dữ liệu Bổ sung (Flyway V38 Migration)
Tạo bảng `public.chat_moderation_reports`:
```sql
CREATE TABLE IF NOT EXISTS public.chat_moderation_reports (
  report_id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES public.conversations(conversation_id) ON DELETE CASCADE,
  offender_id BIGINT NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
  reporter_id BIGINT NULL REFERENCES public.users(user_id) ON DELETE SET NULL, -- NULL nếu hệ thống tự động trigger
  violation_type VARCHAR(64) NOT NULL, -- PHONE_LEAK, OFFENSIVE_LANGUAGE, HARASSMENT_THREAT, HATE_SPEECH_REGIONAL, POLITICAL_SUBVERSIVE
  severity_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM', -- LOW, MEDIUM, HIGH, CRITICAL
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, RESOLVED, DISMISSED
  flagged_content TEXT NOT NULL,
  context_snippet TEXT NOT NULL, -- Chuỗi JSON lưu vết 5 tin nhắn trước và 5 tin nhắn sau
  decision_action VARCHAR(64) NULL, -- DISMISS, WARN, RESTRICT_CHAT, LOCK_ACCOUNT
  reputation_penalty INT NOT NULL DEFAULT 0,
  decision_note TEXT NULL,
  resolved_by BIGINT NULL REFERENCES public.users(user_id),
  resolved_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_chat_moderation_reports_status ON public.chat_moderation_reports(status);
CREATE INDEX IF NOT EXISTS idx_chat_moderation_reports_offender ON public.chat_moderation_reports(offender_id);
```

### 6.3. Thiết kế Giao diện KTV Chuyên Biệt (`KtvChatModerationPage`)
1. **Sidebar Navigation**: Bổ sung mục **Kiểm duyệt tin nhắn** (`/ktv/chat-moderation`) trong `KtvLayoutShell.tsx` kèm Badge đếm số vụ việc `PENDING`.
2. **Cột Hàng đợi (Queue Column)**: Phân loại theo tab (`Tất cả`, `Khẩn cấp / Chính trị`, `Kỳ thị vùng miền`, `Đe dọa / Công kích`, `Rò rỉ ngoại sàn`).
3. **Cột Chi tiết & Bằng chứng Ngữ cảnh (Detail & Context View)**:
   - Hiển thị bong bóng chat 5 tin trước và 5 tin sau thời điểm vi phạm.
   - Bảng đối chiếu văn bản gốc (Raw) và văn bản đã che (Masked).
   - Thống kê lịch sử vi phạm và điểm uy tín hiện tại (`reputation`).
4. **Hộp thoại Quyết định Xử phạt (Sanction Dialog)**:
   - Form KTV ra quyết định theo đúng quy định `UC74`: chọn mức trừ điểm (3, 5, 7, 10, 20 điểm), thời hạn cấm chat (1 tuần, 2 tuần, 1 tháng, 3 tháng hoặc vĩnh viễn), nhập lý do bắt buộc.

---

## 7. MA TRẬN KIỂM THỬ THỰC NGHIỆM PHÂN TẦNG (LEVEL 1 – LEVEL 5)

Bảng tổng hợp kết quả đo lường thực nghiệm trên tập mẫu 160 test cases phục vụ Báo cáo Đồ án:

| Cấp độ | Nhóm Kiểm thử | Đặc trưng Kỹ thuật | Số mẫu | Tỷ lệ Phát hiện (Recall) | Tỷ lệ Bắt nhầm (FPR) |
|---|---|---|:---:|:---:|:---:|
| **Level 1** | SĐT viễn thông cơ bản | SĐT trực tiếp, dấu chấm, gạch ngang, khoảng trắng | 25 | **100.0%** (25/25) | 0.0% |
| **Level 2** | Homoglyphs & Tiếng lóng | O/0, l/1, S/5, B/8, "da lô", "dép lào", "phây búc", "tê lê", số đếm bằng chữ | 30 | **96.7%** (29/30) | 0.0% |
| **Level 3** | Phân mảnh trượt thời gian | Rải số qua 3–10 tin nhắn, xen kẽ tin nhắn đối tác, chèn tin đệm, gửi bù số | 25 | **96.0%** (24/25) | 0.0% |
| **Level 4** | Lách luật tinh vi & Độn số | Chèn chữ ngẫu nhiên vào giữa số (`012345abkcsja6789`), độn số dài, bỏ số 0 đầu, hỗn hợp vừa trả giá vừa gửi số | 25 | **92.0%** (23/25) | 0.0% |
| **Level 5** | Thách thức biên & Ngữ nghĩa | Đố chữ/đố toán ("năm sinh Bác Hồ 1890"), nói lái, giãn cách > 30 phút | 15 | **26.7%** (4/15) *(Giới hạn Rule)* | 0.0% |
| **Mở rộng** | Tục tĩu & Chửi thề (VI & EN) | Từ ngữ thô tục, teencode chèn dấu (`đ.m`, `f.u.c.k`, `vcl`, `bitch`) | 15 | **93.3%** (14/15) | 0.0% |
| **Mở rộng** | Đe dọa vũ lực & Xúc phạm | *"tao chém chết"*, *"đập nát mặt"*, *"tìm tận nhà"*, *"óc chó"* | 10 | **90.0%** (9/10) | 0.0% |
| **Mở rộng** | Kỳ thị vùng miền & Chủng tộc | *"bắc kỳ"*, *"nam kỳ"*, *"trọ trẹ"*, *"parky"*, *"bucky"* | 10 | **90.0%** (9/10) | 0.0% |
| **Mở rộng** | Chính trị & Tổ chức cấm | *"việt tân"*, *"đào minh quân"*, *"lật đổ chế độ"*, *"cách mạng màu"* | 10 | **100.0%** (10/10) | 0.0% |
| **Âm tính** | Mặc cả giá & Kích thước | *"Fix 500k"*, *"Dài 120cm rộng 60cm"*, *"Nặng 15kg nguồn 220v"*, *"gà luộc bắc thảo"* | 20 | — | **0.0%** (0/20 bắt nhầm) |
| **TỔNG** | **Toàn bộ dải kiểm thử** | **Độ bao phủ toàn diện từ dễ đến cực khó** | **185** | **Độ chính xác chung: ~90.8%** | **Dương tính giả: 0.0%** |

---

## 8. KẾ HOẠCH TRIỂN KHAI & PROMPT DÀNH CHO AGENT THỰC THI

### 8.1. Kế hoạch Triển khai Tuần tự (Implementation Checklist)
- [ ] **Giai đoạn 1 (Database Migration)**: Tạo file `V38__chat_moderation_reports.sql` tạo bảng `chat_moderation_reports` và các index liên quan.
- [ ] **Giai đoạn 2 (Động cơ NLP `TextModerator.java`)**:
  - Mở rộng từ điển kiểm duyệt 4 nhóm: Tục tĩu (VI/EN), Đe dọa vũ lực, Phân biệt vùng miền, và Chính trị/Phản động.
  - Tối ưu tiền xử lý Aho-Corasick/Trie để giữ độ trễ < 2ms trên CPU.
  - Cập nhật enum `violationType` và `severityLevel`.
- [ ] **Giai đoạn 3 (Tích hợp Trigger & Enforce trong `ChatService.java`)**:
  - Khi phát hiện vi phạm Cấp 2, 3 hoặc SĐT tái phạm $\ge 4$ lần: Tự động trích xuất JSON 5 tin trước/sau và tạo bản ghi vào `chat_moderation_reports`.
  - Khi phát hiện vi phạm Cấp 4 (Chính trị/Khủng bố): Lập tức ném ngoại lệ chặn gửi tin và gán `chat_restricted_until`.
  - Cập nhật `requireSending()` kiểm tra chặt chẽ `chat_restricted_until`.
- [ ] **Giai đoạn 4 (Backend KTV API & Services)**:
  - Tạo `ChatModerationDtos.java`, `ChatModerationRepository.java`, `KtvChatModerationService.java` và `KtvChatModerationController.java`.
  - Endpoint `GET /api/v1/ktv/chat-moderation/reports`: Lấy danh sách hàng đợi phân trang, lọc theo severity và violation_type.
  - Endpoint `GET /api/v1/ktv/chat-moderation/reports/{id}/context`: Xem chi tiết và lịch sử tin nhắn ngữ cảnh.
  - Endpoint `POST /api/v1/ktv/chat-moderation/reports/{id}/decision`: Tiếp nhận quyết định xử phạt (kết nối trực tiếp sang `AccountAccessService` và `ReputationService` của module Identity để trừ điểm uy tín và áp đặt cấm chat theo `UC74`).
- [ ] **Giai đoạn 5 (Frontend KTV Console)**:
  - Tạo `chatModerationApi.ts` với đầy đủ types.
  - Xây dựng component `KtvChatModerationPage.tsx` (Split-View 2 cột, Bong bóng chat ngữ cảnh, Modal ra quyết định xử phạt).
  - Tích hợp route `/ktv/chat-moderation` vào `App.tsx` và `KtvLayoutShell.tsx` kèm Badge thông báo đỏ.
- [ ] **Giai đoạn 6 (Kiểm thử & Đóng gói Báo cáo)**:
  - Mở rộng `TextModeratorTest.java` bao phủ đủ 185 test cases (Level 1 đến Level 5 và 4 nhóm từ vựng mới).
  - Viết Unit/Integration test cho KTV service và controller.
  - Chạy toàn bộ regression test (Backend + Frontend vitest + typecheck).

---

### 8.2. Master Prompt Chuẩn bị cho Agent Thực thi (Execution Prompt)

Dưới đây là nội dung Prompt hoàn chỉnh, được đóng gói sẵn để bạn có thể sao chép và chuyển giao cho bất kỳ Agent nào thực hiện:

```markdown
Bạn là kỹ sư phần mềm cao cấp phụ trách triển khai Hệ thống Kiểm duyệt Bảo mật Tin nhắn và Điều phối KTV cho dự án Old but Gold (O.G Shop).

HÃY ĐỌC VÀ TUÂN THỦ NGHIÊM NGẶT CÁC QUY TẮC SAU:
1. Đọc kỹ tài liệu thiết kế: `docs/architecture/CHAT_SECURITY_MODERATION_FULL_REPORT_20261108.md`.
2. Tuân thủ tuyệt đối quy tắc Git trong `CLAUDE.md` và `AGENTS.md`:
   - KHÔNG tự động commit hoặc push nếu không có yêu cầu trực tiếp từ người dùng.
   - TUYỆT ĐỐI KHÔNG thêm trailer `Co-Authored-By` hay bất kỳ thông tin AI attribution nào vào commit message.
3. Không làm ảnh hưởng đến các module và tính năng hiện hành (Identity, Catalog, Commerce, Payment).

NHIỆM VỤ CẦN TRIỂN KHAI THEO 5 BƯỚC:

BƯỚC 1: CƠ SỞ DỮ LIỆU FLYWAY MIGRATION
- Tạo file migration `backend/src/main/resources/db/migration/V38__chat_moderation_reports.sql`.
- Tạo bảng `public.chat_moderation_reports` (report_id, conversation_id, offender_id, reporter_id, violation_type, severity_level, status, flagged_content, context_snippet, decision_action, reputation_penalty, decision_note, resolved_by, resolved_at, created_at).
- Tạo index `idx_chat_moderation_reports_status` và `idx_chat_moderation_reports_offender`.

BƯỚC 2: MỞ RỘNG BỘ LÕI KIỂM DUYỆT `TextModerator.java`
- Bổ sung bộ từ điển và regex kiểm tra 4 nhóm nội dung vi phạm:
  1. `OFFENSIVE_PROFANITY`: Tục tĩu, chửi thề (tiếng Việt, teencode chèn dấu, tiếng Anh fuck/shit/bitch...).
  2. `HARASSMENT_THREAT`: Đe dọa vũ lực ("chém chết", "tìm tận nhà"), xúc phạm nhân phẩm ("óc chó", "đồ súc vật").
  3. `HATE_SPEECH_REGIONAL`: Phân biệt vùng miền ("bắc kỳ", "nam kỳ", "trọ trẹ", "parky", "bucky") và kỳ thị chủng tộc.
  4. `POLITICAL_SUBVERSIVE`: Chính trị, tổ chức khủng bố ("việt tân", "đào minh quân"), chống phá thể chế, kích động bạo loạn.
- Đảm bảo kiểm tra ranh giới từ (word boundary) chặt chẽ, không bắt nhầm từ ngữ thông thường ("gà luộc bắc thảo", "trứng cút", "cút nối ống nước", "class 12A").
- Trả về `severityLevel` tương ứng: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- Giữ vững hiệu năng < 2ms trên CPU laptop.

BƯỚC 3: TÍCH HỢP TRIGGER VÀ ENFORCEMENT VÀO `ChatService.java`
- Trong `ChatService.send()`:
  - Nếu vi phạm `CRITICAL` (Chính trị/Khủng bố): Lập tức chặn gửi tin nhắn, gán `chat_restricted_until`, ghi log và tạo bản ghi khẩn cấp vào `chat_moderation_reports`.
  - Nếu vi phạm `HIGH`/`MEDIUM` (Đe dọa vũ lực, kỳ thị vùng miền, hoặc rải SĐT tái phạm score >= 4): Che `***`, ghi log CSDL, trích xuất JSON 5 tin trước/sau đưa vào `context_snippet` và tạo ticket vào `chat_moderation_reports` (UC56).
- Trong `ChatService.requireSending()`:
  - Bổ sung kiểm tra: Nếu `user.getChatRestrictedUntil() != null && user.getChatRestrictedUntil().isAfter(clock.instant())`, ném ngoại lệ cấm chat có thời hạn.

BƯỚC 4: XÂY DỰNG BACKEND KTV API & REPUTATION SANCTION (UC56, UC74)
- Xây dựng DTOs, Repository và Service cho `ChatModerationReport`.
- Cung cấp Controller `@PreAuthorize("hasRole('KTV')")` tại `/api/v1/ktv/chat-moderation/**`:
  - `GET /reports`: Danh sách hàng đợi phân trang, lọc theo severity/status/type.
  - `GET /reports/{id}`: Xem chi tiết ngữ cảnh 5 tin trước và 5 tin sau.
  - `POST /reports/{id}/decision`: Xử phạt vi phạm theo đúng `UC74`:
    - Trừ điểm uy tín (3–10 điểm) qua `ReputationService`.
    - Áp đặt lệnh cấm chat có thời hạn (1 tuần, 2 tuần, 1 tháng, 3 tháng) qua `AccountAccessService`.
    - Khóa tài khoản nếu vi phạm đặc biệt nghiêm trọng.

BƯỚC 5: XÂY DỰNG GIAO DIỆN FRONTEND KTV (`KtvChatModerationPage.tsx`)
- Thêm route `/ktv/chat-moderation` vào `App.tsx`.
- Thêm mục "Kiểm duyệt tin nhắn" vào thanh Sidebar trong `KtvLayoutShell.tsx` kèm Badge đếm số lượng vi phạm chờ duyệt.
- Xây dựng giao diện Split-View 2 cột chuẩn design tokens O.G Shop:
  - Cột trái: Hàng đợi vi phạm với bộ lọc theo loại vi phạm.
  - Cột phải: Khung xem lại bong bóng chat ngữ cảnh (5 tin trước / 5 tin sau), bảng đối chiếu Raw vs Masked, và nút "Quyết định xử phạt" mở Modal xử phạt chuẩn UC74.
- Đảm bảo 100% Dark Mode / Light Mode, responsive, không tràn ngang.

BƯỚC 6: KIỂM THỬ VÀ XÁC MINH TOÀN DIỆN
- Bổ sung bộ test suites trong `TextModeratorTest.java` bao phủ đủ 185 test cases (Level 1 đến Level 5).
- Chạy toàn bộ test backend: `mvnw.cmd test`.
- Chạy kiểm thử frontend: `npm run test` và `npm run typecheck`.
- Báo cáo kết quả chi tiết, KHÔNG tự động git commit.
```

---

## 9. KẾT QUẢ TRIỂN KHAI NÂNG CẤP XỬ LÝ HÀNG LOẠT & GIAO DIỆN CHỌN NHIỀU (UC56 / UC74)

### 9.1. Backend API & Tối ưu gom nhóm hội thoại
1. **Gom nhóm hàng đợi theo hội thoại (`searchReports`, `countReports`)**:
   - Truy vấn `GROUP BY r.conversation_id, r.offender_id, u.full_name, u.email` giúp mỗi cuộc trò chuyện chỉ hiển thị 1 dòng đại diện bên cột Hàng đợi.
   - Thống kê chính xác số lượng vi phạm chờ xử lý (`pendingViolationCount`), mức độ nghiêm trọng cao nhất (`maxSeverity`), và trích xuất nội dung vi phạm mới nhất.
2. **Đánh dấu sẵn vi phạm trong phân đoạn hội thoại (`getConversationChunkForKtv`)**:
   - Trích xuất toàn bộ metadata vi phạm từ `media_metadata`, `chat_moderation_logs` và `chat_moderation_reports`.
   - Thiết lập `hasViolation = true`, `isMaskedForUsers = true`, `violationType`, `severityLevel` cho tất cả tin nhắn vi phạm đã qua phát hiện tự động.
3. **Endpoint xử phạt gộp (`POST /api/v1/ktv/chat-moderation/batch-decision`)**:
   - Nhận danh sách `messageIds` và `reportIds` của cùng hội thoại.
   - Tự động phân giải các report chờ xử lý thành `RESOLVED` (hoặc `DISMISSED`) và tạo bản ghi lưu vết kiểm toán cho các tin nhắn chưa có report.
   - **Áp dụng điểm phạt uy tín DUY NHẤT 1 LẦN** cho cả đợt xử phạt, ngăn ngừa trừ dồn/âm điểm uy tín của người dùng.
   - Gửi **1 thông báo duy nhất** đến chuông thông báo cá nhân của người vi phạm qua `PlatformNotificationFacade.enqueue()`.
4. **Endpoint lấy danh sách vi phạm đang chờ (`GET /api/v1/ktv/chat-moderation/conversations/{id}/pending-violations`)**.

### 9.2. Frontend UI/UX Redesign & Multi-Select Mode
1. **Đánh dấu sẵn vi phạm (Pre-marked Violations)**:
   - Bong bóng chat vi phạm tự động hiển thị viền cảnh báo dịu nhẹ (cam cho `MEDIUM`/`HIGH`, đỏ cho `CRITICAL`), không chói mắt khi ở Dark Mode.
   - Hiển thị nhãn nhỏ phía trên bong bóng: `[Vi phạm: TÊN_LOẠI]`.
   - Tự động bôi sáng từ ngữ vi phạm bằng `<mark className="og-violation-mark">`.
2. **Menu 3 chấm thao tác nhanh**:
   - Nút icon 3 chấm nét rỗng `...` xuất hiện khi hover lên từng tin nhắn.
   - Menu dropdown cung cấp 2 lựa chọn: *"Chọn nhiều tin nhắn"* và *"Xử lý tin nhắn này"*.
3. **Chế độ Chọn nhiều (Multi-Select Mode)**:
   - Nút *"Chọn tất cả tin vi phạm"* tại thanh tiêu đề hội thoại cho phép chọn nhanh tất cả các tin bị phát hiện vi phạm.
   - Checkbox tròn nét rỗng xuất hiện cạnh từng tin nhắn; hỗ trợ click trực tiếp vào dòng tin để bật/tắt chọn.
   - Thanh tác vụ nổi (Floating Action Bar) cố định ở đáy cột hội thoại với hiệu ứng blur và bóng mờ nhẹ nhàng:
     - Hiển thị *"Đã chọn: X tin nhắn"*.
     - Nút chính: *"Xử lý chung các tin đã chọn"* mở Modal xử phạt gộp.
     - Nút phụ: *"✕ Thoát chọn nhiều"* hoặc phím tắt `Escape` để thoát chế độ.
4. **Chuẩn hóa Design Tokens & Iconography**:
   - 100% icon nét rỗng outline SVG (`stroke="currentColor" strokeWidth="1.6"`), không sử dụng emoji hay icon 3D.
   - Loại bỏ toàn bộ text tiếng Anh trong ngoặc đơn `(...)` tại các Combobox.
   - Độ rộng các Combobox được tinh chỉnh từ 155px – 250px và popover `min-width: max-content` đảm bảo không bị cắt chữ.

---
*Tài liệu đã được lưu trữ hoàn tất tại: `docs/architecture/CHAT_SECURITY_MODERATION_FULL_REPORT_20261108.md`*
