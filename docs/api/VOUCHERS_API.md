# Voucher API — V32 / TASK-0093

Implemented/verified locally in TASK-0093; [evidence](../../output/vouchers-0093/README.md). Base `/api/v1`, authenticated signed session, no entity DTO or client-only role decision.

| Role | Endpoint | Behavior |
|---|---|---|
| Admin | GET `/admin/vouchers` | Real campaigns, current policy, issued/used counters |
| Admin | POST `/admin/vouchers` | Create campaign and immutable policy revision |
| Admin | PUT `/admin/vouchers/{id}` | Expected version; new policy revision, previous grants unchanged |
| KTV | GET `/ktv/vouchers` | Programs available to management |
| KTV | GET `/ktv/vouchers/{id}/targets` | Paginated real customers; `action=GRANT/REVOKE`, `query`, `role=ALL/BUYER/SELLER`, `status`, `eligibleOnly`, `page`, `size≤50` |
| KTV | POST `/ktv/vouchers/{id}/preview` | Revalidate explicit selection without issuing or revoking |
| KTV | POST `/ktv/vouchers/{id}/commands` | Transactional batch, stable receipt, success/skipped per target |
| Buyer/Seller | GET `/commerce/vouchers` | Own grants, receivable campaigns, real xu balance/recent ledger |
| Buyer/Seller | POST `/commerce/vouchers/{id}/claim` | One receipt per campaign/customer, idempotent command |

Staff endpoints validate database roles as well as HTTP role guards. Mixed staff/customer accounts cannot access wallet. Filters do not select users; the client sends explicit selections only.

Save body: `commandId` UUID, `version` (0 create), `code` unique 3–50 ASCII letters/digits/_/-, `title`, `description`, `voucherType=ORDER_DISCOUNT/SHIPPING_DISCOUNT`, `discountType=FIXED_AMOUNT/PERCENTAGE`, positive `discountValue`, optional positive `maxDiscountAmount` (required for percent, 1–100), nonnegative `minOrderAmount`, `scope=CHECKOUT/PRODUCTS`, `productIds` (max100, active products when enabling), `quantity`1–1,000,000, ISO timestamps `startTime/endTime`, `active`, `selfClaim`. Shipping scope is CHECKOUT. One receipt per customer/program. Code retained after creation; reductions below previously issued quantities rejected.

Claim body: `{commandId,version}`. Duplicate claim returns the owned grant without extra issuance or duplicate recipient notification. Private programs require KTV. A paused campaign stops new issuance and preserves already granted policy rights.

Bulk body: `{commandId,version,action,ids,reason}`. 1–100 distinct IDs; GRANT uses customer ID strings, REVOKE uses grant UUIDs. Reason required, max1000. Preview returns `{items,successful,skipped}`. Result item: `{id,userId,userName,eligible,status,reason,grantId}`. Execute checks the version again; state changes may convert eligible rows to SKIPPED. Expired/used/revoked grants cannot be revoked. Infrastructure failure rolls back the whole batch. Retry the identical UUID and body for the persisted result; changed content with same UUID is rejected.

Checkout retains `voucherCode`, but preview lists only eligible owned grants. Unknown/unowned/ineligible input returns no applied voucher; the UI explains and clears previous code. Buy-now fails for those codes. Revision, owner, state, date, minimum and scope are rechecked under locks. One voucher per order; grant consumption is bound to the immutable order snapshot. Order cancellation/refund cannot restore it. No new point-spending workflow is asserted.

Notices: VOUCHER_WALLET→`/vouchers`, ADMIN_VOUCHERS→`/admin/vouchers`, KTV_VOUCHERS→`/ktv/vouchers`; recipient-only read/list remains unchanged. Outbox records and command audit share mutation transaction. Reminder scans unused grants expiring within24h or already expired, with deterministic event UUID dedupe; physical state stays AVAILABLE for expired receipts, eligibility derives from deadline. Cursor cycles in500-row pages so historical expiry rows do not starve newer grants.
