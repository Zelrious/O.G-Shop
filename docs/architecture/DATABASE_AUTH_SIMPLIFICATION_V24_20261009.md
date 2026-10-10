# Database authentication simplification — V24

User approved removing `refresh_sessions` after discussing fixed-duration login. The separate question about removing `auth_challenges` does not authorize deleting OTP/quick-auth state. That table and all eKYC tables remain.

## Before and after

| Item | V23 | V24 |
|---|---|---|
| Business tables / physical columns | 48 / 907 | 47 / 895 |
| Public tables including Flyway | 49 | 48 |
| Compatibility views / report views | 104 / 7 | 103 / 7 |
| Physical foreign keys | 191 | 189 |
| Login | Access JWT plus renewable opaque refresh | Fixed access JWT deadline, no renewal |
| Report chapter | Summary + 48 detail tables | Summary + 47 detail tables |

Default login lifetime remains 15 minutes. The HttpOnly access cookie permits restoring login after reload until the original deadline; role reload also preserves that deadline. Frontend clears authentication on expiry/401 and never calls refresh routes. Business APIs require Bearer JWT. Logout clears browser credentials; a copied access token remains usable until its original deadline, with no per-session revocation store. See [Identity API](../api/IDENTITY_API.md).

V24 removes the refresh branch in the V22 graph validator before dropping `og_compat.refresh_sessions` and `public.refresh_sessions`, without CASCADE. Other graph checks remain unchanged. Historical migrations V1–V23 are not rewritten; V24 is now applied and immutable. Use V25+ for any further change.

## Database evidence

Applied to local `og_shop` on 2026-10-09 at 23:39:36, after private custom-format backup and restoring/upgrading a separate clone. The local PostgreSQL image lacks the unused vector extension, so restore skipped only its extension/comment archive entries; no vector column or business data was omitted and original archives remain intact.

All 47 retained table counts/hashes match before, clone after and live after. Flyway validates V1–V24. Temporary application smoke passes Hibernate validation and HTTP health/categories/products (200); an invalid access cookie yields 401. This smoke does not assert that an already-running development server was restarted.

Current structure-only artifacts:

- [Dictionary V24](../../database/docs/DATABASE_TABLE_DICTIONARY_V24.md)
- [ERD DDL V24](../../database/schema/og_shop_v24_tables_for_erd.sql): imported into an empty database, exactly 47 tables; omit Flyway and views from ERD.
- [Full runtime schema V24](../../database/schema/og_shop_v24_runtime_schema.sql): includes compatibility/reporting logic, no table data.
- [Catalog metadata](../../database/design/v24-consolidated-structure.json)

Private backups, fingerprints and logs are ignored under `output/auth-simplification-2026-10-09/`. Do not publish them. Runtime migrations remain the authoritative DDL; do not apply an ERD snapshot over the application database.

## Verification

Backend: 263 tests, 237 passed, 26 skipped, zero failures/errors. All 33 expansion PostgreSQL tests plus the new V23→V24 populated removal test passed in a disposable PostgreSQL/pgvector container using fake fixtures. Other optional PostgreSQL suites were not enabled and are not claimed as passed. Tests cover fixed expiry, invalid/missing credentials, blocked users, trusted Origin, cookie-only rejection on business APIs and OTP writes after removal.

Frontend: full 23-file/104-test suite passed; after the final LoginForm change, 7 dependent files/40 tests passed again. Scoped lint and TypeScript/Vite build passed. Global lint remains failing on the existing CartContext line 100 export warning, unrelated to authentication; build has the existing chunk-size warning.

## Native report

Updated only the database chapter in working tab `t.goqi7hu9cf5` of [Báo cáo đồ án](https://docs.google.com/document/d/1mbj6vIkLgNM0mzdy-xRukPfz2r0vN_kk5jvKx_QToKQ/edit?tab=t.goqi7hu9cf5). Full [native backup](https://docs.google.com/document/d/1OMZpA3CtUTFBq8wre8jX8-rtXsEwxGkArUj156C22QQ/edit) was copied and verified before edits.

Deleted the refresh summary row and detail table/caption, renumbered later entries and kept the 47 retained detail tables' cell text unchanged. Readback confirms 895 fields, 4,854 cells, 48 correct captions, all 48 header rows pinned/centered, STT centered, other body columns justified and every cell vertically MIDDLE. The 106 preceding tables and other tab identities/order were preserved.

The full file-backed trusted-read bridge failed; its receipt is saved. Narrow native reads and a full native backup supported the scoped edit. Do not claim a successful complete protected-control inventory. PDF export returned 403, so page layout has not been visually verified; native content/style checks passed.

## Remaining scope

`auth_challenges` can be removed only if OTP expiry, single use/attempt state and quick-auth results are preserved elsewhere. Gmail is the delivery service; it is not the application's OTP store. No Redis/cache dependency, OTP/Google implementation or prior proposed eKYC merge was added. The canonical API/UI/worker gaps for the 83 use cases remain as recorded in earlier checkpoints.
