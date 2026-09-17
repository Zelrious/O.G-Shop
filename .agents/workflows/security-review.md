# Security review

1. Liệt kê asset, actor, trust boundary và dữ liệu nhạy cảm.
2. Review authentication, authorization, validation, injection, secret, logging và abuse.
3. Với payment/webhook, review signature, replay, idempotency và state transition.
4. Gắn mỗi phát hiện với file/flow và mức ảnh hưởng.
5. Đề xuất sửa cùng test chứng minh; chờ duyệt trước khi thay đổi.
6. Chạy negative-path test và ghi giới hạn còn lại.
