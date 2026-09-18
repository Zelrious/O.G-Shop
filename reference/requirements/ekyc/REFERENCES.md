# Tài liệu Đánh giá các Repository Tham khảo (Reference Repositories)

Tài liệu này tổng hợp phân tích từ 3 repository tham khảo quan trọng được sử dụng để xây dựng hệ thống eKYC (CCCD OCR & Face Matching) hiện tại. Các kiến trúc và thuật toán từ các repo này đóng vai trò quan trọng trong việc xây dựng luồng xử lý nhận diện.

---

## 1. Repo: extract-information-from-identity-card
- **Thư mục gốc:** `Tài liệu tham khảo/extract-information-from-identity-card-master`
- **Mục đích:** Xây dựng pipeline cơ bản cho việc trích xuất thông tin từ ảnh CCCD (Pipeline 3 bước).
- **Kiến trúc Pipeline:**
  1. **Corner Detection and Alignment (Phát hiện góc & Căn chỉnh):** Tìm 4 góc của thẻ CCCD trong ảnh để thực hiện các phép biến đổi hình học (Perspective Transform) giúp thẻ vuông vức.
  2. **Text Detection (Phát hiện vùng chữ):** Crop ra các vùng nhỏ chứa thông tin (Họ tên, Ngày sinh, Quê quán...).
  3. **Text Recognition (Nhận diện chữ):** Sử dụng mô hình **VietOCR** để chuyển đổi hình ảnh vùng chữ thành text (`JSON` format).
- **Ghi chú quan trọng (Cách lấy weights):** 
  - Repo này sử dụng trọng số **transformerocr.pth** cho bước Text Recognition. 
  - Trọng số này không có sẵn trong source code mà phải **tải về từ Google Drive** (theo hướng dẫn trong `README.md` của repo: [Link tải weights](https://drive.google.com/file/d/1pXftFiTGzcXNqsy6jKxQF2WyiOoBmDKU/view?usp=sharing)), sau đó đặt vào thư mục `src/vietocr/config_text_recognition/`. 
  - *(Đây chính là nguyên nhân gây ra lỗi thiếu file `transformerocr.pth` trong code hiện tại của dự án chúng ta).*

---

## 2. Repo: Vietnamese_ID_Card_OCR
- **Thư mục gốc:** `Tài liệu tham khảo/Vietnamese_ID_Card_OCR-main`
- **Mục đích:** Một hệ thống OCR CCCD rất toàn diện, tích hợp nhiều công nghệ mới và có cấu trúc dự án chuẩn chỉ (API + Web UI + Metrics).
- **Công nghệ nổi bật (Tech Stack):**
  - **Corner Detection:** Sử dụng **YOLOv11n** (phiên bản YOLO rất mới, tối ưu hóa tốc độ và độ chính xác).
  - **Text Detection:** Hỗ trợ linh hoạt cả **YOLO** và **PaddleOCR (DB Model)**.
  - **Text Recognition:** Dùng **VietOCR VGG-Transformer**.
  - **Text Correction (Sửa lỗi chính tả):** Tích hợp mô hình Transformers (`bmd1905/vietnamese-correction-v2`) và thuật toán Levenshtein để sửa lỗi kết quả OCR, đây là một tính năng cực kì nâng cao giúp tăng độ chính xác.
  - **QR Code Decoding:** Hỗ trợ quét và giải mã mã QR trên CCCD bằng thư viện `qreader`.
- **Cấu trúc & Triển khai:**
  - Hỗ trợ giao diện Web tương tác bằng **Streamlit**.
  - Hỗ trợ Backend API bằng **FastAPI** (có các tính năng như Batch Processing, History Management với MongoDB, Duplicate Detection).
- **Đánh giá ứng dụng:** Repo này là một kho báu để tham khảo về cách tối ưu API (thêm Rate Limit, MongoDB) và cách sử dụng YOLOv11 cho bài toán phát hiện góc thẻ.

---

## 3. Repo: VietnameseIDCard
- **Thư mục gốc:** `Tài liệu tham khảo/VietnameseIDCard-main`
- **Mục đích:** Xây dựng hệ thống quét CCCD có tích hợp sẵn môi trường Docker và Frontend hoàn chỉnh.
- **Cấu trúc & Công nghệ:**
  - **Frontend:** Xây dựng bằng **Vue.js** (Vue 3, Vue Router, Webpack), mã nguồn đặt tại `src/ui`. Cung cấp một giao diện SPA (Single Page Application) chuyên nghiệp cho quá trình upload và hiển thị dữ liệu CCCD.
  - **Môi trường & Triển khai:** Cung cấp sẵn các file `docker-compose.yaml`, `setup.sh`, `systemd` giúp dễ dàng đóng gói và deploy hệ thống lên các server production.
  - **Backend AI:** Dựa trên PyTorch (`torch==2.5.0 torchvision==0.20.0`).
- **Đánh giá ứng dụng:** Có thể tham khảo repo này để xây dựng module **Frontend (Web App)** ở Bước 2 trong Roadmap, đặc biệt là cách setup Vue.js giao tiếp với Backend và quy trình Dockerize dự án ở giai đoạn sau.

---

## Tổng kết & Đề xuất cho Dự án hiện tại
1. **Khắc phục lỗi OCR hiện tại:** Chúng ta cần tải file `transformerocr.pth` từ link Drive của Repo 1 và đưa vào code của chúng ta để module VietOCR có thể chạy được.
2. **Nâng cấp OCR (Future Task):** Tham khảo mô hình sửa lỗi chính tả (`vietnamese-correction-v2`) từ Repo 2 để tăng tỷ lệ chính xác (Accuracy) cho text được bóc tách.
3. **Phát triển Web App:** Khi tiến hành task Frontend trong Roadmap, có thể tái sử dụng/tham khảo kiến trúc UI (Vue.js) từ Repo 3 hoặc sử dụng Streamlit như Repo 2 để làm Prototype nhanh chóng.
