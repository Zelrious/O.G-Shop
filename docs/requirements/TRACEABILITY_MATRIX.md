# Traceability Matrix

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
