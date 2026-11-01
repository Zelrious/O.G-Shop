# eKYC CCCD OCR & MRZ Test Execution Results

## 1. Thông tin chung về phiên kiểm thử

- **Ngày giờ thực hiện**: 2026-10-27 (Thời gian local: 18:05:43+07:00).
- **Mã định danh mã nguồn (Git revision)**: `4667abc1672a8286f7690ed5061caeed8ed03296` (nhánh `feat/notifications-and-buyer-orders`).
- **Môi trường thực thi**: Local Sandbox (Windows 11, Python 3.11.9, PyTorch, YOLOv11n, VietOCR Transformer, OpenCV).
- **Đường xử lý OCR thực tế**:
  - Mặt trước: `CCCDProcessor` nội bộ (YOLO corner detection + VietOCR text recognition).
  - Mặt sau: `BackCardProcessor` nội bộ (Căn chỉnh góc phối cảnh, tự động xoay hướng 4 chiều, kiểm định tính hợp lệ thẻ CCCD gắn chip mặt sau, bóc tách dòng 1 dải mã MRZ qua VietOCR).
  - Chế độ mạng & nhà cung cấp: **100% Offline / Local**. Tuyệt đối không gọi Gemini API, Google GenAI, hay bất kỳ dịch vụ đám mây bên thứ ba nào (`gemini_processor = None`, `allow_synthetic = False`).
- **Ranh giới an toàn & bảo vệ dữ liệu (Zero PII)**:
  - Toàn bộ ảnh nguồn tại `D:\Khanh\Old but Gold\Source Code\TesteKYC` được bảo toàn nguyên trạng.
  - Mỗi ca kiểm thử được khởi tạo trên bản sao tạm biệt lập trong thư mục tạm hệ thống (`tempfile.TemporaryDirectory()`).
  - Không có bất kỳ dữ liệu định danh thật nào (Số CCCD, Họ tên, Ngày sinh, Quê quán, Địa chỉ thường trú, chuỗi MRZ thô) được ghi vào báo cáo, log chia sẻ hay mã nguồn.
  - Kết quả đối chiếu được mã hóa thành các trạng thái boolean chuẩn hóa (`MATCH`, `MISMATCH`, `NOT_READ`, `PRESENT`, `MISSING`).
- **Toàn vẹn tệp nguồn**:
  - Kiểm tra băm SHA-256 toàn bộ 26 tệp ảnh trước và sau khi chạy: **26/26 tệp không thay đổi (`unchanged: yes`)**.

---

## 2. Bảng tổng hợp kết quả kiểm thử

| Tổng số ca kịch bản | PASS | FAIL | NEEDS_PAIR_CONFIRMATION | BLOCKED |
|:---:|:---:|:---:|:---:|:---:|
| **16** | **9** | **0** | **7** | **0\*** |

*\*Ghi chú về trạng thái BLOCKED*: Toàn bộ 16 ca đã được đưa vào runner an toàn local. Không có ca nào bị dừng do lỗi hạ tầng. Tuy nhiên, đường bóc tách dòng 2 (ngày sinh, giới tính) và dòng 3 (họ tên) của dải MRZ mặt sau hiện tại phụ thuộc vào Gemini Vision; do chính sách an toàn không cấp quyền gọi API bên ngoài đối với dữ liệu CCCD thật, phần đối chiếu đầy đủ 4 trường MRZ đối với thẻ hợp lệ được ghi nhận hạn chế kỹ thuật (Offline Fallback Fail-Closed).

---

## 3. Phân tích chi tiết từng ca kiểm thử (OCR-01 đến OCR-16)

### Ca OCR-01: Cặp ứng viên hợp lệ Test1 (Card 1)
- **Tệp đầu vào**:
  - Mặt trước: `IMG-001` (`Hợp lệ\Test1\Front.jpg`)
  - Mặt sau: `IMG-002` (`Hợp lệ\Test1\Back.jpg`)
- **Đặc điểm ảnh trước khi chạy**:
  - `IMG-001`: Ảnh mặt trước CCCD rõ nét, kích thước 809x502, Laplacian variance 362.61, độ sáng 221.47, phát hiện khuôn mặt chân dung ở bên trái.
  - `IMG-002`: Ảnh mặt sau CCCD crop sát thẻ, kích thước 904x595, Laplacian variance 939.39, độ sáng 210.29, dải MRZ rõ nét ở đáy.
- **Pairing authority**: `Owner to confirm` (Chưa có xác nhận chính thức từ chủ bộ dữ liệu).
- **Kỳ vọng**: Thành công đối chiếu 4 trường nếu là cặp hợp lệ đã xác nhận; nếu chạy offline, hệ thống fail-closed khi thiếu dòng 2/3.
- **Kết quả thực tế**:
  - Mặt trước: `status=SUCCESS`. Trích xuất đủ 4 trường cốt lõi (`so_cccd: PRESENT`, `ho_va_ten: PRESENT`, `ngay_sinh: PRESENT`, `gioi_tinh: PRESENT`).
  - Mặt sau:
    - Căn chỉnh & định hướng: Góc xoay 0° (chuẩn).
    - Kiểm định mặt sau: `is_cccd_back = True` (chip vàng, dấu đỏ, vân tay, tương phản MRZ đạt chuẩn).
    - Trích xuất MRZ dòng 1: Do VietOCR nhận diện thiếu 1 ký tự đệm (chuỗi số gồm 21 chữ số thay vì 22), regex `\d{10}(\d{12})` không khớp; nhánh fallback `re.search(r"(\d{12})", ...)` bắt nhầm 12 chữ số đầu (chứa số phụ) thay vì 12 số CCCD ở đuôi.
    - Hệ thống phát hiện lệch số CCCD giữa mặt trước và mặt sau: trả về thông báo lỗi lệch thẻ.
- **Từng assertion**:
  - Số CCCD: `MISMATCH` (do trích xuất nhầm dải số phụ phía trước ở chế độ offline).
  - Ngày sinh: `NOT_READ` (offline VietOCR chưa bóc tách dòng 2).
  - Giới tính: `NOT_READ`.
  - Họ và tên: `NOT_READ` (offline VietOCR chưa bóc tách dòng 3).
  - Không bù đắp dữ liệu mặt trước: ĐẠT (hệ thống không tự ý lấy dữ liệu mặt trước đắp vào mặt sau).
- **Trạng thái cuối**: `REJECT / MISMATCH`
- **Verdict**: `NEEDS_PAIR_CONFIRMATION` (Chưa có xác nhận cặp từ Owner; phát hiện lỗi lệch số trích xuất dòng 1 ở chế độ offline).

---

### Ca OCR-02: Cặp ứng viên hợp lệ Test2 (Card 2)
- **Tệp đầu vào**:
  - Mặt trước: `IMG-003` (`Hợp lệ\Test2\front.jpg`)
  - Mặt sau: `IMG-004` (`Hợp lệ\Test2\back.jpg`)
- **Đặc điểm ảnh trước khi chạy**:
  - `IMG-003`: Mặt trước rõ nét, kích thước 2560x1565, Laplacian variance 94.34, độ sáng 174.73.
  - `IMG-004`: Mặt sau độ phân giải cao, kích thước 2560x1638, Laplacian variance 150.22, độ sáng 105.51.
- **Pairing authority**: `Owner to confirm`.
- **Kỳ vọng**: Thành công đối chiếu nếu đã xác nhận cặp; nếu offline, fail-closed khi thiếu dòng 2/3, không tự ý pass.
- **Kết quả thực tế**:
  - Mặt trước: `status=SUCCESS`. Trích xuất đủ 4 trường cốt lõi.
  - Mặt sau:
    - Định hướng & kiểm định: `is_cccd_back = True`.
    - Trích xuất MRZ dòng 1: Khớp chính xác 100% 12 chữ số CCCD với mặt trước.
    - Xử lý dòng 2 & 3: Do đang chạy offline và không cho phép sinh dữ liệu giả lập (`allow_synthetic = False`), hệ thống tuân thủ nghiêm ngặt nguyên tắc Fail-Closed theo TASK-0108, trả về thông báo: *"Dải mã MRZ ở mặt sau thẻ không đọc đủ thông tin ngày sinh và họ tên. Vui lòng chụp lại ảnh mặt sau thẻ rõ nét hơn."*
- **Từng assertion**:
  - Số CCCD: `MATCH` (khớp chính xác 12 chữ số).
  - Ngày sinh: `NOT_READ` (offline không đọc được dòng 2).
  - Giới tính: `NOT_READ`.
  - Họ và tên: `NOT_READ` (offline không đọc được dòng 3).
  - Không bù đắp dữ liệu mặt trước: ĐẠT (loại bỏ hoàn toàn synthetic lines).
- **Trạng thái cuối**: `REJECT / UNREADABLE_MRZ_LINES`
- **Verdict**: `NEEDS_PAIR_CONFIRMATION` (Cặp chưa được Owner xác nhận chính thức; chế độ offline hoạt động đúng thiết kế fail-closed khi thiếu provider OCR dòng 2 & 3).

---

### Ca OCR-03: Ghép chéo có chủ ý giữa 2 thẻ khác nhau (Card 1 Front + Card 2 Back)
- **Tệp đầu vào**:
  - Mặt trước: `IMG-001` (`Hợp lệ\Test1\Front.jpg` - Thẻ 1)
  - Mặt sau: `IMG-004` (`Hợp lệ\Test2\back.jpg` - Thẻ 2)
- **Đặc điểm ảnh trước khi chạy**: Cả 2 ảnh đều là ảnh thẻ CCCD rõ nét, nhưng thuộc 2 chủ thể định danh khác nhau.
- **Pairing authority**: Ca kiểm thử nhân tạo có chủ ý (Negative test case).
- **Kỳ vọng**: Từ chối ngay lập tức do lệch thông tin; tuyệt đối không tự động sinh số hoặc đắp dữ liệu mặt trước.
- **Kết quả thực tế**:
  - Mặt trước: `status=SUCCESS` (Số CCCD Thẻ 1).
  - Mặt sau: VietOCR đọc được số CCCD Thẻ 2 từ dải MRZ dòng 1.
  - Đối chiếu: Phát hiện `extracted_cccd != front_cccd`.
  - Hệ thống trả về lỗi tường minh: *"Mã MRZ mặt sau (...) không trùng khớp với số CCCD mặt trước (...). Vui lòng tải lên hai mặt của cùng một thẻ CCCD."*
- **Từng assertion**:
  - Số CCCD: `MISMATCH` (phát hiện chính xác).
  - Ngày sinh: `NOT_APPLICABLE` (dừng ngay ở bước số CCCD).
  - Giới tính: `NOT_APPLICABLE`.
  - Họ và tên: `NOT_APPLICABLE`.
  - Không bù đắp dữ liệu mặt trước: ĐẠT.
- **Trạng thái cuối**: `REJECT / CCCD_MISMATCH`
- **Verdict**: `PASS` (Hệ thống xử lý chuẩn xác kịch bản lệch thẻ giữa 2 mặt).

---

### Ca OCR-04: Cặp không khớp thông tin Test1 trong bộ dữ liệu
- **Tệp đầu vào**:
  - Mặt trước: `IMG-011` (`Ảnh mặt sau không khớp thông tin\Test1\front.jpg` - nội dung trùng `IMG-001`)
  - Mặt sau: `IMG-012` (`Ảnh mặt sau không khớp thông tin\Test1\back.jpg` - nội dung trùng `IMG-004`)
- **Đặc điểm ảnh trước khi chạy**: Thẻ 1 mặt trước kết hợp Thẻ 2 mặt sau.
- **Pairing authority**: Tên thư mục ghi không khớp thông tin, nhưng quy tắc an toàn yêu cầu Owner xác nhận kỳ vọng.
- **Kỳ vọng**: Từ chối do lệch số CCCD giữa 2 mặt thẻ.
- **Kết quả thực tế**: Hệ thống phát hiện chính xác số CCCD mặt sau khác mặt trước và trả về lỗi lệch thẻ.
- **Từng assertion**:
  - Số CCCD: `MISMATCH`.
  - Không bù đắp dữ liệu: ĐẠT.
- **Trạng thái cuối**: `REJECT / CCCD_MISMATCH`
- **Verdict**: `NEEDS_PAIR_CONFIRMATION` (Theo quy tắc kiểm thử, nhãn thư mục không tự động coi là ground truth tuyệt đối; cần Owner xác nhận trước khi chốt PASS/FAIL nghiệp vụ).

---

### Ca OCR-05: Cặp không khớp thông tin Test2 trong bộ dữ liệu
- **Tệp đầu vào**:
  - Mặt trước: `IMG-013` (`Ảnh mặt sau không khớp thông tin\Test2\front.jpg` - nội dung trùng `IMG-003`)
  - Mặt sau: `IMG-014` (`Ảnh mặt sau không khớp thông tin\Test2\back.jpg` - nội dung trùng `IMG-002`)
- **Đặc điểm ảnh trước khi chạy**: Thẻ 2 mặt trước kết hợp Thẻ 1 mặt sau.
- **Pairing authority**: Unconfirmed by owner.
- **Kỳ vọng**: Từ chối do lệch thông tin định danh giữa 2 mặt.
- **Kết quả thực tế**: Hệ thống phát hiện số CCCD bóc tách từ mặt sau không khớp với mặt trước và từ chối xử lý.
- **Từng assertion**: Số CCCD: `MISMATCH`.
- **Trạng thái cuối**: `REJECT / CCCD_MISMATCH`
- **Verdict**: `NEEDS_PAIR_CONFIRMATION`.

---

### Ca OCR-06: Mặt sau bị nghiêng (Tilted Card)
- **Tệp đầu vào**:
  - Mặt trước: `IMG-015` (`Ảnh mặt sau không khớp thông tin\Test3\front.jfif`)
  - Mặt sau: `IMG-016` (`Ảnh mặt sau không khớp thông tin\Test3\back (ảnh bị nghiêng).jfif`)
- **Đặc điểm ảnh trước khi chạy**:
  - `IMG-015`: Mặt trước rõ nét (kích thước 1441x981, Laplacian 657.29).
  - `IMG-016`: Mặt sau bị chụp góc nghiêng phối cảnh mạnh (kích thước 1080x1920 dọc, Laplacian 1408.05).
- **Pairing authority**: Unconfirmed by owner.
- **Kỳ vọng**: Tự động nắn chỉnh góc phối cảnh hoặc từ chối quang học nếu góc quá khó đọc; không chấp nhận sai lệch.
- **Kết quả thực tế**:
  - Mặt trước: `status=SUCCESS`.
  - Mặt sau: Thuật toán `detect_and_align_card` và `correct_orientation` nắn chỉnh ảnh về ngang (canonical ID-1). Tuy nhiên, số trích xuất từ dòng 1 không trùng với số mặt trước (do 2 ảnh vốn thuộc thư mục "Không khớp thông tin" hoặc do góc nghiêng làm mất ký tự).
- **Trạng thái cuối**: `REJECT / CCCD_MISMATCH`
- **Verdict**: `NEEDS_PAIR_CONFIRMATION` (Cần xác nhận xem ảnh nghiêng này có cùng chủ thẻ với `IMG-015` hay không).

---

### Ca OCR-07: Đảo lộn mặt trước / mặt sau Test2
- **Tệp đầu vào**:
  - Input Front: `IMG-019` (`ảnh mặt trước, mặt sau bị đảo lộn\Test2\Front.jpg` - nội dung thực tế là MẶT SAU)
  - Input Back: `IMG-020` (`ảnh mặt trước, mặt sau bị đảo lộn\Test2\Back.jpg` - nội dung thực tế là MẶT TRƯỚC)
- **Đặc điểm ảnh trước khi chạy**: Tên file bị gán ngược hoàn toàn so với nội dung hình ảnh thực tế.
- **Pairing authority**: Kịch bản kiểm tra khả năng phát hiện tráo đổi mặt thẻ.
- **Kỳ vọng**: Hệ thống nhận diện đúng bản chất nội dung ảnh, từ chối tải nhầm mặt thẻ; không tin tưởng tên file.
- **Kết quả thực tế**:
  - Input Front (`IMG-019`): `CCCDProcessor` phát hiện không có bố cục khung mặt trước, trả về lỗi: *"Không nhận diện được khung CCCD hoặc góc chụp quá khó quét. Vui lòng chụp rõ mặt trước CCCD."*
  - Input Back (`IMG-020`): `BackCardProcessor.check_for_front_portrait_face` phát hiện có ảnh chân dung người ở bên trái, chặn ngay tại bước phân loại thẻ và trả về lỗi: *"Ảnh tải lên là mặt trước CCCD (có ảnh chân dung). Vui lòng chụp mặt sau có gắn chip và dải mã MRZ."*
- **Từng assertion**:
  - Phân loại mặt trước: `REJECT_WRONG_SIDE` (bắt chính xác).
  - Phân loại mặt sau: `REJECT_FRONT_PORTRAIT_ON_BACK` (bắt chính xác).
- **Trạng thái cuối**: `REJECT / WRONG_SIDE_UPLOAD`
- **Verdict**: `PASS` (Hệ thống hoàn toàn không bị đánh lừa bởi tên file; phân loại chính xác dựa trên thị giác máy tính).

---

### Ca OCR-08: Đảo lộn mặt trước / mặt sau Test1
- **Tệp đầu vào**:
  - Input Front: `IMG-017` (`ảnh mặt trước, mặt sau bị đảo lộn\Test1\front.jpg` - nội dung thực tế là MẶT SAU)
  - Input Back: `IMG-018` (`ảnh mặt trước, mặt sau bị đảo lộn\Test1\back.jpg` - nội dung thực tế là MẶT TRƯỚC)
- **Đặc điểm ảnh trước khi chạy**: Tên file ngược với nội dung hình ảnh.
- **Pairing authority**: Kịch bản kiểm tra khả năng phát hiện tráo đổi mặt thẻ.
- **Kỳ vọng**: Từ chối upload sai mặt thẻ; fail-closed.
- **Kết quả thực tế**:
  - Input Front: Bị từ chối do thiếu các trường mặt trước.
  - Input Back: Bị từ chối ngay do phát hiện ảnh chân dung khuôn mặt trên mặt sau.
- **Trạng thái cuối**: `REJECT / WRONG_SIDE_UPLOAD`
- **Verdict**: `PASS` (Bảo vệ thành công luồng xử lý trước việc đảo mặt thẻ).

---

### Ca OCR-09: Thay thế mặt trước bằng ảnh chân dung thông thường
- **Tệp đầu vào**:
  - Input Front: `IMG-023` (`1 trong 2 ảnh không phải là ảnh của cccd\Test1\front (ảnh sai).jpg`)
  - Input Back: `IMG-024` (`1 trong 2 ảnh không phải là ảnh của cccd\Test1\back.jpg` - Mặt sau CCCD hợp lệ)
- **Đặc điểm ảnh trước khi chạy**: `IMG-023` là ảnh avatar chân dung kích thước nhỏ (100x100 pixel), hoàn toàn không phải giấy tờ tùy thân.
- **Pairing authority**: Test scenario (Non-document substitution).
- **Kỳ vọng**: Từ chối mặt trước; không xác nhận eKYC; không cho phép dùng mặt sau bù đắp cho mặt trước.
- **Kết quả thực tế**:
  - `CCCDProcessor` quét `IMG-023`, phát hiện kích thước không hợp lệ và không có khung thẻ CCCD, trả về lỗi: *"Không nhận diện được khung CCCD hoặc góc chụp quá khó quét. Vui lòng chụp rõ mặt trước CCCD."*
  - Luồng xử lý dừng lại an toàn ngay ở mặt trước.
- **Trạng thái cuối**: `REJECT / NON_DOCUMENT_DETECTED`
- **Verdict**: `PASS` (Chặn thành công ảnh không phải tài liệu CCCD).

---

### Ca OCR-10: Thay thế mặt trước bằng thẻ không phải CCCD
- **Tệp đầu vào**:
  - Input Front: `IMG-025` (`1 trong 2 ảnh không phải là ảnh của cccd\Test2\front (ảnh sai).jpg`)
  - Input Back: `IMG-026` (`1 trong 2 ảnh không phải là ảnh của cccd\Test2\back.jpg` - Mặt sau CCCD)
- **Đặc điểm ảnh trước khi chạy**: `IMG-025` là ảnh thẻ khác hoặc ảnh chụp bị mờ/mất nét (Laplacian variance 35.48 < 40.0).
- **Pairing authority**: Test scenario (Non-CCCD substitution).
- **Kỳ vọng**: Từ chối với thông báo lỗi chất lượng hoặc không nhận diện được thẻ; fail-closed.
- **Kết quả thực tế**:
  - `CCCDProcessor._check_image_quality` kiểm tra phương sai Laplacian của `IMG-025`, phát hiện độ nét 35.5 dưới ngưỡng tiêu chuẩn 40.0.
  - Hệ thống từ chối ngay: *"Ảnh chụp bị mờ hoặc rung tay (độ nét 35.5 < 40.0). Vui lòng giữ chắc camera và chụp lại."*
- **Trạng thái cuối**: `REJECT / IMAGE_QUALITY_ISSUE`
- **Verdict**: `PASS` (Bộ lọc chất lượng quang học mặt trước hoạt động chính xác).

---

### Ca OCR-11: Mặt trước chụp mờ / khoảng cách xa
- **Tệp đầu vào**:
  - Input Front: `IMG-008` (`Mặt trước chụp không rõ\Mặt trước chụp không rõ.jpg`)
  - Input Back: `IMG-002` (Mặt sau CCCD)
- **Đặc điểm ảnh trước khi chạy**: Ảnh chụp từ khoảng cách xa, thẻ nhỏ trong khung hình, mờ nghiêm trọng (Laplacian variance = 20.71).
- **Pairing authority**: Quality test scenario.
- **Kỳ vọng**: Báo lỗi chất lượng mờ và hướng dẫn chụp lại; fail-closed.
- **Kết quả thực tế**:
  - `CCCDProcessor` bắt lỗi mờ: *"Ảnh chụp bị mờ hoặc rung tay (độ nét 20.7 < 40.0). Vui lòng giữ chắc camera và chụp lại."*
- **Trạng thái cuối**: `REJECT / BLUR_DETECTED`
- **Verdict**: `PASS` (Chặn đúng ảnh mờ không đủ tiêu chuẩn bóc tách).

---

### Ca OCR-12: Mặt trước bị che mất thông tin (Họ tên)
- **Tệp đầu vào**:
  - Input Front: `IMG-009` (`Mặt trước chụp không rõ\Mặt trước bị che mất thông tin.jpg`)
  - Input Back: `IMG-002` (Mặt sau CCCD)
- **Đặc điểm ảnh trước khi chạy**: Ảnh mặt trước CCCD bị ngón tay hoặc vật thể che khuất dòng Họ và Tên.
- **Pairing authority**: Occlusion test scenario.
- **Kỳ vọng**: Từ chối do thiếu/che khuất trường bắt buộc; tuyệt đối không lấy họ tên từ MRZ mặt sau điền sang mặt trước.
- **Kết quả thực tế**:
  - `CCCDProcessor` nhận diện được số CCCD, nhưng trường họ tên rỗng do bị che.
  - `CCCDValidator.validate_ho_va_ten` kích hoạt, trả về mã lỗi `INVALID_FULL_NAME`: *"Họ và tên trên thẻ không hợp lệ hoặc bị che khuất (đọc được ''). Vui lòng không che thẻ và chụp lại ảnh rõ nét."*
- **Trạng thái cuối**: `REJECT / INVALID_FULL_NAME`
- **Verdict**: `PASS` (Bảo đảm tính toàn vẹn dữ liệu mặt trước; chặn trường bị che).

---

### Ca OCR-13: Mặt trước bị che thông tin mẫu 2
- **Tệp đầu vào**:
  - Input Front: `IMG-010` (`Mặt trước chụp không rõ\Mặt trước bị che mất thông tin 2.jpg`)
  - Input Back: `IMG-002` (Mặt sau CCCD)
- **Đặc điểm ảnh trước khi chạy**: Ảnh chụp xa, mờ nét và bị che phần thông tin phía dưới (Laplacian variance = 31.04 < 40.0).
- **Pairing authority**: Occlusion/quality test scenario.
- **Kỳ vọng**: Từ chối yêu cầu chụp lại; fail-closed.
- **Kết quả thực tế**: Hệ thống từ chối do độ nét không đạt chuẩn (31.0 < 40.0).
- **Trạng thái cuối**: `REJECT / IMAGE_QUALITY_ISSUE`
- **Verdict**: `PASS` (Chặn ảnh chất lượng kém).

---

### Ca OCR-14: Mặt sau chụp xa / không rõ nét
- **Tệp đầu vào**:
  - Input Front: `IMG-005` (`Mặt sau chụp không rõ\Front.jpg` - trùng `IMG-001`)
  - Input Back: `IMG-006` (`Mặt sau chụp không rõ\Back (không khớp thông tin).jpg`)
- **Đặc điểm ảnh trước khi chạy**: Ảnh mặt sau thẻ nhỏ trong khung, độ nét thấp, tên file ghi chú không khớp thông tin.
- **Pairing authority**: Unconfirmed pairing.
- **Kỳ vọng**: Yêu cầu chụp lại do chất lượng kém hoặc báo lệch thông tin; không xác nhận eKYC.
- **Kết quả thực tế**: Hệ thống bóc tách số CCCD từ dải mã của thẻ `IMG-006` và phát hiện lệch với số mặt trước của `IMG-005`, từ chối xác thực.
- **Trạng thái cuối**: `REJECT / CCCD_MISMATCH`
- **Verdict**: `NEEDS_PAIR_CONFIRMATION` (Cần Owner xác nhận cặp và kỳ vọng chuẩn).

---

### Ca OCR-15: Xung đột giữa nhãn thư mục và bản chất ảnh
- **Tệp đầu vào**:
  - Input Front: `IMG-001` (`Hợp lệ\Test1\Front.jpg`)
  - Input Back: `IMG-007` (`Mặt trước chụp không rõ\Back.jpg` - Trong ma trận ghi `Front.jpg`, nhưng thực tế tệp là `Back.jpg`)
- **Đặc điểm ảnh trước khi chạy**: Tệp nằm trong thư mục mang tên "Mặt trước chụp không rõ", nhưng bản chất ảnh là mặt sau CCCD.
- **Pairing authority**: Unconfirmed pairing.
- **Kỳ vọng**: Xử lý theo đúng bản chất thị giác của ảnh, không phụ thuộc vào đường dẫn; không chấm PASS/FAIL trước khi xác nhận cặp.
- **Kết quả thực tế**: `BackCardProcessor` nhận diện đúng đây là mặt sau CCCD và thực hiện đối chiếu dải MRZ.
- **Trạng thái cuối**: `REJECT / CCCD_MISMATCH`
- **Verdict**: `NEEDS_PAIR_CONFIRMATION`.

---

### Ca OCR-16: Kiểm tra tính phi trạng thái khi thay đổi ảnh (Changed-Side Regression)
- **Tệp đầu vào**:
  - Bước 1: Upload Thẻ 1 Mặt trước (`IMG-001`) + Thẻ 1 Mặt sau (`IMG-002`).
  - Bước 2: Thay thế ảnh mặt sau bằng Thẻ 2 Mặt sau (`IMG-004`).
- **Đặc điểm ảnh trước khi chạy**: Mô phỏng hành vi người dùng đổi ảnh mặt sau khác trên giao diện.
- **Pairing authority**: Kịch bản hồi quy tính phi trạng thái (Statelessness test).
- **Kỳ vọng**: Yêu cầu request mới xử lý độc lập hoàn toàn, không lưu cache cũ, lập tức phát hiện mismatch giữa Thẻ 1 và Thẻ 2.
- **Kết quả thực tế**:
  - Lần chạy thứ hai xử lý độc lập trên ảnh mới.
  - Hệ thống bóc tách chính xác số CCCD của Thẻ 2 từ `IMG-004`, đối chiếu với số CCCD Thẻ 1 của `IMG-001`, và ném lỗi lệch số CCCD ngay lập tức.
  - Không có hiện tượng rò rỉ dữ liệu hay lưu giữ trạng thái của lần quét trước.
- **Từng assertion**:
  - Tính độc lập của phiên: ĐẠT.
  - Phát hiện lệch thẻ sau khi đổi ảnh: ĐẠT.
- **Trạng thái cuối**: `REJECT / CCCD_MISMATCH`
- **Verdict**: `PASS` (Cơ chế phi trạng thái hoạt động hoàn hảo).

---

## 4. Các phát hiện kỹ thuật trọng tâm & Đề xuất kiến trúc

> [!IMPORTANT]
> **Tuân thủ quy tắc kiểm thử**: Agent **không tự ý sửa mã nguồn**. Các phát hiện dưới đây được ghi lại chi tiết cùng nguyên nhân gốc rễ và đề xuất giải pháp kỹ thuật để các kỹ sư/agent kế tiếp thực hiện sau khi có phê duyệt.

### Phát hiện 1: Lỗi trích xuất số CCCD dòng 1 MRZ trong Offline Fallback
- **Hiện tượng**: Tại ca `OCR-01`, ảnh mặt sau `IMG-002` rõ nét và chứa dải MRZ chuẩn, nhưng `BackCardProcessor.extract_mrz_lines` lại trích xuất ra một chuỗi 12 chữ số không phải số CCCD (chứa các chữ số phụ phía trước), dẫn đến báo sai lệch số CCCD so với mặt trước.
- **Nguyên nhân kỹ thuật**:
  - Chuỗi dòng 1 MRZ chuẩn ICAO TD1 của CCCD Việt Nam có định dạng: `IDVNM` (5 ký tự) + 10 ký tự số phụ + 12 số CCCD + ký tự kiểm tra (tổng 30 ký tự).
  - Khi chạy offline bằng VietOCR, chuỗi ký tự nhận diện được có thể bị thiếu 1 ký tự đệm (ví dụ nhận diện được 21 chữ số thay vì 22 chữ số sau `IDVNM`).
  - Trong file `services/ekyc-service/cccd_module/back_card_processor.py` (dòng 403-407):
    ```python
    m = (
        re.search(r"\d{10}(\d{12})", cleaned_l1)
        or re.search(r"IDVNM[A-Z0-9]{10}(\d{12})", cleaned_l1)
        or re.search(r"(\d{12})", cleaned_l1)
    )
    ```
  - Khi biểu thức 1 và 2 không khớp do số ký tự phụ bị thiếu 1 chữ số, biểu thức fallback thứ 3 `re.search(r"(\d{12})", cleaned_l1)` sẽ lấy **12 chữ số đầu tiên** gặp được trong chuỗi số (tức là bao gồm các số phụ ở đầu) thay vì lấy **12 chữ số CCCD ở cuối**.
- **Đề xuất khắc phục**:
  - Cải tiến biểu thức chính quy để neo vào cuối chuỗi chữ số hoặc lấy từ phía sau:
    ```python
    # Ưu tiên lấy 12 chữ số cuối cùng trước các ký tự đệm đuôi (<)
    m = (
        re.search(r"IDVNM[A-Z0-9]{9,11}(\d{12})", cleaned_l1)
        or re.search(r"(\d{12})[A-Z0-9<]{0,3}$", cleaned_l1)
        or re.search(r"(\d{12})", cleaned_l1[-16:]) # Quét trong 16 ký tự cuối
    )
    ```

### Phát hiện 2: Chế độ Offline Fallback chưa hỗ trợ bóc tách Dòng 2 và Dòng 3 của dải MRZ
- **Hiện tượng**: Tại ca `OCR-02`, mặt sau đọc đúng số CCCD 12 số trùng với mặt trước, nhưng hệ thống vẫn trả về `success = False` với thông báo: *"Dải mã MRZ ở mặt sau thẻ không đọc đủ thông tin ngày sinh và họ tên. Vui lòng chụp lại ảnh mặt sau thẻ rõ nét hơn."*
- **Nguyên nhân kỹ thuật**:
  - Triển khai hiện tại của `BackCardProcessor.extract_mrz_lines` ở chế độ offline chỉ thực hiện crop và OCR duy nhất dòng 1 (`aligned_image[int(h * 0.68):int(h * 0.78), :]`).
  - Không có logic crop và OCR cho dòng 2 (ngày sinh/giới tính) và dòng 3 (họ và tên).
  - TASK-0108 đã loại bỏ hoàn toàn cơ chế tự sinh dữ liệu (synthetic lines) từ mặt trước để bảo đảm an toàn fail-closed. Do đó, khi không có Gemini Vision, hệ thống bắt buộc phải từ chối xác nhận eKYC.
- **Đề xuất khắc phục**:
  - Bổ sung 2 vùng crop cục bộ cho dòng 2 (`[int(h * 0.78):int(h * 0.88), :]`) và dòng 3 (`[int(h * 0.88):int(h * 0.98), :]`) và truyền qua VietOCR.
  - Sử dụng từ điển ký tự giới hạn cho MRZ OCR (chỉ gồm `A-Z, 0-9, <`) để tăng độ chính xác của mô hình Transformer offline.

### Phát hiện 3: Bộ dữ liệu `TesteKYC` có nhiều tệp trùng lặp nội dung
- **Hiện tượng**: Thư mục `TesteKYC` chứa 26 tệp ảnh, nhưng qua phân tích SHA-256 chỉ có đúng **13 nội dung ảnh duy nhất**:
  - Ảnh mặt sau CCCD (Thẻ 1) bị trùng lặp tại 5 vị trí khác nhau trong các thư mục `Hợp lệ\Test1`, `1 trong 2 ảnh không phải...`, `Mặt trước chụp không rõ`, `Ảnh mặt sau không khớp...`, `ảnh mặt trước... bị đảo lộn`.
  - Ảnh mặt sau CCCD (Thẻ 2) bị trùng lặp tại 4 vị trí khác nhau.
  - Ảnh mặt trước CCCD (Thẻ 1) bị trùng lặp tại 4 vị trí khác nhau.
  - Ảnh mặt trước CCCD (Thẻ 2) bị trùng lặp tại 3 vị trí khác nhau.
- **Khuyến nghị**: Chủ dự án cần rà soát lại nguồn thu thập và xác nhận bảng ánh xạ cặp thẻ chuẩn (Ground Truth Mapping) trước khi đưa bộ dữ liệu vào làm bài kiểm tra chấp nhận hồi quy tự động (CI/CD regression suite).

---

## 5. Lệnh thực thi & Quy trình tái hiện

Toàn bộ quá trình kiểm thử được tự động hóa bằng script chạy độc lập tại môi trường sandbox:

```powershell
# 1. Chạy kiểm tra test suite hiện có của dịch vụ eKYC
pytest services/ekyc-service/tests

# 2. Phân tích quang học và trích xuất đặc trưng hình ảnh (Zero PII)
python "C:\Users\Legion\.gemini\antigravity-ide\brain\e022494a-f7d4-425c-911f-7291e7657d87\scratch\eval_images.py"

# 3. Chạy 16 kịch bản kiểm thử trên bản sao tạm biệt lập
python "C:\Users\Legion\.gemini\antigravity-ide\brain\e022494a-f7d4-425c-911f-7291e7657d87\scratch\run_all_scenarios.py"

# 4. Xác minh tính toàn vẹn và nguyên vẹn của 26 tệp ảnh nguồn
python "C:\Users\Legion\.gemini\antigravity-ide\brain\e022494a-f7d4-425c-911f-7291e7657d87\scratch\verify_hashes.py"
```

**Kết quả kiểm tra toàn vẹn**:
```text
Total source files checked: 26
Source files unchanged: YES
```

---

## 6. Kết luận & Cam kết tuân thủ ranh giới an toàn

1. **Bảo toàn mã nguồn & cơ sở dữ liệu**:
   - Không có bất kỳ dòng code, tệp cấu hình, test suite, dependency, hay database migration nào của dự án bị thay đổi, xóa bỏ hoặc ghi đè.
   - Toàn bộ working tree chưa commit của repository được bảo toàn 100%.
2. **Không gọi API bên ngoài**:
   - Toàn bộ quá trình chạy đều thực hiện offline/local.
   - Không gửi dữ liệu hoặc ảnh CCCD tới Google, Gemini, hay bất kỳ bên thứ ba nào.
3. **Bảo vệ dữ liệu định danh**:
   - Báo cáo hoàn toàn không chứa PII.
   - Không lưu trữ ảnh hoặc vector đặc trưng khuôn mặt ngoài các thư mục tạm đã được giải phóng.
4. **Trạng thái tệp nguồn**:
   - Toàn bộ 26 tệp ảnh trong thư mục `D:\Khanh\Old but Gold\Source Code\TesteKYC` giữ nguyên vẹn 100% hash SHA-256 so với thời điểm trước khi bắt đầu nhiệm vụ.
