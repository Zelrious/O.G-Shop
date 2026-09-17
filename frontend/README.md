# O.G Shop Frontend

React/TypeScript frontend cho Old but Gold, tổ chức theo feature và user journey.

## Lệnh chính

```powershell
npm install
npm run dev
npm run lint
npm run typecheck
npm test
npm run build
```

## Quy tắc

- `app`: bootstrap, provider và cấu hình cấp ứng dụng.
- `pages`: ghép feature thành route/page.
- `features`: hành vi người dùng theo nghiệp vụ.
- `shared`: UI, API client, type và utility không phụ thuộc feature.
- Không gọi API trực tiếp từ component trình bày.
- Mọi trạng thái async phải có loading, empty, error và success khi phù hợp.
- Kiểm tra quyền ở Backend; Frontend chỉ điều khiển trải nghiệm hiển thị.
