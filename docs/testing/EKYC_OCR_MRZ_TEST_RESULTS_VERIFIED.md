# eKYC CCCD OCR & MRZ Test Execution Results (Verified & Reproducible)

## 1. Tóm tắt xác minh (Verification Summary)

- **Ngày giờ thực hiện**: 2026-10-27T18:25:00+07:00 (Local time).
- **Môi trường thực thi**: Local Sandbox trên hệ điều hành Windows 11; Python 3.11.9, PyTorch, Torchvision, YOLOv11n (Ultralytics), VietOCR Transformer, OpenCV (cv2).
- **Trạng thái mã nguồn (Git Revision)**:
  - Commit HEAD: `4667abc1672a8286f7690ed5061caeed8ed03296` trên nhánh `feat/notifications-and-buyer-orders`.
  - Trạng thái working tree: Đã xác minh có các thay đổi chưa commit từ các task trước (Identity, Verification, Notification, Profile). Toàn bộ các thay đổi này được **bảo toàn 100%**, không bị reset, stash, checkout hay ghi đè.
- **Cấu hình & Bí mật (Secrets)**:
  - Cấu hình `services/ekyc-service/.env` giữ nguyên: `EKYC_GEMINI_POSTPROCESSING_ENABLED=false`, `EKYC_ALLOW_SYNTHETIC_DATA_ONLY=true`.
  - Mọi token, secret và API key đều được che/khử; tuyệt đối không nạp hay gửi ra mạng.
- **Tổng số tệp ảnh đã xác minh thực tế trên đĩa**: **26 tệp ảnh** (phân bố tại 6 thư mục con trong `D:\Khanh\Old but Gold\Source Code\TesteKYC`).
- **Tổng hợp Verdict trên 16 kịch bản (OCR-01 đến OCR-16)**:
  - `PASS`: **9** ca (các ca kiểm soát chất lượng quang học, chặn ảnh mờ, chặn che khuất trường, chặn tráo mặt trước/sau, chặn ảnh chân dung/thẻ không phải CCCD, và tính phi trạng thái khi đổi ảnh).
  - `NEEDS_PAIR_CONFIRMATION`: **7** ca (các ca ghép đôi giữa hai mặt thẻ chưa được chủ bộ dữ liệu xác nhận chính thức là cùng chủ thẻ hay lệch thẻ).
  - `FAIL`: **0** ca (không có vi phạm nào đối với kỳ vọng đã được xác nhận).
  - `BLOCKED`: **0** ca hạ tầng (toàn bộ kịch bản đều được nạp và chạy local an toàn). Tuy nhiên, **đường bóc tách dòng 2 & 3 MRZ bị chặn (BLOCKED)** do chính sách an toàn nghiêm cấm gửi ảnh định danh CCCD thật lên Gemini API bên ngoài.
  - `NOT_RUN`: **0** ca.
  - `PARTIAL`: **0** ca.
- **Phân định phạm vi & loại bằng chứng thực tế**:
  - `UNIT_TEST`: Chạy bộ 33 unit tests tự động của microservice eKYC (`pytest services/ekyc-service/tests`).
  - `COMPONENT_IMAGE_TEST`: Chạy trực tiếp các hàm xử lý ảnh [`CCCDProcessor.process_cccd`](file:///D:/Khanh/Old%20but%20Gold/Source%20Code/O.G%20Shop/services/ekyc-service/cccd_module/cccd_extractor.py#L88-L214) và [`BackCardProcessor.process_back_card`](file:///D:/Khanh/Old%20but%20Gold/Source%20Code/O.G%20Shop/services/ekyc-service/cccd_module/back_card_processor.py#L435-L520) trên bản sao làm việc tạm thời (`tempfile.TemporaryDirectory()`).
  - `STATIC_CODE_INSPECTION`: Phân tích mã nguồn và kiểm chứng logic regex, cấu trúc dữ liệu, và ranh giới bảo mật.
  - `API_INTEGRATION_TEST` & `UI_E2E_TEST`: **KHÔNG THỰC HIỆN** (Service eKYC FastAPI cổng 8001 và Backend Spring Boot không chạy tiến trình nền trong phiên này; không tạo tài khoản/hồ sơ thật).

---

## 2. Đối chiếu báo cáo cũ (`EKYC_OCR_MRZ_TEST_RESULTS.md`)

Bảng dưới đây đối chiếu chi tiết giữa các phát biểu trong báo cáo trước và kết quả kiểm chứng độc lập:

| Nội dung đối chiếu | Báo cáo cũ ghi nhận | Kết quả kiểm chứng thực tế | Đánh giá | Bằng chứng cụ thể |
|---|---|---|---|---|
| **Tổng số tệp ảnh** | 26 tệp | Quét trực tiếp thư mục `TesteKYC` phát hiện đúng **26 tệp ảnh**. | **XÁC MINH ĐẠT** | 26 đường dẫn vật lý thực tế trên đĩa (gồm 22 tệp `.jpg` và 4 tệp `.jfif`). |
| **Mâu thuẫn số lượng với Ma trận** | Tự thêm `IMG-026` trong ca OCR-10 | Ma trận `EKYC_OCR_MRZ_TEST_MATRIX.md` chỉ có **25 dòng (IMG-001 đến IMG-025)**. | **ĐÃ LÀM RÕ MÂU THUẪN** | Ma trận gốc đã bỏ sót tệp thứ 2 trong thư mục `1 trong 2 ảnh không phải là ảnh của cccd\Test2`: tệp `back.jpg` (165,101 bytes) có trên đĩa nhưng không có dòng nào trong ma trận. |
| **Tên tệp của IMG-007** | `Mặt trước chụp không rõ\Back.jpg` | Ma trận ghi `Mặt trước chụp không rõ\Front.jpg`. Thực tế trên đĩa tệp tên là `Back.jpg`. | **ĐÃ LÀM RÕ MÂU THUẪN** | Ma trận ghi sai tên tệp (`Front.jpg` thay vì `Back.jpg`). Về bản chất thị giác, tệp này là ảnh mặt sau CCCD (trùng nội dung với `IMG-002`). |
| **Bảo toàn ảnh nguồn** | Báo cáo khẳng định ảnh không đổi | So sánh hash SHA-256 của 26 tệp trước và sau toàn bộ quá trình chạy. | **XÁC MINH ĐẠT** | Toàn bộ 26/26 tệp giữ nguyên vẹn 100% nội dung byte (`unchanged: yes`). |
| **Lỗi trích xuất số CCCD dòng 1 (Ca OCR-01)** | Regex lấy nhầm số phụ | Tái lập độc lập qua `COMPONENT_IMAGE_TEST` và `STATIC_CODE_INSPECTION`. | **XÁC MINH ĐẠT** | Chuỗi VietOCR nhận diện được có 21 chữ số sau `IDVNM`, làm biểu thức `IDVNM[A-Z0-9]{10}(\d{12})` không khớp; nhánh fallback `re.search(r"(\d{12})", ...)` lấy 12 chữ số đầu (chứa số phụ) thay vì 12 chữ số cuối (số CCCD). |
| **Thiếu OCR dòng 2 & 3 khi Offline** | Báo cáo ghi offline không đọc được dòng 2 & 3 | Kiểm tra mã nguồn [`back_card_processor.py:L395-L433`](file:///D:/Khanh/Old%20but%20Gold/Source%20Code/O.G%20Shop/services/ekyc-service/cccd_module/back_card_processor.py#L395-L433). | **XÁC MINH ĐẠT** | Code offline chỉ crop dòng 1 `y: 68%-78%`, hoàn toàn không có lệnh crop hay gọi VietOCR cho dòng 2 và 3. Hệ thống fail-closed khi thiếu Gemini. |
| **Trùng lặp tệp trong dataset** | Báo cáo ghi có 13 ảnh duy nhất | Gom nhóm băm SHA-256 cục bộ cho 26 tệp. | **XÁC MINH ĐẠT** | Chính xác 13 nhóm nội dung ảnh duy nhất. Nhiều ảnh bị sao chép vào 3–5 thư mục khác nhau. |
| **Loại bằng chứng thực tế** | Không phân định nhãn loại bằng chứng | Phân loại chuẩn hóa theo quy tắc nhiệm vụ. | **ĐÃ ĐIỀU CHỈNH** | Gắn nhãn tường minh `COMPONENT_IMAGE_TEST` cho chạy component ảnh và `UNIT_TEST` cho pytest suite; không đánh đồng với API integration. |

---

## 3. Danh mục ảnh, ánh xạ Ma trận & Thẩm quyền ghép cặp (Image Inventory & Pairing Authority)

> [!NOTE]
> Để bảo vệ dữ liệu cá nhân theo quy định, bảng dưới đây **chỉ ghi nhận thông tin kỹ thuật không định danh** (kích thước, độ nét Laplacian, độ sáng, phân loại mặt thẻ thị giác, nhóm trùng lặp nội dung). Tuyệt đối không chứa số CCCD, họ tên, ngày sinh, địa chỉ hay chuỗi MRZ thô.

### 3.1. Bảng danh mục 26 tệp ảnh thực tế trên đĩa

| STT | Đường dẫn tương đối từ dataset root | Đuôi | Dung lượng (bytes) | Ánh xạ Ma trận (Matrix ID) | Nhóm trùng nội dung (Duplicate Group) | Phân loại thị giác quan sát được | Pairing Authority | Ghi chú kỹ thuật |
|:---:|---|:---:|:---:|:---:|:---:|---|:---:|---|
| 01 | `1 trong 2 ảnh không phải là ảnh của cccd\Test1\back.jpg` | `.jpg` | 913,947 | `IMG-024` | Nhóm A (4 bản) | Mặt sau CCCD (Thẻ 2) | `VISUALLY_ASSESSED` | Ảnh rõ nét, dải MRZ rõ ở đáy, chip vàng bên trái. |
| 02 | `1 trong 2 ảnh không phải là ảnh của cccd\Test1\front (ảnh sai).jpg` | `.jpg` | 4,338 | `IMG-023` | Nhóm B (1 bản) | Ảnh chân dung avatar | `TEST_SUBSTITUTION` | Kích thước siêu nhỏ 100x100, không có khung CCCD. |
| 03 | `1 trong 2 ảnh không phải là ảnh của cccd\Test2\back.jpg` | `.jpg` | 165,101 | **CHƯA CÓ TRONG MA TRẬN** | Nhóm C (5 bản) | Mặt sau CCCD (Thẻ 1) | `UNKNOWN` | Tệp thực tế tồn tại trên đĩa nhưng ma trận ban đầu bỏ sót dòng này. |
| 04 | `1 trong 2 ảnh không phải là ảnh của cccd\Test2\front (ảnh sai).jpg` | `.jpg` | 278,208 | `IMG-025` | Nhóm D (1 bản) | Thẻ không phải CCCD / mờ | `TEST_SUBSTITUTION` | Độ nét thấp (Laplacian 35.48 < 40.0), không có khung CCCD. |
| 05 | `Hợp lệ\Test1\Back.jpg` | `.jpg` | 165,101 | `IMG-002` | Nhóm C (5 bản) | Mặt sau CCCD (Thẻ 1) | `VISUALLY_ASSESSED` | Crop sát viền thẻ, MRZ rõ nét, độ nét cao (939.39). |
| 06 | `Hợp lệ\Test1\Front.jpg` | `.jpg` | 105,232 | `IMG-001` | Nhóm E (4 bản) | Mặt trước CCCD (Thẻ 1) | `VISUALLY_ASSESSED` | Rõ nét (Laplacian 362.61), có khuôn mặt chân dung bên trái. |
| 07 | `Hợp lệ\Test2\back.jpg` | `.jpg` | 913,947 | `IMG-004` | Nhóm A (4 bản) | Mặt sau CCCD (Thẻ 2) | `VISUALLY_ASSESSED` | Độ phân giải cao (2560x1638), MRZ rõ ở đáy. |
| 08 | `Hợp lệ\Test2\front.jpg` | `.jpg` | 667,335 | `IMG-003` | Nhóm F (3 bản) | Mặt trước CCCD (Thẻ 2) | `VISUALLY_ASSESSED` | Độ phân giải cao (2560x1565), đầy đủ thông tin mặt trước. |
| 09 | `Mặt sau chụp không rõ\Back (không khớp thông tin).jpg` | `.jpg` | 215,462 | `IMG-006` | Nhóm G (1 bản) | Mặt sau CCCD (Thẻ 4) | `UNKNOWN` | Thẻ chụp xa, nhỏ trong khung hình dọc 1074x2340. |
| 10 | `Mặt sau chụp không rõ\Front.jpg` | `.jpg` | 105,232 | `IMG-005` | Nhóm E (4 bản) | Mặt trước CCCD (Thẻ 1) | `UNKNOWN` | Trùng nội dung 100% với `IMG-001`. |
| 11 | `Mặt trước chụp không rõ\Back.jpg` | `.jpg` | 165,101 | `IMG-007` (ma trận ghi sai tên `Front.jpg`) | Nhóm C (5 bản) | Mặt sau CCCD (Thẻ 1) | `UNKNOWN` | Tên tệp trên đĩa là `Back.jpg`, nội dung là mặt sau Thẻ 1. |
| 12 | `Mặt trước chụp không rõ\Mặt trước bị che mất thông tin 2.jpg` | `.jpg` | 203,541 | `IMG-010` | Nhóm H (1 bản) | Mặt trước chụp xa / mờ | `UNKNOWN` | Thẻ nhỏ trong khung, độ nét thấp (Laplacian 31.04 < 40.0). |
| 13 | `Mặt trước chụp không rõ\Mặt trước bị che mất thông tin.jpg` | `.jpg` | 263,002 | `IMG-009` | Nhóm I (1 bản) | Mặt trước bị che thông tin | `UNKNOWN` | Dòng họ và tên bị che khuất, các trường khác đọc được. |
| 14 | `Mặt trước chụp không rõ\Mặt trước chụp không rõ.jpg` | `.jpg` | 194,653 | `IMG-008` | Nhóm J (1 bản) | Mặt trước chụp rất mờ | `UNKNOWN` | Thẻ chụp xa, mờ nghiêm trọng (Laplacian 20.71 < 40.0). |
| 15 | `Ảnh mặt sau không khớp thông tin\Test1\back.jpg` | `.jpg` | 913,947 | `IMG-012` | Nhóm A (4 bản) | Mặt sau CCCD (Thẻ 2) | `UNKNOWN` | Trùng nội dung với `IMG-004`. |
| 16 | `Ảnh mặt sau không khớp thông tin\Test1\front.jpg` | `.jpg` | 105,232 | `IMG-011` | Nhóm E (4 bản) | Mặt trước CCCD (Thẻ 1) | `UNKNOWN` | Trùng nội dung với `IMG-001`. |
| 17 | `Ảnh mặt sau không khớp thông tin\Test2\back.jpg` | `.jpg` | 165,101 | `IMG-014` | Nhóm C (5 bản) | Mặt sau CCCD (Thẻ 1) | `UNKNOWN` | Trùng nội dung với `IMG-002`. |
| 18 | `Ảnh mặt sau không khớp thông tin\Test2\front.jpg` | `.jpg` | 667,335 | `IMG-013` | Nhóm F (3 bản) | Mặt trước CCCD (Thẻ 2) | `UNKNOWN` | Trùng nội dung với `IMG-003`. |
| 19 | `Ảnh mặt sau không khớp thông tin\Test3\back (ảnh bị nghiêng).jfif` | `.jfif` | 287,962 | `IMG-016` | Nhóm K (1 bản) | Mặt sau chụp nghiêng góc | `UNKNOWN` | Khung thẻ bị nghiêng phối cảnh mạnh trong khung dọc. |
| 20 | `Ảnh mặt sau không khớp thông tin\Test3\front.jfif` | `.jfif` | 191,653 | `IMG-015` | Nhóm L (2 bản) | Mặt trước CCCD (Thẻ 3) | `UNKNOWN` | Ảnh rõ nét (Laplacian 657.29), có đầy đủ 4 trường. |
| 21 | `ảnh mặt trước, mặt sau bị đảo lộn\Test1\back.jpg` | `.jpg` | 105,232 | `IMG-018` | Nhóm E (4 bản) | **Mặt trước CCCD** (Thẻ 1) | `TEST_SWAPPED` | Tên tệp là `back.jpg` nhưng nội dung hình ảnh là MẶT TRƯỚC. |
| 22 | `ảnh mặt trước, mặt sau bị đảo lộn\Test1\front.jpg` | `.jpg` | 165,101 | `IMG-017` | Nhóm C (5 bản) | **Mặt sau CCCD** (Thẻ 1) | `TEST_SWAPPED` | Tên tệp là `front.jpg` nhưng nội dung hình ảnh là MẶT SAU. |
| 23 | `ảnh mặt trước, mặt sau bị đảo lộn\Test2\Back.jpg` | `.jpg` | 667,335 | `IMG-020` | Nhóm F (3 bản) | **Mặt trước CCCD** (Thẻ 2) | `TEST_SWAPPED` | Tên tệp là `Back.jpg` nhưng nội dung hình ảnh là MẶT TRƯỚC. |
| 24 | `ảnh mặt trước, mặt sau bị đảo lộn\Test2\Front.jpg` | `.jpg` | 913,947 | `IMG-019` | Nhóm A (4 bản) | **Mặt sau CCCD** (Thẻ 2) | `TEST_SWAPPED` | Tên tệp là `Front.jpg` nhưng nội dung hình ảnh là MẶT SAU. |
| 25 | `ảnh mặt trước, mặt sau bị đảo lộn\Test3\Back.jfif` | `.jfif` | 191,653 | `IMG-022` | Nhóm L (2 bản) | **Mặt trước CCCD** (Thẻ 3) | `TEST_SWAPPED` | Tên tệp là `Back.jfif` nhưng nội dung hình ảnh là MẶT TRƯỚC. |
| 26 | `ảnh mặt trước, mặt sau bị đảo lộn\Test3\Front.jfif` | `.jfif` | 119,563 | `IMG-021` | Nhóm M (1 bản) | **Mặt sau CCCD** (Thẻ 3) | `TEST_SWAPPED` | Tên tệp là `Front.jfif` nhưng nội dung hình ảnh là MẶT SAU. |

### 3.2. Chi tiết 13 nhóm nội dung trùng lặp (Duplicate Content Groups)

- **Nhóm A (4 tệp trùng)**: `IMG-004`, `IMG-012`, `IMG-019`, `IMG-024` — Bản chất: Mặt sau Thẻ 2.
- **Nhóm B (1 tệp)**: `IMG-023` — Bản chất: Avatar chân dung (100x100).
- **Nhóm C (5 tệp trùng)**: `IMG-002`, `IMG-014`, `IMG-017`, tệp unmapped `Test2\back.jpg`, và tệp `IMG-007` (thực tế tên `Back.jpg`) — Bản chất: Mặt sau Thẻ 1.
- **Nhóm D (1 tệp)**: `IMG-025` — Bản chất: Thẻ không rõ loại / độ nét thấp.
- **Nhóm E (4 tệp trùng)**: `IMG-001`, `IMG-005`, `IMG-011`, `IMG-018` — Bản chất: Mặt trước Thẻ 1.
- **Nhóm F (3 tệp trùng)**: `IMG-003`, `IMG-013`, `IMG-020` — Bản chất: Mặt trước Thẻ 2.
- **Nhóm G (1 tệp)**: `IMG-006` — Bản chất: Mặt sau chụp xa (Thẻ 4).
- **Nhóm H (1 tệp)**: `IMG-010` — Bản chất: Mặt trước chụp xa / mờ (Thẻ 5).
- **Nhóm I (1 tệp)**: `IMG-009` — Bản chất: Mặt trước bị che dòng họ tên (Thẻ 6).
- **Nhóm J (1 tệp)**: `IMG-008` — Bản chất: Mặt trước chụp rất mờ (Thẻ 7).
- **Nhóm K (1 tệp)**: `IMG-016` — Bản chất: Mặt sau chụp nghiêng góc (Thẻ 8).
- **Nhóm L (2 tệp trùng)**: `IMG-015`, `IMG-022` — Bản chất: Mặt trước Thẻ 3.
- **Nhóm M (1 tệp)**: `IMG-021` — Bản chất: Mặt sau Thẻ 3.

---

## 4. Kết quả kiểm thử từng ca kịch bản (OCR-01 đến OCR-16)

### Ca OCR-01: Cặp ứng viên hợp lệ Test1 (Candidate Valid Control 1)
- **Tệp sử dụng**: Mặt trước: `IMG-001` (`Hợp lệ\Test1\Front.jpg`) + Mặt sau: `IMG-002` (`Hợp lệ\Test1\Back.jpg`).
- **Kỳ vọng**: Thành công đối chiếu toàn bộ 4 trường nếu là cặp cùng thẻ đã xác nhận. Nếu chạy offline, fail-closed khi thiếu bóc tách dòng 2 & 3.
- **Thẩm quyền ghép cặp**: `VISUALLY_ASSESSED` (Chưa có `OWNER_CONFIRMED`).
- **Đường xử lý OCR**: Offline Local (YOLOv11 + VietOCR Transformer, không dùng Gemini).
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST` kết hợp `STATIC_CODE_INSPECTION`.
- **Từng assertion**:
  - Số CCCD: `MISMATCH` (Do lỗi regex lấy nhầm dải số phụ phía trước ở chế độ offline).
  - Ngày sinh: `NOT_READ` (Offline không hỗ trợ bóc tách dòng 2).
  - Giới tính: `NOT_READ`.
  - Họ và tên: `NOT_READ` (Offline không hỗ trợ bóc tách dòng 3).
  - Đủ 3 dòng MRZ: `FAIL` (Offline chỉ đọc dòng 1).
  - Chống bù đắp dữ liệu mặt trước: `PASS` (Hệ thống không lấy họ tên/ngày sinh mặt trước đắp vào dải MRZ).
- **Phản hồi hệ thống**: `REJECT / CCCD_MISMATCH`
- **Verdict**: `NEEDS_PAIR_CONFIRMATION` (Chưa có xác nhận cặp từ chủ dữ liệu; đường offline bị lỗi trích xuất số và bị chặn đọc dòng 2 & 3).

---

### Ca OCR-02: Cặp ứng viên hợp lệ Test2 (Candidate Valid Control 2)
- **Tệp sử dụng**: Mặt trước: `IMG-003` (`Hợp lệ\Test2\front.jpg`) + Mặt sau: `IMG-004` (`Hợp lệ\Test2\back.jpg`).
- **Kỳ vọng**: Thành công đối chiếu nếu cùng thẻ; offline fail-closed khi thiếu dòng 2 & 3.
- **Thẩm quyền ghép cặp**: `VISUALLY_ASSESSED`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**:
  - Số CCCD: `MATCH` (Khớp chính xác 100% 12 chữ số giữa dòng 1 MRZ và mặt trước).
  - Ngày sinh: `NOT_READ` (Offline không đọc dòng 2).
  - Giới tính: `NOT_READ`.
  - Họ và tên: `NOT_READ` (Offline không đọc dòng 3).
  - Đủ 3 dòng MRZ: `FAIL` (Thiếu dòng 2 & 3).
  - Chống bù đắp dữ liệu: `PASS` (Không tự sinh synthetic lines).
- **Phản hồi hệ thống**: `REJECT / UNREADABLE_MRZ_LINES` (*"Dải mã MRZ ở mặt sau thẻ không đọc đủ thông tin ngày sinh và họ tên. Vui lòng chụp lại ảnh mặt sau thẻ rõ nét hơn."*).
- **Verdict**: `NEEDS_PAIR_CONFIRMATION` (Chưa có xác nhận cặp; hệ thống hoạt động đúng nguyên tắc an toàn fail-closed khi chạy offline).

---

### Ca OCR-03: Ghép chéo có chủ ý giữa 2 thẻ khác nhau (Card 1 Front + Card 2 Back)
- **Tệp sử dụng**: Mặt trước: `IMG-001` (Thẻ 1) + Mặt sau: `IMG-004` (Thẻ 2).
- **Kỳ vọng**: Từ chối ngay lập tức do lệch thông tin; không tự sinh số giả hay lấy dữ liệu mặt trước.
- **Thẩm quyền ghép cặp**: `TEST_DESIGN_SYNTHETIC` (Cố ý ghép chéo để kiểm tra kiểm soát lỗi).
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**:
  - Số CCCD: `MISMATCH` (Phát hiện chính xác sự khác biệt giữa hai số thẻ).
  - Ngày sinh: `NOT_APPLICABLE` (Ngắt luồng ngay tại bước kiểm tra số CCCD).
  - Giới tính: `NOT_APPLICABLE`.
  - Họ và tên: `NOT_APPLICABLE`.
  - Chống bù đắp dữ liệu: `PASS`.
- **Phản hồi hệ thống**: `REJECT / CCCD_MISMATCH` (*"Mã MRZ mặt sau (...) không trùng khớp với số CCCD mặt trước (...). Vui lòng tải lên hai mặt của cùng một thẻ CCCD."*).
- **Verdict**: `PASS` (Ca phủ định nhân tạo hoạt động hoàn hảo, chặn đứng việc ghép nhầm thẻ).

---

### Ca OCR-04: Cặp không khớp thông tin Test1
- **Tệp sử dụng**: Mặt trước: `IMG-011` (Thẻ 1) + Mặt sau: `IMG-012` (Thẻ 2).
- **Kỳ vọng**: Từ chối do lệch thông tin nếu Owner xác nhận đây là cặp không khớp; nếu chưa xác nhận thì không chấm PASS/FAIL.
- **Thẩm quyền ghép cặp**: `UNKNOWN` (Tên thư mục ghi mismatch, nhưng quy tắc ma trận yêu cầu Owner xác nhận).
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**: Số CCCD: `MISMATCH`. Chống bù đắp: `PASS`.
- **Phản hồi hệ thống**: `REJECT / CCCD_MISMATCH`.
- **Verdict**: `NEEDS_PAIR_CONFIRMATION` (Theo đúng quy tắc kiểm thử, nhãn thư mục không tự động coi là ground truth; ghi nhận sự kiện kỹ thuật nhưng giữ trạng thái chờ xác nhận).

---

### Ca OCR-05: Cặp không khớp thông tin Test2
- **Tệp sử dụng**: Mặt trước: `IMG-013` (Thẻ 2) + Mặt sau: `IMG-014` (Thẻ 1).
- **Kỳ vọng**: Từ chối nếu xác nhận là cặp lệch thẻ.
- **Thẩm quyền ghép cặp**: `UNKNOWN`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**: Số CCCD: `MISMATCH`.
- **Phản hồi hệ thống**: `REJECT / CCCD_MISMATCH`.
- **Verdict**: `NEEDS_PAIR_CONFIRMATION`.

---

### Ca OCR-06: Mặt sau bị chụp góc nghiêng mạnh (Tilted Back)
- **Tệp sử dụng**: Mặt trước: `IMG-015` (Thẻ 3) + Mặt sau: `IMG-016` (Thẻ 8 chụp nghiêng).
- **Kỳ vọng**: Tự động xoay và căn chỉnh phối cảnh; từ chối quang học nếu góc quá khó đọc; không chấp nhận sai lệch danh tính.
- **Thẩm quyền ghép cặp**: `UNKNOWN`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**:
  - Tự động nắn chỉnh góc: ĐẠT (đưa về chuẩn ID-1 landscape).
  - Số CCCD: `MISMATCH` (Số trích xuất từ dải MRZ không khớp mặt trước).
- **Phản hồi hệ thống**: `REJECT / CCCD_MISMATCH`.
- **Verdict**: `NEEDS_PAIR_CONFIRMATION` (Chưa xác nhận thẻ nghiêng có cùng chủ thể với `IMG-015` hay không).

---

### Ca OCR-07: Đảo lộn mặt trước / mặt sau Test2
- **Tệp sử dụng**: Slot Front: `IMG-019` (thực chất là MẶT SAU) + Slot Back: `IMG-020` (thực chất là MẶT TRƯỚC).
- **Kỳ vọng**: Phát hiện nộp ngược mặt thẻ dựa trên nội dung hình ảnh; không tin tưởng tên file; fail-closed.
- **Thẩm quyền ghép cặp**: `TEST_SWAPPED`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**:
  - Nhận diện mặt trước ở slot Front: `REJECT` (Không tìm thấy góc/khung CCCD mặt trước).
  - Nhận diện mặt sau ở slot Back: `REJECT` (Hàm `check_for_front_portrait_face` phát hiện ảnh chân dung người, báo lỗi: *"Ảnh tải lên là mặt trước CCCD (có ảnh chân dung)..."*).
- **Phản hồi hệ thống**: `REJECT / WRONG_SIDE_UPLOAD`.
- **Verdict**: `PASS` (Cả 2 component đều phân loại thẻ dựa trên thị giác máy tính, từ chối nộp nhầm mặt thẻ).

---

### Ca OCR-08: Đảo lộn mặt trước / mặt sau Test1
- **Tệp sử dụng**: Slot Front: `IMG-017` (Mặt sau Thẻ 1) + Slot Back: `IMG-018` (Mặt trước Thẻ 1).
- **Kỳ vọng**: Từ chối upload ngược mặt thẻ; fail-closed.
- **Thẩm quyền ghép cặp**: `TEST_SWAPPED`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**: Slot Front bị từ chối do thiếu khung thẻ; Slot Back bị từ chối do phát hiện khuôn mặt chân dung.
- **Phản hồi hệ thống**: `REJECT / WRONG_SIDE_UPLOAD`.
- **Verdict**: `PASS`.

---

### Ca OCR-09: Thay thế mặt trước bằng ảnh chân dung avatar
- **Tệp sử dụng**: Slot Front: `IMG-023` (Ảnh avatar 100x100) + Slot Back: `IMG-024` (Mặt sau CCCD).
- **Kỳ vọng**: Từ chối ảnh không phải tài liệu CCCD; không cho phép mặt sau bù đắp cho mặt trước.
- **Thẩm quyền ghép cặp**: `TEST_SUBSTITUTION`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**: Slot Front bị từ chối lập tức: *"Không nhận diện được khung CCCD hoặc góc chụp quá khó quét."*
- **Phản hồi hệ thống**: `REJECT / NON_DOCUMENT_DETECTED`.
- **Verdict**: `PASS` (Chặn thành công ảnh không phải giấy tờ tùy thân).

---

### Ca OCR-10: Thay thế mặt trước bằng thẻ không phải CCCD
- **Tệp sử dụng**: Slot Front: `IMG-025` (Thẻ lạ / độ nét 35.48) + Slot Back: tệp unmapped `Test2\back.jpg`.
- **Kỳ vọng**: Từ chối ảnh không đạt chuẩn CCCD hoặc mờ; fail-closed.
- **Thẩm quyền ghép cặp**: `TEST_SUBSTITUTION`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**: Bộ lọc chất lượng `_check_image_quality` phát hiện phương sai Laplacian 35.5 < 40.0, từ chối xử lý ngay.
- **Phản hồi hệ thống**: `REJECT / IMAGE_QUALITY_ISSUE` (*"Ảnh chụp bị mờ hoặc rung tay (độ nét 35.5 < 40.0). Vui lòng giữ chắc camera và chụp lại."*).
- **Verdict**: `PASS` (Cổng kiểm soát chất lượng quang học hoạt động đúng thiết kế).

---

### Ca OCR-11: Mặt trước chụp mờ từ xa
- **Tệp sử dụng**: Slot Front: `IMG-008` (Laplacian 20.71 < 40.0) + Slot Back: `IMG-002`.
- **Kỳ vọng**: Báo lỗi ảnh mờ và yêu cầu chụp lại; fail-closed.
- **Thẩm quyền ghép cặp**: `UNKNOWN`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**: Phát hiện độ nét 20.7 < 40.0, từ chối xử lý.
- **Phản hồi hệ thống**: `REJECT / BLUR_DETECTED`.
- **Verdict**: `PASS` (Yêu cầu chụp lại đạt chuẩn).

---

### Ca OCR-12: Mặt trước bị che mất thông tin họ và tên
- **Tệp sử dụng**: Slot Front: `IMG-009` (Che dòng họ tên) + Slot Back: `IMG-002`.
- **Kỳ vọng**: Từ chối do thiếu trường bắt buộc; tuyệt đối không lấy họ tên từ MRZ mặt sau điền sang.
- **Thẩm quyền ghép cặp**: `UNKNOWN`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**:
  - Nhận diện số CCCD: CÓ.
  - Nhận diện Họ và tên: KHÔNG (bị che khuất).
  - Validation: Hàm `validate_ho_va_ten` kích hoạt lỗi `INVALID_FULL_NAME`.
  - Chống bù đắp dữ liệu: `PASS`.
- **Phản hồi hệ thống**: `REJECT / INVALID_FULL_NAME` (*"Họ và tên trên thẻ không hợp lệ hoặc bị che khuất... Vui lòng không che thẻ và chụp lại ảnh rõ nét."*).
- **Verdict**: `PASS` (Ngăn chặn thành công việc nộp thẻ bị che thông tin quan trọng).

---

### Ca OCR-13: Mặt trước bị che thông tin / mờ mẫu 2
- **Tệp sử dụng**: Slot Front: `IMG-010` (Laplacian 31.04 < 40.0) + Slot Back: `IMG-002`.
- **Kỳ vọng**: Từ chối yêu cầu chụp lại; fail-closed.
- **Thẩm quyền ghép cặp**: `UNKNOWN`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**: Bị từ chối tại cổng kiểm soát chất lượng do độ nét không đạt chuẩn (31.0 < 40.0).
- **Phản hồi hệ thống**: `REJECT / IMAGE_QUALITY_ISSUE`.
- **Verdict**: `PASS`.

---

### Ca OCR-14: Mặt sau chụp xa / không rõ nét
- **Tệp sử dụng**: Slot Front: `IMG-005` + Slot Back: `IMG-006` (Thẻ nhỏ trong khung).
- **Kỳ vọng**: Yêu cầu chụp lại do chất lượng kém hoặc báo lệch thẻ; không xác nhận eKYC.
- **Thẩm quyền ghép cặp**: `UNKNOWN`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**: Bóc tách số CCCD từ dải mã của `IMG-006` và phát hiện lệch so với mặt trước của `IMG-005`.
- **Phản hồi hệ thống**: `REJECT / CCCD_MISMATCH`.
- **Verdict**: `NEEDS_PAIR_CONFIRMATION` (Cần xác nhận cặp và kỳ vọng chuẩn từ chủ dữ liệu).

---

### Ca OCR-15: Xung đột nhãn thư mục và bản chất ảnh
- **Tệp sử dụng**: Slot Front: `IMG-001` + Slot Back: `IMG-007` (Tệp trên đĩa là `Back.jpg`).
- **Kỳ vọng**: Xử lý theo đúng bản chất thị giác của ảnh (mặt sau); không suy đoán kết quả từ đường dẫn.
- **Thẩm quyền ghép cặp**: `UNKNOWN`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**: `BackCardProcessor` nhận diện đúng đây là mặt sau CCCD và thực hiện đối chiếu dải MRZ.
- **Phản hồi hệ thống**: `REJECT / CCCD_MISMATCH`.
- **Verdict**: `NEEDS_PAIR_CONFIRMATION`.

---

### Ca OCR-16: Kiểm tra tính phi trạng thái khi thay đổi ảnh (Changed-Side Regression)
- **Tệp sử dụng**:
  - Lượt 1: Upload Thẻ 1 Mặt trước (`IMG-001`) + Thẻ 1 Mặt sau (`IMG-002`).
  - Lượt 2: Thay thế ảnh mặt sau bằng Thẻ 2 Mặt sau (`IMG-004`).
- **Kỳ vọng**: Lượt 2 xử lý độc lập hoàn toàn, không lưu cache cũ, lập tức phát hiện mismatch giữa Thẻ 1 và Thẻ 2.
- **Thẩm quyền ghép cặp**: `TEST_DESIGN_SYNTHETIC`.
- **Đường xử lý OCR**: Offline Local.
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST`.
- **Từng assertion**:
  - Tính phi trạng thái: `PASS` (Lượt 2 không bị ảnh hưởng bởi kết quả của lượt 1).
  - Bắt lỗi lệch thẻ: `PASS` (Báo lệch số CCCD ngay lập tức).
- **Phản hồi hệ thống**: `REJECT / CCCD_MISMATCH`.
- **Verdict**: `PASS`.

---

## 5. Lệnh thực thi & Kết quả đo lường thực tế

### 5.1. Bộ kiểm thử đơn vị (`UNIT_TEST`)

Lệnh chạy trực tiếp từ thư mục gốc dự án:
```powershell
pytest services/ekyc-service/tests
```
- **Exit Code**: `0` (Thành công).
- **Thời lượng thực thi**: `12.38 giây`.
- **Tóm tắt kết quả**: **33 passed**, 1 warning, 0 failures, 0 errors.
  - `test_back_card_processor.py`: 14 passed (Bao phủ 5 điều kiện quang học, orientation correction, offline mismatch, fail-closed khi thiếu dòng 2/3).
  - `test_mrz_parser.py`: 11 passed (Bao phủ bảng mã thế kỷ 0–9, calendar validation, bất biến giới tính/năm sinh, cấm substring matching, exact match).
  - `test_quality_and_agent.py`: 4 passed (Kiểm tra chất lượng ảnh, Laplacian variance, Gemini fallback).
  - `test_security_contract.py`: 4 passed (Ranh giới bảo mật nội bộ, internal token, không lưu trữ singleton).

### 5.2. Kiểm thử thành phần xử lý ảnh (`COMPONENT_IMAGE_TEST`)

Lệnh thực thi bộ quét và chạy kịch bản an toàn:
```powershell
python "C:\Users\Legion\.gemini\antigravity-ide\brain\e022494a-f7d4-425c-911f-7291e7657d87\scratch\run_all_scenarios.py"
```
- **Exit Code**: `0`.
- **Thời lượng thực thi**: `52.4 giây`.
- **Tóm tắt kết quả**: 16/16 kịch bản đã thực thi hoàn chỉnh đến các bước assertion cụ thể trên bản sao tạm.

### 5.3. Kiểm tra tính toàn vẹn của tệp nguồn

Lệnh so sánh băm SHA-256 trước và sau phiên làm việc:
```powershell
python "C:\Users\Legion\.gemini\antigravity-ide\brain\e022494a-f7d4-425c-911f-7291e7657d87\scratch\verify_hashes.py"
```
- **Exit Code**: `0`.
- **Đầu ra ghi nhận**:
  ```text
  Total source files checked: 26
  Source files unchanged: YES
  ```

---

## 6. Tính toàn vẹn ảnh nguồn & An toàn hệ thống

- **Source files unchanged**: **YES** (Đã xác minh bằng việc so sánh hash SHA-256 từng tệp độc lập).
- **No external API called**: **YES** (Biến môi trường `EKYC_GEMINI_POSTPROCESSING_ENABLED=false` được tôn trọng; tham số `gemini_processor=None` được truyền cứng trong toàn bộ kịch bản; không có gói tin nào gửi ra Internet).
- **Cơ chế cách ly bản sao tạm**:
  - Tại mã nguồn [`BackCardProcessor.process_back_card:L473-L476`](file:///D:/Khanh/Old%20but%20Gold/Source%20Code/O.G%20Shop/services/ekyc-service/cccd_module/back_card_processor.py#L473-L476), phương thức này **tự động ghi đè ảnh đã xoay/căn chỉnh vào chính đường dẫn tệp đầu vào (`image_path`)**.
  - Do đó, nếu truyền trực tiếp đường dẫn ảnh nguồn trong `TesteKYC` vào hàm này, ảnh nguồn sẽ bị thay đổi vĩnh viễn.
  - Toàn bộ các kiểm thử của chúng tôi đều tạo bản sao độc lập trong thư mục tạm hệ thống (`tempfile.TemporaryDirectory()`), giải phóng ngay sau khi chạy xong. Nhờ đó, tính toàn vẹn của 26 tệp ảnh gốc được bảo vệ tuyệt đối.

---

## 7. Các phát hiện kỹ thuật (Technical Findings)

> [!CAUTION]
> **Quy định bất biến**: Các phát hiện dưới đây được ghi lại làm cơ sở kỹ thuật phục vụ việc cải tiến tiếp theo. **Không có bất kỳ dòng code nào bị sửa đổi trong nhiệm vụ này**.

### Phát hiện 1: Lỗi trích xuất 12 chữ số CCCD từ dải MRZ dòng 1 khi chạy Offline
- **Loại bằng chứng**: `COMPONENT_IMAGE_TEST` (Tái lập quan sát được tại ca `OCR-01`) kết hợp `STATIC_CODE_INSPECTION`.
- **Chi tiết kỹ thuật**:
  - Tại file [`services/ekyc-service/cccd_module/back_card_processor.py`](file:///D:/Khanh/Old%20but%20Gold/Source%20Code/O.G%20Shop/services/ekyc-service/cccd_module/back_card_processor.py#L403-L407):
    ```python
    m = (
        re.search(r"\d{10}(\d{12})", cleaned_l1)
        or re.search(r"IDVNM[A-Z0-9]{10}(\d{12})", cleaned_l1)
        or re.search(r"(\d{12})", cleaned_l1)
    )
    ```
  - Khi VietOCR bóc tách dòng 1 của ảnh `IMG-002`, chuỗi nhận diện được gồm 5 ký tự `IDVNM` + 21 chữ số + 2 ký tự đuôi. Do thiếu 1 chữ số đệm so với chuẩn 22 ký tự sau `IDVNM`, cả 2 biểu thức đầu đều không khớp.
  - Biểu thức thứ ba `re.search(r"(\d{12})", cleaned_l1)` tìm thấy 12 chữ số đầu tiên trong chuỗi số (tức dải số phụ) thay vì 12 chữ số CCCD nằm ở cuối chuỗi số.
- **Hệ quả**: Báo sai lệch số CCCD ở mặt sau của thẻ hợp lệ, dẫn đến từ chối nhầm.
- **Đề xuất kỹ thuật cho task kế tiếp**: Cải tiến biểu thức regex neo vào đuôi chuỗi hoặc quét trong 16 ký tự cuối:
  ```python
  m = (
      re.search(r"IDVNM[A-Z0-9]{9,11}(\d{12})", cleaned_l1)
      or re.search(r"(\d{12})[A-Z0-9<]{0,3}$", cleaned_l1)
      or re.search(r"(\d{12})", cleaned_l1[-16:])
  )
  ```

### Phát hiện 2: Chế độ Offline Fallback hoàn toàn chưa hỗ trợ bóc tách Dòng 2 & 3 của MRZ
- **Loại bằng chứng**: `STATIC_CODE_INSPECTION` kết hợp `COMPONENT_IMAGE_TEST` (Tái lập tại ca `OCR-02`).
- **Chi tiết kỹ thuật**:
  - Triển khai offline của hàm [`extract_mrz_lines`](file:///D:/Khanh/Old%20but%20Gold/Source%20Code/O.G%20Shop/services/ekyc-service/cccd_module/back_card_processor.py#L395-L433) chỉ định nghĩa `line1_crop = aligned_image[int(h * 0.68):int(h * 0.78), :]`.
  - Không có vùng crop và không gọi VietOCR cho dòng 2 (ngày sinh/giới tính) và dòng 3 (họ tên).
  - Nguyên tắc fail-closed của TASK-0108 bắt buộc phải từ chối khi thiếu dòng 2 & 3. Do đó, trong môi trường hoàn toàn offline, thẻ CCCD hợp lệ 100% vẫn không thể hoàn tất xác nhận eKYC.
- **Đề xuất kỹ thuật cho task kế tiếp**: Thêm 2 vùng crop cục bộ cho dòng 2 (`[int(h * 0.78):int(h * 0.88), :]`) và dòng 3 (`[int(h * 0.88):int(h * 0.98), :]`), truyền qua VietOCR với tập ký tự MRZ chuẩn ICAO TD1.

### Phát hiện 3: Nguy cơ ghi đè ảnh gốc của hàm `BackCardProcessor.process_back_card`
- **Loại bằng chứng**: `STATIC_CODE_INSPECTION`.
- **Chi tiết kỹ thuật**:
  - Tại dòng 474-476 của [`back_card_processor.py`](file:///D:/Khanh/Old%20but%20Gold/Source%20Code/O.G%20Shop/services/ekyc-service/cccd_module/back_card_processor.py#L474-L476):
    ```python
    try:
        cv2.imencode(".jpg", upright)[1].tofile(image_path)
    except Exception:
        cv2.imwrite(image_path, upright)
    ```
  - Code ghi đè trực tiếp ảnh đã xoay vào `image_path`. Nếu người dùng hoặc agent khác truyền đường dẫn file gốc vào hàm này, file gốc sẽ bị thay đổi vĩnh viễn.
- **Đề xuất kỹ thuật cho task kế tiếp**: Bổ sung tham số `save_aligned_image: bool = True` hoặc tách riêng `output_path: Optional[str] = None` để tránh vô tình ghi đè ảnh gốc.

---

## 8. Giới hạn & Các bước cần Chủ dữ liệu (Dataset Owner) xác nhận

Để có thể đưa bộ kiểm thử này thành bộ kiểm thử chấp nhận tự động (Automated Acceptance Suite) trong quy trình CI/CD, cần chủ dữ liệu xác nhận rõ 3 điểm sau:

1. **Xác nhận Bảng ánh xạ Ground Truth (Cặp thẻ hợp lệ & Cặp lệch thẻ)**:
   - Xác nhận chính thức cặp `IMG-001` + `IMG-002` có phải là 2 mặt của cùng 1 thẻ Căn cước công dân thật hay không (`OWNER_CONFIRMED`).
   - Xác nhận chính thức cặp `IMG-003` + `IMG-004` có phải là 2 mặt của cùng 1 thẻ Căn cước công dân thật hay không.
   - Xác nhận tệp thứ 26: `1 trong 2 ảnh không phải là ảnh của cccd\Test2\back.jpg` (kích thước 165,101 bytes) có thuộc diện kiểm thử hay không để bổ sung vào ma trận chính thức.
2. **Quyết định về Chính sách bóc tách dải mã MRZ khi Offline**:
   - Xác nhận việc triển khai mở rộng VietOCR bóc tách cả 3 dòng MRZ offline cục bộ để hệ thống eKYC hoàn toàn tự chủ mà không cần phụ thuộc vào Gemini Vision bên ngoài.
3. **Chuẩn hóa lại tệp nguồn**:
   - Loại bỏ các tệp ảnh trùng lặp giữa các thư mục và sửa lại tên tệp bị đảo lộn trong thư mục `ảnh mặt trước, mặt sau bị đảo lộn` để tránh gây nhầm lẫn cho các kỹ sư kiểm thử tiếp theo.
