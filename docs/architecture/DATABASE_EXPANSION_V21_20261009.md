# Database V21 — mở rộng theo báo cáo 83 use case

Ngày triển khai: 09-10-2026. Nguồn yêu cầu: [rà soát 83 UC](DATABASE_COVERAGE_83_USE_CASES_20261009.md), được người dùng cho phép triển khai bằng “tiến hành mở rộng database”.

Database local `og_shop` trên PostgreSQL 17.11 đã nâng **V14 → V21**. Có 21 migration success, 0 failed; 105 bảng public gồm Flyway history, tương đương 104 bảng nghiệp vụ, tăng 61 bảng. Có 7 view. Hash và số hàng theo toàn bộ cột V14 của **43/43 bảng legacy khớp khi rollout V15–V20**; backup V14→V21 trên clone độc lập cũng xác nhận 43/43 bảng khớp, tách khỏi auth session đang hoạt động. Không tạo giả eKYC/KTV/phí đã thu: canonical eKYC profiles và fee receipts vẫn 0; 25 product revision snapshots được đánh dấu `LEGACY_MIGRATION`.

Đây là triển khai nền database cho 12 nhóm mở rộng, gồm ràng buộc và primitive transaction. **Không phải nghiệm thu hoàn chỉnh 83 API/UI/use case**. Mã ứng dụng cũ vẫn dùng workflow legacy; phải triển khai các vertical slice dưới đây để sử dụng mô hình mới.

## Phạm vi và nguồn dữ liệu

| Migration | Nhóm/UC chính | Cấu trúc đã thêm |
|---|---|---|
| V15 | G01/G02; UC01–09, UC66–68 | Email OTP/challenge, security settings, eKYC revisions, HMAC document registry, private assets/attempts, KTV decisions, seller profile/approval riêng, quick-auth sessions/attempts/results |
| V16 | G03/G04; UC12–17, UC70, UC72–73, UC79 | Revision/media/checklist/AI-run, current/public revision FK, quantity counters, moderation revision proof, policy/assessment/charge/receipt phí tin |
| V17 | G05/G06; UC20–24, UC29–31, UC82 | Quantity integer, checkout group, inventory reservations, immutable order/item snapshots, order events, conversation links, voucher revision/grant/revocation/redemption/allocation, reward policies/accounts/ledger |
| V18 | G07; UC25, UC71, UC83 | Purpose-specific payment intents, nối attempts/IPN/reconciliation V14, confirmations/allocations, cash/subsidy components, holds, refund/release operations |
| V19 | G08/G09; UC26–28, UC32–33, UC61–65, UC74–76, UC82–83 | Shipment legs, events, pickup attempts, handover/OTP, early completion consent, return/system cases, rounds/evidence/decisions/events, money blockers và cleanup candidates |
| V20 | G10/G11/G12; UC10–11, UC14, UC34–60, UC69, UC77–81 | Restrictions/party blocks, penalty ledger, review history, immutable audit/status history, dispatch/delivery/reminder/dedup, interactions, export metadata và views |

Các UC thông báo dùng chung G11 và event từ nghiệp vụ nguồn. V21 thêm `zero_checkout_confirmations`: tổng nhóm/đơn phải bằng 0, đúng Buyer/trước hạn, chưa thanh toán; xác nhận và fund reservations nguyên tử, giữ proof riêng với tiền thu provider. Đơn toàn voucher/points vẫn có subsidy source; không tạo cash receipt 0đ giả. V15–V20 đã áp dụng nên không sửa checksum để bổ sung tình huống này.

Ma trận từng UC ở cuối tài liệu nối tới nhóm đã xác định trong bản rà soát, không biến một nhóm bảng thành bằng chứng mọi hành vi của UC đã hoàn tất.

## Các invariant đã được bảo vệ

- eKYC VERIFIED/REJECTED và Seller APPROVED/REJECTED cần quyết định KTV tương ứng trong cùng transaction. AI/eKYC approval không tự cấp SELLER; chỉ seller-profile approval cấp quyền. Registry CCCD là HMAC có key version và unique xuyên lịch sử, không dùng plaintext hoặc hash không khóa. Private reference/assets tối đa 3 tháng; quick auth cần match, đúng user/profile/reference, không tái eKYC đang mở; nonmatch tối đa 15/cooldown 3 giây, service error riêng.
- Revision UC83 bắt đầu DRAFT; nộp cần 1–5 ảnh, 1–2 video. Ảnh tối đa 5MB, video tối đa 50MB/60s; khóa parent chống race. Nội dung/media đã nộp giữ nguyên. Legacy metadata thiếu được giữ nguồn, không tự coi là bằng chứng đầy đủ.
- Phí tin tách khỏi order fees. `P = listed unit price × approved quantity`; policy đầu: dưới 100.000đ thu 10.000đ, từ 100.000đ thu 10%. `C` chỉ cộng confirmed receipts của cùng tin; revision tăng P thu `max(0,F−C)`, giảm/giữ P thu 0. Approval cần assessment; charge/receipt đúng số tiền; ACTIVE UC83 cần current approval, đủ phí và seller approval. Không hoàn phí cũ khi giảm giá/hủy/trả.
- Checkout có command/request digest, tổng bằng các đơn và deadline cố định 1 giờ. Item đúng Seller/revision/return flag/delivery method/accepted-offer owner. Group chung địa chỉ, phương thức và carrier; mọi item phải có reservation. Reservation khóa product và cập nhật held/sold; không giữ vượt khả dụng hoặc fund tiền đến sau hủy. Giao dịch mới bị chặn bởi restriction/party block/eKYC không còn hợp lệ, nhưng không xóa nghĩa vụ cũ.
- Mỗi checkout dùng một grant, giới hạn claim và một redemption; voucher policy có typed amount/percentage/cap/minimum/component/scope. Voucher trước points; phân bổ phải bảo toàn tổng, thuộc đúng group/product và đúng component. Reward balance đổi qua append-only ledger dưới khóa account, không âm. Không restore grant đã dùng hoặc xóa ledger khi hủy/trả.
- Payment intent có purpose/target/amount/method/deadline bất biến. VNPay confirmation cần receipt VERIFIED và event thuộc đúng attempt/intent; mock dùng đúng method. Confirmation đúng amount, callback trùng có unique provider transaction. Payment đến sau hủy/deadline đi RECONCILIATION, không phân bổ hoặc phục hồi giữ hàng. Confirmation hợp lệ phải phân bổ đủ từng đơn trong cùng transaction.
- Fund component bằng đúng goods cash/shipping cash/subsidy/late-payment source của order, không nạp số tiền tự do. Reservation/refund/release balances chỉ đổi qua operation và luôn ≤ confirmed amount. Operation có command/provider key, khóa order/component; retry phần FAILED, không retry phần CONFIRMED. Return chỉ hoàn 100% goods cash thực trả, không purchase shipping; cancellation shipping chỉ khi chưa dùng; seller release cần hết cửa sổ/consent hoặc KTV exemption đúng case.
- Return case trong 3 ngày từ valid delivery, đúng Buyer/cờ trả; early consent khóa quyền mở return mới. Case pending/hold chặn release; fund component tạo sau khi mở case cũng nhận hold. Một case có nhiều round; Seller/KTV duyệt return, chỉ KTV miễn hoàn sau formal damage request; Seller declined không phải quyết định cuối. Timeout dùng giờ server, full refund sau 2 ngày nếu không formal request; quyết định tài chính giữ lịch sử, không tự sửa mức phần trăm.
- Shipment unique `(order,leg)` thay unique order; return pickup từ approval, expected 24 giờ và deadline 3 ngày, tối đa 3 lần/1 lần mỗi ngày theo Asia/Saigon, không gia hạn do reschedule. Direct handover cần hai xác nhận SUCCESS hoặc OTP hợp lệ; có FAILED không tự xác nhận valid delivery. Có view phát hiện conflict và cả hai im lặng 14 ngày để worker xử lý.
- Review đúng hai bên/đơn đã nhận; sửa lưu revision. Penalty có policy/source/correction; audit chỉ thêm, JPA audit entity immutable để tránh dirty-check UPDATE. Manual dispatch/export cần Admin; event-recipient, channel và consumer checkpoint có khóa dedup. Revenue view chỉ cộng confirmed listing receipts, case statistics đếm case kết thúc thay vì nhân số round.

## Hợp đồng transaction cho các service tiếp theo

1. Đặt `identity_workflow`/`workflow_model='UC83'` rõ ràng cho dữ liệu mới khi đã nối API mới. Default `LEGACY_V14` giữ tương thích, không được dùng làm cách né gate ở service mới. Không tự chuyển account/product/order lịch sử thành canonical nếu thiếu chứng cứ. Legacy amounts/fees/receipts không được double-write vào ledger mới.
2. Tạo group → order snapshots → item snapshots → voucher/points sources và allocations → inventory reservations trong **một transaction**. Invariant tổng dùng deferred constraint triggers, kiểm tra khi COMMIT; không chia các INSERT thành autocommit. Lock product/user IDs theo thứ tự ổn định khi checkout nhiều item; khi deadlock/serialization failure, retry cả command cùng idempotency key, không tự bỏ item.
3. Tạo confirmation → đủ payment allocations → cập nhật paid timestamp/deadline/reservations trong một transaction. Tạo fund components từ allocation chính xác; subsidy phải có funding reference. Tiền đến sau hủy giữ provenance RECONCILIATION và xử lý riêng.
4. Tạo KTV listing decision → fee assessment trong một transaction; tạo charge chỉ khi D>0. Receipt thành công kích hoạt charge; API chọn revision public và chuyển ACTIVE sau đủ gate. Product IDs/revision phải khớp; bỏ qua AI/fee không được coi là duyệt.
5. Dùng `og_consume_auth_challenge`, `og_consume_handover_otp`, `og_confirm_direct_handover`, `og_early_complete_order` trong transaction có caller đã xác thực. Primitive có row lock/idempotency/deadline, nhưng **không tự xác thực HTTP caller**. Service vẫn phải xác minh credential, purpose, chữ ký provider, quyền đọc private objects và cảnh báo người dùng.
6. Case round/decision, shipment receipt và settlement có liên kết FK. Formal damage request cần evidence + event trong hạn. Chỉ đóng holds thuộc case đã quyết; các hold độc lập giữ nguyên. Backend phải tính/ghi `last_related_completion_at` khi tất cả nghĩa vụ thực sự xong rồi cleanup object trong vòng tối đa 7 ngày. View chỉ chọn ứng viên, không xóa evidence tự động; legacy cleanup +3 ngày đã tắt.

## Chuyển đổi và vận hành

V1–V14 không sửa. V15–V20 additive; V21 bổ sung zero checkout proof; migration V16 mở status width 40, V17 đổi quantity integer, V19 đổi shipment uniqueness. Mapping `ProductEntity`, `OrderItemEntity`, DTO/service quantity và `AuditLogEntity` được chỉnh tương thích. Indexes/CHECK/FK/trigger chạy trong migration transaction; trên local tổng 6 migration mất khoảng 0,988 giây. Có table locks khi ALTER/index/backfill; CLI giới hạn lock timeout 10 giây, statement timeout 120 giây. Với dữ liệu lớn hơn phải thử trên clone và bố trí maintenance phù hợp; không lấy thời gian local làm dự báo production.

CLI `SchemaMigrationCli` chỉ chạy Flyway, không khởi động web/scheduler/provider. [Runner](../../database/scripts/migrate-schema.ps1) kiểm tra env, archive PGDMP, compiled/source SQL hashes và checksum đã áp dụng; chỉ cho phép pending migrations, không repair history. Default `validate`; `-Action migrate` cần `-BackupPath`. Credentials chỉ từ env, không đặt trong command log/document.

Backup riêng: `output/database-review-2026-10-09/before-v20-private.dump`, 215.766 bytes; SHA-256 `535E268F14C23146E94E6362BB099D48BDEF65A919A333A6C72358C946309AF1`. File nằm trong `/output/` bị Git ignore; có thể chứa dữ liệu riêng tư, không đưa vào commit. Đã `pg_restore --no-owner --no-privileges --exit-on-error` vào database test riêng và migrate lên V20 thành công. Không reset/restore đè database local. Nếu cần phục hồi, khôi phục archive vào database mới, kiểm tra và đổi kết nối có kiểm soát; forward-fix sau rollout phải dùng V22 trở lên.

## Kiểm chứng quan sát được

- Backend Maven Java25, target21: **249 tests, 0 failures, 0 errors, 1 skipped**; 248 chạy thành công. Skip `symlinkToPrivateFileIsNotServed` do quyền symlink Windows, không thuộc migration. PostgreSQL tests dùng container riêng `og-shop-task69-tests`, pgvector/PostgreSQL17, port45569, không gắn volume ứng dụng.
- `DatabaseExpansionPostgresTest`: **30/30 pass**, database UUID riêng và cleanup đúng tên; clean V1–V21, V14 upgrade có SOLD fixture/giữ checksums, eKYC/role/document ownership, OTP uniqueness/counters, media constraints/immutability, fee boundary/top-up/revenue, stock race, deadline/snapshot, buyer block, exact funding/payment, late reconciliation, refund race/retry, direct handover/early consent/case hold/final refund, evidence ownership, reward race, restrictions, voucher quota/conservation/reuse, quick auth, checkout 0đ/negative/replay.
- Existing category/moderation/payment PostgreSQL tests và upgrade test chạy thực; Spring contexts **Hibernate `ddl-auto=validate`** trên V21 đạt. Append-only audit tương thích sau `@Immutable`; không nới ràng buộc để làm test qua.
- Restore clone phát hiện pending V8 trigger events khi backfill SOLD trước DDL; đã sửa thứ tự trong draft V16 và thêm fixture SOLD. Full regression sau sửa đạt. Live apply sau khi kiểm chứng: V15–V20 success lúc 13:02:15–13:02:17 ngày09-10-2026; validate20 pass; V21 checkout 0đ apply/validate21 pass lúc 13:10:05.
- Metadata sau apply: 105 public tables/7 views, 21 success/0 failed; fingerprints của **43/43 bảng legacy khớp ở checkpoint V20**. Sau đó refresh_sessions của ứng dụng tiếp tục thay đổi; V21 không chạy DML lên bảng này. Bản restore riêng đã migrate/validate toàn tuyến V14→V21; hash/count theo tất cả cột V14 của 43/43 bảng khớp. Artifact riêng ở `output/database-review-2026-10-09/`: backup proof, backend-v21.log, expansion-tests.json, restored-upgrade.log, restored-v14-v21.log, live-v21-migration.log, restore-preservation-before/after.json, live-pre-validation.log, live-migration.log, legacy-before/after.json, expansion-live-verification.json và expanded-runtime-schema.sql.

## Phần ứng dụng còn cần triển khai

Ưu tiên nối các API Identity/eKYC/Seller và bỏ MVP bypass cho canonical onboarding; Catalog revision/fee approval; checkout nhiều item/quantity/voucher/points; payment group IPN/allocations/components; shipping/return/case/settlement workers; notifications/reports. Media storage private/encryption/cleanup, email delivery/rate limits, provider signatures/sandbox E2E, policy administration và screen consent vẫn là trách nhiệm service/UI.

Schema không tự gửi email, gọi AI/VNPay/carrier, hoàn/giải ngân tiền, xử lý timeout/cancel im lặng, hay export file. Các primitive và view hỗ trợ những worker đó; chưa đánh dấu UC/API hoàn thành. Các JSON evidence/policy/model snapshots cần payload validation/redaction tại API; database CHECK không thay authorization hoặc bảo mật đối tượng lưu trữ.

## Ma trận nối từng UC với migration đã tạo

Ma trận được dẫn xuất từ nhóm G trong bản rà soát, với tên UC giữ nguyên. “Nền dữ liệu” dưới đây không phải trạng thái nghiệm thu hành vi.


| UC / tên hiện hành | Nhóm nền dữ liệu | Migration |
|---|---|---|
| UC01 — Đăng ký tài khoản bằng email | G01, G02 | V15 |
| UC02 — Đăng ký tài khoản bằng Google | G01, G02 | V15 |
| UC03 — Đăng nhập | G01, G10 | V15, V20 |
| UC04 — Quên mật khẩu | G01 | V15 |
| UC05 — Quản lý hồ sơ | G01, G02, G10 | V15, V20 |
| UC06 — Đăng ký xác thực tài khoản eKYC | G02 | V15 |
| UC07 — Xác minh danh tính | G02 | V15 |
| UC08 — Xác thực lại khuôn mặt | G02 | V15 |
| UC09 — Thiết lập xác thực hai lớp | G01, G02 | V15 |
| UC10 — Quản lý danh sách chặn | G10 | V20 |
| UC11 — Quản lý điểm phạt | G10 | V20 |
| UC12 — Tìm kiếm và lọc sản phẩm | G03, G04, G05 | V16, V17, V18 |
| UC13 — Xem chi tiết sản phẩm | G02, G03, G04, G05 | V15, V16, V17, V18 |
| UC14 — Xem sản phẩm được gợi ý | G12, G03, G04, G05 | V16, V17, V18, V20 |
| UC15 — Đăng bán sản phẩm | G02, G03, G04, G05 | V15, V16, V17, V18 |
| UC16 — Kiểm tra ảnh/video bằng AI | G03 | V16 |
| UC17 — Quản lý tin đăng | G03, G04, G05 | V16, V17, V18 |
| UC18 — Trao đổi qua tin nhắn | G05, G09, G10 | V16, V17, V19, V20 |
| UC19 — Thương lượng giá | G03, G05 | V16, V17 |
| UC20 — Xem voucher và điểm thưởng | G06 | V17, V21 |
| UC21 — Nhận voucher | G06 | V17, V21 |
| UC22 — Tích lũy và sử dụng điểm thưởng | G06 | V17, V21 |
| UC23 — Quản lý giỏ hàng | G05 | V16, V17 |
| UC24 — Đặt hàng | G03, G05, G06, G07, G08 | V16, V17, V18, V19, V21 |
| UC25 — Thanh toán đơn hàng | G05, G07 | V16, V17, V18, V21 |
| UC26 — Theo dõi đơn hàng và vận chuyển | G08, G09, G07 | V18, V19, V21 |
| UC27 — Xác nhận nhận hàng | G05, G08, G09, G07 | V16, V17, V18, V19, V21 |
| UC28 — Xác thực đồng kiểm | G05, G08 | V16, V17, V19 |
| UC29 — Xem lại các đơn hàng đã đặt | G03, G05, G07, G08, G09 | V16, V17, V18, V19, V21 |
| UC30 — Quản lý đơn bán | G05, G07, G08 | V16, V17, V18, V19, V21 |
| UC31 — Xem lại các đơn hàng đã bán | G03, G05, G07, G08, G09 | V16, V17, V18, V19, V21 |
| UC32 — Chuẩn bị giao hàng và xác nhận bàn giao | G05, G08 | V16, V17, V19 |
| UC33 — Theo dõi và xác nhận hàng trả lại | G08, G09, G07 | V18, V19, V21 |
| UC34 — Nhận thông báo yêu cầu đăng nhập | G11, G01 | V15, V20 |
| UC35 — Nhận thông báo yêu cầu xác thực eKYC | G02, G11 | V15, V20 |
| UC36 — Nhận thông báo mặt hàng trong giỏ hàng đã hết | G05, G11 | V16, V17, V20 |
| UC37 — Nhận thông báo khi mặt hàng đã hết | G05, G11 | V16, V17, V20 |
| UC38 — Nhận thông báo về voucher | G06, G11 | V17, V20, V21 |
| UC39 — Nhận thông báo về tình trạng đơn hàng | G05, G07, G08, G09, G11 | V16, V17, V18, V19, V20, V21 |
| UC40 — Nhận thông báo hoàn tiền | G07, G09, G11 | V18, V19, V20, V21 |
| UC41 — Nhận thông báo yêu cầu thanh toán | G05, G07, G11 | V16, V17, V18, V20, V21 |
| UC42 — Nhận thông báo đơn hàng đã được thanh toán | G07, G11 | V18, V20, V21 |
| UC43 — Nhận thông báo khi nội dung bài đăng bị lỗi | G03, G04, G11 | V16, V18, V20 |
| UC44 — Nhận thông báo tình trạng bài đăng | G03, G04, G05, G11 | V16, V17, V18, V20 |
| UC45 — Nhận thông báo khi có người mua hàng từ bài đăng | G05, G11 | V16, V17, V20 |
| UC46 — Nhận thông báo khi được đánh giá | G10, G11 | V20 |
| UC47 — Nhận thông báo khi có tin nhắn | G11, G10 | V20 |
| UC48 — Nhận thông báo về thời hạn khiếu nại | G05, G08, G09, G11 | V16, V17, V19, V20 |
| UC49 — Nhận thông báo khi khiếu nại hệ thống được xem xét | G09, G07, G11 | V18, V19, V20, V21 |
| UC50 — Nhận thông báo khi cập nhật phí nền tảng | G04, G11 | V16, V18, V20 |
| UC51 — Nhận thông báo khi vô hiệu hóa/kích hoạt tài khoản | G10, G11 | V20 |
| UC52 — Nhận thông báo có các vấn đề cần giải quyết | G02, G03, G07, G09, G11 | V15, V16, V18, V19, V20, V21 |
| UC53 — Nhận thông báo xác nhận phê duyệt, giải quyết | G02, G03, G04, G09, G11 | V15, V16, V18, V19, V20 |
| UC54 — Nhận thông báo xác nhận tạo mới, cập nhật voucher | G06, G11 | V17, V20, V21 |
| UC55 — Nhận thông báo xác nhận tặng, thu hồi voucher | G06, G11 | V17, V20, V21 |
| UC56 — Nhận thông báo khi có tin nhắn vi phạm quy định | G09, G10, G11 | V19, V20 |
| UC57 — Nhận thông báo từ Admin | G11 | V20 |
| UC58 — Gửi thông báo | G11 | V20 |
| UC59 — Xem thông báo | G11 | V20 |
| UC60 — Điều hướng theo thông báo | G11 | V20 |
| UC61 — Đánh giá đối tác sau giao dịch | G05, G08, G10 | V16, V17, V19, V20 |
| UC62 — Yêu cầu trả hàng | G05, G08, G09, G07 | V16, V17, V18, V19, V21 |
| UC63 — Đề nghị xem xét lại hoàn tiền sau khi nhận hàng trả | G08, G09, G07 | V18, V19, V21 |
| UC64 — Gửi khiếu nại lỗi hệ thống | G09, G04, G07 | V16, V18, V19, V21 |
| UC65 — Chuyển hướng khiếu nại | G09, G10 | V19, V20 |
| UC66 — Quản lý tài khoản KTV | G01, G10 | V15, V20 |
| UC67 — Quản lý tài khoản người dùng | G10, G09, G05 | V16, V17, V19, V20 |
| UC68 — Xét duyệt hồ sơ eKYC | G02, G10 | V15, V20 |
| UC69 — Quản lý voucher | G06 | V17, V21 |
| UC70 — Tặng voucher | G06, G09, G10 | V17, V19, V20, V21 |
| UC71 — Quản lý trạng thái giữ tiền và giải phóng tiền | G07, G05, G08, G09 | V16, V17, V18, V19, V21 |
| UC72 — Quản lý phí hệ thống | G04 | V16, V18 |
| UC73 — Kiểm duyệt tin đăng | G03, G04, G10 | V16, V18, V20 |
| UC74 — Xử lý báo cáo vi phạm và quản lý điểm phạt | G09, G10 | V19, V20 |
| UC75 — Xử lý tranh chấp | G09, G08, G07 | V18, V19, V21 |
| UC76 — Xử lý khiếu nại hệ thống | G09, G06, G07 | V17, V18, V19, V21 |
| UC77 — Lịch sử hệ thống | G10 | V20 |
| UC78 — Kết xuất báo cáo số lượng người dùng | G10, G12 | V20 |
| UC79 — Kết xuất báo cáo doanh thu | G04, G12 | V16, V18, V20 |
| UC80 — Thống kê mặt hàng được tìm mua nhiều/ít nhất | G12, G03, G05 | V16, V17, V20 |
| UC81 — Thống kê số lượng hàng bị trả và khiếu nại | G09, G08, G12 | V19, V20 |
| UC82 — Hủy đơn hàng | G05, G07, G08, G09 | V16, V17, V18, V19, V21 |
| UC83 — Hoàn tiền đơn hàng | G07, G08, G09, G06 | V17, V18, V19, V21 |
