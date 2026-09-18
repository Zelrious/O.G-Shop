# Prompt triển khai giao diện O.G Shop bằng Gemini

Sao chép toàn bộ nội dung trong khối lệnh dưới đây vào Gemini CLI tại thư mục gốc của repository. Có thể chạy `/project:design-ui-screens` trước để Gemini nạp workflow UI, rồi gửi prompt này.

```text
Bạn đang làm việc trong repository của “Old but Gold” (O.G Shop), một nền tảng mua bán đồ cũ đáng tin cậy phục vụ đồ án môn học.

Mục tiêu của bạn là thiết kế và code giao diện React/TypeScript cho các màn hình của O.G Shop theo từng đợt nhỏ có thể kiểm tra. Không triển khai tất cả màn hình trong một lần và không biến giả định thành yêu cầu đã được duyệt.

1. Nạp ngữ cảnh bắt buộc

- Đọc `AGENTS.md`, `GEMINI.md`, `docs/progress/STATUS.md`, `docs/modules/INDEX.md`, `docs/requirements/REQUIREMENTS_INDEX.md` và `docs/requirements/TRACEABILITY_MATRIX.md`.
- Đọc `.agents/rules/approval-and-reporting.md`, `.agents/rules/frontend.md`, `.agents/workflows/session-start.md`, `.agents/workflows/feature-delivery.md`, `.agents/skills/frontend-feature-change/SKILL.md` và `.agents/skills/marketplace-ui-implementation/SKILL.md`.
- Đọc tài liệu của mọi module liên quan đến đợt màn hình được đề xuất.
- Kiểm tra `frontend/package.json`, toàn bộ cấu trúc liên quan trong `frontend/src`, các test hiện có và `git diff`. Dùng trạng thái thực tế trong repository, không dựa vào trí nhớ hay giả định.

2. Giới hạn nguồn sự thật

- 19 use case trong `REQUIREMENTS_INDEX.md` là phạm vi chức năng hiện có. Chi tiết BR/NFR chính thức chưa được nhập đầy đủ; vì vậy mọi hành vi chưa được tài liệu xác nhận phải ghi rõ là `ASSUMPTION` hoặc `PROPOSAL`.
- Không tự tạo API endpoint, DTO, quyền, trạng thái domain, quy tắc thanh toán, vận chuyển, KYC hoặc tranh chấp. Nếu backend/API chưa tồn tại, dùng typed fixture hoặc mock adapter nằm sau ranh giới feature và ghi rõ đây chỉ là dữ liệu mô phỏng cho UI.
- Không sửa Backend, Database, API contract, dependency hay kiến trúc nếu chưa có phê duyệt riêng.
- Không thêm thư viện UI, router, state management, icon hoặc CSS framework nếu chưa giải thích nhu cầu, ưu điểm, trade-off và được chủ dự án phê duyệt.
- Chỉ dùng dữ liệu giả, không dùng dữ liệu cá nhân, KYC hay bằng chứng khiếu nại thật.

3. Nguyên tắc thiết kế

- Áp dụng skill `marketplace-ui-implementation` làm chuẩn UI. Hai file tham khảo ban đầu đến từ design system hướng Mercari; chỉ học cách tổ chức token, component state, responsive và accessibility. Không sao chép tên, logo, nội dung, bố cục hay nhận diện Mercari.
- Trình bày một bảng token đề xuất cho O.G Shop trong báo cáo xin duyệt. Bảng màu tối từ tài liệu tham khảo chỉ là ứng viên; hãy đánh giá lại theo nhận diện “Old but Gold”, độ tương phản WCAG 2.2 AA và tính dễ đọc trước khi dùng.
- Dùng semantic token cho màu, typography, spacing, radius, shadow và motion. Component không rải raw value lặp lại.
- Mọi thành phần tương tác phải xem xét các trạng thái phù hợp: default, hover, focus-visible, active, disabled, loading, error và success.
- Mọi màn hình dữ liệu phải có trạng thái loading, empty, error, retry và unauthorized khi phù hợp.
- Thiết kế mobile-first; kiểm tra tối thiểu mobile, tablet và desktop. Xử lý chuỗi tiếng Việt dài, giá tiền, địa chỉ dài, ảnh lỗi hoặc thiếu và danh sách rỗng.
- Dùng HTML semantic, điều hướng bàn phím, nhãn form bền vững, focus dễ thấy, thông báo lỗi rõ ràng và touch target phù hợp.
- Tín hiệu uy tín phải nói chính xác điều đã được xác minh. Không khiến người dùng hiểu rằng mock KYC, sandbox payment hoặc fake shipping là dịch vụ production.

4. Bản đồ màn hình đề xuất theo use case

Hãy kiểm chứng danh sách này với tài liệu module và đề xuất chia thành các batch nhỏ. Đây là bản đồ ứng viên, chưa phải lệnh triển khai đồng thời:

- Public/Guest — UC-01, UC-04: app shell/navigation, trang khám phá hoặc tìm kiếm, kết quả tìm kiếm và bộ lọc, chi tiết sản phẩm, đăng nhập, đăng ký; có 404 và trạng thái lỗi chung.
- Buyer — UC-01, UC-04–08, UC-10–12, UC-14–15, UC-19: hồ sơ/địa chỉ; chat và trả giá; giỏ hàng; checkout; thanh toán mô phỏng; danh sách/chi tiết đơn; theo dõi vận chuyển; xác nhận kiểm tra hàng; tạo/theo dõi khiếu nại; hoàn hàng/hoàn tiền; đánh giá; thông báo.
- Seller — UC-02–06, UC-09–10, UC-12, UC-15, UC-19: gửi và xem trạng thái xác minh Seller; danh sách tin; tạo/sửa/xem trước tin; chat/trả giá; danh sách/chi tiết đơn bán; xác nhận xử lý và bàn giao vận chuyển; phản hồi khiếu nại; uy tín và thông báo.
- Admin/Moderator — UC-02, UC-13, UC-16–19: dashboard; hàng chờ xác minh Seller; kiểm duyệt báo cáo/tin; hồ sơ tranh chấp và quyết định có lý do; quản lý user/quyền hoặc hạn chế tài khoản; audit và thông báo quản trị.

Không thêm wishlist, voucher, ví thật, social login, đấu giá, partial refund hoặc tính năng khác nếu không có yêu cầu được duyệt.

5. Trình tự bắt buộc trước khi code

Trước tiên, hãy chỉ lập “BÁO CÁO ĐỀ XUẤT TRƯỚC THAY ĐỔI” và dừng để tôi phê duyệt. Báo cáo phải có:

- sự thật đã kiểm chứng, giả định và điểm còn thiếu;
- batch màn hình đầu tiên được đề xuất và UC/role tương ứng;
- user flow của batch dưới dạng Mermaid đơn giản;
- route/page/feature/shared ownership dự kiến;
- component và design token dự kiến tái sử dụng;
- mọi trạng thái UI và hành vi responsive;
- phương án mock/API boundary;
- danh sách file tạo/sửa;
- output dự kiến và tiêu chí nghiệm thu có thể quan sát;
- cách kiểm tra lint, typecheck, test, build và accessibility;
- ưu điểm, trade-off, rủi ro và lý do chọn phương án này thay vì các phương án gần nhất;
- các đoạn code trọng tâm dự kiến và giải thích cách chúng hoạt động ở mức phù hợp cho người học.

Không chỉnh sửa file trong bước báo cáo. Chỉ bắt đầu code sau khi tôi phê duyệt rõ batch và phương án. Nếu trong lúc code cần đổi phạm vi, dependency hoặc hướng kỹ thuật đáng kể, hãy dừng và xin phê duyệt lại.

6. Quy tắc khi được phê duyệt triển khai

- Tạo hoặc cập nhật task sản phẩm theo quy định repository khi bắt đầu code.
- Giữ page composition trong `frontend/src/pages`, logic theo domain trong feature sở hữu, và primitive/token dùng chung không phụ thuộc domain trong `frontend/src/shared`.
- Ưu tiên CSS và dependency hiện có. Tránh abstraction sớm; chỉ tách component khi có trách nhiệm rõ hoặc được tái sử dụng thật.
- Viết test có ý nghĩa cho hành vi tương tác, phân quyền hiển thị và trạng thái dễ hồi quy; không viết test chỉ lặp lại markup tĩnh.
- Chạy các kiểm tra đã phê duyệt và chỉ báo thành công khi đã quan sát exit code/kết quả.
- Đồng bộ tài liệu module và tiến trình chỉ cho thay đổi sản phẩm thực tế, không ghi các bước setup thuần kỹ thuật thành task sản phẩm.

7. Báo cáo sau triển khai

Sau khi hoàn tất batch, báo cáo bằng tiếng Việt:

- file đã thay đổi và phạm vi tác động;
- trước và sau thay đổi bằng hành vi quan sát được;
- giải thích code trọng tâm và luồng dữ liệu từ fixture/mock adapter đến component;
- kết quả từng lệnh kiểm tra;
- phần đã hoàn thành, chưa hoàn thành, giới hạn của mock và rủi ro còn lại;
- đối chiếu từng tiêu chí nghiệm thu và UC;
- đề xuất batch tiếp theo nhưng không tự triển khai khi chưa được duyệt.

Bây giờ hãy đọc repository và chỉ đưa ra báo cáo đề xuất cho batch UI đầu tiên. Không code trong phản hồi đầu tiên.
```

