# Frontend rules

- Tổ chức theo feature; `shared/` chỉ chứa phần không phụ thuộc nghiệp vụ cụ thể.
- Page điều phối layout và route, không chứa toàn bộ business logic.
- Mọi trạng thái loading, empty, error, unauthorized và success phải được cân nhắc.
- Không dựa vào ẩn nút để bảo mật; Backend vẫn là nơi thực thi authorization.
- Dữ liệu từ API phải có type và được xử lý lỗi rõ ràng.
- Tránh dependency mới nếu Web API hoặc thành phần hiện có đáp ứng đủ.
