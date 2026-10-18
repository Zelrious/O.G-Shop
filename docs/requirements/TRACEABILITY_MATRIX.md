# Traceability Matrix

## Voucher — TASK-0093 / 2026-10-13

| Requirement | Implementation / proof | Status |
|---|---|---|
| UC20/21 voucher | Own wallet/claim/date/scope/minimum/limits; repeated claim safe; UI customer claim + PG race/privacy | VERIFIED local voucher; full xu history/spending separate |
| UC69 | Admin create/update/version/code/policy/limits/date; immutable old benefit; UI create/update | VERIFIED local |
| UC70 + approved bulk extension | KTV/customer scope; filtered explicit1–100; preview/reason/confirm/recheck/receipt retry; used/expired guards; UI grant2→revoke2 | VERIFIED local |
| Received voucher checkout | Owned revision/percent cap/minimum/scope, one grant/one order race, no spent-grant restoration | VERIFIED current buy-now; full canonical multi-seller separate |
| UC38/54/55 and targets UC59/60 | Private recipient/actor notices;24h/expired reminder dedupe; role destinations | VERIFIED local |
| UC77 voucher writes | Immutable actor/request/reason/result audit and grant events in transaction | VERIFIED writing foundation; central audit viewer separate |

[API](../api/VOUCHERS_API.md), [evidence](../../output/vouchers-0093/README.md). App V32/47 tables, preservation150/150. Providers unchanged; checkpoints below remain historical.

## Chat/offer — TASK-0090 / 2026-10-13

| Requirement | Implementation / proof | Status |
|---|---|---|
| UC18, UC47 | ChatService/MediaService, participant-only API, MessagesPage, private blob, cursor/read/unread/retry, PostgreSQL quota/privacy/rollback/race/retention, browser2 vai trò | VERIFIED local |
| UC10 | Seller block/search/manage, audit, checkout/native block guard, old open-order chat exception; staff/role negatives | VERIFIED local |
| UC19 | OfferService/state/version/key/snapshot/fee/expiry, OfferCard/Dialog, counter chain and one pending, accept/reject race | VERIFIED local |
| Checkout nhận offer | NegotiatedCheckoutFacade, preview agreedPrice/acceptedOfferId, single-use/idempotent/race/cancel/rollback/link/outbox; browser subtotal1.050.000 vs niêm yết1.200.000 | VERIFIED trong checkout1 món hiện hành; không nâng full UC83 |
| UC59–UC60 | CONVERSATION target, owner-only/read/allowlist; chat/media/offer/result/expiry/negotiated-order outbox | VERIFIED local nguồn chat/offer |
| UC73 + UC67 core | Giữ phần manual/account đợt1/2, regression lại trên V31 | VERIFIED phạm vi đã nối |

[API](../api/CHAT_OFFER_API.md), [bằng chứng](../../output/phase34-runtime/README.md). App V31/47 bảng, backup/restore/migrate giữ150/150 nguồn. Full UC68/UC74/AI/providers không được đánh dấu từ task này; các trạng thái dưới giữ lịch sử.

## Quản lý user — TASK-0088 / 2026-10-12

| Requirement | Implementation / proof | Status |
|---|---|---|
| UC67 | AccountManagementService/AccessFilter, KtvUsersPage/AccountStatusPage; governance PG role/target/scope/key/version/rollback/race/expiry, browser khóa→login→old order→case response | VERIFIED local core; UC68 và quyết định xử phạt UC74 tiếp nối |
| Phần đọc UC11 | AccountTrustRepository confirmed penalty/review projection; score/history và warning UI, PG xác nhận không autoban | VERIFIED nguồn đọc; pipeline ghi/phục hồi điểm chưa nối |
| UC51; phần account UC53 | Audit/outbox cùng command, ACCOUNT_REVIEWED/ACCOUNT_CONFIRMED, actor/owner targets; PG rollback/privacy, browser read/navigation | VERIFIED account notices; UC66/các nguồn khác tiếp nối |
| Phần UC52 | Warning <90/<80 trên hồ sơ KTV | UI đã nối; notification khi vượt ngưỡng chưa triển khai |
| UC59–UC60 | Account destinations, KTV/Admin notification routes; owner read và allowlist, browser giảm unread/đúng target | VERIFIED local account + listing targets |
| Nền UC77 | Governance history, immutable before/after/request/key, audited SQL update proof | VERIFIED ghi lịch sử; viewer/export toàn hệ thống ngoài scope |

[API](../api/ACCOUNT_MANAGEMENT_API.md), [thiết kế/rollout](../architecture/ACCOUNT_GOVERNANCE_V30_20261012.md), [bằng chứng](../../output/phase2-runtime/README.md). UC74 chỉ đọc nguồn đã xác nhận; CHAT scope chưa là UC18, UC10/UC19 tiếp nối. V28–V30 chưa apply database ứng dụng. Các bảng bên dưới giữ lịch sử.

## Kiểm duyệt và phí đăng — TASK-0087 / 2026-10-12

Dùng mã bộ 83 hiện hành, tách phạm vi thực hiện khỏi nghiệm thu toàn UC.

| Requirement | Implementation / proof | Status |
|---|---|---|
| UC73; phần manual UC15/UC17 | SellerProductService/ListingWorkflowService, ModerationWorkbench/SellerListingReviewPage; stale/version/key/history/media/edit-hide/PG races | VERIFIED local phần manual; UC16 AI/full eKYC/UC83 tiếp nối |
| UC72 phí đăng; UC22 nguồn tin đầu | Assessment/charge/receipt/reward facade, ListingFeeDialog; quantity/threshold/top-up/decrease/replay/rollback/concurrency | VERIFIED mock/source tin đầu; provider thật và nguồn xu khác chưa nghiệm thu |
| UC44; phần listing UC52/UC53 | Submit/decision/open events + outbox recipient; workflow PostgreSQL | VERIFIED listing notifications; các nguồn khác tiếp nối |
| UC59/UC60 | NotificationController, Bell/Page, read owner-only/role allowlist; PG privacy và browser desktop/mobile | VERIFIED local phần listing targets; không có chat/user/order targets mới |
| Nền UC77 audit | Audit ghi cùng transaction, negative rollback tests | VERIFIED ghi audit trong workflow; viewer/export UC77 ngoài scope |

[Thiết kế/rollout](../architecture/MODERATION_LISTING_V27_20261012.md), [Ảnh và kiểm chứng](../../output/phase1-runtime/README.md). V27 chưa apply database ứng dụng. Bảng bên dưới giữ lịch sử.

## Bộ 83 UC hiện hành — auth / TASK-0082 và TASK-0086

Mã UC không dấu gạch dưới đây thuộc bộ 83 hiện hành; bảng UC-01... phía dưới là baseline 19 UC giữ lịch sử.

| Requirement | Backend / Frontend | Database | Verification | Status |
|---|---|---|---|---|
| UC01 email registration | EmailRegistrationService, AuthController; RegisterForm OTP/Buyer/Seller | V25 auth draft/seller metadata; giữ V1–V24 | AuthControllerTest, RegisterForm.test.tsx, 12 EmailRegistrationPostgresTest; local SMTP transport | VERIFIED local; Gmail chờ cấu hình |
| UC22 registration coins | RegistrationCommerceFacade; thông báo 3.000 xu sau xác nhận | CREDIT_REGISTRATION đúng 3.000, unique/user, verified-account guard | PostgreSQL ledger failure rollback/concurrency/duplicate reward pass | VERIFIED registration source; nguồn xu khác tiếp nối |
| UC02 Google registration/link | GoogleIdTokenVerifier, AuthFlowService, EmailRegistrationService; GoogleSignInButton/RegisterForm | V26 google_subject, purpose/nonce/ticket; giữ pipeline V25 | GoogleIdTokenVerifierTest RSA/JWK local, AuthFlowsPostgresTest link/new Buyer/Seller/replay, SDK component mock | VERIFIED local implementation; Google/Gmail thật và origin chưa nghiệm thu |
| UC03 password/Google login và email 2FA | AuthService authenticatePassword, AuthFlowService; LoginForm/AuthOtpForm/AuthContext | V26 auth_data/LOGIN_EMAIL_2FA; settings hiện có, deadline V24 | PostgreSQL no session trước OTP/attempts/concurrency/staff; frontend pending/race/error | VERIFIED local login theo cờ; không thay nghiệm thu UC09 bật/tắt bằng face |
| UC04 reset / UC05 password change | AuthFlowService/ResetEmailDispatcher, ProfileService; ForgotPasswordForm/SecurityTab | V26 PASSWORD_RESET/COMPLETE và credential_version | PostgreSQL generic/SMTP delay/rollback/replay, old Bearer/cookie/missing-cv; frontend reset ba bước | VERIFIED local; provider email thật và rollout V26 tiếp nối |
| UC03 locked exception, phần auth UC67 | AccountRestrictedException; LoginForm/GoogleSignInButton | status/restrictions hiện có, cv validator fail closed | Credential đúng 403 structured; sai 401 chung | VERIFIED auth response; full UC67 từng hành động/nghĩa vụ cũ chưa nghiệm thu |
| UC01 Seller draft, nền UC06–UC07 identity verification | PENDING_EKYC draft, guard AI/MVP cho user mới; màn hình chờ xác minh | V25 store/contact/return address; profile/decisions V15–V24 | PostgreSQL seller/addresses/activation guard và Verification.test.tsx pass | VERIFIED draft; UC06–UC07 eKYC/KTV tiếp nối |

Ma trận này phải được cập nhật khi tài liệu yêu cầu chính thức được đưa vào repository.

| Requirement | Module doc | Backend | Frontend | Database | Test | Status |
|---|---|---|---|---|---|---|
| UC-01 | `modules/identity.md` | Auth/session baseline | Auth API client | V1 + V2 sessions | `AuthServiceTest`, `AuthControllerTest`, frontend auth test | IN_PROGRESS |
| UC-02 | `modules/identity.md` | eKYC gateway + MVP Seller Activation | eKYC wizard + MVP activation UI | V2 privacy metrics + V4 MVP bypass | `EkycVerificationServiceTest`, `SellerActivationServiceTest`, frontend verification test | IN_PROGRESS |
| UC-03 | `modules/catalog.md` | `SellerProductService`, `SellerProductController` | `features/listing/`, `SellerListingsPage`, `ListingForm` | V1 + V3 + V5 MVP categories | `SellerProductServiceTest`, `Listing.test.tsx`, DB tests | IMPLEMENTED |
| UC-04 | `modules/catalog.md` | `CatalogService`, `CatalogController` | `features/marketplace/`, `MarketplacePage`, `ProductDetailPage` | V1 + V3 + V5 MVP categories | `CatalogServiceTest`, `Marketplace.test.tsx`, DB tests | IMPLEMENTED |
| UC-05–06 | `modules/communication.md` | Pending | Pending | V1 + V3 chat/offer baseline | Message/participant/offer constraint tests | PLANNED |
| UC-07, UC-09 | `modules/commerce.md` | Pending | Pending | V1 + V3 price/fee/order snapshot | Order total, reservation, legacy backfill tests | PLANNED |
| UC-08 | `modules/payment.md` | Pending | Pending | V1 + V3 fee-inclusive order total | Payment/amount invariant tests | PLANNED |
| UC-10–11, UC-14 | `modules/fulfillment.md` | Pending | Pending | Pending | Pending | PLANNED |
| UC-12–13, UC-15–17 | `modules/trust-safety.md` | Pending | Pending | Pending | Pending | PLANNED |
| UC-18–19 | `modules/platform.md` | Pending | Pending | V1 + V3 audit/notification/outbox baseline | Outbox payload constraint test | PLANNED |
