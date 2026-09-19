# O.G Shop eKYC AI Microservice

FastAPI service nội bộ cho luồng eKYC mô phỏng của O.G Shop. Browser không gọi service này; Spring Boot là gateway duy nhất và gửi `X-Internal-Token` cho mọi endpoint xử lý.

## Ranh giới bảo mật

- Chỉ dùng ảnh/CCCD tổng hợp hoặc dữ liệu sandbox.
- Không trả face embedding qua API và không lưu embedding trên singleton.
- Card embedding và live embedding chỉ tồn tại trong memory cục bộ của một request so khớp.
- File OCR tạm bị xóa trong `finally` sau request.
- Gemini tắt mặc định; khi bật vẫn chỉ xử lý request được đánh dấu synthetic.
- File upload chỉ nhận JPEG, PNG, WebP và tối đa 4 MB theo cấu hình.

## Endpoint

- `GET /api/v1/ekyc/health`: health check, không yêu cầu credential.
- `POST /api/v1/ekyc/ocr`: OCR `multipart/form-data`; yêu cầu `X-Internal-Token`.
- `POST /api/v1/ekyc/match-face`: nhận ảnh thẻ và live frame dạng base64; yêu cầu `X-Internal-Token`.

Response chỉ chứa thông tin OCR, kết quả match, distance, threshold và metadata model; không chứa vector sinh trắc học.

## Cấu hình

Sao chép `.env.example` thành `.env` và thay placeholder local:

```text
EKYC_INTERNAL_TOKEN=<random internal credential>
EKYC_ALLOWED_ORIGINS=http://localhost:8080
EKYC_GEMINI_POSTPROCESSING_ENABLED=false
EKYC_ALLOW_SYNTHETIC_DATA_ONLY=true
MAX_FILE_SIZE_MB=4
```

`EKYC_INTERNAL_TOKEN` phải khớp với biến môi trường của Spring Boot. Không đưa token này vào biến `VITE_*`.

## Chạy local

```powershell
python services/ekyc-service/start.py
```

Service mặc định bind `127.0.0.1:8001`. Swagger dùng cho local development tại `http://localhost:8001/docs`.

## Kiểm tra contract bảo mật

```powershell
cd services/ekyc-service
python -m unittest discover -s tests -v
python -m py_compile app.py biometric_module/face_matcher.py
```
