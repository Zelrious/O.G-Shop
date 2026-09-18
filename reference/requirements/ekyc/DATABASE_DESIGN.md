# Thiết kế Database Cơ sở dữ liệu eKYC

## Tổng quan
Dự án eKYC sử dụng **PostgreSQL** kết hợp cùng Extension **PGVector** để lưu trữ và truy vấn hồ sơ người dùng. Việc sử dụng DB quan hệ truyền thống kết hợp với vector storage giúp dự án dễ dàng triển khai, dễ quản lý theo cấu trúc ACID, và tối ưu cho tốc độ xác thực sinh trắc học (Biometric authentication).

## Bảng `sellers`
Đây là bảng chính lưu trữ toàn bộ hồ sơ của người bán hàng trên nền tảng.

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa |
| :--- | :--- | :--- | :--- |
| `id` | SERIAL | PRIMARY KEY | ID tự tăng của hệ thống. |
| `cccd_number` | VARCHAR(20) | UNIQUE, NOT NULL | Số CCCD trích xuất qua OCR (Dùng làm định danh duy nhất chống trùng lặp). |
| `full_name` | VARCHAR(100) | NOT NULL | Họ và tên. |
| `dob` | VARCHAR(20) | | Ngày sinh. |
| `hometown` | TEXT | | Quê quán. |
| `address` | TEXT | | Địa chỉ thường trú. |
| `face_embedding` | vector(512) | | Lưu mảng 512 phần tử (đặc trưng khuôn mặt) do mô hình ArcFace chiết xuất. Dùng cho thuật toán tìm kiếm khoảng cách. |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Thời điểm đăng ký. |

## Tại sao chọn PGVector?
- **All-in-one:** Trong cùng một bảng `sellers`, ta vừa có thể truy vấn bằng text `SELECT * FROM sellers WHERE cccd_number = '...'`, vừa có thể lôi được ngay Vector khuôn mặt ra để nhận diện mà không cần đồng bộ qua một hệ thống VectorDB thứ 3 (như Milvus hay Qdrant). Điều này đặc biệt phù hợp cho bài toán eKYC 1:1 (Xác thực xem tôi có đúng là người sở hữu mã CCCD này không).

## Toán tử truy vấn Vector (Cosine Distance)
Khi đăng nhập, ứng dụng không dùng AI quét mặt liên tục để chọc vào DB (vì nó rất nặng). Thay vào đó, app truy xuất thẳng `face_embedding` của dòng tương ứng bằng khóa `cccd_number` thông qua B-Tree Index siêu nhanh của Postgres.
Sau đó, ứng dụng tính khoảng cách Cosine Distance ngay trên RAM bằng numpy:
```python
distance = 1.0 - (dot_product / (norm_id * norm_live))
```
Tuy nhiên, nếu sau này dự án muốn nâng cấp thành bài toán truy vấn 1:N (Quét mặt một cái là biết ngay ID của nhân viên), PostgreSQL Vector hỗ trợ toán tử `<=>` (Cosine Distance) để truy vấn trực tiếp bằng SQL:
```sql
SELECT id, cccd_number, full_name, (face_embedding <=> '[0.1, 0.2, ...]') AS distance 
FROM sellers 
ORDER BY face_embedding <=> '[0.1, 0.2, ...]' 
LIMIT 1;
```
