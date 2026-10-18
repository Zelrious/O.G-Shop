# Rà soát và hướng mở rộng database theo 83 use case

> Bản rà soát này ghi tình trạng V14 trước triển khai. TASK-0069 đã mở rộng và áp dụng V15–V21; xem [schema mới và ma trận từng UC](DATABASE_EXPANSION_V21_20261009.md). Các mức thiếu/mâu thuẫn bên dưới giữ làm căn cứ lịch sử, không phải trạng thái runtime V21 hoặc nghiệm thu API.


Ngày rà soát: 09-10-2026, Asia/Saigon. Task: TASK-0068. Phạm vi: phân tích cấu trúc và hành vi hiện có, đề xuất thiết kế; chưa triển khai DDL, entity, service hoặc dữ liệu nghiệp vụ.

## 1. Kết luận

**Database hiện tại có nền tảng marketplace khá đầy đủ nhưng chưa đủ phục vụ trọn vẹn 83 UC trong báo cáo hiện hành.** Các bảng người dùng, sản phẩm, đơn, chat, offer, voucher, thanh toán và kiểm duyệt đã có. Tuy nhiên, mô hình hiện tại vẫn mang nhiều giả định của MVP: một tin là một đơn vị, giữ cả tin cho một đơn, phí theo đơn mua, một payment tổng hợp và một shipment cho mỗi đơn, một kết luận khiếu nại.

Các giả định này mâu thuẫn trực tiếp với yêu cầu mới về số lượng, nhóm thanh toán, phí đăng tin cộng dồn, hoàn hàng theo nhiều vòng và xử lý tiền từng thành phần. Thêm một vài cột trạng thái sẽ không giải quyết được các quan hệ thiếu.

Ưu tiên thiết kế:

1. Tách **phiên bản tin – kiểm duyệt – phí đăng tin – xác nhận tiền – ACTIVE**.
2. Bổ sung **tồn theo lượng, nhiều giữ hàng, nhóm checkout và snapshot từng đơn/món**.
3. Tách **tiền thu của nhóm, phân bổ từng đơn và các thao tác hoàn/giải ngân**.
4. Tách **giao đi/giao trả, mốc nhận hợp lệ, hạn trả và hoàn tất sớm**.
5. Mô hình hóa **một hồ sơ trả với nhiều vòng**, đồng thời tách lỗi hệ thống.
6. Hoàn thiện **email OTP/lớp hai, eKYC do KTV quyết cuối, voucher cá nhân, điểm, phạt, thông báo và dữ liệu thống kê**.

Không cần một bảng riêng cho mỗi UC. Các UC thông báo dùng chung một cơ chế; các UC trả, xét miễn và thống kê dùng chung hồ sơ và lịch sử, có loại/giai đoạn rõ ràng.

## 2. Căn cứ và tình trạng thực tế

### 2.1. Nguồn yêu cầu

- [Báo cáo đồ án — Bản sao của Thẻ 1](https://docs.google.com/document/d/1mbj6vIkLgNM0mzdy-xRukPfz2r0vN_kk5jvKx_QToKQ/edit?tab=t.goqi7hu9cf5): UC01–UC83 sau tinh giản, 10 trường/đặc tả; tên UC trong ma trận lấy từ nguồn này.
- Checkpoint chính sách: [TASK-0065](../progress/archive/TASK-0065-report-policy-checkpoint-20261009.md); hoàn thành tinh giản: [TASK-0067](../progress/archive/TASK-0067-simplify-83-use-case-specifications.md).
- Bản native cuối được đọc ở TASK-0067 và lưu trong output/usecase-simplification-2026-10-09/final-trusted-files. Không thay bằng ma trận 78 UC cũ hoặc các số UC-01–UC-19 trong tài liệu module trước đây.

### 2.2. Database đã quan sát

| Hạng mục | Bằng chứng quan sát ngày 09-10-2026 |
|---|---|
| Database local | og_shop, container og-shop-postgres-1, PostgreSQL 17.11 |
| Flyway history | V1–V14 đều success = true |
| Số bảng public | 44 bảng, gồm flyway_schema_history; còn 43 bảng nghiệp vụ/hạ tầng |
| Kiểm tra runtime | Transaction READ ONLY đọc metadata; pg_dump chỉ xuất schema, không xuất bản ghi nghiệp vụ |
| Snapshot schema | output/database-review-2026-10-09/runtime-schema.sql, 131.106 byte |
| SHA-256 snapshot | 055F48FF96FB751F5D4B50180416662BF412AE6E2ADA43A5CEED317C97AEE3EC |
| Metadata xác nhận | output/database-review-2026-10-09/live-verification.json; thời điểm 2026-10-09 11:40:48 Asia/Saigon |
| Giới hạn chứng minh | Đã đối chiếu các cấu trúc/ràng buộc trọng yếu với migrations; chưa chạy Flyway validate/checksum, chưa kiểm toán dữ liệu hoặc chứng nhận không có mọi loại schema drift |

[Thư mục migration](../../backend/src/main/resources/db/migration/README.md) là nguồn DDL trong Git. File database/docs/second_hand_marketplace_schema.sql là baseline cũ, không đại diện đầy đủ cho schema sau V14. Một số ghi chú runtime trong tài liệu cũ chỉ tới V8/V13 đã được thay bằng quan sát trên cho phạm vi rà soát này.

### 2.3. Phần đã có và nên tái sử dụng

| Nhóm | Bảng hiện hành |
|---|---|
| Identity | users, roles, user_roles, addresses, external_identities, refresh_sessions, password_reset_challenges, seller_verifications, seller_verification_metrics |
| Catalog | categories, products, product_categories, product_media, product_moderation_decisions, product_moderation_legacy_history |
| Communication | conversations, conversation_user_state, messages, offers, chat_media_daily_quotas |
| Commerce/ưu đãi | carts, cart_items, orders, order_items, vouchers, order_vouchers, order_unboxing_evidences, system_fee_policies |
| Payment | payments, vnpay_payment_attempts, payment_attempts, payment_ipn_raw_receipts, payment_ipn_events, payment_review_audits, payment_reconciliation_cases, payment_alert_requests |
| Fulfillment/Trust/Platform | shipments, reviews, complaints, reports, notifications, audit_logs, outbox_events |

Nền tốt đã có: email chuẩn hóa/unique; Google provider + subject unique; refresh digest và token family; một địa chỉ mặc định; danh mục nhiều-nhiều; optimistic version; snapshot giá/địa chỉ đơn; message idempotency và FK thành viên; offer chain/một offer chờ; accepted offer chỉ dùng một order item; lịch sử kiểm duyệt chỉ thêm; outbox; payment attempts, receipt, xác minh lệch tiền và đối soát.

### 2.4. Các điểm chặn có bằng chứng trực tiếp

| Điểm chặn | Hiện trạng và hệ quả | Nguồn |
|---|---|---|
| Số lượng và giữ hàng | cart_items.quantity và order_items.quantity bị CHECK quantity = 1; products chỉ có một reserved_order_id/reserved_until, chưa có số lượng tổng/giữ/bán. Không hỗ trợ bán một phần hoặc nhiều đơn giữ lượng cùng tin. | [V1](../../backend/src/main/resources/db/migration/V1__initial_schema.sql), snapshot runtime |
| Nhóm checkout | orders.checkout_group_id chỉ là UUID và index; không có bảng đầu nhóm, snapshot lựa chọn chung, command idempotency hoặc phân bổ thanh toán. Có nhiều order_items về quan hệ, nhưng lượng bị khóa và code chỉ tạo một món. | V1; [CheckoutService](../../backend/src/main/java/com/oldbutgold/shop/modules/commerce/application/CheckoutService.java) |
| Phí tin và trạng thái | system_fee_policies mô tả phí Buyer/Seller theo giao dịch; fee_policy_id nằm trên offer/order item. Chưa có biểu phí đăng tin theo ngưỡng P, các lần xác định D hoặc ledger C. products.status VARCHAR(20) còn không đủ chứa APPROVED_AWAITING_FEE dài 21 ký tự. | [V3](../../backend/src/main/resources/db/migration/V3__pricing_negotiation_and_chat_baseline.sql), V1 |
| Phiên bản tin | Có content_revision và proof kiểm duyệt nhưng không có snapshot bất biến của toàn bộ nội dung/media/checklist từng bản. Quyết định trỏ số revision chưa đủ để tái dựng hàng đã bán. | [V9](../../backend/src/main/resources/db/migration/V9__product_moderation_decisions.sql), [V10](../../backend/src/main/resources/db/migration/V10__product_moderation_enforcement.sql), [V12](../../backend/src/main/resources/db/migration/V12__explicit_moderation_proof_and_legacy_history.sql) |
| Giới hạn media | Trigger tối đa 1 video/tin trong khi UC15 cho 1–2. CHECK file_size_bytes chung tới 50 MB, chưa ép ảnh ≤5 MB. Metadata có thể NULL. Trigger đếm chỉ INSERT, không khóa hàng cha, chưa bảo vệ đầy đủ UPDATE/concurrency. | [V6](../../backend/src/main/resources/db/migration/V6__voucher_and_media_lifecycle_baseline.sql), runtime functions |
| Thanh toán/hoàn | payments.order_id UNIQUE: một payment/đơn; CHECK refund_amount = amount. Chưa hoàn riêng G mà giữ/không hoàn ship chiều mua, hoặc hoàn/giải ngân từng phần của các đơn trong nhóm. V13/V14 bổ sung receipt/attempt nhưng không thay mô hình này. | V1, [V13](../../backend/src/main/resources/db/migration/V13__vnpay_payment_attempts.sql), [V14](../../backend/src/main/resources/db/migration/V14__add_vnpay_ipn_receipts.sql) |
| Giao đi/trả | shipments.order_id UNIQUE, trạng thái bị ghi trên một bản; chưa có leg, event, lần lấy, OTP/hai bên và mốc trả mở hạn Seller. | V1 |
| Khiếu nại | complaints bắt buộc order_id, một resolution/resolved_at; chưa có các vòng UC62/UC63, lỗi hệ thống không có đơn, bàn giao KTV→Admin hoặc quyết định miễn hoàn riêng. | V1, V6 |
| eKYC và SELLER | V2 đã xóa CCCD/ảnh/vector cũ, chỉ giữ metrics; không có registry CCCD chống trùng xuyên phiên bản hoặc reference riêng tư có hạn. CHECK cho phép VERIFIED không KTV khi AI_EKYC/MVP_BYPASS. | [V2](../../backend/src/main/resources/db/migration/V2__identity_and_ekyc_security_baseline.sql), [V4](../../backend/src/main/resources/db/migration/V4__mvp_seller_activation.sql) |
| Ưu đãi/điểm/phạt | Voucher có campaign/giá trị và order snapshot, chưa có quyền nhận cá nhân/cấp-thu hồi, phạm vi món, điểm thưởng/điểm phạt. UNIQUE(order_id,voucher_id) vẫn cho nhiều voucher khác nhau/đơn. | V6, runtime table inventory |
| Lịch sử, media và thông báo | audit_logs chưa có guard chỉ thêm; cleanup complaint cố định resolved_at + 3 ngày không kiểm tra nghĩa vụ đang mở/mốc kết thúc muộn nhất. notifications thiếu event dedup, sender/đợt gửi và reference UUID/version. | V1, V6, runtime triggers |

Các sai lệch ở service phải xử lý cùng thiết kế schema:

- AuthService.register tạo user và phiên ngay, chưa có bước email OTP theo UC01. Login chưa có lớp hai trong service này.
- EkycVerificationService.verify gọi SellerVerificationWriter.recordVerified khi AI match; writer cấp SELLER. SellerActivationService còn nhánh MVP_BYPASS. UC07/UC68 hiện hành yêu cầu AI sơ kiểm và KTV quyết cuối, eKYC Buyer không tự cấp bán.
- SellerProductService.approveProduct gọi product.approve và trả ACTIVE; chưa có bước phí tin đã xác nhận.
- CheckoutService.buyNow dùng một productId, quantity = 1, phí ship cố định 30.000 đồng, tạo UUID nhóm mới, tính seller fee theo đơn; chưa phải checkout nhiều món/nhóm có lựa chọn giao chung.
- OrderManagementService.cancelBuyerOrder chỉ chấp nhận PAYMENT_PENDING; UC82 cho các bên hủy trước nhận và xử lý cả đã thu/đang gửi. confirmSellerOrder chưa kiểm tra hạn 2 ngày và đánh SOLD toàn tin.
- PaymentService có nhánh từ chối lệch tiền, duplicate charge/late charge và review; đây là nền cần giữ. Khi review từ chối, yêu cầu hoàn đang lấy toàn payment.amount; chưa phải settlement theo thành phần.

Các test CheckoutServiceTest và PaymentServiceTest/VnPayPaymentPostgresTest hiện có minh họa luồng một món, giữ 1 giờ, mismatch, replay và late payment. **Rà soát này đọc test, không chạy lại suite; không suy ra các luồng 83 UC đã được nghiệm thu.**

## 3. Nhóm mở rộng dùng chung

Tên bảng/cột dưới đây là **đề xuất thiết kế**, chưa tồn tại nếu không được liệt kê ở mục 2.3. Có thể điều chỉnh tên khi triển khai; quan hệ, quyền sở hữu dữ liệu và invariant là phần cần giữ.

| Mã | Module sở hữu | Mở rộng tối thiểu theo vòng đời dữ liệu |
|---|---|---|
| G01 | Identity | users.email_verified_at; user_security_settings; auth_challenges dùng cho đăng ký, reset, xác minh SĐT/email và login lớp hai; phiên chờ xác minh chưa là refresh session đầy đủ |
| G02 | Identity | eKYC hồ sơ/phiên bản, asset reference riêng tư, CCCD registry, verification attempts, quyết định KTV, seller_profiles riêng và kết quả xác thực nhanh có expiry |
| G03 | Catalog | product_revisions, revision_media, checklist_policies và media_analysis_runs; phương thức giao/cờ trả theo bản; product moderation proof trỏ đúng revision |
| G04 | Catalog/Payment | listing_fee_policies, listing_fee_assessments, listing_fee_charges và các khoản confirmed fee; ledger phí thuộc tin, dùng payment intent purpose LISTING_FEE riêng |
| G05 | Commerce/Catalog | checkout_groups, inventory_reservations, snapshot mở rộng orders/order_items và order_events; lượng giỏ >0; lựa chọn/giữ tồn/nhóm có idempotency |
| G06 | Commerce | voucher_grants + grant history, phạm vi policy version, checkout_redemptions, discount_allocations, reward_accounts và reward_ledger |
| G07 | Payment | payment_intents theo purpose, payment_allocations, order_fund_components, settlement_operations và money_holds; nối receipt/attempt/reconciliation hiện có |
| G08 | Fulfillment | shipments có leg/phương thức, shipment_events, pickup_attempts và handover_confirmations; OTP digest/hạn/thử gắn bàn giao cụ thể; các mốc/hạn hợp lệ |
| G09 | Trust & Safety | cases có loại RETURN/SYSTEM_ISSUE, case_rounds, case_decisions, case_evidence và case_events gồm bổ sung/chuyển người xử lý; chặn tiền có phạm vi |
| G10 | Identity/Trust & Safety | seller_buyer_blocks, penalty_policies + penalty_ledger, account_restrictions; reviews gắn đúng hai bên và lịch sử sửa; audit quyết định/trạng thái chỉ thêm |
| G11 | Platform | notifications mở rộng event/version; notification_dispatches, trạng thái giao/nhắc; outbox consumer dedup. Thông báo Khách thuộc phiên hiện tại, không dùng user_id giả |
| G12 | Platform | interaction_events có chỉ tiêu riêng; truy vấn/view tổng hợp và metadata bộ lọc/thời điểm export; dùng ledger phí, case và user thực tế |

P0: chặn luồng cốt lõi hoặc sai tiền/quyền. P1: chức năng phải có để hoàn thành 83 UC nhưng có thể triển khai sau nền cốt lõi. P2: tối ưu sau khi luồng và dữ liệu đã đúng.

## 4. Ma trận phân tích từng UC

Mức đánh giá là **mức đáp ứng của cấu trúc dữ liệu**, không phải tỷ lệ hoàn thành phần mềm:

- **Đủ nền**: quan hệ chính đã có; còn invariant/truy vấn/audit/service phải hoàn thiện.
- **Một phần**: có dữ liệu chính nhưng thiếu quan hệ, trạng thái hoặc lịch sử cần thiết.
- **Thiếu**: chưa có mô hình dữ liệu cốt lõi cho chức năng.
- **Mâu thuẫn**: constraint/mô hình hoặc luồng hiện tại trực tiếp trái quy tắc đã chốt.

Đếm theo ma trận bên dưới: **53 Một phần, 18 Mâu thuẫn, 10 Thiếu, 2 Đủ nền**. Các UC thông báo cùng phụ thuộc G11 và dữ liệu nguồn nên số UC không tương đương số bảng phải thêm hoặc tỷ lệ triển khai. “Đủ nền” vẫn cần service/authorization/kiểm thử để nghiệm thu.

### 4.1. Identity — UC01–UC11

| UC / tên hiện hành | Mức và dữ liệu đang có | Khoảng thiếu → hướng mở rộng |
|---|---|---|
| UC01 — Đăng ký tài khoản bằng email | Một phần: users, email unique/chuẩn hóa, password hash, user_roles. | Chưa có verified email và mã trước hoàn tất; thiếu hồ sơ Người bán PENDING_EKYC. G01/G02, P0: challenge theo email trước tạo user; digest/hạn/thử/gửi lại; tài khoản + seller profile nguyên tử, public chỉ BUYER. |
| UC02 — Đăng ký tài khoản bằng Google | Một phần: external_identities unique provider/subject và user/provider. | Thiếu onboarding/password riêng, seller profile và phiên chờ lớp hai. G01/G02, P0: tạo/link nguyên tử; email trùng không tự gộp; Google đã verified email vẫn phải qua lớp hai nếu bật. |
| UC03 — Đăng nhập | Một phần: refresh_sessions có digest/family/consumed/revoked/expiry. | Thiếu security setting, challenge email và login pending. G01/G10, P0: cả password/Google dùng cùng gate lớp hai; kiểm tra restriction thực tế, cấp full session sau đủ điều kiện. |
| UC04 — Quên mật khẩu | Một phần: password_reset_challenges và refresh session. | Chưa đảm bảo một mã hoạt động/user/purpose, resend/rate limit và email đã verified. G01, P0: dùng chung challenge; đổi hash + consume mã + revoke phiên nguyên tử; không tắt lớp hai. |
| UC05 — Quản lý hồ sơ | Một phần: users, bank fields, addresses một mặc định và địa chỉ order snapshot. | Thiếu lịch sử nhạy cảm, xác thực nhanh, xác minh SĐT mới, khóa sửa email/CCCD sau duyệt. G01/G02/G10, P0: audit che dữ liệu; đổi giấy tờ tạo hồ sơ mới; fallback địa chỉ mặc định theo thứ tự lưu trong transaction. |
| UC06 — Đăng ký xác thực tài khoản eKYC | Mâu thuẫn: seller_verifications chỉ PENDING/VERIFIED/REJECTED; index coi cả VERIFIED là hồ sơ hoạt động. | Không hỗ trợ DRAFT/IN_PROGRESS hoặc giữ bản đã duyệt khi tái xác minh. G02, P0: một hồ sơ đang xử lý/user; các phiên bản cũ bất biến; trạng thái eKYC và seller profile tách riêng. |
| UC07 — Xác minh danh tính | Mâu thuẫn: metrics không có đủ hồ sơ/asset; AI match đang có thể VERIFIED + SELLER. | G02, P0: đủ hai mặt CCCD/frame/OCR/sửa/AI; registry CCCD xuyên lịch sử; ≤15 nonmatch, cách ≥3s, lỗi dịch vụ riêng; AI chỉ sơ kiểm, manual fallback có quyết định Admin; KTV quyết cuối. |
| UC08 — Xác thực lại khuôn mặt | Thiếu: chưa có reference được duyệt và kết quả xác thực nhanh có hạn. | G02, P0: account/reference/result liên kết, expires_at ≤3 tháng và không vượt hạn nguồn; đếm nonmatch/cooldown; mẫu thiếu/hết hạn hoặc tái eKYC không cấp pass/bypass. |
| UC09 — Thiết lập xác thực hai lớp | Thiếu: chưa có settings và challenge mục đích bật/tắt. | G01/G02, P0: kết quả UC08 còn hạn + mã email hợp lệ; ghi setting/phiên bản/thời điểm; không thêm SMS/TOTP ngoài báo cáo. |
| UC10 — Quản lý danh sách chặn | Thiếu: conversations.status BLOCKED không thay quan hệ chặn giữa tài khoản. | G10, P1: unique(seller,buyer), thời điểm/lý do/bỏ chặn; không xóa chat/đơn, vẫn cho thao tác cần thiết cho nghĩa vụ cũ. |
| UC11 — Quản lý điểm phạt | Thiếu: reports/reviews không có penalty ledger/policy. | G10, P1: mỗi quyết định vi phạm ghi một entry, sửa bằng entry điều chỉnh; policy version và ngưỡng; tính uy tín = trung bình đánh giá hợp lệ − điểm phạt, không phạt vì hủy. |

### 4.2. Tin đăng, chat, ưu đãi và checkout — UC12–UC24

| UC / tên hiện hành | Mức và dữ liệu đang có | Khoảng thiếu → hướng mở rộng |
|---|---|---|
| UC12 — Tìm kiếm và lọc sản phẩm | Một phần: products/category membership, index ACTIVE và GIN title. | G03/G04/G05, P0: chỉ bản ACTIVE đã duyệt/đủ phí/còn khả dụng; loại tài khoản bị chặn giao dịch. Chỉ giá niêm yết; nhiều danh mục dùng EXISTS/DISTINCT tránh lặp. Tối ưu truy vấn P2 sau dữ liệu đúng. |
| UC13 — Xem chi tiết sản phẩm | Một phần: nội dung, media, giá và seller FK đã có. | Thiếu lượng, cờ trả, phương thức, bản duyệt/đủ phí và public seller projection. G02/G03/G04/G05, P0: không lộ bank/KYC; mua một phần không làm hết tin; chi tiết đơn cũ dùng snapshot. |
| UC14 — Xem sản phẩm được gợi ý | Thiếu: có ứng viên products nhưng chưa có lịch sử tương tác cho cá nhân hóa. | G12 + G03/G04/G05, P1: interaction_events theo user/session và loại; gợi ý đơn giản theo tương tác là đủ, không cần vector DB; lọc quyền/còn tồn, mỗi tin một lần. |
| UC15 — Đăng bán sản phẩm | Mâu thuẫn: products/media thiếu lượng/cờ trả/phương thức/checklist; giới hạn video hiện là 1. | G02/G03/G04/G05, P0: bản bất biến, 1–5 ảnh/1–2 video, ảnh ≤5MB, video ≤50MB/60s; nháp được thiếu tối thiểu. Đủ quyền + duyệt + confirmed fee mới ACTIVE; gửi duyệt không thu phí. |
| UC16 — Kiểm tra ảnh/video bằng AI | Thiếu: chưa có analysis run, phiên bản input/checklist/model/kết quả từng file. | G03, P1: lưu processing status khác kết luận, frame policy và input hash/version; late result không ghi đè bản mới; AI lỗi/không đạt không chặn PENDING hợp lệ hoặc tự quyết thay KTV. |
| UC17 — Quản lý tin đăng | Mâu thuẫn: trạng thái hiện tại thiếu chờ phí, giữ lượng và revision snapshot. | G03/G04/G05, P0: sửa nội dung đưa nháp/duyệt lại, giữ bản đơn cũ; phí C xuyên tin, D theo mỗi lượt; hàng trả cần xác nhận tình trạng/quyền rồi KTV duyệt lại. |
| UC18 — Trao đổi qua tin nhắn | Một phần: conversation snapshot, participant/read cursor, message dedup/media quota. | G05/G09/G10, P1: conversation_order_links cho thẻ đơn/giá/lượng/offer; giữ chat sau SOLD; chặn mới không cắt trao đổi nghĩa vụ cũ; media cần hold theo hồ sơ chính thức, chat không tự mở case/giữ tiền. |
| UC19 — Thương lượng giá | Một phần: offer chain, một PENDING/conversation, version và accepted_offer unique trên item. | G03/G05, P1: chốt giá là đơn giá hoặc snapshot rõ lượng áp dụng; kiểm tra buyer/seller/product/revision/khả dụng lúc dùng. Không tính phí tin theo offer; offer đã dùng không phục hồi sau hủy. |
| UC20 — Xem voucher và điểm thưởng | Một phần: vouchers/order_vouchers chỉ có policy và lịch sử dùng theo order. | G06, P1: quyền sở hữu grant, trạng thái cấp/thu hồi/đã dùng và reward ledger; chỉ chủ xem; ưu đãi mua không dùng cho listing fee. |
| UC21 — Nhận voucher | Thiếu: chưa có grant cá nhân/source chương trình. | G06, P1: grant unique theo command/giới hạn người dùng; khóa hạn mức cùng transaction; lưu nguồn, kỳ nhận và trạng thái; kiểm tra lại khi dùng. |
| UC22 — Tích lũy và sử dụng điểm thưởng | Thiếu: chưa có reward account/ledger/policy version. | G06, P1: balance không âm, source unique; chỉ cộng khi giao dịch hoàn tất đủ điều kiện; trừ sau voucher và phân bổ từng món/đơn; hủy/trả không hoàn điểm đã dùng. |
| UC23 — Quản lý giỏ hàng | Mâu thuẫn: cart_items unique(cart,product) nhưng quantity bị CHECK = 1. | G05, P0: quantity >0; giỏ không giữ tồn/giá; kiểm tra lượng hiện tại và cờ trả trước checkout; một món lỗi không tự bỏ khỏi lựa chọn. |
| UC24 — Đặt hàng | Mâu thuẫn: UUID nhóm không có head/FK; một reservation/tin; code một món/lượng 1. | G03/G05/G06/G07/G08, P0: nhóm + nhiều đơn một seller/cùng cờ trả/chung kiện; chung địa chỉ/carrier/method/payment method; snapshot per-item, ưu đãi và 1h hold. Fail-all trước tạo/giữ nếu selection không hợp lệ; command lặp trả nhóm cũ. |

### 4.3. Thanh toán, nhận và trả — UC25–UC33

| UC / tên hiện hành | Mức và dữ liệu đang có | Khoảng thiếu → hướng mở rộng |
|---|---|---|
| UC25 — Thanh toán đơn hàng | Một phần: V13/V14 có attempts, receipt/event, mismatch, duplicate, review/reconciliation. | G05/G07, P0: intent cấp nhóm + allocations từng order; fixed expected amount và deadline gốc; correct late charge đối soát/hoàn không hồi sinh; mismatch không success/hoàn dư; phân biệt listing intent. |
| UC26 — Theo dõi đơn hàng và vận chuyển | Một phần: orders và shipment current state/time. | G08/G09/G07, P0: event nguồn/dedup, outbound/return leg, valid delivery earliest, OTP/hai bên trực tiếp; mâu thuẫn giữ tiền/xác minh; cả hai im lặng 14 ngày hủy, không tự giao xong. |
| UC27 — Xác nhận nhận hàng | Một phần: orders.completed_at và unboxing chưa tách các mốc nghiệp vụ. | G05/G08/G09/G07, P0: received_at khác early_completed_at + warning/acceptance version; cờ trả snapshot và deadline 3 ngày từ giao hợp lệ; hồ sơ ngăn không được hoàn tất/giải ngân. |
| UC28 — Xác thực đồng kiểm | Một phần: order_unboxing_evidences có SUBMITTED/SKIPPED_BY_BUYER. | G05/G08, P0: trạng thái đã quay khác đã upload; lưu skip warning accepted_at/version, FK đúng buyer/order; video cả kiện dùng chung, thiếu video không tự bác quyền trả. |
| UC29 — Xem lại các đơn hàng đã đặt | Một phần: buyer order index, item giá/tên và địa chỉ snapshot. | G03/G05/G07/G08/G09, P0: lượng/cờ trả/media bản mua; chi tiết tất cả items và lịch sử riêng đơn/tiền/giao/case. Quyền Buyer, không lấy tin mới thay snapshot. |
| UC30 — Quản lý đơn bán | Mâu thuẫn: seller index và seller_confirmed_at có; code xác nhận làm SOLD toàn tin. | G05/G07/G08, P0: cập nhật đúng lượng, confirmed command unique, seller_accept_due_at = tạo hợp lệ hoặc paid_confirmed +2 ngày; quá hạn hủy/hoàn theo trạng thái, không thu phí tin theo order. |
| UC31 — Xem lại các đơn hàng đã bán | Một phần: seller orders/history cơ bản. | G03/G05/G07/G08/G09, P0: giữ các đơn hủy/trả, toàn bộ items, bản hàng và kết quả settlement; Seller phản đối theo hạn nhận trả, lịch sử không theo listing hiện tại. |
| UC32 — Chuẩn bị giao hàng và xác nhận bàn giao | Mâu thuẫn: một shipment/order, thiếu method snapshot, OTP/hai bên và return leg. | G05/G08, P0: một kiện/leg/đơn, nhiều seller nhóm có shipment riêng; phí trực tiếp =0; riêng handover challenge và hai kết quả; giao lại ≤3 ngày từ thất bại đầu; đổi phương thức có xác nhận/đối chiếu phí. |
| UC33 — Theo dõi và xác nhận hàng trả lại | Mâu thuẫn: shipment RETURNED không lưu đủ tiến trình trả độc lập, complaint chỉ một quyết định. | G08/G09/G07, P0: approval_at, pickup_expected≤24h, deadline = approval+3 ngày, ≤3 failures/≤1 lần mỗi ngày; reschedule không kéo dài. Valid returned delivery mở Seller 2 ngày; nhận khác đồng ý hoàn; fault carrier/unknown/disputed chuyển KTV và giữ tiền. |

### 4.4. Thông báo — UC34–UC60

Nền dùng chung là notifications/outbox_events. G11 bổ sung unique(event_id,recipient), revision/reference đúng đối tượng, trạng thái giao/đọc riêng, reminder có điều kiện và quyền kiểm tra lại khi mở. Các dòng dưới nêu dữ liệu nguồn phải có để thông báo đúng; không tạo 27 bảng thông báo.

| UC / tên hiện hành | Mức và dữ liệu đang có | Khoảng thiếu → hướng mở rộng |
|---|---|---|
| UC34 — Nhận thông báo yêu cầu đăng nhập | Một phần: notifications bắt buộc user_id nên không lưu cho Khách. | G11/G01, P1: lời nhắc theo guest session/lượt truy cập hiện tại; đích allowlist, dừng khi login. Không cần tạo user Khách hoặc lưu DB nếu chỉ nhắc tại màn hình. |
| UC35 — Nhận thông báo yêu cầu xác thực eKYC | Một phần: notification tổng quát và requires_buyer_ekyc. | G02/G11, P1: reminder trỏ yêu cầu/hồ sơ cụ thể; dừng khi đủ eKYC hoặc mục đích hết; xem công khai/tin không yêu cầu không ép eKYC. |
| UC36 — Nhận thông báo mặt hàng trong giỏ hàng đã hết | Một phần: cart/product và notification. | G05/G11, P1: event lượng khả dụng + lượng giỏ; phân biệt hết tồn/giữ/chờ phí/ẩn; đánh không mua được, không xóa món; dedup mỗi event/người nhận. |
| UC37 — Nhận thông báo khi mặt hàng đã hết | Một phần: có product/cart để kiểm tra. | G05/G11, P1: thông báo tại thêm thất bại, lưu command/event nếu cần replay; không chèn món hết vào giỏ; mua một đơn vị không báo hết tin còn hàng. |
| UC38 — Nhận thông báo về voucher | Một phần: voucher window và notification. | G06/G11, P1: nguồn grant cá nhân/cấp/sắp hết/hết hạn; reminder điều kiện hiện tại và dedup từng mốc; thông báo không cấp/gia hạn voucher. |
| UC39 — Nhận thông báo về tình trạng đơn hàng | Một phần: orders/shipments và notification. | G05/G07/G08/G09/G11, P1: event riêng order/payment/leg/case; không dùng một status cho đã hủy/đã hoàn; không hứa hạn trả với order không cho trả. |
| UC40 — Nhận thông báo hoàn tiền | Một phần: payment/complaint tổng hợp và notification. | G07/G09/G11, P1: trỏ operation/decision có revision; phân biệt được duyệt/chờ/hoàn confirmed hoặc KTV miễn, tiền hàng khác ship; không báo xong khi mới quyết. |
| UC41 — Nhận thông báo yêu cầu thanh toán | Một phần: order.payment_due_at và notification. | G05/G07/G11, P1: reminder theo nhóm/intent và 1h gốc, retry không gia hạn; dừng khi hủy/paid/expired; uncertain result nhắc đối soát, không đòi thanh toán lại ngay. |
| UC42 — Nhận thông báo đơn hàng đã được thanh toán | Một phần: verified payment IPN và notification. | G07/G11, P1: event sau money allocation commit, recipients Buyer/Seller theo đơn; thông báo paid/held mô phỏng không đồng nghĩa release. |
| UC43 — Nhận thông báo khi nội dung bài đăng bị lỗi | Một phần: product/media và notification. | G03/G04/G11, P1: error code/field/media/revision/command; lỗi nội dung khác lỗi thanh toán/đối soát; không làm mất duyệt đã có hoặc ép trả phí lại. |
| UC44 — Nhận thông báo tình trạng bài đăng | Một phần: product status và moderation decision. | G03/G04/G05/G11, P1: approved revision, fee assessment/D/C/confirmed receipt; rõ chờ phí/ACTIVE/giữ hết/SOLD khi hết bán; đích UC17 đúng bản/khoản. |
| UC45 — Nhận thông báo khi có người mua hàng từ bài đăng | Một phần: order/product links và notification. | G05/G11, P1: event tạo order đã commit; một recipient Seller theo order; seller_accept_due_at gốc 2 ngày, xem không reset, có đơn chưa là paid. |
| UC46 — Nhận thông báo khi được đánh giá | Một phần: reviews và notification. | G10/G11, P1: review revision/event; người được đánh giá, không tính uy tín lại do gửi lặp; không lộ dữ liệu riêng. |
| UC47 — Nhận thông báo khi có tin nhắn | Một phần: message/participant/read cursor, notification. | G11/G10, P1: message event unique/người nhận và link conversation; preview theo quyền; không phát cho người ngoài/không công khai private media. |
| UC48 — Nhận thông báo về thời hạn khiếu nại | Một phần: có order/shipment/case sơ bộ, chưa có deadline đúng giai đoạn. | G05/G08/G09/G11, P1: Buyer 3 ngày nếu cho trả/chưa early complete; Seller 2 ngày từ nhận trả; KTV SLA 48h riêng. Reminder dừng/chuyển giai đoạn đúng, không reset hạn. |
| UC49 — Nhận thông báo khi khiếu nại hệ thống được xem xét | Một phần: notification, complaint order-bound chưa đủ nguồn. | G09/G07/G11, P1: system case + review/decision/compensation operation riêng; escalation/đọc hồ sơ chưa là quyết định hay tiền confirmed. |
| UC50 — Nhận thông báo khi cập nhật phí nền tảng | Một phần: policy version và audit/notification. | G04/G11, P1: policy-change event gửi Admin thực hiện, đúng hiệu lực; không tính lại C/doanh thu/thu bù tin không tăng P. Gửi người dùng theo UC58. |
| UC51 — Nhận thông báo khi vô hiệu hóa/kích hoạt tài khoản | Một phần: user status và audit/notification. | G10/G11, P1: restriction/status change event, gửi KTV/Admin thực hiện; không nhầm người nhận với chủ tài khoản bị khóa. |
| UC52 — Nhận thông báo có các vấn đề cần giải quyết | Một phần: moderation/payment reconciliation/notification. | G02/G03/G07/G09/G11, P1: work item/round/source, quyền KTV và due_at; tách yêu cầu trả, xét miễn và lỗi tiền; notify không tự assign/approve. |
| UC53 — Nhận thông báo xác nhận phê duyệt, giải quyết | Một phần: moderation decision và notification. | G02/G03/G04/G09/G11, P1: event decision/actor/round; gửi KTV thực hiện, tin chờ phí khác ACTIVE; quyết định hoàn khác settlement confirmed. |
| UC54 — Nhận thông báo xác nhận tạo mới, cập nhật voucher | Một phần: vouchers và notification. | G06/G11, P1: immutable policy-change event gửi Admin thực hiện; tạo campaign không tự cấp voucher mọi người. |
| UC55 — Nhận thông báo xác nhận tặng, thu hồi voucher | Một phần: notification nhưng chưa có grant history. | G06/G11, P1: grant/revocation event gửi KTV thực hiện; không sửa ưu đãi order cũ, dedup cấp/thu hồi. |
| UC56 — Nhận thông báo khi có tin nhắn vi phạm quy định | Một phần: messages/reports nhưng reports chưa có message/evidence relation. | G09/G10/G11, P1: report_message link + private evidence snapshot; chỉ KTV có quyền, dấu hiệu chưa là penalty decision. |
| UC57 — Nhận thông báo từ Admin | Một phần: notification riêng user có read flag. | G11, P1: dispatch/sender/audience snapshot + từng recipient; chỉ Buyer/Seller/KTV Admin chọn; policy message không thay snapshot hay tiền. |
| UC58 — Gửi thông báo | Một phần: notification cho từng user, thiếu sender/đợt gửi/retry. | G11, P1: dispatch có Admin/command/content/time/target; snapshot recipients unique(dispatch,user), save trước phát; delivered khác read, không gửi Khách. |
| UC59 — Xem thông báo | Một phần: notifications có user/read_at và index thời gian. | G11, P1: cursor(created_at,id) ổn định, recipient access, version reference; Khách chỉ phiên của mình; đọc không đổi nghiệp vụ hoặc reveal dữ liệu. |
| UC60 — Điều hướng theo thông báo | Một phần: reference_type/reference_id BIGINT. | G11, P1: hỗ trợ ID UUID và revision/assessment/round khi cần; structured target allowlist thay arbitrary URL; kiểm tra lại quyền/trạng thái ở đích, link không là lệnh chuyển tiền. |

### 4.5. Trust, quản trị và lịch sử — UC61–UC77

| UC / tên hiện hành | Mức và dữ liệu đang có | Khoảng thiếu → hướng mở rộng |
|---|---|---|
| UC61 — Đánh giá đối tác sau giao dịch | Một phần: reviews 1–5 sao, unique(order,reviewer,reviewee), không tự đánh giá. | G05/G08/G10, P1: enforce reviewer/reviewee là hai bên, giao thành công; revisions để sửa/gỡ; aggregate/projection tính từ valid reviews − penalties, không sao giả, replay không cộng lặp. |
| UC62 — Yêu cầu trả hàng | Mâu thuẫn: complaint một order/active và JSON evidence nhưng chưa vòng/approval/deadline/return flag. | G05/G08/G09/G07, P0: một RETURN case/active order chứa các rounds; nộp/bổ sung ≤3 ngày, chưa early complete; Seller/KTV approve trước gửi; đủ hồ sơ mở KTV SLA48h; evidence versions, default cả đơn. |
| UC63 — Đề nghị xem xét lại hoàn tiền sau khi nhận hàng trả | Mâu thuẫn: complaint một resolution không bảo toàn quyết định cho trả và vòng xét miễn. | G08/G09/G07, P0: cùng case UC62, DAMAGE_EXEMPTION round; valid returned_at +2 ngày; valid submitted_at mới chặn tiền; chỉ KTV miễn, bác/thiếu căn cứ hoàn 100% G. |
| UC64 — Gửi khiếu nại lỗi hệ thống | Mâu thuẫn: complaints.order_id NOT NULL, reason/resolution dành giao dịch. | G09/G04/G07, P0: SYSTEM_ISSUE case với loại target product/fee/payment/order/account, không bắt buộc order; ảnh hưởng tiền chỉ hold phần liên quan; không dùng lỗi hệ thống mở quyền trả trái cờ. |
| UC65 — Chuyển hướng khiếu nại | Thiếu: chưa có assignment/handover history. | G09/G10, P1: case_event có KTV chuyển/Admin nhận/lý do/time/command; không đóng case, sửa evidence hoặc tự đền bù; giữ quyền theo loại vấn đề. |
| UC66 — Quản lý tài khoản KTV | Đủ nền: users/roles/user_roles, status và V11 role KTV. | G01/G10, P1: Admin-only service, password reset/forced change/session revoke theo thao tác; status/role audit, không xóa actor cũ; public registration không cấp KTV/ADMIN. Không cần bảng user KTV riêng. |
| UC67 — Quản lý tài khoản người dùng | Một phần: users status, roles, products owner. | G10/G09/G05, P0: restriction mode NEW_TRANSACTIONS_BLOCKED tách login block; giữ quyền nghĩa vụ cũ khi còn hồ sơ/hạn phản hồi; status actor/reason/history; confirmed system outage có bản bù hạn, không gia hạn do mở muộn. |
| UC68 — Xét duyệt hồ sơ eKYC | Mâu thuẫn: có reviewed_at/verified_by nhưng AI/MVP cho verified không KTV. | G02/G10, P0: decision unique(profile revision/final), lock/version + command; reject reason; evidence/OCR/AI đúng bản; eKYC Buyer khác duyệt seller profile và cấp SELLER nguyên tử. |
| UC69 — Quản lý voucher | Một phần: mã normalized/unique, discount/window/limits/version và order snapshot. | G06, P1: policy revision + product/checkout scope do Admin thiết lập; PLATFORM sponsor cho quy tắc mới; một voucher/group, points sau; không sửa grant/redemption cũ hoặc phục hồi khi hủy/trả. |
| UC70 — Tặng voucher | Thiếu: chưa có ownership, grant/revoke và actor/source compensation. | G06/G09/G10, P1: KTV/grantee/voucher/time/reason/command; grant events giữ lịch sử; revoked khác redeemed, compensation voucher riêng goods refund. |
| UC71 — Quản lý trạng thái giữ tiền và giải phóng tiền | Mâu thuẫn: một payment aggregate, full-only refund, thiếu money blockers/components. | G07/G05/G08/G09, P0: từng order/component hold/release/refund, balance/concurrency/idempotency; giao hợp lệ +3 ngày hoặc early completion và không blockers; fee tin riêng, quyết định khác confirmed operation. |
| UC72 — Quản lý phí hệ thống | Mâu thuẫn: system_fee_policies là per-order buyer/seller fee, chưa ledger phí tin. | G04, P0: immutable fee policy; approved P, prior approved P, F, C_before, D; initial 10.000 nếu P<100.000, 10% nếu P≥100.000; P tăng tính F biểu lúc duyệt, D=max(0,F−C), C chỉ confirmed, giảm không hoàn. |
| UC73 — Kiểm duyệt tin đăng | Một phần: decision append-only/reviewer/command/version/content proof. | G03/G04/G10, P0: review đúng immutable revision/checklist/media; final decision unique/revision; approved→chờ phí nếu D>0, D0 đủ quyền→ACTIVE; AI không thay KTV, không lấy media bản mới chứng minh bản cũ. |
| UC74 — Xử lý báo cáo vi phạm và quản lý điểm phạt | Một phần: reports target user/product/status/reviewer. | G09/G10, P1: message target/evidence, dedup cùng sự việc; penalty policy/ledger gắn quyết định confirmed, sửa có lịch sử; khóa/ẩn không tự hoàn/miễn/thu phí; giữ quyền đơn cũ. |
| UC75 — Xử lý tranh chấp | Mâu thuẫn: complaint một kết luận/refund_amount chưa mô hình hai vòng và tiền G. | G09/G08/G07, P0: một case nhiều rounds, final decision unique mỗi vòng; first approval≤48h; Seller2 ngày từ returned delivery; G full refund hoặc KTV exemption, không intermediate damage percent; decision không là tiền confirmed. |
| UC76 — Xử lý khiếu nại hệ thống | Một phần: audit, payment reconciliation và complaint sơ bộ. | G09/G06/G07, P0: system case/response/assignment/decision correction không ghi đè; compensation unique/source, voucher qua UC70; unresolved direct transaction issue hold phần liên quan; quá hạn báo Admin theo dõi. |
| UC77 — Lịch sử hệ thống | Một phần: audit_logs JSON before/after, moderation decisions/legacy có append-only trigger. | G10 và toàn bộ nhóm, P0: audit_logs/domain decisions append-only theo quyền ứng dụng; actor ID/correlation/version, transaction cùng thao tác; mask bank/CCCD/secret; riêng fee assessment/receipt/ACTIVE và money results. |

### 4.6. Báo cáo, hủy và hoàn — UC78–UC83

| UC / tên hiện hành | Mức và dữ liệu đang có | Khoảng thiếu → hướng mở rộng |
|---|---|---|
| UC78 — Kết xuất báo cáo số lượng người dùng | Đủ nền: users.status/created_at/user_roles. | G10/G12, P1: COUNT distinct user ACTIVE theo phạm vi; phân nhóm nhiều role không cộng trùng. Đường lịch sử hoạt động cần status history; timezone/filter/cutoff thống nhất bảng/chart/file. Không cần bảng lưu mọi báo cáo. |
| UC79 — Kết xuất báo cáo doanh thu | Mâu thuẫn: order seller_system_fee/held payment không phải doanh thu phí tin hiện hành. | G04/G12, P0: SUM khoản confirmed initial/top-up theo confirmed_at; không SUM C mỗi revision, tiền hàng/escrow/pending không revenue; policy giảm/hủy/trả không tự trừ fee cũ. |
| UC80 — Thống kê mặt hàng được tìm mua nhiều/ít nhất | Một phần: có products/categories/order_items để đếm mua; chưa search/view history. | G12/G03/G05, P1: event SEARCH/VIEW/PURCHASE tách rõ metric, định nghĩa đơn vị/count/time window trong query/export; nhiều danh mục không nhân hàng, lượng bán khác số đơn; không tự gộp chỉ tiêu. |
| UC81 — Thống kê số lượng hàng bị trả và khiếu nại | Một phần: complaint/status/order nhưng chưa loại/giai đoạn/kết quả rõ. | G09/G08/G12, P1: đếm distinct case đã kết thúc, UC62+UC63 một case; tách system issue, units returned khác cases; confirmed full G refund/KTV exemption riêng, pending không completed. |
| UC82 — Hủy đơn hàng | Một phần: cancelled_at/reason, scheduler expiry và reservation release. | G05/G07/G08/G09, P0: actor/cause/command/snapshot giai đoạn, hủy trước nhận cả prepaid/in-transit; goods G + unused shipping eligible, phần vận chuyển theo mock; nhả đúng quantity, gửi rồi cần nhận về/duyệt trước bán lại; không phạt/restore perks/hoàn phí tin. |
| UC83 — Hoàn tiền đơn hàng | Mâu thuẫn: CHECK refund_amount = payment.amount; một trạng thái refund/release toàn payment. | G07/G08/G09/G06, P0: confirmed money portions từng order, G sau voucher/points; return không refund purchase shipping; cancellation unused shipping riêng; round/KTV exemption/blocker đúng; retry lỗi một phần không lặp phần thành công, không dùng bank profile tự chuyển tiền thật. |

## 5. Thiết kế mở rộng quan hệ và ràng buộc

### 5.1. G01/G02 — tài khoản, hồ sơ eKYC và quyền bán

- auth_challenges: challenge_id, purpose, email/phone hoặc user_id, code_digest, issued_at, expires_at, attempt_count, max_attempts, next_send_at, consumed_at, invalidated_at, command_id. Đăng ký cần challenge trước user; challenge login không cấp quyền/session đầy đủ.
- Một challenge đang mở theo subject/purpose. Không dùng partial index với điều kiện expires_at > now(); khi cấp mới khóa subject/purpose và invalidate mã trước. CHECK dữ liệu nội tại, unique ngăn hai mã mở; worker xử lý hết hạn.
- user_security_settings: user_id PK/FK, email_2fa_enabled, verified_at, version. Một update settings cần challenge đúng purpose và kết quả UC08 còn hạn; luật quyền ở service, không dựa vào boolean client.
- Tách ekyc_profiles/versions khỏi seller_profiles. Một seller profile/user; seller approval trỏ eKYC đã duyệt và đủ pickup/bank. user_roles SELLER chỉ là projection quyền từ quyết định, không thay hồ sơ.
- eKYC revision giữ submitted info, OCR original/correction, AI outcome/model và decisions; index unique một phiên đang DRAFT/IN_PROGRESS/PENDING/user. Bản VERIFIED cũ vẫn lưu khi có lượt mới; tài khoản có cờ hạn chế giao dịch mới trong tái xác minh.
- identity_document_registry: normalized CCCD dưới HMAC/keyed digest để unique và tra trùng, user_id, first/last verification refs. Giữ mã định danh cũ của cùng user; cùng CCCD không cấp user khác. Không dùng plain hash dễ dò trên không gian CCCD, không log số gốc.
- Private assets: lưu object key/reference, loại FRONT/BACK/LIVE_FRAME/REFERENCE, version, owner/profile, expires_at, trạng thái xóa; ảnh/mẫu trong kho riêng tư, truy cập KTV/UC08 đúng quyền. Không đưa embedding vào client/audit/Git hoặc khôi phục cột vector công khai đã bỏ ở V2.
- verification_attempts phân biệt NON_MATCH/SERVICE_ERROR; counter/cooldown theo session, tối đa15 nonmatch, ≥3s. Missing camera/reference không pass. Manual AI fallback cần policy/authorization record, không giả AI match.
- quick_auth_results gắn user + approved source + purpose context/result_time/expires_at. Hết hạn nguồn hoặc đang tái eKYC làm kết quả không dùng được dù result chưa tới 3 tháng.
- final KTV decision unique(profile_version_id), actor FK, rejection reason, command unique. Lock/version + quyết định + quyền/hồ sơ + outbox trong cùng transaction; role hiện tại của actor phải được service xác thực.

### 5.2. G03/G04 — bản tin, media, kiểm duyệt và phí

Quan hệ chính: products 1–N product_revisions; revision 1–N revision_media; revision trỏ checklist policy version và có moderation decision; decision/approved revision 1–1 fee assessment; assessment 0–N payment attempts nhưng chỉ một charge cần xác nhận cho D; confirmed fee entries thuộc cùng product.

- product_revisions: unique(product_id,revision_no), content snapshot (giá đơn vị, lượng duyệt, tình trạng/lỗi/danh mục, return flag, buyer eKYC, delivery options, pickup info/checklist). Giữ FK tới bản đã duyệt/current public revision. Nội dung bản submitted không UPDATE làm mất dấu vết.
- revision_media: đúng file, object key/hash, loại/duration/bytes, order, retention; CHECK loại-specific khi đã nhận/xác minh. Metadata bắt buộc cho file hoàn tất, không bắt buộc ngay lúc đang upload.
- Checklist policy version theo danh mục con; nhiều danh mục lấy hợp mục/bỏ trùng, một media được map nhiều mục. Có thể giữ checklist snapshot JSON bất biến trên revision để giảm bảng; ID/version/policy và file mapping phải truy xuất được.
- Giới hạn số file là tổng trên revision, cần lock revision/parent khi INSERT/UPDATE/replace; không chỉ CHECK trên từng dòng. Publish/review kiểm tra tối thiểu 1 ảnh/1 video và tối đa 5/2 ở mọi đường chuyển hợp lệ.
- analysis run giữ input revision/hash, file results, frame/model/policy version, thời gian, processing status và conclusion riêng. enum cuối chưa chốt trong UC16; chỉ cần giữ phân biệt ý nghĩa, không tự áp tên enum mới thành yêu cầu.
- product_moderation_decisions hiện có tiếp tục append-only; thêm revision FK/checklist snapshot và final unique. Proof legacy NULL vẫn phải duyệt lại, không đánh dấu tự đủ.
- Mở rộng độ dài products.status (ví dụ VARCHAR(40)) và CHECK cho APPROVED_AWAITING_FEE. Không chỉ thêm giá trị vào CHECK trên VARCHAR(20). ACTIVE là projection khi bản đúng đã duyệt, phí đủ và quyền/tồn đủ.
- listing_fee_policies: ngưỡng, fixed_below, rate_at_or_above, currency, effective window/version, Admin actor. Ngăn hiệu lực chồng nhau bằng exclusion hoặc transaction khóa lịch policy; immutable bản đã tham chiếu.
- listing_fee_assessments: product/revision/decision/policy, P_current/P_previous_approved, F, C_before, D, assessed_at. Có một assessment cuối/revision; đủ dữ liệu kiểm tra độc lập.
- Lần đầu: P = đơn giá × lượng duyệt; F =10.000 nếu P<100.000, F=10%P nếu P≥100.000. Các lần sau nếu P tăng so bản duyệt gần nhất: D=max(0,F−C); P không tăng: D=0. D0 không tạo payment 0 đồng.
- listing_fee_charges/confirmed entries: assessment_id, purpose LISTING_FEE, expected D, provider/attempt/transaction, confirmed_at, amount, command key. C là SUM confirmed fee của product, có thể cache nhưng ledger là nguồn gốc. Không tăng C từ pending/rollback/callback không hợp lệ.
- Khóa product fee aggregate khi assess/confirm; payment cần đúng D, purpose/reference/revision; mỗi charge xác nhận một lần. Thay đổi tin khi khoản còn pending phải theo điều kiện UC17, không chuyển callback sang bản khác.
- C không giảm do giảm giá/lượng/biểu, hủy/trả order. Correct late/duplicate charge được đưa đối soát có căn cứ; không tự tính là phí hợp lệ lần hai, không hoàn dư từ amount mismatch.
- Ví dụ kiểm chứng: P80.000 lần đầu →10.000; sửa P200.000 biểu10% →D10.000; biểu15% lúc duyệt →D20.000 nếu C trước10.000; sau đó giảm P hoặc F<C →D0/C giữ nguyên. P80.000×5=400.000 →F40.000. Tại P100.000 dùng nhánh10%.

### 5.3. G05/G06 — lượng, nhóm đặt hàng và ưu đãi

Quan hệ chính: buyer 1–N checkout_groups; group 1–N orders; order 1–N order_items; item 1–1 reservation nguồn; product 1–N reservations. Group có một redemption voucher và một lần dùng điểm, allocations tới item/order.

- products hoặc inventory aggregate giữ total/sold/unavailable quantity và version; inventory_reservations giữ product_id, order_item_id/order_id, quantity, state, created_at/expires_at/released_at/consumed_at. Lượng khả dụng = tồn có thể bán − các giữ hợp lệ; hàng gửi/đang trả không tự thêm vào tồn.
- Không chỉ bỏ CHECK quantity=1: phải thay reserved_order_id đơn, service đánh SOLD, calculation và projection hiển thị. Lượng nguyên dương; sold/held không âm; tổng không vượt lượng thực có. Khóa product rows theo ID thứ tự ổn định để tránh oversell/deadlock; giỏ không tạo reservation.
- checkout_groups có buyer, idempotency_key + request fingerprint, created_at/payment_due_at, chung snapshot address/carrier/method/payment_method, expected_total và policy snapshot. unique(buyer,idempotency_key); cùng key khác payload trả conflict.
- Mỗi order có seller, return flag snapshot, chung kiện/ngày bàn giao; items trỏ product_revision_id và price/qty/discounts snapshot. Enforce seller nhất quán bằng composite FK hoặc constraint trigger, không chỉ FK product_id và order_id riêng.
- Chia order theo seller + cờ trả trong điều kiện kiện/phương thức hợp lệ. Nếu lựa chọn chung không đáp ứng mọi món hoặc không thể gom đúng điều kiện: fail-all, không tự bỏ món/đổi phương thức/tách sang checkout khác để che lỗi.
- Tổng item/order/group và allocation đúng cùng transaction; tổng tiền không lấy từ client. Giữ/đơn/outbox/consume offer/voucher/points commit tất cả hoặc không gì. Deadline group cố định created_at+1h, reopen/retry không reset.
- Snapshot: đơn giá niêm yết/đã thương lượng, qty, goods gross, voucher allocation, point allocation, G net paid, seller entitlement, shipping quote/discount, return flag, eKYC requirement, phương thức và policy version. Tiền ưu đãi sàn tài trợ ghi riêng để không nhầm Buyer cash G với entitlement Seller.
- voucher_grants: owner, campaign/policy version, source SELF_CLAIM/KTV_GIFT/COMPENSATION, actor/time/reason, state/expiry. Grant history chỉ thêm; redeemed không bị sửa thành unused khi hủy/trả.
- Một checkout_redemption voucher/group; product scope do Admin quản lý, allocation chỉ vào eligible bases. Nếu hệ thống hỗ trợ ưu đãi ship theo campaign, ghi thành phần ship riêng; points dùng trên tiền hàng còn lại sau voucher, không vượt base/balance.
- discount_allocations unique(redemption,item/component), rounding xác định; tổng allocation bằng tổng giảm. Không để rounding tạo G âm hoặc hoàn vượt cash goods.
- reward_accounts là projection có lock/version; reward_ledger chỉ thêm CREDIT/DEBIT/correction, source/command unique. Award chỉ khi completed đủ điều kiện, không khi mới paid. Hai lần dùng cùng balance không được âm; hủy/trả không phục hồi debit đã dùng.
- order_events giữ actor/cause/previous/current/time/source command. Các mốc paid_confirmed, seller_accept_due, valid_delivery, receipt, early completion, cancellation cần cột/projection riêng, không gom vào completed_at.

### 5.4. G07 — thu tiền, phân bổ, giữ, hoàn và giải ngân

Quan hệ chính: payment_intent 1–N attempts/receipts; intent CHECKOUT_GROUP 1–N payment_allocations tới orders; order 1–N fund components; component 1–N settlement operations/money holds. Listing fee dùng intent purpose riêng và không tạo allocation Buyer order.

- Giữ payments hiện có làm compatibility projection trong giai đoạn chuyển; chỉ một canonical nguồn tiền mới. Không để payments và ledger mới cùng tự điều khiển tiền.
- payment_intents: purpose, target group/fee charge, expected_amount/currency, fixed_deadline, state, version. Target phải đúng purpose, có FK qua quan hệ cụ thể hoặc các nullable FK với CHECK exactly-one; không chỉ reference_type/id không kiểm tra.
- payment_attempts bổ sung intent FK, idempotency/provider refs. V13 vnpay_payment_attempts và V14 payment_attempts đang cùng tồn tại: chọn canonical sau khi kiểm kê usage/backfill trên DB test; không drop dữ liệu/unique cũ ngay.
- Provider transaction accepted ledger unique(provider,transaction_id), receipt→attempt→intent xác định. V14 chống lặp theo attempt/order chưa thay được guard nguồn thu dùng chung nhiều purpose/nhóm; callback trùng hoặc reference chéo không được ghi hai khoản.
- Nối composite FK giữa IPN event/review/reconciliation và đúng attempt/payment/order hoặc intent allocation. Các trường order_id/payment_id rời nhau trong audit/event không đủ chứng minh cùng aggregate. Actor dùng FK user/service principal + snapshot label, không chỉ email VARCHAR.
- order_fund_components tối thiểu GOODS_CASH, SHIPPING_CASH và entitlement/ưu đãi sàn theo nguồn; listing fee ledger riêng. Amounts expected/confirmed/held/refunded/released có tổng bảo toàn. Tiền sàn tài trợ có nguồn riêng, không giả như Buyer đã trả.
- settlement_operations: component_id, type REFUND/RELEASE, amount, cause decision/cancellation, recipient, idempotency_key, state REQUESTED/PROCESSING/CONFIRMED/FAILED, provider operation ref, requested/confirmed_at. Retry dùng cùng operation; sau CONFIRMED không gọi lại dù phần khác lỗi.
- Khóa component/escrow aggregate trước reserve operation amount. Tổng refunded + released + pending-reserved không vượt confirmed amount khả dụng của đúng nguồn; không hoàn và giải ngân cùng đồng tiền. Unique operation key chưa đủ chống hai command key khác nhau dùng cùng số dư.
- money_holds: order/component, source case/round/issue, reason, opened/closed_at. Có thể nhiều blocker, hết một không đóng các blocker khác. Giữ chỉ phần liên quan, không chặn toàn nhóm/đơn độc lập.
- Return: G = hàng thực trả sau voucher/points; hoàn100% G, không ship chiều mua; KTV miễn là quyết định cuối hợp lệ, không mức20/80 hoặc partial theo hư hỏng.
- Cancel: chưa thu không tạo refund; hàng G và ship chưa dùng hoàn riêng; đã gửi phí giao/thu hồi ghi theo mock và trách nhiệm đã chọn. Correct payment sau cancel ghi đối soát/refund, không reopen order/restore reservation.
- Release sau valid delivery+3 ngày hoặc early completion được chấp nhận, và không case/blocker. Cờ không trả chỉ đóng UC62, không giải ngân tức thì bỏ qua kiểm tra.
- Thông tin ngân hàng có che/reference cho mock; không coi đổi bank profile là quyền payout thật. Quyết định/thỏa điều kiện/operation success là ba sự kiện khác nhau.
- Chỉ bỏ ck_payments_refund_amount và đổi thành refund≤amount vẫn chưa đủ: thiếu allocation/component/holds khiến hoàn ship hoặc release trùng còn xảy ra.

### 5.5. G08/G09 — giao nhận, trả và các vòng xử lý

- shipments chuyển unique(order_id) thành unique(order_id,leg) nếu một leg/chiều là đủ; leg OUTBOUND/RETURN. Retry là attempt/event dưới leg, không nhiều shipment không kiểm soát cho cùng kiện. Cancellation recall có thể là subtype RETURN với nguyên nhân rõ, không mở quyền trả sau nhận.
- Snapshot method/carrier/fee quote, status projection, first_failure_at/retry_due_at; shipment_events có source + source_event_id unique, occurred_at/received_at và raw metadata đã lọc. Callback muộn không đảo mốc đã xác nhận hoặc làm giao hai lần.
- handover_confirmations gắn đúng order/leg/party và revision của việc bàn giao; Buyer/Seller outcome riêng. OTP digest, expiry/attempts/consumed_at riêng leg, không dùng OTP outbound cho returned receipt.
- Hai bên thành công hoặc OTP hợp lệ tạo valid delivery; một bên nói đã giao không đủ. Hai thất bại→failed, trái kết quả→verification + hold; cả hai im lặng14 ngày→cancel/settlement. Bản ghi outcome và quyết định xử lý mâu thuẫn được giữ.
- valid_delivered_at lấy mốc hợp lệ sớm nhất và không reset bởi mở màn hình/xác nhận muộn. return_allowed snapshot mở return_due_at=valid delivery+3 ngày; release eligibility vẫn kiểm tra riêng. early_completion lưu cảnh báo/acceptance rõ, không từ nhận hàng suy ra từ bỏ quyền.
- RETURN case approval_at mở pickup_expected_at≤24h và pickup_deadline=approval+3 ngày. pickup_attempts có attempt_no≤3, failed reason/responsibility BUYER/CARRIER/UNKNOWN/DISPUTED, ngày theo timezone nghiệp vụ; tối đa một lần/ngày với unique/locking.
- Hết3 ngày hoặc3failed sớm hơn thì dừng; hẹn lại giữ deadline. Chỉ mock xác nhận Buyer fault mới có thể đóng theo quy tắc; carrier/unknown/disputed cần KTV và hold, không gán Buyer mặc định.
- valid_returned_at mở seller_response_due_at=+2 ngày. Đã nhận trả khác đồng ý hoàn. Hết hạn không có đề nghị miễn hợp lệ/blocker→request full G refund; upload dở/chat/mở form không được giữ tiền vô hạn.
- cases tách RETURN và SYSTEM_ISSUE, target đúng loại. RETURN bắt buộc order và active partial unique/order; SYSTEM_ISSUE có thể target fee/product/account không order. FK/check loại mục tiêu; không dùng reason OTHER để giấu khác biệt vòng đời.
- case_rounds: stage RETURN_APPROVAL hoặc DAMAGE_EXEMPTION (tên đề xuất), opened/submitted/complete_at/deadline/state, previous round. One final decision/round, final decisions immutable, correction tạo bản superseding có lý do, không ghi đè approval trước.
- Vòng trước trả: Seller hoặc KTV có quyền approve; KTV SLA48h từ hồ sơ đủ, overdue event tới Admin theo dõi không autoapprove/transfer authority. Vòng xét miễn sau returned receipt: chỉ KTV, serious damage đủ căn cứ mới miễn, thiếu mức/bằng chứng→full G refund.
- case_evidence giữ author/role/time/object ref/hash/revision/round/type; original và bổ sung riêng. ≤5 ảnh/2video≤60s mỗi bộ theo chính sách file; Buyer bổ sung đúng hạn; giữ bằng chứng Seller trong vòng được phép.
- case_events lưu assignment, escalation KTV→Admin, reason, submission/compensation/correction. Compensation voucher trỏ grant UC70 và source decision unique; không là refund G.
- Retention theo max(last_related_completion) và không có active obligation/hold; dọn trong≤7 ngày sau mốc cuối theo UC15/UC62. Bỏ trigger fixed+3 ngày thiếu blocker trước bật cleanup mới. KYC reference giữ3 tháng là chính sách khác; CCCD registry và quyết định giữ lịch sử.
- Media mồ côi có cleanup riêng/outbox; cloud upload không commit cùng PostgreSQL, xóa object phải có retry và reference/hold check. Không tự xóa media bản tin còn ACTIVE/còn lượng.

### 5.6. G10/G11/G12 — quản trị, thông báo và báo cáo

- account_restrictions lưu phạm vi/new_transactions/login/obligations, reason/source decision, actor/time; khóa giao dịch mới không bắt buộc khóa truy cập nghĩa vụ cũ. Policy thu hồi phiên phải xét cùng quyền hiện tại; JWT cũ không tự bảo toàn quyền.
- seller_buyer_blocks unique pair, events để giữ lịch sử. reports bổ sung message FK/evidence và incident key để gom cùng sự việc; báo cáo mới không tự tạo penalty.
- penalty_policies version và penalty_ledger gắn quyết định KTV; source unique, correction append-only. reviews bảo đảm đúng đối tác/order và đủ delivery; edit/delete dùng history, recompute rating đúng một contribution/review.
- audit_logs chỉ thêm đối với role ứng dụng; trigger/revoke UPDATE/DELETE, quyền migrations/bảo trì tách. Không tuyên bố trigger chống được superuser. Audit bắt buộc ghi cùng transaction và redact theo allowlist.
- notifications thêm event_id, source/version, recipient, sender/dispatch, structured target; unique(event,recipient). Nếu reference dùng UUID thì đổi kiểu/quan hệ phù hợp, không ép BIGINT. Recipient FK cho account notification; guest chỉ session, không lộ mã private.
- notification_dispatches snapshot Admin/content/recipients/command; delivery/read riêng người nhận. Outbox transaction giữ event đã commit; consumer checkpoint unique(consumer,event), retry/backoff và reminder condition check ngay lúc gửi. Reminder theo deadline gốc, không reset dữ liệu nguồn.
- Index cursor(user_id,created_at,id), unread tương ứng; deadline jobs partial index trạng thái còn chờ + due_at. Index case assignment/status/deadline, settlement pending và retention pending theo workload sau đo.
- interaction_events tách SEARCH/VIEW và nguồn PURCHASE theo order item thành công; event key chống client retry/bot lặp theo định nghĩa metric. Tìm kiếm query không luôn có product_id, có thể lưu result/product interactions riêng, không quy mọi search thành lượt mua.
- UC78 đếm người dùng distinct, không đếm role. Lịch sử ACTIVE theo kỳ cần events/status history; dữ liệu hiện tại chỉ đủ snapshot hiện tại, không giả tái dựng quá khứ thiếu.
- UC79 SUM từng confirmed fee receipt trong kỳ, không SUM C nhiều lần. UC80 tách search/views/units bought/order count; UC81 distinct RETURN case đã xong và SYSTEM_ISSUE riêng, rounds/evidence không nhân số vụ.
- Query/view export cùng timezone Asia/Saigon, range rõ, filters/cutoff; báo cáo chỉ đọc dữ liệu trong quyền. export_runs là tùy chọn nếu cần lịch sử tải, không bắt buộc thêm kho thống kê hoặc materialized views trước khi cần.

## 6. Thứ tự mở rộng và xử lý dữ liệu cũ

Không gán ngay số V15–Vxx vì cần kiểm tra version mới nhất lúc triển khai. Tất cả là migration mới tiến về trước; không sửa V1–V14, không repair/reset history để né dữ liệu cũ.

| Đợt | Phạm vi và điều kiện hoàn thành | Chuyển đổi/backfill cần lưu ý |
|---|---|---|
| 1 — nền snapshot/quyền | G01/G02/G03 + audit/outbox chung. Có eKYC KTV quyết cuối và phiên bản tin/media có thể truy xuất. | Không tự nâng AI_EKYC/MVP_BYPASS cũ thành KTV-approved; đánh legacy source và yêu cầu xét theo chính sách. CCCD cũ đã bị V2 xóa không thể phục hồi bằng metrics. Snapshot tin cũ ghi known-at-migration, không giả là bản lúc mua. |
| 2 — phí tin | G04 + payment intent purpose cơ bản G07. Duyệt đúng bản, assess P/F/C/D, confirm rồi ACTIVE; UC79 đọc ledger đúng. | Per-order fees cũ không chứng minh phí tin đã thu. Không backfill C từ seller_system_fee hoặc tự truy thu. Tin đang ACTIVE không có receipt cần chính sách chuyển đổi rõ trước bật gate; chưa có chứng cứ thì không tự đánh PAID. |
| 3 — lượng và nhóm checkout | G05 + G06 + group allocations G07. Tạo/giữ fail-all, snapshot, 1h, một voucher rồi điểm. | Legacy quantity=1 map rõ nguồn; RESERVED map một reservation, SOLD map tình trạng quan sát được. Không mặc định quantity>1 từ dữ liệu thiếu. UUID cũ map group head, kiểm tra chung buyer/address/method trước merge. |
| 4 — giao/return/case/settlement | G08/G09 và hoàn thiện G07. Có outbound/return, earliest valid delivery, các hạn, blockers và operation confirmed riêng. | Legacy shipment map OUTBOUND nếu có căn cứ; RETURNED cũ chưa chứng minh timeline trả mới. Complaint cũ archive/mapping theo known facts, không tạo giả KTV miễn hoàn. Payment summary cũ giữ nguồn LEGACY_UNALLOCATED đến đối soát, không tự release/refund lại. |
| 5 — hoàn thiện UC còn lại | G10/G11/G12 và APIs/truy vấn/UI tương ứng. Grant/phạt/notify/export phản ánh canonical data. | Không cấp điểm lịch sử, voucher owner, số search/view hoặc phạt từ dữ liệu không có chứng cứ. Giá trị missing hiển thị chưa có dữ liệu; báo cáo không lấp bằng số giả. |
| 6 — tối ưu | Chỉ sau các acceptance gates: index đo EXPLAIN, jobs, batch cleanup, delivery retries, materialized view khi cần. | Đo trên DB test/clone, kiểm soát locks/size/latency; không thay policy để dễ tối ưu. Redis/WebSocket không thay invariant PostgreSQL. |

Ở mỗi đợt: thêm cấu trúc nullable trước → backfill có nguồn và báo thiếu → kiểm tra inconsistent rows → thêm/validate FK/CHECK/unique → triển khai service tương thích → chuyển canonical reads/writes → cuối cùng retire projection cũ. Cross-table invariant dùng composite FK/constraint trigger hoặc service transaction + lock phù hợp; CHECK không truy vấn bảng khác.

Thay đổi UNIQUE/FK của products reservation, payments/order và shipments/order cần đánh giá consumer/service, lock và downtime. Constraint/index mới chạy trên DB riêng trước; nếu dùng CREATE INDEX CONCURRENTLY phải quản lý transaction Flyway đúng cách. Rollback tài chính không bằng xóa ledger; dùng correction/compensating operation có căn cứ.

Các việc cần chốt khi viết migration là kỹ thuật/chuyển đổi, không mở lại chính sách UC đã chốt: tên và canonical bảng mới, grant/points policy parameters đang cấu hình, treatment tin/tiền legacy thiếu chứng cứ, định nghĩa metric/search counting được chọn cho từng báo cáo.

## 7. Kiểm chứng bắt buộc khi triển khai

Các kịch bản dưới đây là acceptance plan, **chưa chạy trong lần phân tích này**. Dùng PostgreSQL cùng major17 trong DB/container test riêng, providers mock/sandbox.

| Nhóm | Kịch bản phải đạt |
|---|---|
| Identity | Đăng ký chưa OTP không full session; public không SELLER/KTV/ADMIN; Google+password cùng lớp hai; reset nguyên tử/revoke sessions; hai KTV cạnh tranh chỉ một final decision; CCCD cũ không cấp user khác; reference hết3tháng fail; 15nonmatch/cooldown/error riêng. |
| Catalog/media | 1–5 ảnh/1–2video, byte loại-specific, file count race/UPDATE không vượt; DRAFT thiếu tối thiểu được; input đổi không dùng AI/proof cũ; order vẫn thấy revision/media lúc mua. |
| Phí tin | P dưới/tại/trên100.000; quantity multiplication; tăng P cùng biểu vẫn D; biểu tăng mà P không tăng D0; F<C không refund; hai callbacks chỉ tăng C một lần; late/duplicate đúng khoản vào reconciliation; paid nhưng activation lỗi không đòi tiền lại. |
| Checkout/tồn | Hai buyers giữ tổng không vượt available; qty>1 và bán một phần; một món lỗi fail-all/no hold; seller/cờ trả/kiện/common method đúng; same command same group, khác payload conflict; deadline1h không reset; expiry vs callback race không resurrect. |
| Ưu đãi | Grant limit/cấp-thu hồi cạnh tranh; một voucher/group; product scope; points sau voucher/không âm; rounding allocation sum; hủy/trả không restore voucher/points; credit completion chỉ một lần. |
| Payment | Tampered/reference/amount/currency mismatch không success/hoàn dư; receipt replay/cross purpose không double money; late payment cancelled order reconciliation; allocation sum; refund và release concurrent không cùng tiền; một operation fail retry không lặp confirmed operation khác. |
| Giao/trả | Outbound/return OTP không dùng chéo; hai bên trái kết quả hold; 14ngày silent không auto-deliver; valid earliest date không reset; 24h pickup,3ngày/3failed/1daily; reschedule không extend; carrier/unknown fault không mặc định Buyer. |
| Case/refund | No-return chỉ chặn sau nhận; received khác early completion; Buyer3ngày, Seller2ngày, KTV48h từ đủ hồ sơ; valid Seller request trước deadline vs timer race; cùng RETURN case hai rounds; KTV-only exemption; full G/ship riêng; system issue hold đúng phần. |
| Audit/notification/report | Audit/domain history không UPDATE/DELETE bằng app role; redact; event+recipient dedup, authority recheck khi mở; guest không đọc private; cursor ổn định; UC78 distinct users, UC79 confirmed fee only, UC80 metric riêng, UC81 onecase/manyrounds. |
| Migration | Clean install tới mới nhất và upgrade V14+legacy fixture đều đạt; constraints validate/backfill có nguồn; Hibernate validate và consumer compatibility; không mất dữ liệu đã thu/quyết định/receipt; không repair checksum cũ. |

## 8. Phạm vi đã hoàn thành của lần rà soát

- Đọc 83 đặc tả hiện hành và phân tích một dòng cho từng UC, giữ tên theo báo cáo.
- Đọc migrations V1–V14, schema runtime, constraints/index/triggers trọng yếu, các service và test liên quan.
- Xác nhận live database local áp dụng V1–V14 thành công; xuất metadata/schema chỉ đọc.
- Lập 12 nhóm mở rộng, quan hệ/ràng buộc, thứ tự chuyển đổi và acceptance plan.
- Không chạy migration, thay dữ liệu nghiệp vụ hoặc sửa backend/frontend trong phạm vi TASK-0068. Các sửa frontend đang có trong workspace thuộc công việc khác.

Kết quả này là cơ sở để thiết kế schema mở rộng theo từng đợt. Việc schema có bảng/column không thay bằng chứng service, quyền, concurrency và luồng end-to-end khi nghiệm thu.
