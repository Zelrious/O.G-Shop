# Traceability Matrix

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
