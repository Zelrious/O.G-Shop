# Database V22–V23 — gộp 104 nguồn thành 48 bảng nghiệp vụ

**Checkpoint cuối:** local og_shop đã ở **V23**, 23 migration success/0 failed, **48 bảng nghiệp vụ/49 public tables**. V22 gộp dữ liệu; V23 căn bộ đếm ID của aggregate dùng chung. Backup, restore, proof 104 nguồn, 252 tests và live Hibernate/HTTP đều đạt. Tên file V22 trong dictionary/SQL chỉ phiên bản cấu trúc gộp, catalog xuất hiện hành là V23.

Người dùng đã duyệt việc gộp ngày 09-10-2026. V22 là migration tiếp theo của [V21/83 use case](DATABASE_EXPANSION_V21_20261009.md); V1–V21 giữ nguyên. Mục tiêu là giảm số bảng trong ERD và báo cáo, giữ dữ liệu và ràng buộc đã thiết kế theo use case.

## Kết quả cấu trúc

Schema `public` gồm **48 bảng nghiệp vụ**, cộng `flyway_schema_history` thành **49 bảng vật lý**. Có 7 view báo cáo trong `public` và 104 view tương thích trong `og_compat`. View là phép chiếu lên dữ liệu trong 48 bảng, không lưu thêm bản sao hay giữ lại 104 bảng cũ. Schema chuyển đổi tạm `_og70_v21` bị xóa khi transaction thành công.

| Nhóm | Số bảng | Các bảng chính |
|---|---:|---|
| Tài khoản/xác minh | 11 | users, roles, user_roles, addresses, refresh_sessions, auth_challenges, ekyc_profiles, ekyc_private_assets, identity_document_registry, verification_attempts, seller_profiles |
| Tin đăng/chính sách | 7 | categories, products, product_categories, product_revisions, product_media, listing_fee_charges, business_policies |
| Trao đổi | 3 | conversations, messages, offers |
| Mua hàng/ưu đãi | 9 | cart_items, checkout_groups, orders, order_items, inventory_reservations, vouchers, voucher_products, voucher_grants, reward_ledger |
| Thanh toán | 6 | payments, payment_attempts, payment_events, payment_confirmations, order_fund_components, settlement_operations |
| Giao nhận | 2 | shipments, shipment_events |
| Khiếu nại/tranh chấp | 3 | cases, case_actions, case_evidence |
| Quản trị/thông báo | 7 | reviews, seller_buyer_blocks, penalty_ledger, notifications, audit_logs, outbox_events, interaction_events |
| **Tổng** | **48** | Giảm **56 bảng** so với V21 |

[Danh mục đầy đủ 48 bảng, thuộc tính, PK/FK và ánh xạ 104 nguồn](../../database/docs/DATABASE_TABLE_DICTIONARY_V22.md). [DDL 48 bảng để dựng ERD](../../database/schema/og_shop_v22_tables_for_erd.sql).

## Những phần được gộp

- Cấu hình 2FA, tài khoản điểm, Google identity, hạn chế tài khoản và đầu giỏ gắn với `users`; dòng giỏ vẫn nằm trong `cart_items`. `users.reward_balance` và `email_2fa_enabled` là cột generated để đọc trực tiếp, ghi qua nghiệp vụ tương ứng.
- OTP/reset/quick-auth dùng chung `auth_challenges`; các lần thử/metric dùng `verification_attempts`. Legacy seller verification nằm trong `ekyc_profiles` với provenance riêng, không tự trở thành hồ sơ eKYC đã duyệt theo workflow mới.
- Quyết định eKYC/Seller, kiểm tra AI/media và quyết định duyệt tin lưu cùng hồ sơ hoặc phiên bản tin bằng JSONB lịch sử. Ảnh/video cũ và revision media nằm trong `product_media`, có liên kết product/revision.
- Năm loại policy gom vào `business_policies`. Assessment/charge gom vào `listing_fee_charges`; receipt vẫn nằm trong `payment_confirmations` để phân biệt khoản dự kiến với tiền đã xác nhận.
- Trạng thái hội thoại/quota media gắn với `conversations`; voucher revision nằm trong `vouchers`; grant history gắn `voucher_grants`; redemption/checkout 0đ gắn `checkout_groups`; discount allocations gắn `order_items`.
- Intent và payment legacy cùng `payments`; hai loại attempt cùng `payment_attempts`; receipt thô và parsed callback cùng `payment_events`. `record_type` phân biệt vai trò của từng dòng; không coi receipt thô là callback đã xác thực. Allocation và fund component cùng `order_fund_components` nhưng giữ source identity riêng.
- Pickup/handover/events gom `shipment_events`; case/complaint/report/reconciliation gom `cases`; rounds/decisions/events/holds/review audit gom `case_actions`; evidence và unboxing metadata gom `case_evidence`.
- Review edit history, notification delivery và outbox consumer checkpoint lưu trên chủ thể. Các loại thông báo gom `notifications`; audit/status/order event/export run gom `audit_logs`.

Các khoản tiền vẫn có cột số và nguồn xác nhận; giữ sổ điểm, tồn kho, operation hoàn/giải ngân, private assets, document registry và evidence. Chúng cần ràng buộc đồng thời hoặc lịch sử rõ ràng, nên không chuyển thành thao tác phân biệt thủ công của người dùng.

## Backend và ràng buộc

Các entity hiện có ánh xạ view `og_compat`, gồm cả join table `user_roles`. Hibernate bật xác minh kiểu VIEW; Flyway dùng schema `public`. Các view ghi chuyển thao tác vào aggregate vật lý và gọi lại guard nghiệp vụ cũ. API hiện tại giữ hợp đồng trường/định danh; primitive database cũ truy cập view có kiểu dữ liệu tương ứng.

V22 giữ NOT NULL/CHECK/unique/FK theo từng nguồn thông qua trigger và kiểm tra quan hệ khi commit. Catalog có **191 FK vật lý**; metadata JSONB có kiểm tra quan hệ bằng guard, không tự xuất hiện thành FK riêng trên ERD. Hai FK `NOT VALID` lịch sử vẫn chặn dữ liệu mới sai chủ/attempt; dữ liệu lịch sử không bị tự sửa hoặc xóa. Không sửa trực tiếp mảng JSON lịch sử qua SQL thủ công.

Các phép ghi giữ advisory transaction lock `700048`. Khi một view chọn OLD trước lúc chờ khóa, writer kiểm tra lại OLD trên bảng thật để trả zero-row cho update đã stale; Hibernate `@Version` tiếp tục báo xung đột. Kiểm thử hai quyết định duyệt tin cạnh tranh đã chứng minh chỉ một kết quả thắng.

Đổi lại, đây là một lớp chuyển tiếp có nhiều view/function. Nó giảm số bảng và nơi lưu dữ liệu, nhưng không làm toàn bộ logic nghiệp vụ đơn giản tương ứng. Khóa ghi toàn cục phù hợp quy mô đồ án, hạn chế throughput; khi mở rộng tải cần forward migration để thay bằng khóa aggregate và service truy cập schema vật lý phù hợp. Không bỏ lớp tương thích trước khi chuyển hết query và guard.

Các transaction checkout, confirmation/allocation, listing decision/assessment và case/settlement vẫn phải nguyên tử như [hợp đồng V21](DATABASE_EXPANSION_V21_20261009.md). Schema có nền dữ liệu cho 83 UC; API/UI/worker canonical vẫn chưa được nối đầy đủ, không đổi trạng thái nghiệm thu chỉ từ số bảng.

## Migration, backup và phục hồi

V22 lấy ACCESS EXCLUSIVE lock trên 104 bảng trước khi tính fingerprint. Migration chuyển dữ liệu trong một transaction, đối chiếu count và MD5 trên JSONB toàn bộ cột của từng nguồn trước khi bỏ schema cũ. Sai fingerprint, guard hoặc timeout sẽ rollback. Lock timeout 10 giây, statement timeout 120 giây; cần thử trên bản khôi phục trước khi áp dụng.

Backup custom archive riêng nằm dưới `output/database-consolidation-2026-10-09/` bị Git ignore. Backup chứa dữ liệu riêng tư, không đưa vào Git/báo cáo. Bản khôi phục được kiểm tra trong chính container PostgreSQL local. Image hiện tại thiếu file extension `vector` dù catalog còn đăng ký; đã xác minh không có cột dữ liệu phụ thuộc. Archive đầy đủ vẫn được giữ; archive dùng để khôi phục clone chỉ loại extension không sử dụng này, không loại bảng/dữ liệu.

Container pgvector kiểm thử dùng image digest đã ghim, thông tin đăng nhập riêng và toàn bộ dữ liệu giả; không nhận backup hoặc credentials của ứng dụng.

Không có down migration phá hủy lịch sử. Khi cần khôi phục: restore backup V21 vào database mới với runtime hỗ trợ extension tương ứng, kiểm tra dữ liệu và đổi kết nối có kiểm soát. Sau khi V22 đã áp dụng, sửa tiếp bằng **V24 trở lên**; không chỉnh checksum hoặc regenerate SQL V22 đã chia sẻ.

Nguồn cấu trúc V21 được lưu tại `database/design/v21-consolidation-metadata.json`, không có row data. Hai builder chỉ tái tạo migration để kiểm chứng byte-for-byte, không dùng để sửa V22 đã áp dụng. Export metadata và report builder tạo dictionary/DDL từ catalog PostgreSQL hiện hành; snapshot ERD chỉ dùng trong database trống để vẽ sơ đồ, không thay Flyway hay trigger runtime.

## Kiểm chứng và trạng thái áp dụng

- Bản khôi phục local V21 → V22 đã migrate/validate thành công; migration đối chiếu đủ 104 nguồn.
- Fixture V21 có eKYC/Seller decision, revision/media/fee, voucher, checkout, tiền/giữ tiền, giao hàng, case/evidence và điểm: count/hash cả 104 nguồn khớp sau upgrade; tiếp tục xử lý case sau migration và vẫn chặn giải ngân.
- Kiểm thử PostgreSQL bao gồm stock/refund/points/voucher race, callback dedup, optimistic locking, component funding, late money, source ownership, history immutability và checkout 0đ. Các Spring context kiểm tra Hibernate `ddl-auto=validate` với adapter views.
- Local og_shop đã migrate V22 lúc **15:49:27 ngày09-10-2026**, validate22 đạt: 48 business tables/49 public tables, 22 success/0 failed; 7 report views, 104 adapter views; không còn schema _og70_v21. Fresh backup restore→V22 và live V21→V22 đều khớp count/hash **104/104 nguồn**.
- Maven final: **252 tests, 0 failures, 0 errors, 1 skipped**; 251 pass. Skip symlink Windows thuộc MediaControllerTest. Expansion PostgreSQL **33/33 pass**. FK wrong-Buyer fixture dùng trạng thái PENDING đúng hợp đồng trước khi kiểm tra FK; không nới constraint.
- Backend khởi động riêng trên port18070, scheduler tắt và fake VNPay settings cho smoke test; Hibernate validate đạt, GET /actuator/health, /api/v1/categories, /api/v1/products đều **200**. Tiến trình thử nghiệm đã dừng sau kiểm tra; không tạo business data.
- ERD DDL đã import vào database trống, tạo đúng **48 bảng**. Dictionary xuất từ live catalog gồm 907 thuộc tính vật lý, 191 FK; JSON metadata/history được mô tả riêng. Full runtime schema export không có row data.
- Bằng chứng riêng: output/database-consolidation-2026-10-09/backend-tests-verified.log, final-backup-verification.json, final-before-proof.json, final-restore-proof.json, final-live-proof.json, live-v22-migration.log, live-v22-verification.json, live-runtime-verification.json và erd-ddl-verification.log.

## Gắn lại 12 nhóm nền dữ liệu với 48 bảng

| Nhóm use case V21 | Nơi lưu V22 |
|---|---|
| G01 — tài khoản/OTP | users, auth_challenges, refresh_sessions, roles, user_roles, addresses |
| G02 — eKYC/Seller/quick auth | ekyc_profiles, ekyc_private_assets, identity_document_registry, verification_attempts, seller_profiles, auth_challenges |
| G03 — phiên bản tin/media/AI | products, product_revisions, product_media, categories, product_categories, business_policies |
| G04 — phí tin | business_policies, listing_fee_charges, payments, payment_confirmations |
| G05 — checkout/lượng/tồn | cart_items, checkout_groups, orders, order_items, inventory_reservations, audit_logs, conversations |
| G06 — voucher/points | vouchers, voucher_products, voucher_grants, reward_ledger, users, checkout_groups, order_items, business_policies |
| G07 — thanh toán/hoàn/giải ngân | payments, payment_attempts, payment_events, payment_confirmations, order_fund_components, settlement_operations, cases, case_actions |
| G08 — giao/nhận/trả | shipments, shipment_events |
| G09 — case nhiều vòng | cases, case_actions, case_evidence, orders, shipments, order_fund_components, settlement_operations |
| G10 — quản trị/chặn/phạt/review | users, reviews, seller_buyer_blocks, penalty_ledger, business_policies, audit_logs |
| G11 — thông báo | notifications, outbox_events |
| G12 — thống kê/export | interaction_events, audit_logs, 7 reporting views trên dữ liệu nghiệp vụ |

[Ma trận từng UC01–UC83](DATABASE_EXPANSION_V21_20261009.md#ma-trận-nối-từng-uc-với-migration-đã-tạo) giữ các nhóm G tương ứng; dùng bảng trên để đổi tên nguồn sang cấu trúc gộp V22. Không có use case bị bỏ chỉ để đạt con số 48.

Backup cuối: `before-v22-final-private.dump`, 619,178 bytes, SHA-256 `311D0C9AD5EC7ED06AB55D9B105E884D8F3F9C4C2C0D630C1024E8C6927B3513`. Migration V22 SHA-256 `E00A97D24863D21DFC03E19B4D0DC64BE60374694BD02BCC60D411286B5289C8`. Các fingerprint chỉ chứa hash/count, backup chứa dữ liệu riêng tư và không được đưa vào Git.

Archive tương thích cuối `before-v22-final-compatible-private.dump` cũng được giữ riêng trong output; đã restore thành công trong container hiện tại. Chỉ extension vector không có cột sử dụng bị loại khỏi archive này. Clone và container dữ liệu giả của TASK-0070 đã được dọn, runtime smoke process đã dừng; validate22 cuối đạt lúc 15:55:18.

## Forward-fix bộ đếm ID — V23

Sau chuyển đổi, một số sequence được dùng chung cho PK vật lý và default của view nguồn cơ sở. V22 căn lại default theo max của nguồn cơ sở, có thể thấp hơn ID đã cấp cho dòng sibling khi import; ví dụ legacy verification có thể chiếm profile_id mà nguồn eKYC mới sắp cấp. Không sửa V22 đã áp dụng.

V23 lấy max PK trên **mọi record_type** của từng bảng, giữ cả last_value đã dùng và tăng sequence tới giá trị lớn nhất; không đổi ID hay dữ liệu nghiệp vụ. Nó giữ is_called cho sequence trống để không bỏ ID đầu vô cớ. Bộ kiểm thử populated upgrade nay tạo thêm eKYC profile, legacy verification, voucher revision và legacy payment sau conversion; bắt được va chạm loại này.

V23 đã apply/validate local lúc **16:01:59 ngày09-10-2026**, 23 success/0 failed. Fresh V22 backup `before-v23-private.dump` được giữ riêng. Restore V21→V23 trong chính container local khớp 104/104 source fingerprints; tạo mới hai loại identity trên clone + SET CONSTRAINTS ALL IMMEDIATE thành công rồi rollback. Live sau V23 vẫn khớp dữ liệu trước V22 của cả 104 nguồn.

Kết quả cuối trên V23: 252 tests/0 failures/0 errors/1 Windows symlink skip; expansion Pg 33/33 pass. Live readonly health/categories/products đều 200; Hibernate validate và ERD import 48 bảng đạt. Clone, container fake và smoke process cuối đã dọn. Bằng chứng: backend-tests-v23.log, restored-v23-migration.log, restored-v23-proof.json, restored-v23-new-writes.log, live-v23-migration.log, live-v23-proof.json, live-v23-verification.json và live-runtime-v23-verification.json trong output/database-consolidation-2026-10-09/. Future migration dùng V24+, giữ V1–V23 immutable.
