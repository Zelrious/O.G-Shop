# Test Strategy

## Test pyramid

1. Unit test cho domain rule có nhánh logic.
2. Slice/integration test cho authorization, persistence và API contract.
3. PostgreSQL integration test cho constraint, migration và concurrency.
4. Một số end-to-end test cho core flow.

## Mandatory risk tests

- Hai Buyer checkout cùng một Product bằng connection độc lập.
- Payment callback lặp và callback đến sau timeout.
- Auto-complete chạy đồng thời với tạo Complaint.
- Hai Admin resolve cùng Complaint theo hai hướng khác nhau.
- User thay ID của Order, Conversation, Evidence hoặc Address.
- Product/Address thay đổi sau checkout không làm thay snapshot lịch sử.
- Restart giữa trạng thái pending và chạy lại scheduled job.
- Backup/restore giữ được quan hệ giao dịch.

## Completion rule

Một task chỉ được đánh dấu `VERIFIED` khi:

- Kiểm thử liên quan đã chạy và có kết quả ghi trong task file.
- Build/lint/typecheck phù hợp đã qua.
- Module document và traceability đã được cập nhật nếu hành vi thay đổi.

Coverage phần trăm không thay thế kiểm thử theo rủi ro.
